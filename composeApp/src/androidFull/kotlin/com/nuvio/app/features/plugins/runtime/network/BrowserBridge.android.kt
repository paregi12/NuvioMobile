package com.nuvio.app.features.plugins.runtime.network

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nuvio.app.features.plugins.PluginStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal actual object BrowserBridge {
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    actual suspend fun resolve(
        url: String,
        script: String?,
        userAgent: String?,
        timeout: Long
    ): BrowserSolveResult = withContext(Dispatchers.IO) {
        val context = PluginStorage.context ?: return@withContext BrowserSolveResult(false)
        val latch = CountDownLatch(1)
        var solved = false
        var resultUrl: String? = null
        var resultBody: String? = null
        var resultUA: String? = null
        var resultCookies: Map<String, String>? = null
        var webViewRef: WebView? = null

        handler.post {
            val webView = WebView(context)
            webViewRef = webView
            
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = false
                userAgentString = userAgent ?: PluginStorage.DEFAULT_USER_AGENT
            }

            // Interface for script to signal completion
            class BridgeInterface {
                @JavascriptInterface
                fun onSolve(body: String?) {
                    resultBody = body
                    solved = true
                    latch.countDown()
                }
            }
            webView.addJavascriptInterface(BridgeInterface(), "__nuvio_bridge")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    resultUrl = url
                    resultUA = view.settings.userAgentString
                    
                    // Always try to extract cookies
                    val cookieString = CookieManager.getInstance().getCookie(url)
                    resultCookies = parseCookies(cookieString)

                    // Execute custom script or default Cloudflare auto-clicker
                    val scriptToRun = script ?: DEFAULT_CF_SCRIPT
                    view.evaluateJavascript(scriptToRun, null)
                    
                    // If no custom script and we have cf_clearance, we are done
                    if (script == null && cookieString?.contains("cf_clearance") == true) {
                        solved = true
                        latch.countDown()
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val reqUrl = request.url.toString().lowercase()
                    if (isBlacklisted(reqUrl)) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webView.loadUrl(url)
        }

        // Wait for result or timeout
        latch.await(timeout, TimeUnit.MILLISECONDS)
        
        // Final cookie extraction if solved
        if (solved) {
            handler.post {
                val finalCookies = CookieManager.getInstance().getCookie(resultUrl ?: url)
                resultCookies = parseCookies(finalCookies)
            }
        }

        // Cleanup
        handler.post {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
        }
        
        BrowserSolveResult(
            success = solved,
            url = resultUrl,
            body = resultBody,
            userAgent = resultUA,
            cookies = resultCookies
        )
    }

    private fun parseCookies(cookieString: String?): Map<String, String> {
        if (cookieString == null) return emptyMap()
        return cookieString.split(";").associate {
            val parts = it.split("=", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val value = parts.getOrNull(1)?.trim().orEmpty()
            name to value
        }.filter { it.key.isNotEmpty() }
    }

    private fun isBlacklisted(url: String): Boolean {
        return BLACKLIST_EXTENSIONS.any { url.contains(it) } || 
               BLACKLIST_KEYWORDS.any { url.contains(it) }
    }

    private val BLACKLIST_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
        ".mp4", ".m4v", ".mkv", ".webm", ".mov",
        ".mp3", ".wav", ".ogg",
        ".woff", ".woff2", ".ttf", ".otf",
        ".css"
    )

    private val BLACKLIST_KEYWORDS = listOf(
        "google-analytics", "doubleclick", "adservice", "facebook.net", "googletagmanager"
    )

    private val DEFAULT_CF_SCRIPT = """
        (function() {
            setInterval(() => {
                const challenge = document.querySelector("#challenge-form, #challenge-stage");
                if (challenge) {
                    const simpleButton = document.querySelector("#challenge-stage input[type='button']");
                    if (simpleButton) simpleButton.click();

                    const turnstile = document.querySelector("div.hcaptcha-box > iframe, #challenge-stage iframe");
                    if (turnstile && turnstile.contentWindow) {
                        const cb = turnstile.contentWindow.document.querySelector("input[type='checkbox']");
                        if (cb) cb.click();
                    }
                }
            }, 2000);
        })();
    """.trimIndent()
}
