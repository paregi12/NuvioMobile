package com.nuvio.app.features.home

import com.nuvio.app.features.plugins.PluginHomeSection
import com.nuvio.app.features.plugins.PluginScraper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeCatalogDefinitionTest {
    private val definition = HomeCatalogDefinition(
        key = "plugin:anime:popular",
        defaultTitle = "Popular - Anime",
        catalogName = "Popular",
        addonName = "AnimePlugin",
        type = "anime",
        catalogId = "popular",
        supportsPagination = false,
        descriptorSignature = "signature",
    )

    @Test
    fun `shows the type suffix by default`() {
        assertEquals("Popular - Anime", definition.titleFor(showCatalogType = true))
    }

    @Test
    fun `omits the type suffix when disabled`() {
        assertEquals("Popular", definition.titleFor(showCatalogType = false))
    }

    @Test
    fun `plugin refresh signature tracks enabled scrapers`() {
        val scraper1 = PluginScraper(
            id = "scraper1",
            name = "Scraper 1",
            version = "1.0.0",
            enabled = true,
            manifestEnabled = true,
        )
        val scraper2 = PluginScraper(
            id = "scraper2",
            name = "Scraper 2",
            version = "2.0.0",
            enabled = false,
            manifestEnabled = true,
        )

        val sig1 = buildPluginCatalogRefreshSignature(listOf(scraper1))
        val sig2 = buildPluginCatalogRefreshSignature(listOf(scraper1, scraper2))

        assertEquals(sig1, sig2)
        assertTrue(sig1.isNotEmpty())
    }

    @Test
    fun `builds plugin catalog definitions from sections`() {
        val sections = listOf(
            PluginHomeSection(
                pluginId = "plugin1",
                pluginName = "AnimeProvider",
                title = "Trending",
                items = emptyList(),
            ),
        )
        val defs = buildPluginCatalogDefinitions(sections)
        assertEquals(1, defs.size)
        assertEquals("Trending", defs[0].catalogName)
        assertEquals("AnimeProvider", defs[0].addonName)
    }
}
