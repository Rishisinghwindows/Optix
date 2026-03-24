import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { marketAPI } from '../../services/marketAPI'
import { paperTradingAPI } from '../../services/paperTradingAPI'
import { useAuth } from '../../context/AuthContext'
import { logScreenView, logEvent } from '../../services/analytics'
import TradeModal from './TradeModal'
import { AdBanner } from '../ads'
import { ADS_CONFIG } from '../../config/adsConfig'

const INDICES = {
  NIFTY: { name: 'NIFTY 50', lotSize: 75, step: 50, color: '#22d3ee', pairId: 17940 },
  BANKNIFTY: { name: 'BANK NIFTY', lotSize: 30, step: 100, color: '#a78bfa', pairId: 17950 },
  FINNIFTY: { name: 'FIN NIFTY', lotSize: 25, step: 50, color: '#34d399', pairId: 17940 },
  SENSEX: { name: 'SENSEX', lotSize: 10, step: 100, color: '#f472b6', pairId: 17941 },
  MIDCPNIFTY: { name: 'MIDCAP NIFTY', lotSize: 50, step: 25, color: '#fbbf24', pairId: 17940 },
  BANKEX: { name: 'BANKEX', lotSize: 15, step: 100, color: '#fb923c', pairId: 17941 },
}

// View Chart Link Component - Opens TradingView chart
function ViewChartLink({ index }) {
  // TradingView symbol mapping
  const symbolMap = {
    NIFTY: 'NSE:NIFTY',
    BANKNIFTY: 'NSE:BANKNIFTY',
    FINNIFTY: 'NSE:FINNIFTY',
    SENSEX: 'BSE:SENSEX',
    MIDCPNIFTY: 'NSE:NIFTY',
    BANKEX: 'BSE:BANKEX'
  }

  const symbol = symbolMap[index] || 'NSE:NIFTY'
  const chartUrl = `https://www.tradingview.com/chart/?symbol=${symbol}`

  const handleOpenChart = () => {
    window.open(chartUrl, '_blank')
  }

  return (
    <button className="view-chart-link" onClick={handleOpenChart}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M3 3v18h18" />
        <path d="M18 9l-5 5-4-4-3 3" />
      </svg>
      <span>View Chart</span>
      <svg className="external-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
        <polyline points="15 3 21 3 21 9" />
        <line x1="10" y1="14" x2="21" y2="3" />
      </svg>
    </button>
  )
}

