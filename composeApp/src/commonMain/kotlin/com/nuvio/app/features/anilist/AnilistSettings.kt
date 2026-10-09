package com.nuvio.app.features.anilist

import com.nuvio.app.features.home.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class AnilistSettings(
    val enabled: Boolean = true,
    val showHomeScreenCatalogs: Boolean = true,
    val useCast: Boolean = true,
    val useTrailers: Boolean = true,
    val useDescription: Boolean = true,
    val useStudios: Boolean = true,
    val useMoreLikeThis: Boolean = true,
    val useArtwork: Boolean = true,
)

object AnilistSettingsRepository {
    private val _uiState = MutableStateFlow(AnilistSettings())
    val uiState: StateFlow<AnilistSettings> = _uiState.asStateFlow()

    fun snapshot(): AnilistSettings = _uiState.value

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
        HomeRepository.applyCurrentSettings()
    }

    fun setShowHomeScreenCatalogs(show: Boolean) {
        _uiState.update { it.copy(showHomeScreenCatalogs = show) }
        HomeRepository.applyCurrentSettings()
    }

    fun setUseCast(use: Boolean) {
        _uiState.update { it.copy(useCast = use) }
    }

    fun setUseTrailers(use: Boolean) {
        _uiState.update { it.copy(useTrailers = use) }
    }

    fun setUseDescription(use: Boolean) {
        _uiState.update { it.copy(useDescription = use) }
    }

    fun setUseStudios(use: Boolean) {
        _uiState.update { it.copy(useStudios = use) }
    }

    fun setUseMoreLikeThis(use: Boolean) {
        _uiState.update { it.copy(useMoreLikeThis = use) }
    }

    fun setUseArtwork(use: Boolean) {
        _uiState.update { it.copy(useArtwork = use) }
    }
}
