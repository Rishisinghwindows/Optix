package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.AuthState
import com.optix.app.domain.model.AuthTokens
import com.optix.app.domain.model.User
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for authentication
 */
interface AuthRepository {
    /**
     * Observable auth state
     */
    val authState: StateFlow<AuthState>

    /**
     * Current user (null if not authenticated)
     */
    val currentUser: User?

    /**
     * Check if user is authenticated
     */
    val isAuthenticated: Boolean

    /**
     * Send OTP to phone number
     */
    suspend fun sendOtp(phone: String): Resource<Boolean>

    /**
     * Verify OTP and login
     */
    suspend fun verifyOtp(phoneNumber: String, otp: String): Resource<User>

    /**
     * Login with Google
     */
    suspend fun signInWithGoogle(idToken: String): Resource<User>

    /**
     * Refresh access token
     */
    suspend fun refreshToken(): Resource<AuthTokens>

    /**
     * Logout current session
     */
    suspend fun logout(): Resource<Boolean>

    /**
     * Get current user profile
     */
    suspend fun getCurrentUser(): Resource<User>

    /**
     * Get stored access token
     */
    fun getAccessToken(): String?

    /**
     * Check and restore session on app start
     */
    suspend fun restoreSession(): Resource<User>
}
