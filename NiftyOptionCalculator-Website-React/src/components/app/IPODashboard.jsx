import React, { useState, useEffect, useCallback } from 'react'
import { ipoAPI } from '../../services/ipoAPI'
import './IPODashboard.css'

// Status filter options
const STATUS_FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'open', label: 'Open' },
  { value: 'upcoming', label: 'Upcoming' },
  { value: 'listed', label: 'Listed' },
  { value: 'closed', label: 'Closed' },
]

// Type filter options
const TYPE_FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'mainboard', label: 'Mainboard' },
  { value: 'sme', label: 'SME' },
]

// Demo IPO data for when API fails
const DEMO_IPOS = [
  {
    company_name: 'Hexaware Technologies',
    slug: 'hexaware-technologies',
    status: 'open',
    ipo_type: 'mainboard',
    open_date: '12 Feb 2026',
    close_date: '14 Feb 2026',
    listing_date: '19 Feb 2026',
    price_band_low: 674,
    price_band_high: 708,
    lot_size: 21,
    min_investment: 14868,
    exchange: 'NSE/BSE',
    gmp: { gmp_value: 85, listing_gain_pct: 12.0, estimated_listing_price: 793 },
    subscription: { total: 4.52, retail: 3.21, nii: 5.84, qib: 6.12 }
  },
  {
    company_name: 'Dr Agarwals Eye Hospital',
    slug: 'dr-agarwals-eye-hospital',
    status: 'upcoming',
    ipo_type: 'mainboard',
    open_date: '19 Feb 2026',
    close_date: '21 Feb 2026',
    price_band_low: 382,
    price_band_high: 402,
    lot_size: 37,
    min_investment: 14874,
    exchange: 'NSE/BSE',
    gmp: { gmp_value: 45, listing_gain_pct: 11.2, estimated_listing_price: 447 },
    subscription: null
  },
  {
    company_name: 'Ather Energy',
    slug: 'ather-energy',
    status: 'upcoming',
    ipo_type: 'mainboard',
    open_date: '25 Feb 2026',
    close_date: '27 Feb 2026',
    price_band_low: 304,
    price_band_high: 321,
    lot_size: 46,
    min_investment: 14766,
    exchange: 'NSE/BSE',
    gmp: { gmp_value: 65, listing_gain_pct: 20.2, estimated_listing_price: 386 },
    subscription: null
  },
  {
    company_name: 'Sai Life Sciences',
    slug: 'sai-life-sciences',
    status: 'listed',
    ipo_type: 'mainboard',
    open_date: '1 Feb 2026',
    close_date: '3 Feb 2026',
    listing_date: '7 Feb 2026',
    price_band_low: 522,
    price_band_high: 549,
    lot_size: 27,
    min_investment: 14823,
    exchange: 'NSE/BSE',
    gmp: { gmp_value: 120, listing_gain_pct: 21.9, estimated_listing_price: 669 },
    subscription: { total: 62.18, retail: 14.72, nii: 98.45, qib: 112.56 }
  },
  {
    company_name: 'TechSolutions SME',
    slug: 'techsolutions-sme',
    status: 'open',
    ipo_type: 'sme',
    open_date: '10 Feb 2026',
    close_date: '13 Feb 2026',
    price_band_low: 85,
    price_band_high: 90,
    lot_size: 1600,
    min_investment: 144000,
    exchange: 'NSE SME',
    gmp: { gmp_value: 25, listing_gain_pct: 27.8, estimated_listing_price: 115 },
    subscription: { total: 2.85, retail: 1.92, nii: 3.45, qib: null }
  },
  {
    company_name: 'Mobikwik',
    slug: 'mobikwik',
    status: 'closed',
    ipo_type: 'mainboard',
    open_date: '3 Feb 2026',
    close_date: '5 Feb 2026',
    listing_date: '10 Feb 2026',
    price_band_low: 265,
    price_band_high: 279,
    lot_size: 53,
    min_investment: 14787,
    exchange: 'NSE/BSE',
    gmp: { gmp_value: 95, listing_gain_pct: 34.1, estimated_listing_price: 374 },
    subscription: { total: 119.35, retail: 52.41, nii: 208.43, qib: 156.78 }
  },
  {
    company_name: 'Quadrant Future Tek',
    slug: 'quadrant-future-tek',
    status: 'closed',
    ipo_type: 'mainboard',
    open_date: '4 Feb 2026',
    close_date: '6 Feb 2026',
    listing_date: '11 Feb 2026',
    price_band_low: 230,
    price_band_high: 243,
    lot_size: 61,
    min_investment: 14823,
    exchange: 'NSE/BSE',
    gmp: { gmp_value: 52, listing_gain_pct: 21.4, estimated_listing_price: 295 },
    subscription: { total: 186.72, retail: 68.23, nii: 312.56, qib: 245.89 }
  },
  {
    company_name: 'Capital Infra SME',
    slug: 'capital-infra-sme',
    status: 'closed',
    ipo_type: 'sme',
    open_date: '1 Feb 2026',
    close_date: '4 Feb 2026',
    price_band_low: 95,
    price_band_high: 100,
    lot_size: 1200,
    min_investment: 120000,
    exchange: 'BSE SME',
    gmp: { gmp_value: 18, listing_gain_pct: 18.0, estimated_listing_price: 118 },
    subscription: { total: 45.67, retail: 28.34, nii: 62.18, qib: null }
  },
]

