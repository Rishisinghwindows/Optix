package com.optix.app.data.local

import android.content.Context
import com.optix.app.domain.model.AITradeSuggestion
import com.optix.app.domain.model.PickOutcome
import com.optix.app.domain.model.ScorecardStats
import com.optix.app.domain.model.TrackedPick
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIScorecardDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("ai_scorecard", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val maxPicks = 100

    fun loadPicks(): List<TrackedPick> {
        val raw = prefs.getString("picks", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<TrackedPick>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePicks(picks: List<TrackedPick>) {
        val trimmed = picks.takeLast(maxPicks)
        prefs.edit().putString("picks", json.encodeToString(trimmed)).apply()
    }

    fun trackSuggestions(suggestions: List<AITradeSuggestion>, indexName: String) {
        val picks = loadPicks().toMutableList()
        val existingIds = picks.map { it.id }.toSet()

        for (suggestion in suggestions) {
            val pick = TrackedPick.from(suggestion, indexName)
            if (pick.id !in existingIds) {
                picks.add(pick)
            }
        }

        savePicks(picks)
    }

    fun resolveOutcomes(currentPrices: Map<String, Double>) {
        val picks = loadPicks().toMutableList()
        var changed = false
        val now = System.currentTimeMillis()

        for (i in picks.indices) {
            if (picks[i].outcome != PickOutcome.ACTIVE) continue

            val currentPrice = currentPrices[picks[i].id]

            if (currentPrice != null) {
                // Update HWM
                if (currentPrice > (picks[i].highWaterMark ?: 0.0)) {
                    picks[i] = picks[i].copy(highWaterMark = currentPrice)
                    changed = true
                }
                // Target hit
                if (currentPrice >= picks[i].targetPrice) {
                    picks[i] = picks[i].copy(
                        outcome = PickOutcome.WIN,
                        exitPrice = picks[i].targetPrice,
                        returnPct = ((picks[i].targetPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100,
                        resolvedAt = now
                    )
                    changed = true
                }
                // SL hit
                else if (currentPrice <= picks[i].stopLossPrice) {
                    picks[i] = picks[i].copy(
                        outcome = PickOutcome.LOSS,
                        exitPrice = picks[i].stopLossPrice,
                        returnPct = ((picks[i].stopLossPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100,
                        resolvedAt = now
                    )
                    changed = true
                }
            }
        }
        if (changed) savePicks(picks)
    }

    fun getStats(): ScorecardStats {
        val picks = loadPicks()
        val resolved = picks.filter { it.outcome != PickOutcome.ACTIVE }
        val wins = resolved.filter { it.outcome == PickOutcome.WIN }
        val losses = resolved.filter { it.outcome == PickOutcome.LOSS }
        val expired = resolved.filter { it.outcome == PickOutcome.EXPIRED }
        val active = picks.filter { it.outcome == PickOutcome.ACTIVE }

        val winRate = if (resolved.isNotEmpty()) (wins.size.toDouble() / resolved.size) * 100 else 0.0
        val avgReturn = if (resolved.isNotEmpty()) {
            val returns = resolved.mapNotNull { it.returnPct }
            if (returns.isNotEmpty()) returns.average() else 0.0
        } else 0.0

        val topPickResolved = resolved.filter { it.tier == "topPick" }
        val topPickWins = topPickResolved.filter { it.outcome == PickOutcome.WIN }
        val topPickWinRate = if (topPickResolved.isNotEmpty()) (topPickWins.size.toDouble() / topPickResolved.size) * 100 else 0.0

        return ScorecardStats(
            totalPicks = picks.size,
            activePicks = active.size,
            wins = wins.size,
            losses = losses.size,
            expired = expired.size,
            winRate = winRate,
            avgReturn = avgReturn,
            topPickWinRate = topPickWinRate,
            recentPicks = picks.sortedByDescending { it.createdAt }.take(10)
        )
    }

    fun clear() {
        prefs.edit().remove("picks").apply()
    }
}
