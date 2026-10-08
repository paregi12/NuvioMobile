package com.nuvio.app.features.home

import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.features.plugins.PluginHomeSection
import com.nuvio.app.features.plugins.PluginScraper

data class HomeCatalogDefinition(
    val key: String,
    val defaultTitle: String,
    val catalogName: String,
    val addonName: String,
    val manifestUrl: String = "",
    val type: String = "anime",
    val catalogId: String = "",
    val supportsPagination: Boolean = false,
    val descriptorSignature: String = "",
) {
    val cacheKey: String
        get() = "$key|$descriptorSignature"

    fun titleFor(showCatalogType: Boolean): String =
        if (showCatalogType) defaultTitle else catalogName
}

fun buildPluginCatalogRefreshSignature(scrapers: List<PluginScraper>): List<String> =
    scrapers.filter { it.enabled && it.manifestEnabled }.map { scraper ->
        "${scraper.id}:${scraper.version}:${scraper.enabled}"
    }.sorted()

fun buildPluginCatalogDefinitions(sections: List<PluginHomeSection>): List<HomeCatalogDefinition> =
    sections.map { section ->
        HomeCatalogDefinition(
            key = "${section.pluginId}:${section.title}",
            defaultTitle = section.title,
            catalogName = section.title,
            addonName = section.pluginName,
            manifestUrl = "",
            type = "anime",
            catalogId = section.title,
            supportsPagination = false,
            descriptorSignature = "${section.pluginId}:${section.title}",
        )
    }.distinctBy(HomeCatalogDefinition::key)

internal fun String.displayLabel(): String = localizedMediaTypeLabel(this)
