package com.nuvio.app.features.plugins.runtime.network

import co.touchlab.kermit.Logger
import io.ktor.http.*

/**
 * High-level solver for Cloudflare challenges.
 * Wraps the BrowserBridge for specific Cloudflare logic.
 */
internal object CloudflareSolver {
    private val log = Logger.withTag("CloudflareSolver")

    suspend fun solve(url: String, userAgent: String?): BrowserSolveResult {
        log.i { "Attempting to solve Cloudflare for $url" }
        return BrowserBridge.resolve(
            url = url,
            userAgent = userAgent,
            timeout = 45_000L
        )
    }
}

/**
 * Shared cache for Cloudflare cookies and User-Agents.
 */
internal object CloudflareCache {
    private val log = Logger.withTag("CloudflareCache")
    private val hostToCookies = mutableMapOf<String, Map<String, String>>()
    private val hostToUserAgent = mutableMapOf<String, String>()

    fun getCookies(url: String): Map<String, String>? {
        val host = runCatching { Url(url).host }.getOrNull() ?: return null
        return hostToCookies[host]
    }

    fun getUserAgent(url: String): String? {
        val host = runCatching { Url(url).host }.getOrNull() ?: return null
        return hostToUserAgent[host]
    }

    fun update(url: String, userAgent: String?, cookies: Map<String, String>?) {
        val host = runCatching { Url(url).host }.getOrNull() ?: return
        if (userAgent != null) {
            hostToUserAgent[host] = userAgent
        }
        if (cookies != null) {
            val existing = hostToCookies[host].orEmpty()
            hostToCookies[host] = existing + cookies
            log.i { "Updated Cloudflare cache for $host" }
        }
    }
}
