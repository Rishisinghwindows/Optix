package com.optix.app.data.repository

import com.optix.app.core.util.Resource
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.data.remote.dto.AlertDto
import com.optix.app.data.remote.dto.CreateAlertRequest
import com.optix.app.data.remote.dto.MarkReadRequest
import com.optix.app.data.remote.dto.UpdateAlertRequest
import com.optix.app.domain.model.Alert
import com.optix.app.domain.model.AlertCondition
import com.optix.app.domain.model.AlertNotification
import com.optix.app.domain.model.AlertStats
import com.optix.app.domain.model.AlertType
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.repository.AlertRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val api: OptixApiService
) : AlertRepository {

    private val dateFormatter = DateTimeFormatter.ISO_DATE_TIME

    private fun AlertDto.toAlert(): Alert {
        return Alert(
            id = id,
            userId = userId,
            alertType = AlertType.entries.find { it.value == alertType } ?: AlertType.SPOT_PRICE,
            symbol = symbol,
            condition = AlertCondition.entries.find { it.value == condition } ?: AlertCondition.ABOVE,
            targetValue = targetValue,
            strikePrice = strikePrice,
            optionType = optionType?.let { type -> OptionType.entries.find { it.code == type } },
            expiry = expiry,
            name = name,
            note = note,
            isActive = isActive,
            isTriggered = isTriggered,
            triggeredAt = triggeredAt?.let { parseDateTime(it) },
            triggeredValue = triggeredValue,
            lastCheckedValue = lastCheckedValue,
            lastCheckedAt = lastCheckedAt?.let { parseDateTime(it) },
            currentGap = currentGap,
            gapPercentage = gapPercentage,
            createdAt = parseDateTime(createdAt) ?: LocalDateTime.now(),
            updatedAt = parseDateTime(updatedAt) ?: LocalDateTime.now()
        )
    }

    private fun parseDateTime(dateString: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(dateString, dateFormatter)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAlerts(): Resource<List<Alert>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAlerts()
            if (response.isSuccessful && response.body() != null) {
                val alerts = response.body()!!.alerts.map { it.toAlert() }
                Resource.Success(alerts)
            } else {
                Resource.Error("Failed to fetch alerts")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun getAlert(alertId: String): Resource<Alert> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAlert(alertId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.toAlert())
            } else {
                Resource.Error("Failed to fetch alert")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun createAlert(alert: Alert): Resource<Alert> = withContext(Dispatchers.IO) {
        try {
            val request = CreateAlertRequest(
                alertType = alert.alertType.value,
                symbol = alert.symbol,
                condition = alert.condition.value,
                targetValue = alert.targetValue,
                strikePrice = alert.strikePrice,
                optionType = alert.optionType?.code,
                expiry = alert.expiry,
                name = alert.name,
                note = alert.note
            )
            val response = api.createAlert(request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.toAlert())
            } else {
                Resource.Error("Failed to create alert")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun toggleAlert(alertId: String): Resource<Alert> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.toggleAlert(alertId)
                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!.toAlert())
                } else {
                    Resource.Error("Failed to toggle alert")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun updateAlert(alert: Alert): Resource<Alert> = withContext(Dispatchers.IO) {
        try {
            val request = UpdateAlertRequest(
                targetValue = alert.targetValue,
                condition = alert.condition.value,
                name = alert.name,
                note = alert.note,
                isActive = alert.isActive
            )
            val response = api.updateAlert(alert.id, request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.toAlert())
            } else {
                Resource.Error("Failed to update alert")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun deleteAlert(alertId: String): Resource<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.deleteAlert(alertId)
                if (response.isSuccessful) {
                    Resource.Success(true)
                } else {
                    Resource.Error("Failed to delete alert")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun getAlertStats(): Resource<AlertStats> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAlertStats()
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                Resource.Success(
                    AlertStats(
                        totalAlerts = dto.totalAlerts,
                        activeAlerts = dto.activeAlerts,
                        triggeredToday = dto.triggeredToday,
                        unreadNotifications = dto.unreadNotifications,
                        alertsByType = dto.alertsByType,
                        alertsBySymbol = dto.alertsBySymbol
                    )
                )
            } else {
                Resource.Error("Failed to fetch alert stats")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun getNotifications(
        limit: Int,
        offset: Int,
        unreadOnly: Boolean
    ): Resource<List<AlertNotification>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getNotifications(limit, offset, unreadOnly)
            if (response.isSuccessful && response.body() != null) {
                val notifications = response.body()!!.notifications.map { dto ->
                    AlertNotification(
                        id = dto.id,
                        alertId = dto.alertId,
                        userId = dto.userId,
                        title = dto.title,
                        message = dto.message,
                        triggeredValue = dto.triggeredValue,
                        isRead = dto.isRead,
                        readAt = dto.readAt?.let { parseDateTime(it) },
                        createdAt = parseDateTime(dto.createdAt) ?: LocalDateTime.now(),
                        alertType = dto.alertType?.let { type ->
                            AlertType.entries.find { it.value == type }
                        },
                        symbol = dto.symbol
                    )
                }
                Resource.Success(notifications)
            } else {
                Resource.Error("Failed to fetch notifications")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun markNotificationsRead(notificationIds: List<String>): Resource<Int> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.markNotificationsRead(MarkReadRequest(notificationIds))
                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!.markedCount)
                } else {
                    Resource.Error("Failed to mark notifications as read")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun markAllNotificationsRead(): Resource<Int> = withContext(Dispatchers.IO) {
        try {
            val response = api.markAllNotificationsRead()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.markedCount)
            } else {
                Resource.Error("Failed to mark all notifications as read")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }
}
