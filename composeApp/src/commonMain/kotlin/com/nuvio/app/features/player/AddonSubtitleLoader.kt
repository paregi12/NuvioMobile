package com.nuvio.app.features.player

@Serializable
data class SubtitleAddonRequest(
    val url: String,
    val addonId: String,
    val addonName: String,
)

internal fun addonSubtitleRequests(type: String, videoId: String): List<SubtitleAddonRequest> = emptyList()

internal suspend fun loadAddonSubtitles(
    requests: List<SubtitleAddonRequest>,
    onLoaded: (SubtitleAddonRequest, List<AddonSubtitle>) -> Unit = { _, _ -> },
): List<AddonSubtitle> = supervisorScope {
    requests.map { request ->
        async {
            val subtitles = try {
                withTimeoutOrNull(10_000L) {
                    parseAddonSubtitles(fetchAddonResponseText(request.url), request)
                }.orEmpty()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
            currentCoroutineContext().ensureActive()
            onLoaded(request, subtitles)
            subtitles
        }
    }.awaitAll().flatten()
}

private suspend fun parseAddonSubtitles(response: String, request: SubtitleAddonRequest): List<AddonSubtitle> {
    val subtitles = Json.parseToJsonElement(response).jsonObject["subtitles"]?.jsonArray.orEmpty()
    return subtitles.mapIndexedNotNull { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexedNotNull null
        val url = obj.stringValue("url") ?: return@mapIndexedNotNull null
        val language = listOf("lang", "language", "languageCode", "locale", "label")
            .firstNotNullOfOrNull(obj::stringValue) ?: "unknown"
        AddonSubtitle(
            id = obj.stringValue("id") ?: "${request.addonId}_$index",
            url = url,
            language = normalizeLanguageCode(language) ?: language,
            display = getString(
                Res.string.player_addon_subtitle_display_format,
                getLanguageLabelForCode(language),
                request.addonName,
            ),
            addonName = request.addonName,
        )
    }
}



private fun JsonObject.stringValue(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
