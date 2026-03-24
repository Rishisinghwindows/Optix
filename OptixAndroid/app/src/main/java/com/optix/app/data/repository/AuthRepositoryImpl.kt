package com.optix.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.optix.app.core.util.AnalyticsHelper
import com.optix.app.core.util.CrashlyticsHelper
import com.optix.app.core.util.Resource
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.data.remote.dto.GoogleSignInRequestDto
import com.optix.app.data.remote.dto.OtpRequestDto
import com.optix.app.data.remote.dto.VerifyOtpRequestDto
import com.optix.app.domain.model.AuthState
import com.optix.app.domain.model.AuthTokens
import com.optix.app.domain.model.User
import com.optix.app.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: OptixApiService,
    private val dataStore: DataStore<Preferences>
) : AuthRepository {

    companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        val USER_KEY = stringPreferencesKey("user")
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var _currentUser: User? = null
    override val currentUser: User? get() = _currentUser

    override val isAuthenticated: Boolean get() = _currentUser != null

    init {
        // Initialize auth state on creation
        CoroutineScope(Dispatchers.IO).launch {
            restoreSession()
        }
    }

    override suspend fun sendOtp(phone: String): Resource<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.sendOtp(OtpRequestDto(phone))
                if (response.isSuccessful) {
                    Resource.Success(true)
                } else {
                    Resource.Error("Failed to send OTP")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun verifyOtp(phoneNumber: String, otp: String): Resource<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.verifyOtp(VerifyOtpRequestDto(phoneNumber, otp))
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!
                    val tokens = AuthTokens(
                        accessToken = dto.accessToken,
                        refreshToken = dto.refreshToken ?: "",
                        expiresAt = dto.expiresAt
                    )
                    saveTokens(tokens)

                    val user = dto.user?.let { userDto ->
                        User(
                            id = userDto.id,
                            fullName = userDto.name,
                            email = userDto.email,
                            phone = userDto.phone
                        )
                    } ?: User(id = "unknown", phone = phoneNumber)

                    saveUser(user)
                    _currentUser = user
                    _authState.value = AuthState.Authenticated(user)
                    CrashlyticsHelper.setUser(user.id)
                    // Bind analytics identity so all future events are attributed to this user
                    AnalyticsHelper.setUserId(user.id)
                    AnalyticsHelper.logLogin("otp")

                    Resource.Success(user)
                } else {
                    Resource.Error("Invalid OTP")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun signInWithGoogle(idToken: String): Resource<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.googleSignIn(GoogleSignInRequestDto(idToken))
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!
                    val tokens = AuthTokens(
                        accessToken = dto.accessToken,
                        refreshToken = dto.refreshToken ?: "",
                        expiresAt = dto.expiresAt
                    )
                    saveTokens(tokens)

                    val user = dto.user?.let { userDto ->
                        User(
                            id = userDto.id,
                            fullName = userDto.name,
                            email = userDto.email,
                            phone = userDto.phone
                        )
                    } ?: User(id = "unknown", email = "unknown@email.com")

                    saveUser(user)
                    _currentUser = user
                    _authState.value = AuthState.Authenticated(user)
                    CrashlyticsHelper.setUser(user.id)
                    AnalyticsHelper.setUserId(user.id)
                    AnalyticsHelper.logLogin("google")

                    Resource.Success(user)
                } else {
                    Resource.Error("Google sign-in failed")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun refreshToken(): Resource<AuthTokens> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = getRefreshToken() ?: return@withContext Resource.Error("No refresh token")

            val response = api.refreshToken(mapOf("refresh_token" to refreshToken))
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val tokens = AuthTokens(
                    accessToken = dto.accessToken,
                    refreshToken = dto.refreshToken ?: refreshToken,
                    expiresAt = dto.expiresAt
                )
                saveTokens(tokens)
                Resource.Success(tokens)
            } else {
                Resource.Error("Failed to refresh token")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun logout(): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            api.logout()
            clearTokens()
            _currentUser = null
            _authState.value = AuthState.Unauthenticated
            CrashlyticsHelper.setUser(null)
            // Clear analytics identity so post-logout events are anonymous
            AnalyticsHelper.setUserId(null)
            Resource.Success(true)
        } catch (e: Exception) {
            // Even if the server-side logout fails, clear local state to avoid stale sessions
            clearTokens()
            _currentUser = null
            _authState.value = AuthState.Unauthenticated
            CrashlyticsHelper.setUser(null)
            AnalyticsHelper.setUserId(null)
            Resource.Success(true)
        }
    }

    override suspend fun getCurrentUser(): Resource<User> = withContext(Dispatchers.IO) {
        _currentUser?.let {
            Resource.Success(it)
        } ?: run {
            val user = getStoredUser()
            if (user != null) {
                _currentUser = user
                Resource.Success(user)
            } else {
                Resource.Error("No user found")
            }
        }
    }

    override fun getAccessToken(): String? {
        // This is called synchronously by the auth interceptor
        // We use a blocking approach here
        return kotlinx.coroutines.runBlocking {
            dataStore.data.first()[ACCESS_TOKEN_KEY]
        }
    }

    override suspend fun restoreSession(): Resource<User> = withContext(Dispatchers.IO) {
        try {
            val token = dataStore.data.first()[ACCESS_TOKEN_KEY]
            val user = getStoredUser()

            if (token != null && user != null) {
                _currentUser = user
                _authState.value = AuthState.Authenticated(user)
                CrashlyticsHelper.setUser(user.id)
                Resource.Success(user)
            } else {
                _authState.value = AuthState.Unauthenticated
                Resource.Error("No session found")
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
            Resource.Error(e.message ?: "Failed to restore session")
        }
    }

    private suspend fun getRefreshToken(): String? {
        return dataStore.data.first()[REFRESH_TOKEN_KEY]
    }

    private suspend fun getStoredUser(): User? {
        val userJson = dataStore.data.first()[USER_KEY] ?: return null
        return try {
            Json.decodeFromString(userJson)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveTokens(tokens: AuthTokens) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = tokens.accessToken
            tokens.refreshToken?.let { preferences[REFRESH_TOKEN_KEY] = it }
        }
    }

    private suspend fun saveUser(user: User) {
        dataStore.edit { preferences ->
            preferences[USER_KEY] = Json.encodeToString(user)
        }
    }

    private suspend fun clearTokens() {
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(USER_KEY)
        }
    }
}
