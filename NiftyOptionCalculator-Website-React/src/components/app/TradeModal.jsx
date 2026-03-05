import React, { useState, useMemo, useEffect } from 'react'
import { useAuth } from '../../context/AuthContext'
import { paperTradingAPI } from '../../services/paperTradingAPI'
import { useNavigate } from 'react-router-dom'
import './TradeModal.css'

// Simple option price calculator using intrinsic + time value approximation
function calculateOptionPrice(spotPrice, strikePrice, optionType, currentLTP, targetSpot) {
  const isCall = optionType.toUpperCase() === 'CALL' || optionType.toUpperCase() === 'CE'

  // Current intrinsic value
  const currentIntrinsic = isCall
    ? Math.max(0, spotPrice - strikePrice)
    : Math.max(0, strikePrice - spotPrice)

  // Time value = current premium - intrinsic
  const timeValue = Math.max(0, currentLTP - currentIntrinsic)

  // New intrinsic at target
  const newIntrinsic = isCall
    ? Math.max(0, targetSpot - strikePrice)
    : Math.max(0, strikePrice - targetSpot)

  // Simplified: assume time value decays proportionally based on how far from ATM
  const moneyness = Math.abs(targetSpot - strikePrice) / strikePrice
  const timeValueDecay = Math.max(0.3, 1 - moneyness * 2) // Rough approximation

  return Math.max(0.05, newIntrinsic + (timeValue * timeValueDecay))
}

