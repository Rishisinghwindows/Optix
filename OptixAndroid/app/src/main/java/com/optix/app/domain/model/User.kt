package com.optix.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * User profile data
 */
@Serializable
data class User(
    val id: String,
    val phone: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val paperTradingBalance: Double = 1000000.0, // 10 Lakh default
    val isActive: Boolean = true,
    val isPremium: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Alias for fullName (transient - not serialized)
    @Transient
    val name: String? = fullName

    @Transient
    val displayName: String = fullName ?: phone ?: email ?: "User"

    @Transient
    val initials: String = displayName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "U" }
}

/**
 * Authentication state
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Auth tokens
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long = 0
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt
}
