package com.nuvio.app.features.plugins.runtime.network

import co.touchlab.kermit.Logger
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.plugins.runtime.host.HostModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_FETCH_HEADER_VALUE_CHARS = 8 * 1024
private const val FETCH_TRUNCATION_SUFFIX = "\n...[truncated]"

internal class FetchBridge : HostModule {
    private val log = Logger.withTag("PluginRuntime")
    private val json = Json { ignoreUnknownKeys = true }

    override fun register(runtime: QuickJs) {
        runtime.function("__native_fetch") { args ->
            val url = args.getOrNull(0)?.toString() ?: ""
            val method = args.getOrNull(1)?.toString() ?: "GET"
            val headersJson = args.getOrNull(2)?.toString() ?: "{}"
            val body = args.getOrNull(3)?.toString() ?: ""
            val followRedirects = args.getOrNull(4) as? Boolean ?: true
            try {
                performNativeFetch(url, method, headersJson, body, followRedirects)
            } catch (t: Throwable) {
                log.e(t) { "Fetch bridge error for $method $url" }
                JsonObject(
                    mapOf(
                        "ok" to JsonPrimitive(false),
                        "status" to JsonPrimitive(0),
                        "statusText" to JsonPrimitive(t.message ?: "Fetch failed"),
                        "url" to JsonPrimitive(url),
                        "body" to JsonPrimitive(""),
                        "headers" to JsonObject(emptyMap()),
                    ),
                ).toString()
            }
        }
    }

    private fun performNativeFetch(
        url: String,
        method: String,
        headersJson: String,
        body: String,
        followRedirects: Boolean,
    ): String {
        val headers = parseHeaders(headersJson).toMutableMap()

        // Inject cached Cloudflare data if available
        CloudflareCache.getUserAgent(url)?.let { headers["User-Agent"] = it }
        CloudflareCache.getCookies(url)?.let { cachedCookies ->
            val existing = headers["Cookie"]
            val newCookies = cachedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            headers["Cookie"] = if (existing != null) "$existing; $newCookies" else newCookies
        }

        if (!headers.containsKey("User-Agent")) {
            headers["User-Agent"] = com.nuvio.app.features.plugins.PluginStorage.DEFAULT_USER_AGENT
        }

        var response = runBlocking {
            httpRequestRaw(
                method = method,
                url = url,
                headers = headers,
                body = body,
                followRedirects = followRedirects,
            )
        }

        // Detect anti-bot challenge (Cloudflare or DDoS-Guard)
        val serverHeader = response.headers["server"]?.lowercase() ?: ""
        val bodyLower = response.body.lowercase()
        val isCloudflare = serverHeader.contains("cloudflare") || bodyLower.contains("cf-challenge") || bodyLower.contains("ray id")
        val isDdosGuard = serverHeader.contains("ddos-guard") || bodyLower.contains("ddos-guard") || response.status == 403 && bodyLower.contains("check.ddos-guard.net")

        if ((response.status == 403 || response.status == 503) && (isCloudflare || isDdosGuard)) {
            val challengeType = if (isCloudflare) "Cloudflare" else "DDoS-Guard"
            log.i { "$challengeType challenge detected for $url (Status: ${response.status}). Attempting to solve..." }
            
            val solveResult = runBlocking {
                CloudflareSolver.solve(url, headers["User-Agent"])
            }

            if (solveResult.success) {
                log.i { "$challengeType challenge solved successfully for $url. Retrying request..." }
                CloudflareCache.update(url, solveResult.userAgent, solveResult.cookies)

                val retryHeaders = headers.toMutableMap()
                solveResult.userAgent?.let { retryHeaders["User-Agent"] = it }
                solveResult.cookies?.let { newCookiesMap ->
                    val existing = retryHeaders["Cookie"]
                    val newCookiesStr = newCookiesMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    retryHeaders["Cookie"] = if (existing != null) {
                        if (existing.contains(newCookiesStr)) existing else "$existing; $newCookiesStr"
                    } else {
                        newCookiesStr
                    }
                }

                response = runBlocking {
                    httpRequestRaw(
                        method = method,
                        url = url,
                        headers = retryHeaders,
                        body = body,
                        followRedirects = followRedirects,
                    )
                }
            } else {
                log.w { "Cloudflare challenge solve failed for $url" }
            }
        }

        val responseHeaders = response.headers.mapKeys { (key, _) -> key.lowercase() }
            .mapValues { (_, value) -> truncateString(value, MAX_FETCH_HEADER_VALUE_CHARS) }
        val result = JsonObject(
            mapOf(
                "ok" to JsonPrimitive(response.status in 200..299),
                "status" to JsonPrimitive(response.status),
                "statusText" to JsonPrimitive(response.statusText),
                "url" to JsonPrimitive(response.url),
                "body" to JsonPrimitive(response.body),
                "headers" to JsonObject(responseHeaders.mapValues { JsonPrimitive(it.value) }),
            ),
        )
        return result.toString()
    }

    private fun parseHeaders(headersJson: String): Map<String, String> {
        return runCatching {
            val obj = json.parseToJsonElement(headersJson) as? JsonObject ?: JsonObject(emptyMap())
            obj.entries
                .mapNotNull { (key, value) ->
                    value.jsonPrimitive.contentOrNull?.let { key to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun truncateString(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        val end = maxChars - FETCH_TRUNCATION_SUFFIX.length
        if (end <= 0) return FETCH_TRUNCATION_SUFFIX.take(maxChars)
        return value.substring(0, end) + FETCH_TRUNCATION_SUFFIX
    }
}
