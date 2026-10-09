package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.anilist.AnilistMetadataService
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktConnectionMode
import com.nuvio.app.features.trakt.TraktRelatedRepository
import com.nuvio.app.features.trakt.MoreLikeThisSourcePreference
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.trakt.shouldUseTraktMoreLikeThis
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

object MetaDetailsRepository {
    private data class CachedMetaEntry(
        val baseMeta: MetaDetails,
        val metaScreenMeta: MetaDetails? = null,
        val metaScreenSettingsFingerprint: String? = null,
    )

    private val log = Logger.withTag("MetaDetailsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(MetaDetailsUiState())
    val uiState: StateFlow<MetaDetailsUiState> = _uiState.asStateFlow()
    private var activeRequestKey: String? = null
    private val cachedMetaByRequestKey = mutableMapOf<String, CachedMetaEntry>()

    fun load(type: String, id: String) {
        log.d { "load() called — type=$type id=$id" }
        val requestKey = "$type:$id"
        val currentState = _uiState.value
        val mdbListSettings = MdbListSettingsRepository.snapshot()
        val metaScreenSettingsFingerprint = buildMetaScreenSettingsFingerprint(mdbListSettings)

        cachedMetaByRequestKey[requestKey]?.let { cachedEntry ->
            cachedEntry.metaScreenMeta
                ?.takeIf { cachedEntry.metaScreenSettingsFingerprint == metaScreenSettingsFingerprint }
                ?.let { cachedMeta ->
                    _uiState.value = MetaDetailsUiState(meta = cachedMeta.withUnreleasedFilter())
                    activeRequestKey = requestKey
                    return
                }

            val cachedBaseMeta = cachedEntry.baseMeta
            if (!shouldEnrichForMetaScreen(cachedBaseMeta, id, mdbListSettings)) {
                _uiState.value = MetaDetailsUiState(meta = cachedBaseMeta.withUnreleasedFilter())
                activeRequestKey = requestKey
                return
            }

            if (currentState.isLoading && activeRequestKey == requestKey) {
                log.d { "Meta screen enrichment already in flight — type=$type id=$id" }
                return
            }

            activeRequestKey = requestKey
            _uiState.value = MetaDetailsUiState(
                isLoading = true,
                meta = cachedBaseMeta,
            )

            scope.launch {
                val enrichedMeta = withContext(Dispatchers.Default) {
                    enrichForMetaScreen(
                        requestKey = requestKey,
                        meta = cachedBaseMeta,
                        fallbackItemId = id,
                        fallbackItemType = type,
                        settings = mdbListSettings,
                        settingsFingerprint = metaScreenSettingsFingerprint,
                    )
                }
                _uiState.value = MetaDetailsUiState(meta = enrichedMeta.withUnreleasedFilter())
                activeRequestKey = requestKey
            }
            return
        }

        if (currentState.meta?.type == type && currentState.meta.id == id && !currentState.isLoading) {
            log.d { "Skipping reload for cached meta — type=$type id=$id" }
            activeRequestKey = requestKey
            return
        }

        if (currentState.isLoading && activeRequestKey == requestKey) {
            log.d { "Request already in flight — type=$type id=$id" }
            return
        }

        activeRequestKey = requestKey
        _uiState.value = MetaDetailsUiState(isLoading = true)

        scope.launch {
            val anilistSettings = com.nuvio.app.features.anilist.AnilistSettingsRepository.snapshot()
            val pluginDetails = PluginRepository.getAnimeDetails(id)

            val loadedMeta = if (!anilistSettings.enabled) {
                pluginDetails?.toMetaDetails(id, type)
            } else {
                val anilistId = pluginDetails?.anilistId
                    ?: pluginDetails?.ids?.get("anilist")?.toIntOrNull()
                    ?: id.removePrefix("anilist:").trim().toIntOrNull()
                val malId = pluginDetails?.malId
                    ?: pluginDetails?.ids?.get("mal")?.toIntOrNull()

                var animeMeta = if (anilistId != null || malId != null) {
                    AnilistMetadataService.fetchAnimeDetails(id = anilistId, idMal = malId)
                } else {
                    fetchMetaFromAnilist(id = id, type = type)
                }

                if (animeMeta == null && pluginDetails?.title?.isNotBlank() == true) {
                    animeMeta = AnilistMetadataService.searchAnimeDetails(pluginDetails.title)
                }
                if (animeMeta != null) {
                    animeMeta = animeMeta.copy(
                        cast = if (anilistSettings.useCast) animeMeta.cast else emptyList(),
                        trailers = if (anilistSettings.useTrailers) animeMeta.trailers else emptyList(),
                        description = if (anilistSettings.useDescription) animeMeta.description else pluginDetails?.description,
                        productionCompanies = if (anilistSettings.useStudios) animeMeta.productionCompanies else emptyList(),
                        moreLikeThis = if (anilistSettings.useMoreLikeThis) animeMeta.moreLikeThis else emptyList(),
                        poster = if (anilistSettings.useArtwork) animeMeta.poster else pluginDetails?.poster,
                        background = if (anilistSettings.useArtwork) animeMeta.background else pluginDetails?.banner ?: pluginDetails?.poster,
                    )
                }

                if (pluginDetails != null) {
                    if (animeMeta == null) {
                        pluginDetails.toMetaDetails(id, type)
                    } else {
                        val fillerByEp = pluginDetails.episodes.associate { it.episode to it.isFiller }
                        val subByEp = pluginDetails.episodes.associate { it.episode to it.isSub }
                        val dubByEp = pluginDetails.episodes.associate { it.episode to it.isDub }
                        val enrichedVideos = animeMeta.videos.map { video ->
                            val epNum = video.episode ?: 0
                            video.copy(
                                isFiller = fillerByEp[epNum] ?: video.isFiller,
                                isSub = subByEp[epNum] ?: video.isSub,
                                isDub = dubByEp[epNum] ?: video.isDub,
                            )
                        }
                        animeMeta.copy(
                            totalEpisodes = pluginDetails.totalEpisodes ?: animeMeta.totalEpisodes,
                            subEpisodesCount = pluginDetails.subEpisodes ?: animeMeta.subEpisodesCount,
                            dubEpisodesCount = pluginDetails.dubEpisodes ?: animeMeta.dubEpisodesCount,
                            ageRating = pluginDetails.ageRating ?: animeMeta.ageRating,
                            videos = if (enrichedVideos.isNotEmpty()) enrichedVideos else animeMeta.videos,
                        )
                    }
                } else {
                    animeMeta
                }
            }

            if (loadedMeta != null) {
                publishLoadedMeta(
                    requestKey = requestKey,
                    meta = loadedMeta,
                    fallbackItemId = id,
                    fallbackItemType = type,
                    mdbListSettings = mdbListSettings,
                    metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
                )
                return@launch
            }

            log.w { "No metadata found for type=$type id=$id" }
            _uiState.value = MetaDetailsUiState(
                errorMessage = getString(Res.string.details_no_addon_meta),
            )
            activeRequestKey = null
        }
    }

    fun peek(type: String, id: String): MetaDetails? {
        val requestKey = "$type:$id"
        val currentMeta = _uiState.value.meta?.takeIf { it.type == type && it.id == id }
        if (currentMeta != null) return currentMeta

        val metaScreenSettingsFingerprint = buildMetaScreenSettingsFingerprint(MdbListSettingsRepository.snapshot())
        val cachedEntry = cachedMetaByRequestKey[requestKey] ?: return null
        val cachedMeta = cachedEntry.metaScreenMeta
            ?.takeIf { cachedEntry.metaScreenSettingsFingerprint == metaScreenSettingsFingerprint }
            ?: cachedEntry.baseMeta
        return cachedMeta.withUnreleasedFilter()
    }

    fun clear() {
        activeRequestKey = null
        cachedMetaByRequestKey.clear()
        _uiState.value = MetaDetailsUiState()
    }

    suspend fun fetch(type: String, id: String, cacheResult: Boolean = true): MetaDetails? {
        val requestKey = "$type:$id"
        cachedMetaByRequestKey[requestKey]?.let { return it.baseMeta }

        return fetchMetaFromAnilist(id = id, type = type)?.also { result ->
            if (cacheResult) {
                cachedMetaByRequestKey[requestKey] = CachedMetaEntry(baseMeta = result)
            }
        }
    }

    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val METADATA_PROVIDER_READY_TIMEOUT_MS = 10_000L
    private const val TMDB_ENRICH_TIMEOUT_MS = 5_000L
    private const val MDBLIST_ENRICH_TIMEOUT_MS = 5_000L

    private suspend fun fetchMetaFromAnilist(id: String, type: String): MetaDetails? {
        val numericId = id.removePrefix("anilist:").trim().toIntOrNull()
        if (numericId != null) {
            val details = AnilistMetadataService.fetchAnimeDetails(numericId)
            if (details != null) return details
        }
        val cleanSearch = id.removePrefix("anilist:")
            .removePrefix("kitsu:")
            .replace(Regex("[:/_-]"), " ")
            .trim()
        return AnilistMetadataService.searchAnimeDetails(cleanSearch)
    }

    private suspend fun publishLoadedMeta(
        requestKey: String,
        meta: MetaDetails,
        fallbackItemId: String,
        fallbackItemType: String,
        mdbListSettings: com.nuvio.app.features.mdblist.MdbListSettings,
        metaScreenSettingsFingerprint: String,
    ) {
        val cachedEntry = CachedMetaEntry(baseMeta = meta)
        cachedMetaByRequestKey[requestKey] = cachedEntry

        if (!shouldEnrichForMetaScreen(meta, fallbackItemId, mdbListSettings)) {
            _uiState.value = MetaDetailsUiState(meta = meta.withUnreleasedFilter())
            activeRequestKey = requestKey
            return
        }

        _uiState.value = MetaDetailsUiState(
            isLoading = true,
            meta = meta,
        )
        val enrichedMeta = withContext(Dispatchers.Default) {
            enrichForMetaScreen(
                requestKey = requestKey,
                meta = meta,
                fallbackItemId = fallbackItemId,
                fallbackItemType = fallbackItemType,
                settings = mdbListSettings,
                settingsFingerprint = metaScreenSettingsFingerprint,
            )
        }
        cachedMetaByRequestKey[requestKey] = cachedEntry.copy(
            metaScreenMeta = enrichedMeta,
            metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
        )
        _uiState.value = MetaDetailsUiState(meta = enrichedMeta.withUnreleasedFilter())
        activeRequestKey = requestKey
    }

    private suspend fun enrichForMetaScreen(
        requestKey: String,
        meta: MetaDetails,
        fallbackItemId: String,
        fallbackItemType: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
        settingsFingerprint: String,
    ): MetaDetails {
        val mdbListEnrichedMeta = withTimeoutOrNull(MDBLIST_ENRICH_TIMEOUT_MS) {
            MdbListMetadataService.enrichMeta(
                meta = meta,
                fallbackItemId = fallbackItemId,
                settings = settings,
            )
        } ?: meta
        val enrichedMeta = applyMoreLikeThisSource(
            meta = mdbListEnrichedMeta,
            fallbackItemId = fallbackItemId,
            fallbackItemType = fallbackItemType,
        )

        cachedMetaByRequestKey[requestKey] = cachedMetaByRequestKey[requestKey]
            ?.copy(
                metaScreenMeta = enrichedMeta,
                metaScreenSettingsFingerprint = settingsFingerprint,
            )
            ?: CachedMetaEntry(
                baseMeta = meta,
                metaScreenMeta = enrichedMeta,
                metaScreenSettingsFingerprint = settingsFingerprint,
            )

        return enrichedMeta
    }

    private suspend fun applyMoreLikeThisSource(
        meta: MetaDetails,
        fallbackItemId: String,
        fallbackItemType: String,
    ): MetaDetails {
        TrackingSettingsRepository.ensureLoaded()
        TraktAuthRepository.ensureLoaded()

        val trackingSettings = TrackingSettingsRepository.uiState.value
        val isTraktAuthenticated = TraktAuthRepository.uiState.value.mode == TraktConnectionMode.CONNECTED

        // Simkl source
        if (shouldUseSimklMoreLikeThis(trackingSettings.moreLikeThisSource) &&
            supportsMoreLikeThis(meta, fallbackItemType)
        ) {
            val items = runCatching {
                com.nuvio.app.features.simkl.SimklRelatedRepository.getRelated(
                    meta = meta,
                    fallbackItemId = fallbackItemId,
                    fallbackItemType = fallbackItemType,
                )
            }.onFailure { error ->
                log.w { "Failed to load Simkl related titles for ${meta.id}: ${error.message}" }
            }.getOrDefault(emptyList())

            return meta.copy(
                moreLikeThis = items,
                moreLikeThisSource = MoreLikeThisSource.SIMKL.takeIf { items.isNotEmpty() },
            )
        }

        // Trakt source
        val shouldUseTrakt = shouldUseTraktMoreLikeThis(
            isAuthenticated = isTraktAuthenticated,
            source = trackingSettings.moreLikeThisSource,
        ) && supportsMoreLikeThis(meta, fallbackItemType)

        if (shouldUseTrakt) {
            val items = runCatching {
                TraktRelatedRepository.getRelated(
                    meta = meta,
                    fallbackItemId = fallbackItemId,
                    fallbackItemType = fallbackItemType,
                )
            }.onFailure { error ->
                log.w { "Failed to load Trakt related titles for ${meta.id}: ${error.message}" }
            }.getOrDefault(emptyList())

            return meta.copy(
                moreLikeThis = items,
                moreLikeThisSource = MoreLikeThisSource.TRAKT.takeIf { items.isNotEmpty() },
            )
        }

        return meta.copy(
            moreLikeThisSource = if (meta.moreLikeThis.isNotEmpty()) MoreLikeThisSource.ANILIST else null,
        )
    }

    private fun shouldFetchMdbListOnMetaScreen(
        meta: MetaDetails,
        fallbackItemId: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): Boolean = MdbListMetadataService.shouldFetchForMeta(
        meta = meta,
        fallbackItemId = fallbackItemId,
        settings = settings,
    )

    private fun shouldEnrichForMetaScreen(
        meta: MetaDetails,
        fallbackItemId: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): Boolean {
        if (shouldFetchMdbListOnMetaScreen(meta, fallbackItemId, settings)) return true
        return shouldApplyMoreLikeThisSource(meta)
    }

    private fun shouldApplyMoreLikeThisSource(meta: MetaDetails): Boolean {
        TrackingSettingsRepository.ensureLoaded()
        TraktAuthRepository.ensureLoaded()

        val trackingSettings = TrackingSettingsRepository.uiState.value
        val isTraktAuthenticated = TraktAuthRepository.uiState.value.mode == TraktConnectionMode.CONNECTED
        return shouldUseTraktMoreLikeThis(
            isAuthenticated = isTraktAuthenticated,
            source = trackingSettings.moreLikeThisSource,
        ) || meta.moreLikeThisSource == null && meta.moreLikeThis.isNotEmpty()
    }

    private fun buildMetaScreenSettingsFingerprint(
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): String {
        TrackingSettingsRepository.ensureLoaded()
        TraktAuthRepository.ensureLoaded()
        val providers = settings.enabledProvidersInPriorityOrder().joinToString(",")
        val trackingSettings = TrackingSettingsRepository.uiState.value
        val traktAuthMode = TraktAuthRepository.uiState.value.mode
        return buildString {
            append("${settings.enabled}:${settings.apiKey.trim()}:$providers")
            append("|mdblist_account=${settings.accountScope.takeUnless { settings.hasApiKey }}")
            append("|more_like=${trackingSettings.moreLikeThisSource}:$traktAuthMode")
        }
    }

    private fun supportsMoreLikeThis(meta: MetaDetails, fallbackItemType: String): Boolean =
        normalizeMoreLikeThisType(meta.type) != null || normalizeMoreLikeThisType(fallbackItemType) != null

    private fun shouldUseSimklMoreLikeThis(source: MoreLikeThisSourcePreference): Boolean {
        if (source != MoreLikeThisSourcePreference.SIMKL) return false
        com.nuvio.app.features.simkl.SimklAuthRepository.ensureLoaded()
        return com.nuvio.app.features.simkl.SimklAuthRepository.isAuthenticated.value
    }

    private fun normalizeMoreLikeThisType(value: String?): String? =
        when (value?.trim()?.lowercase()) {
            "movie", "film" -> "movie"
            "series", "show", "tv", "tvshow" -> "series"
            else -> null
        }

    private fun MetaDetails.withUnreleasedFilter(): MetaDetails {
        val posterPattern = com.nuvio.app.core.poster.CustomPosterUrlRepository.let {
            it.ensureLoaded()
            it.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.DETAILS)
        }
        val base = withCustomPosterUrls(posterPattern)
        if (!HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) return base
        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        val releasedMoreLikeThis = base.moreLikeThis.filterReleasedItems(todayIsoDate)
        return base.copy(
            moreLikeThis = releasedMoreLikeThis,
            moreLikeThisSource = base.moreLikeThisSource.takeIf { releasedMoreLikeThis.isNotEmpty() },
            collectionItems = base.collectionItems.filterReleasedItems(todayIsoDate),
        )
    }

   
    fun findEmbeddedStreams(videoId: String): List<com.nuvio.app.features.streams.StreamItem> {
        val meta = _uiState.value.meta ?: return emptyList()
        val videosWithStreams = meta.videos.filter { it.streams.isNotEmpty() }
        if (videosWithStreams.isEmpty()) return emptyList()

        val directMatch = videosWithStreams.firstOrNull { it.id == videoId }
        if (directMatch != null) return directMatch.streams

        val parts = videoId.split(":")
        if (parts.size >= 3) {
            val season = parts[parts.size - 2].toIntOrNull()
            val episode = parts[parts.size - 1].toIntOrNull()
            if (season != null && episode != null) {
                val episodeMatch = videosWithStreams.firstOrNull { it.season == season && it.episode == episode }
                if (episodeMatch != null) return episodeMatch.streams
            }
        }

        val prefixMatch = videosWithStreams.firstOrNull { it.id.startsWith("$videoId:") }
        if (prefixMatch != null) return prefixMatch.streams

        if (videoId == meta.id && videosWithStreams.size == 1) {
            return videosWithStreams.first().streams
        }

        if (videoId == meta.id && videosWithStreams.isNotEmpty()) {
            return videosWithStreams.flatMap { it.streams }
        }

        return emptyList()
    }

