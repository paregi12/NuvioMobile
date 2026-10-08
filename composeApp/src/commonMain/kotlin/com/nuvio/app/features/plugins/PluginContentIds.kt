package com.nuvio.app.features.plugins

internal fun pluginContentId(
    videoId: String,
    season: Int?,
    episode: Int?,
): String {
    val trimmed = videoId.trim()
    if (trimmed.isBlank()) return videoId

    val withoutEpisodeSuffix = if (season != null && episode != null) {
        trimmed.removeSuffix(":$season:$episode")
    } else {
        trimmed
    }

    return withoutEpisodeSuffix.substringBefore('/').ifBlank { trimmed }
}
