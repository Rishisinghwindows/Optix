package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.*

/**
 * Repository interface for price alerts
 */
interface AlertRepository {
    /**
     * Get all alerts for current user
     */
    suspend fun getAlerts(): Resource<List<Alert>>

    /**
     * Get a specific alert
     */
    suspend fun getAlert(alertId: String): Resource<Alert>

    /**
     * Create a new alert
     */
    suspend fun createAlert(alert: Alert): Resource<Alert>

    /**
     * Toggle alert active state
     */
    suspend fun toggleAlert(alertId: String): Resource<Alert>

    /**
     * Update an alert
     */
    suspend fun updateAlert(alert: Alert): Resource<Alert>

    /**
     * Delete an alert
     */
    suspend fun deleteAlert(alertId: String): Resource<Boolean>

    /**
     * Get alert statistics
     */
    suspend fun getAlertStats(): Resource<AlertStats>

    /**
     * Get notifications
     */
    suspend fun getNotifications(
        limit: Int = 50,
        offset: Int = 0,
        unreadOnly: Boolean = false
    ): Resource<List<AlertNotification>>

    /**
     * Mark notifications as read
     */
    suspend fun markNotificationsRead(notificationIds: List<String>): Resource<Int>

    /**
     * Mark all notifications as read
     */
    suspend fun markAllNotificationsRead(): Resource<Int>
}
