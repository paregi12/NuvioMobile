package com.nuvio.app.features.debrid

import kotlinx.serialization.Serializable

internal interface DebridProviderApi {
    val provider: DebridProvider

    suspend fun validateApiKey(apiKey: String): Boolean

    suspend fun startDeviceAuthorization(appName: String): DebridDeviceAuthorization? = null

    suspend fun redeemDeviceAuthorization(deviceCode: String): DebridDeviceAuthorizationTokenResult =
        DebridDeviceAuthorizationTokenResult.Unsupported
}

internal object DebridProviderApis {
    private val registered = listOf(
        TorboxDebridProviderApi(),
        PremiumizeDebridProviderApi(),
        RealDebridProviderApi(),
    )

    fun apiFor(providerId: String?): DebridProviderApi? {
        val normalized = DebridProviders.byId(providerId)?.id ?: return null
        return registered.firstOrNull { it.provider.id == normalized }
    }
}

@Serializable
internal data class DebridDeviceAuthorization(
    val providerId: String,
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val friendlyVerificationUrl: String,
    val intervalSeconds: Int,
    val expiresAt: String?,
)

internal sealed interface DebridDeviceAuthorizationTokenResult {
    data class Authorized(val accessToken: String) : DebridDeviceAuthorizationTokenResult
    data object Pending : DebridDeviceAuthorizationTokenResult
    data object Expired : DebridDeviceAuthorizationTokenResult
    data object Unsupported : DebridDeviceAuthorizationTokenResult
    data class Failed(val message: String?) : DebridDeviceAuthorizationTokenResult
}

private class TorboxDebridProviderApi : DebridProviderApi {
    override val provider: DebridProvider = DebridProviders.Torbox

    override suspend fun validateApiKey(apiKey: String): Boolean =
        TorboxApiClient.validateApiKey(apiKey)

