package com.nuvio.app.features.player

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleAddonRequest(
    val url: String = "",
    val addonId: String = "",
    val addonName: String = "",
)

internal fun addonSubtitleRequests(type: String, videoId: String): List<SubtitleAddonRequest> = emptyList()

internal suspend fun loadAddonSubtitles(
    requests: List<SubtitleAddonRequest>,
    onLoaded: (SubtitleAddonRequest, List<AddonSubtitle>) -> Unit = { _, _ -> },
): List<AddonSubtitle> = emptyList()
