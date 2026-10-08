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
            val result = AnilistMetadataService.searchAnime(normalizedQuery)
            result.onSuccess { previews ->
                if (previews.isEmpty()) {
                    _uiState.value = SearchUiState(
                        isLoading = false,
                        sections = emptyList(),
                        emptyStateReason = SearchEmptyStateReason.NoResults,
                    )
                } else {
                    val section = HomeCatalogSection(
                        key = "anilist_search",
                        title = "Anime",
                        subtitle = "AniList",
                        addonName = "AniList",
                        items = previews,
                    )
                    _uiState.value = SearchUiState(
                        isLoading = false,
                        sections = listOf(section),
                        emptyStateReason = null,
                    )
                }
            }.onFailure { err ->
                _uiState.value = SearchUiState(
                    isLoading = false,
                    emptyStateReason = SearchEmptyStateReason.RequestFailed,
                    errorMessage = err.message,
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
            val trending = AnilistMetadataService.fetchTrendingAnime().getOrElse { emptyList() }
            val popular = AnilistMetadataService.fetchPopularAnime().getOrElse { emptyList() }
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
