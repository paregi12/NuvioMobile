package com.nuvio.app.features.plugins

import android.content.Context
import android.content.SharedPreferences

internal object PluginStorage {
    private const val preferencesName = "nuvio_plugins"
    private const val pluginsStateKey = "plugins_state"

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

    private var preferences: SharedPreferences? = null
    var context: Context? = null
        private set

    fun initialize(context: Context) {
        this.context = context
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        
        // Clear cookies on session start to ensure fresh anti-bot challenges
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
        } catch (_: Exception) {}
    }

    fun loadState(profileId: Int): String? =
        preferences?.getString("${pluginsStateKey}_$profileId", null)

    fun saveState(profileId: Int, payload: String) {
        preferences
            ?.edit()
            ?.putString("${pluginsStateKey}_$profileId", payload)
            ?.apply()
    }

    fun loadScraperSettings(scraperId: String): String? =
        preferences?.getString("settings_${scraperId}", null)

    fun saveScraperSettings(scraperId: String, payload: String) {
        preferences
            ?.edit()
            ?.putString("settings_${scraperId}", payload)
            ?.apply()
    }
}

internal fun currentPluginPlatform(): String = "android"

internal fun currentEpochMillis(): Long = System.currentTimeMillis()
