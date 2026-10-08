package com.nuvio.app.features.debrid

import com.nuvio.app.core.network.RawHttpResponse
import com.nuvio.app.core.network.DefaultRawHttpResponseMaxBytes
import com.nuvio.app.core.network.httpRequestRaw
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal data class DebridApiResponse<T>(
    val status: Int,
    val body: T?,
    val rawBody: String,
) {
    val isSuccessful: Boolean
        get() = status in 200..299
}

internal object DebridApiJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

internal object TorboxApiClient {
    private const val BASE_URL = "https://api.torbox.app"
    // Torbox returns up to 1,000 items, each with its full files array.
    private const val cloudListResponseMaxBytes = 16 * 1024 * 1024

    suspend fun startDeviceAuthorization(
        appName: String,
    ): DebridApiResponse<TorboxEnvelopeDto<TorboxDeviceAuthorizationDto>> =
        requestWithoutAuth(
            method = "GET",
            url = "$BASE_URL/v1/api/user/auth/device/start?${
                queryString("app" to appName)
            }",
        )

    suspend fun redeemDeviceAuthorization(
        deviceCode: String,
    ): DebridApiResponse<TorboxEnvelopeDto<TorboxDeviceTokenDto>> =
        requestWithoutAuth(
            method = "POST",
            url = "$BASE_URL/v1/api/user/auth/device/token",
            body = DebridApiJson.json.encodeToString(TorboxDeviceTokenRequestDto(deviceCode = deviceCode)),
            contentType = "application/json",
        )

    suspend fun validateApiKey(apiKey: String): Boolean =
        getUser(apiKey.trim()).status in 200..299

    private suspend fun getUser(apiKey: String): RawHttpResponse =
        httpRequestRaw(
            method = "GET",
            url = "$BASE_URL/v1/api/user/me",
            headers = authHeaders(apiKey),
            body = "",
        )

    suspend fun listCloudUsenet(apiKey: String): DebridApiResponse<TorboxEnvelopeDto<List<TorboxCloudItemDto>>> =
        request(
            method = "GET",
            url = "$BASE_URL/v1/api/usenet/mylist",
            apiKey = apiKey,
            maxResponseBodyBytes = cloudListResponseMaxBytes,
        )

    suspend fun listCloudWebDownloads(apiKey: String): DebridApiResponse<TorboxEnvelopeDto<List<TorboxCloudItemDto>>> =
        request(
            method = "GET",
            url = "$BASE_URL/v1/api/webdl/mylist",
            apiKey = apiKey,
            maxResponseBodyBytes = cloudListResponseMaxBytes,
        )

    suspend fun requestCloudUsenetDownloadLink(
        apiKey: String,
        usenetId: String,
        fileId: String?,
    ): DebridApiResponse<TorboxEnvelopeDto<String>> =
        request(
            method = "GET",
            url = "$BASE_URL/v1/api/usenet/requestdl?${
                queryString(
                    "token" to apiKey,
                    "usenet_id" to usenetId,
                    "file_id" to fileId,
                    "zip_link" to "false",
                    "redirect" to "false",
                    "append_name" to "false",
                )
            }",
            apiKey = apiKey,
        )

    suspend fun requestCloudWebDownloadLink(
        apiKey: String,
        webId: String,
        fileId: String?,
    ): DebridApiResponse<TorboxEnvelopeDto<String>> =
        request(
            method = "GET",
            url = "$BASE_URL/v1/api/webdl/requestdl?${
                queryString(
                    "token" to apiKey,
                    "web_id" to webId,
                    "file_id" to fileId,
                    "zip_link" to "false",
                    "redirect" to "false",
                    "append_name" to "false",
                )
            }",
            apiKey = apiKey,
        )

    private suspend inline fun <reified T> request(
        method: String,
        url: String,
        apiKey: String,
        body: String = "",
        contentType: String? = null,
        maxResponseBodyBytes: Int = DefaultRawHttpResponseMaxBytes,
    ): DebridApiResponse<T> {
        val headers = authHeaders(apiKey) + listOfNotNull(
            contentType?.let { "Content-Type" to it },
            "Accept" to "application/json",
        )
        val response = httpRequestRaw(
            method = method,
            url = url,
            headers = headers,
            body = body,
            maxResponseBodyBytes = maxResponseBodyBytes,
        )
        return DebridApiResponse(
            status = response.status,
            body = response.decodeBody<T>(),
            rawBody = response.body,
        )
    }

    private suspend inline fun <reified T> requestWithoutAuth(
        method: String,
        url: String,
        body: String = "",
        contentType: String? = null,
    ): DebridApiResponse<T> {
        val headers = listOfNotNull(
            contentType?.let { "Content-Type" to it },
            "Accept" to "application/json",
        ).toMap()
        val response = httpRequestRaw(
            method = method,
            url = url,
            headers = headers,
            body = body,
        )
        return DebridApiResponse(
            status = response.status,
            body = response.decodeBody<T>(),
            rawBody = response.body,
        )
    }

    private fun authHeaders(apiKey: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $apiKey")
}