// OI Chart Modal Component
function OIChartModal({ isOpen, onClose, optionChain, spotPrice }) {
  if (!isOpen) return null

  // Get max OI for scaling
  const maxOI = Math.max(
    ...optionChain.map(row => Math.max(row.call.oi, row.put.oi)),
    1
  )

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="oi-chart-modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Open Interest Analysis</h3>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>

        <div className="oi-chart-container">
          <div className="oi-chart-labels">
            <span className="chart-label call-label">Call OI</span>
            <span className="chart-label strike-label">Strike</span>
            <span className="chart-label put-label">Put OI</span>
          </div>

          <div className="oi-chart-bars">
            {optionChain.map((row) => {
              const callWidth = (row.call.oi / maxOI) * 100
              const putWidth = (row.put.oi / maxOI) * 100
              const isATM = row.isATM
              const isSpotNear = Math.abs(row.strike - spotPrice) < 100

              return (
                <div key={row.strike} className={`oi-bar-row ${isATM ? 'atm' : ''}`}>
                  <div className="call-bar-container">
                    <div
                      className="oi-bar call-bar"
                      style={{ width: `${callWidth}%` }}
                    >
                      {callWidth > 20 && <span className="bar-value">{formatOI(row.call.oi)}</span>}
                    </div>
                  </div>
                  <div className={`strike-label-chart ${isATM ? 'atm' : ''} ${isSpotNear ? 'near-spot' : ''}`}>
                    {row.strike}
                  </div>
                  <div className="put-bar-container">
                    <div
                      className="oi-bar put-bar"
                      style={{ width: `${putWidth}%` }}
                    >
                      {putWidth > 20 && <span className="bar-value">{formatOI(row.put.oi)}</span>}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <div className="oi-chart-summary">
          <div className="summary-item">
            <span className="summary-label">Total Call OI</span>
            <span className="summary-value call">{formatOI(optionChain.reduce((sum, r) => sum + r.call.oi, 0))}</span>
          </div>
          <div className="summary-item">
            <span className="summary-label">Total Put OI</span>
            <span className="summary-value put">{formatOI(optionChain.reduce((sum, r) => sum + r.put.oi, 0))}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

// Helper function for OI formatting
function formatOI(num) {
  if (num >= 10000000) return (num / 10000000).toFixed(2) + 'Cr'
  if (num >= 100000) return (num / 100000).toFixed(2) + 'L'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num?.toFixed?.(0) || num
}

// Helper function to determine if OI change is significant
function getOIClass(oiChange, oi) {
  if (!oiChange || oiChange === 0) return ''

  const isUp = oiChange > 0
  const absChange = Math.abs(oiChange)
  const changePercent = oi > 0 ? (absChange / oi) * 100 : 0

  // Consider significant if change is >= 10% of OI OR absolute change >= 50000
  const isSignificant = changePercent >= 10 || absChange >= 50000

  if (isUp) {
    return isSignificant ? 'oi-up oi-significant' : 'oi-up'
  } else {
    return isSignificant ? 'oi-down oi-significant-down' : 'oi-down'
  }
}

// Portfolio Widget Component
function PortfolioWidget({ portfolio, positions, onNavigate, isAuthenticated }) {
  if (!isAuthenticated) {
    return (
      <div className="portfolio-widget guest">
        <div className="pw-content">
          <span className="pw-icon">💼</span>
          <span className="pw-text">Login to start Paper Trading with ₹10,00,000</span>
          <button className="pw-login-btn" onClick={() => onNavigate('/login')}>Login</button>
        </div>
      </div>
    )
  }

  const totalPnL = portfolio?.total_pnl || 0
  const openPositions = positions?.length || 0
  const cashBalance = portfolio?.cash_balance || 1000000

  return (
    <div className="portfolio-widget">
      <div className="pw-item balance">
        <span className="pw-label">Balance</span>
        <span className="pw-value">₹{(cashBalance / 100000).toFixed(2)}L</span>
      </div>
      <div className="pw-divider" />
      <div className="pw-item positions">
        <span className="pw-label">Positions</span>
        <span className="pw-value">{openPositions}</span>
      </div>
      <div className="pw-divider" />
      <div className="pw-item pnl">
        <span className="pw-label">P&L</span>
        <span className={`pw-value ${totalPnL >= 0 ? 'positive' : 'negative'}`}>
          {totalPnL >= 0 ? '+' : ''}₹{Math.abs(totalPnL).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
        </span>
      </div>
      <button className="pw-view-btn" onClick={() => onNavigate('/app/paper-trading')}>
        View Portfolio →
      </button>
    </div>
  )
}

function OptionChain() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [selectedIndex, setSelectedIndex] = useState('NIFTY')
  const [spotPrice, setSpotPrice] = useState(0)
  const [spotChange, setSpotChange] = useState(0)
  const [priceChange, setPriceChange] = useState(0)
  const [selectedExpiry, setSelectedExpiry] = useState(0)
  const [expiries, setExpiries] = useState([])
  const [optionChain, setOptionChain] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [dataSource, setDataSource] = useState('demo')
  const [lastUpdate, setLastUpdate] = useState(null)
  const [indiaVix, setIndiaVix] = useState({ value: 0, change: 0, lastUpdate: null })
  const [chainTotals, setChainTotals] = useState(null) // Full-chain OI totals from backend
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [showOIChart, setShowOIChart] = useState(false)
  const [showTradeModal, setShowTradeModal] = useState(false)
  const [selectedOption, setSelectedOption] = useState(null)
  const [portfolio, setPortfolio] = useState(null)
  const [positions, setPositions] = useState([])
  const refreshIntervalRef = useRef(null)

  // Firebase screen_view — fired once when the component mounts
  useEffect(() => { logScreenView('option_chain'); }, [])

  // Fire a detailed analytics event each time the user switches index/expiry
  // or when spot price first loads, so we can see which chains are most viewed.
  useEffect(() => {
    if (spotPrice) {
      logEvent('option_chain_view', {
        index: selectedIndex,
        expiry: expiries[selectedExpiry]?.value || '',
        spot_price: Math.round(spotPrice),
        is_live: dataSource.includes('live') || dataSource.includes('upstox'),
      });
    }
  }, [selectedIndex, selectedExpiry, spotPrice]);

  // Fetch portfolio data
  const fetchPortfolio = useCallback(async () => {
    if (!isAuthenticated) return
    try {
      const [portfolioData, positionsData] = await Promise.all([
        paperTradingAPI.getPortfolio(),
        paperTradingAPI.getPositions(),
      ])
      setPortfolio(portfolioData)
      setPositions(positionsData.filter(p => p.status === 'open'))
    } catch (err) {
      // Portfolio fetch failed silently
    }
  }, [isAuthenticated])

  // Fetch portfolio on mount and after trades
  useEffect(() => {
    fetchPortfolio()
  }, [fetchPortfolio])

  // Calculate Max Pain
  const calculateMaxPain = useCallback(() => {
    if (optionChain.length === 0) return 0

    const strikes = optionChain.map(row => row.strike)
    let minPain = Infinity
    let maxPainStrike = strikes[0]

    for (const testStrike of strikes) {
      let totalPain = 0

      for (const row of optionChain) {
        // Call writers' pain (if spot ends above strike, calls are ITM)
        if (testStrike > row.strike) {
          totalPain += row.call.oi * (testStrike - row.strike)
        }
        // Put writers' pain (if spot ends below strike, puts are ITM)
        if (testStrike < row.strike) {
          totalPain += row.put.oi * (row.strike - testStrike)
        }
      }

      if (totalPain < minPain) {
        minPain = totalPain
        maxPainStrike = testStrike
      }
    }

    return maxPainStrike
  }, [optionChain])

  // Calculate market metrics
  const getMarketMetrics = useCallback(() => {
    if (optionChain.length === 0) return { pcr: 0, totalCallOI: 0, totalPutOI: 0, support: 0, resistance: 0 }

    // Use full-chain totals from backend for accurate PCR (covers ALL strikes)
    // Fall back to summing visible rows if backend totals unavailable
    let totalCallOI = 0
    let totalPutOI = 0

    if (chainTotals) {
      totalCallOI = chainTotals.CE?.totalOI || 0
      totalPutOI = chainTotals.PE?.totalOI || 0
    } else {
      optionChain.forEach(row => {
        totalCallOI += row.call.oi
        totalPutOI += row.put.oi
      })
    }

    let maxCallOI = 0
    let maxPutOI = 0
    let resistance = 0
    let support = 0

    optionChain.forEach(row => {
      if (row.call.oi > maxCallOI && row.strike > spotPrice) {
        maxCallOI = row.call.oi
        resistance = row.strike
      }
      if (row.put.oi > maxPutOI && row.strike < spotPrice) {
        maxPutOI = row.put.oi
        support = row.strike
      }
    })

    const pcr = totalCallOI > 0 ? (totalPutOI / totalCallOI) : 0

    return { pcr, totalCallOI, totalPutOI, support, resistance }
  }, [optionChain, spotPrice, chainTotals])

  // Fetch India VIX
  const fetchIndiaVix = useCallback(async () => {
    try {
      const data = await marketAPI.getSpotPrice('INDIAVIX')
      setIndiaVix({
        value: data.lastPrice || data.spotPrice || 0,
        change: data.pChange || data.change || 0,
        lastUpdate: new Date()
      })
    } catch (err) {
      // Fallback to demo VIX value
      setIndiaVix({
        value: 13.5 + Math.random() * 2,
        change: Math.random() * 2 - 1,
        lastUpdate: new Date()
      })
    }
  }, [])

  // Fetch spot price
  const fetchSpotPrice = useCallback(async (symbol) => {
    try {
      const data = await marketAPI.getSpotPrice(symbol)
      const newPrice = data.lastPrice || data.spotPrice || 0
      setPriceChange(data.change || 0)
      setSpotPrice(newPrice)
      setSpotChange(data.pChange || data.percentChange || 0)
      setDataSource(data.dataSource || 'live')

      // Handle cache metadata
      if (data._isStale) {
        setIsRefreshing(true)
      } else {
        setIsRefreshing(false)
        if (!data._fromCache) {
          setLastUpdate(new Date())
        }
      }
    } catch (err) {
      // Spot price fetch failed silently
    }
  }, [])

  // Fetch expiry dates
  const fetchExpiries = useCallback(async (symbol) => {
    try {
      const data = await marketAPI.getExpiryDates(symbol)
      const expiryDates = data.expiryDates || []

      const today = new Date()
      today.setHours(0, 0, 0, 0)

      const formattedExpiries = expiryDates.map((expiry, idx) => {
        const parts = expiry.split('-')
        const expiryDate = new Date(`${parts[1]} ${parts[0]}, ${parts[2]}`)
        const days = Math.ceil((expiryDate - today) / (1000 * 60 * 60 * 24))

        return {
          label: idx === 0 ? 'Weekly' : expiry,
          shortLabel: idx === 0 ? 'Weekly' : `${parts[0]} ${parts[1]}`,
          days: Math.max(0, days),
          date: expiryDate,
          value: expiry
        }
      })

      setExpiries(formattedExpiries)
      setSelectedExpiry(0)
    } catch (err) {
      setExpiries([])
    }
  }, [])

  // Fetch option chain data
  const fetchOptionChain = useCallback(async (symbol, expiry, isBackgroundRefresh = false) => {
    try {
      // Only show full loading on initial load, not background refreshes
      if (!isBackgroundRefresh) {
        setLoading(true)
      }
      setError(null)

      const options = { strikes: 15 }
      if (expiry) {
        options.expiry = expiry
      }

      const data = await marketAPI.getOptionChain(symbol, options)

      const spotPriceValue = data.underlyingValue || data.spotPrice || 0
      if (spotPriceValue) {
        setSpotPrice(spotPriceValue)
      }
      if (data.pChange !== undefined || data.percentChange !== undefined) {
        setSpotChange(data.pChange || data.percentChange || 0)
      }
      setDataSource(data.dataSource || 'live')

      // Handle cache metadata
      if (data._isStale) {
        setIsRefreshing(true)
      } else {
        setIsRefreshing(false)
        if (!data._fromCache) {
          setLastUpdate(new Date())
        }
      }

      const rawData = data.data || data.strikes || []
      const atmStrike = data.atmStrike || 0

      const transformedData = rawData.map(row => {
        const strike = row.strikePrice || row.strike
        const isATM = strike === atmStrike
        const isITMCall = strike < spotPriceValue
        const isITMPut = strike > spotPriceValue

        const ce = row.CE || row.call || {}
        const pe = row.PE || row.put || {}

        return {
          strike,
          isATM,
          call: {
            ltp: ce.lastPrice || ce.ltp || 0,
            change: ce.change || 0,
            iv: ce.impliedVolatility || ce.iv || 0,
            oi: ce.openInterest || ce.oi || 0,
            oiChange: ce.changeinOpenInterest || 0,
            volume: ce.totalTradedVolume || ce.volume || 0,
            bidPrice: ce.bidprice || ce.bidPrice || 0,
            askPrice: ce.askPrice || 0,
            isITM: isITMCall,
          },
          put: {
            ltp: pe.lastPrice || pe.ltp || 0,
            change: pe.change || 0,
            iv: pe.impliedVolatility || pe.iv || 0,
            oi: pe.openInterest || pe.oi || 0,
            oiChange: pe.changeinOpenInterest || 0,
            volume: pe.totalTradedVolume || pe.volume || 0,
            bidPrice: pe.bidprice || pe.bidPrice || 0,
            askPrice: pe.askPrice || 0,
            isITM: isITMPut,
          }
        }
      })

      setOptionChain(transformedData)

      // Store full-chain totals from backend for accurate PCR
      if (data.totals) {
        setChainTotals(data.totals)
      } else {
        setChainTotals(null)
      }
    } catch (err) {
      setError(err.message || 'Failed to fetch option chain data')
      setOptionChain([])
      setChainTotals(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSpotPrice(selectedIndex)
    fetchExpiries(selectedIndex)
    fetchIndiaVix()
  }, [selectedIndex, fetchSpotPrice, fetchExpiries, fetchIndiaVix])

  useEffect(() => {
    if (expiries.length > 0 && expiries[selectedExpiry]) {
      fetchOptionChain(selectedIndex, expiries[selectedExpiry].value)
    } else if (expiries.length === 0) {
      fetchOptionChain(selectedIndex, null)
    }
  }, [selectedIndex, selectedExpiry, expiries, fetchOptionChain])

  useEffect(() => {
    refreshIntervalRef.current = setInterval(() => {
      fetchSpotPrice(selectedIndex)
      fetchIndiaVix()
      if (expiries.length > 0 && expiries[selectedExpiry]) {
        // Don't clear cache - let stale-while-revalidate handle it
        // This shows cached data instantly while fetching fresh data in background
        fetchOptionChain(selectedIndex, expiries[selectedExpiry].value, true)
      }
    }, 5000)

    return () => {
      if (refreshIntervalRef.current) {
        clearInterval(refreshIntervalRef.current)
      }
    }
  }, [selectedIndex, selectedExpiry, expiries, fetchSpotPrice, fetchOptionChain, fetchIndiaVix])

  const handleIndexChange = (index) => {
    setSelectedIndex(index)
    setLoading(true)
  }

  const handleStrikeClick = (strike, type, optionData) => {
    // Open trade modal with the selected option
    setSelectedOption({
      strike,
      type,
      ltp: optionData.ltp,
      change: optionData.change,
      iv: optionData.iv,
      oi: optionData.oi,
    })
    setShowTradeModal(true)
  }

  const formatNumber = (num) => {
    if (num >= 10000000) return (num / 10000000).toFixed(2) + 'Cr'
    if (num >= 100000) return (num / 100000).toFixed(2) + 'L'
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
    return num?.toFixed?.(0) || num
  }

  const formatPrice = (num) => {
    return num?.toLocaleString('en-IN', { maximumFractionDigits: 2, minimumFractionDigits: 2 }) || '0.00'
  }

  const metrics = getMarketMetrics()
  const maxPain = calculateMaxPain()

  return (
    <div className="option-chain-page">
      {/* Portfolio Widget */}
      <PortfolioWidget
        portfolio={portfolio}
        positions={positions}
        onNavigate={navigate}
        isAuthenticated={isAuthenticated}
      />

      {/* Combined Top Controls Row */}
      <div className="top-controls-row">
        {/* Index Selector */}
        <div className="index-selector-compact">
          {Object.entries(INDICES).map(([key, value]) => (
            <button
              key={key}
              className={`index-pill ${selectedIndex === key ? 'active' : ''}`}
              onClick={() => handleIndexChange(key)}
              style={{ '--pill-color': value.color }}
            >
              <span className="pill-name">{key}</span>
            </button>
          ))}
        </div>

        {/* Quick Stats */}
        <div className="quick-stats">
          <div className="stat-chip max-pain">
            <span className="stat-label">Max Pain</span>
            <span className="stat-value">{maxPain.toLocaleString('en-IN')}</span>
          </div>
          <div className={`stat-chip vix ${indiaVix.change >= 0 ? 'up' : 'down'}`}>
            <span className="stat-label">
              India VIX
              <span className="vix-pulse"></span>
            </span>
            <span className="stat-value">
              {indiaVix.value.toFixed(2)}
              <span className={`vix-badge ${indiaVix.change >= 0 ? 'up' : 'down'}`}>
                {indiaVix.change >= 0 ? '▲' : '▼'}{Math.abs(indiaVix.change).toFixed(2)}%
              </span>
            </span>
          </div>
        </div>

        {/* OI Chart Button */}
        <button className="oi-chart-btn-compact" onClick={() => setShowOIChart(true)}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <path d="M3 9h18M9 21V9"/>
          </svg>
          <span>OI Chart</span>
        </button>
      </div>

      {/* Spot Price Card - Premium Design */}
      <div className="spot-card-premium">
        {/* Left Section - Price */}
        <div className="spot-price-section">
          <div className="spot-header">
            <span className="index-name">{INDICES[selectedIndex].name}</span>
            <span className={`live-badge ${dataSource.includes('live') || dataSource.includes('upstox') ? 'live' : 'cached'} ${isRefreshing ? 'refreshing' : ''}`}>
              <span className={isRefreshing ? 'spin' : 'pulse'}></span>
              {isRefreshing ? 'UPDATING' : (dataSource.includes('live') || dataSource.includes('upstox') ? 'LIVE' : 'CACHED')}
            </span>
          </div>
          <div className="spot-price-display">
            <span className="price-value">₹{formatPrice(spotPrice)}</span>
            <div className={`price-change ${spotChange >= 0 ? 'up' : 'down'}`}>
              <span className="change-arrow">{spotChange >= 0 ? '▲' : '▼'}</span>
              <span className="change-amount">{Math.abs(priceChange).toFixed(2)}</span>
              <span className="change-percent">({Math.abs(spotChange).toFixed(2)}%)</span>
            </div>
          </div>
          {lastUpdate && (
            <div className="update-time">
              Updated: {lastUpdate.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
            </div>
          )}
        </div>

        {/* Divider */}
        <div className="spot-divider"></div>

        {/* Middle Section - Key Metrics */}
        <div className="spot-metrics-grid">
          <div className="metric-box">
            <span className="metric-icon">📊</span>
            <div className="metric-content">
              <span className="metric-label">Lot Size</span>
              <span className="metric-value">{INDICES[selectedIndex].lotSize}</span>
            </div>
          </div>
          <div className="metric-box pcr">
            <span className="metric-icon">{metrics.pcr > 1 ? '🟢' : '🔴'}</span>
            <div className="metric-content">
              <span className="metric-label">PCR</span>
              <span className={`metric-value ${metrics.pcr > 1 ? 'bullish' : 'bearish'}`}>{metrics.pcr.toFixed(2)}</span>
            </div>
          </div>
          <div className="metric-box support">
            <span className="metric-icon">⬇️</span>
            <div className="metric-content">
              <span className="metric-label">Support</span>
              <span className="metric-value green">{metrics.support || '-'}</span>
            </div>
          </div>
          <div className="metric-box resistance">
            <span className="metric-icon">⬆️</span>
            <div className="metric-content">
              <span className="metric-label">Resistance</span>
              <span className="metric-value red">{metrics.resistance || '-'}</span>
            </div>
          </div>
          <div className="metric-box">
            <span className="metric-icon">📈</span>
            <div className="metric-content">
              <span className="metric-label">Call OI</span>
              <span className="metric-value">{formatNumber(metrics.totalCallOI)}</span>
            </div>
          </div>
          <div className="metric-box">
            <span className="metric-icon">📉</span>
            <div className="metric-content">
              <span className="metric-label">Put OI</span>
              <span className="metric-value">{formatNumber(metrics.totalPutOI)}</span>
            </div>
          </div>
        </div>

        {/* Right Section - Chart Button */}
        <div className="spot-actions">
          <ViewChartLink index={selectedIndex} />
        </div>
      </div>

      {/* Expiry Selector - Horizontal Scroll */}
      <div className="expiry-selector">
        <div className="expiry-scroll">
          {expiries.length > 0 ? (
            expiries.slice(0, 8).map((expiry, idx) => (
              <button
                key={idx}
                className={`expiry-chip ${selectedExpiry === idx ? 'active' : ''}`}
                onClick={() => setSelectedExpiry(idx)}
              >
                <span className="expiry-label">{expiry.shortLabel}</span>
                <span className="expiry-days">{expiry.days}d</span>
              </button>
            ))
          ) : (
            <div className="expiry-loading">Loading expiries...</div>
          )}
        </div>
      </div>

      {/* Loading Overlay */}
      {loading && (
        <div className="chain-loading">
          <div className="loading-spinner-chain"></div>
          <span>Fetching live data...</span>
        </div>
      )}

      {/* Error State */}
      {error && !loading && (
        <div className="chain-error">
          <span className="error-icon">⚠️</span>
          <span>{error}</span>
          <button onClick={() => fetchOptionChain(selectedIndex, expiries[selectedExpiry]?.value)}>
            Retry
          </button>
        </div>
      )}

      {/* Option Chain Table - Premium Design */}
      <div className={`chain-wrapper ${loading ? 'loading' : ''}`}>
        <table className="chain-table-new">
          <thead>
            <tr className="header-row">
              <th colSpan="5" className="calls-header">
                <div className="header-content">
                  <span className="header-icon">📈</span>
                  <span>CALLS</span>
                </div>
              </th>
              <th className="strike-header-main">STRIKE</th>
              <th colSpan="5" className="puts-header">
                <div className="header-content">
                  <span>PUTS</span>
                  <span className="header-icon">📉</span>
                </div>
              </th>
            </tr>
            <tr className="subheader-row">
              <th>OI</th>
              <th>Chg</th>
              <th>Vol</th>
              <th>IV</th>
              <th>LTP</th>
              <th></th>
              <th>LTP</th>
              <th>IV</th>
              <th>Vol</th>
              <th>Chg</th>
              <th>OI</th>
            </tr>
          </thead>
          <tbody>
            {optionChain.map((row) => (
              <tr
                key={row.strike}
                className={`data-row ${row.isATM ? 'atm' : ''} ${row.call.isITM ? 'call-itm' : ''} ${row.put.isITM ? 'put-itm' : ''} ${row.strike === maxPain ? 'max-pain-row' : ''}`}
              >
                {/* Call Side */}
                <td className={`oi-cell ${getOIClass(row.call.oiChange, row.call.oi)}`}>
                  {formatNumber(row.call.oi)}
                </td>
                <td className={`change-cell ${row.call.change >= 0 ? 'up' : 'down'}`}>
                  {row.call.change >= 0 ? '+' : ''}{row.call.change.toFixed(2)}
                </td>
                <td className="vol-cell">{formatNumber(row.call.volume)}</td>
                <td className="iv-cell">{row.call.iv > 0 ? row.call.iv.toFixed(1) + '%' : '-'}</td>
                <td
                  className={`ltp-cell call ${row.call.isITM ? 'itm' : 'otm'}`}
                  onClick={() => handleStrikeClick(row.strike, 'call', row.call)}
                  title="Click to trade"
                >
                  <div className="ltp-content">
                    <span className="ltp-price">{formatPrice(row.call.ltp)}</span>
                    <span className={`ltp-trend ${row.call.change >= 0 ? 'up' : 'down'}`}>
                      {row.call.change >= 0 ? '↑' : '↓'}
                    </span>
                  </div>
                </td>

                {/* Strike */}
                <td className={`strike-cell ${row.isATM ? 'atm' : ''} ${row.strike === maxPain ? 'max-pain' : ''}`}>
                  <div className="strike-content">
                    <span className="strike-value">{row.strike}</span>
                    {row.isATM && <span className="atm-tag">ATM</span>}
                    {row.strike === maxPain && !row.isATM && <span className="mp-tag">MP</span>}
                  </div>
                </td>

                {/* Put Side */}
                <td
                  className={`ltp-cell put ${row.put.isITM ? 'itm' : 'otm'}`}
                  onClick={() => handleStrikeClick(row.strike, 'put', row.put)}
                  title="Click to trade"
                >
                  <div className="ltp-content">
                    <span className={`ltp-trend ${row.put.change >= 0 ? 'up' : 'down'}`}>
                      {row.put.change >= 0 ? '↑' : '↓'}
                    </span>
                    <span className="ltp-price">{formatPrice(row.put.ltp)}</span>
                  </div>
                </td>
                <td className="iv-cell">{row.put.iv > 0 ? row.put.iv.toFixed(1) + '%' : '-'}</td>
                <td className="vol-cell">{formatNumber(row.put.volume)}</td>
                <td className={`change-cell ${row.put.change >= 0 ? 'up' : 'down'}`}>
                  {row.put.change >= 0 ? '+' : ''}{row.put.change.toFixed(2)}
                </td>
                <td className={`oi-cell ${getOIClass(row.put.oiChange, row.put.oi)}`}>
                  {formatNumber(row.put.oi)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Legend */}
      <div className="chain-legend-new">
        <div className="legend-item-new">
          <span className="legend-dot itm-call"></span>
          <span>Call ITM</span>
        </div>
        <div className="legend-item-new">
          <span className="legend-dot itm-put"></span>
          <span>Put ITM</span>
        </div>
        <div className="legend-item-new">
          <span className="legend-dot atm"></span>
          <span>ATM</span>
        </div>
        <div className="legend-item-new">
          <span className="legend-dot max-pain"></span>
          <span>Max Pain</span>
        </div>
      </div>

      {/* Sidebar Ad */}
      <div className="chain-sidebar-ad">
        <AdBanner
          slot={ADS_CONFIG.adUnits.optionChainSidebar}
          placement="optionChainSidebar"
          className="ad-sidebar"
        />
      </div>

      {/* OI Chart Modal */}
      <OIChartModal
        isOpen={showOIChart}
        onClose={() => setShowOIChart(false)}
        optionChain={optionChain}
        spotPrice={spotPrice}
      />

      {/* Trade Modal */}
      <TradeModal
        isOpen={showTradeModal}
        onClose={() => {
          setShowTradeModal(false)
          setSelectedOption(null)
        }}
        option={selectedOption}
        index={selectedIndex}
        expiry={expiries[selectedExpiry]?.value || ''}
        spotPrice={spotPrice}
        lotSize={INDICES[selectedIndex].lotSize}
        onTradeSuccess={() => {
          // Refresh portfolio after successful trade
          fetchPortfolio()
        }}
      />
    </div>
  )
}

export default OptionChain
