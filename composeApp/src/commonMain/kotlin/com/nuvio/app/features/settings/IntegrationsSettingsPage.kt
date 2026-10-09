package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_integrations_section_title
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.integrationsContent(
    isTablet: Boolean,
    onAnilistClick: () -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = "AniList",
                    description = "Enrich anime with AniList cast, trailers, synopses, and studios",
                    isTablet = isTablet,
                    onClick = onAnilistClick,
                )
            }
        }
    }
}
