package com.nuvio.app.features.plugins.runtime

import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginStorage
import com.nuvio.app.features.plugins.runtime.crypto.CryptoBridge
import com.nuvio.app.features.plugins.runtime.dom.DomBridge
import com.nuvio.app.features.plugins.runtime.host.HostApiRegistry
import com.nuvio.app.features.plugins.runtime.host.HostFunctions
import com.nuvio.app.features.plugins.runtime.js.JsBindings
import com.nuvio.app.features.plugins.runtime.js.JsRuntime
import com.nuvio.app.features.plugins.runtime.network.FetchBridge
import com.nuvio.app.features.plugins.runtime.network.UrlBridge
import com.nuvio.app.features.plugins.runtime.wasm.WasmBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.generic_unknown
import org.jetbrains.compose.resources.getString

internal const val MAX_CONCURRENT_PLUGINS = 10
internal const val PLUGIN_TIMEOUT_MS = 60_000L

internal object PluginRuntime {
    private val json = Json { ignoreUnknownKeys = true }
    private val scraperSemaphore = Semaphore(MAX_CONCURRENT_PLUGINS)
    private val searchPaused = MutableStateFlow(false)

    fun setSearchPaused(paused: Boolean) {
        searchPaused.value = paused
    }

    suspend fun executePlugin(
        code: String,
        mediaId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        scraperId: String,
        respectSearchPause: Boolean = true,
    ): List<PluginRuntimeResult> {
        suspend fun run(): List<PluginRuntimeResult> {
            val scraperSettingsJson = PluginStorage.loadScraperSettings(scraperId) ?: "{}"
            val scraperSettingsMap = runCatching {
                json.decodeFromString<Map<String, JsonElement>>(scraperSettingsJson)
            }.getOrElse { emptyMap() }

            return scraperSemaphore.withPermit {
                withContext(pluginDispatcher) {
                    withTimeout(PLUGIN_TIMEOUT_MS) {
                        executePluginInternal(
                            code = code,
                            mediaId = mediaId,
                            mediaType = mediaType,
                            season = season,
                            episode = episode,
                            scraperId = scraperId,
                            scraperSettings = scraperSettingsMap,
                        )
                    }
                }
            }
        }

        return if (respectSearchPause) {
            runWhenSearchActive { run() }
        } else {
            run()
        }
    }

