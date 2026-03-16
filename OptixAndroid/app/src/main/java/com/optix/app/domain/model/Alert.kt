package com.optix.app.domain.model

import java.time.LocalDateTime

/**
 * Alert type
 */
enum class AlertType(val value: String, val displayName: String) {
    SPOT_PRICE("spot_price", "Spot Price"),
    OPTION_PREMIUM("option_premium", "Option Premium"),
    PCR("pcr", "PCR"),
    OI_CHANGE("oi_change", "OI Change"),
    IV("iv", "IV")
}

/**
 * Alert condition
 */
enum class AlertCondition(val value: String, val displayName: String, val icon: String) {
    ABOVE("above", "Goes Above", "↑"),
    BELOW("below", "Goes Below", "↓"),
    CROSSES("crosses", "Crosses", "↔")
}

/**
 * Price alert
 */
data class Alert(
    val id: String,
    val userId: String,
    val alertType: AlertType,
    val symbol: String,
    val condition: AlertCondition,
    val targetValue: Double,
    val strikePrice: Double? = null,
    val optionType: OptionType? = null,
    val expiry: String? = null,
    val name: String? = null,
    val note: String? = null,
    val isActive: Boolean = true,
    val isTriggered: Boolean = false,
    val triggeredAt: LocalDateTime? = null,
    val triggeredValue: Double? = null,
    val lastCheckedValue: Double? = null,
    val lastCheckedAt: LocalDateTime? = null,
    val currentGap: Double? = null,
    val gapPercentage: Double? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    val displayName: String
        get() = name ?: buildDefaultName()

    private fun buildDefaultName(): String {
        return when (alertType) {
            AlertType.SPOT_PRICE -> "$symbol ${condition.displayName} $targetValue"
            AlertType.OPTION_PREMIUM -> "$symbol $strikePrice ${optionType?.code} ${condition.displayName} ₹$targetValue"
            AlertType.PCR -> "$symbol PCR ${condition.displayName} $targetValue"
            AlertType.OI_CHANGE -> "$symbol OI ${condition.displayName} $targetValue"
            AlertType.IV -> "$symbol IV ${condition.displayName} $targetValue%"
        }
    }

    val statusText: String
        get() = when {
            isTriggered -> "Triggered"
            isActive -> "Active"
            else -> "Paused"
        }
}

/**
 * Alert notification
 */
data class AlertNotification(
    val id: String,
    val alertId: String,
    val userId: String,
    val title: String,
    val message: String,
    val triggeredValue: Double? = null,
    val isRead: Boolean = false,
    val readAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val alertType: AlertType? = null,
    val symbol: String? = null
)

/**
 * Alert statistics
 */
data class AlertStats(
    val totalAlerts: Int = 0,
    val activeAlerts: Int = 0,
    val triggeredToday: Int = 0,
    val unreadNotifications: Int = 0,
    val alertsByType: Map<String, Int> = emptyMap(),
    val alertsBySymbol: Map<String, Int> = emptyMap()
)
