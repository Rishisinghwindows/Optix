import React, { useState, useEffect, useMemo } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  AreaChart, Area, BarChart, Bar, Cell, Legend, ReferenceLine
} from 'recharts'
import './Backtest.css'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8000'

// ==================== STRATEGY DEFINITIONS ====================
const STRATEGIES = [
  {
    id: 'short_straddle',
    name: 'Short Straddle',
    category: 'neutral',
    description: 'Sell ATM CE + PE',
    legs: [
      { type: 'CE', strike: 'ATM', action: 'SELL', ratio: 1 },
      { type: 'PE', strike: 'ATM', action: 'SELL', ratio: 1 }
    ],
    maxProfit: 'Limited (Premium)',
    maxLoss: 'Unlimited',
    breakeven: 'ATM +/- Premium',
    idealCondition: 'Low volatility, range-bound'
  },
  {
    id: 'long_straddle',
    name: 'Long Straddle',
    category: 'volatile',
    description: 'Buy ATM CE + PE',
    legs: [
      { type: 'CE', strike: 'ATM', action: 'BUY', ratio: 1 },
      { type: 'PE', strike: 'ATM', action: 'BUY', ratio: 1 }
    ],
    maxProfit: 'Unlimited',
    maxLoss: 'Limited (Premium)',
    breakeven: 'ATM +/- Premium',
    idealCondition: 'High volatility expected'
  },
  {
    id: 'short_strangle',
    name: 'Short Strangle',
    category: 'neutral',
    description: 'Sell OTM CE + PE',
    legs: [
      { type: 'CE', strike: 'OTM1', action: 'SELL', ratio: 1 },
      { type: 'PE', strike: 'OTM1', action: 'SELL', ratio: 1 }
    ],
    maxProfit: 'Limited (Premium)',
    maxLoss: 'Unlimited',
    breakeven: 'Strikes +/- Premium',
    idealCondition: 'Low volatility, wider range'
  },
  {
    id: 'long_strangle',
    name: 'Long Strangle',
    category: 'volatile',
    description: 'Buy OTM CE + PE',
    legs: [
      { type: 'CE', strike: 'OTM1', action: 'BUY', ratio: 1 },
      { type: 'PE', strike: 'OTM1', action: 'BUY', ratio: 1 }
    ],
    maxProfit: 'Unlimited',
    maxLoss: 'Limited (Premium)',
    breakeven: 'Strikes +/- Premium',
    idealCondition: 'Big move expected'
  },
  {
    id: 'iron_condor',
    name: 'Iron Condor',
    category: 'neutral',
    description: 'Sell OTM spreads both sides',
    legs: [
      { type: 'CE', strike: 'OTM1', action: 'SELL', ratio: 1 },
      { type: 'CE', strike: 'OTM2', action: 'BUY', ratio: 1 },
      { type: 'PE', strike: 'OTM1', action: 'SELL', ratio: 1 },
      { type: 'PE', strike: 'OTM2', action: 'BUY', ratio: 1 }
    ],
    maxProfit: 'Limited (Net Premium)',
    maxLoss: 'Limited (Spread Width - Premium)',
    breakeven: 'Short strikes +/- Premium',
    idealCondition: 'Range-bound, time decay'
  },
  {
    id: 'iron_fly',
    name: 'Iron Butterfly',
    category: 'neutral',
    description: 'Sell ATM straddle + buy OTM wings',
    legs: [
      { type: 'CE', strike: 'ATM', action: 'SELL', ratio: 1 },
      { type: 'PE', strike: 'ATM', action: 'SELL', ratio: 1 },
      { type: 'CE', strike: 'OTM1', action: 'BUY', ratio: 1 },
      { type: 'PE', strike: 'OTM1', action: 'BUY', ratio: 1 }
    ],
    maxProfit: 'Limited (Net Premium)',
    maxLoss: 'Limited (Wing Width - Premium)',
    breakeven: 'ATM +/- Premium',
    idealCondition: 'Very low movement expected'
  },
  {
    id: 'bull_call_spread',
    name: 'Bull Call Spread',
    category: 'bullish',
    description: 'Buy lower CE, sell higher CE',
    legs: [
      { type: 'CE', strike: 'ATM', action: 'BUY', ratio: 1 },
      { type: 'CE', strike: 'OTM1', action: 'SELL', ratio: 1 }
    ],
    maxProfit: 'Limited (Spread Width - Premium)',
    maxLoss: 'Limited (Net Premium)',
    breakeven: 'Lower strike + Premium',
    idealCondition: 'Moderately bullish'
  },
  {
    id: 'bear_put_spread',
    name: 'Bear Put Spread',
    category: 'bearish',
    description: 'Buy higher PE, sell lower PE',
    legs: [
      { type: 'PE', strike: 'ATM', action: 'BUY', ratio: 1 },
      { type: 'PE', strike: 'OTM1', action: 'SELL', ratio: 1 }
    ],
    maxProfit: 'Limited (Spread Width - Premium)',
    maxLoss: 'Limited (Net Premium)',
    breakeven: 'Higher strike - Premium',
    idealCondition: 'Moderately bearish'
  },
  {
    id: 'bull_put_spread',
    name: 'Bull Put Spread',
    category: 'bullish',
    description: 'Sell higher PE, buy lower PE',
    legs: [
      { type: 'PE', strike: 'OTM1', action: 'SELL', ratio: 1 },
      { type: 'PE', strike: 'OTM2', action: 'BUY', ratio: 1 }
    ],
    maxProfit: 'Limited (Net Premium)',
    maxLoss: 'Limited (Spread Width - Premium)',
    breakeven: 'Short strike - Premium',
    idealCondition: 'Neutral to bullish'
  },
  {
    id: 'bear_call_spread',
    name: 'Bear Call Spread',
    category: 'bearish',
    description: 'Sell lower CE, buy higher CE',
    legs: [
      { type: 'CE', strike: 'OTM1', action: 'SELL', ratio: 1 },
      { type: 'CE', strike: 'OTM2', action: 'BUY', ratio: 1 }
    ],
    maxProfit: 'Limited (Net Premium)',
    maxLoss: 'Limited (Spread Width - Premium)',
    breakeven: 'Short strike + Premium',
    idealCondition: 'Neutral to bearish'
  }
]

