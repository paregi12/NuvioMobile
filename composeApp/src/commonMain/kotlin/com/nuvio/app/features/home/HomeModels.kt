package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val posterShape: PosterShape = PosterShape.Poster,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rawReleaseDate: String? = null,
    val popularity: Double? = null,
    val voteCount: Int? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val rawPosterUrl: String? = null,
    val landscapePoster: String? = null,
    val rawLandscapePosterUrl: String? = null,
    val totalEpisodes: Int? = null,
    val subEpisodes: Int? = null,
    val dubEpisodes: Int? = null,
)

fun MetaPreview.stableKey(): String = "$type:$id"

enum class PosterShape {
    Poster,
    Square,
    Landscape,
}

data class HomeCatalogSection(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val addonName: String = "",
    val target: CatalogTarget? = null,
    val items: List<MetaPreview>,
    val availableItemCount: Int = items.size,
    val hasMore: Boolean = false,
)

fun HomeCatalogSection.canOpenCatalog(previewLimit: Int): Boolean =
    availableItemCount > previewLimit || hasMore

data class HomeUiState(
    val isLoading: Boolean = false,
    val heroItems: List<MetaPreview> = emptyList(),
    val sections: List<HomeCatalogSection> = emptyList(),
    val hasNoPlugins: Boolean = false,
    val errorMessage: String? = null,
)

internal fun shouldShowInitialHomeLoading(
    hasRenderableHomeRows: Boolean,
    homeCatalogLoading: Boolean,
): Boolean = !hasRenderableHomeRows && homeCatalogLoading

internal fun shouldShowHomeHeroSlot(
    heroEnabled: Boolean,
    hasHeroItems: Boolean,
    isResolvingHeroSources: Boolean,
    hasRenderableHomeRows: Boolean,
): Boolean = heroEnabled && (hasHeroItems || isResolvingHeroSources || hasRenderableHomeRows)
