package com.nuvio.app.features.plugins

internal fun pluginContentId(
    videoId: String,
    season: Int?,
    episode: Int?,
    parentMetaId: String? = null,
): String {
    val trimmedParent = parentMetaId?.trim().orEmpty()
    if (trimmedParent.isNotBlank()) {
        val cleanParent = if (season != null && episode != null) {
            trimmedParent.removeSuffix(":$season:$episode")
        } else {
            trimmedParent
        }
        val baseParent = cleanParent.substringBefore('/').ifBlank { trimmedParent }
        if (baseParent.isNotBlank()) return baseParent
    }

    val trimmed = videoId.trim()
    if (trimmed.isBlank()) return videoId

    val withoutEpisodeSuffix = if (season != null && episode != null) {
        trimmed.removeSuffix(":$season:$episode")
    } else {
        trimmed
    }

    return withoutEpisodeSuffix.substringBefore('/').ifBlank { trimmed }
}
