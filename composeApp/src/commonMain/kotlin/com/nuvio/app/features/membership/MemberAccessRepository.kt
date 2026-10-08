package com.nuvio.app.features.membership

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MemberAccessRepository {
    private val log = Logger.withTag("MemberAccessRepository")
    private val _access = MutableStateFlow(MemberAccess.All)
    val access: StateFlow<MemberAccess> = _access.asStateFlow()
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true
        _access.value = MemberAccess.All
        ProfileBackgroundRepository.ensureLoaded()
    }

    fun refresh() {}

    fun refreshIfStale() {}

    fun clearLocalState() {
        _access.value = MemberAccess.All
    }
}
