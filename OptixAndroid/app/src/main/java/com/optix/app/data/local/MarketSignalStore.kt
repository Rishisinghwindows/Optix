package com.optix.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MarketSignal(
    val id: String,
    val signalType: String,
    val title: String,
    val body: String,
    val index: String,
    val timestamp: Long,
    val isRead: Boolean = false
) {
    val icon: String get() = when (signalType) {
        "long_buildup" -> "trending_up"
        "short_buildup" -> "trending_down"
        "long_unwinding" -> "south_east"
        "short_covering" -> "north_east"
        "pcr_shift" -> "swap_horiz"
        "vix_change" -> "flash_on"
        "oi_surge" -> "local_fire_department"
        "max_pain_shift" -> "gps_fixed"
        else -> "bar_chart"
    }

    val sentiment: String get() = when (signalType) {
        "long_buildup" -> "Bullish"
        "short_buildup" -> "Bearish"
        "long_unwinding" -> "Weak"
        "short_covering" -> "Squeeze"
        "pcr_shift" -> "Shift"
        "vix_change" -> "Volatility"
        "oi_surge" -> "Activity"
        "max_pain_shift" -> "Institutional"
        else -> ""
    }

    val isBullish: Boolean get() = signalType in listOf("long_buildup", "short_covering")
    val isBearish: Boolean get() = signalType in listOf("short_buildup", "long_unwinding")
    val isVolatility: Boolean get() = signalType in listOf("vix_change", "oi_surge", "pcr_shift")

    val signalTypeLabel: String get() = when (signalType) {
        "long_buildup" -> "Long Buildup — Price up + OI up"
        "short_buildup" -> "Short Buildup — Price down + OI up"
        "long_unwinding" -> "Long Unwinding — Price down + OI down"
        "short_covering" -> "Short Covering — Price up + OI down"
        "pcr_shift" -> "PCR Shift — Put/Call ratio changed"
        "vix_change" -> "VIX Change — Volatility spike/drop"
        "oi_surge" -> "OI Surge — Major position buildup"
        "max_pain_shift" -> "Max Pain Shift — Institutional view"
        else -> signalType
    }
}

@Singleton
class MarketSignalStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("market_signals", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val maxSignals = 200

    fun loadSignals(): List<MarketSignal> {
        val raw = prefs.getString("signals", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<MarketSignal>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSignals(signals: List<MarketSignal>) {
        val trimmed = signals.take(maxSignals)
        prefs.edit().putString("signals", json.encodeToString(trimmed)).apply()
    }

    fun addSignal(signalType: String, title: String, body: String, index: String) {
        val signals = loadSignals().toMutableList()
        val signal = MarketSignal(
            id = "${signalType}_${index}_${System.currentTimeMillis()}",
            signalType = signalType,
            title = title,
            body = body,
            index = index,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        signals.add(0, signal)
        saveSignals(signals)
    }

    fun markAsRead(signalId: String) {
        val signals = loadSignals().toMutableList()
        val idx = signals.indexOfFirst { it.id == signalId }
        if (idx >= 0) {
            signals[idx] = signals[idx].copy(isRead = true)
            saveSignals(signals)
        }
    }

    fun markAllAsRead() {
        val signals = loadSignals().map { it.copy(isRead = true) }
        saveSignals(signals)
    }

    fun deleteSignal(signalId: String) {
        val signals = loadSignals().filter { it.id != signalId }
        saveSignals(signals)
    }

    fun clearAll() {
        prefs.edit().remove("signals").apply()
    }

    fun unreadCount(): Int = loadSignals().count { !it.isRead }
}