// AI Sparkle Icon
const AIIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 3v2M12 19v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M3 12h2M19 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/>
    <circle cx="12" cy="12" r="4"/>
  </svg>
)

// Magic Wand Icon for Get Analysis
const MagicIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M15 4V2M15 16v-2M8 9h2M20 9h2M17.8 11.8L19 13M17.8 6.2L19 5M3 21l9-9M12.2 6.2L11 5"/>
  </svg>
)

// Brain Icon for Animation
const BrainIcon = () => (
  <svg className="ai-brain-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M12 2C8.5 2 5.5 4.5 5.5 8c0 1.5.5 3 1.5 4-1 .5-2 1.5-2 3 0 2 1.5 3.5 3.5 3.5.5 0 1-.1 1.5-.3.5 1.3 1.5 2.3 3 2.8V22"/>
    <path d="M12 2c3.5 0 6.5 2.5 6.5 6 0 1.5-.5 3-1.5 4 1 .5 2 1.5 2 3 0 2-1.5 3.5-3.5 3.5-.5 0-1-.1-1.5-.3-.5 1.3-1.5 2.3-3 2.8"/>
    <circle cx="8" cy="8" r="1" fill="currentColor"/>
    <circle cx="16" cy="8" r="1" fill="currentColor"/>
    <circle cx="7" cy="14" r="1" fill="currentColor"/>
    <circle cx="17" cy="14" r="1" fill="currentColor"/>
    <path d="M9 11h6"/>
  </svg>
)

// Checkmark Icon
const CheckIcon = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
    <polyline points="20 6 9 17 4 12"/>
  </svg>
)

// Warning Icon
const WarningIcon = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
    <path d="M12 9v4M12 17h.01"/>
  </svg>
)

// Refresh Icon
const RefreshIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M23 4v6h-6M1 20v-6h6"/>
    <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
  </svg>
)

// Animated AI Analysis Loading Component
const AIAnalyzing = () => (
  <div className="ai-analyzing">
    <div className="ai-brain-animation">
      <BrainIcon />
      <div className="ai-brain-rings">
        <div className="ai-ring" />
        <div className="ai-ring" />
        <div className="ai-ring" />
      </div>
    </div>
    <span className="ai-analyzing-text">Analyzing IPO data...</span>
    <div className="ai-analyzing-subtext">
      <span>Evaluating fundamentals</span>
      <div className="ai-analyzing-dots">
        <span /><span /><span />
      </div>
    </div>
  </div>
)

