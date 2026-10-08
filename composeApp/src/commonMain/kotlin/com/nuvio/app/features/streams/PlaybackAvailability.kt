package com.nuvio.app.features.streams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginsUiState

internal fun hasCompatiblePlaybackSource(
    plugins: PluginsUiState,
    type: String,
    videoId: String,
): Boolean = plugins.pluginsEnabled && plugins.scrapers.any { it.enabled && it.supportsType(type) }

internal class PlaybackAvailability(
    private val plugins: PluginsUiState,
) {
    fun canStream(type: String, videoId: String): Boolean =
        hasCompatiblePlaybackSource(plugins, type, videoId) ||
            MetaDetailsRepository.findEmbeddedStreams(videoId).isNotEmpty()

    fun canPlay(
        type: String,
        videoId: String,
        parentMetaId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): Boolean = canStream(type, videoId) || DownloadsRepository.findPlayableDownload(
        parentMetaId = parentMetaId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        videoId = videoId,
    ) != null

    companion object {
        fun current(): PlaybackAvailability = PlaybackAvailability(
            plugins = if (AppFeaturePolicy.pluginsEnabled) {
                PluginRepository.uiState.value
            } else {
                PluginsUiState(pluginsEnabled = false)
            },
        )
    }
}

@Composable
internal fun rememberPlaybackAvailability(): PlaybackAvailability {
    val plugins = if (AppFeaturePolicy.pluginsEnabled) {
        val state by remember {
            PluginRepository.initialize()
            PluginRepository.uiState
        }.collectAsStateWithLifecycle()
        state
    } else {
        PluginsUiState(pluginsEnabled = false)
    }
    val downloads by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    return remember(plugins, downloads) {
        PlaybackAvailability(plugins)
    }
}
