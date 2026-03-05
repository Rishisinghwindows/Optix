import { useState, useEffect, useMemo, useCallback } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { calculateBlackScholes } from '../../utils/blackScholes'
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'

const INTEREST_RATE = 0.065 // RBI repo rate

/**
 * Computes the theoretical price and P&L for one option leg.
 */
function computeLeg(leg, spotChange, daysElapsed, ivChange) {
  const newSpot = leg.spotPrice * (1 + spotChange / 100)
  const rawIV = leg.iv * (1 + ivChange / 100)
  const newIV = Math.max(0.01, Math.min(5.0, rawIV))
  const remainingDays = Math.max(leg.daysToExpiry - daysElapsed, 0)

  const result = calculateBlackScholes({
    spotPrice: newSpot,
    strikePrice: leg.strikePrice,
    volatility: newIV,
    daysToExpiry: remainingDays,
    interestRate: INTEREST_RATE,
    optionType: leg.optionType,
  })

  const pnlPerUnit = result.price - leg.premium
  const totalPnl = pnlPerUnit * leg.quantity * leg.lotSize
  const pnlPercent = leg.premium > 0 ? (pnlPerUnit / leg.premium) * 100 : 0

  return {
    newPrice: result.price,
    pnl: totalPnl,
    pnlPercent,
    delta: result.delta,
    gamma: result.gamma,
    theta: result.theta,
    vega: result.vega,
    newSpot,
    newIV,
    remainingDays,
  }
}

/**
 * Generates payoff chart data across a range of spot prices.
 */
function generatePayoffData(legs, spotPrice, daysElapsed, ivChange) {
  const points = []
  const steps = 41

  for (let i = 0; i < steps; i++) {
    const spotChangePercent = -10 + (i * 20) / (steps - 1)
    let totalPnl = 0

    for (const leg of legs) {
      const result = computeLeg(leg, spotChangePercent, daysElapsed, ivChange)
      totalPnl += result.pnl
    }

    const newSpotValue = spotPrice * (1 + spotChangePercent / 100)
    points.push({
      spot: Math.round(newSpotValue),
      spotLabel: newSpotValue.toFixed(0),
      pnl: Math.round(totalPnl),
      spotChange: spotChangePercent.toFixed(1),
    })
  }

  return points
}

