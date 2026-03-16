package com.optix.app.presentation.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val isLoggedIn: Boolean = false,
    val userName: String? = null,
    val userEmail: String? = null,
    val currentTheme: String = "System",
    val currentLanguage: String = "English",
    val spotPriceInterval: Float = 2f,
    val optionChainInterval: Float = 5f,
    val defaultIndex: String = "NIFTY",
    val hapticEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val THEME_KEY = stringPreferencesKey("theme")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val SPOT_PRICE_INTERVAL_KEY = floatPreferencesKey("spot_price_interval")
        val OPTION_CHAIN_INTERVAL_KEY = floatPreferencesKey("option_chain_interval")
        val DEFAULT_INDEX_KEY = stringPreferencesKey("default_index")
        val HAPTIC_ENABLED_KEY = booleanPreferencesKey("haptic_enabled")
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
        observeAuthState()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val preferences = dataStore.data.first()
            _state.update {
                it.copy(
                    currentTheme = preferences[THEME_KEY] ?: "System",
                    currentLanguage = preferences[LANGUAGE_KEY] ?: "English",
                    spotPriceInterval = preferences[SPOT_PRICE_INTERVAL_KEY] ?: 2f,
                    optionChainInterval = preferences[OPTION_CHAIN_INTERVAL_KEY] ?: 5f,
                    defaultIndex = preferences[DEFAULT_INDEX_KEY] ?: "NIFTY",
                    hapticEnabled = preferences[HAPTIC_ENABLED_KEY] ?: true
                )
            }
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                val user = authRepository.currentUser
                _state.update {
                    it.copy(
                        isLoggedIn = authRepository.isAuthenticated,
                        userName = user?.name,
                        userEmail = user?.email
                    )
                }
            }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[THEME_KEY] = theme
            }
            _state.update { it.copy(currentTheme = theme) }
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[LANGUAGE_KEY] = language
            }
            _state.update { it.copy(currentLanguage = language) }
        }
    }

    fun setSpotPriceInterval(interval: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[SPOT_PRICE_INTERVAL_KEY] = interval
            }
            _state.update { it.copy(spotPriceInterval = interval) }
        }
    }

    fun setOptionChainInterval(interval: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[OPTION_CHAIN_INTERVAL_KEY] = interval
            }
            _state.update { it.copy(optionChainInterval = interval) }
        }
    }

    fun setDefaultIndex(index: String) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[DEFAULT_INDEX_KEY] = index
            }
            _state.update { it.copy(defaultIndex = index) }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[HAPTIC_ENABLED_KEY] = enabled
            }
            _state.update { it.copy(hapticEnabled = enabled) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
