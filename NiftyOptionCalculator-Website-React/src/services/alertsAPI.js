/**
 * Alerts API Service
 * Handles all alert-related API calls
 */

const API_URL = import.meta.env.VITE_API_URL || 'https://api.optix.d23ai.in'

// Get auth token from localStorage
const getAuthToken = () => {
  return localStorage.getItem('access_token')
}

// Common fetch wrapper with auth
const fetchWithAuth = async (endpoint, options = {}) => {
  const token = getAuthToken()
  if (!token) {
    throw new Error('Not authenticated')
  }

  const response = await fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      ...options.headers,
    },
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: 'Request failed' }))
    throw new Error(error.detail || 'Request failed')
  }

  return response.json()
}

// ============== Alert CRUD ==============

/**
 * Create a new alert
 */
export const createAlert = async (alertData) => {
  return fetchWithAuth('/api/v1/alerts', {
    method: 'POST',
    body: JSON.stringify(alertData),
  })
}

/**
 * Get all alerts for the current user
 */
export const getAlerts = async (filters = {}) => {
  const params = new URLSearchParams()
  if (filters.is_active !== undefined) params.append('is_active', filters.is_active)
  if (filters.symbol) params.append('symbol', filters.symbol)
  if (filters.alert_type) params.append('alert_type', filters.alert_type)

  const queryString = params.toString()
  return fetchWithAuth(`/api/v1/alerts${queryString ? `?${queryString}` : ''}`)
}

/**
 * Get a specific alert
 */
export const getAlert = async (alertId) => {
  return fetchWithAuth(`/api/v1/alerts/${alertId}`)
}

/**
 * Update an alert
 */
export const updateAlert = async (alertId, alertData) => {
  return fetchWithAuth(`/api/v1/alerts/${alertId}`, {
    method: 'PUT',
    body: JSON.stringify(alertData),
  })
}

/**
 * Delete an alert
 */
export const deleteAlert = async (alertId) => {
  return fetchWithAuth(`/api/v1/alerts/${alertId}`, {
    method: 'DELETE',
  })
}

/**
 * Get alert statistics
 */
export const getAlertStats = async () => {
  return fetchWithAuth('/api/v1/alerts/stats')
}

/**
 * Check a single alert manually
 */
export const checkAlert = async (alertId) => {
  return fetchWithAuth(`/api/v1/alerts/${alertId}/check`)
}

// ============== Notifications ==============

/**
 * Get notifications
 */
export const getNotifications = async (options = {}) => {
  const params = new URLSearchParams()
  if (options.limit) params.append('limit', options.limit)
  if (options.offset) params.append('offset', options.offset)
  if (options.unread_only) params.append('unread_only', 'true')

  const queryString = params.toString()
  return fetchWithAuth(`/api/v1/alerts/notifications/list${queryString ? `?${queryString}` : ''}`)
}

/**
 * Mark notifications as read
 */
export const markNotificationsRead = async (notificationIds) => {
  return fetchWithAuth('/api/v1/alerts/notifications/mark-read', {
    method: 'POST',
    body: JSON.stringify({ notification_ids: notificationIds }),
  })
}

/**
 * Mark all notifications as read
 */
export const markAllNotificationsRead = async () => {
  return fetchWithAuth('/api/v1/alerts/notifications/mark-all-read', {
    method: 'POST',
  })
}

/**
 * Get alert service status
 */
export const getAlertServiceStatus = async () => {
  const response = await fetch(`${API_URL}/api/v1/alerts/service/status`)
  return response.json()
}

export default {
  createAlert,
  getAlerts,
  getAlert,
  updateAlert,
  deleteAlert,
  getAlertStats,
  checkAlert,
  getNotifications,
  markNotificationsRead,
  markAllNotificationsRead,
  getAlertServiceStatus,
}
