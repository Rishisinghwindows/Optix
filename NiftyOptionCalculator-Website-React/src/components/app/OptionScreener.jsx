import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { marketAPI } from '../../services/marketAPI'
import { filterOptions, sortResults, getDefaultFilters } from '../../utils/screenerEngine'
import { getMoneyness } from '../../utils/blackScholes'

const INDICES = {
  NIFTY: { name: 'NIFTY 50', lotSize: 75, step: 50, color: '#22d3ee' },
  BANKNIFTY: { name: 'BANK NIFTY', lotSize: 30, step: 100, color: '#a78bfa' },
  FINNIFTY: { name: 'FIN NIFTY', lotSize: 25, step: 50, color: '#34d399' },
  SENSEX: { name: 'SENSEX', lotSize: 10, step: 100, color: '#f472b6' },
  MIDCPNIFTY: { name: 'MIDCAP NIFTY', lotSize: 50, step: 25, color: '#fbbf24' },
}

function OptionScreener() {
  const navigate = useNavigate()

  // Data state
  const [selectedIndex, setSelectedIndex] = useState('NIFTY')
  const [expiries, setExpiries] = useState([])
  const [selectedExpiry, setSelectedExpiry] = useState(0)
  const [rawChainData, setRawChainData] = useState([])
  const [spotPrice, setSpotPrice] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [dataSource, setDataSource] = useState('demo')
  const [lastUpdate, setLastUpdate] = useState(null)

  // Filter state
  const [filters, setFilters] = useState(getDefaultFilters())

  // Results
  const [results, setResults] = useState([])

  // Refresh interval
  const refreshIntervalRef = useRef(null)

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
          value: expiry,
        }
      })

      setExpiries(formattedExpiries)
      setSelectedExpiry(0)
    } catch (err) {
      setExpiries([])
    }
  }, [])

  // Fetch option chain and normalize to screenerEngine format
  const fetchOptionChain = useCallback(async (symbol, expiry, isBackground = false) => {
    try {
      if (!isBackground) {
        setLoading(true)
      }
      setError(null)

      const options = { strikes: 30 } // Wider range for screener
      if (expiry) {
        options.expiry = expiry
      }

      const data = await marketAPI.getOptionChain(symbol, options)

      const spotPriceValue = data.underlyingValue || data.spotPrice || 0
      if (spotPriceValue) {
        setSpotPrice(spotPriceValue)
      }
      setDataSource(data.dataSource || 'live')

      if (!data._fromCache) {
        setLastUpdate(new Date())
      }

      const rawData = data.data || data.strikes || []

      // Normalize API data into the format screenerEngine expects
      const normalizedChain = rawData.map((row) => {
        const strike = row.strikePrice || row.strike
        const ce = row.CE || row.call || {}
        const pe = row.PE || row.put || {}

        return {
          strikePrice: strike,
          callOption: {
            lastTradedPrice: ce.lastPrice || ce.ltp || 0,
            openInterest: ce.openInterest || ce.oi || 0,
            changeInOI: ce.changeinOpenInterest || ce.oiChange || 0,
            impliedVolatility: ce.impliedVolatility || ce.iv || 0,
            volume: ce.totalTradedVolume || ce.volume || 0,
            delta: ce.delta || 0,
            gamma: ce.gamma || 0,
            theta: ce.theta || 0,
            vega: ce.vega || 0,
            underlyingValue: spotPriceValue,
          },
          putOption: {
            lastTradedPrice: pe.lastPrice || pe.ltp || 0,
            openInterest: pe.openInterest || pe.oi || 0,
            changeInOI: pe.changeinOpenInterest || pe.oiChange || 0,
            impliedVolatility: pe.impliedVolatility || pe.iv || 0,
            volume: pe.totalTradedVolume || pe.volume || 0,
            delta: pe.delta || 0,
            gamma: pe.gamma || 0,
            theta: pe.theta || 0,
            vega: pe.vega || 0,
            underlyingValue: spotPriceValue,
          },
        }
      })

      setRawChainData(normalizedChain)
    } catch (err) {
      setError(err.message || 'Failed to fetch option chain data')
      setRawChainData([])
    } finally {
      setLoading(false)
    }
  }, [])

  // Apply filters whenever raw data or filters change
  useEffect(() => {
    if (rawChainData.length === 0) {
      setResults([])
      return
    }

    const filtered = filterOptions(rawChainData, filters)
    const sorted = sortResults(filtered, filters.sortBy, filters.sortAscending)
    setResults(sorted)
  }, [rawChainData, filters])

  // Fetch expiries when index changes
  useEffect(() => {
    fetchExpiries(selectedIndex)
  }, [selectedIndex, fetchExpiries])

  // Fetch chain when index or expiry changes
  useEffect(() => {
    if (expiries.length > 0 && expiries[selectedExpiry]) {
      fetchOptionChain(selectedIndex, expiries[selectedExpiry].value)
    } else if (expiries.length === 0) {
      fetchOptionChain(selectedIndex, null)
    }
  }, [selectedIndex, selectedExpiry, expiries, fetchOptionChain])

  // Auto-refresh
  useEffect(() => {
    refreshIntervalRef.current = setInterval(() => {
      if (expiries.length > 0 && expiries[selectedExpiry]) {
        fetchOptionChain(selectedIndex, expiries[selectedExpiry].value, true)
      }
    }, 10000) // 10 second refresh for screener

    return () => {
      if (refreshIntervalRef.current) {
        clearInterval(refreshIntervalRef.current)
      }
    }
  }, [selectedIndex, selectedExpiry, expiries, fetchOptionChain])

  // Filter update helpers
  const updateFilter = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }))
  }

  const resetFilters = () => {
    setFilters(getDefaultFilters())
  }

  const handleIndexChange = (index) => {
    setSelectedIndex(index)
    setLoading(true)
  }

  // Format helpers
  const formatOI = (num) => {
    if (num >= 10000000) return (num / 10000000).toFixed(2) + 'Cr'
    if (num >= 100000) return (num / 100000).toFixed(2) + 'L'
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
    return num?.toFixed?.(0) || '0'
  }

  const formatPrice = (num) => {
    return num?.toLocaleString('en-IN', { maximumFractionDigits: 2, minimumFractionDigits: 2 }) || '0.00'
  }

  // Navigate to simulator
  const openSimulator = (row) => {
    const dte = expiries[selectedExpiry]?.days || 7
    const params = new URLSearchParams({
      spotPrice: row.underlyingValue || spotPrice,
      strikePrice: row.strikePrice,
      optionType: row.optionType === 'CE' ? 'call' : 'put',
      premium: row.ltp,
      iv: row.iv,
      daysToExpiry: dte,
      lotSize: INDICES[selectedIndex].lotSize,
      quantity: 1,
    })
    navigate(`/app/simulator?${params.toString()}`)
  }

  return (
    <div className="screener-container">
      {/* Header */}
      <div className="screener-header">
        <h2 className="screener-title">Options Screener</h2>
        <div className="screener-header-info">
          {spotPrice > 0 && (
            <span className="screener-spot">
              {INDICES[selectedIndex].name}: <strong>{formatPrice(spotPrice)}</strong>
            </span>
          )}
          {lastUpdate && (
            <span className="screener-update-time">
              Updated: {lastUpdate.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
            </span>
          )}
        </div>
      </div>

      {/* Index Selector */}
      <div className="screener-index-selector">
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

      {/* Expiry Selector */}
      <div className="screener-expiry-selector">
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

      {/* Filter Panel */}
      <div className="screener-filters">
        {/* Row 1: Option Type + Moneyness */}
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label">Option Type</label>
            <div className="pill-group">
              {['CE', 'PE', 'Both'].map((type) => (
                <button
                  key={type}
                  className={`filter-pill ${filters.optionType === type ? 'active' : ''}`}
                  onClick={() => updateFilter('optionType', type)}
                >
                  {type}
                </button>
              ))}
            </div>
          </div>

          <div className="filter-group">
            <label className="filter-label">Moneyness</label>
            <div className="pill-group">
              {['All', 'ATM', 'ITM', 'OTM'].map((m) => (
                <button
                  key={m}
                  className={`filter-pill ${filters.moneyness === m ? 'active' : ''}`}
                  onClick={() => updateFilter('moneyness', m)}
                >
                  {m}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Row 2: Range filters */}
        <div className="filter-row">
          <div className="filter-group range-group">
            <label className="filter-label">Delta Range</label>
            <div className="range-inputs">
              <input
                type="number"
                className="filter-input"
                placeholder="Min"
                value={filters.deltaMin}
                min={0}
                max={1}
                step={0.05}
                onChange={(e) => updateFilter('deltaMin', parseFloat(e.target.value) || 0)}
              />
              <span className="range-separator">-</span>
              <input
                type="number"
                className="filter-input"
                placeholder="Max"
                value={filters.deltaMax}
                min={0}
                max={1}
                step={0.05}
                onChange={(e) => updateFilter('deltaMax', parseFloat(e.target.value) || 1)}
              />
            </div>
          </div>

          <div className="filter-group range-group">
            <label className="filter-label">IV Range (%)</label>
            <div className="range-inputs">
              <input
                type="number"
                className="filter-input"
                placeholder="Min"
                value={filters.ivMin}
                min={0}
                step={1}
                onChange={(e) => updateFilter('ivMin', parseFloat(e.target.value) || 0)}
              />
              <span className="range-separator">-</span>
              <input
                type="number"
                className="filter-input"
                placeholder="Max"
                value={filters.ivMax}
                min={0}
                step={1}
                onChange={(e) => updateFilter('ivMax', parseFloat(e.target.value) || 200)}
              />
            </div>
          </div>

          <div className="filter-group range-group">
            <label className="filter-label">Premium Range</label>
            <div className="range-inputs">
              <input
                type="number"
                className="filter-input"
                placeholder="Min"
                value={filters.premiumMin}
                min={0}
                step={1}
                onChange={(e) => updateFilter('premiumMin', parseFloat(e.target.value) || 0)}
              />
              <span className="range-separator">-</span>
              <input
                type="number"
                className="filter-input"
                placeholder="Max"
                value={filters.premiumMax}
                min={0}
                step={1}
                onChange={(e) => updateFilter('premiumMax', parseFloat(e.target.value) || 99999)}
              />
            </div>
          </div>
        </div>

        {/* Row 3: Min filters + Sort */}
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label">Min OI</label>
            <input
              type="number"
              className="filter-input single"
              placeholder="0"
              value={filters.oiMin}
              min={0}
              step={1000}
              onChange={(e) => updateFilter('oiMin', parseInt(e.target.value) || 0)}
            />
          </div>

          <div className="filter-group">
            <label className="filter-label">Min Volume</label>
            <input
              type="number"
              className="filter-input single"
              placeholder="0"
              value={filters.volumeMin}
              min={0}
              step={100}
              onChange={(e) => updateFilter('volumeMin', parseInt(e.target.value) || 0)}
            />
          </div>

          <div className="filter-group">
            <label className="filter-label">Sort By</label>
            <select
              className="filter-select"
              value={filters.sortBy}
              onChange={(e) => updateFilter('sortBy', e.target.value)}
            >
              <option value="oi">Open Interest</option>
              <option value="volume">Volume</option>
              <option value="iv">Implied Volatility</option>
              <option value="delta">Delta</option>
              <option value="premium">Premium</option>
            </select>
          </div>

          <div className="filter-group">
            <label className="filter-label">Direction</label>
            <button
              className="sort-toggle-btn"
              onClick={() => updateFilter('sortAscending', !filters.sortAscending)}
            >
              {filters.sortAscending ? 'Ascending' : 'Descending'}
              <span className="sort-arrow">{filters.sortAscending ? ' \u2191' : ' \u2193'}</span>
            </button>
          </div>

          <div className="filter-group">
            <label className="filter-label">&nbsp;</label>
            <button className="reset-filters-btn" onClick={resetFilters}>
              Reset Filters
            </button>
          </div>
        </div>
      </div>

      {/* Results count */}
      <div className="screener-results-info">
        <span className="results-count">
          {results.length} option{results.length !== 1 ? 's' : ''} found
        </span>
        {dataSource && (
          <span className={`data-source-badge ${dataSource.includes('live') || dataSource.includes('upstox') ? 'live' : 'cached'}`}>
            {dataSource.includes('live') || dataSource.includes('upstox') ? 'LIVE' : 'CACHED'}
          </span>
        )}
      </div>

      {/* Loading State */}
      {loading && (
        <div className="chain-loading">
          <div className="loading-spinner-chain"></div>
          <span>Scanning options...</span>
        </div>
      )}

      {/* Error State */}
      {error && !loading && (
        <div className="chain-error">
          <span className="error-icon">!</span>
          <span>{error}</span>
          <button onClick={() => fetchOptionChain(selectedIndex, expiries[selectedExpiry]?.value)}>
            Retry
          </button>
        </div>
      )}

      {/* Results Table */}
      {!loading && !error && (
        <div className="screener-results">
          <table className="screener-table">
            <thead>
              <tr>
                <th>Strike</th>
                <th>Type</th>
                <th>LTP</th>
                <th>IV%</th>
                <th>Delta</th>
                <th>Gamma</th>
                <th>Theta</th>
                <th>Vega</th>
                <th>OI</th>
                <th>Volume</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {results.length === 0 ? (
                <tr>
                  <td colSpan="11" className="screener-empty">
                    No options match your filters. Try adjusting the criteria.
                  </td>
                </tr>
              ) : (
                results.map((row, idx) => (
                  <tr
                    key={`${row.strikePrice}-${row.optionType}-${idx}`}
                    className={`screener-row ${row.optionType === 'CE' ? 'call-row' : 'put-row'}`}
                  >
                    <td className="strike-col">{row.strikePrice}</td>
                    <td className={`type-col ${row.optionType === 'CE' ? 'call-type' : 'put-type'}`}>
                      {row.optionType}
                    </td>
                    <td className="ltp-col">{formatPrice(row.ltp)}</td>
                    <td className="iv-col">{(row.iv * 100).toFixed(1)}%</td>
                    <td className="delta-col">{row.delta.toFixed(3)}</td>
                    <td className="gamma-col">{row.gamma.toFixed(4)}</td>
                    <td className="theta-col">{row.theta.toFixed(2)}</td>
                    <td className="vega-col">{row.vega.toFixed(2)}</td>
                    <td className="oi-col">{formatOI(row.oi)}</td>
                    <td className="volume-col">{formatOI(row.volume)}</td>
                    <td className="action-col">
                      <button
                        className="simulate-btn"
                        onClick={() => openSimulator(row)}
                        title="Open in P&L Simulator"
                      >
                        Simulate
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default OptionScreener
