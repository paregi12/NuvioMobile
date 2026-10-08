package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.features.watchprogress.WatchProgressEntry

@Composable
internal fun PlayerScreenModalHosts(
    onNextEpisodeAutoPlaySearchingChanged: (Boolean) -> Unit,
    onNextEpisodeAutoPlayCountdownChanged: (Int?) -> Unit,
    onNextEpisodeAutoPlaySourceNameChanged: (String?) -> Unit,
    showAudioModal: Boolean,
    audioTracks: List<AudioTrack>,
    selectedAudioIndex: Int,
    onAudioTrackSelected: (Int) -> Unit,
    onAudioModalDismissed: () -> Unit,
    showSubtitleModal: Boolean,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleIndex: Int,
    addonSubtitles: List<AddonSubtitle>,
    selectedAddonSubtitleId: String?,
    isLoadingAddonSubtitles: Boolean,
    subtitleStyle: SubtitleStyleState,
    subtitleDelayMs: Int,
    selectedAddonSubtitle: AddonSubtitle?,
    subtitleAutoSyncState: SubtitleAutoSyncUiState,
    onBuiltInSubtitleTrackSelected: (Int) -> Unit,
    onAddonSubtitleSelected: (AddonSubtitle) -> Unit,
    onFetchAddonSubtitles: () -> Unit,
    onSubtitleStyleChanged: (SubtitleStyleState) -> Unit,
    onSubtitleDelayChanged: (Int) -> Unit,
    onSubtitleDelayReset: () -> Unit,
    onAutoSyncCapture: () -> Unit,
    onAutoSyncCueSelected: (SubtitleSyncCue) -> Unit,
    onAutoSyncReload: () -> Unit,
    onSubtitleModalDismissed: () -> Unit,
    showVideoSettingsModal: Boolean,
    playerSettings: PlayerSettingsUiState,
    onVideoSettingsChanged: () -> Unit,
    onVideoSettingsModalDismissed: () -> Unit,
    showSourcesPanel: Boolean,
    sourceStreamsState: StreamsUiState,
    contentTitle: String,
    activeEpisodeTitle: String?,
    activeSourceUrl: String,
    activeStreamTitle: String,
    onSourceFilterSelected: (String?) -> Unit,
    onSourceStreamSelected: (StreamItem) -> Unit,
    onReloadSources: () -> Unit,
    onSourcesPanelDismissed: () -> Unit,
    isSeries: Boolean,
    showEpisodesPanel: Boolean,
    allEpisodes: List<MetaVideo>,
    parentMetaType: String,
    parentMetaId: String,
    activeSeasonNumber: Int?,
    activeEpisodeNumber: Int?,
    watchProgressByVideoId: Map<String, WatchProgressEntry>,
    watchedKeys: Set<String>,
    blurUnwatchedEpisodes: Boolean,
    episodeStreamsPanelState: EpisodeStreamsPanelState,
    episodeStreamsRepoState: StreamsUiState,
    onEpisodeSelectedForDownload: (MetaVideo) -> Boolean,
    onEpisodeStreamsRequested: (MetaVideo) -> Unit,
    onEpisodeStreamFilterSelected: (String?) -> Unit,
    onEpisodeStreamSelected: (StreamItem, MetaVideo) -> Unit,
    onBackToEpisodes: () -> Unit,
    onReloadEpisodeStreams: () -> Unit,
    onEpisodesPanelDismissed: () -> Unit,
) {
    AudioTrackModal(
        visible = showAudioModal,
        audioTracks = audioTracks,
        selectedIndex = selectedAudioIndex,
        onTrackSelected = onAudioTrackSelected,
        onDismiss = onAudioModalDismissed,
    )

    SubtitleModal(
        visible = showSubtitleModal,
        subtitleTracks = subtitleTracks,
        selectedSubtitleIndex = selectedSubtitleIndex,
        addonSubtitles = addonSubtitles,
        selectedAddonSubtitleId = selectedAddonSubtitleId,
        isLoadingAddonSubtitles = isLoadingAddonSubtitles,
        preferredSubtitleLanguage = playerSettings.preferredSubtitleLanguage,
        secondaryPreferredSubtitleLanguage = playerSettings.secondaryPreferredSubtitleLanguage,
        subtitleStyle = subtitleStyle,
        subtitleDelayMs = subtitleDelayMs,
        selectedAddonSubtitle = selectedAddonSubtitle,
        subtitleAutoSyncState = subtitleAutoSyncState,
        onBuiltInTrackSelected = onBuiltInSubtitleTrackSelected,
        onAddonSubtitleSelected = onAddonSubtitleSelected,
        onFetchAddonSubtitles = onFetchAddonSubtitles,
        onStyleChanged = onSubtitleStyleChanged,
        onSubtitleDelayChanged = onSubtitleDelayChanged,
        onSubtitleDelayReset = onSubtitleDelayReset,
        onAutoSyncCapture = onAutoSyncCapture,
        onAutoSyncCueSelected = onAutoSyncCueSelected,
        onAutoSyncReload = onAutoSyncReload,
        onDismiss = onSubtitleModalDismissed,
    )

    IosVideoSettingsModal(
        visible = showVideoSettingsModal,
        settings = playerSettings,
        onSettingsChanged = onVideoSettingsChanged,
        onDismiss = onVideoSettingsModalDismissed,
    )

    PlayerSourcesPanel(
        visible = showSourcesPanel,
        streamsUiState = sourceStreamsState,
        contentTitle = contentTitle,
        currentSeason = activeSeasonNumber,
        currentEpisode = activeEpisodeNumber,
        currentEpisodeTitle = activeEpisodeTitle,
        currentStreamUrl = activeSourceUrl,
        currentStreamName = activeStreamTitle,
        onFilterSelected = onSourceFilterSelected,
        onStreamSelected = onSourceStreamSelected,
        onReload = onReloadSources,
        onDismiss = onSourcesPanelDismissed,
    )

    if (isSeries) {
        PlayerEpisodesPanel(
            visible = showEpisodesPanel,
            episodes = allEpisodes,
            parentMetaType = parentMetaType,
            parentMetaId = parentMetaId,
            currentSeason = activeSeasonNumber,
            currentEpisode = activeEpisodeNumber,
            progressByVideoId = watchProgressByVideoId,
            watchedKeys = watchedKeys,
            blurUnwatchedEpisodes = blurUnwatchedEpisodes,
            episodeStreamsState = episodeStreamsPanelState.copy(
                streamsUiState = episodeStreamsRepoState,
            ),
            onSeasonSelected = { },
            onEpisodeSelected = { episode ->
                if (!onEpisodeSelectedForDownload(episode)) {
                    onEpisodeStreamsRequested(episode)
                }
            },
            onEpisodeStreamFilterSelected = onEpisodeStreamFilterSelected,
            onEpisodeStreamSelected = onEpisodeStreamSelected,
            onBackToEpisodes = onBackToEpisodes,
            onReloadEpisodeStreams = onReloadEpisodeStreams,
            onDismiss = onEpisodesPanelDismissed,
        )
    }
}

internal fun selectDownloadedEpisodeForPlayback(
    parentMetaId: String,
    episode: MetaVideo,
    onDownloadedEpisodeSelected: (com.nuvio.app.features.downloads.DownloadItem, MetaVideo) -> Unit,
): Boolean {
    val downloadedEpisode = DownloadsRepository.findPlayableDownload(
        parentMetaId = parentMetaId,
        seasonNumber = episode.season,
        episodeNumber = episode.episode,
        videoId = episode.id,
    )
    if (downloadedEpisode != null) {
        onDownloadedEpisodeSelected(downloadedEpisode, episode)
        return true
    }
    return false
}
