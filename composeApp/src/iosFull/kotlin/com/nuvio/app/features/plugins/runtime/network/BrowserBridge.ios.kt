package com.nuvio.app.features.plugins.runtime.network

import com.nuvio.app.features.plugins.PluginStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.*
import platform.WebKit.*
import platform.darwin.NSObject

internal actual object BrowserBridge {

    actual suspend fun resolve(
        url: String,
        script: String?,
        userAgent: String?,
        timeout: Long
    ): BrowserSolveResult = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<BrowserSolveResult>()
        
        val config = WKWebViewConfiguration()
        val userController = WKUserContentController()
        
        // Signal interface equivalent
        val handler = object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
                if (didReceiveScriptMessage.name == "nuvioBridge") {
                    val body = didReceiveScriptMessage.body as? String
                    // We don't complete here immediately if we still need cookies, 
                    // but for custom scripts this is the "solve" signal.
                    // To keep it simple and consistent with Android, we'll extract cookies then complete.
                }
            }
        }
        userController.addScriptMessageHandler(handler, "nuvioBridge")
        config.userContentController = userController
        
        val webView = WKWebView(frame = platform.CoreGraphics.CGRectZero.readValue(), configuration = config)
        webView.customUserAgent = userAgent ?: PluginStorage.DEFAULT_USER_AGENT
        
        val navigationDelegate = object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                val scriptToRun = script ?: DEFAULT_CF_SCRIPT
                webView.evaluateJavaScript(scriptToRun) { _, _ -> }
                
                // Start a background check for cookies
                checkCookies(webView, url, deferred)
            }
        }
        webView.navigationDelegate = navigationDelegate
        
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
        } else {
            return@withContext BrowserSolveResult(false)
        }

        val result = withTimeoutOrNull(timeout) {
            deferred.await()
        } ?: BrowserSolveResult(false)

        // Cleanup
        webView.navigationDelegate = null
        userController.removeScriptMessageHandlerForName("nuvioBridge")
        
        result
    }

    private fun checkCookies(webView: WKWebView, url: String, deferred: CompletableDeferred<BrowserSolveResult>) {
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies ->
            val cookieMap = mutableMapOf<String, String>()
            var foundCf = false
            (cookies as? List<NSHTTPCookie>)?.forEach { cookie ->
                cookieMap[cookie.name] = cookie.value
                if (cookie.name == "cf_clearance") foundCf = true
            }
            
            if (foundCf) {
                deferred.complete(
                    BrowserSolveResult(
                        success = true,
                        url = webView.URL?.absoluteString,
                        userAgent = webView.customUserAgent,
                        cookies = cookieMap
                    )
                )
            } else {
                // If not found yet, check again in 1 second if not timed out
                if (!deferred.isCompleted) {
                    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 1_000_000_000L), dispatch_get_main_queue()) {
                        checkCookies(webView, url, deferred)
                    }
                }
            }
        }
    }

    private val DEFAULT_CF_SCRIPT = """
        (function() {
            setInterval(() => {
                const challenge = document.querySelector("#challenge-form, #challenge-stage");
                if (challenge) {
                    const simpleButton = document.querySelector("#challenge-stage input[type='button']");
                    if (simpleButton) simpleButton.click();

                    const turnstile = document.querySelector("div.hcaptcha-box > iframe, #challenge-stage iframe");
                    if (turnstile && turnstile.contentWindow) {
                        try {
                            const cb = turnstile.contentWindow.document.querySelector("input[type='checkbox']");
                            if (cb) cb.click();
                        } catch(e) {}
                    }
                }
            }, 2000);
        })();
    """.trimIndent()
}

// Helper for GCD on Kotlin/Native
@Suppress("UNUSED_PARAMETER")
private fun dispatch_after(whenTime: Long, queue: platform.darwin.dispatch_queue_t, block: () -> Unit) {
    platform.darwin.dispatch_after(whenTime, queue, block)
}

private fun dispatch_time(whenTime: Long, delta: Long): Long {
    return platform.darwin.dispatch_time(whenTime, delta)
}

private val DISPATCH_TIME_NOW = 0L
