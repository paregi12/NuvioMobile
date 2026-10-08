package com.nuvio.app.features.details

import com.nuvio.app.features.home.MetaPreview

enum class EntityKind(val routeValue: String) {
    COMPANY("company"),
    NETWORK("network");

    companion object {
        fun fromRouteValue(value: String): EntityKind = when (value.trim().lowercase()) {
            "network" -> NETWORK
            else -> COMPANY
        }
    }
}

enum class EntityMediaType(val value: String) {
    MOVIE("movie"),
    TV("tv"),
}

enum class EntityRailType(val value: String) {
    POPULAR("popular"),
    TOP_RATED("top_rated"),
    RECENT("recent"),
}

data class EntityHeader(
    val id: Int,
    val kind: EntityKind,
    val name: String,
    val logo: String?,
    val originCountry: String?,
    val secondaryLabel: String?,
    val description: String?,
)

data class EntityRail(
    val mediaType: EntityMediaType,
    val railType: EntityRailType,
    val items: List<MetaPreview>,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
)

data class EntityBrowseData(
    val header: EntityHeader,
    val rails: List<EntityRail>,
)

data class EntityRailPageResult(
    val items: List<MetaPreview>,
    val hasMore: Boolean,
)
