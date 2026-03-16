package com.optix.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendOTPRequest(
    val phone: String,
    val purpose: String = "login"
)

@Serializable
data class OTPResponse(
    val success: Boolean,
    val message: String,
    @SerialName("expires_in") val expiresIn: Int = 300
)

@Serializable
data class VerifyOTPRequest(
    val phone: String,
    val otp: String,
    @SerialName("device_info") val deviceInfo: DeviceInfo? = null
)

@Serializable
data class DeviceInfo(
    @SerialName("device_name") val deviceName: String,
    val os: String,
    @SerialName("app_version") val appVersion: String
)

@Serializable
data class GoogleLoginRequest(
    @SerialName("id_token") val idToken: String,
    @SerialName("device_info") val deviceInfo: DeviceInfo? = null
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_at") val expiresAt: Long = 0,
    val user: UserDto? = null
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class RefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_at") val expiresAt: Long = 0
)

@Serializable
data class UserDto(
    val id: String,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val name: String? = fullName,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("paper_trading_balance") val paperTradingBalance: Double = 1000000.0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

// Aliases for repository implementations
typealias OtpRequestDto = SendOTPRequest
typealias VerifyOtpRequestDto = VerifyOTPRequest
typealias GoogleSignInRequestDto = GoogleLoginRequest
