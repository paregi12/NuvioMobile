package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.nuvio.app.features.anilist.AnilistSettings
import com.nuvio.app.features.anilist.AnilistSettingsRepository

internal fun LazyListScope.anilistSettingsContent(
    isTablet: Boolean,
    settings: AnilistSettings,
) {
    val enrichmentEnabled = settings.enabled

    item {
        SettingsSection(
            title = "AniList Enrichment",
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Enable AniList Enrichment",
                    description = "Enrich anime with AniList metadata. When turned off, all details, episodes, and artwork are loaded purely from plugin sources.",
                    checked = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = AnilistSettingsRepository::setEnabled,
                )
            }
        }
    }

    item {
        SettingsSection(
            title = "Metadata Modules",
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Cast & Characters",
                    description = "Load character list and Japanese voice actors from AniList",
                    checked = settings.useCast,
                    enabled = enrichmentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AnilistSettingsRepository::setUseCast,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Trailers",
                    description = "Show official YouTube trailers from AniList",
                    checked = settings.useTrailers,
                    enabled = enrichmentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AnilistSettingsRepository::setUseTrailers,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Synopsis & Descriptions",
                    description = "Use clean AniList synopsis instead of source site descriptions",
                    checked = settings.useDescription,
                    enabled = enrichmentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AnilistSettingsRepository::setUseDescription,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Animation Studios",
                    description = "Display producing anime studios and browse studio works",
                    checked = settings.useStudios,
                    enabled = enrichmentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AnilistSettingsRepository::setUseStudios,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Recommendations & Relations",
                    description = "Show AniList community recommendations and anime relation trees",
                    checked = settings.useMoreLikeThis,
                    enabled = enrichmentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AnilistSettingsRepository::setUseMoreLikeThis,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Artwork & Posters",
                    description = "Use high-resolution AniList cover art and banners",
                    checked = settings.useArtwork,
                    enabled = enrichmentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AnilistSettingsRepository::setUseArtwork,
                )
            }
        }
    }
}
