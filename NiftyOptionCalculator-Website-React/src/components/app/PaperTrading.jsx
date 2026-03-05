import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { paperTradingAPI } from '../../services/paperTradingAPI'
import { NativeAd } from '../ads'
import { ADS_CONFIG } from '../../config/adsConfig'

const INITIAL_BALANCE = 1000000 // ₹10,00,000

function LoginPrompt({ onLogin, fullScreen = false }) {
  if (fullScreen) {
    return (
      <div className="paper-trading-page">
        <div className="login-required-screen">
          <div className="login-required-content">
            <div className="login-icon">📊</div>
            <h2>Paper Trading</h2>
            <p className="feature-description">
              Practice trading with ₹10,00,000 virtual money. Test your strategies risk-free!
            </p>
            <div className="feature-list">
              <div className="feature-item">✓ Virtual portfolio with ₹10L starting balance</div>
              <div className="feature-item">✓ Trade NIFTY, BANKNIFTY options</div>
              <div className="feature-item">✓ Track P&L and performance</div>
              <div className="feature-item">✓ No real money at risk</div>
            </div>
            <button className="primary-btn large" onClick={onLogin}>
              Login to Start Trading
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="login-prompt-overlay" onClick={() => {}}>
      <div className="login-prompt" onClick={e => e.stopPropagation()}>
        <div className="login-prompt-icon">🔐</div>
        <h3>Login Required</h3>
        <p>Sign in to access your paper trading portfolio and start virtual trading with ₹10,00,000.</p>
        <div className="login-prompt-actions">
          <button className="primary-btn" onClick={onLogin}>Login Now</button>
        </div>
      </div>
    </div>
  )
}

function PaperTrading() {
  const { isAuthenticated, refreshUser } = useAuth()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState('positions')
  const [positions, setPositions] = useState([])
  const [history, setHistory] = useState([])
  const [portfolio, setPortfolio] = useState(null)
  const [performance, setPerformance] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showResetModal, setShowResetModal] = useState(false)
  const [showLoginPrompt, setShowLoginPrompt] = useState(false)

  // Load data on mount - only for authenticated users
  useEffect(() => {
    if (isAuthenticated) {
      loadData()
    } else {
      // Don't load demo data - show login prompt instead
      setLoading(false)
    }
  }, [isAuthenticated])

  const loadData = useCallback(async () => {
    if (!isAuthenticated) return

    setLoading(true)
    setError(null)

    try {
      const [portfolioData, positionsData, tradesData, performanceData] = await Promise.all([
        paperTradingAPI.getPortfolio(),
        paperTradingAPI.getPositions(),
        paperTradingAPI.getTradeHistory(50),
        paperTradingAPI.getPerformance(),
      ])

      setPortfolio(portfolioData)
      setPositions(positionsData)
      setHistory(tradesData.filter(t => t.trade_type === 'exit'))
      setPerformance(performanceData)
    } catch (err) {
      setError(err.message || 'Failed to load data')
    } finally {
      setLoading(false)
    }
  }, [isAuthenticated])

  // No more demo mode simulation - guests see login prompt only

  // Calculate P&L for a position
  const calculatePnL = (position) => {
    const multiplier = position.direction === 'buy' ? 1 : -1
    const priceDiff = (position.current_price || position.entry_price) - position.entry_price
    return priceDiff * multiplier * position.quantity * position.lot_size
  }

  // Totals
  const totalUnrealizedPnL = positions.reduce((sum, pos) => sum + calculatePnL(pos), 0)
  const totalRealizedPnL = history.reduce((sum, trade) => sum + (trade.pnl || 0), 0)
  const cashBalance = portfolio?.cash_balance || INITIAL_BALANCE
  const portfolioValue = cashBalance + positions.reduce((sum, pos) => {
    return sum + ((pos.current_price || pos.entry_price) * pos.quantity * pos.lot_size)
  }, 0)

  // Performance metrics
  const winningTrades = performance?.winning_trades || 0
  const losingTrades = performance?.losing_trades || 0
  const winRate = performance?.win_rate || 0

  const handleSquareOff = async (positionId) => {
    if (!isAuthenticated) {
      setShowLoginPrompt(true)
      return
    }

    const position = positions.find(p => p.id === positionId)
    if (!position) return

    try {
      await paperTradingAPI.squareOff(positionId, position.current_price)
      await loadData()
      await refreshUser() // Refresh user balance

      // Prompt to add to journal
      const addToJournal = confirm('Position closed! Add this trade to your journal?')
      if (addToJournal) {
        const params = new URLSearchParams({
          symbol: position.symbol || 'NIFTY',
          strike: position.strike_price,
          type: position.option_type,
          direction: position.direction,
          entry: position.entry_price,
          exit: position.current_price,
          qty: position.quantity || 1,
          lotSize: position.lot_size || 75,
        })
        navigate(`/app/journal?prefill=${params}`)
      }
    } catch (err) {
      setError(err.message || 'Failed to square off position')
    }
  }

  const handleReset = async () => {
    try {
      await paperTradingAPI.resetPortfolio()
      await loadData()
      await refreshUser()
      setShowResetModal(false)
    } catch (err) {
      setError(err.message || 'Failed to reset portfolio')
    }
  }

  const formatCurrency = (value) => {
    const absValue = Math.abs(value)
    if (absValue >= 10000000) return `₹${(value / 10000000).toFixed(2)}Cr`
    if (absValue >= 100000) return `₹${(value / 100000).toFixed(2)}L`
    return `₹${value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  }

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
  }

  if (loading) {
    return (
      <div className="paper-trading-page">
        <div className="loading-overlay">
          <div className="loading-spinner"></div>
          <span>Loading portfolio...</span>
        </div>
      </div>
    )
  }

  // Show full-screen login prompt for guests
  if (!isAuthenticated) {
    return (
      <LoginPrompt
        fullScreen={true}
        onLogin={() => navigate('/login', { state: { from: { pathname: '/app/paper-trading' } } })}
      />
    )
  }

  return (
    <div className="paper-trading-page">
      {/* Login Prompt Modal (for actions) */}
      {showLoginPrompt && (
        <LoginPrompt
          onLogin={() => navigate('/login', { state: { from: { pathname: '/app/paper-trading' } } })}
        />
      )}

      {/* Error Message */}
      {error && (
        <div className="error-message">
          <span>⚠️ {error}</span>
          <button onClick={loadData}>Retry</button>
        </div>
      )}

      {/* Portfolio Summary */}
      <div className="portfolio-summary">
        <div className="summary-card main">
          <div className="summary-label">Portfolio Value</div>
          <div className="summary-value">{formatCurrency(portfolioValue)}</div>
          <div className={`summary-change ${totalUnrealizedPnL + totalRealizedPnL >= 0 ? 'positive' : 'negative'}`}>
            {totalUnrealizedPnL + totalRealizedPnL >= 0 ? '+' : ''}
            {formatCurrency(totalUnrealizedPnL + totalRealizedPnL)}
            ({((totalUnrealizedPnL + totalRealizedPnL) / INITIAL_BALANCE * 100).toFixed(2)}%)
          </div>
        </div>

        <div className="summary-grid">
          <div className="summary-card">
            <div className="summary-label">Cash Balance</div>
            <div className="summary-value small">{formatCurrency(cashBalance)}</div>
          </div>
          <div className="summary-card">
            <div className="summary-label">Unrealized P&L</div>
            <div className={`summary-value small ${totalUnrealizedPnL >= 0 ? 'positive' : 'negative'}`}>
              {totalUnrealizedPnL >= 0 ? '+' : ''}{formatCurrency(totalUnrealizedPnL)}
            </div>
          </div>
          <div className="summary-card">
            <div className="summary-label">Realized P&L</div>
            <div className={`summary-value small ${totalRealizedPnL >= 0 ? 'positive' : 'negative'}`}>
              {totalRealizedPnL >= 0 ? '+' : ''}{formatCurrency(totalRealizedPnL)}
            </div>
          </div>
          <div className="summary-card">
            <div className="summary-label">Win Rate</div>
            <div className="summary-value small">{winRate}%</div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="pt-tabs">
        <button
          className={`pt-tab ${activeTab === 'positions' ? 'active' : ''}`}
          onClick={() => setActiveTab('positions')}
        >
          Positions ({positions.length})
        </button>
        <button
          className={`pt-tab ${activeTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveTab('history')}
        >
          History ({history.length})
        </button>
        <button
          className={`pt-tab ${activeTab === 'performance' ? 'active' : ''}`}
          onClick={() => setActiveTab('performance')}
        >
          Performance
        </button>
        <button className="reset-btn" onClick={() => setShowResetModal(true)}>
          Reset Portfolio
        </button>
      </div>

      {/* Content */}
      <div className="pt-content">
        {activeTab === 'positions' && (
          <div className="positions-list">
            {positions.length === 0 ? (
              <div className="empty-state">
                <span className="empty-icon">📈</span>
                <h3>No Open Positions</h3>
                <p>Start trading from the Option Chain to see your positions here</p>
              </div>
            ) : (
              positions.map(position => {
                const pnl = calculatePnL(position)
                const invested = position.entry_price * position.quantity * position.lot_size
                const pnlPercent = invested > 0 ? (pnl / invested * 100) : 0

                return (
                  <div key={position.id} className="position-card">
                    <div className="position-header">
                      <div className="position-badge-group">
                        <span className={`index-badge ${position.symbol.toLowerCase()}`}>{position.symbol}</span>
                        <span className={`direction-badge ${position.direction}`}>{position.direction.toUpperCase()}</span>
                      </div>
                      <span className="position-strike">{position.strike_price} {position.option_type}</span>
                    </div>

                    <div className="position-details">
                      <div className="detail-row">
                        <span className="detail-label">Entry</span>
                        <span className="detail-value">₹{position.entry_price.toFixed(2)}</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">Current</span>
                        <span className="detail-value">₹{(position.current_price || position.entry_price).toFixed(2)}</span>
                      </div>
                      <div className="detail-row">
                        <span className="detail-label">Qty</span>
                        <span className="detail-value">{position.quantity} lots ({position.quantity * position.lot_size})</span>
                      </div>
                    </div>

                    <div className="position-pnl">
                      <div className={`pnl-value ${pnl >= 0 ? 'positive' : 'negative'}`}>
                        {pnl >= 0 ? '+' : ''}{formatCurrency(pnl)}
                        <span className="pnl-percent">({pnlPercent >= 0 ? '+' : ''}{pnlPercent.toFixed(2)}%)</span>
                      </div>
                      <button
                        className="square-off-btn"
                        onClick={() => handleSquareOff(position.id)}
                      >
                        Square Off
                      </button>
                    </div>
                  </div>
                )
              })
            )}
          </div>
        )}

        {/* Native Ad above history */}
        {activeTab === 'history' && (
          <NativeAd
            slot={ADS_CONFIG.adUnits.paperTradingNative}
            variant="card"
          />
        )}

        {activeTab === 'history' && (
          <div className="history-list">
            {history.length === 0 ? (
              <div className="empty-state">
                <span className="empty-icon">📋</span>
                <h3>No Trade History</h3>
                <p>Your closed trades will appear here</p>
              </div>
            ) : (
              history.map(trade => (
                <div key={trade.id} className="history-card">
                  <div className="history-header">
                    <div className="position-badge-group">
                      <span className={`index-badge ${trade.symbol.toLowerCase()}`}>{trade.symbol}</span>
                      <span className={`direction-badge ${trade.direction}`}>{trade.direction.toUpperCase()}</span>
                    </div>
                    <span className="trade-date">{formatDate(trade.executed_at)}</span>
                  </div>

                  <div className="history-details">
                    <span className="trade-strike">{trade.strike_price} {trade.option_type}</span>
                    <span className="trade-prices">Exit: ₹{trade.price.toFixed(2)}</span>
                  </div>

                  <div className={`history-pnl ${trade.pnl >= 0 ? 'positive' : 'negative'}`}>
                    {trade.pnl >= 0 ? '+' : ''}{formatCurrency(trade.pnl)}
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {activeTab === 'performance' && (
          <div className="performance-section">
            <div className="metrics-grid">
              <div className="metric-card">
                <div className="metric-icon">📊</div>
                <div className="metric-value">{performance?.total_trades || 0}</div>
                <div className="metric-label">Total Trades</div>
              </div>
              <div className="metric-card">
                <div className="metric-icon">✅</div>
                <div className="metric-value positive">{winningTrades}</div>
                <div className="metric-label">Winning</div>
              </div>
              <div className="metric-card">
                <div className="metric-icon">❌</div>
                <div className="metric-value negative">{losingTrades}</div>
                <div className="metric-label">Losing</div>
              </div>
              <div className="metric-card">
                <div className="metric-icon">🎯</div>
                <div className="metric-value">{winRate}%</div>
                <div className="metric-label">Win Rate</div>
              </div>
            </div>

            <div className="performance-chart">
              <h4>P&L Over Time</h4>
              <div className="chart-placeholder">
                <div className="chart-bars">
                  {history.slice().reverse().map((trade, idx) => (
                    <div
                      key={idx}
                      className={`chart-bar ${trade.pnl >= 0 ? 'positive' : 'negative'}`}
                      style={{ height: `${Math.min(100, Math.abs(trade.pnl) / 100)}%` }}
                    >
                      <span className="bar-tooltip">{formatCurrency(trade.pnl)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="best-worst">
              <div className="bw-card best">
                <div className="bw-header">🏆 Best Trade</div>
                {performance?.best_trade ? (
                  <div className="bw-value positive">+{formatCurrency(performance.best_trade)}</div>
                ) : (
                  <div className="bw-empty">No trades yet</div>
                )}
              </div>
              <div className="bw-card worst">
                <div className="bw-header">📉 Worst Trade</div>
                {performance?.worst_trade ? (
                  <div className="bw-value negative">{formatCurrency(performance.worst_trade)}</div>
                ) : (
                  <div className="bw-empty">No trades yet</div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Reset Modal */}
      {showResetModal && (
        <div className="modal-overlay" onClick={() => setShowResetModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3>Reset Portfolio?</h3>
            <p>This will clear all positions and history, and reset your balance to ₹10,00,000.</p>
            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setShowResetModal(false)}>Cancel</button>
              <button className="btn-danger" onClick={handleReset}>Reset</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default PaperTrading
