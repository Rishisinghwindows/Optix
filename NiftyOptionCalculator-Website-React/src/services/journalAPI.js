/**
 * Trade Journal API Service
 * Handles all journal-related API calls
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

// ============== Journal CRUD ==============

export async function createJournalEntry(data) {
  return fetchWithAuth('/api/v1/journal', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getJournalEntries(filters = {}) {
  const params = new URLSearchParams()
  if (filters.date_from) params.append('date_from', filters.date_from)
  if (filters.date_to) params.append('date_to', filters.date_to)
  if (filters.tag) params.append('tag', filters.tag)
  if (filters.outcome) params.append('outcome', filters.outcome)
  if (filters.symbol) params.append('symbol', filters.symbol)
  if (filters.mood) params.append('mood', filters.mood)
  if (filters.limit) params.append('limit', filters.limit)
  if (filters.offset) params.append('offset', filters.offset)

  const query = params.toString()
  return fetchWithAuth(`/api/v1/journal${query ? `?${query}` : ''}`)
}

export async function getJournalEntry(entryId) {
  return fetchWithAuth(`/api/v1/journal/${entryId}`)
}

export async function updateJournalEntry(entryId, data) {
  return fetchWithAuth(`/api/v1/journal/${entryId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export async function deleteJournalEntry(entryId) {
  return fetchWithAuth(`/api/v1/journal/${entryId}`, {
    method: 'DELETE',
  })
}

export async function getJournalStats(filters = {}) {
  const params = new URLSearchParams()
  if (filters.date_from) params.append('date_from', filters.date_from)
  if (filters.date_to) params.append('date_to', filters.date_to)

  const query = params.toString()
  return fetchWithAuth(`/api/v1/journal/stats${query ? `?${query}` : ''}`)
}
