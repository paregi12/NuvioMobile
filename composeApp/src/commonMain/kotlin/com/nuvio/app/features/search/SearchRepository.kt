package com.nuvio.app.features.search

import co.touchlab.kermit.Logger
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.nuvio.app.features.anilist.AnilistMetadataService
import com.nuvio.app.features.plugins.PluginRepository
import kotlinx.coroutines.launch

object SearchRepository {
    private val log = Logger.withTag("SearchRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _discoverUiState = MutableStateFlow(DiscoverUiState())
    val discoverUiState: StateFlow<DiscoverUiState> = _discoverUiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeDiscoverJob: Job? = null

    fun search(
        query: String,
        forceRefresh: Boolean = false,
    ) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            clear()
            return
        }
        activeJob?.cancel()
        _uiState.value = SearchUiState(isLoading = true)
        activeJob = scope.launch {
            val pluginSections = runCatching {
                PluginRepository.search(normalizedQuery)
            }.getOrElse { emptyList() }

            val pluginCatalogSections = pluginSections.map { section ->
                HomeCatalogSection(
                    key = "plugin_${section.pluginId}_search",
                    title = section.title.ifBlank { section.pluginName },
                    subtitle = section.pluginName,
                    addonName = section.pluginName,
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

            if (pluginCatalogSections.isEmpty()) {
                _uiState.value = SearchUiState(
                    isLoading = false,
                    sections = emptyList(),
                    emptyStateReason = SearchEmptyStateReason.NoResults,
                )
            } else {
                _uiState.value = SearchUiState(
                    isLoading = false,
                    sections = pluginCatalogSections,
                    emptyStateReason = null,
                )
            }
        }
    }

    fun refreshDiscover(
        forceRefresh: Boolean = false,
    ) {
        activeDiscoverJob?.cancel()
        _discoverUiState.value = DiscoverUiState(isLoading = true)
        activeDiscoverJob = scope.launch {
            val trending = runCatching { AnilistMetadataService.fetchTrending() }.getOrElse { emptyList() }
            val popular = runCatching { AnilistMetadataService.fetchPopular() }.getOrElse { emptyList() }
            val combined = (trending + popular).distinctBy { it.id }
            _discoverUiState.value = DiscoverUiState(
                isLoading = false,
                items = combined,
                emptyStateReason = if (combined.isEmpty()) DiscoverEmptyStateReason.NoResults else null,
            )
        }
    }

    fun selectDiscoverType(type: String) {
        _discoverUiState.value = _discoverUiState.value.copy(selectedType = type)
    }

    fun selectDiscoverCatalog(catalogKey: String) {
        _discoverUiState.value = _discoverUiState.value.copy(selectedCatalogKey = catalogKey)
    }

    fun selectDiscoverGenre(genre: String?) {
        _discoverUiState.value = _discoverUiState.value.copy(selectedGenre = genre)
    }

    fun loadMoreDiscover() {
    }

    fun clear() {
        activeJob?.cancel()
        _uiState.value = SearchUiState()
    }

    fun reset() {
        activeJob?.cancel()
        activeDiscoverJob?.cancel()
        _uiState.value = SearchUiState()
        _discoverUiState.value = DiscoverUiState()
    }
}
