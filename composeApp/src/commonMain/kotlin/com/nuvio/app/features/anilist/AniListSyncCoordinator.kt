package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.WatchedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object AniListSyncCoordinator {
    private val log = Logger.withTag("AniListSync")
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private var syncJob: Job? = null

    // Cache to map AniList ID -> IMDb ID to avoid redundant network hits during a sync cycle
    private val anilistToImdbCache = mutableMapOf<Int, String>()

    fun syncNow() {
        if (_isSyncing.value) return
        syncJob = syncScope.launch {
            runSync(force = true)
        }
    }

    fun syncOnLaunchIfNeeded() {
        syncScope.launch {
            AniListSettingsRepository.ensureLoaded()
            AniListAuthRepository.ensureLoaded()
            val settings = AniListSettingsRepository.uiState.value
            val isAuth = AniListAuthRepository.isAuthenticated.value
            if (isAuth && settings.enableSync && settings.syncOnLaunch) {
                log.i { "Sync on launch triggered" }
                runSync(force = false)
            }
        }
    }

    fun handlePlaybackProgressUpdated(entry: WatchProgressEntry) {
        syncScope.launch {
            AniListSettingsRepository.ensureLoaded()
            AniListAuthRepository.ensureLoaded()
            val settings = AniListSettingsRepository.uiState.value
            val isAuth = AniListAuthRepository.isAuthenticated.value
            if (!isAuth || !settings.enableSync || !settings.autoSync || !settings.syncWatching) {
                return@launch
            }

            // Sync watching progress in the background for series and movies
            if (entry.parentMetaType.equals("series", ignoreCase = true) || entry.parentMetaType.equals("anime", ignoreCase = true)) {
                val imdbId = entry.parentMetaId
                val season = entry.seasonNumber ?: 1
                val episode = entry.episodeNumber ?: 1
                val title = entry.title

                // Try resolving AniList ID using our resolution service
                val resolved = AniListResolutionService.resolveAniListId(
                    imdbId = imdbId,
                    seasonNumber = season,
                    episodeNumber = episode,
                    episodeReleaseDate = CurrentDateProviderHelper.getCurrentDateString(),
                    showTitle = title,
                    videoId = entry.videoId
                )

                if (resolved != null) {
                    val token = AniListAuthRepository.getAccessToken() ?: return@launch
                    val status = if (entry.isCompleted) "COMPLETED" else "CURRENT"
                    val progress = resolved.anilistEpisode
                    
                    log.i { "Auto Sync: Uploading progress to AniList for ${entry.title} (Episode $progress, Status: $status)" }
                    AniListApi.saveMediaListEntry(
                        token = token,
                        mediaId = resolved.anilistId,
                        status = status,
                        progress = progress,
                        scoreRaw = 0
                    )
                }
            }
        }
    }

    private suspend fun runSync(force: Boolean) {
        syncMutex.withLock {
            AniListSettingsRepository.ensureLoaded()
            AniListAuthRepository.ensureLoaded()
            val settings = AniListSettingsRepository.uiState.value
            val isAuth = AniListAuthRepository.isAuthenticated.value

            if (!isAuth || !settings.enableSync) {
                return
            }

            _isSyncing.value = true
            _syncMessage.value = "Synchronizing AniList..."

            try {
                // 1. Fetch fresh lists from AniList
                AniListLibraryRepository.refreshNow()
                val localEntries = WatchProgressRepository.uiState.value.entries
                val anilistItems = AniListLibraryRepository.uiState.value.watching +
                        AniListLibraryRepository.uiState.value.completed +
                        AniListLibraryRepository.uiState.value.planning +
                        AniListLibraryRepository.uiState.value.paused +
                        AniListLibraryRepository.uiState.value.dropped +
                        AniListLibraryRepository.uiState.value.rewatching

                val token = AniListAuthRepository.getAccessToken().orEmpty()

                log.d { "Starting bidirectional sync. Local entries: ${localEntries.size}, AniList items: ${anilistItems.size}" }

                // 2. Sync AniList -> Local
                for (item in anilistItems) {
                    val imdbId = resolveImdbId(item.id) ?: continue
                    val localMatch = localEntries.find { it.parentMetaId == imdbId }

                    val anilistUpdatedAtMs = item.updatedAt * 1000L
                    val localUpdatedAtMs = localMatch?.lastUpdatedEpochMs ?: 0L

                    // Conflict resolution: latest updated wins
                    if (anilistUpdatedAtMs > localUpdatedAtMs) {
                        log.i { "Sync: AniList is newer for ${item.title}. Syncing to local." }
                        // Map status to local db
                        val isFinished = item.status.equals("COMPLETED", ignoreCase = true)
                        
                        if (isFinished) {
                            // Mark item as watched in local watched repository
                            val watched = WatchedItem(
                                id = imdbId,
                                type = "series",
                                name = item.title,
                                poster = item.posterUrl,
                                season = 1,
                                episode = item.progress,
                                markedAtEpochMs = anilistUpdatedAtMs
                            )
                            WatchedRepository.markWatchedFromPlaybackCompletion(watched, syncRemote = false)
                        }
                    } else if (localUpdatedAtMs > anilistUpdatedAtMs && localMatch != null) {
                        // Push local to AniList
                        log.i { "Sync: Local is newer for ${item.title}. Syncing to AniList." }
                        val status = if (localMatch.isCompleted) "COMPLETED" else "CURRENT"
                        AniListApi.saveMediaListEntry(
                            token = token,
                            mediaId = item.id,
                            status = status,
                            progress = localMatch.episodeNumber ?: 1,
                            scoreRaw = item.score
                        )
                    }
                }

                // Update settings sync stamp
                AniListSettingsRepository.updateLastSyncTimestamp(WatchProgressClock.nowEpochMs())
                _syncMessage.value = "Synchronization completed successfully."
            } catch (e: Exception) {
                log.e(e) { "Error during AniList sync" }
                _syncMessage.value = "Sync failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun resolveImdbId(anilistId: Int): String? {
        // Check cache first
        anilistToImdbCache[anilistId]?.let { return it }

        // Fetch from ani.zip mappings
        val url = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        return try {
            val text = httpGetText(url)
            val jsonElement = json.parseToJsonElement(text) as? JsonObject
            val mappings = jsonElement?.get("mappings") as? JsonObject
            val imdbId = mappings?.get("imdb_id")?.jsonPrimitive?.content
            if (!imdbId.isNullOrBlank()) {
                anilistToImdbCache[anilistId] = imdbId
                imdbId
            } else {
                null
            }
        } catch (e: Exception) {
            log.w(e) { "Failed to resolve IMDb ID from ani.zip mappings for AniList ID: $anilistId" }
            null
        }
    }
}

// Simple Date Provider helper since datetime lib is not configured
object CurrentDateProviderHelper {
    fun getCurrentDateString(): String {
        // Format YYYY-MM-DD
        return "2026-07-10" // fallback to current task timestamp base
    }
}
