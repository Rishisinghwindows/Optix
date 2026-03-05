import React, { useState, useEffect, useCallback } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import {
  getAlerts,
  createAlert,
  updateAlert,
  deleteAlert,
  getAlertStats,
  getNotifications,
  markNotificationsRead,
  markAllNotificationsRead,
} from '../../services/alertsAPI'
import './Alerts.css'

// Alert type options
const ALERT_TYPES = [
  { value: 'spot_price', label: 'Spot Price', description: 'Get notified when index reaches a price level' },
  { value: 'option_premium', label: 'Option Premium', description: 'Track specific option contract prices' },
  { value: 'pcr', label: 'PCR', description: 'Monitor Put-Call Ratio changes' },
]

const SYMBOLS = [
  { value: 'NIFTY', label: 'NIFTY 50' },
  { value: 'BANKNIFTY', label: 'Bank Nifty' },
  { value: 'FINNIFTY', label: 'Fin Nifty' },
  { value: 'MIDCPNIFTY', label: 'Midcap Nifty' },
  { value: 'SENSEX', label: 'Sensex' },
]

const CONDITIONS = [
  { value: 'above', label: 'Goes Above', icon: '↑' },
  { value: 'below', label: 'Goes Below', icon: '↓' },
  { value: 'crosses', label: 'Crosses', icon: '↔' },
]

