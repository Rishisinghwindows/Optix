import { useState, useEffect, useCallback, useMemo } from 'react'
import marketAPI from '../../services/marketAPI'
import aiAnalysisService from '../../services/aiAnalysisService'

function AIInsights() {
  const [selectedIndex, setSelectedIndex] = useState('NIFTY')
  const [selectedExpiry, setSelectedExpiry] = useState(null)
  const [expiries, setExpiries] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [expandedSuggestion, setExpandedSuggestion] = useState(null)
  const [suggestions, setSuggestions] = useState([])
  const [marketContext, setMarketContext] = useState(null)
  const [optionChain, setOptionChain] = useState([])
  const [aiAnalysis, setAiAnalysis] = useState({})
  const [activeTab, setActiveTab] = useState('calls') // 'calls', 'puts', 'market', 'strategies'
  const [lastUpdated, setLastUpdated] = useState(new Date())
  const [error, setError] = useState(null)
  const [expandedWhyTrade, setExpandedWhyTrade] = useState({})
  const [strategies, setStrategies] = useState([])
  const [expandedStrategy, setExpandedStrategy] = useState(null)
  const [uaExpanded, setUAExpanded] = useState(false)

  // Unusual activity detection
  const unusualActivities = useMemo(() => {
    if (!optionChain?.length) return [];
    const activities = [];
    const avgVol = optionChain.reduce((s, r) => s + (r.CE?.totalTradedVolume || 0) + (r.PE?.totalTradedVolume || 0), 0) / (optionChain.length * 2);
    for (const row of optionChain) {
      for (const type of ['CE', 'PE']) {
        const opt = row[type];
        if (!opt) continue;
        const vol = opt.totalTradedVolume || 0;
        if (vol > avgVol * 4 && vol > 5000) {
          activities.push({
            strikePrice: row.strikePrice, optionType: type,
            description: `${(vol / avgVol).toFixed(1)}x avg volume`,
            displayValue: vol.toLocaleString()
          });
        }
      }
    }
    return activities.slice(0, 5);
  }, [optionChain]);

  // Fetch expiry dates
  useEffect(() => {
    const fetchExpiries = async () => {
      try {
        const data = await marketAPI.getExpiryDates(selectedIndex)
        // API returns expiryDates, not expiries
        const expiryList = data.expiryDates || data.expiries || []
        if (expiryList.length > 0) {
          setExpiries(expiryList)
          setSelectedExpiry(expiryList[0])
        }
      } catch (error) {
        setIsLoading(false)
      }
    }
    fetchExpiries()
  }, [selectedIndex])

  // Fetch market data and generate suggestions
  const fetchData = useCallback(async () => {
    if (!selectedExpiry) return

    setIsLoading(true)
    setError(null)
    try {
      // Fetch spot price first to get intraday change (pChange)
      let spotData = null
      let indiaVix = null
      try {
        spotData = await marketAPI.getSpotPrice(selectedIndex)
      } catch (e) {
        // Spot price fetch failed; continue with chain data
      }

      // Fetch India VIX for volatility context
      try {
        const vixData = await marketAPI.getSpotPrice('INDIAVIX')
        indiaVix = vixData?.price || vixData?.lastPrice || null
      } catch (e) {
        // VIX fetch failed; continue without VIX data
      }

      // Fetch option chain
      const chainData = await marketAPI.getOptionChain(selectedIndex, {
        expiry: selectedExpiry,
        strikes: 25,
      })

      if (chainData && chainData.data) {
        setOptionChain(chainData.data)

        // Calculate market context - use spot data for intraday change
        const spotPrice = spotData?.price || spotData?.lastPrice || chainData.underlyingValue
        const intradayChangeFromSpot = spotData?.pChange || spotData?.change || 0
        const pcr = parseFloat(marketAPI.calculatePCR(chainData)) || 1.0
        const maxPain = marketAPI.calculateMaxPain(chainData)
        const { support, resistance } = marketAPI.getSupportResistance(chainData)

        // Get intraday change (percentage change from previous close)
        // Use spot data which has accurate pChange, fallback to chain data
        const intradayChange = intradayChangeFromSpot ||
          chainData.pChange || chainData.change || chainData.percentChange || 0

        // Calculate days to expiry
        const expiryDate = new Date(selectedExpiry.split('-').reverse().join('-'))
        const today = new Date()
        const daysToExpiry = Math.ceil((expiryDate - today) / (1000 * 60 * 60 * 24))

        // Determine market bias based on momentum + PCR (matching iOS)
        let marketBias = 'Neutral'
        if (intradayChange > 0.5 && pcr > 1.0) marketBias = 'Strong Bullish'
        else if (intradayChange > 0.3 || pcr > 1.2) marketBias = 'Bullish'
        else if (intradayChange < -0.5 && pcr < 1.0) marketBias = 'Strong Bearish'
        else if (intradayChange < -0.3 || pcr < 0.8) marketBias = 'Bearish'

        const context = {
          spotPrice,
          pcr,
          maxPain,
          support,
          resistance,
          atmStrike: Math.round(spotPrice / 50) * 50,
          indexName: selectedIndex,
          daysToExpiry,
          marketBias,
          intradayChange,  // Pass intraday change to AI service
          pChange: intradayChange,  // Alias for compatibility
          indiaVix,  // VIX for volatility context
          vix: indiaVix,  // Alias
        }

        // Generate suggestions (may annotate context with no-trade reason)
        const generatedSuggestions = aiAnalysisService.generateTradeSuggestions(
          chainData.data,
          context
        )
        setMarketContext({ ...context })
        setSuggestions(generatedSuggestions)

        // Generate multi-leg strategy suggestions
        const generatedStrategies = aiAnalysisService.generateStrategySuggestions(
          chainData.data,
          context
        )
        setStrategies(generatedStrategies)
        setLastUpdated(new Date())
      } else {
        setError('No data available for this expiry')
      }
    } catch (err) {
      setError('Failed to fetch market data. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }, [selectedIndex, selectedExpiry])

  useEffect(() => {
    fetchData()
    // Auto-refresh every 30 seconds
    const interval = setInterval(fetchData, 30000)
    return () => clearInterval(interval)
  }, [fetchData])

  // Analyze suggestion with AI when expanded
  const analyzeSuggestion = async (suggestion) => {
    if (aiAnalysis[suggestion.strikePrice + suggestion.optionType]) return

    try {
      const analysis = await aiAnalysisService.analyzeTradeWithAI(
        suggestion,
        marketContext,
        optionChain
      )
      setAiAnalysis(prev => ({
        ...prev,
        [suggestion.strikePrice + suggestion.optionType]: analysis,
      }))
    } catch (error) {
      // Analysis failed silently
    }
  }

  const handleExpandSuggestion = (suggestion) => {
    const key = suggestion.strikePrice + suggestion.optionType
    if (expandedSuggestion === key) {
      setExpandedSuggestion(null)
    } else {
      setExpandedSuggestion(key)
      analyzeSuggestion(suggestion)
    }
  }

  const filteredSuggestions = suggestions.filter(s => {
    if (activeTab === 'calls') return s.optionType === 'CE'
    if (activeTab === 'puts') return s.optionType === 'PE'
    return true
  })

  const topPicks = filteredSuggestions.filter(s => s.tier === 'topPick')
  const worthWatching = filteredSuggestions.filter(s => s.tier === 'worthWatching')

  const getVerdictColor = (verdict) => {
    if (verdict === 'YES') return '#4ade80'
    if (verdict === 'NO') return '#f87171'
    return '#fbbf24'
  }

  const getVerdictIcon = (verdict) => {
    if (verdict === 'YES') return '✅'
    if (verdict === 'NO') return '❌'
    return '⏳'
  }

  const getMLSignalColor = (signal) => {
    if (signal === 'STRONG BUY' || signal === 'BUY') return '#4ade80'
    if (signal === 'STRONG SELL' || signal === 'SELL') return '#f87171'
    return '#fbbf24'
  }

  return (
    <div className="ai-insights-page">
      {/* Header */}
      <div className="ai-header">
        <div className="ai-title">
          <span className="ai-icon">🤖</span>
          <div>
            <h2>AI Trade Insights</h2>
            <p>Powered by ML & Option Chain Analysis</p>
          </div>
        </div>
        <button className="refresh-btn" onClick={fetchData} disabled={isLoading}>
          🔄 {isLoading ? 'Loading...' : 'Refresh'}
        </button>
      </div>

      {/* Index Selector */}
      <div className="ai-index-tabs">
        {['NIFTY', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY'].map(index => (
          <button
            key={index}
            className={`ai-tab ${selectedIndex === index ? 'active' : ''}`}
            onClick={() => setSelectedIndex(index)}
          >
            {index}
          </button>
        ))}
      </div>

      {/* Expiry Selector */}
      <div className="expiry-selector">
        {expiries.slice(0, 5).map((expiry) => {
          const expiryDate = new Date(expiry.split('-').reverse().join('-'))
          const today = new Date()
          const days = Math.ceil((expiryDate - today) / (1000 * 60 * 60 * 24))
          return (
            <button
              key={expiry}
              className={`expiry-btn ${selectedExpiry === expiry ? 'active' : ''}`}
              onClick={() => setSelectedExpiry(expiry)}
            >
              <span className="expiry-date">{expiry.slice(0, 6)}</span>
              <span className="expiry-days">{days}D</span>
            </button>
          )
        })}
      </div>

      {isLoading ? (
        <div className="ai-loading">
          <div className="loading-spinner"></div>
          <p>Analyzing market data...</p>
        </div>
      ) : error ? (
        <div className="ai-error">
          <p>⚠️ {error}</p>
          <button className="retry-btn" onClick={fetchData}>Retry</button>
        </div>
      ) : (
        <>
          {/* Market Overview */}
          {marketContext && (
            <div className="market-overview-card">
              <div className="market-header">
                <span className={`market-bias ${marketContext.marketBias.toLowerCase()}`}>
                  {marketContext.marketBias === 'Bullish' ? '↗' : marketContext.marketBias === 'Bearish' ? '↘' : '↔'} {marketContext.marketBias}
                </span>
                <span className="suggestions-count">{suggestions.length} Suggestions</span>
              </div>
              <div className="market-stats">
                <div className="stat">
                  <span className="stat-icon">₹</span>
                  <span className="stat-value">{marketContext.spotPrice?.toFixed(2)}</span>
                  <span className="stat-label">Spot</span>
                </div>
                <div className="stat">
                  <span className="stat-icon">📊</span>
                  <span className="stat-value">{marketContext.pcr?.toFixed(2)}</span>
                  <span className="stat-label">PCR</span>
                </div>
                <div className="stat">
                  <span className="stat-icon">🎯</span>
                  <span className="stat-value">{marketContext.maxPain}</span>
                  <span className="stat-label">Max Pain</span>
                </div>
                {marketContext.vix > 0 && (
                  <div className="stat">
                    <span className="stat-icon" style={{ color: marketContext.vix > 20 ? '#FF3B30' : marketContext.vix > 15 ? '#FF9500' : '#34C759' }}>⚡</span>
                    <span className="stat-value" style={{ color: marketContext.vix > 20 ? '#FF3B30' : marketContext.vix > 15 ? '#FF9500' : '#34C759' }}>{marketContext.vix.toFixed(1)}</span>
                    <span className="stat-label">India VIX</span>
                  </div>
                )}
                {marketContext.spotPrice > 0 && marketContext.atmIV > 0 && (
                  <div className="stat">
                    <span className="stat-icon">📐</span>
                    <span className="stat-value">
                      {Math.round(marketContext.spotPrice - marketContext.spotPrice * (marketContext.atmIV / 100) * Math.sqrt((marketContext.daysToExpiry ?? 7) / 365))}
                      {' – '}
                      {Math.round(marketContext.spotPrice + marketContext.spotPrice * (marketContext.atmIV / 100) * Math.sqrt((marketContext.daysToExpiry ?? 7) / 365))}
                    </span>
                    <span className="stat-label">Expected Range</span>
                  </div>
                )}
              </div>
              <div className="support-resistance">
                <div className="level support">
                  <span className="level-label">Support</span>
                  <span className="level-value">{marketContext.support || '-'}</span>
                </div>
                <div className="level resistance">
                  <span className="level-label">Resistance</span>
                  <span className="level-value">{marketContext.resistance || '-'}</span>
                </div>
              </div>
              {marketContext.noTradeReason && (
                <div className="ai-warning">
                  ⚠️ No-trade filter active: {marketContext.noTradeReason}
                </div>
              )}
            </div>
          )}

          {/* Tab Selector */}
          <div className="suggestion-tabs">
            <button
              className={`tab-btn ${activeTab === 'calls' ? 'active calls' : ''}`}
              onClick={() => setActiveTab('calls')}
            >
              ↑ Calls
            </button>
            <button
              className={`tab-btn ${activeTab === 'puts' ? 'active puts' : ''}`}
              onClick={() => setActiveTab('puts')}
            >
              ↓ Puts
            </button>
            <button
              className={`tab-btn ${activeTab === 'market' ? 'active' : ''}`}
              onClick={() => setActiveTab('market')}
            >
              📊 All
            </button>
            <button
              className={`tab-btn ${activeTab === 'strategies' ? 'active strategies' : ''}`}
              onClick={() => setActiveTab('strategies')}
            >
              🔀 Strategies {strategies.length > 0 && <span className="tab-count-badge">{strategies.length}</span>}
            </button>
          </div>

          {/* Market Regime Card */}
          {marketContext?.marketRegime && (() => {
            const regimeInfo = aiAnalysisService.getRegimeInfo(marketContext.marketRegime);
            return (
              <div className={`regime-card regime-${marketContext.marketRegime}`}>
                <span className="regime-icon">{regimeInfo.icon}</span>
                <div className="regime-text">
                  <span className="regime-label">{regimeInfo.label}</span>
                  <span className="regime-desc">{regimeInfo.description}</span>
                </div>
              </div>
            );
          })()}

          {/* Unusual Activity */}
          {unusualActivities.length > 0 && (
            <div className="unusual-activity-section">
              <div className="ua-header" onClick={() => setUAExpanded(!uaExpanded)}>
                <span>⚡ Unusual Activity</span>
                <span className="ua-count">{unusualActivities.length}</span>
                <span className="ua-toggle">{uaExpanded ? '▾' : '▸'}</span>
              </div>
              {uaExpanded && unusualActivities.map((ua, i) => (
                <div key={i} className="ua-row">
                  <span className="ua-strike">{ua.strikePrice} {ua.optionType}</span>
                  <span className="ua-detail">{ua.description}</span>
                  <span className="ua-value">{ua.displayValue}</span>
                </div>
              ))}
            </div>
          )}

          {/* Strategy Suggestions Tab */}
          {activeTab === 'strategies' && (
            <div className="strategies-list">
              {strategies.length === 0 ? (
                <div className="no-suggestions"><p>No strategy suggestions available.</p></div>
              ) : (
                strategies.map((strategy) => {
                  const isExpanded = expandedStrategy === strategy.id
                  return (
                    <div
                      key={strategy.id}
                      className={`strategy-card ${isExpanded ? 'expanded' : ''}`}
                      onClick={() => setExpandedStrategy(isExpanded ? null : strategy.id)}
                    >
                      {/* Strategy Header */}
                      <div className="strategy-header">
                        <div className="strategy-name-row">
                          <span className="strategy-icon">{strategy.icon}</span>
                          <span className="strategy-name">{strategy.name}</span>
                          <span className={`direction-badge direction-${strategy.direction.toLowerCase()}`}>
                            {strategy.direction}
                          </span>
                        </div>
                        <div className="strategy-score-gauge">
                          <svg viewBox="0 0 36 36" className="circular-chart">
                            <path className="circle-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                            <path className="circle" strokeDasharray={`${strategy.score || 0}, 100`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" style={{ stroke: (strategy.score || 0) >= 70 ? '#4ade80' : (strategy.score || 0) >= 50 ? '#fbbf24' : '#f87171' }} />
                            <text x="18" y="20.35" className="percentage">{Math.round(strategy.score || 0)}</text>
                          </svg>
                        </div>
                      </div>

                      {/* Leg Pills */}
                      <div className="strategy-legs-pills">
                        {strategy.legs.map((leg, i) => (
                          <span key={i} className="leg-pill">
                            <span className={`leg-action ${leg.action.toLowerCase()}`}>{leg.action === 'BUY' ? 'B' : 'S'}</span>
                            <span className="leg-strike">{leg.strike}{leg.type}</span>
                          </span>
                        ))}
                      </div>

                      {/* Quick Metrics */}
                      <div className="strategy-metrics">
                        <div className="strategy-metric">
                          <span className="metric-label">{strategy.isCredit ? 'Credit' : 'Debit'}</span>
                          <span className="metric-value">{strategy.formatNumber ? strategy.formatNumber(Math.abs(strategy.netPremium)) : '₹' + Math.abs(Math.round(strategy.netPremium))}</span>
                        </div>
                        <div className="strategy-metric">
                          <span className="metric-label">Max Loss</span>
                          <span className="metric-value loss">{strategy.maxLoss > 100000 ? '∞' : '₹' + Math.round(strategy.maxLoss)}</span>
                        </div>
                        <div className="strategy-metric">
                          <span className="metric-label">Max Profit</span>
                          <span className="metric-value profit">{strategy.maxProfit > 100000 ? '∞' : '₹' + Math.round(strategy.maxProfit)}</span>
                        </div>
                        <div className="strategy-metric">
                          <span className="metric-label">POP</span>
                          <span className="metric-value">{Math.round((strategy.pop || 0) * 100)}%</span>
                        </div>
                      </div>

                      {/* Expanded Detail */}
                      {isExpanded && (
                        <div className="strategy-detail" onClick={(e) => e.stopPropagation()}>
                          {/* Legs Table */}
                          <div className="strategy-legs-table">
                            <div className="strategy-legs-header">
                              <span>Action</span><span>Strike</span><span>Type</span><span>LTP</span><span>Premium</span>
                            </div>
                            {strategy.legs.map((leg, i) => (
                              <div key={i} className="strategy-leg-row">
                                <span className={`leg-action-badge ${leg.action.toLowerCase()}`}>{leg.action}</span>
                                <span className="leg-strike-val">{leg.strike}</span>
                                <span className="leg-type-val">{leg.type}</span>
                                <span className="leg-ltp-val">₹{(leg.ltp || 0).toFixed(1)}</span>
                                <span className="leg-premium-val">₹{Math.round(leg.premium)}</span>
                              </div>
                            ))}
                          </div>

                          {/* Breakevens */}
                          {strategy.breakevens && strategy.breakevens.length > 0 && (
                            <div className="strategy-breakevens">
                              <span className="be-label">Breakeven:</span>
                              <span className="be-values">{strategy.breakevens.map(b => Math.round(b)).join(' / ')}</span>
                            </div>
                          )}

                          {/* Net Greeks */}
                          <div className="strategy-net-greeks">
                            <h5>Net Greeks</h5>
                            <div className="greeks-grid">
                              <div className="greek-item"><span className="greek-label">Delta</span><span className="greek-val">{(strategy.netDelta || 0).toFixed(3)}</span></div>
                              <div className="greek-item"><span className="greek-label">Theta</span><span className="greek-val">{(strategy.netTheta || 0).toFixed(2)}</span></div>
                              <div className="greek-item"><span className="greek-label">Gamma</span><span className="greek-val">{(strategy.netGamma || 0).toFixed(4)}</span></div>
                              <div className="greek-item"><span className="greek-label">Vega</span><span className="greek-val">{(strategy.netVega || 0).toFixed(2)}</span></div>
                            </div>
                          </div>

                          {/* Score Breakdown */}
                          {strategy.scoreBreakdown && (
                            <div className="strategy-score-breakdown">
                              <h5>Score Breakdown</h5>
                              {Object.entries(strategy.scoreBreakdown).map(([key, val]) => (
                                <div key={key} className="score-factor-row">
                                  <span className="factor-name">{key === 'ivAlignment' ? 'IV Alignment' : key === 'pop' ? 'Probability' : key === 'riskReward' ? 'Risk/Reward' : key === 'liquidity' ? 'Liquidity' : 'Regime Fit'}</span>
                                  <div className="factor-bar-container">
                                    <div className="factor-bar-fill" style={{ width: `${val}%`, backgroundColor: val >= 70 ? '#4ade80' : val >= 40 ? '#fbbf24' : '#f87171' }}></div>
                                  </div>
                                  <span className="factor-score">{val}/100</span>
                                </div>
                              ))}
                            </div>
                          )}

                          {/* Reasoning */}
                          {strategy.reasoning && (
                            <div className="strategy-reasoning">
                              {strategy.reasoning.map((r, i) => (
                                <div key={i} className="reasoning-item">✓ {r}</div>
                              ))}
                            </div>
                          )}

                          {/* Market Condition */}
                          {strategy.marketCondition && (
                            <div className="strategy-condition">{strategy.marketCondition}</div>
                          )}
                        </div>
                      )}
                    </div>
                  )
                })
              )}
            </div>
          )}

          {/* Trade Suggestions - Two Tier Layout */}
          {activeTab !== 'strategies' && <div className="suggestions-list">
            {/* Top Picks Section */}
            {topPicks.length > 0 && (
              <div className="tier-section">
                <div className="tier-header top-picks">
                  <span className="tier-icon">⭐</span>
                  <span className="tier-title">Top Picks</span>
                  <span className="tier-count">{topPicks.length}</span>
                </div>
                {topPicks.map((suggestion) => {
                  const key = suggestion.strikePrice + suggestion.optionType
                  const isExpanded = expandedSuggestion === key
                  const analysis = aiAnalysis[key]
                  const isWhyExpanded = expandedWhyTrade[key]

                  return (
                    <div
                      key={key}
                      className={`suggestion-card top-pick ${isExpanded ? 'expanded' : ''}`}
                      onClick={() => handleExpandSuggestion(suggestion)}
                    >
                      {/* Tier Badge */}
                      <div className="card-tier-badge top-pick-badge">⭐ Top Pick</div>

                      {/* Card Header */}
                      <div className="suggestion-header">
                        <div className="strike-info">
                          <span className="strike-price">{suggestion.strikePrice}</span>
                          <span className={`option-type ${suggestion.optionType.toLowerCase()}`}>
                            {suggestion.optionType}
                          </span>
                        </div>

                        <div className="ml-badge" style={{ backgroundColor: getMLSignalColor(suggestion.mlPrediction?.signal) }}>
                          {suggestion.lowConfidence && suggestion.mlPrediction?.signal === 'STRONG BUY'
                            ? 'BUY'
                            : (suggestion.mlPrediction?.signal || 'HOLD')}
                          <span className="ml-prob">{Math.round((suggestion.mlPrediction?.probability || 0.5) * 100)}%</span>
                        </div>

                        <div className="score-gauge">
                          <svg viewBox="0 0 36 36" className="circular-chart">
                            <path className="circle-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                            <path className="circle" strokeDasharray={`${suggestion.score}, 100`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" style={{ stroke: suggestion.score >= 70 ? '#4ade80' : suggestion.score >= 50 ? '#fbbf24' : '#f87171' }} />
                            <text x="18" y="20.35" className="percentage">{Math.round(suggestion.score)}</text>
                          </svg>
                        </div>
                      </div>

                      {/* Badges Row - Confidence, Theta, OI Signal */}
                      <div className="badges-row">
                        {suggestion.confidence && (
                          <span className={`confidence-badge confidence-${suggestion.confidence.level.toLowerCase()}`}>
                            {suggestion.confidence.icon} {suggestion.confidence.level}
                          </span>
                        )}
                        {suggestion.thetaZone && (
                          <span className="theta-badge">
                            <span className="theta-dot" style={{ backgroundColor: suggestion.thetaZone.color }}></span>
                            Theta: {suggestion.thetaZone.zone}
                          </span>
                        )}
                        {suggestion.oiSignal && suggestion.oiSignal.signal !== 'N/A' && (
                          <span className="oi-signal-badge" style={{ borderColor: suggestion.oiSignal.color }}>
                            {suggestion.oiSignal.icon} {suggestion.oiSignal.signal}
                          </span>
                        )}
                        {suggestion.pop != null && (
                          <span className="pop-badge" style={{
                            borderColor: suggestion.pop >= 55 ? '#4ade80' : suggestion.pop >= 40 ? '#fbbf24' : '#f87171'
                          }}>
                            📊 POP {suggestion.pop}%
                          </span>
                        )}
                        {suggestion.termStructure && suggestion.termStructure !== 'contango' && (
                          <span className={`term-structure-badge ${suggestion.termStructure}`}>
                            {suggestion.termStructure === 'inverted' ? '⚠️' : '='} {suggestion.termStructure === 'inverted' ? 'Inverted' : 'Flat'}
                          </span>
                        )}
                      </div>

                      {/* Card Details */}
                      <div className="suggestion-details">
                        <div className="price-row">
                          <div className="price-item">
                            <span className="price-label">Entry</span>
                            <span className="price-value">₹{(parseFloat(suggestion.entryPrice) || parseFloat(suggestion.ltp) || 0).toFixed(2)}</span>
                          </div>
                          <div className="price-item target">
                            <span className="price-label">Target</span>
                            <span className="price-value">₹{(parseFloat(suggestion.targetPrice) || 0).toFixed(2)}</span>
                          </div>
                          <div className="price-item stoploss">
                            <span className="price-label">Stop Loss</span>
                            <span className="price-value">₹{(parseFloat(suggestion.stopLossPrice) || 0).toFixed(2)}</span>
                          </div>
                        </div>

                        <div className="metrics-row">
                          <span className="metric">R:R {(suggestion.riskReward || 1.6).toFixed(1)}:1</span>
                          <span className="metric profit">↗ +{Math.round((suggestion.targetPct || 0.6) * 100)}%</span>
                          <span className="metric loss">↘ -{Math.round((suggestion.stopLossPct || 0.38) * 100)}%</span>
                          <span className="metric">Intraday</span>
                        </div>

                        {/* Why This Trade? */}
                        {suggestion.scoreFactors && suggestion.scoreFactors.length > 0 && (
                          <div className="why-trade-section">
                            <button
                              className="why-trade-toggle"
                              onClick={(e) => {
                                e.stopPropagation()
                                setExpandedWhyTrade(prev => ({ ...prev, [key]: !prev[key] }))
                              }}
                            >
                              <span>💡 Why this trade?</span>
                              <span className={`why-trade-arrow ${isWhyExpanded ? 'expanded' : ''}`}>▸</span>
                            </button>
                            {isWhyExpanded && (
                              <div className="why-trade-content">
                                {suggestion.scoreFactors.map((factor, i) => (
                                  <div key={i} className="score-factor-row">
                                    <span className="factor-name">{factor.name}</span>
                                    <div className="factor-bar-container">
                                      <div
                                        className="factor-bar-fill"
                                        style={{
                                          width: `${factor.score}%`,
                                          backgroundColor: factor.score >= 70 ? '#4ade80' : factor.score >= 40 ? '#fbbf24' : '#f87171'
                                        }}
                                      ></div>
                                    </div>
                                    <span className="factor-score">{factor.score}/100</span>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        )}

                        {/* Ask AI Button */}
                        {!isExpanded && (
                          <button
                            className="ask-ai-btn"
                            onClick={(e) => {
                              e.stopPropagation()
                              handleExpandSuggestion(suggestion)
                            }}
                          >
                            🤖 Get Advanced AI Analysis
                          </button>
                        )}
                      </div>

                      {/* Expanded AI Analysis */}
                      {isExpanded && (
                        <div className="ai-analysis-section">
                          {!analysis ? (
                            <div className="analysis-loading">
                              <div className="mini-spinner"></div>
                              Analyzing with AI...
                            </div>
                          ) : (
                            <>
                              <div className="analysis-header">
                                <h4>✨ AI Analysis</h4>
                                <div className="verdict-badge" style={{ backgroundColor: getVerdictColor(analysis.verdict) }}>
                                  {getVerdictIcon(analysis.verdict)} {analysis.verdict}
                                </div>
                              </div>

                              <div className="win-probability">
                                <div className="prob-bar-container">
                                  <div className="prob-bar-fill" style={{ width: `${analysis.winProbability}%`, backgroundColor: analysis.winProbability >= 60 ? '#4ade80' : analysis.winProbability >= 40 ? '#fbbf24' : '#f87171' }}></div>
                                </div>
                                <span className="prob-text">{analysis.winProbability}% Win Probability</span>
                              </div>

                              <div className="analysis-content">
                                <div className="insight-item">
                                  <span className="insight-icon">💡</span>
                                  <div className="insight-text">
                                    <strong>Key Insight</strong>
                                    <p>{analysis.keyReason}</p>
                                  </div>
                                </div>
                                <div className="insight-item warning">
                                  <span className="insight-icon">⚠️</span>
                                  <div className="insight-text">
                                    <strong>Risk Warning</strong>
                                    <p>{analysis.riskWarning}</p>
                                  </div>
                                </div>
                                {analysis.betterAlternative && (
                                  <div className="insight-item alternative">
                                    <span className="insight-icon">💎</span>
                                    <div className="insight-text">
                                      <strong>Consider</strong>
                                      <p>{analysis.betterAlternative}</p>
                                    </div>
                                  </div>
                                )}
                              </div>

                              {analysis.chainAnalysis && (
                                <div className="chain-analysis">
                                  <h5>Option Chain Analysis</h5>
                                  <div className="chain-metrics">
                                    {analysis.chainAnalysis.ivSkew !== null && (
                                      <span className="chain-metric">IV Skew: {(analysis.chainAnalysis.ivSkew * 100).toFixed(1)}%</span>
                                    )}
                                    {analysis.chainAnalysis.support && (
                                      <span className="chain-metric support">Support: {analysis.chainAnalysis.support}</span>
                                    )}
                                    {analysis.chainAnalysis.resistance && (
                                      <span className="chain-metric resistance">Resistance: {analysis.chainAnalysis.resistance}</span>
                                    )}
                                    {analysis.chainAnalysis.smartMoneyBullish && (
                                      <span className="chain-metric bullish">Smart Money: Bullish</span>
                                    )}
                                    {analysis.chainAnalysis.smartMoneyBearish && (
                                      <span className="chain-metric bearish">Smart Money: Bearish</span>
                                    )}
                                  </div>
                                </div>
                              )}

                              <div className="analysis-source">Powered by {analysis.source}</div>
                              <button className="paper-trade-btn" onClick={(e) => { e.stopPropagation() }}>
                                📝 Paper Trade This
                              </button>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}

            {/* Worth Watching Section */}
            {worthWatching.length > 0 && (
              <div className="tier-section">
                <div className="tier-header worth-watching">
                  <span className="tier-icon">👁️</span>
                  <span className="tier-title">Worth Watching</span>
                  <span className="tier-count">{worthWatching.length}</span>
                </div>
                {worthWatching.map((suggestion) => {
                  const key = suggestion.strikePrice + suggestion.optionType
                  return (
                    <div
                      key={key}
                      className="compact-suggestion-card"
                      onClick={() => handleExpandSuggestion(suggestion)}
                    >
                      <div className="compact-left">
                        <span className="compact-strike">{suggestion.strikePrice}</span>
                        <span className={`compact-type ${suggestion.optionType.toLowerCase()}`}>{suggestion.optionType}</span>
                        {suggestion.confidence && (
                          <span className={`compact-confidence confidence-${suggestion.confidence.level.toLowerCase()}`}>
                            {suggestion.confidence.icon}
                          </span>
                        )}
                      </div>
                      <div className="compact-center">
                        <span className="compact-entry">₹{(parseFloat(suggestion.entryPrice) || parseFloat(suggestion.ltp) || 0).toFixed(2)}</span>
                        <span className="compact-rr">R:R {(suggestion.riskReward || 1.6).toFixed(1)}:1</span>
                      </div>
                      <div className="compact-right">
                        <div className="compact-score-gauge">
                          <svg viewBox="0 0 36 36" className="circular-chart">
                            <path className="circle-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                            <path className="circle" strokeDasharray={`${suggestion.score}, 100`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" style={{ stroke: suggestion.score >= 70 ? '#4ade80' : suggestion.score >= 50 ? '#fbbf24' : '#f87171' }} />
                            <text x="18" y="20.35" className="percentage">{Math.round(suggestion.score)}</text>
                          </svg>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}

            {/* No Suggestions */}
            {filteredSuggestions.length === 0 && (
              <div className="no-suggestions">
                <p>No {activeTab === 'calls' ? 'Call' : activeTab === 'puts' ? 'Put' : ''} suggestions available for this expiry.</p>
              </div>
            )}
          </div>}

          {/* Disclaimer */}
          <div className="ai-disclaimer">
            <p>
              <strong>Disclaimer:</strong> AI insights are for educational purposes only.
              Always do your own research before trading. Past performance doesn't guarantee future results.
            </p>
          </div>

          {/* Last Updated */}
          <div className="last-updated">
            Last updated: {lastUpdated.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}
          </div>
        </>
      )}
    </div>
  )
}

export default AIInsights
