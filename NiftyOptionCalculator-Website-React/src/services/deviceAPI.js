/**
 * Device API Service
 * Handles FCM token registration for push notifications
 */

const API_URL = import.meta.env.VITE_API_URL || 'https://api.optix.d23ai.in'

const getAuthToken = () => localStorage.getItem('access_token')

const fetchWithAuth = async (endpoint, options = {}) => {
  const token = getAuthToken()
  if (!token) throw new Error('Not authenticated')

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

export async function registerDevice(token, platform = 'web', deviceName = null) {
  return fetchWithAuth('/api/v1/devices/register', {
    method: 'POST',
    body: JSON.stringify({ token, platform, device_name: deviceName }),
  })
}

export async function listDevices() {
  return fetchWithAuth('/api/v1/devices')
}

export async function unregisterDevice(token) {
  return fetchWithAuth(`/api/v1/devices/${encodeURIComponent(token)}`, {
    method: 'DELETE',
  })
}
