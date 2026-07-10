package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AniListSettingsRepository {
    private val log = Logger.withTag("AniListSettings")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(AniListSettingsUiState())
    val uiState: StateFlow<AniListSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        _uiState.value = AniListSettingsUiState()
        persist()
    }

    fun setEnableSync(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.enableSync == enabled) return
        _uiState.value = _uiState.value.copy(enableSync = enabled)
        persist()
    }

    fun setSyncWatching(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.syncWatching == enabled) return
        _uiState.value = _uiState.value.copy(syncWatching = enabled)
        persist()
    }

    fun setAutoSync(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.autoSync == enabled) return
        _uiState.value = _uiState.value.copy(autoSync = enabled)
        persist()
    }

    fun setSyncOnLaunch(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.syncOnLaunch == enabled) return
        _uiState.value = _uiState.value.copy(syncOnLaunch = enabled)
        persist()
    }

    fun updateLastSyncTimestamp(timestamp: Long) {
        ensureLoaded()
        if (_uiState.value.lastSyncTimestamp == timestamp) return
        _uiState.value = _uiState.value.copy(lastSyncTimestamp = timestamp)
        persist()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val payload = AniListStorage.loadSettingsPayload().orEmpty().trim()
        val loadedState = if (payload.isBlank()) {
            AniListSettingsUiState()
        } else {
            runCatching { json.decodeFromString<AniListSettingsUiState>(payload) }
                .getOrElse {
                    log.w { "Failed to parse AniList settings payload: ${it.message}" }
                    AniListSettingsUiState()
                }
        }
        _uiState.value = loadedState
    }

    private fun persist() {
        runCatching {
            val payload = json.encodeToString(_uiState.value)
            AniListStorage.saveSettingsPayload(payload)
        }.onFailure {
            log.w { "Failed to persist AniList settings state: ${it.message}" }
        }
    }
}
