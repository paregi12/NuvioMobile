package com.nuvio.app.features.anilist

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object AniListStorage {
    private const val authPayloadKey = "anilist_auth_payload"
    private const val settingsPayloadKey = "anilist_settings_payload"
    private const val libraryPayloadKey = "anilist_library_payload"

    actual fun loadAuthPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(authPayloadKey))

    actual fun saveAuthPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(authPayloadKey))
    }

    actual fun loadSettingsPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(settingsPayloadKey))

    actual fun saveSettingsPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(settingsPayloadKey))
    }

    actual fun loadLibraryPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(libraryPayloadKey))

    actual fun saveLibraryPayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(libraryPayloadKey))
    }
}
