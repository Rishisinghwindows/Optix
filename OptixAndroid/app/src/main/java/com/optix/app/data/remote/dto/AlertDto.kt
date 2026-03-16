package com.optix.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("alert_type") val alertType: String,
    val symbol: String,
    val condition: String,
    @SerialName("target_value") val targetValue: Double,
    @SerialName("strike_price") val strikePrice: Double? = null,
    @SerialName("option_type") val optionType: String? = null,
    val expiry: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_triggered") val isTriggered: Boolean = false,
    @SerialName("triggered_at") val triggeredAt: String? = null,
    @SerialName("triggered_value") val triggeredValue: Double? = null,
    @SerialName("last_checked_value") val lastCheckedValue: Double? = null,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val note: String? = null,
    @SerialName("current_gap") val currentGap: Double? = null,
    @SerialName("gap_percentage") val gapPercentage: Double? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class AlertsResponse(
    val alerts: List<AlertDto>,
    val total: Int,
    @SerialName("active_count") val activeCount: Int,
    @SerialName("triggered_count") val triggeredCount: Int
)

@Serializable
data class CreateAlertRequest(
    @SerialName("alert_type") val alertType: String,
    val symbol: String,
    val condition: String,
    @SerialName("target_value") val targetValue: Double,
    @SerialName("strike_price") val strikePrice: Double? = null,
    @SerialName("option_type") val optionType: String? = null,
    val expiry: String? = null,
    val name: String? = null,
    val note: String? = null
)

@Serializable
data class UpdateAlertRequest(
    @SerialName("target_value") val targetValue: Double? = null,
    val condition: String? = null,
    val name: String? = null,
    val note: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null
)

@Serializable
data class AlertStatsDto(
    @SerialName("total_alerts") val totalAlerts: Int,
    @SerialName("active_alerts") val activeAlerts: Int,
    @SerialName("triggered_today") val triggeredToday: Int,
    @SerialName("unread_notifications") val unreadNotifications: Int,
    @SerialName("alerts_by_type") val alertsByType: Map<String, Int> = emptyMap(),
    @SerialName("alerts_by_symbol") val alertsBySymbol: Map<String, Int> = emptyMap()
)

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("alert_id") val alertId: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val message: String,
    @SerialName("triggered_value") val triggeredValue: Double? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("alert_type") val alertType: String? = null,
    val symbol: String? = null
)

@Serializable
data class NotificationsResponse(
    val notifications: List<NotificationDto>,
    val total: Int,
    @SerialName("unread_count") val unreadCount: Int
)

@Serializable
data class MarkReadRequest(
    @SerialName("notification_ids") val notificationIds: List<String>
)

@Serializable
data class MarkReadResponse(
    @SerialName("marked_count") val markedCount: Int,
    val message: String
)