// Strike selection methods
const STRIKE_SELECTION = [
  { value: 'ATM', label: 'ATM' },
  { value: 'OTM1', label: 'OTM 1' },
  { value: 'OTM2', label: 'OTM 2' },
  { value: 'OTM3', label: 'OTM 3' },
  { value: 'OTM4', label: 'OTM 4' },
  { value: 'OTM5', label: 'OTM 5' },
  { value: 'ITM1', label: 'ITM 1' },
  { value: 'ITM2', label: 'ITM 2' },
  { value: 'PREMIUM', label: 'Premium' },
  { value: 'DELTA', label: 'Delta' },
  { value: 'OFFSET', label: 'Offset' }
]

// Supported indices
const INDICES = [
  { symbol: 'NIFTY', name: 'NIFTY 50', lotSize: 25, tickSize: 50 },
  { symbol: 'BANKNIFTY', name: 'Bank NIFTY', lotSize: 15, tickSize: 100 },
  { symbol: 'FINNIFTY', name: 'Fin NIFTY', lotSize: 25, tickSize: 50 },
  { symbol: 'MIDCPNIFTY', name: 'Midcap NIFTY', lotSize: 50, tickSize: 25 },
  { symbol: 'SENSEX', name: 'SENSEX', lotSize: 10, tickSize: 100 }
]

// Expiry types
const EXPIRY_TYPES = [
  { value: 'current_week', label: 'Current Week' },
  { value: 'next_week', label: 'Next Week' },
  { value: 'current_month', label: 'Current Month' },
  { value: 'next_month', label: 'Next Month' }
]

// Days of week
const DAYS_OF_WEEK = [
  { value: 0, label: 'Mon' },
  { value: 1, label: 'Tue' },
  { value: 2, label: 'Wed' },
  { value: 3, label: 'Thu' },
  { value: 4, label: 'Fri' }
]

// Strategy categories for display
const STRATEGY_CATEGORIES = [
  { key: 'neutral', label: 'Neutral' },
  { key: 'bullish', label: 'Bullish' },
  { key: 'bearish', label: 'Bearish' },
  { key: 'volatile', label: 'Volatile' }
]