    private fun com.nuvio.app.features.plugins.PluginDetailsResult.toMetaDetails(id: String, type: String): MetaDetails {
        val displayTitle = title?.takeIf { it.isNotBlank() } ?: id
        val videoList = episodes.map { ep ->
            MetaVideo(
                id = ep.id ?: "$id:${ep.season}:${ep.episode}",
                title = ep.title ?: "Episode ${ep.episode}",
                thumbnail = ep.thumbnail ?: poster,
                season = ep.season,
                episode = ep.episode,
                overview = ep.overview,
                isFiller = ep.isFiller,
                isSub = ep.isSub,
                isDub = ep.isDub,
            )
        }.ifEmpty {
            val total = totalEpisodes ?: 1
            (1..total).map { epNum ->
                MetaVideo(
                    id = "$id:1:$epNum",
                    title = "Episode $epNum",
                    thumbnail = poster,
                    season = 1,
                    episode = epNum,
                    overview = null,
                )
            }
        }

        val relatedPreviews = related.map { r ->
            MetaPreview(
                id = r.id,
                type = "anime",
                name = r.title,
                poster = r.poster,
                banner = r.banner,
                description = r.description,
                imdbRating = r.rating,
                releaseInfo = r.year,
                totalEpisodes = r.episodes,
                subEpisodes = r.subEpisodes,
                dubEpisodes = r.dubEpisodes,
            )
        }

        return MetaDetails(
            id = id,
            type = type,
            name = displayTitle,
            poster = poster,
            background = banner ?: poster,
            description = description,
            releaseInfo = year,
            status = status,
            ageRating = ageRating,
            genres = genres,
            totalEpisodes = totalEpisodes,
            subEpisodesCount = subEpisodes,
            dubEpisodesCount = dubEpisodes,
            moreLikeThis = relatedPreviews,
            moreLikeThisSource = MoreLikeThisSource.ANILIST,
            videos = videoList,
        )
    }
}
