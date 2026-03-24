package com.optix.app.data.remote.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.optix.app.MainActivity
import com.optix.app.R
import com.optix.app.data.repository.AuthRepositoryImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OptixFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ALERTS_CHANNEL_ID = "alerts_channel"
        const val ALERTS_CHANNEL_NAME = "Price Alerts"
        const val GENERAL_CHANNEL_ID = "general_channel"
        const val GENERAL_CHANNEL_NAME = "General Notifications"
        const val MARKET_MONITOR_CHANNEL_ID = "market_monitor_channel"
        const val MARKET_MONITOR_CHANNEL_NAME = "Market Signals"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Register the new FCM token with the backend so push notifications
        // can be targeted to this device. Runs on IO via serviceScope.
        serviceScope.launch {
            sendTokenToServer(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Handle data payload
        message.data.isNotEmpty().let {
            handleDataMessage(message.data)
        }

        // Handle notification payload
        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: getString(R.string.app_name),
                body = notification.body ?: "",
                data = message.data
            )
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: return

        when (type) {
            "price_alert" -> handlePriceAlert(data)
            "ai_suggestion" -> handleAISuggestion(data)
            "market_update" -> handleMarketUpdate(data)
            "market_monitor" -> handleMarketMonitor(data)
        }
    }

    private fun handlePriceAlert(data: Map<String, String>) {
        val alertName = data["alert_name"] ?: "Price Alert"
        val symbol = data["symbol"] ?: ""
        val condition = data["condition"] ?: ""
        val value = data["value"] ?: ""

        showNotification(
            title = alertName,
            body = "$symbol $condition ₹$value",
            channelId = ALERTS_CHANNEL_ID,
            data = data
        )
    }

    private fun handleAISuggestion(data: Map<String, String>) {
        val title = data["title"] ?: "AI Trade Suggestion"
        val body = data["body"] ?: ""

        showNotification(
            title = title,
            body = body,
            channelId = GENERAL_CHANNEL_ID,
            data = data
        )
    }

    private fun handleMarketUpdate(data: Map<String, String>) {
        val title = data["title"] ?: "Market Update"
        val body = data["body"] ?: ""

        showNotification(
            title = title,
            body = body,
            channelId = GENERAL_CHANNEL_ID,
            data = data
        )
    }

    private fun handleMarketMonitor(data: Map<String, String>) {
        val signal = data["signal"] ?: ""
        val title = data["title"] ?: "Market Signal"
        val body = data["body"] ?: ""
        val index = data["index"] ?: ""

        // Store signal locally
        try {
            val store = com.optix.app.data.local.MarketSignalStore(this)
            store.addSignal(signal, title, body, index)
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Failed to store market signal: ${e.message}")
        }

        val emoji = when (signal) {
            "long_buildup" -> "\uD83D\uDCC8"    // 📈
            "short_buildup" -> "\uD83D\uDCC9"   // 📉
            "long_unwinding" -> "⚠\uFE0F"       // ⚠️
            "short_covering" -> "\uD83D\uDE80"   // 🚀
            "pcr_shift" -> "\uD83D\uDD04"        // 🔄
            "vix_change" -> "\u26A1"             // ⚡
            "oi_surge" -> "\uD83D\uDCA5"         // 💥
            "max_pain_shift" -> "\uD83C\uDFAF"   // 🎯
            else -> "\uD83D\uDCCA"               // 📊
        }

        showNotification(
            title = "$emoji $title",
            body = body,
            channelId = MARKET_MONITOR_CHANNEL_ID,
            data = data
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        channelId: String = GENERAL_CHANNEL_ID,
        data: Map<String, String> = emptyMap()
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Alerts channel
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                ALERTS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for price alerts"
                enableVibration(true)
                enableLights(true)
            }

            // General channel
            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                GENERAL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }

            // Market monitor channel
            val marketMonitorChannel = NotificationChannel(
                MARKET_MONITOR_CHANNEL_ID,
                MARKET_MONITOR_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Significant market changes — PCR shifts, OI buildup/unwinding, VIX spikes"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannels(
                listOf(alertsChannel, generalChannel, marketMonitorChannel)
            )
        }
    }

    /**
     * Registers the FCM device token with the Optix backend.
     *
     * Uses raw HttpURLConnection (not Retrofit) because this runs inside a
     * FirebaseMessagingService where Hilt injection of the API service is not
     * reliably available at the time [onNewToken] fires.
     *
     * Auth is optional: unauthenticated (guest) devices still register so they
     * can receive broadcast notifications (e.g. market signals).
     *
     * On failure, retries up to 3 times with exponential backoff via [retryIfNeeded].
     */
    private suspend fun sendTokenToServer(token: String, retryCount: Int = 0) {
        try {
            val url = java.net.URL("https://api.optix.d23ai.in/api/v1/devices/register")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Attach auth token if available (optional — guest devices can still receive broadcasts)
            try {
                val accessToken = dataStore.data.first()[AuthRepositoryImpl.ACCESS_TOKEN_KEY]
                if (accessToken != null) {
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                }
            } catch (_: Exception) { /* proceed without auth */ }

            connection.doOutput = true

            val body = """{"token":"$token","platform":"android","device_name":"${android.os.Build.MODEL}"}"""
            connection.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                android.util.Log.d("FCM", "Token registered with backend")
            } else {
                android.util.Log.e("FCM", "Token registration failed: HTTP $responseCode")
                retryIfNeeded(token, retryCount)
            }
            connection.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Token registration error: ${e.message}")
            retryIfNeeded(token, retryCount)
        }
    }

    // Exponential backoff retry: 1s, 2s, 4s — max 3 attempts
    private suspend fun retryIfNeeded(token: String, retryCount: Int) {
        if (retryCount < 3) {
            val delayMs = (1L shl retryCount) * 1000 // 1s, 2s, 4s
            android.util.Log.d("FCM", "Retrying token registration in ${delayMs}ms (attempt ${retryCount + 1}/3)")
            kotlinx.coroutines.delay(delayMs)
            sendTokenToServer(token, retryCount + 1)
        }
    }
}
