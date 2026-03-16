package com.optix.app.data.remote.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.optix.app.MainActivity
import com.optix.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OptixFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ALERTS_CHANNEL_ID = "alerts_channel"
        const val ALERTS_CHANNEL_NAME = "Price Alerts"
        const val GENERAL_CHANNEL_ID = "general_channel"
        const val GENERAL_CHANNEL_NAME = "General Notifications"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to server
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

            notificationManager.createNotificationChannels(listOf(alertsChannel, generalChannel))
        }
    }

    private suspend fun sendTokenToServer(token: String) {
        // Implementation to send FCM token to backend
        // This would typically call your API to register the device
    }
}
