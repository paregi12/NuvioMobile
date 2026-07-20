package com.nuvio.app.features.watching.application

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.episodePlaybackId
import com.nuvio.app.features.watched.releasedMainSeasonEpisodes
import com.nuvio.app.features.watched.toEpisodeWatchedItem
import com.nuvio.app.features.watched.toSeriesWatchedItem
import com.nuvio.app.features.watched.toWatchedItem
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WatchingActions {
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun togglePosterWatched(preview: MetaPreview) {
        if (!preview.type.isSeriesLikeType()) {
            WatchedRepository.toggleWatched(preview.toWatchedItem(markedAtEpochMs = 0L))
            return
        }

        val isCurrentlyWatched = WatchedRepository.isWatched(
            id = preview.id,
            type = preview.type,
        )
        val meta = MetaDetailsRepository.fetch(type = preview.type, id = preview.id)
        if (meta == null) {
            if (isCurrentlyWatched) {
                WatchedRepository.unmarkWatched(preview.toWatchedItem(markedAtEpochMs = 0L))
                WatchedRepository.updateFullyWatchedSeries(
                    id = preview.id,
                    type = preview.type,
                    isFullyWatched = false,
                )
            }
            return
        }

        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        val releasedMainEpisodes = meta.releasedMainSeasonEpisodes(todayIsoDate)
        if (releasedMainEpisodes.isEmpty()) {
            if (isCurrentlyWatched) {
                WatchedRepository.unmarkWatched(meta.toSeriesWatchedItem())
                WatchedRepository.updateFullyWatchedSeries(
                    id = meta.id,
                    type = meta.type,
                    isFullyWatched = false,
                )
            }
            return
        }
        val seriesItems = buildList {
            add(meta.toSeriesWatchedItem())
            addAll(releasedMainEpisodes.map(meta::toEpisodeWatchedItem))
        }

        if (isCurrentlyWatched) {
            WatchedRepository.unmarkWatched(seriesItems)
            WatchProgressRepository.clearProgress(
                videoIds = releasedMainEpisodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
            WatchedRepository.updateFullyWatchedSeries(
                id = meta.id,
                type = meta.type,
                isFullyWatched = false,
            )
        } else {
            WatchedRepository.markWatched(seriesItems)
            WatchedRepository.updateFullyWatchedSeries(
                id = meta.id,
                type = meta.type,
                isFullyWatched = true,
            )
            WatchProgressRepository.clearProgress(
                videoIds = releasedMainEpisodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        }
    }

    fun toggleEpisodeWatched(
        meta: MetaDetails,
        episode: MetaVideo,
        isCurrentlyWatched: Boolean,
    ) {
        val watchedItem = meta.toEpisodeWatchedItem(episode)
        if (isCurrentlyWatched) {
            WatchedRepository.unmarkWatched(watchedItem)
            WatchProgressRepository.clearProgress(
                videoId = meta.episodePlaybackId(episode),
                parentMetaId = meta.id,
            )
        } else {
            WatchedRepository.markWatched(watchedItem)
            WatchProgressRepository.clearProgress(
                videoId = meta.episodePlaybackId(episode),
                parentMetaId = meta.id,
            )
            syncEpisodeToAniList(meta, episode)
        }
        reconcileSeriesWatchedState(meta)
    }

    fun togglePreviousEpisodesWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        toggleEpisodesWatched(
            meta = meta,
            episodes = episodes,
            areCurrentlyWatched = areCurrentlyWatched,
        )
    }

    fun toggleSeasonWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        toggleEpisodesWatched(
            meta = meta,
            episodes = episodes,
            areCurrentlyWatched = areCurrentlyWatched,
        )
        if (!areCurrentlyWatched) {
            syncSeasonToAniList(meta, episodes)
        }
    }

    private fun syncEpisodeToAniList(meta: MetaDetails, episode: MetaVideo) {
        actionScope.launch {
            val token = com.nuvio.app.features.anilist.AniListAuthRepository.getAccessToken() ?: return@launch
            val settings = com.nuvio.app.features.anilist.AniListSettingsRepository.uiState.value

            var directAniListId: Int? = null
            var directEpisode: Int? = null

            if (episode.id.startsWith("anilist:", ignoreCase = true)) {
                val parts = episode.id.split(":")
                directAniListId = parts.getOrNull(1)?.toIntOrNull()
                directEpisode = parts.getOrNull(2)?.toIntOrNull()
            }

            if (directAniListId == null && meta.id.startsWith("anilist:", ignoreCase = true)) {
                val parts = meta.id.split(":")
                directAniListId = parts.getOrNull(1)?.toIntOrNull()
                directEpisode = parts.getOrNull(2)?.toIntOrNull()
            }

            if (directAniListId != null) {
                // Guard: skip if not in library and auto-add is off
                if (!settings.autoAddNewAnime &&
                    !com.nuvio.app.features.anilist.AniListLibraryRepository.isInLibrary(directAniListId)
                ) return@launch

                val epNum = directEpisode ?: episode.episode ?: 1
                com.nuvio.app.features.anilist.AniListApi.saveMediaListEntry(
                    token = token,
                    mediaId = directAniListId,
                    status = "CURRENT",
                    progress = epNum,
                    scoreRaw = null
                )
            } else {
                val resolved = com.nuvio.app.features.anilist.AniListResolutionService.resolveAniListId(
                    imdbId = meta.id,
                    seasonNumber = episode.season ?: 1,
                    episodeNumber = episode.episode ?: 1,
                    episodeReleaseDate = episode.released,
                    showTitle = meta.name,
                    videoId = episode.id,
                    isMovie = meta.type.equals("movie", ignoreCase = true)
                )
                if (resolved != null) {
                    // Guard: skip if not in library and auto-add is off
                    if (!settings.autoAddNewAnime &&
                        !com.nuvio.app.features.anilist.AniListLibraryRepository.isInLibrary(resolved.anilistId)
                    ) return@launch

                    com.nuvio.app.features.anilist.AniListApi.saveMediaListEntry(
                        token = token,
                        mediaId = resolved.anilistId,
                        status = "CURRENT",
                        progress = resolved.anilistEpisode,
                        scoreRaw = null
                    )
                }
            }
        }
    }

    private fun syncSeasonToAniList(meta: MetaDetails, episodes: Collection<MetaVideo>) {
        actionScope.launch {
            val token = com.nuvio.app.features.anilist.AniListAuthRepository.getAccessToken() ?: return@launch

            var directAniListId: Int? = null

            if (meta.id.startsWith("anilist:", ignoreCase = true)) {
                val parts = meta.id.split(":")
                directAniListId = parts.getOrNull(1)?.toIntOrNull()
            }

            if (directAniListId == null) {
                for (ep in episodes) {
                    if (ep.id.startsWith("anilist:", ignoreCase = true)) {
                        val parts = ep.id.split(":")
                        directAniListId = parts.getOrNull(1)?.toIntOrNull()
                        if (directAniListId != null) break
                    }
                }
            }

            if (directAniListId != null) {
                val totalEpisodeCount = episodes.size
                com.nuvio.app.features.anilist.AniListApi.saveMediaListEntry(
                    token = token,
                    mediaId = directAniListId,
                    status = "COMPLETED",
                    progress = totalEpisodeCount,
                    scoreRaw = null
                )
            }
        }
    }

    fun reconcileSeriesWatchedState(
        meta: MetaDetails,
        todayIsoDate: String = CurrentDateProvider.todayIsoDate(),
    ) {
        if (!meta.type.isSeriesLikeType()) return

        WatchedRepository.reconcileSeriesWatchedState(
            meta = meta,
            todayIsoDate = todayIsoDate,
            isEpisodeCompleted = { episode ->
                WatchProgressRepository.progressForVideo(
                    videoId = meta.episodePlaybackId(episode),
                    parentMetaId = meta.id,
                    seasonNumber = episode.season,
                    episodeNumber = episode.episode,
                )?.isCompleted == true
            },
        )
    }

    fun onProgressEntryUpdated(entry: WatchProgressEntry, syncRemote: Boolean = true) {
        if (!entry.isCompleted) return

        val watchedItem = WatchedItem(
            id = entry.parentMetaId,
            type = entry.parentMetaType,
            name = entry.title,
            poster = entry.poster,
            season = entry.seasonNumber,
            episode = entry.episodeNumber,
            markedAtEpochMs = entry.lastUpdatedEpochMs,
        )
        WatchedRepository.markWatchedFromPlaybackCompletion(watchedItem, syncRemote = syncRemote)

        if (!syncRemote || !entry.isEpisode) return
        actionScope.launch {
            val meta = runCatching {
                MetaDetailsRepository.fetch(
                    type = entry.parentMetaType,
                    id = entry.parentMetaId,
                )
            }.getOrNull() ?: return@launch

            reconcileSeriesWatchedState(meta = meta)
        }
    }

    private fun toggleEpisodesWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        if (episodes.isEmpty()) return
        val watchedItems = episodes.map(meta::toEpisodeWatchedItem)
        if (areCurrentlyWatched) {
            WatchedRepository.unmarkWatched(watchedItems)
            WatchProgressRepository.clearProgress(
                videoIds = episodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        } else {
            WatchedRepository.markWatched(watchedItems)
            WatchProgressRepository.clearProgress(
                videoIds = episodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        }
        reconcileSeriesWatchedState(meta)
    }
}

private fun String.isSeriesLikeType(): Boolean =
    trim().lowercase() in setOf("series", "show", "tv", "tvshow")