// ==================== MAIN COMPONENT ====================
export default function Backtest() {
  const { isAuthenticated, token } = useAuth()
  const navigate = useNavigate()

  // UI State
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [runs, setRuns] = useState([])
  const [selectedRun, setSelectedRun] = useState(null)
  const [dataRange, setDataRange] = useState(null)

  // Leg builder state
  const [legs, setLegs] = useState([])

  // Strategy Configuration
  const [config, setConfig] = useState({
    symbol: 'NIFTY',
    strategyId: 'short_straddle',
    lots: 1,
    startDate: '2024-01-01',
    endDate: '2024-06-30',
    entryTime: '09:20',
    entryDays: [0, 1, 2, 3, 4],
    expiryType: 'current_week',
    dteMin: 0,
    dteMax: 7,
    strikeSelection: 'ATM',
    strikeOffset: 0,
    premiumMin: 0,
    premiumMax: 0,
    targetDelta: 0.30,
    exitTime: '15:20',
    profitTargetType: 'percent',
    profitTarget: 50,
    stopLossType: 'percent',
    stopLoss: 30,
    enableTrailingSL: false,
    trailingSLType: 'fixed',
    trailingSLTrigger: 20,
    trailingSLValue: 10,
    enableMoveSLToCost: false,
    moveSLToCostTrigger: 25,
    exitMode: 'combined',
    exitBeforeExpiry: true,
    exitDaysBeforeExpiry: 1,
    enableReentry: false,
    maxReentries: 1,
    reentryWaitMinutes: 30,
    reentryOnSL: true,
    reentryOnTarget: false,
    maxDailyLoss: 0,
    maxDailyTrades: 0,
    maxOpenPositions: 1,
    initialCapital: 1000000,
    enableSlippage: true,
    slippagePercent: 0.5,
    enableBrokerage: true,
    brokeragePerLot: 40,
    enableSTT: true,
    executionMode: 'intraday',
    holdingDays: 3,
    customLegs: []
  })

  // Derived
  const selectedStrategy = useMemo(() =>
    STRATEGIES.find(s => s.id === config.strategyId),
    [config.strategyId]
  )

  const selectedIndex = useMemo(() =>
    INDICES.find(i => i.symbol === config.symbol),
    [config.symbol]
  )

  // Auto-populate legs from strategy template
  useEffect(() => {
    if (selectedStrategy) {
      setLegs(selectedStrategy.legs.map((leg, i) => ({
        id: i,
        action: leg.action,
        type: leg.type,
        expiry: config.expiryType,
        strike: leg.strike,
        lots: config.lots
      })))
    }
  }, [config.strategyId])

  // Fetch data range on mount / symbol change
  useEffect(() => {
    fetchDataRange()
    fetchRuns()
  }, [config.symbol])

  const fetchDataRange = async () => {
    try {
      const response = await fetch(`${API_BASE}/api/v1/backtest/historical/data-range?symbol=${config.symbol}`)
      if (response.ok) {
        const data = await response.json()
        setDataRange(data)
        if (data.start_date && data.end_date) {
          setConfig(prev => ({
            ...prev,
            startDate: data.start_date,
            endDate: data.end_date
          }))
        }
      }
    } catch (err) {
      console.error('Failed to fetch data range:', err)
    }
  }

  const fetchRuns = async () => {
    try {
      const headers = {}
      if (isAuthenticated && token) {
        headers['Authorization'] = `Bearer ${token}`
      }
      const response = await fetch(`${API_BASE}/api/v1/backtest/runs`, { headers })
      if (response.ok) {
        const data = await response.json()
        setRuns(data.runs || [])
      }
    } catch (err) {
      console.error('Failed to fetch runs:', err)
    }
  }

  const updateConfig = (field, value) => {
    setConfig(prev => ({ ...prev, [field]: value }))
  }

  const toggleDay = (day) => {
    setConfig(prev => ({
      ...prev,
      entryDays: prev.entryDays.includes(day)
        ? prev.entryDays.filter(d => d !== day)
        : [...prev.entryDays, day].sort()
    }))
  }

  // Leg builder operations
  const addLeg = () => {
    const newId = legs.length > 0 ? Math.max(...legs.map(l => l.id)) + 1 : 0
    setLegs(prev => [...prev, {
      id: newId,
      action: 'SELL',
      type: 'CE',
      expiry: config.expiryType,
      strike: 'ATM',
      lots: config.lots
    }])
  }

  const removeLeg = (id) => {
    setLegs(prev => prev.filter(l => l.id !== id))
  }

  const updateLeg = (id, field, value) => {
    setLegs(prev => prev.map(l => l.id === id ? { ...l, [field]: value } : l))
  }

  // Start backtest
  const startBacktest = async () => {
    setLoading(true)
    setError(null)

    try {
      const headers = { 'Content-Type': 'application/json' }
      if (isAuthenticated && token) {
        headers['Authorization'] = `Bearer ${token}`
      }

      // Build legs payload from leg builder
      const legsPayload = legs.map(leg => ({
        type: leg.type,
        strike: leg.strike,
        action: leg.action,
        ratio: leg.lots
      }))

      const payload = {
        symbol: config.symbol,
        start_date: config.startDate,
        end_date: config.endDate,
        initial_capital: config.initialCapital,
        lot_size: selectedIndex?.lotSize || 25,
        max_positions: config.maxOpenPositions,
        strategy_config: {
          strategy_type: config.strategyId,
          strategy_name: selectedStrategy?.name,
          legs: legsPayload.length > 0 ? legsPayload : selectedStrategy?.legs,
          entry_rules: {
            entry_time: config.entryTime,
            entry_days: config.entryDays,
            expiry_type: config.expiryType,
            dte_min: config.dteMin,
            dte_max: config.dteMax,
            strike_selection: config.strikeSelection,
            strike_offset: config.strikeOffset,
            premium_min: config.premiumMin,
            premium_max: config.premiumMax,
            target_delta: config.targetDelta
          },
          exit_rules: {
            exit_time: config.exitTime,
            profit_target_type: config.profitTargetType,
            profit_target: config.profitTarget,
            stop_loss_type: config.stopLossType,
            stop_loss: config.stopLoss,
            exit_mode: config.exitMode,
            exit_before_expiry: config.exitBeforeExpiry,
            exit_days_before_expiry: config.exitDaysBeforeExpiry,
            holding_period_type: config.executionMode === 'intraday' ? null : config.executionMode,
            holding_period_days: config.executionMode === 'positional' ? config.holdingDays : null,
          },
          trailing_sl: config.enableTrailingSL ? {
            type: config.trailingSLType,
            trigger: config.trailingSLTrigger,
            value: config.trailingSLValue
          } : null,
          move_sl_to_cost: config.enableMoveSLToCost ? {
            trigger: config.moveSLToCostTrigger
          } : null,
          reentry: config.enableReentry ? {
            max_reentries: config.maxReentries,
            wait_minutes: config.reentryWaitMinutes,
            on_sl: config.reentryOnSL,
            on_target: config.reentryOnTarget
          } : null,
          risk: {
            max_daily_loss: config.maxDailyLoss,
            max_daily_trades: config.maxDailyTrades
          },
          simulation: {
            enable_slippage: config.enableSlippage,
            slippage_percent: config.slippagePercent,
            enable_brokerage: config.enableBrokerage,
            brokerage_per_lot: config.brokeragePerLot,
            enable_stt: config.enableSTT
          }
        }
      }

      const response = await fetch(`${API_BASE}/api/v1/backtest/run`, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload)
      })

      if (!response.ok) {
        const err = await response.json()
        throw new Error(err.detail || 'Failed to start backtest')
      }

      const run = await response.json()
      setRuns(prev => [run, ...prev])
      setSelectedRun(run)
      pollRunStatus(run.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const pollRunStatus = async (runId) => {
    const poll = async () => {
      try {
        const headers = {}
        if (isAuthenticated && token) {
          headers['Authorization'] = `Bearer ${token}`
        }
        const response = await fetch(`${API_BASE}/api/v1/backtest/runs/${runId}`, { headers })
        if (response.ok) {
          const run = await response.json()
          setSelectedRun(run)
          setRuns(prev => prev.map(r => r.id === run.id ? run : r))
          if (run.status === 'pending' || run.status === 'running') {
            setTimeout(poll, 1000)
          }
        }
      } catch (err) {
        console.error('Poll error:', err)
      }
    }
    poll()
  }

  // ==================== FORMAT HELPERS ====================
  const formatCurrency = (value) => {
    if (value === null || value === undefined) return '-'
    const absValue = Math.abs(value)
    if (absValue >= 10000000) {
      return `\u20B9${(value / 10000000).toFixed(2)} Cr`
    } else if (absValue >= 100000) {
      return `\u20B9${(value / 100000).toFixed(2)} L`
    }
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0
    }).format(value)
  }

  const formatPercent = (value, showSign = true) => {
    if (value === null || value === undefined) return '-'
    const sign = showSign && value >= 0 ? '+' : ''
    return `${sign}${value.toFixed(2)}%`
  }

  const formatNumber = (value, decimals = 2) => {
    if (value === null || value === undefined) return '-'
    return value.toFixed(decimals)
  }

  // ==================== RENDER: SECTION 1 - INDEX SELECTION ====================
  const renderIndexSection = () => (
    <div className="bt-section">
      <div className="bt-section-title">
        <span className="section-number">1</span>
        Index Selection
      </div>
      <div className="bt-toggle-group">
        {INDICES.map(index => (
          <button
            key={index.symbol}
            className={`bt-toggle-btn bt-index-btn ${config.symbol === index.symbol ? 'active' : ''}`}
            onClick={() => updateConfig('symbol', index.symbol)}
          >
            <span className="index-name">{index.symbol}</span>
            <span className="index-lot">Lot: {index.lotSize}</span>
          </button>
        ))}
      </div>
      {dataRange && (
        <div className="bt-data-range">
          Data available: {dataRange.start_date} to {dataRange.end_date}
        </div>
      )}
    </div>
  )

  // ==================== RENDER: SECTION 2 - STRATEGY ====================
  const renderStrategySection = () => (
    <div className="bt-section">
      <div className="bt-section-title">
        <span className="section-number">2</span>
        Strategy
      </div>
      {STRATEGY_CATEGORIES.map(cat => {
        const strats = STRATEGIES.filter(s => s.category === cat.key)
        if (strats.length === 0) return null
        return (
          <div key={cat.key} className="bt-strategy-category">
            <div className="bt-category-label">{cat.label}</div>
            <div className="bt-strategy-pills">
              {strats.map(strategy => (
                <button
                  key={strategy.id}
                  className={`bt-strategy-pill ${config.strategyId === strategy.id ? 'active' : ''}`}
                  onClick={() => updateConfig('strategyId', strategy.id)}
                  title={strategy.description}
                >
                  {strategy.name}
                </button>
              ))}
            </div>
          </div>
        )
      })}
      {selectedStrategy && (
        <div className="bt-strategy-summary">
          <span className="strategy-label">{selectedStrategy.name}:</span>
          {selectedStrategy.legs.map((leg, i) => (
            <span key={i} className={`bt-leg-badge ${leg.action.toLowerCase()}`}>
              {leg.action} {leg.strike} {leg.type}
            </span>
          ))}
        </div>
      )}
    </div>
  )

  // ==================== RENDER: SECTION 3 - LEG BUILDER ====================
  const renderLegBuilder = () => (
    <div className="bt-section">
      <div className="bt-section-title">
        <span className="section-number">3</span>
        Leg Builder
      </div>
      <div className="bt-leg-table-wrap">
        <table className="bt-leg-table">
          <thead>
            <tr>
              <th>#</th>
              <th>B/S</th>
              <th>Type</th>
              <th>Expiry</th>
              <th>Strike</th>
              <th>Lots</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {legs.map((leg, index) => (
              <tr key={leg.id}>
                <td className="leg-num">{index + 1}</td>
                <td>
                  <div className="bt-bs-toggle">
                    <button
                      className={leg.action === 'BUY' ? 'active-buy' : ''}
                      onClick={() => updateLeg(leg.id, 'action', 'BUY')}
                    >
                      BUY
                    </button>
                    <button
                      className={leg.action === 'SELL' ? 'active-sell' : ''}
                      onClick={() => updateLeg(leg.id, 'action', 'SELL')}
                    >
                      SELL
                    </button>
                  </div>
                </td>
                <td>
                  <select
                    value={leg.type}
                    onChange={(e) => updateLeg(leg.id, 'type', e.target.value)}
                  >
                    <option value="CE">CE</option>
                    <option value="PE">PE</option>
                  </select>
                </td>
                <td>
                  <select
                    value={leg.expiry}
                    onChange={(e) => updateLeg(leg.id, 'expiry', e.target.value)}
                  >
                    {EXPIRY_TYPES.map(et => (
                      <option key={et.value} value={et.value}>{et.label}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <select
                    value={leg.strike}
                    onChange={(e) => updateLeg(leg.id, 'strike', e.target.value)}
                  >
                    {STRIKE_SELECTION.map(ss => (
                      <option key={ss.value} value={ss.value}>{ss.label}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <input
                    type="number"
                    value={leg.lots}
                    onChange={(e) => updateLeg(leg.id, 'lots', Math.max(1, parseInt(e.target.value) || 1))}
                    min="1"
                  />
                </td>
                <td>
                  <button
                    className="bt-remove-btn"
                    onClick={() => removeLeg(leg.id)}
                    title="Remove leg"
                  >
                    &times;
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button className="bt-add-leg" onClick={addLeg}>
        + Add Leg
      </button>
    </div>
  )

  // ==================== RENDER: SECTION 4 - ENTRY SETTINGS ====================
  const renderEntrySection = () => (
    <div className="bt-section">
      <div className="bt-section-title">
        <span className="section-number">4</span>
        Entry Settings
      </div>

      {/* Execution Mode */}
      <div className="bt-field">
        <label className="bt-label">Execution Mode</label>
        <div className="bt-exit-mode-group">
          <button
            className={`bt-exit-mode-btn ${config.executionMode === 'intraday' ? 'active' : ''}`}
            onClick={() => updateConfig('executionMode', 'intraday')}
          >
            Intraday
          </button>
          <button
            className={`bt-exit-mode-btn ${config.executionMode === 'btst' ? 'active' : ''}`}
            onClick={() => updateConfig('executionMode', 'btst')}
          >
            BTST
          </button>
          <button
            className={`bt-exit-mode-btn ${config.executionMode === 'positional' ? 'active' : ''}`}
            onClick={() => updateConfig('executionMode', 'positional')}
          >
            Positional
          </button>
        </div>
      </div>
      {config.executionMode === 'positional' && (
        <div className="bt-field">
          <label className="bt-label">Holding Period (days)</label>
          <input
            type="number"
            className="bt-input"
            value={config.holdingDays}
            onChange={(e) => updateConfig('holdingDays', Math.max(1, parseInt(e.target.value) || 1))}
            min="1"
            style={{ maxWidth: 120 }}
          />
        </div>
      )}

      <div className="bt-form-row">
        <div className="bt-field">
          <label className="bt-label">Start Date</label>
          <input
            type="date"
            className="bt-input"
            value={config.startDate}
            onChange={(e) => updateConfig('startDate', e.target.value)}
            min={dataRange?.start_date}
            max={dataRange?.end_date}
          />
        </div>
        <div className="bt-field">
          <label className="bt-label">End Date</label>
          <input
            type="date"
            className="bt-input"
            value={config.endDate}
            onChange={(e) => updateConfig('endDate', e.target.value)}
            min={dataRange?.start_date}
            max={dataRange?.end_date}
          />
        </div>
      </div>

      <div className="bt-form-row">
        <div className="bt-field">
          <label className="bt-label">Entry Time</label>
          <input
            type="time"
            className="bt-input"
            value={config.entryTime}
            onChange={(e) => updateConfig('entryTime', e.target.value)}
          />
          <span className="bt-hint">Market hours: 09:15 - 15:30</span>
        </div>
        <div className="bt-field">
          <label className="bt-label">Entry Days</label>
          <div className="bt-days-group">
            {DAYS_OF_WEEK.map(day => (
              <button
                key={day.value}
                className={`bt-day-btn ${config.entryDays.includes(day.value) ? 'active' : ''}`}
                onClick={() => toggleDay(day.value)}
              >
                {day.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="bt-form-row">
        <div className="bt-field">
          <label className="bt-label">Expiry</label>
          <select
            className="bt-select"
            value={config.expiryType}
            onChange={(e) => updateConfig('expiryType', e.target.value)}
          >
            {EXPIRY_TYPES.map(expiry => (
              <option key={expiry.value} value={expiry.value}>{expiry.label}</option>
            ))}
          </select>
        </div>
        <div className="bt-field">
          <label className="bt-label">DTE Range</label>
          <div className="bt-form-row" style={{ marginBottom: 0 }}>
            <input
              type="number"
              className="bt-input"
              value={config.dteMin}
              onChange={(e) => updateConfig('dteMin', parseInt(e.target.value) || 0)}
              min="0"
              placeholder="Min"
            />
            <input
              type="number"
              className="bt-input"
              value={config.dteMax}
              onChange={(e) => updateConfig('dteMax', parseInt(e.target.value) || 7)}
              min="0"
              placeholder="Max"
            />
          </div>
        </div>
      </div>

      <div className="bt-field">
        <label className="bt-label">Number of Lots</label>
        <div className="bt-lots-row">
          <button className="bt-lots-btn" onClick={() => updateConfig('lots', Math.max(1, config.lots - 1))}>-</button>
          <input
            type="number"
            className="bt-input"
            value={config.lots}
            onChange={(e) => updateConfig('lots', Math.max(1, parseInt(e.target.value) || 1))}
            min="1"
          />
          <button className="bt-lots-btn" onClick={() => updateConfig('lots', config.lots + 1)}>+</button>
          <span className="bt-lots-info">= {config.lots * (selectedIndex?.lotSize || 25)} qty</span>
        </div>
      </div>
    </div>
  )

  // ==================== RENDER: SECTION 5 - EXIT / TARGET SETTINGS ====================
  const renderExitSection = () => (
    <div className="bt-section">
      <div className="bt-section-title">
        <span className="section-number">5</span>
        Exit / Target Settings
      </div>

      {/* Exit Mode toggle */}
      <div className="bt-field">
        <label className="bt-label">Exit Mode</label>
        <div className="bt-exit-mode-group">
          <button
            className={`bt-exit-mode-btn ${config.exitMode === 'combined' ? 'active' : ''}`}
            onClick={() => updateConfig('exitMode', 'combined')}
          >
            Combined
          </button>
          <button
            className={`bt-exit-mode-btn ${config.exitMode === 'legwise' ? 'active' : ''}`}
            onClick={() => updateConfig('exitMode', 'legwise')}
          >
            Legwise
          </button>
        </div>
      </div>

      {/* Target & SL */}
      <div className="bt-form-row">
        <div className="bt-field">
          <label className="bt-label">Target Profit</label>
          <div className="bt-form-row" style={{ marginBottom: 0 }}>
            <select
              className="bt-select"
              value={config.profitTargetType}
              onChange={(e) => updateConfig('profitTargetType', e.target.value)}
            >
              <option value="percent">%</option>
              <option value="absolute">&#8377;</option>
              <option value="points">Pts</option>
            </select>
            <input
              type="number"
              className="bt-input"
              value={config.profitTarget}
              onChange={(e) => updateConfig('profitTarget', parseFloat(e.target.value) || 0)}
            />
          </div>
        </div>
        <div className="bt-field">
          <label className="bt-label">Stop Loss</label>
          <div className="bt-form-row" style={{ marginBottom: 0 }}>
            <select
              className="bt-select"
              value={config.stopLossType}
              onChange={(e) => updateConfig('stopLossType', e.target.value)}
            >
              <option value="percent">%</option>
              <option value="absolute">&#8377;</option>
              <option value="points">Pts</option>
            </select>
            <input
              type="number"
              className="bt-input"
              value={config.stopLoss}
              onChange={(e) => updateConfig('stopLoss', parseFloat(e.target.value) || 0)}
            />
          </div>
        </div>
      </div>

      {/* Exit Time */}
      <div className="bt-field">
        <label className="bt-label">Exit Time (if no target/SL hit)</label>
        <input
          type="time"
          className="bt-input"
          value={config.exitTime}
          onChange={(e) => updateConfig('exitTime', e.target.value)}
          style={{ maxWidth: 200 }}
        />
      </div>

      {/* Exit Before Expiry */}
      <label className="bt-checkbox-label">
        <input
          type="checkbox"
          checked={config.exitBeforeExpiry}
          onChange={(e) => updateConfig('exitBeforeExpiry', e.target.checked)}
        />
        Exit Before Expiry
      </label>
      {config.exitBeforeExpiry && (
        <div className="bt-nested">
          <div className="bt-field">
            <label className="bt-label">Days Before Expiry</label>
            <input
              type="number"
              className="bt-input"
              value={config.exitDaysBeforeExpiry}
              onChange={(e) => updateConfig('exitDaysBeforeExpiry', parseInt(e.target.value) || 1)}
              min="0"
              style={{ maxWidth: 120 }}
            />
          </div>
        </div>
      )}

      <div className="bt-divider" />

      {/* Trailing SL */}
      <label className="bt-checkbox-label">
        <input
          type="checkbox"
          checked={config.enableTrailingSL}
          onChange={(e) => updateConfig('enableTrailingSL', e.target.checked)}
        />
        Trailing Stop Loss
      </label>
      {config.enableTrailingSL && (
        <div className="bt-nested">
          <div className="bt-form-row-3">
            <div className="bt-field">
              <label className="bt-label">Trail Type</label>
              <select
                className="bt-select"
                value={config.trailingSLType}
                onChange={(e) => updateConfig('trailingSLType', e.target.value)}
              >
                <option value="fixed">Fixed</option>
                <option value="recalculated">Recalculated</option>
              </select>
            </div>
            <div className="bt-field">
              <label className="bt-label">Activation (% profit)</label>
              <input
                type="number"
                className="bt-input"
                value={config.trailingSLTrigger}
                onChange={(e) => updateConfig('trailingSLTrigger', parseFloat(e.target.value) || 0)}
              />
            </div>
            <div className="bt-field">
              <label className="bt-label">Trail Value (%)</label>
              <input
                type="number"
                className="bt-input"
                value={config.trailingSLValue}
                onChange={(e) => updateConfig('trailingSLValue', parseFloat(e.target.value) || 0)}
              />
            </div>
          </div>
        </div>
      )}

      {/* Move SL to Cost */}
      <label className="bt-checkbox-label">
        <input
          type="checkbox"
          checked={config.enableMoveSLToCost}
          onChange={(e) => updateConfig('enableMoveSLToCost', e.target.checked)}
        />
        Move SL to Cost (Break-even)
      </label>
      {config.enableMoveSLToCost && (
        <div className="bt-nested">
          <div className="bt-field">
            <label className="bt-label">Trigger at (% profit)</label>
            <input
              type="number"
              className="bt-input"
              value={config.moveSLToCostTrigger}
              onChange={(e) => updateConfig('moveSLToCostTrigger', parseFloat(e.target.value) || 0)}
              style={{ maxWidth: 160 }}
            />
            <span className="bt-hint">Move SL to entry price when profit reaches this %</span>
          </div>
        </div>
      )}

      <div className="bt-divider" />

      {/* Re-entry */}
      <label className="bt-checkbox-label">
        <input
          type="checkbox"
          checked={config.enableReentry}
          onChange={(e) => updateConfig('enableReentry', e.target.checked)}
        />
        Re-entry after Exit
      </label>
      {config.enableReentry && (
        <div className="bt-nested">
          <div className="bt-form-row">
            <div className="bt-field">
              <label className="bt-label">Max Re-entries/Day</label>
              <input
                type="number"
                className="bt-input"
                value={config.maxReentries}
                onChange={(e) => updateConfig('maxReentries', parseInt(e.target.value) || 1)}
                min="1"
              />
            </div>
            <div className="bt-field">
              <label className="bt-label">Wait Time (min)</label>
              <input
                type="number"
                className="bt-input"
                value={config.reentryWaitMinutes}
                onChange={(e) => updateConfig('reentryWaitMinutes', parseInt(e.target.value) || 30)}
                min="0"
              />
            </div>
          </div>
          <div className="bt-field">
            <label className="bt-label">Re-entry On</label>
            <div style={{ display: 'flex', gap: 16 }}>
              <label className="bt-checkbox-label">
                <input
                  type="checkbox"
                  checked={config.reentryOnSL}
                  onChange={(e) => updateConfig('reentryOnSL', e.target.checked)}
                />
                Stop Loss
              </label>
              <label className="bt-checkbox-label">
                <input
                  type="checkbox"
                  checked={config.reentryOnTarget}
                  onChange={(e) => updateConfig('reentryOnTarget', e.target.checked)}
                />
                Target Hit
              </label>
            </div>
          </div>
        </div>
      )}
    </div>
  )

  // ==================== RENDER: SECTION 6 - RISK & ADVANCED ====================
  const renderRiskSection = () => (
    <div className="bt-section">
      <div className="bt-section-title">
        <span className="section-number">6</span>
        Risk & Advanced
      </div>

      <div className="bt-risk-grid">
        <div className="bt-field">
          <label className="bt-label">Capital</label>
          <input
            type="number"
            className="bt-input"
            value={config.initialCapital}
            onChange={(e) => updateConfig('initialCapital', parseFloat(e.target.value) || 1000000)}
            step="100000"
          />
        </div>
        <div className="bt-field">
          <label className="bt-label">Max Daily Loss</label>
          <input
            type="number"
            className="bt-input"
            value={config.maxDailyLoss}
            onChange={(e) => updateConfig('maxDailyLoss', parseFloat(e.target.value) || 0)}
          />
          <span className="bt-hint">0 = No limit</span>
        </div>
        <div className="bt-field">
          <label className="bt-label">Max Trades/Day</label>
          <input
            type="number"
            className="bt-input"
            value={config.maxDailyTrades}
            onChange={(e) => updateConfig('maxDailyTrades', parseInt(e.target.value) || 0)}
          />
          <span className="bt-hint">0 = No limit</span>
        </div>
        <div className="bt-field">
          <label className="bt-label">Max Positions</label>
          <input
            type="number"
            className="bt-input"
            value={config.maxOpenPositions}
            onChange={(e) => updateConfig('maxOpenPositions', parseInt(e.target.value) || 1)}
            min="1"
          />
        </div>
      </div>

      <div className="bt-divider" />

      <div className="bt-charges-row">
        <label className="bt-charge-item">
          <input
            type="checkbox"
            checked={config.enableSlippage}
            onChange={(e) => updateConfig('enableSlippage', e.target.checked)}
          />
          Slippage
          {config.enableSlippage && (
            <input
              type="number"
              value={config.slippagePercent}
              onChange={(e) => updateConfig('slippagePercent', parseFloat(e.target.value) || 0)}
              step="0.1"
              min="0"
            />
          )}
          {config.enableSlippage && <span className="bt-hint">%</span>}
        </label>

        <label className="bt-charge-item">
          <input
            type="checkbox"
            checked={config.enableBrokerage}
            onChange={(e) => updateConfig('enableBrokerage', e.target.checked)}
          />
          Brokerage
          {config.enableBrokerage && (
            <input
              type="number"
              value={config.brokeragePerLot}
              onChange={(e) => updateConfig('brokeragePerLot', parseFloat(e.target.value) || 0)}
            />
          )}
          {config.enableBrokerage && <span className="bt-hint">&#8377;/lot</span>}
        </label>

        <label className="bt-charge-item">
          <input
            type="checkbox"
            checked={config.enableSTT}
            onChange={(e) => updateConfig('enableSTT', e.target.checked)}
          />
          STT & Charges
        </label>
      </div>
    </div>
  )

  // ==================== RENDER: SECTION 7 - RESULTS ====================
  const renderResultsSection = () => {
    // Nothing to show if no run
    if (!selectedRun && runs.length === 0) return null

    // Show empty state with previous runs
    if (!selectedRun) {
      return (
        <div className="bt-section">
          <div className="bt-section-title">
            <span className="section-number">7</span>
            Results
          </div>
          <div className="bt-results-empty">
            <div className="bt-empty-state">
              <span className="bt-empty-icon">&#128202;</span>
              <h3>No Results Yet</h3>
              <p>Configure your strategy and run a backtest to see results</p>
            </div>
          </div>
          {renderPreviousRuns()}
        </div>
      )
    }

    // Running state
    if (selectedRun.status === 'pending' || selectedRun.status === 'running') {
      return (
        <div className="bt-section">
          <div className="bt-section-title">
            <span className="section-number">7</span>
            Results
          </div>
          <div className="bt-running-state">
            <div className="bt-spinner"></div>
            <h3>Backtest Running...</h3>
            <div className="bt-progress-bar">
              <div className="bt-progress-fill" style={{ width: `${selectedRun.progress || 0}%` }}></div>
            </div>
            <p>{selectedRun.progress || 0}% complete</p>
          </div>
        </div>
      )
    }

    // Failed state
    if (selectedRun.status === 'failed') {
      return (
        <div className="bt-section">
          <div className="bt-section-title">
            <span className="section-number">7</span>
            Results
          </div>
          <div className="bt-error-state">
            <h3>Backtest Failed</h3>
            <p>{selectedRun.error_message || 'An error occurred during the backtest'}</p>
          </div>
          {renderPreviousRuns()}
        </div>
      )
    }

    const result = selectedRun.result
    if (!result) {
      return (
        <div className="bt-section">
          <div className="bt-section-title">
            <span className="section-number">7</span>
            Results
          </div>
          <div className="bt-results-empty">
            <p>Results not available</p>
          </div>
        </div>
      )
    }

    return (
      <div className="bt-section">
        <div className="bt-section-title">
          <span className="section-number">7</span>
          Results
        </div>

        {/* Summary Cards */}
        <div className="bt-results-summary">
          <div className={`bt-summary-card main ${result.total_pnl >= 0 ? 'profit' : 'loss'}`}>
            <span className="bt-card-label">Total P&L</span>
            <span className="bt-card-value">{formatCurrency(result.total_pnl)}</span>
            <span className="bt-card-sub">{formatPercent(result.total_return_pct)}</span>
          </div>
          <div className="bt-summary-card">
            <span className="bt-card-label">Win Rate</span>
            <span className="bt-card-value">{formatPercent(result.win_rate, false)}</span>
            <span className="bt-card-sub">{result.winning_trades}W / {result.losing_trades}L</span>
          </div>
          <div className="bt-summary-card">
            <span className="bt-card-label">Total Trades</span>
            <span className="bt-card-value">{result.total_trades}</span>
            <span className="bt-card-sub">Avg {formatNumber(result.avg_holding_days, 1)} days</span>
          </div>
          <div className="bt-summary-card">
            <span className="bt-card-label">Max Drawdown</span>
            <span className="bt-card-value" style={{ color: 'var(--loss)' }}>{formatPercent(result.max_drawdown_pct, false)}</span>
            <span className="bt-card-sub">{formatCurrency(result.max_drawdown)}</span>
          </div>
        </div>

        {/* Performance Metrics */}
        <div className="bt-metrics-grid">
          <div className="bt-metric-card">
            <span className="bt-metric-label">Profit Factor</span>
            <span className="bt-metric-value">{formatNumber(result.profit_factor)}</span>
          </div>
          <div className="bt-metric-card">
            <span className="bt-metric-label">Sharpe Ratio</span>
            <span className="bt-metric-value">{formatNumber(result.sharpe_ratio)}</span>
          </div>
          <div className="bt-metric-card">
            <span className="bt-metric-label">Sortino Ratio</span>
            <span className="bt-metric-value">{formatNumber(result.sortino_ratio)}</span>
          </div>
          <div className="bt-metric-card">
            <span className="bt-metric-label">CAGR</span>
            <span className="bt-metric-value">{formatPercent(result.cagr)}</span>
          </div>
          <div className="bt-metric-card">
            <span className="bt-metric-label">Avg Profit</span>
            <span className="bt-metric-value profit">{formatCurrency(result.avg_profit)}</span>
          </div>
          <div className="bt-metric-card">
            <span className="bt-metric-label">Avg Loss</span>
            <span className="bt-metric-value loss">{formatCurrency(result.avg_loss)}</span>
          </div>
        </div>

        {/* Equity Curve */}
        {result.equity_curve && result.equity_curve.length > 0 && (
          <div className="bt-chart-container">
            <h4>Equity Curve</h4>
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={result.equity_curve}>
                <defs>
                  <linearGradient id="equityGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--profit, #10b981)" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="var(--profit, #10b981)" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border-light, #333)" />
                <XAxis
                  dataKey="date"
                  stroke="var(--text-muted, #888)"
                  tick={{ fill: 'var(--text-muted, #888)', fontSize: 11 }}
                  tickFormatter={(date) => new Date(date).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })}
                />
                <YAxis
                  stroke="var(--text-muted, #888)"
                  tick={{ fill: 'var(--text-muted, #888)', fontSize: 11 }}
                  tickFormatter={(value) => `\u20B9${(value/100000).toFixed(1)}L`}
                />
                <Tooltip
                  contentStyle={{ backgroundColor: 'var(--surface-solid, #1a1a2e)', border: '1px solid var(--border-light, #333)', borderRadius: '8px' }}
                  labelStyle={{ color: 'var(--text-muted, #888)' }}
                  formatter={(value) => [formatCurrency(value), 'Equity']}
                  labelFormatter={(date) => new Date(date).toLocaleDateString('en-IN')}
                />
                <ReferenceLine
                  y={config.initialCapital}
                  stroke="var(--text-disabled, #666)"
                  strokeDasharray="5 5"
                  label={{ value: 'Initial', fill: 'var(--text-disabled, #666)', fontSize: 10 }}
                />
                <Area
                  type="monotone"
                  dataKey="equity"
                  stroke="var(--profit, #10b981)"
                  fill="url(#equityGradient)"
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* Drawdown Chart */}
        {result.equity_curve && result.equity_curve.length > 0 && (
          <div className="bt-chart-container">
            <h4>Drawdown</h4>
            <ResponsiveContainer width="100%" height={200}>
              <AreaChart data={result.equity_curve}>
                <defs>
                  <linearGradient id="ddGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--loss, #ef4444)" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="var(--loss, #ef4444)" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border-light, #333)" />
                <XAxis
                  dataKey="date"
                  stroke="var(--text-muted, #888)"
                  tick={{ fill: 'var(--text-muted, #888)', fontSize: 11 }}
                  tickFormatter={(date) => new Date(date).toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })}
                />
                <YAxis
                  stroke="var(--text-muted, #888)"
                  tick={{ fill: 'var(--text-muted, #888)', fontSize: 11 }}
                  tickFormatter={(value) => `${value.toFixed(1)}%`}
                />
                <Tooltip
                  contentStyle={{ backgroundColor: 'var(--surface-solid, #1a1a2e)', border: '1px solid var(--border-light, #333)', borderRadius: '8px' }}
                  formatter={(value) => [`${value?.toFixed(2)}%`, 'Drawdown']}
                />
                <Area
                  type="monotone"
                  dataKey="drawdown_pct"
                  stroke="var(--loss, #ef4444)"
                  fill="url(#ddGradient)"
                  strokeWidth={2}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* Monthly Returns */}
        {result.monthly_returns && result.monthly_returns.length > 0 && (
          <div className="bt-chart-container">
            <h4>Monthly Returns</h4>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={result.monthly_returns}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border-light, #333)" />
                <XAxis
                  dataKey="month"
                  stroke="var(--text-muted, #888)"
                  tick={{ fill: 'var(--text-muted, #888)', fontSize: 11 }}
                />
                <YAxis
                  stroke="var(--text-muted, #888)"
                  tick={{ fill: 'var(--text-muted, #888)', fontSize: 11 }}
                  tickFormatter={(value) => `${value}%`}
                />
                <Tooltip
                  contentStyle={{ backgroundColor: 'var(--surface-solid, #1a1a2e)', border: '1px solid var(--border-light, #333)', borderRadius: '8px' }}
                  formatter={(value) => [`${value?.toFixed(2)}%`, 'Return']}
                />
                <ReferenceLine y={0} stroke="var(--text-disabled, #666)" />
                <Bar dataKey="return_pct">
                  {result.monthly_returns.map((entry, index) => (
                    <Cell
                      key={`cell-${index}`}
                      fill={entry.return_pct >= 0 ? '#10b981' : '#ef4444'}
                    />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}

        {renderPreviousRuns()}
      </div>
    )
  }

  // Previous runs sub-component
  const renderPreviousRuns = () => {
    if (runs.length === 0) return null
    return (
      <div className="bt-runs-list">
        <h4>Previous Runs</h4>
        {runs.map(run => (
          <div
            key={run.id}
            className={`bt-run-item ${selectedRun?.id === run.id ? 'selected' : ''}`}
            onClick={() => {
              setSelectedRun(run)
              if (run.status === 'pending' || run.status === 'running') {
                pollRunStatus(run.id)
              }
            }}
          >
            <div className="bt-run-info">
              <span className="bt-run-symbol">{run.symbol}</span>
              <span className="bt-run-date">{run.start_date} to {run.end_date}</span>
            </div>
            <div className="bt-run-status">
              <span className={`bt-status-badge ${run.status}`}>{run.status}</span>
              {run.result && (
                <span className={`bt-run-pnl ${run.result.total_pnl >= 0 ? 'profit' : 'loss'}`}>
                  {formatCurrency(run.result.total_pnl)}
                </span>
              )}
            </div>
          </div>
        ))}
      </div>
    )
  }

  // ==================== RENDER: STICKY BOTTOM BAR ====================
  const renderStickyBar = () => (
    <div className="bt-sticky-bar">
      <div className="bt-bar-summary">
        <div className="bt-bar-item">
          <span className="bar-label">Index:</span>
          <span className="bar-value">{config.symbol}</span>
        </div>
        <div className="bt-bar-item">
          <span className="bar-label">Strategy:</span>
          <span className="bar-value">{selectedStrategy?.name}</span>
        </div>
        <div className="bt-bar-item">
          <span className="bar-label">Period:</span>
          <span className="bar-value">{config.startDate} to {config.endDate}</span>
        </div>
        <div className="bt-bar-item">
          <span className="bar-label">Legs:</span>
          <span className="bar-value">{legs.length}</span>
        </div>
        <div className="bt-bar-item">
          <span className="bar-label">Mode:</span>
          <span className="bar-value">
            {config.executionMode === 'intraday' ? 'Intraday' : config.executionMode === 'btst' ? 'BTST' : `Positional (${config.holdingDays}d)`}
          </span>
        </div>
      </div>
      <div className="bt-bar-actions">
        <button
          className="bt-btn-run"
          onClick={startBacktest}
          disabled={loading}
        >
          {loading ? 'Running...' : 'Run Backtest'}
        </button>
      </div>
    </div>
  )

  // ==================== MAIN RENDER ====================
  return (
    <div className="backtest-container">
      {/* Header */}
      <div className="backtest-header">
        <h1>Strategy Backtester</h1>
      </div>

      {/* Error Message */}
      {error && (
        <div className="backtest-error">
          {error}
        </div>
      )}

      {/* All sections stacked vertically */}
      {renderIndexSection()}
      {renderStrategySection()}
      {renderLegBuilder()}
      {renderEntrySection()}
      {renderExitSection()}
      {renderRiskSection()}
      {renderResultsSection()}

      {/* Sticky Bottom Bar */}
      {renderStickyBar()}
    </div>
  )
}