// Animated AI Analysis Result Component
const AIAnalysisResult = ({ analysis, onRefresh }) => {
  if (!analysis) return null

  const verdictClass = analysis.verdict?.toLowerCase().includes('subscribe')
    ? 'subscribe'
    : analysis.verdict?.toLowerCase().includes('avoid')
    ? 'avoid'
    : 'neutral'

  const verdictIcon = verdictClass === 'subscribe' ? '✓'
    : verdictClass === 'avoid' ? '✗' : '~'

  return (
    <div className="ai-analysis-result">
      {/* Verdict Badge */}
      <div className="ai-verdict-container">
        <div className={`ai-verdict-badge-large ${verdictClass}`}>
          <span className="ai-verdict-icon">{verdictIcon}</span>
          <span>{analysis.verdict}</span>
        </div>
      </div>

      {/* Confidence Meter */}
      <div className="ai-confidence-section">
        <div className="ai-confidence-header">
          <span className="ai-confidence-label">AI Confidence</span>
          <span className="ai-confidence-value">{analysis.confidence}%</span>
        </div>
        <div className="ai-confidence-bar">
          <div
            className="ai-confidence-fill"
            style={{ '--confidence': `${analysis.confidence}%` }}
          />
        </div>
      </div>

      {/* Key Positives */}
      {analysis.key_positives?.length > 0 && (
        <div className="ai-key-points positives">
          <div className="ai-key-points-header positives">
            <CheckIcon /> Key Positives
          </div>
          <div className="ai-points-list">
            {analysis.key_positives.map((point, i) => (
              <div key={i} className="ai-point-item positive">
                <span className="ai-point-icon positive"><CheckIcon /></span>
                <span className="ai-point-text">{point}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Key Risks */}
      {analysis.key_risks?.length > 0 && (
        <div className="ai-key-points risks">
          <div className="ai-key-points-header risks">
            <WarningIcon /> Key Risks
          </div>
          <div className="ai-points-list">
            {analysis.key_risks.map((point, i) => (
              <div key={i} className="ai-point-item negative">
                <span className="ai-point-icon negative"><WarningIcon /></span>
                <span className="ai-point-text">{point}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Summary */}
      {analysis.recommendation_summary && (
        <div className="ai-summary">
          <div className="ai-summary-label">Summary</div>
          <div className="ai-summary-text">{analysis.recommendation_summary}</div>
        </div>
      )}

      {/* Disclaimer */}
      <div className="ai-disclaimer">
        {analysis.disclaimer || 'This analysis is AI-generated and should not be considered as financial advice. Please do your own research before investing.'}
      </div>

      {/* Refresh Button */}
      {onRefresh && (
        <button className="ai-refresh-btn" onClick={onRefresh}>
          <RefreshIcon /> Refresh Analysis
        </button>
      )}
    </div>
  )
}

// Subscription Indicator Component
function SubscriptionIndicator({ subscription, status }) {
  if (!subscription || status === 'upcoming') {
    return null
  }

  const formatSubs = (val) => {
    if (!val && val !== 0) return '-'
    return `${val.toFixed(2)}x`
  }

  const getSubsColor = (val) => {
    if (!val) return 'neutral'
    if (val >= 10) return 'high'
    if (val >= 3) return 'medium'
    return 'low'
  }

  return (
    <div className="ipo-subscription-section">
      <div className="ipo-subscription-header">
        <span className="ipo-subscription-label">Subscription</span>
        <span className={`ipo-subscription-total ${getSubsColor(subscription.total)}`}>
          {formatSubs(subscription.total)}
        </span>
      </div>
      <div className="ipo-subscription-breakdown">
        <div className="subs-item">
          <span className="subs-category">Retail</span>
          <span className={`subs-value ${getSubsColor(subscription.retail)}`}>
            {formatSubs(subscription.retail)}
          </span>
        </div>
        <div className="subs-item">
          <span className="subs-category">NII</span>
          <span className={`subs-value ${getSubsColor(subscription.nii)}`}>
            {formatSubs(subscription.nii)}
          </span>
        </div>
        {subscription.qib !== null && (
          <div className="subs-item">
            <span className="subs-category">QIB</span>
            <span className={`subs-value ${getSubsColor(subscription.qib)}`}>
              {formatSubs(subscription.qib)}
            </span>
          </div>
        )}
      </div>
    </div>
  )
}

// GMP Indicator Component
function GMPIndicator({ gmp, priceHigh }) {
  if (!gmp?.gmp_value && gmp?.gmp_value !== 0) {
    return <span className="ipo-gmp-value neutral">-</span>
  }

  const isPositive = gmp.gmp_value >= 0
  const gainPct = gmp.listing_gain_pct || (priceHigh ? (gmp.gmp_value / priceHigh) * 100 : 0)
  // Cap the bar width at 100%
  const barWidth = Math.min(Math.abs(gainPct), 50) * 2

  return (
    <div className="ipo-gmp-section">
      <div className="ipo-gmp-header">
        <span className="ipo-gmp-label">Grey Market Premium</span>
        <span className={`ipo-gmp-value ${isPositive ? 'positive' : 'negative'}`}>
          {isPositive ? '+' : ''}{ipoAPI.formatCurrency(gmp.gmp_value)}
        </span>
      </div>
      {gmp.listing_gain_pct !== undefined && (
        <span className={`ipo-listing-gain ${isPositive ? 'positive' : 'negative'}`}>
          Expected Gain: {ipoAPI.formatPercent(gmp.listing_gain_pct)}
        </span>
      )}
      <div className="gmp-indicator">
        <div
          className={`gmp-indicator-fill ${isPositive ? 'positive' : 'negative'}`}
          style={{ width: `${barWidth}%` }}
        />
      </div>
    </div>
  )
}

// AI Verdict Badge
function AIVerdictBadge({ verdict }) {
  if (!verdict) return null

  const verdictClass = verdict.toLowerCase().includes('subscribe')
    ? 'subscribe'
    : verdict.toLowerCase().includes('avoid')
    ? 'avoid'
    : 'neutral'

  return (
    <span className={`ai-verdict-badge ${verdictClass}`}>
      {verdict}
    </span>
  )
}

// AI Analysis Modal Component
function AIAnalysisModal({ ipo, analysis, analyzing, onClose, onAnalyze }) {
  if (!ipo) return null

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

  return (
    <div className="ipo-modal-overlay" onClick={handleOverlayClick}>
      <div className="ipo-modal ai-analysis-modal">
        <div className="ipo-modal-header">
          <div>
            <h2>AI Analysis</h2>
            <span className="ai-modal-company">{ipo.company_name}</span>
          </div>
          <button className="ipo-modal-close" onClick={onClose}>
            &times;
          </button>
        </div>

        <div className="ipo-modal-body">
          {/* Show analyzing animation */}
          {analyzing && <AIAnalyzing />}

          {/* Show analysis result */}
          {analysis && !analyzing && (
            <AIAnalysisResult
              analysis={analysis}
              onRefresh={() => onAnalyze(ipo)}
            />
          )}

          {/* Initial loading state */}
          {!analysis && !analyzing && (
            <div className="ai-modal-loading">
              <p>Loading analysis...</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// IPO Card Component
function IPOCard({ ipo, onSelect, onAnalyze, analyzing, analysis, onShowAnalysis }) {
  const handleAnalyze = (e) => {
    e.stopPropagation()
    onShowAnalysis(ipo)
    if (!analysis) {
      onAnalyze(ipo)
    }
  }

  return (
    <div className="ipo-card" onClick={() => onSelect(ipo)}>
      <div className="ipo-card-header">
        <div>
          <h3 className="ipo-company-name">{ipo.company_name}</h3>
          <div className="ipo-badges">
            <span className={`ipo-badge status-${ipo.status}`}>
              {ipo.status}
            </span>
            <span className={`ipo-badge type-${ipo.ipo_type}`}>
              {ipo.ipo_type}
            </span>
          </div>
        </div>
      </div>

      <div className="ipo-card-details">
        {ipo.price_band_high && (
          <div className="ipo-detail-item">
            <span className="ipo-detail-label">Price Band</span>
            <span className="ipo-detail-value">
              {ipo.price_band_low && ipo.price_band_low !== ipo.price_band_high
                ? `${ipoAPI.formatCurrency(ipo.price_band_low)} - ${ipoAPI.formatCurrency(ipo.price_band_high)}`
                : ipoAPI.formatCurrency(ipo.price_band_high)}
            </span>
          </div>
        )}
        {ipo.lot_size && (
          <div className="ipo-detail-item">
            <span className="ipo-detail-label">Lot Size</span>
            <span className="ipo-detail-value">{ipo.lot_size} shares</span>
          </div>
        )}
        {ipo.min_investment && (
          <div className="ipo-detail-item">
            <span className="ipo-detail-label">Min Investment</span>
            <span className="ipo-detail-value">{ipoAPI.formatCurrency(ipo.min_investment)}</span>
          </div>
        )}
        {ipo.open_date && (
          <div className="ipo-detail-item">
            <span className="ipo-detail-label">Open Date</span>
            <span className="ipo-detail-value">{ipo.open_date}</span>
          </div>
        )}
      </div>

      <GMPIndicator gmp={ipo.gmp} priceHigh={ipo.price_band_high} />

      <SubscriptionIndicator subscription={ipo.subscription} status={ipo.status} />

      {/* AI Analysis Button */}
      <div className="ipo-ai-section">
        <button
          className={`ipo-ai-btn ${analysis ? 'has-analysis' : ''}`}
          onClick={handleAnalyze}
          disabled={analyzing}
        >
          {analysis ? <AIIcon /> : <MagicIcon />}
          {analyzing ? 'Analyzing...' : (analysis ? 'View Analysis' : 'Get AI Analysis')}
        </button>
        {analysis && <AIVerdictBadge verdict={analysis.verdict} />}
      </div>
    </div>
  )
}

// IPO Detail Modal
function IPODetailModal({ ipo, analysis, onClose, onAnalyze, analyzing }) {
  if (!ipo) return null

  const handleOverlayClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

  const handleRefreshAnalysis = () => {
    onAnalyze(ipo)
  }

  return (
    <div className="ipo-modal-overlay" onClick={handleOverlayClick}>
      <div className="ipo-modal">
        <div className="ipo-modal-header">
          <div>
            <h2>{ipo.company_name}</h2>
            <div className="ipo-badges">
              <span className={`ipo-badge status-${ipo.status}`}>
                {ipo.status}
              </span>
              <span className={`ipo-badge type-${ipo.ipo_type}`}>
                {ipo.ipo_type}
              </span>
              {ipo.exchange && (
                <span className="ipo-badge type-mainboard">{ipo.exchange}</span>
              )}
            </div>
          </div>
          <button className="ipo-modal-close" onClick={onClose}>
            &times;
          </button>
        </div>

        <div className="ipo-modal-body">
          {/* Price Info */}
          <div className="ipo-modal-section">
            <h3>Pricing</h3>
            <div className="ipo-modal-grid">
              <div className="ipo-modal-item">
                <span className="label">Price Band</span>
                <span className="value">
                  {ipo.price_band_low && ipo.price_band_low !== ipo.price_band_high
                    ? `${ipoAPI.formatCurrency(ipo.price_band_low)} - ${ipoAPI.formatCurrency(ipo.price_band_high)}`
                    : ipoAPI.formatCurrency(ipo.price_band_high) || '-'}
                </span>
              </div>
              <div className="ipo-modal-item">
                <span className="label">Lot Size</span>
                <span className="value">{ipo.lot_size ? `${ipo.lot_size} shares` : '-'}</span>
              </div>
              <div className="ipo-modal-item">
                <span className="label">Min Investment</span>
                <span className="value">{ipoAPI.formatCurrency(ipo.min_investment) || '-'}</span>
              </div>
              <div className="ipo-modal-item">
                <span className="label">Issue Size</span>
                <span className="value">{ipo.issue_size_cr ? `Rs ${ipo.issue_size_cr} Cr` : '-'}</span>
              </div>
            </div>
          </div>

          {/* Dates */}
          <div className="ipo-modal-section">
            <h3>Timeline</h3>
            <div className="ipo-modal-grid">
              <div className="ipo-modal-item">
                <span className="label">Open Date</span>
                <span className="value">{ipo.open_date || '-'}</span>
              </div>
              <div className="ipo-modal-item">
                <span className="label">Close Date</span>
                <span className="value">{ipo.close_date || '-'}</span>
              </div>
              <div className="ipo-modal-item">
                <span className="label">Listing Date</span>
                <span className="value">{ipo.listing_date || '-'}</span>
              </div>
            </div>
          </div>

          {/* GMP Section */}
          <div className="ipo-modal-section">
            <h3>Grey Market Premium</h3>
            <GMPIndicator gmp={ipo.gmp} priceHigh={ipo.price_band_high} />
            {ipo.gmp?.estimated_listing_price && (
              <div className="ipo-modal-item" style={{ marginTop: '0.75rem' }}>
                <span className="label">Estimated Listing Price</span>
                <span className="value">{ipoAPI.formatCurrency(ipo.gmp.estimated_listing_price)}</span>
              </div>
            )}
          </div>

          {/* AI Analysis Section */}
          <div className="ipo-modal-section">
            <h3>AI Analysis</h3>

            {/* Show analyzing animation */}
            {analyzing && <AIAnalyzing />}

            {/* Show analysis result with animations */}
            {analysis && !analyzing && (
              <AIAnalysisResult
                analysis={analysis}
                onRefresh={handleRefreshAnalysis}
              />
            )}

            {/* Show get analysis button if no analysis */}
            {!analysis && !analyzing && (
              <button
                className="ipo-ai-btn"
                onClick={() => onAnalyze(ipo)}
              >
                <AIIcon />
                Get AI Analysis
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

// Loading Skeleton
function LoadingSkeleton() {
  return (
    <div className="ipo-grid">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="ipo-skeleton">
          <div className="skeleton-line title" />
          <div>
            <span className="skeleton-line badge" />
            <span className="skeleton-line badge" />
          </div>
          <div className="skeleton-line detail" />
          <div className="skeleton-line detail" />
          <div className="skeleton-line gmp" />
        </div>
      ))}
    </div>
  )
}

// Pagination Component
function Pagination({ currentPage, totalPages, onPageChange }) {
  if (totalPages <= 1) return null

  const pages = []
  const maxVisible = 5
  let startPage = Math.max(1, currentPage - Math.floor(maxVisible / 2))
  let endPage = Math.min(totalPages, startPage + maxVisible - 1)

  if (endPage - startPage + 1 < maxVisible) {
    startPage = Math.max(1, endPage - maxVisible + 1)
  }

  for (let i = startPage; i <= endPage; i++) {
    pages.push(i)
  }

  return (
    <div className="ipo-pagination">
      <button
        className="pagination-btn"
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 1}
      >
        &laquo; Prev
      </button>

      {startPage > 1 && (
        <>
          <button className="pagination-btn" onClick={() => onPageChange(1)}>1</button>
          {startPage > 2 && <span className="pagination-dots">...</span>}
        </>
      )}

      {pages.map(page => (
        <button
          key={page}
          className={`pagination-btn ${currentPage === page ? 'active' : ''}`}
          onClick={() => onPageChange(page)}
        >
          {page}
        </button>
      ))}

      {endPage < totalPages && (
        <>
          {endPage < totalPages - 1 && <span className="pagination-dots">...</span>}
          <button className="pagination-btn" onClick={() => onPageChange(totalPages)}>{totalPages}</button>
        </>
      )}

      <button
        className="pagination-btn"
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages}
      >
        Next &raquo;
      </button>
    </div>
  )
}

// Search Icon
const SearchIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="11" cy="11" r="8"/>
    <path d="m21 21-4.35-4.35"/>
  </svg>
)

// Main IPO Dashboard Component
function IPODashboard() {
  const [ipoList, setIpoList] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [lastUpdated, setLastUpdated] = useState(null)

  const [statusFilter, setStatusFilter] = useState('open')
  const [typeFilter, setTypeFilter] = useState('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const ITEMS_PER_PAGE = 6

  const [selectedIPO, setSelectedIPO] = useState(null)
  const [aiAnalysis, setAiAnalysis] = useState({}) // { slug: analysisData }
  const [analyzingIPO, setAnalyzingIPO] = useState(null)
  const [analysisModalIPO, setAnalysisModalIPO] = useState(null) // IPO for AI analysis modal

  // Filter demo data based on current filters
  const filterDemoData = useCallback((data) => {
    let filtered = [...data]
    if (statusFilter && statusFilter !== 'all') {
      filtered = filtered.filter(ipo => ipo.status === statusFilter)
    }
    if (typeFilter && typeFilter !== 'all') {
      filtered = filtered.filter(ipo => ipo.ipo_type === typeFilter)
    }
    return filtered
  }, [statusFilter, typeFilter])

  // Fetch IPO data
  const fetchIPOs = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const data = await ipoAPI.getIPOList({
        status: statusFilter,
        ipo_type: typeFilter,
      })
      if (data.ipos && data.ipos.length > 0) {
        setIpoList(data.ipos)
        setLastUpdated(data.last_updated)
      } else {
        // Use demo data if API returns empty
        setIpoList(filterDemoData(DEMO_IPOS))
        setLastUpdated(new Date().toISOString())
      }
    } catch (err) {
      console.error('IPO API error:', err)
      // Use demo data on error
      setIpoList(filterDemoData(DEMO_IPOS))
      setLastUpdated(new Date().toISOString())
    } finally {
      setLoading(false)
    }
  }, [statusFilter, typeFilter, filterDemoData])

  useEffect(() => {
    fetchIPOs()
  }, [fetchIPOs])

  // Handle AI analysis
  const handleAnalyze = async (ipo) => {
    if (analyzingIPO) return

    setAnalyzingIPO(ipo.slug)

    try {
      const analysis = await ipoAPI.analyzeIPO(ipo.company_name)
      setAiAnalysis((prev) => ({
        ...prev,
        [ipo.slug]: analysis,
      }))
    } catch (err) {
      console.error('AI analysis error:', err)
      setAiAnalysis((prev) => ({
        ...prev,
        [ipo.slug]: {
          verdict: 'Error',
          confidence: 0,
          analysis: `Failed to get AI analysis: ${err.message}`,
          key_positives: [],
          key_risks: [],
          recommendation_summary: 'Analysis unavailable',
          disclaimer: 'AI service error',
        },
      }))
    } finally {
      setAnalyzingIPO(null)
    }
  }

  // Format last updated time
  const formatLastUpdated = (timestamp) => {
    if (!timestamp) return null
    try {
      const date = new Date(timestamp)
      return date.toLocaleString('en-IN', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return null
    }
  }

  // Filter by search query
  const filteredList = ipoList.filter(ipo => {
    if (!searchQuery.trim()) return true
    const query = searchQuery.toLowerCase()
    return (
      ipo.company_name?.toLowerCase().includes(query) ||
      ipo.exchange?.toLowerCase().includes(query)
    )
  })

  // Pagination calculations
  const totalPages = Math.ceil(filteredList.length / ITEMS_PER_PAGE)
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE
  const paginatedList = filteredList.slice(startIndex, startIndex + ITEMS_PER_PAGE)

  // Reset to page 1 when filters or search change
  useEffect(() => {
    setCurrentPage(1)
  }, [statusFilter, typeFilter, searchQuery])

  return (
    <div className="ipo-dashboard">
      {/* Header */}
      <div className="ipo-header">
        <div>
          <h1>IPO Dashboard</h1>
          {lastUpdated && (
            <span className="ipo-last-updated">
              Last updated: {formatLastUpdated(lastUpdated)}
            </span>
          )}
        </div>
      </div>

      {/* Search Bar */}
      <div className="ipo-search-bar">
        <div className="search-input-wrapper">
          <SearchIcon />
          <input
            type="text"
            className="search-input"
            placeholder="Search IPO by company name..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button className="search-clear" onClick={() => setSearchQuery('')}>
              &times;
            </button>
          )}
        </div>
        {!loading && (
          <span className="ipo-count">
            Showing {paginatedList.length} of {filteredList.length} IPOs
          </span>
        )}
      </div>

      {/* Filter Bar */}
      <div className="ipo-filter-bar">
        <div className="filter-group">
          {STATUS_FILTERS.map((filter) => (
            <button
              key={filter.value}
              className={`filter-pill ${statusFilter === filter.value ? 'active' : ''}`}
              onClick={() => setStatusFilter(filter.value)}
            >
              {filter.label}
            </button>
          ))}
        </div>
        <div className="filter-group">
          {TYPE_FILTERS.map((filter) => (
            <button
              key={filter.value}
              className={`filter-pill ${typeFilter === filter.value ? 'active' : ''}`}
              onClick={() => setTypeFilter(filter.value)}
            >
              {filter.label}
            </button>
          ))}
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="ipo-error">
          <p>{error}</p>
          <button className="ipo-error-btn" onClick={fetchIPOs}>
            Try Again
          </button>
        </div>
      )}

      {/* Loading State */}
      {loading && <LoadingSkeleton />}

      {/* IPO Grid */}
      {!loading && !error && paginatedList.length > 0 && (
        <>
          <div className="ipo-grid">
            {paginatedList.map((ipo) => (
              <IPOCard
                key={ipo.slug}
                ipo={ipo}
                onSelect={setSelectedIPO}
                onAnalyze={handleAnalyze}
                analyzing={analyzingIPO === ipo.slug}
                analysis={aiAnalysis[ipo.slug]}
                onShowAnalysis={setAnalysisModalIPO}
              />
            ))}
          </div>

          {/* Pagination */}
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            onPageChange={setCurrentPage}
          />
        </>
      )}

      {/* Empty State */}
      {!loading && !error && filteredList.length === 0 && (
        <div className="ipo-empty">
          <div className="ipo-empty-icon">📋</div>
          <h3>No IPOs Found</h3>
          <p>
            {searchQuery
              ? `No IPOs match "${searchQuery}". Try a different search term.`
              : 'No IPOs match your current filters. Try adjusting the filters above.'}
          </p>
        </div>
      )}

      {/* Detail Modal */}
      {selectedIPO && (
        <IPODetailModal
          ipo={selectedIPO}
          analysis={aiAnalysis[selectedIPO.slug]}
          onClose={() => setSelectedIPO(null)}
          onAnalyze={handleAnalyze}
          analyzing={analyzingIPO === selectedIPO.slug}
        />
      )}

      {/* AI Analysis Modal */}
      {analysisModalIPO && (
        <AIAnalysisModal
          ipo={analysisModalIPO}
          analysis={aiAnalysis[analysisModalIPO.slug]}
          analyzing={analyzingIPO === analysisModalIPO.slug}
          onClose={() => setAnalysisModalIPO(null)}
          onAnalyze={handleAnalyze}
        />
      )}
    </div>
  )
}

export default IPODashboard
