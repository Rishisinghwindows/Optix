package com.optix.app.domain.usecase.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.optix.app.data.local.datastore.AIPreferencesDataStore
import com.optix.app.domain.model.AITradeSuggestion
import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.GreeksRecommendation
import com.optix.app.domain.model.PersonalizedAITradeSuggestion
import com.optix.app.domain.model.RecommendationSeverity
import com.optix.app.domain.model.SmartMoneySignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for sending AI-related notifications to users
 */
@Singleton
class AINotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataStore: AIPreferencesDataStore
) {

    companion object {
        // Notification channels
        const val CHANNEL_HIGH_CONFIDENCE = "ai_high_confidence"
        const val CHANNEL_SMART_MONEY = "ai_smart_money"
        const val CHANNEL_GREEKS_ALERT = "ai_greeks_alert"
        const val CHANNEL_WATCHLIST = "ai_watchlist"

        // Notification IDs
        private const val HIGH_CONFIDENCE_NOTIFICATION_ID = 1001
        private const val SMART_MONEY_NOTIFICATION_ID = 1002
        private const val GREEKS_ALERT_NOTIFICATION_ID = 1003
        private const val WATCHLIST_NOTIFICATION_ID = 1004

        // Group keys
        private const val GROUP_AI_INSIGHTS = "ai_insights"
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for AI alerts
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            // High confidence suggestions channel
            val highConfidenceChannel = NotificationChannel(
                CHANNEL_HIGH_CONFIDENCE,
                "High Confidence AI Suggestions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for high confidence AI trade suggestions"
                enableVibration(true)
            }

            // Smart money alerts channel
            val smartMoneyChannel = NotificationChannel(
                CHANNEL_SMART_MONEY,
                "Smart Money Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for smart money activity detection"
            }

            // Greeks alerts channel
            val greeksChannel = NotificationChannel(
                CHANNEL_GREEKS_ALERT,
                "Greeks Risk Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for critical Greeks-based risk alerts"
                enableVibration(true)
            }

            // Watchlist alerts channel
            val watchlistChannel = NotificationChannel(
                CHANNEL_WATCHLIST,
                "Watchlist Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for watchlist item updates"
            }

            notificationManager.createNotificationChannels(
                listOf(highConfidenceChannel, smartMoneyChannel, greeksChannel, watchlistChannel)
            )
        }
    }

    /**
     * Notify user of high confidence AI suggestion
     */
    suspend fun notifyHighConfidenceSuggestion(suggestion: AITradeSuggestion) {
        val preferences = preferencesDataStore.aiPreferences.first()

        if (!preferences.notifyOnHighConfidence) return
        if (suggestion.score < preferences.highConfidenceThreshold) return

        val title = "High Confidence Trade: ${suggestion.symbol}"
        val content = "${suggestion.action.name} ${suggestion.optionType.displayName} " +
                "@ ${String.format("%.2f", suggestion.strikePrice)} - Score: ${suggestion.score}%"

        val notification = NotificationCompat.Builder(context, CHANNEL_HIGH_CONFIDENCE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$content\n\n" +
                "Entry: ₹${String.format("%.2f", suggestion.entryPrice)}\n" +
                "Target: ₹${String.format("%.2f", suggestion.targetPrice)}\n" +
                "Stop Loss: ₹${String.format("%.2f", suggestion.stopLoss)}\n\n" +
                "Risk/Reward: ${String.format("%.2f", suggestion.riskReward)}"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_AI_INSIGHTS)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(HIGH_CONFIDENCE_NOTIFICATION_ID + suggestion.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Notify user of personalized high-value suggestion
     */
    suspend fun notifyPersonalizedSuggestion(suggestion: PersonalizedAITradeSuggestion) {
        if (!suggestion.isHighlyPersonalized) return

        val preferences = preferencesDataStore.aiPreferences.first()
        if (!preferences.notifyOnHighConfidence) return

        val title = "Personalized Trade: ${suggestion.baseSuggestion.symbol}"
        val reasons = suggestion.relevanceReasons.take(3).joinToString(" • ")

        val notification = NotificationCompat.Builder(context, CHANNEL_HIGH_CONFIDENCE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(reasons)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${suggestion.baseSuggestion.actionText} @ " +
                "${String.format("%.2f", suggestion.baseSuggestion.strikePrice)}\n\n" +
                "Score: ${suggestion.totalScore}% (includes personalization)\n\n" +
                "Why this matters:\n${suggestion.relevanceReasons.joinToString("\n• ", "• ")}"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_AI_INSIGHTS)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(HIGH_CONFIDENCE_NOTIFICATION_ID + suggestion.baseSuggestion.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Notify user of smart money signal
     */
    fun notifySmartMoneySignal(signal: SmartMoneySignal) {
        if (signal.confidence != ConfidenceLevel.HIGH) return

        val title = "Smart Money: ${signal.signalType.displayName}"
        val content = "${signal.optionType.displayName} @ ${String.format("%.2f", signal.strikePrice)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_SMART_MONEY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$content\n\n${signal.interpretation}"
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_AI_INSIGHTS)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(SMART_MONEY_NOTIFICATION_ID + signal.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Notify user of critical Greeks alert
     */
    fun notifyGreeksAlert(recommendation: GreeksRecommendation) {
        if (recommendation.severity != RecommendationSeverity.CRITICAL) return

        val title = "Greeks Alert: ${recommendation.title}"
        val content = recommendation.description

        val notification = NotificationCompat.Builder(context, CHANNEL_GREEKS_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$content\n\n" +
                "Action: ${recommendation.actionSuggested}\n\n" +
                "${recommendation.affectedGreek}: ${String.format("%.4f", recommendation.currentValue)} " +
                "(threshold: ${String.format("%.4f", recommendation.thresholdValue)})"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_AI_INSIGHTS)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(GREEKS_ALERT_NOTIFICATION_ID + recommendation.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Notify user of watchlist item alert
     */
    fun notifyWatchlistAlert(
        symbol: String,
        strikePrice: Double?,
        message: String
    ) {
        val title = "Watchlist: $symbol" + (strikePrice?.let { " @ ${String.format("%.2f", it)}" } ?: "")

        val notification = NotificationCompat.Builder(context, CHANNEL_WATCHLIST)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_AI_INSIGHTS)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(WATCHLIST_NOTIFICATION_ID + symbol.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Send summary notification for multiple alerts
     */
    fun notifyInsightsSummary(
        highConfidenceCount: Int,
        smartMoneyCount: Int,
        greeksAlertCount: Int
    ) {
        if (highConfidenceCount + smartMoneyCount + greeksAlertCount == 0) return

        val parts = mutableListOf<String>()
        if (highConfidenceCount > 0) parts.add("$highConfidenceCount high-confidence trades")
        if (smartMoneyCount > 0) parts.add("$smartMoneyCount smart money signals")
        if (greeksAlertCount > 0) parts.add("$greeksAlertCount Greeks alerts")

        val notification = NotificationCompat.Builder(context, CHANNEL_HIGH_CONFIDENCE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AI Insights Summary")
            .setContentText(parts.joinToString(", "))
            .setGroup(GROUP_AI_INSIGHTS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(0, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /**
     * Cancel all AI notifications
     */
    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
