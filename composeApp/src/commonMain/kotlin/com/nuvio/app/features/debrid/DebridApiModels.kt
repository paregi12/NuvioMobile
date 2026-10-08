package com.nuvio.app.features.debrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class TorboxEnvelopeDto<T>(
    val success: Boolean? = null,
    val data: T? = null,
    val error: String? = null,
    val detail: String? = null,
)

@Serializable
internal data class TorboxCloudItemDto(
    val id: JsonElement? = null,
    val hash: String? = null,
    val name: String? = null,
    val status: String? = null,
    val state: String? = null,
    @SerialName("download_state") val downloadState: String? = null,
    val progress: Double? = null,
    @SerialName("download_progress") val downloadProgress: Double? = null,
    val size: Long? = null,
    @SerialName("total_size") val totalSize: Long? = null,
    val files: List<TorboxCloudFileDto>? = null,
)

@Serializable
internal data class TorboxCloudFileDto(
    val id: JsonElement? = null,
    val name: String? = null,
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("absolute_path") val absolutePath: String? = null,
    @SerialName("mimetype") val mimeType: String? = null,
    @SerialName("mime_type") val mimeTypeAlt: String? = null,
    val size: Long? = null,
)

@Serializable
internal data class TorboxDeviceAuthorizationDto(
    @SerialName("device_code") val deviceCode: String? = null,
    val code: String? = null,
    @SerialName("verification_url") val verificationUrl: String? = null,
    @SerialName("friendly_verification_url") val friendlyVerificationUrl: String? = null,
    val interval: Int? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
internal data class TorboxDeviceTokenRequestDto(
    @SerialName("device_code") val deviceCode: String,
)

@Serializable
internal data class TorboxDeviceTokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
)

@Serializable
internal data class PremiumizeDeviceAuthorizationDto(
    @SerialName("device_code") val deviceCode: String? = null,
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val interval: Int? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
internal data class PremiumizeDeviceTokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
internal data class PremiumizeApiEnvelopeDto(
    val status: String? = null,
    val message: String? = null,
    val code: String? = null,
)

@Serializable
internal data class PremiumizeAccountInfoDto(
    val status: String? = null,
    val message: String? = null,
    val code: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("premium_until") val premiumUntil: Long? = null,
    @SerialName("limit_used") val limitUsed: Double? = null,
    @SerialName("booster_points") val boosterPoints: Int? = null,
)

@Serializable
internal data class PremiumizeItemListAllDto(
    val status: String? = null,
    val message: String? = null,
    val code: String? = null,
    val files: List<PremiumizeCloudFileDto>? = null,
)

@Serializable
internal data class PremiumizeCloudFileDto(
    val id: String? = null,
    val name: String? = null,
    val path: String? = null,
    val type: String? = null,
    val size: Long? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val link: String? = null,
)

@Serializable
internal data class PremiumizeItemDetailsDto(
    val status: String? = null,
    val message: String? = null,
    val code: String? = null,
    val id: String? = null,
    val name: String? = null,
    val size: Long? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("folder_id") val folderId: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val link: String? = null,
)