    suspend fun getPluginSettingsLayout(
        code: String,
        scraperId: String,
    ): String? = scraperSemaphore.withPermit {
        withContext(pluginDispatcher) {
            withTimeout(PLUGIN_TIMEOUT_MS) {
                val jsRuntime = JsRuntime()
                val deferred = CompletableDeferred<String?>()
                try {
                    jsRuntime.use {
                        HostFunctions(
                            scraperId = scraperId,
                            scraperSettingsJson = "{}",
                            onResult = { deferred.complete(it) },
                        ).register(this)
                        FetchBridge().register(this)
                        UrlBridge().register(this)
                        CryptoBridge().register(this)

                        evaluateCached({ JsRuntime.polyfillBytecode(this) }, JsBindings.staticPolyfillCode)
                        evaluate<Any?>(wrapPluginModule(code))
                        evaluateCached({ JsRuntime.settingsCallBytecode(this) }, JsBindings.staticSettingsCallCode)
                        deferred.await()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    suspend fun executePluginHome(
        code: String,
        scraperId: String,
        scraperName: String,
    ): List<com.nuvio.app.features.plugins.PluginHomeSection> = scraperSemaphore.withPermit {
        withContext(pluginDispatcher) {
            withTimeout(PLUGIN_TIMEOUT_MS) {
                val scraperSettingsJson = PluginStorage.loadScraperSettings(scraperId) ?: "{}"
                val jsRuntime = JsRuntime()
                val deferred = CompletableDeferred<String?>()
                val domBridge = DomBridge()
                val hostRegistry = HostApiRegistry().apply {
                    addModule(
                        HostFunctions(
                            scraperId = scraperId,
                            scraperSettingsJson = scraperSettingsJson,
                            onResult = { deferred.complete(it) },
                        ),
                    )
                    addModule(FetchBridge())
                    addModule(UrlBridge())
                    addModule(CryptoBridge())
                    addModule(WasmBridge())
                    addModule(domBridge)
                }

                try {
                    jsRuntime.use {
                        hostRegistry.registerAll(this)
                        evaluateCached({ JsRuntime.polyfillBytecode(this) }, JsBindings.staticPolyfillCode)
                        evaluate<Any?>(wrapPluginModule(code))
                        evaluate<Any?>(JsBindings.staticHomeCallCode)
                        val rawJson = deferred.await().orEmpty()
                        parseJsonHomeResults(rawJson, scraperId, scraperName)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                } finally {
                    domBridge.clear()
                }
            }
        }
    suspend fun executePluginDetails(
        code: String,
        scraperId: String,
        contentId: String,
    ): com.nuvio.app.features.plugins.PluginDetailsResult? = scraperSemaphore.withPermit {
        withContext(pluginDispatcher) {
            withTimeout(PLUGIN_TIMEOUT_MS) {
                val scraperSettingsJson = PluginStorage.loadScraperSettings(scraperId) ?: "{}"
                val jsRuntime = JsRuntime()
                val deferred = CompletableDeferred<String?>()
                val domBridge = DomBridge()
                val callArgsJson = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(contentId),
                        "mediaId" to JsonPrimitive(contentId),
                    ),
                ).toString()
                val hostRegistry = HostApiRegistry().apply {
                    addModule(
                        HostFunctions(
                            scraperId = scraperId,
                            scraperSettingsJson = scraperSettingsJson,
                            callArgsJson = callArgsJson,
                            onResult = { deferred.complete(it) },
                        ),
                    )
                    addModule(FetchBridge())
                    addModule(UrlBridge())
                    addModule(CryptoBridge())
                    addModule(WasmBridge())
                    addModule(domBridge)
                }

                try {
                    jsRuntime.use {
                        hostRegistry.registerAll(this)
                        evaluateCached({ JsRuntime.polyfillBytecode(this) }, JsBindings.staticPolyfillCode)
                        evaluate<Any?>(wrapPluginModule(code))
                        evaluate<Any?>(JsBindings.staticDetailsCallCode)
                        val rawJson = deferred.await().orEmpty()
                        parseJsonDetailsResult(rawJson)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } finally {
                    domBridge.clear()
                }
            }
        }
    }

    suspend fun executePluginSearch(
        code: String,
        scraperId: String,
        scraperName: String,
        query: String,
        page: Int = 1,
    ): List<com.nuvio.app.features.plugins.PluginHomeSection> = scraperSemaphore.withPermit {
        withContext(pluginDispatcher) {
            withTimeout(PLUGIN_TIMEOUT_MS) {
                val scraperSettingsJson = PluginStorage.loadScraperSettings(scraperId) ?: "{}"
                val jsRuntime = JsRuntime()
                val deferred = CompletableDeferred<String?>()
                val domBridge = DomBridge()
                val callArgsJson = JsonObject(
                    mapOf(
                        "query" to JsonPrimitive(query),
                        "q" to JsonPrimitive(query),
                        "keyword" to JsonPrimitive(query),
                        "page" to JsonPrimitive(page),
                    ),
                ).toString()
                val hostRegistry = HostApiRegistry().apply {
                    addModule(
                        HostFunctions(
                            scraperId = scraperId,
                            scraperSettingsJson = scraperSettingsJson,
                            callArgsJson = callArgsJson,
                            onResult = { deferred.complete(it) },
                        ),
                    )
                    addModule(FetchBridge())
                    addModule(UrlBridge())
                    addModule(CryptoBridge())
                    addModule(WasmBridge())
                    addModule(domBridge)
                }

                try {
                    jsRuntime.use {
                        hostRegistry.registerAll(this)
                        evaluateCached({ JsRuntime.polyfillBytecode(this) }, JsBindings.staticPolyfillCode)
                        evaluate<Any?>(wrapPluginModule(code))
                        evaluate<Any?>(JsBindings.staticSearchCallCode)
                        val rawJson = deferred.await().orEmpty()
                        parseJsonSearchResults(rawJson, scraperId, scraperName)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                } finally {
                    domBridge.clear()
                }
            }
        }
    }

    private suspend fun executePluginInternal(
        code: String,
        mediaId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        scraperId: String,
        scraperSettings: Map<String, JsonElement>,
    ): List<PluginRuntimeResult> {
        val jsRuntime = JsRuntime()
        val deferred = CompletableDeferred<String>()
        val settingsJson = JsonObject(scraperSettings).toString()
        val callArgsJson = JsonObject(
            mapOf(
                "id" to JsonPrimitive(mediaId),
                "mediaId" to JsonPrimitive(mediaId),
                "mediaType" to JsonPrimitive(mediaType),
                "season" to (season?.let(::JsonPrimitive) ?: JsonNull),
                "episode" to (episode?.let(::JsonPrimitive) ?: JsonNull),
            ),
        ).toString()

        val domBridge = DomBridge()
        val hostRegistry = HostApiRegistry().apply {
            addModule(
                HostFunctions(
                    scraperId = scraperId,
                    scraperSettingsJson = settingsJson,
                    callArgsJson = callArgsJson,
                    onResult = { deferred.complete(it) },
                ),
            )
            addModule(FetchBridge())
            addModule(UrlBridge())
            addModule(CryptoBridge())
            addModule(WasmBridge())
            addModule(domBridge)
        }

        try {
            jsRuntime.use {
                hostRegistry.registerAll(this)
                evaluateCached({ JsRuntime.polyfillBytecode(this) }, JsBindings.staticPolyfillCode)
                evaluate<Any?>(wrapPluginModule(code))
                evaluateCached({ JsRuntime.callBytecode(this) }, JsBindings.staticCallCode)
                deferred.await()
            }
            return parseJsonResults(deferred.await())
        } finally {
            domBridge.clear()
        }
    }

    private fun wrapPluginModule(code: String): String = """
        var module = { exports: {} };
        var exports = module.exports;
        (function() {
            $code
        })();
    """.trimIndent()

    private suspend fun com.dokar.quickjs.QuickJs.evaluateCached(
        bytecode: com.dokar.quickjs.QuickJs.() -> ByteArray,
        source: String,
    ) {
        val compiled = runCatching { bytecode() }.getOrNull()
        if (compiled != null) {
            evaluate<Any?>(compiled)
        } else {
            evaluate<Any?>(source)
        }
    }

    private fun parseJsonResults(rawJson: String): List<PluginRuntimeResult> {
        return runCatching {
            val array = json.parseToJsonElement(rawJson) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val url = when (val urlValue = item["url"]) {
                    is JsonPrimitive -> urlValue.contentOrNull?.takeIf { it.isNotBlank() }
                    is JsonObject -> urlValue["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    else -> null
                } ?: return@mapNotNull null

                val headers = (item["headers"] as? JsonObject)
                    ?.mapNotNull { (key, value) ->
                        value.jsonPrimitive.contentOrNull?.let { key to it }
                    }
                    ?.toMap()
                    ?.takeIf { it.isNotEmpty() }

                val subtitles = (item["subtitles"] as? JsonArray)?.mapNotNull { subElement ->
                    val subObj = subElement as? JsonObject ?: return@mapNotNull null
                    val subUrl = subObj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val subLang = subObj["language"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                    val subName = subObj["name"]?.jsonPrimitive?.contentOrNull
                    val subHeaders = (subObj["headers"] as? JsonObject)
                        ?.mapNotNull { (key, value) ->
                            value.jsonPrimitive.contentOrNull?.let { key to it }
                        }
                        ?.toMap()
                        ?.takeIf { it.isNotEmpty() }
                    com.nuvio.app.features.plugins.PluginSubtitleResult(
                        url = subUrl,
                        language = subLang,
                        name = subName,
                        headers = subHeaders
                    )
                }?.takeIf { it.isNotEmpty() }

                PluginRuntimeResult(
                    title = item.stringOrNull("title") ?: item.stringOrNull("name") ?: runBlocking { getString(Res.string.generic_unknown) },
                    name = item.stringOrNull("name"),
                    url = url,
                    quality = item.stringOrNull("quality"),
                    size = item.stringOrNull("size"),
                    language = item.stringOrNull("language"),
                    provider = item.stringOrNull("provider"),
                    server = item.stringOrNull("server") ?: item.stringOrNull("serverName"),
                    type = item.stringOrNull("type"),
                    seeders = item["seeders"]?.jsonPrimitive?.intOrNull,
                    peers = item["peers"]?.jsonPrimitive?.intOrNull,
                    infoHash = item.stringOrNull("infoHash"),
                    headers = headers,
                    subtitles = subtitles,
                )
            }.filter { it.url.isNotBlank() }
        }.getOrElse { emptyList() }
    }

    private fun parseJsonHomeResults(
        rawJson: String,
        scraperId: String,
        scraperName: String,
    ): List<com.nuvio.app.features.plugins.PluginHomeSection> {
        return runCatching {
            val element = json.parseToJsonElement(rawJson)
            val array = element as? JsonArray ?: return emptyList()
            if (array.isEmpty()) return emptyList()

            val firstObj = array.firstOrNull() as? JsonObject
            if (firstObj != null && firstObj.containsKey("items")) {
                array.mapNotNull { itemElement ->
                    val sectionObj = itemElement as? JsonObject ?: return@mapNotNull null
                    val title = sectionObj["title"]?.jsonPrimitive?.contentOrNull ?: scraperName
                    val itemsArray = sectionObj["items"] as? JsonArray ?: JsonArray(emptyList())
                    val items = itemsArray.mapNotNull { it.toPluginHomeItem() }
                    com.nuvio.app.features.plugins.PluginHomeSection(
                        pluginId = scraperId,
                        pluginName = scraperName,
                        title = title,
                        items = items,
                    )
                }
            } else {
                val items = array.mapNotNull { it.toPluginHomeItem() }
                if (items.isEmpty()) emptyList()
                else listOf(
                    com.nuvio.app.features.plugins.PluginHomeSection(
                        pluginId = scraperId,
                        pluginName = scraperName,
                        title = scraperName,
                        items = items,
                    )
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parseJsonSearchResults(
        rawJson: String,
        scraperId: String,
        scraperName: String,
    ): List<com.nuvio.app.features.plugins.PluginHomeSection> {
        return runCatching {
            val element = json.parseToJsonElement(rawJson)
            val array = when (element) {
                is JsonArray -> element
                is JsonObject -> {
                    (element["results"] ?: element["items"] ?: element["anime"] ?: element["data"] ?: element["list"]) as? JsonArray
                        ?: JsonArray(emptyList())
                }
                else -> return emptyList()
            }
            if (array.isEmpty()) return emptyList()

            val firstObj = array.firstOrNull() as? JsonObject
            if (firstObj != null && firstObj.containsKey("items")) {
                array.mapNotNull { itemElement ->
                    val sectionObj = itemElement as? JsonObject ?: return@mapNotNull null
                    val title = sectionObj["title"]?.jsonPrimitive?.contentOrNull ?: scraperName
                    val itemsArray = sectionObj["items"] as? JsonArray ?: JsonArray(emptyList())
                    val items = itemsArray.mapNotNull { it.toPluginHomeItem() }
                    com.nuvio.app.features.plugins.PluginHomeSection(
                        pluginId = scraperId,
                        pluginName = scraperName,
                        title = title,
                        items = items,
                    )
                }.filter { it.items.isNotEmpty() }
            } else {
                val items = array.mapNotNull { it.toPluginHomeItem() }
                if (items.isEmpty()) emptyList()
                else listOf(
                    com.nuvio.app.features.plugins.PluginHomeSection(
                        pluginId = scraperId,
                        pluginName = scraperName,
                        title = scraperName,
                        items = items,
                    )
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JsonElement.toPluginHomeItem(): com.nuvio.app.features.plugins.PluginHomeItem? {
        val obj = this as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull
            ?: obj["session"]?.jsonPrimitive?.contentOrNull
            ?: obj["slug"]?.jsonPrimitive?.contentOrNull
            ?: obj["url"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        return com.nuvio.app.features.plugins.PluginHomeItem(
            id = id,
            title = title,
            poster = obj["poster"]?.jsonPrimitive?.contentOrNull ?: obj["image"]?.jsonPrimitive?.contentOrNull ?: obj["cover"]?.jsonPrimitive?.contentOrNull ?: obj["snapshot"]?.jsonPrimitive?.contentOrNull,
            banner = obj["banner"]?.jsonPrimitive?.contentOrNull ?: obj["backdrop"]?.jsonPrimitive?.contentOrNull,
            url = obj["url"]?.jsonPrimitive?.contentOrNull,
            description = obj["description"]?.jsonPrimitive?.contentOrNull ?: obj["synopsis"]?.jsonPrimitive?.contentOrNull,
            rating = obj["rating"]?.jsonPrimitive?.contentOrNull ?: obj["score"]?.jsonPrimitive?.contentOrNull,
            year = obj["year"]?.jsonPrimitive?.contentOrNull,
            episodes = obj["episodes"]?.jsonPrimitive?.intOrNull ?: obj["totalEpisodes"]?.jsonPrimitive?.intOrNull ?: obj["episodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: obj["totalEpisodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            subEpisodes = obj["subEpisodes"]?.jsonPrimitive?.intOrNull ?: obj["sub"]?.jsonPrimitive?.intOrNull ?: obj["subEpisodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: obj["sub"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            dubEpisodes = obj["dubEpisodes"]?.jsonPrimitive?.intOrNull ?: obj["dub"]?.jsonPrimitive?.intOrNull ?: obj["dubEpisodes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: obj["dub"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            ageRating = obj["ageRating"]?.jsonPrimitive?.contentOrNull ?: obj["rated"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun parseJsonDetailsResult(rawJson: String): com.nuvio.app.features.plugins.PluginDetailsResult? {
        if (rawJson.isBlank() || rawJson == "null" || rawJson == "undefined") return null
        return runCatching {
            val obj = json.parseToJsonElement(rawJson) as? JsonObject ?: return null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: obj["url"]?.jsonPrimitive?.contentOrNull
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: obj["name"]?.jsonPrimitive?.contentOrNull
            val poster = obj["poster"]?.jsonPrimitive?.contentOrNull ?: obj["image"]?.jsonPrimitive?.contentOrNull
            val banner = obj["banner"]?.jsonPrimitive?.contentOrNull ?: obj["backdrop"]?.jsonPrimitive?.contentOrNull
            val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: obj["synopsis"]?.jsonPrimitive?.contentOrNull
            val totalEpisodes = obj["totalEpisodes"]?.jsonPrimitive?.intOrNull ?: obj["episodes"]?.jsonPrimitive?.intOrNull
            val subEpisodes = obj["subEpisodes"]?.jsonPrimitive?.intOrNull ?: obj["sub"]?.jsonPrimitive?.intOrNull
            val dubEpisodes = obj["dubEpisodes"]?.jsonPrimitive?.intOrNull ?: obj["dub"]?.jsonPrimitive?.intOrNull
            val ageRating = obj["ageRating"]?.jsonPrimitive?.contentOrNull ?: obj["rated"]?.jsonPrimitive?.contentOrNull
            val status = obj["status"]?.jsonPrimitive?.contentOrNull
            val year = obj["year"]?.jsonPrimitive?.contentOrNull
            val genres = (obj["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

            val episodesArray = (obj["episodes"] as? JsonArray) ?: (obj["episodeList"] as? JsonArray)
            val episodes = episodesArray?.mapNotNull { epElement ->
                val epObj = epElement as? JsonObject ?: return@mapNotNull null
                val epNum = epObj["episode"]?.jsonPrimitive?.intOrNull ?: epObj["number"]?.jsonPrimitive?.intOrNull ?: 1
                val isFiller = epObj["isFiller"]?.jsonPrimitive?.booleanOrNull ?: (epObj["filler"]?.jsonPrimitive?.booleanOrNull ?: false)
                val isSub = epObj["isSub"]?.jsonPrimitive?.booleanOrNull ?: (epObj["sub"]?.jsonPrimitive?.booleanOrNull ?: true)
                val isDub = epObj["isDub"]?.jsonPrimitive?.booleanOrNull ?: (epObj["dub"]?.jsonPrimitive?.booleanOrNull ?: false)
                com.nuvio.app.features.plugins.PluginEpisodeItem(
                    id = epObj["id"]?.jsonPrimitive?.contentOrNull,
                    episode = epNum,
                    season = epObj["season"]?.jsonPrimitive?.intOrNull ?: 1,
                    title = epObj["title"]?.jsonPrimitive?.contentOrNull,
                    thumbnail = epObj["thumbnail"]?.jsonPrimitive?.contentOrNull ?: epObj["image"]?.jsonPrimitive?.contentOrNull,
                    overview = epObj["overview"]?.jsonPrimitive?.contentOrNull ?: epObj["description"]?.jsonPrimitive?.contentOrNull,
                    isFiller = isFiller,
                    isSub = isSub,
                    isDub = isDub,
                )
            }.orEmpty()

            val relatedArray = (obj["related"] as? JsonArray) ?: (obj["recommendations"] as? JsonArray)
            val related = relatedArray?.mapNotNull { it.toPluginHomeItem() }.orEmpty()

            com.nuvio.app.features.plugins.PluginDetailsResult(
                id = id,
                title = title,
                poster = poster,
                banner = banner,
                description = desc,
                totalEpisodes = totalEpisodes,
                subEpisodes = subEpisodes,
                dubEpisodes = dubEpisodes,
                ageRating = ageRating,
                status = status,
                genres = genres,
                year = year,
                episodes = episodes,
                related = related,
            )
        }.getOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && !it.contains("[object") }

    private suspend fun <T> runWhenSearchActive(block: suspend () -> T): T {
        while (true) {
            searchPaused.first { !it }
            try {
                return coroutineScope {
                    val watcher = launch {
                        searchPaused.first { it }
                        throw CancellationException(PAUSED_MESSAGE)
                    }
                    try {
                        block()
                    } finally {
                        watcher.cancel()
                    }
                }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (searchPaused.value || cancelled.message == PAUSED_MESSAGE) {
                    continue
                }
                throw cancelled
            }
        }
    }

    private const val PAUSED_MESSAGE = "plugin-search-paused"
}
