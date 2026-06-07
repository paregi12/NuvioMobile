package com.nuvio.app.features.plugins

import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

internal object PluginStorage {
    private const val pluginsStateKey = "plugins_state"

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

    fun loadState(profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey("${pluginsStateKey}_$profileId")

    fun saveState(profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = "${pluginsStateKey}_$profileId",
        )
    }

    fun loadScraperSettings(scraperId: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey("settings_${scraperId}")

    fun saveScraperSettings(scraperId: String, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(
            payload,
            forKey = "settings_${scraperId}",
        )
    }
}

internal fun currentPluginPlatform(): String = "ios"

internal fun currentEpochMillis(): Long =
    (platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0).toLong()