    override suspend fun startDeviceAuthorization(appName: String): DebridDeviceAuthorization? {
        val response = TorboxApiClient.startDeviceAuthorization(appName = appName)
        val data = response.body?.takeIf { response.isSuccessful && it.success != false }?.data
            ?: return null
        val deviceCode = data.deviceCode?.takeIf { it.isNotBlank() } ?: return null
        val userCode = data.code?.takeIf { it.isNotBlank() } ?: return null
        val verificationUrl = data.verificationUrl?.takeIf { it.isNotBlank() } ?: return null
        return DebridDeviceAuthorization(
            providerId = provider.id,
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUrl = verificationUrl,
            friendlyVerificationUrl = data.friendlyVerificationUrl?.takeIf { it.isNotBlank() }
                ?: verificationUrl,
            intervalSeconds = data.interval?.coerceAtLeast(1) ?: 5,
            expiresAt = data.expiresAt?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun redeemDeviceAuthorization(deviceCode: String): DebridDeviceAuthorizationTokenResult {
        val normalized = deviceCode.trim()
        if (normalized.isBlank()) return DebridDeviceAuthorizationTokenResult.Failed(null)
        return torboxDeviceAuthorizationTokenResult(
            TorboxApiClient.redeemDeviceAuthorization(deviceCode = normalized),
        )
    }
}

internal class PremiumizeDebridProviderApi(
    private val clientIdProvider: () -> String = { PremiumizeConfig.CLIENT_ID },
) : DebridProviderApi {
    override val provider: DebridProvider = DebridProviders.Premiumize

    override suspend fun validateApiKey(apiKey: String): Boolean =
        PremiumizeApiClient.validateApiKey(apiKey)

    override suspend fun startDeviceAuthorization(appName: String): DebridDeviceAuthorization? {
        val clientId = premiumizeClientIdOrThrow()
        val response = PremiumizeApiClient.startDeviceAuthorization(clientId = clientId)
        return premiumizeDeviceAuthorizationFromResponse(response, provider.id)
    }

    override suspend fun redeemDeviceAuthorization(deviceCode: String): DebridDeviceAuthorizationTokenResult {
        val clientId = premiumizeClientIdOrThrow()
        val normalized = deviceCode.trim()
        if (normalized.isBlank()) return DebridDeviceAuthorizationTokenResult.Failed(null)
        val response = PremiumizeApiClient.redeemDeviceAuthorization(
            clientId = clientId,
            deviceCode = normalized,
        )
        return premiumizeDeviceAuthorizationTokenResult(response)
    }

    private fun premiumizeClientIdOrThrow(): String =
        clientIdProvider().trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Premiumize sign-in is missing PREMIUMIZE_CLIENT_ID.")
}

private class RealDebridProviderApi : DebridProviderApi {
    override val provider: DebridProvider = DebridProviders.RealDebrid

    override suspend fun validateApiKey(apiKey: String): Boolean =
        RealDebridApiClient.validateApiKey(apiKey)
}

internal fun premiumizeDeviceAuthorizationFromResponse(
    response: DebridApiResponse<PremiumizeDeviceAuthorizationDto>,
    providerId: String,
): DebridDeviceAuthorization? {
    val data = response.body?.takeIf { response.isSuccessful } ?: return null
    val deviceCode = data.deviceCode?.takeIf { it.isNotBlank() } ?: return null
    val userCode = data.userCode?.takeIf { it.isNotBlank() } ?: return null
    val verificationUrl = data.verificationUri?.takeIf { it.isNotBlank() } ?: return null
    return DebridDeviceAuthorization(
        providerId = providerId,
        deviceCode = deviceCode,
        userCode = userCode,
        verificationUrl = verificationUrl,
        friendlyVerificationUrl = data.verificationUriComplete?.takeIf { it.isNotBlank() }
            ?: verificationUrl,
        intervalSeconds = data.interval?.coerceAtLeast(1) ?: 5,
        expiresAt = data.expiresIn?.takeIf { it > 0 }?.let { "${it}s" },
    )
}

internal fun torboxDeviceAuthorizationTokenResult(
    response: DebridApiResponse<TorboxEnvelopeDto<TorboxDeviceTokenDto>>,
): DebridDeviceAuthorizationTokenResult {
    val envelope = response.body
    val accessToken = envelope
        ?.takeIf { response.isSuccessful && it.success != false }
        ?.data
        ?.accessToken
        ?.takeIf { it.isNotBlank() }
    if (accessToken != null) {
        return DebridDeviceAuthorizationTokenResult.Authorized(accessToken)
    }
    val message = listOfNotNull(envelope?.error, envelope?.detail, response.rawBody)
        .joinToString(" ")
        .lowercase()
    return when {
        message.contains("pending") ||
            message.contains("not authorized") ||
            message.contains("not been used") ||
            message.contains("not used yet") ||
            message.contains("scan the code") ->
            DebridDeviceAuthorizationTokenResult.Pending
        message.contains("expired") ->
            DebridDeviceAuthorizationTokenResult.Expired
        response.status == 404 || response.status == 409 || response.status == 425 ->
            DebridDeviceAuthorizationTokenResult.Pending
        response.status == 410 ->
            DebridDeviceAuthorizationTokenResult.Expired
        else ->
            DebridDeviceAuthorizationTokenResult.Failed(envelope?.detail ?: envelope?.error)
    }
}

internal fun premiumizeDeviceAuthorizationTokenResult(
    response: DebridApiResponse<PremiumizeDeviceTokenDto>,
): DebridDeviceAuthorizationTokenResult {
    val body = response.body
    body?.accessToken?.takeIf { response.isSuccessful && it.isNotBlank() }?.let { accessToken ->
        return DebridDeviceAuthorizationTokenResult.Authorized(accessToken)
    }
    return when (body?.error?.lowercase()) {
        "authorization_pending", "slow_down" -> DebridDeviceAuthorizationTokenResult.Pending
        "invalid_grant", "expired_token" -> DebridDeviceAuthorizationTokenResult.Expired
        "access_denied" -> DebridDeviceAuthorizationTokenResult.Failed(body.errorDescription)
        else -> {
            if (response.status == 400 && body?.error.isNullOrBlank()) {
                DebridDeviceAuthorizationTokenResult.Pending
            } else {
                DebridDeviceAuthorizationTokenResult.Failed(body?.errorDescription ?: body?.error ?: response.rawBody)
            }
        }
    }
}