function CreateAlertModal({ isOpen, onClose, onCreated }) {
  const [formData, setFormData] = useState({
    alert_type: 'spot_price',
    symbol: 'NIFTY',
    condition: 'above',
    target_value: '',
    strike_price: '',
    option_type: 'CE',
    expiry: '',
    name: '',
    note: '',
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      const alertData = {
        alert_type: formData.alert_type,
        symbol: formData.symbol,
        condition: formData.condition,
        target_value: parseFloat(formData.target_value),
        name: formData.name || undefined,
        note: formData.note || undefined,
      }

      // Add option fields if needed
      if (formData.alert_type === 'option_premium') {
        alertData.strike_price = parseFloat(formData.strike_price)
        alertData.option_type = formData.option_type
        alertData.expiry = formData.expiry
      }

      await createAlert(alertData)
      onCreated()
      onClose()
      setFormData({
        alert_type: 'spot_price',
        symbol: 'NIFTY',
        condition: 'above',
        target_value: '',
        strike_price: '',
        option_type: 'CE',
        expiry: '',
        name: '',
        note: '',
      })
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content create-alert-modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2>Create Price Alert</h2>
          <button className="modal-close" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit} className="alert-form">
          {error && <div className="alert-error">{error}</div>}

          <div className="form-section">
            <label>Alert Type</label>
            <div className="alert-type-options">
              {ALERT_TYPES.map(type => (
                <button
                  key={type.value}
                  type="button"
                  className={`type-option ${formData.alert_type === type.value ? 'active' : ''}`}
                  onClick={() => setFormData({ ...formData, alert_type: type.value })}
                >
                  <span className="type-label">{type.label}</span>
                  <span className="type-desc">{type.description}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Symbol</label>
              <select
                value={formData.symbol}
                onChange={e => setFormData({ ...formData, symbol: e.target.value })}
              >
                {SYMBOLS.map(s => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label>Condition</label>
              <select
                value={formData.condition}
                onChange={e => setFormData({ ...formData, condition: e.target.value })}
              >
                {CONDITIONS.map(c => (
                  <option key={c.value} value={c.value}>{c.icon} {c.label}</option>
                ))}
              </select>
            </div>
          </div>

          {formData.alert_type === 'option_premium' && (
            <div className="form-row option-fields">
              <div className="form-group">
                <label>Strike Price</label>
                <input
                  type="number"
                  value={formData.strike_price}
                  onChange={e => setFormData({ ...formData, strike_price: e.target.value })}
                  placeholder="e.g., 26000"
                  required
                />
              </div>
              <div className="form-group">
                <label>Type</label>
                <select
                  value={formData.option_type}
                  onChange={e => setFormData({ ...formData, option_type: e.target.value })}
                >
                  <option value="CE">Call (CE)</option>
                  <option value="PE">Put (PE)</option>
                </select>
              </div>
              <div className="form-group">
                <label>Expiry</label>
                <input
                  type="text"
                  value={formData.expiry}
                  onChange={e => setFormData({ ...formData, expiry: e.target.value })}
                  placeholder="e.g., 13-Feb-2026"
                  required
                />
              </div>
            </div>
          )}

          <div className="form-group">
            <label>Target Value {formData.alert_type === 'pcr' ? '(PCR)' : '(Price)'}</label>
            <input
              type="number"
              step={formData.alert_type === 'pcr' ? '0.01' : '0.5'}
              value={formData.target_value}
              onChange={e => setFormData({ ...formData, target_value: e.target.value })}
              placeholder={formData.alert_type === 'pcr' ? 'e.g., 0.85' : 'e.g., 26000'}
              required
            />
          </div>

          <div className="form-group">
            <label>Alert Name (Optional)</label>
            <input
              type="text"
              value={formData.name}
              onChange={e => setFormData({ ...formData, name: e.target.value })}
              placeholder="e.g., NIFTY breakout level"
              maxLength={100}
            />
          </div>

          <div className="form-group">
            <label>Note (Optional)</label>
            <textarea
              value={formData.note}
              onChange={e => setFormData({ ...formData, note: e.target.value })}
              placeholder="Add notes about this alert..."
              rows={2}
            />
          </div>

          <div className="form-actions">
            <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Creating...' : 'Create Alert'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function NotificationsPanel({ isOpen, onClose, notifications, onMarkRead }) {
  if (!isOpen) return null

  return (
    <div className="notifications-panel">
      <div className="notifications-header">
        <h3>Notifications</h3>
        <button className="mark-all-read" onClick={() => onMarkRead('all')}>
          Mark all as read
        </button>
      </div>
      <div className="notifications-list">
        {notifications.length === 0 ? (
          <div className="no-notifications">
            <span className="bell-icon">🔔</span>
            <p>No notifications yet</p>
          </div>
        ) : (
          notifications.map(n => (
            <div
              key={n.id}
              className={`notification-item ${n.is_read ? 'read' : 'unread'}`}
              onClick={() => !n.is_read && onMarkRead([n.id])}
            >
              <div className="notification-icon">
                {n.alert_type === 'spot_price' && '📈'}
                {n.alert_type === 'option_premium' && '📊'}
                {n.alert_type === 'pcr' && '⚖️'}
              </div>
              <div className="notification-content">
                <h4>{n.title}</h4>
                <p>{n.message}</p>
                <span className="notification-time">
                  {new Date(n.created_at).toLocaleString()}
                </span>
              </div>
              {!n.is_read && <span className="unread-dot" />}
            </div>
          ))
        )}
      </div>
    </div>
  )
}

function AlertCard({ alert, onToggle, onDelete, onEdit }) {
  const [expanded, setExpanded] = useState(false)

  const getAlertIcon = () => {
    switch (alert.alert_type) {
      case 'spot_price': return '📈'
      case 'option_premium': return '📊'
      case 'pcr': return '⚖️'
      default: return '🔔'
    }
  }

  const getConditionLabel = () => {
    const cond = CONDITIONS.find(c => c.value === alert.condition)
    return cond ? `${cond.icon} ${cond.label}` : alert.condition
  }

  const formatValue = (value) => {
    if (alert.alert_type === 'pcr') return value?.toFixed(2)
    return value?.toLocaleString('en-IN', { maximumFractionDigits: 2 })
  }

  return (
    <div className={`alert-card ${alert.is_triggered ? 'triggered' : ''} ${!alert.is_active ? 'inactive' : ''}`}>
      <div className="alert-card-main" onClick={() => setExpanded(!expanded)}>
        <div className="alert-icon">{getAlertIcon()}</div>
        <div className="alert-info">
          <div className="alert-name">
            {alert.name || alert.display_name}
          </div>
          <div className="alert-details">
            <span className="alert-symbol">{alert.symbol}</span>
            <span className="alert-condition">{getConditionLabel()}</span>
            <span className="alert-target">{formatValue(alert.target_value)}</span>
          </div>
          {alert.option_type && (
            <div className="alert-option-info">
              {alert.strike_price} {alert.option_type} | {alert.expiry}
            </div>
          )}
        </div>
        <div className="alert-status">
          {alert.is_triggered ? (
            <span className="status-badge triggered">Triggered</span>
          ) : alert.is_active ? (
            <span className="status-badge active">Active</span>
          ) : (
            <span className="status-badge inactive">Paused</span>
          )}
        </div>
      </div>

      {expanded && (
        <div className="alert-card-expanded">
          {alert.last_checked_value && (
            <div className="alert-last-check">
              <span>Last checked: {formatValue(alert.last_checked_value)}</span>
              {alert.current_gap !== null && (
                <span className={`gap ${alert.current_gap > 0 ? 'positive' : 'negative'}`}>
                  Gap: {formatValue(Math.abs(alert.current_gap))} ({alert.gap_percentage?.toFixed(1)}%)
                </span>
              )}
            </div>
          )}
          {alert.triggered_at && (
            <div className="alert-triggered-info">
              Triggered at {new Date(alert.triggered_at).toLocaleString()}
              with value {formatValue(alert.triggered_value)}
            </div>
          )}
          {alert.note && <div className="alert-note">{alert.note}</div>}
          <div className="alert-actions">
            <button
              className="btn-icon"
              onClick={(e) => { e.stopPropagation(); onToggle(alert); }}
              title={alert.is_active ? 'Pause' : 'Resume'}
            >
              {alert.is_active ? '⏸️' : '▶️'}
            </button>
            <button
              className="btn-icon delete"
              onClick={(e) => { e.stopPropagation(); onDelete(alert.id); }}
              title="Delete"
            >
              🗑️
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function Alerts() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const [alerts, setAlerts] = useState([])
  const [stats, setStats] = useState(null)
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showNotifications, setShowNotifications] = useState(false)
  const [filter, setFilter] = useState('all') // all, active, triggered

  const loadData = useCallback(async () => {
    if (!isAuthenticated) return

    try {
      setLoading(true)
      const [alertsData, statsData, notifsData] = await Promise.all([
        getAlerts(),
        getAlertStats(),
        getNotifications({ limit: 20 }),
      ])
      setAlerts(alertsData.alerts || [])
      setStats(statsData)
      setNotifications(notifsData.notifications || [])
      setError('')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [isAuthenticated])

  useEffect(() => {
    if (isAuthenticated) {
      loadData()
      // Poll for updates every 30 seconds
      const interval = setInterval(loadData, 30000)
      return () => clearInterval(interval)
    }
  }, [isAuthenticated, loadData])

  const handleToggleAlert = async (alert) => {
    try {
      await updateAlert(alert.id, { is_active: !alert.is_active })
      loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  const handleDeleteAlert = async (alertId) => {
    if (!window.confirm('Are you sure you want to delete this alert?')) return
    try {
      await deleteAlert(alertId)
      loadData()
    } catch (err) {
      setError(err.message)
    }
  }

  const handleMarkNotificationsRead = async (ids) => {
    try {
      if (ids === 'all') {
        await markAllNotificationsRead()
      } else {
        await markNotificationsRead(ids)
      }
      loadData()
    } catch (err) {
      console.error('Failed to mark as read:', err)
    }
  }

  const filteredAlerts = alerts.filter(a => {
    if (filter === 'active') return a.is_active && !a.is_triggered
    if (filter === 'triggered') return a.is_triggered
    return true
  })

  if (!isAuthenticated) {
    return (
      <div className="alerts-page">
        <div className="alerts-login-prompt">
          <h2>Price Alerts</h2>
          <p>Login to create and manage your price alerts</p>
          <button className="btn-primary" onClick={() => navigate('/login')}>
            Login to Continue
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="alerts-page">
      <div className="alerts-header">
        <div className="alerts-title">
          <h1>Price Alerts</h1>
          <p>Get notified when market conditions match your criteria</p>
        </div>
        <div className="alerts-header-actions">
          <button
            className="notification-btn"
            onClick={() => setShowNotifications(!showNotifications)}
          >
            <span className="bell-icon">🔔</span>
            {stats?.unread_notifications > 0 && (
              <span className="notification-badge">{stats.unread_notifications}</span>
            )}
          </button>
          <button className="btn-primary create-btn" onClick={() => setShowCreateModal(true)}>
            + New Alert
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      {stats && (
        <div className="alerts-stats">
          <div className="stat-card">
            <span className="stat-value">{stats.active_alerts}</span>
            <span className="stat-label">Active Alerts</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">{stats.triggered_today}</span>
            <span className="stat-label">Triggered Today</span>
          </div>
          <div className="stat-card">
            <span className="stat-value">{stats.total_alerts}</span>
            <span className="stat-label">Total Alerts</span>
          </div>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="alerts-filters">
        <button
          className={`filter-btn ${filter === 'all' ? 'active' : ''}`}
          onClick={() => setFilter('all')}
        >
          All ({alerts.length})
        </button>
        <button
          className={`filter-btn ${filter === 'active' ? 'active' : ''}`}
          onClick={() => setFilter('active')}
        >
          Active ({alerts.filter(a => a.is_active && !a.is_triggered).length})
        </button>
        <button
          className={`filter-btn ${filter === 'triggered' ? 'active' : ''}`}
          onClick={() => setFilter('triggered')}
        >
          Triggered ({alerts.filter(a => a.is_triggered).length})
        </button>
      </div>

      {/* Error Message */}
      {error && <div className="alerts-error">{error}</div>}

      {/* Alerts List */}
      <div className="alerts-list">
        {loading ? (
          <div className="alerts-loading">
            <div className="spinner" />
            <p>Loading alerts...</p>
          </div>
        ) : filteredAlerts.length === 0 ? (
          <div className="alerts-empty">
            <span className="empty-icon">🔔</span>
            <h3>No alerts yet</h3>
            <p>Create your first alert to get notified when prices hit your targets</p>
            <button className="btn-primary" onClick={() => setShowCreateModal(true)}>
              Create Alert
            </button>
          </div>
        ) : (
          filteredAlerts.map(alert => (
            <AlertCard
              key={alert.id}
              alert={alert}
              onToggle={handleToggleAlert}
              onDelete={handleDeleteAlert}
            />
          ))
        )}
      </div>

      {/* Create Alert Modal */}
      <CreateAlertModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreated={loadData}
      />

      {/* Notifications Panel */}
      {showNotifications && (
        <>
          <div className="notifications-overlay" onClick={() => setShowNotifications(false)} />
          <NotificationsPanel
            isOpen={showNotifications}
            onClose={() => setShowNotifications(false)}
            notifications={notifications}
            onMarkRead={handleMarkNotificationsRead}
          />
        </>
      )}
    </div>
  )
}

export default Alerts
