package com.nuvio.app.features.plugins.runtime.network

/**
 * Result of a headless browser (WebView) session.
 */
internal data class BrowserSolveResult(
    val success: Boolean,
    val url: String? = null,
    val body: String? = null,
    val userAgent: String? = null,
    val cookies: Map<String, String>? = null,
)

/**
 * Headless Browser Bridge.
 * Allows plugins to execute custom JavaScript in a real DOM (WebView)
 * to solve challenges or extract dynamic content.
 */
internal expect object BrowserBridge {
    /**
     * Loads a URL in a headless WebView and optionally executes a script.
     * @param url The target URL.
     * @param script Custom JS to execute in the WebView.
     * @param timeout Timeout in milliseconds.
     */
    suspend fun resolve(
        url: String, 
        script: String? = null, 
        userAgent: String? = null,
        timeout: Long = 45_000L
    ): BrowserSolveResult
}