function PnLSimulator() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  // Parse initial values from URL params
  const parseLegs = useCallback(() => {
    const legsParam = searchParams.get('legs')
    if (legsParam) {
      try {
        const parsed = JSON.parse(decodeURIComponent(legsParam))
        return parsed.map((leg) => ({
          spotPrice: parseFloat(leg.spotPrice) || 25000,
          strikePrice: parseFloat(leg.strikePrice) || 25000,
          optionType: leg.optionType || 'call',
          premium: parseFloat(leg.premium) || 100,
          iv: parseFloat(leg.iv) || 0.15,
          daysToExpiry: parseInt(leg.daysToExpiry) || 7,
          lotSize: parseInt(leg.lotSize) || 75,
          quantity: parseInt(leg.quantity) || 1,
        }))
      } catch {
        // Fall through to single-leg parsing
      }
    }

    // Single-leg from individual URL params
    return [
      {
        spotPrice: parseFloat(searchParams.get('spotPrice')) || 25000,
        strikePrice: parseFloat(searchParams.get('strikePrice')) || 25000,
        optionType: searchParams.get('optionType') || 'call',
        premium: parseFloat(searchParams.get('premium')) || 100,
        iv: parseFloat(searchParams.get('iv')) || 0.15,
        daysToExpiry: parseInt(searchParams.get('daysToExpiry')) || 7,
        lotSize: parseInt(searchParams.get('lotSize')) || 75,
        quantity: parseInt(searchParams.get('quantity')) || 1,
      },
    ]
  }, [searchParams])

  const [legs] = useState(parseLegs)

  // Derive shared values from first leg
  const spotPrice = legs[0].spotPrice
  const maxDTE = Math.max(...legs.map((l) => l.daysToExpiry))
  const isMultiLeg = legs.length > 1

  // Slider state
  const [spotChange, setSpotChange] = useState(0)
  const [daysElapsed, setDaysElapsed] = useState(0)
  const [ivChange, setIvChange] = useState(0)

  // Compute results for all legs
  const legResults = useMemo(() => {
    return legs.map((leg) => computeLeg(leg, spotChange, daysElapsed, ivChange))
  }, [legs, spotChange, daysElapsed, ivChange])

  // Aggregate P&L across legs
  const aggregate = useMemo(() => {
    let totalPnl = 0
    let totalInvestment = 0
    let totalDelta = 0
    let totalGamma = 0
    let totalTheta = 0
    let totalVega = 0

    legs.forEach((leg, i) => {
      const res = legResults[i]
      totalPnl += res.pnl
      totalInvestment += leg.premium * leg.quantity * leg.lotSize
      totalDelta += res.delta * leg.quantity * leg.lotSize
      totalGamma += res.gamma * leg.quantity * leg.lotSize
      totalTheta += res.theta * leg.quantity * leg.lotSize
      totalVega += res.vega * leg.quantity * leg.lotSize
    })

    const pnlPercent = totalInvestment > 0 ? (totalPnl / totalInvestment) * 100 : 0

    return {
      totalPnl,
      pnlPercent,
      totalDelta,
      totalGamma,
      totalTheta,
      totalVega,
    }
  }, [legs, legResults])

  // Payoff chart data
  const payoffData = useMemo(() => {
    return generatePayoffData(legs, spotPrice, daysElapsed, ivChange)
  }, [legs, spotPrice, daysElapsed, ivChange])

  // Reset sliders
  const handleReset = () => {
    setSpotChange(0)
    setDaysElapsed(0)
    setIvChange(0)
  }

  // Format currency
  const formatCurrency = (val) => {
    const sign = val >= 0 ? '+' : ''
    return `${sign}${val.toLocaleString('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).replace('INR', '').trim()}`
  }

  // Custom tooltip for chart
  const CustomTooltip = ({ active, payload }) => {
    if (active && payload && payload.length) {
      const data = payload[0].payload
      return (
        <div className="simulator-tooltip">
          <div className="tooltip-spot">Spot: {data.spot.toLocaleString('en-IN')}</div>
          <div className={`tooltip-pnl ${data.pnl >= 0 ? 'profit' : 'loss'}`}>
            P&L: {formatCurrency(data.pnl)}
          </div>
          <div className="tooltip-change">Move: {data.spotChange}%</div>
        </div>
      )
    }
    return null
  }

  return (
    <div className="simulator-container">
      {/* Header */}
      <div className="simulator-header">
        <h2 className="simulator-title">P&L Simulator</h2>
        <button className="simulator-reset-btn" onClick={handleReset}>
          Reset
        </button>
      </div>

      {/* Position Summary */}
      <div className="simulator-position-summary">
        {legs.map((leg, i) => (
          <div key={i} className={`position-card ${leg.optionType}`}>
            <div className="position-card-header">
              <span className={`position-type-badge ${leg.optionType}`}>
                {leg.optionType === 'call' ? 'CE' : 'PE'}
              </span>
              <span className="position-strike">{leg.strikePrice}</span>
              {leg.quantity > 0 ? (
                <span className="position-side buy">BUY</span>
              ) : (
                <span className="position-side sell">SELL</span>
              )}
            </div>
            <div className="position-card-details">
              <div className="position-detail">
                <span className="detail-label">Entry</span>
                <span className="detail-value">{leg.premium.toFixed(2)}</span>
              </div>
              <div className="position-detail">
                <span className="detail-label">IV</span>
                <span className="detail-value">{(leg.iv * 100).toFixed(1)}%</span>
              </div>
              <div className="position-detail">
                <span className="detail-label">DTE</span>
                <span className="detail-value">{leg.daysToExpiry}d</span>
              </div>
              <div className="position-detail">
                <span className="detail-label">Lots</span>
                <span className="detail-value">{Math.abs(leg.quantity)} x {leg.lotSize}</span>
              </div>
              {!isMultiLeg && legResults[i] && (
                <div className="position-detail">
                  <span className="detail-label">New Price</span>
                  <span className="detail-value">{legResults[i].newPrice.toFixed(2)}</span>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Sliders */}
      <div className="simulator-sliders">
        {/* Spot Price Change */}
        <div className="slider-group">
          <div className="slider-header">
            <label className="slider-label">Spot Price Change</label>
            <span className={`slider-value ${spotChange >= 0 ? 'positive' : 'negative'}`}>
              {spotChange >= 0 ? '+' : ''}{spotChange.toFixed(1)}%
              <span className="slider-subtext">
                ({(spotPrice * (1 + spotChange / 100)).toFixed(0)})
              </span>
            </span>
          </div>
          <input
            type="range"
            className="simulator-slider"
            min={-10}
            max={10}
            step={0.1}
            value={spotChange}
            onChange={(e) => setSpotChange(parseFloat(e.target.value))}
          />
          <div className="slider-range-labels">
            <span>-10%</span>
            <span>0%</span>
            <span>+10%</span>
          </div>
        </div>

        {/* Days Elapsed */}
        <div className="slider-group">
          <div className="slider-header">
            <label className="slider-label">Days Elapsed</label>
            <span className="slider-value">
              {daysElapsed} day{daysElapsed !== 1 ? 's' : ''}
              <span className="slider-subtext">
                ({Math.max(maxDTE - daysElapsed, 0)} DTE remaining)
              </span>
            </span>
          </div>
          <input
            type="range"
            className="simulator-slider"
            min={0}
            max={maxDTE}
            step={1}
            value={daysElapsed}
            onChange={(e) => setDaysElapsed(parseInt(e.target.value))}
          />
          <div className="slider-range-labels">
            <span>0d</span>
            <span>{Math.round(maxDTE / 2)}d</span>
            <span>{maxDTE}d</span>
          </div>
        </div>

        {/* IV Change */}
        <div className="slider-group">
          <div className="slider-header">
            <label className="slider-label">IV Change</label>
            <span className={`slider-value ${ivChange >= 0 ? 'positive' : 'negative'}`}>
              {ivChange >= 0 ? '+' : ''}{ivChange}%
            </span>
          </div>
          <input
            type="range"
            className="simulator-slider"
            min={-30}
            max={30}
            step={1}
            value={ivChange}
            onChange={(e) => setIvChange(parseInt(e.target.value))}
          />
          <div className="slider-range-labels">
            <span>-30%</span>
            <span>0%</span>
            <span>+30%</span>
          </div>
        </div>
      </div>

      {/* P&L Display */}
      <div className="simulator-pnl-display">
        <div className={`pnl-main ${aggregate.totalPnl >= 0 ? 'profit' : 'loss'}`}>
          <span className="pnl-label">Total P&L</span>
          <span className="pnl-amount">{formatCurrency(aggregate.totalPnl)}</span>
          <span className="pnl-percent">
            ({aggregate.pnlPercent >= 0 ? '+' : ''}{aggregate.pnlPercent.toFixed(2)}%)
          </span>
        </div>

        <div className="greeks-display">
          <div className="greek-item">
            <span className="greek-label">Delta</span>
            <span className="greek-value">{aggregate.totalDelta.toFixed(2)}</span>
          </div>
          <div className="greek-item">
            <span className="greek-label">Gamma</span>
            <span className="greek-value">{aggregate.totalGamma.toFixed(4)}</span>
          </div>
          <div className="greek-item">
            <span className="greek-label">Theta</span>
            <span className="greek-value">{aggregate.totalTheta.toFixed(2)}</span>
          </div>
          <div className="greek-item">
            <span className="greek-label">Vega</span>
            <span className="greek-value">{aggregate.totalVega.toFixed(2)}</span>
          </div>
        </div>
      </div>

      {/* Payoff Chart */}
      <div className="simulator-chart">
        <h3 className="chart-title">Payoff at Current Settings</h3>
        <ResponsiveContainer width="100%" height={320}>
          <AreaChart data={payoffData} margin={{ top: 10, right: 30, left: 10, bottom: 10 }}>
            <defs>
              <linearGradient id="profitGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#4ade80" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#4ade80" stopOpacity={0} />
              </linearGradient>
              <linearGradient id="lossGradient" x1="0" y1="1" x2="0" y2="0">
                <stop offset="5%" stopColor="#f87171" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#f87171" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
            <XAxis
              dataKey="spot"
              tick={{ fill: 'var(--text-secondary)', fontSize: 11 }}
              tickFormatter={(val) => val.toLocaleString('en-IN')}
              interval={Math.floor(payoffData.length / 6)}
            />
            <YAxis
              tick={{ fill: 'var(--text-secondary)', fontSize: 11 }}
              tickFormatter={(val) => {
                if (Math.abs(val) >= 100000) return (val / 100000).toFixed(1) + 'L'
                if (Math.abs(val) >= 1000) return (val / 1000).toFixed(1) + 'K'
                return val
              }}
            />
            <Tooltip content={<CustomTooltip />} />
            <ReferenceLine y={0} stroke="rgba(255,255,255,0.3)" strokeDasharray="3 3" />
            <ReferenceLine
              x={Math.round(spotPrice)}
              stroke="var(--accent-primary)"
              strokeDasharray="3 3"
              label={{ value: 'Spot', fill: 'var(--accent-primary)', fontSize: 11 }}
            />
            <Area
              type="monotone"
              dataKey="pnl"
              stroke="#4ade80"
              fill="url(#profitGradient)"
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, fill: '#4ade80' }}
              baseValue={0}
              connectNulls
            />
            {/* Overlay for loss portion: we use a second area for visual clarity */}
            <Area
              type="monotone"
              dataKey={(dataPoint) => (dataPoint.pnl < 0 ? dataPoint.pnl : 0)}
              stroke="#f87171"
              fill="url(#lossGradient)"
              strokeWidth={2}
              dot={false}
              activeDot={false}
              baseValue={0}
              connectNulls
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      {/* Individual Leg Results (for multi-leg) */}
      {isMultiLeg && (
        <div className="simulator-legs-detail">
          <h3 className="legs-detail-title">Individual Leg Breakdown</h3>
          <table className="legs-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Strike</th>
                <th>Entry</th>
                <th>Current</th>
                <th>P&L</th>
                <th>Delta</th>
                <th>Theta</th>
              </tr>
            </thead>
            <tbody>
              {legs.map((leg, i) => {
                const res = legResults[i]
                return (
                  <tr key={i}>
                    <td className={`type-col ${leg.optionType}`}>
                      {leg.optionType === 'call' ? 'CE' : 'PE'}
                    </td>
                    <td>{leg.strikePrice}</td>
                    <td>{leg.premium.toFixed(2)}</td>
                    <td>{res.newPrice.toFixed(2)}</td>
                    <td className={res.pnl >= 0 ? 'profit-text' : 'loss-text'}>
                      {formatCurrency(res.pnl)}
                    </td>
                    <td>{res.delta.toFixed(3)}</td>
                    <td>{res.theta.toFixed(2)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Quick Scenario Buttons */}
      <div className="simulator-scenarios">
        <h3 className="scenarios-title">Quick Scenarios</h3>
        <div className="scenario-buttons">
          <button
            className="scenario-btn bullish"
            onClick={() => { setSpotChange(3); setDaysElapsed(0); setIvChange(-5); }}
          >
            Bullish Rally (+3%, IV crush)
          </button>
          <button
            className="scenario-btn bearish"
            onClick={() => { setSpotChange(-3); setDaysElapsed(0); setIvChange(10); }}
          >
            Bearish Drop (-3%, IV spike)
          </button>
          <button
            className="scenario-btn decay"
            onClick={() => { setSpotChange(0); setDaysElapsed(Math.min(3, maxDTE)); setIvChange(0); }}
          >
            Time Decay (3 days)
          </button>
          <button
            className="scenario-btn expiry"
            onClick={() => { setSpotChange(0); setDaysElapsed(maxDTE); setIvChange(0); }}
          >
            At Expiry
          </button>
          <button
            className="scenario-btn vol-spike"
            onClick={() => { setSpotChange(0); setDaysElapsed(0); setIvChange(20); }}
          >
            IV Spike (+20%)
          </button>
          <button
            className="scenario-btn vol-crush"
            onClick={() => { setSpotChange(0); setDaysElapsed(0); setIvChange(-20); }}
          >
            IV Crush (-20%)
          </button>
        </div>
      </div>
    </div>
  )
}

export default PnLSimulator