function TradeModal({ isOpen, onClose, option, index, expiry, spotPrice, lotSize, onTradeSuccess }) {
  const { isAuthenticated, user, refreshUser } = useAuth()
  const navigate = useNavigate()
  const [direction, setDirection] = useState('buy')
  const [quantity, setQuantity] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  // Target and Stop Loss (spot prices)
  const [targetSpot, setTargetSpot] = useState(0)
  const [stopLossSpot, setStopLossSpot] = useState(0)
  // Target and Stop Loss (option prices - user editable)
  const [targetOptionPrice, setTargetOptionPrice] = useState(0)
  const [stopLossOptionPrice, setStopLossOptionPrice] = useState(0)
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [initialized, setInitialized] = useState(false)

  // Set initial values only once when modal opens with valid spotPrice
  useEffect(() => {
    if (isOpen && spotPrice > 0 && !initialized) {
      setTargetSpot(Math.round(spotPrice + 200))
      setStopLossSpot(Math.round(spotPrice - 150))
      setInitialized(true)
    }
    // Reset initialized flag when modal closes
    if (!isOpen) {
      setInitialized(false)
      setTargetOptionPrice(0)
      setStopLossOptionPrice(0)
    }
  }, [isOpen, spotPrice, initialized])

  // Derived values - calculate before any conditional returns
  const price = option?.ltp || 0
  const totalValue = price * quantity * lotSize
  const marginRequired = direction === 'sell' ? totalValue * 0.2 : totalValue
  const balance = user?.paper_trading_balance || 1000000
  const canTrade = isAuthenticated && balance >= marginRequired

  // Calculate projected prices at target and stop loss - must be before conditional return
  const projections = useMemo(() => {
    if (!option) {
      return {
        calculatedTargetPrice: 0,
        calculatedStopLossPrice: 0,
        priceAtTarget: 0,
        priceAtStopLoss: 0,
        profitAtTarget: 0,
        lossAtStopLoss: 0,
        profitPercent: 0,
        lossPercent: 0,
      }
    }

    const optionType = option.type
    const optionPrice = option.ltp || 0

    // Calculate option prices based on spot targets
    const calculatedTargetPrice = calculateOptionPrice(spotPrice, option.strike, optionType, optionPrice, targetSpot)
    const calculatedStopLossPrice = calculateOptionPrice(spotPrice, option.strike, optionType, optionPrice, stopLossSpot)

    // Use user-entered prices if set, otherwise use calculated
    const priceAtTarget = targetOptionPrice > 0 ? targetOptionPrice : calculatedTargetPrice
    const priceAtStopLoss = stopLossOptionPrice > 0 ? stopLossOptionPrice : calculatedStopLossPrice

    const profitAtTarget = (priceAtTarget - optionPrice) * quantity * lotSize * (direction === 'buy' ? 1 : -1)
    const lossAtStopLoss = (priceAtStopLoss - optionPrice) * quantity * lotSize * (direction === 'buy' ? 1 : -1)

    const profitPercent = optionPrice > 0 ? ((priceAtTarget - optionPrice) / optionPrice) * 100 * (direction === 'buy' ? 1 : -1) : 0
    const lossPercent = optionPrice > 0 ? ((priceAtStopLoss - optionPrice) / optionPrice) * 100 * (direction === 'buy' ? 1 : -1) : 0

    return {
      calculatedTargetPrice,
      calculatedStopLossPrice,
      priceAtTarget,
      priceAtStopLoss,
      profitAtTarget,
      lossAtStopLoss,
      profitPercent,
      lossPercent,
    }
  }, [spotPrice, option, targetSpot, stopLossSpot, targetOptionPrice, stopLossOptionPrice, quantity, lotSize, direction])

  // Update option prices when spot targets change (only if user hasn't manually set them)
  useEffect(() => {
    if (targetOptionPrice === 0 && projections.calculatedTargetPrice > 0) {
      // Don't auto-update, let user edit manually or use calculated
    }
  }, [projections.calculatedTargetPrice, targetOptionPrice])

  // Early return AFTER all hooks
  if (!isOpen || !option) return null

  // Quick adjustment buttons
  const targetAdjustments = [100, 200, 300, 500]
  const stopLossAdjustments = [-100, -150, -200, -300]

  const handleTrade = async () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: '/app' } } })
      return
    }

    if (!canTrade) {
      setError('Insufficient balance for this trade')
      return
    }

    setLoading(true)
    setError(null)

    try {
      await paperTradingAPI.createTrade({
        symbol: index,
        strike_price: option.strike,
        option_type: option.type.toUpperCase() === 'CALL' ? 'CE' : 'PE',
        direction: direction,
        expiry_date: expiry,
        quantity: quantity,
        lot_size: lotSize,
        price: price,
        target_price: showAdvanced ? targetSpot : null,
        stop_loss_price: showAdvanced ? stopLossSpot : null,
      })

      await refreshUser()
      onTradeSuccess?.()
      onClose()
    } catch (err) {
      console.error('Trade error:', err)
      if (err.message?.includes('token') || err.message?.includes('401') || err.message?.includes('Unauthorized')) {
        setError('Session expired. Please logout and login again.')
      } else {
        setError(err.message || 'Failed to place trade')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="trade-modal-overlay" onClick={onClose}>
      <div className="trade-modal" onClick={e => e.stopPropagation()}>
        <div className="trade-modal-header">
          <div className="trade-option-info">
            <span className={`trade-index-badge ${index.toLowerCase()}`}>{index}</span>
            <span className="trade-strike">{option.strike}</span>
            <span className={`trade-type-badge ${option.type.toLowerCase()}`}>
              {option.type.toUpperCase()}
            </span>
          </div>
          <button className="trade-modal-close" onClick={onClose}>×</button>
        </div>

        <div className="trade-price-display">
          <div className="current-price">
            <span className="price-label">Current Price</span>
            <span className="price-value">₹{price.toFixed(2)}</span>
          </div>
          <div className={`price-change ${option.change >= 0 ? 'positive' : 'negative'}`}>
            {option.change >= 0 ? '+' : ''}{option.change.toFixed(2)}
          </div>
        </div>

        {/* Direction Toggle */}
        <div className="trade-direction-toggle">
          <button
            className={`direction-btn buy ${direction === 'buy' ? 'active' : ''}`}
            onClick={() => setDirection('buy')}
          >
            <span className="direction-icon">📈</span>
            <span>BUY</span>
          </button>
          <button
            className={`direction-btn sell ${direction === 'sell' ? 'active' : ''}`}
            onClick={() => setDirection('sell')}
          >
            <span className="direction-icon">📉</span>
            <span>SELL</span>
          </button>
        </div>

        {/* Quantity Selector */}
        <div className="trade-quantity">
          <label>Quantity (Lots)</label>
          <div className="quantity-controls">
            <button
              className="qty-btn"
              onClick={() => setQuantity(Math.max(1, quantity - 1))}
              disabled={quantity <= 1}
            >
              −
            </button>
            <input
              type="number"
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
              min="1"
              max="100"
            />
            <button
              className="qty-btn"
              onClick={() => setQuantity(Math.min(100, quantity + 1))}
              disabled={quantity >= 100}
            >
              +
            </button>
          </div>
          <span className="lot-info">{quantity} lot × {lotSize} = {quantity * lotSize} units</span>
        </div>

        {/* Advanced Toggle */}
        <button
          className="advanced-toggle"
          onClick={() => setShowAdvanced(!showAdvanced)}
        >
          <span>{showAdvanced ? '▼' : '▶'} Price Calculator</span>
          <span className="toggle-badge">{showAdvanced ? 'Hide' : 'Show'}</span>
        </button>

        {/* Target & Stop Loss Section */}
        {showAdvanced && (
          <div className="price-calculator-section">
            {/* Target Price */}
            <div className="price-input-group target">
              <div className="price-input-header">
                <div className="price-input-icon target">↑</div>
                <div className="price-input-label">
                  <span className="label-title">If {index} goes to</span>
                  <span className="label-subtitle">Your target spot price</span>
                </div>
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  className="price-input"
                  value={targetSpot > 0 ? targetSpot : ''}
                  onChange={(e) => {
                    const val = e.target.value.replace(/[^0-9]/g, '')
                    setTargetSpot(val === '' ? 0 : parseInt(val))
                    setTargetOptionPrice(0) // Reset option price to use calculated
                  }}
                  onFocus={(e) => e.target.select()}
                />
              </div>
              <div className="quick-adjust-buttons">
                <span className="quick-label">Quick:</span>
                {targetAdjustments.map(adj => (
                  <button
                    key={adj}
                    className="quick-btn target"
                    onClick={() => {
                      setTargetSpot(Math.round(spotPrice + adj))
                      setTargetOptionPrice(0) // Reset to use calculated
                    }}
                  >
                    +{adj}
                  </button>
                ))}
              </div>
            </div>

            {/* Stop Loss Price */}
            <div className="price-input-group stoploss">
              <div className="price-input-header">
                <div className="price-input-icon stoploss">↓</div>
                <div className="price-input-label">
                  <span className="label-title">If {index} falls to</span>
                  <span className="label-subtitle">Your stop-loss spot price</span>
                </div>
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  className="price-input"
                  value={stopLossSpot > 0 ? stopLossSpot : ''}
                  onChange={(e) => {
                    const val = e.target.value.replace(/[^0-9]/g, '')
                    setStopLossSpot(val === '' ? 0 : parseInt(val))
                    setStopLossOptionPrice(0) // Reset option price to use calculated
                  }}
                  onFocus={(e) => e.target.select()}
                />
              </div>
              <div className="quick-adjust-buttons">
                <span className="quick-label">Quick:</span>
                {stopLossAdjustments.map(adj => (
                  <button
                    key={adj}
                    className="quick-btn stoploss"
                    onClick={() => {
                      setStopLossSpot(Math.round(spotPrice + adj))
                      setStopLossOptionPrice(0) // Reset to use calculated
                    }}
                  >
                    {adj}
                  </button>
                ))}
              </div>
            </div>

            {/* Projections */}
            <div className="projections-section">
              <div className="projections-divider">
                <span>YOUR OPTION WILL BE</span>
              </div>

              {/* At Target */}
              <div className="projection-card target">
                <div className="projection-icon">🎯</div>
                <div className="projection-info">
                  <span className="projection-title">
                    At Target (
                    <input
                      type="text"
                      inputMode="numeric"
                      className="projection-input target"
                      value={targetSpot > 0 ? targetSpot : ''}
                      onChange={(e) => {
                        const val = e.target.value.replace(/[^0-9]/g, '')
                        setTargetSpot(val === '' ? 0 : parseInt(val))
                        setTargetOptionPrice(0)
                      }}
                      onFocus={(e) => e.target.select()}
                      onClick={(e) => e.stopPropagation()}
                    />)
                  </span>
                  <span className="projection-subtitle">Option Premium</span>
                </div>
                <div className="projection-values">
                  <input
                    type="text"
                    inputMode="decimal"
                    className="projection-price-input target"
                    value={targetOptionPrice > 0 ? targetOptionPrice.toFixed(2) : projections.calculatedTargetPrice.toFixed(2)}
                    onChange={(e) => {
                      const val = e.target.value.replace(/[^0-9.]/g, '')
                      setTargetOptionPrice(val === '' ? 0 : parseFloat(val) || 0)
                    }}
                    onFocus={(e) => e.target.select()}
                    onClick={(e) => e.stopPropagation()}
                  />
                  <span className={`projection-change ${projections.profitPercent >= 0 ? 'profit' : 'loss'}`}>
                    {projections.profitPercent >= 0 ? '↗' : '↘'} {projections.profitPercent >= 0 ? '+' : ''}{projections.profitPercent.toFixed(0)}%
                  </span>
                </div>
              </div>

              {/* At Stop Loss */}
              <div className="projection-card stoploss">
                <div className="projection-icon">🛑</div>
                <div className="projection-info">
                  <span className="projection-title">
                    At Stop-Loss (
                    <input
                      type="text"
                      inputMode="numeric"
                      className="projection-input stoploss"
                      value={stopLossSpot > 0 ? stopLossSpot : ''}
                      onChange={(e) => {
                        const val = e.target.value.replace(/[^0-9]/g, '')
                        setStopLossSpot(val === '' ? 0 : parseInt(val))
                        setStopLossOptionPrice(0)
                      }}
                      onFocus={(e) => e.target.select()}
                      onClick={(e) => e.stopPropagation()}
                    />)
                  </span>
                  <span className="projection-subtitle">Option Premium</span>
                </div>
                <div className="projection-values">
                  <input
                    type="text"
                    inputMode="decimal"
                    className="projection-price-input stoploss"
                    value={stopLossOptionPrice > 0 ? stopLossOptionPrice.toFixed(2) : projections.calculatedStopLossPrice.toFixed(2)}
                    onChange={(e) => {
                      const val = e.target.value.replace(/[^0-9.]/g, '')
                      setStopLossOptionPrice(val === '' ? 0 : parseFloat(val) || 0)
                    }}
                    onFocus={(e) => e.target.select()}
                    onClick={(e) => e.stopPropagation()}
                  />
                  <span className={`projection-change ${projections.lossPercent >= 0 ? 'profit' : 'loss'}`}>
                    {projections.lossPercent >= 0 ? '↗' : '↘'} {projections.lossPercent >= 0 ? '+' : ''}{projections.lossPercent.toFixed(0)}%
                  </span>
                </div>
              </div>

              {/* P&L Summary */}
              <div className="pnl-summary">
                <div className="pnl-item profit">
                  <span>Max Profit (at target)</span>
                  <span className="pnl-value">
                    {projections.profitAtTarget >= 0 ? '+' : ''}₹{Math.abs(projections.profitAtTarget).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </span>
                </div>
                <div className="pnl-item loss">
                  <span>Max Loss (at SL)</span>
                  <span className="pnl-value">
                    {projections.lossAtStopLoss >= 0 ? '+' : ''}₹{Math.abs(projections.lossAtStopLoss).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Trade Summary */}
        <div className="trade-summary">
          <div className="summary-row">
            <span>Price per unit</span>
            <span>₹{price.toFixed(2)}</span>
          </div>
          <div className="summary-row">
            <span>Total units</span>
            <span>{quantity * lotSize}</span>
          </div>
          <div className="summary-row total">
            <span>{direction === 'buy' ? 'Total Cost' : 'Total Value'}</span>
            <span>₹{totalValue.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>
          </div>
          {direction === 'sell' && (
            <div className="summary-row margin">
              <span>Margin Required (~20%)</span>
              <span>₹{marginRequired.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>
            </div>
          )}
          <div className="summary-row balance">
            <span>Available Balance</span>
            <span className={balance >= marginRequired ? 'sufficient' : 'insufficient'}>
              ₹{balance.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
            </span>
          </div>
        </div>

        {/* Error Message */}
        {error && (
          <div className="trade-error">
            <span>⚠️ {error}</span>
          </div>
        )}

        {/* Login Prompt for guests */}
        {!isAuthenticated && (
          <div className="login-prompt-inline">
            <span>🔐 Login to start paper trading with ₹10,00,000</span>
          </div>
        )}

        {/* Action Button */}
        <button
          className={`trade-submit-btn ${direction}`}
          onClick={handleTrade}
          disabled={loading || (isAuthenticated && !canTrade)}
        >
          {loading ? (
            <span className="loading-spinner-small"></span>
          ) : !isAuthenticated ? (
            'Login to Trade'
          ) : (
            `${direction.toUpperCase()} ${quantity} LOT${quantity > 1 ? 'S' : ''}`
          )}
        </button>

        {/* Expiry Info */}
        <div className="trade-expiry-info">
          <span>Expiry: {expiry}</span>
        </div>
      </div>
    </div>
  )
}

export default TradeModal
