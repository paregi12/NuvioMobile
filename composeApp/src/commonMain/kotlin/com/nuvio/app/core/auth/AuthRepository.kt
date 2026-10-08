package com.nuvio.app.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthRepository {
    private const val defaultUserId = "local"

    private val currentUserId: String
        get() = AuthStorage.loadAnonymousUserId() ?: defaultUserId.also {
            AuthStorage.saveAnonymousUserId(it)
        }

    private val _state = MutableStateFlow<AuthState>(
        AuthState.Authenticated(
            userId = currentUserId,
            email = null,
            isAnonymous = true,
        ),
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun initialize() {
        _state.value = AuthState.Authenticated(
            userId = currentUserId,
            email = null,
            isAnonymous = true,
        )
    }

    fun signInAnonymously() {
        initialize()
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = Result.success(Unit)

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = Result.success(Unit)

    suspend fun signOut(): Result<Unit> = Result.success(Unit)

    suspend fun prepareForServerSwitch(): Result<Unit> = Result.success(Unit)

    fun reinitialize() {
        initialize()
    }

    suspend fun signOutIfSessionInvalid(error: Throwable, source: String): Boolean = false

    suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    fun clearError() {
        _error.value = null
    }
}
