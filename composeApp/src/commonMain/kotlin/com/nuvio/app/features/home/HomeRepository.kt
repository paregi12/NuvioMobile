package com.nuvio.app.features.home

import com.nuvio.app.features.anilist.AnilistMetadataService
import com.nuvio.app.features.anilist.AnilistSettingsRepository
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.plugins.PluginRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object HomeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun refresh(force: Boolean = false) {
        val scrapers = PluginRepository.uiState.value.scrapers.filter { it.enabled && it.manifestEnabled }
        val anilistSettings = AnilistSettingsRepository.snapshot()

        if (scrapers.isEmpty() && (!anilistSettings.enabled || !anilistSettings.showHomeScreenCatalogs)) {
            activeJob?.cancel()
            activeJob = null
            _uiState.value = HomeUiState(
                isLoading = false,
                heroItems = emptyList(),
                sections = emptyList(),
                hasNoPlugins = true,
                errorMessage = null,
            )
            return
        }

        activeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null, hasNoPlugins = false) }
        activeJob = scope.launch {
            val pluginSectionsDeferred = async {
                runCatching {
                    PluginRepository.loadHomeSections()
                }.getOrElse { emptyList() }
            }

            val anilistTrendingDeferred = async {
                if (anilistSettings.enabled && anilistSettings.showHomeScreenCatalogs) {
                    runCatching {
                        AnilistMetadataService.fetchTrending()
                    }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }
            }

            val anilistPopularDeferred = async {
                if (anilistSettings.enabled && anilistSettings.showHomeScreenCatalogs) {
                    runCatching {
                        AnilistMetadataService.fetchPopular()
                    }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }
            }

            val pluginSections = pluginSectionsDeferred.await()
            val anilistTrending = anilistTrendingDeferred.await()
            val anilistPopular = anilistPopularDeferred.await()

            val convertedPluginSections = pluginSections.map { section ->
                HomeCatalogSection(
                    key = "plugin_${section.pluginId}_${section.title}",
                    title = section.title,
                    subtitle = section.pluginName,
                    addonName = section.pluginName,
                    target = CatalogTarget.Library(contentType = "anime", sectionType = section.title),
                    items = section.items.map { item ->
                        MetaPreview(
                            id = item.id,
                            type = "anime",
                            name = item.title,
                            poster = item.poster,
                            banner = item.banner,
                            description = item.description,
                            imdbRating = item.rating,
                            releaseInfo = item.year,
                            totalEpisodes = item.episodes,
                            subEpisodes = item.subEpisodes,
                            dubEpisodes = item.dubEpisodes,
                        )
                    },
                )
            }.filter { it.items.isNotEmpty() }

            val anilistSections = if (anilistSettings.enabled && anilistSettings.showHomeScreenCatalogs) {
                buildList {
                    if (anilistTrending.isNotEmpty()) {
                        add(
                            HomeCatalogSection(
                                key = "anilist_trending",
                                title = "Trending Now",
                                subtitle = "AniList",
                                addonName = "AniList",
                                target = CatalogTarget.Library(contentType = "anime", sectionType = "trending"),
                                items = anilistTrending,
                            )
                        )
                    }
                    if (anilistPopular.isNotEmpty()) {
                        add(
                            HomeCatalogSection(
                                key = "anilist_popular",
                                title = "All Time Popular",
                                subtitle = "AniList",
                                addonName = "AniList",
                                target = CatalogTarget.Library(contentType = "anime", sectionType = "popular"),
                                items = anilistPopular,
                            )
                        )
                    }
                }
            } else {
                emptyList()
            }

            val homeSections = convertedPluginSections + anilistSections
            val heroItems = homeSections.firstOrNull()?.items?.take(6) ?: emptyList()

            HomeCatalogSettingsRepository.syncCatalogs(pluginSections)

            _uiState.value = HomeUiState(
                isLoading = false,
                heroItems = heroItems,
                sections = homeSections,
                hasNoPlugins = scrapers.isEmpty(),
                errorMessage = null,
            )
        }
    }

    fun applyCurrentSettings() {
        refresh()
    }

    fun clear() {
        activeJob?.cancel()
        activeJob = null
        _uiState.value = HomeUiState()
    }
}
