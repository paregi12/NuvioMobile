package com.nuvio.app.features.plugins.runtime.network

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.nuvio.app.features.plugins.runtime.host.HostModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class BrowserHostBridge : HostModule {
    private val json = Json { ignoreUnknownKeys = true }

    override fun register(runtime: QuickJs) {
        runtime.function("__native_browser_resolve") { args ->
            val url = args.getOrNull(0)?.toString() ?: ""
            val script = args.getOrNull(1)?.toString()
            val userAgent = args.getOrNull(2)?.toString()
            val timeout = (args.getOrNull(3) as? Number)?.toLong() ?: 45_000L

            val result = runBlocking {
                BrowserBridge.resolve(url, script, userAgent, timeout)
            }

            val resultObj = JsonObject(
                mapOf(
                    "success" to JsonPrimitive(result.success),
                    "url" to JsonPrimitive(result.url ?: ""),
                    "body" to JsonPrimitive(result.body ?: ""),
                    "userAgent" to JsonPrimitive(result.userAgent ?: ""),
                    "cookies" to JsonObject((result.cookies ?: emptyMap()).mapValues { JsonPrimitive(it.value) }),
                )
            )
            resultObj.toString()
        }
    }
}