internal object RealDebridApiClient {
    private const val BASE_URL = "https://api.real-debrid.com/rest/1.0"

    suspend fun validateApiKey(apiKey: String): Boolean =
        httpRequestRaw(
            method = "GET",
            url = "$BASE_URL/user",
            headers = authHeaders(apiKey.trim()),
            body = "",
        ).status in 200..299

    private fun authHeaders(apiKey: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $apiKey")
}

internal object PremiumizeApiClient {
    private const val BASE_URL = "https://www.premiumize.me"

    suspend fun validateApiKey(apiKey: String): Boolean {
        val response = accountInfo(apiKey.trim())
        return response.isSuccessful && response.body?.isSuccess == true
    }

    suspend fun startDeviceAuthorization(
        clientId: String,
    ): DebridApiResponse<PremiumizeDeviceAuthorizationDto> =
        formRequestWithoutAuth(
            method = "POST",
            url = "$BASE_URL/token",
            fields = listOf(
                "response_type" to "device_code",
                "client_id" to clientId,
            ),
        )

    suspend fun redeemDeviceAuthorization(
        clientId: String,
        deviceCode: String,
    ): DebridApiResponse<PremiumizeDeviceTokenDto> =
        formRequestWithoutAuth(
            method = "POST",
            url = "$BASE_URL/token",
            fields = listOf(
                "grant_type" to "device_code",
                "code" to deviceCode,
                "client_id" to clientId,
            ),
        )

    suspend fun accountInfo(apiKey: String): DebridApiResponse<PremiumizeAccountInfoDto> =
        request(
            method = "GET",
            url = "$BASE_URL/api/account/info",
            apiKey = apiKey,
        )

    suspend fun listAllItems(apiKey: String): DebridApiResponse<PremiumizeItemListAllDto> =
        request(
            method = "GET",
            url = "$BASE_URL/api/item/listall",
            apiKey = apiKey,
        )

    suspend fun itemDetails(
        apiKey: String,
        itemId: String,
    ): DebridApiResponse<PremiumizeItemDetailsDto> =
        request(
            method = "GET",
            url = "$BASE_URL/api/item/details?${queryString("id" to itemId)}",
            apiKey = apiKey,
        )

    private suspend inline fun <reified T> formRequestWithoutAuth(
        method: String,
        url: String,
        fields: List<Pair<String, String>>,
    ): DebridApiResponse<T> =
        requestWithoutAuth(
            method = method,
            url = url,
            body = formBody(fields),
            contentType = "application/x-www-form-urlencoded",
        )

    private suspend inline fun <reified T> formRequest(
        method: String,
        url: String,
        apiKey: String,
        fields: List<Pair<String, String>>,
    ): DebridApiResponse<T> =
        request(
            method = method,
            url = url,
            apiKey = apiKey,
            body = formBody(fields),
            contentType = "application/x-www-form-urlencoded",
        )

    private suspend inline fun <reified T> requestWithoutAuth(
        method: String,
        url: String,
        body: String = "",
        contentType: String? = null,
    ): DebridApiResponse<T> {
        val headers = listOfNotNull(
            contentType?.let { "Content-Type" to it },
            "Accept" to "application/json",
        ).toMap()
        val response = httpRequestRaw(
            method = method,
            url = url,
            headers = headers,
            body = body,
        )
        return DebridApiResponse(
            status = response.status,
            body = response.decodeBody<T>(),
            rawBody = response.body,
        )
    }

    private suspend inline fun <reified T> request(
        method: String,
        url: String,
        apiKey: String,
        body: String = "",
        contentType: String? = null,
    ): DebridApiResponse<T> {
        val headers = authHeaders(apiKey) + listOfNotNull(
            contentType?.let { "Content-Type" to it },
            "Accept" to "application/json",
        )
        val response = httpRequestRaw(
            method = method,
            url = url,
            headers = headers,
            body = body,
        )
        return DebridApiResponse(
            status = response.status,
            body = response.decodeBody<T>(),
            rawBody = response.body,
        )
    }

    private fun formBody(fields: List<Pair<String, String>>): String =
        fields.joinToString("&") { (key, value) ->
            "${encodeFormValue(key)}=${encodeFormValue(value)}"
        }

    private fun authHeaders(apiKey: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $apiKey")

    private val PremiumizeAccountInfoDto.isSuccess: Boolean
        get() = status.equals("success", ignoreCase = true)
}

object DebridCredentialValidator {
    suspend fun validateProvider(providerId: String, apiKey: String): Boolean {
        val normalized = apiKey.trim()
        if (normalized.isBlank()) return false
        return DebridProviderApis.apiFor(providerId)?.validateApiKey(normalized) == true
    }
}

private inline fun <reified T> RawHttpResponse.decodeBody(): T? {
    if (body.isBlank() || T::class == Unit::class) return null
    return try {
        DebridApiJson.json.decodeFromString<T>(body)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
