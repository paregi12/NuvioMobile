package com.nuvio.app.features.streams

import com.nuvio.app.features.plugins.PluginScraper
import com.nuvio.app.features.plugins.PluginsUiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackAvailabilityTest {
    @Test
    fun `empty setup does not enable playback`() {
        assertFalse(available())
    }

    @Test
    fun `an enabled compatible plugin enables playback`() {
        val plugins = PluginsUiState(scrapers = listOf(scraper()))
        assertTrue(available(plugins = plugins))
        assertTrue(available(plugins = plugins, type = "series"))
        assertTrue(available(plugins = plugins, type = "anime"))
        assertFalse(available(plugins = plugins, type = "channel"))
        assertFalse(available(plugins = plugins.copy(pluginsEnabled = false)))
        assertFalse(available(plugins = plugins.copy(scrapers = listOf(scraper().copy(enabled = false)))))
    }

    @Test
    fun `a tv and movie plugin enables anime playback`() {
        val plugins = PluginsUiState(scrapers = listOf(scraper().copy(supportedTypes = listOf("movie", "tv"))))
        assertTrue(available(plugins = plugins, type = "anime"))
    }

    private fun available(
        plugins: PluginsUiState = PluginsUiState(),
        type: String = "movie",
        videoId: String = "tt123",
    ): Boolean = hasCompatiblePlaybackSource(plugins, type, videoId)

    private fun scraper(): PluginScraper = PluginScraper(
        id = "test",
        repositoryUrl = "https://example.com/plugins.json",
        name = "Test",
        description = "",
        version = "1.0.0",
        filename = "test.js",
        supportedTypes = listOf("movie", "tv", "series", "anime"),
        enabled = true,
        manifestEnabled = true,
        code = "",
    )
}
