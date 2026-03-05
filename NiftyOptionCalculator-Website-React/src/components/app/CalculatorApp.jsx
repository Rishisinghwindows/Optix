import React, { useState, useEffect, useMemo } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { calculateBlackScholes, getMoneyness, formatCurrency } from '../../utils/blackScholes'
import { AdBanner } from '../ads'
import { ADS_CONFIG } from '../../config/adsConfig'

const INDICES = {
  NIFTY: { name: 'NIFTY', lotSize: 75, defaultSpot: 24250 },
  BANKNIFTY: { name: 'BANKNIFTY', lotSize: 30, defaultSpot: 51500 },
  FINNIFTY: { name: 'FINNIFTY', lotSize: 65, defaultSpot: 23100 },
  SENSEX: { name: 'SENSEX', lotSize: 20, defaultSpot: 79500 },
  MIDCPNIFTY: { name: 'MIDCPNIFTY', lotSize: 120, defaultSpot: 12800 }
}

function CalculatorApp() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  // Get initial values from URL params (from option chain click)
  const initialSpot = searchParams.get('spot') ? Number(searchParams.get('spot')) : 24250
  const initialStrike = searchParams.get('strike') ? Number(searchParams.get('strike')) : 24300
  const initialType = searchParams.get('type') || 'call'
  const initialIndex = searchParams.get('index') || 'NIFTY'
  const initialDays = searchParams.get('days') ? Number(searchParams.get('days')) : 7

  const [spotPrice, setSpotPrice] = useState(initialSpot)
  const [strikePrice, setStrikePrice] = useState(initialStrike)
  const [volatility, setVolatility] = useState(15)
  const [daysToExpiry, setDaysToExpiry] = useState(initialDays)
  const [interestRate, setInterestRate] = useState(7)
  const [optionType, setOptionType] = useState(initialType)
  const [selectedIndex, setSelectedIndex] = useState(initialIndex)

  // Simple mode state
  const [mode, setMode] = useState('simple') // 'simple' or 'advanced'
  const [targetPrice, setTargetPrice] = useState(initialSpot + 200)
  const [stopLossPrice, setStopLossPrice] = useState(initialSpot - 100)

  const lotSize = INDICES[selectedIndex].lotSize

  // Calculate option values at current spot
  const result = useMemo(() => {
    return calculateBlackScholes({
      spotPrice,
      strikePrice,
      volatility: volatility / 100,
      daysToExpiry,
      interestRate: interestRate / 100,
      optionType
    })
  }, [spotPrice, strikePrice, volatility, daysToExpiry, interestRate, optionType])

  // Calculate option value at target price
  const targetResult = useMemo(() => {
    return calculateBlackScholes({
      spotPrice: targetPrice,
      strikePrice,
      volatility: volatility / 100,
      daysToExpiry: Math.max(0, daysToExpiry - 1), // Assume 1 day passes
      interestRate: interestRate / 100,
      optionType
    })
  }, [targetPrice, strikePrice, volatility, daysToExpiry, interestRate, optionType])

  // Calculate option value at stop-loss price
  const stopLossResult = useMemo(() => {
    return calculateBlackScholes({
      spotPrice: stopLossPrice,
      strikePrice,
      volatility: volatility / 100,
      daysToExpiry: Math.max(0, daysToExpiry - 1),
      interestRate: interestRate / 100,
      optionType
    })
  }, [stopLossPrice, strikePrice, volatility, daysToExpiry, interestRate, optionType])

  // Calculate profit/loss scenarios
  const profitAtTarget = (targetResult.price - result.price) * lotSize
  const profitAtTargetPct = ((targetResult.price - result.price) / result.price) * 100
  const lossAtStopLoss = (stopLossResult.price - result.price) * lotSize
  const lossAtStopLossPct = ((stopLossResult.price - result.price) / result.price) * 100

  const maxProfit = profitAtTarget
  const maxLoss = Math.abs(lossAtStopLoss)
  const riskReward = maxLoss > 0 ? (maxProfit / maxLoss).toFixed(1) : '∞'

  const moneyness = getMoneyness(spotPrice, strikePrice, optionType)
  const lotValue = result.price * lotSize

  // Update target/stop-loss when spot changes
  useEffect(() => {
    setTargetPrice(spotPrice + 200)
    setStopLossPrice(spotPrice - 100)
  }, [spotPrice])

  // Profit/Loss at different spot prices
  const payoffData = useMemo(() => {
    const data = []
    const range = spotPrice * 0.1 // 10% range
    for (let price = spotPrice - range; price <= spotPrice + range; price += range / 10) {
      const newResult = calculateBlackScholes({
        spotPrice: price,
        strikePrice,
        volatility: volatility / 100,
        daysToExpiry: 0, // At expiry
        interestRate: interestRate / 100,
        optionType
      })
      data.push({
        spot: price,
        pnl: (newResult.price - result.price) * lotSize
      })
    }
    return data
  }, [spotPrice, strikePrice, volatility, interestRate, optionType, result.price, lotSize])

  const handleIndexChange = (index) => {
    setSelectedIndex(index)
    setSpotPrice(INDICES[index].defaultSpot)
    const step = index === 'SENSEX' ? 100 : 50
    setStrikePrice(Math.round(INDICES[index].defaultSpot / step) * step)
  }

  const handleBuy = () => {
    navigate(`/app/trade?index=${selectedIndex}&strike=${strikePrice}&type=${optionType}&action=buy`)
  }

  return (
    <div className="calculator-app-page">
      {/* Header with Mode Toggle */}
      <div className="calc-app-header">
        <div className="selected-option">
          <span className={`option-badge ${optionType}`}>{optionType.toUpperCase()}</span>
          <span className="strike-display">{strikePrice}</span>
          <span className={`moneyness ${moneyness.toLowerCase()}`}>{moneyness}</span>
        </div>
        <div className="mode-toggle">
          <button
            className={`mode-btn ${mode === 'simple' ? 'active' : ''}`}
            onClick={() => setMode('simple')}
          >
            Simple
          </button>
          <button
            className={`mode-btn ${mode === 'advanced' ? 'active' : ''}`}
            onClick={() => setMode('advanced')}
          >
            Advanced
          </button>
        </div>
      </div>

      {/* Simple Mode */}
      {mode === 'simple' && (
        <div className="simple-mode">
          {/* Index Selection */}
          <div className="simple-index-row">
            {Object.keys(INDICES).map(index => (
              <button
                key={index}
                className={`simple-index-btn ${selectedIndex === index ? 'active' : ''}`}
                onClick={() => handleIndexChange(index)}
              >
                {index}
              </button>
            ))}
          </div>

          {/* Current Price Display */}
          <div className="current-price-display">
            <span className="current-label">Current {selectedIndex}</span>
            <span className="current-value">{spotPrice.toLocaleString()}</span>
          </div>

          {/* Target Price Section */}
          <div className="scenario-section target">
            <div className="scenario-card">
              <div className="scenario-icon up">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 4l-8 8h5v8h6v-8h5z"/>
                </svg>
              </div>
              <div className="scenario-content">
                <div className="scenario-label">If {selectedIndex} goes to</div>
                <div className="scenario-sublabel">Your target price</div>
              </div>
              <div className="scenario-input-wrap">
                <input
                  type="number"
                  value={targetPrice}
                  onChange={e => setTargetPrice(Number(e.target.value))}
                  className="scenario-input"
                />
              </div>
            </div>
            <div className="quick-buttons">
              <span className="quick-label">Quick:</span>
              {[100, 200, 300, 500].map(val => (
                <button
                  key={val}
                  className="quick-btn up"
                  onClick={() => setTargetPrice(spotPrice + val)}
                >
                  +{val}
                </button>
              ))}
            </div>
          </div>

          {/* Stop-Loss Section */}
          <div className="scenario-section stoploss">
            <div className="scenario-card">
              <div className="scenario-icon down">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 20l8-8h-5V4H9v8H4z"/>
                </svg>
              </div>
              <div className="scenario-content">
                <div className="scenario-label">If {selectedIndex} falls to</div>
                <div className="scenario-sublabel">Your stop-loss price</div>
              </div>
              <div className="scenario-input-wrap">
                <input
                  type="number"
                  value={stopLossPrice}
                  onChange={e => setStopLossPrice(Number(e.target.value))}
                  className="scenario-input stoploss"
                />
              </div>
            </div>
            <div className="quick-buttons">
              <span className="quick-label">Quick:</span>
              {[100, 150, 200, 300].map(val => (
                <button
                  key={val}
                  className="quick-btn down"
                  onClick={() => setStopLossPrice(spotPrice - val)}
                >
                  -{val}
                </button>
              ))}
            </div>
          </div>

          {/* Strike & Type Selection */}
          <div className="simple-strike-section">
            <div className="strike-type-row">
              <div className="strike-input-group">
                <label>Strike Price</label>
                <div className="strike-adjuster">
                  <button onClick={() => setStrikePrice(p => p - 50)}>-</button>
                  <span>{strikePrice}</span>
                  <button onClick={() => setStrikePrice(p => p + 50)}>+</button>
                </div>
              </div>
              <div className="type-toggle-group">
                <button
                  className={`type-toggle-btn ${optionType === 'call' ? 'active call' : ''}`}
                  onClick={() => setOptionType('call')}
                >
                  CALL
                </button>
                <button
                  className={`type-toggle-btn ${optionType === 'put' ? 'active put' : ''}`}
                  onClick={() => setOptionType('put')}
                >
                  PUT
                </button>
              </div>
            </div>
          </div>

          {/* Divider */}
          <div className="results-divider">
            <span>YOUR OPTION WILL BE</span>
          </div>

          {/* Results at Target */}
          <div className="result-card profit">
            <div className="result-icon profit">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M3 3v18h18V3H3zm14 12.59L15.59 17 12 13.41 8.41 17 7 15.59l5-5 5 5z"/>
              </svg>
            </div>
            <div className="result-info">
              <div className="result-title">At Target ({targetPrice.toLocaleString()})</div>
              <div className="result-subtitle">Option Premium</div>
            </div>
            <div className="result-values">
              <div className="result-premium profit">{formatCurrency(targetResult.price)}</div>
              <div className="result-change profit">
                ↗ {profitAtTarget >= 0 ? '+' : ''}{formatCurrency(profitAtTarget)} ({profitAtTargetPct >= 0 ? '+' : ''}{profitAtTargetPct.toFixed(0)}%)
              </div>
            </div>
          </div>

          {/* Results at Stop-Loss */}
          <div className="result-card loss">
            <div className="result-icon loss">
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
              </svg>
            </div>
            <div className="result-info">
              <div className="result-title">At Stop-Loss ({stopLossPrice.toLocaleString()})</div>
              <div className="result-subtitle">Option Premium</div>
            </div>
            <div className="result-values">
              <div className="result-premium loss">{formatCurrency(stopLossResult.price)}</div>
              <div className="result-change loss">
                ↘ {formatCurrency(lossAtStopLoss)} ({lossAtStopLossPct.toFixed(0)}%)
              </div>
            </div>
          </div>

          {/* Summary Stats */}
          <div className="summary-stats">
            <div className="stat-item">
              <div className="stat-label">Max Profit</div>
              <div className="stat-value profit">+{formatCurrency(maxProfit)}</div>
            </div>
            <div className="stat-item">
              <div className="stat-label">Max Loss</div>
              <div className="stat-value loss">-{formatCurrency(maxLoss)}</div>
            </div>
            <div className="stat-item">
              <div className="stat-label">Risk:Reward</div>
              <div className="stat-value highlight">1:{riskReward}</div>
            </div>
          </div>

          {/* Buy Button */}
          <button className="buy-button" onClick={handleBuy}>
            <svg viewBox="0 0 24 24" fill="currentColor" className="cart-icon">
              <path d="M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z"/>
            </svg>
            <span>BUY</span>
            <span className="buy-price">{formatCurrency(result.price)}</span>
          </button>

          {/* Days to Expiry */}
          <div className="expiry-info">
            <span>Days to Expiry: {daysToExpiry}</span>
            <input
              type="range"
              value={daysToExpiry}
              onChange={e => setDaysToExpiry(Number(e.target.value))}
              min="0"
              max="30"
              className="expiry-slider"
            />
          </div>
        </div>
      )}

      {/* Advanced Mode */}
      {mode === 'advanced' && (
        <>
          {/* Main Result */}
          <div className="calc-main-result">
            <div className="result-label">Theoretical Price</div>
            <div className="result-price-large">{formatCurrency(result.price)}</div>
            <div className="lot-value-display">
              Lot Value: {formatCurrency(lotValue)} ({lotSize} × {formatCurrency(result.price)})
            </div>
            <button
              onClick={() => {
                const params = new URLSearchParams({
                  spotPrice, strikePrice, optionType,
                  premium: result.price.toFixed(2),
                  iv: (volatility / 100).toFixed(4),
                  daysToExpiry, lotSize, quantity: 1,
                })
                navigate(`/app/simulator?${params}`)
              }}
              style={{
                marginTop: 8, padding: '6px 16px', borderRadius: 6, border: 'none',
                background: 'linear-gradient(135deg, #7c3aed, #448aff)', color: '#fff',
                cursor: 'pointer', fontSize: 12, fontWeight: 600,
              }}
            >
              What-If Simulator
            </button>
          </div>

          {/* Greeks Display */}
          <div className="greeks-display">
            <div className="greek-item delta">
              <span className="greek-symbol">Δ</span>
              <div className="greek-info">
                <span className="greek-value">{result.delta.toFixed(4)}</span>
                <span className="greek-name">Delta</span>
              </div>
            </div>
            <div className="greek-item gamma">
              <span className="greek-symbol">Γ</span>
              <div className="greek-info">
                <span className="greek-value">{result.gamma.toFixed(6)}</span>
                <span className="greek-name">Gamma</span>
              </div>
            </div>
            <div className="greek-item theta">
              <span className="greek-symbol">Θ</span>
              <div className="greek-info">
                <span className="greek-value negative">{result.theta.toFixed(2)}</span>
                <span className="greek-name">Theta</span>
              </div>
            </div>
            <div className="greek-item vega">
              <span className="greek-symbol">V</span>
              <div className="greek-info">
                <span className="greek-value">{result.vega.toFixed(2)}</span>
                <span className="greek-name">Vega</span>
              </div>
            </div>
            <div className="greek-item rho">
              <span className="greek-symbol">ρ</span>
              <div className="greek-info">
                <span className="greek-value">{result.rho.toFixed(2)}</span>
                <span className="greek-name">Rho</span>
              </div>
            </div>
          </div>

          {/* Intrinsic vs Time Value */}
          <div className="value-breakdown">
            <h4>Value Breakdown</h4>
            <div className="breakdown-bar">
              <div
                className="intrinsic-part"
                style={{ width: `${(result.intrinsicValue / result.price) * 100 || 0}%` }}
              >
                {result.intrinsicValue > 0 && formatCurrency(result.intrinsicValue)}
              </div>
              <div
                className="time-part"
                style={{ width: `${(result.timeValue / result.price) * 100 || 100}%` }}
              >
                {formatCurrency(result.timeValue)}
              </div>
            </div>
            <div className="breakdown-labels">
              <span>Intrinsic: {formatCurrency(result.intrinsicValue)}</span>
              <span>Time Value: {formatCurrency(result.timeValue)}</span>
            </div>
          </div>

          {/* Input Controls */}
          <div className="calc-inputs-section">
            <h4>Parameters</h4>

            <div className="input-row">
              <label>Index</label>
              <div className="index-buttons">
                {Object.keys(INDICES).map(index => (
                  <button
                    key={index}
                    className={`index-btn-small ${selectedIndex === index ? 'active' : ''}`}
                    onClick={() => handleIndexChange(index)}
                  >
                    {index}
                  </button>
                ))}
              </div>
            </div>

            <div className="input-row">
              <label>Option Type</label>
              <div className="type-buttons">
                <button
                  className={`type-btn ${optionType === 'call' ? 'active call' : ''}`}
                  onClick={() => setOptionType('call')}
                >
                  CALL
                </button>
                <button
                  className={`type-btn ${optionType === 'put' ? 'active put' : ''}`}
                  onClick={() => setOptionType('put')}
                >
                  PUT
                </button>
              </div>
            </div>

            <div className="input-row">
              <label>Spot Price</label>
              <div className="input-with-buttons">
                <button onClick={() => setSpotPrice(p => p - 50)}>-</button>
                <input
                  type="number"
                  value={spotPrice}
                  onChange={e => setSpotPrice(Number(e.target.value))}
                />
                <button onClick={() => setSpotPrice(p => p + 50)}>+</button>
              </div>
            </div>

            <div className="input-row">
              <label>Strike Price</label>
              <div className="input-with-buttons">
                <button onClick={() => setStrikePrice(p => p - 50)}>-</button>
                <input
                  type="number"
                  value={strikePrice}
                  onChange={e => setStrikePrice(Number(e.target.value))}
                />
                <button onClick={() => setStrikePrice(p => p + 50)}>+</button>
              </div>
            </div>

            <div className="input-row">
              <label>IV: {volatility}%</label>
              <input
                type="range"
                value={volatility}
                onChange={e => setVolatility(Number(e.target.value))}
                min="5"
                max="80"
                className="slider"
              />
            </div>

            <div className="input-row">
              <label>Days to Expiry: {daysToExpiry}</label>
              <input
                type="range"
                value={daysToExpiry}
                onChange={e => setDaysToExpiry(Number(e.target.value))}
                min="0"
                max="90"
                className="slider"
              />
            </div>

            <div className="input-row">
              <label>Interest Rate: {interestRate}%</label>
              <input
                type="range"
                value={interestRate}
                onChange={e => setInterestRate(Number(e.target.value))}
                min="0"
                max="15"
                step="0.25"
                className="slider"
              />
            </div>
          </div>

          {/* Payoff Chart Placeholder */}
          <div className="payoff-section">
            <h4>Payoff at Expiry (per lot)</h4>
            <div className="payoff-chart">
              <div className="chart-y-axis">
                <span>Profit</span>
                <span>0</span>
                <span>Loss</span>
              </div>
              <div className="chart-area">
                {payoffData.map((point, idx) => (
                  <div
                    key={idx}
                    className={`payoff-bar ${point.pnl >= 0 ? 'profit' : 'loss'}`}
                    style={{
                      height: `${Math.min(100, Math.abs(point.pnl) / 200)}%`,
                      bottom: point.pnl >= 0 ? '50%' : 'auto',
                      top: point.pnl < 0 ? '50%' : 'auto'
                    }}
                    title={`Spot: ₹${point.spot.toFixed(0)}, P&L: ${formatCurrency(point.pnl)}`}
                  />
                ))}
              </div>
              <div className="chart-x-axis">
                <span>-10%</span>
                <span>Spot</span>
                <span>+10%</span>
              </div>
            </div>
          </div>

          {/* Greek Explanations */}
          <div className="greek-explanations">
            <h4>What These Greeks Mean</h4>
            <div className="explanation-cards">
              <div className="exp-card">
                <span className="exp-greek">Δ Delta</span>
                <p>Option price changes by ₹{Math.abs(result.delta).toFixed(2)} for every ₹1 move in spot</p>
              </div>
              <div className="exp-card">
                <span className="exp-greek">Θ Theta</span>
                <p>Option loses ₹{Math.abs(result.theta).toFixed(2)} per day due to time decay</p>
              </div>
              <div className="exp-card">
                <span className="exp-greek">V Vega</span>
                <p>Option price changes by ₹{result.vega.toFixed(2)} for every 1% change in IV</p>
              </div>
            </div>
          </div>

          {/* Calculator Results Ad */}
          <AdBanner
            slot={ADS_CONFIG.adUnits.calculatorResults}
            placement="calculatorResults"
            className="ad-results"
          />
        </>
      )}
    </div>
  )
}

export default CalculatorApp
