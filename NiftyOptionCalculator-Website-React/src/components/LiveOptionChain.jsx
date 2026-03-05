import React, { useState, useEffect, useCallback } from 'react';
import { marketAPI } from '../services/marketAPI';
import './LiveOptionChain.css';

const INDICES = [
  { symbol: 'NIFTY', name: 'NIFTY 50', color: '#22c55e' },
  { symbol: 'BANKNIFTY', name: 'BANK NIFTY', color: '#3b82f6' },
  { symbol: 'FINNIFTY', name: 'FIN NIFTY', color: '#8b5cf6' },
  { symbol: 'MIDCPNIFTY', name: 'MIDCAP NIFTY', color: '#f59e0b' },
];

export default function LiveOptionChain() {
  const [selectedIndex, setSelectedIndex] = useState('NIFTY');
  const [selectedExpiry, setSelectedExpiry] = useState(null);
  const [optionChain, setOptionChain] = useState(null);
  const [expiryDates, setExpiryDates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [marketStatus, setMarketStatus] = useState(null);

  // Derived values
  const pcr = optionChain ? marketAPI.calculatePCR(optionChain) : null;
  const maxPain = optionChain ? marketAPI.calculateMaxPain(optionChain) : null;
  const { support, resistance } = optionChain
    ? marketAPI.getSupportResistance(optionChain)
    : { support: null, resistance: null };

  // Fetch market status
  useEffect(() => {
    marketAPI.getMarketStatus()
      .then(setMarketStatus)
      .catch(console.error);
  }, []);

  // Fetch expiry dates when index changes
  useEffect(() => {
    marketAPI.getExpiryDates(selectedIndex)
      .then(data => {
        setExpiryDates(data.expiryDates || []);
        if (data.expiryDates?.length > 0) {
          setSelectedExpiry(data.expiryDates[0]);
        }
      })
      .catch(err => {
        console.error('Error fetching expiries:', err);
        setExpiryDates([]);
      });
  }, [selectedIndex]);

  // Fetch option chain
  const fetchOptionChain = useCallback(async () => {
    if (!selectedExpiry) return;

    setLoading(true);
    setError(null);

    try {
      const data = await marketAPI.getOptionChain(selectedIndex, {
        expiry: selectedExpiry,
        strikes: 12,
      });
      setOptionChain(data);
      setLastUpdated(new Date());
    } catch (err) {
      setError(err.message || 'Failed to fetch data');
    } finally {
      setLoading(false);
    }
  }, [selectedIndex, selectedExpiry]);

  // Fetch on mount and when index/expiry changes
  useEffect(() => {
    fetchOptionChain();
  }, [fetchOptionChain]);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    const interval = setInterval(fetchOptionChain, 30000);
    return () => clearInterval(interval);
  }, [fetchOptionChain]);

  // Format numbers
  const formatOI = (oi) => {
    if (!oi) return '-';
    if (oi >= 100000) return `${(oi / 100000).toFixed(1)}L`;
    if (oi >= 1000) return `${(oi / 1000).toFixed(1)}K`;
    return oi.toString();
  };

  const formatPrice = (price) => {
    if (!price && price !== 0) return '-';
    return price.toFixed(2);
  };

  // Get days to expiry
  const getDaysToExpiry = (expiryStr) => {
    const expiry = new Date(expiryStr.replace(/-/g, ' '));
    const today = new Date();
    const diff = Math.ceil((expiry - today) / (1000 * 60 * 60 * 24));
    return Math.max(0, diff);
  };

  // Check if strike is ATM
  const isATM = (strike) => {
    if (!optionChain) return false;
    return Math.abs(strike - optionChain.atmStrike) < 10;
  };

  // Check if strike is ITM for calls/puts
  const isCallITM = (strike) => optionChain && optionChain.underlyingValue > strike;
  const isPutITM = (strike) => optionChain && optionChain.underlyingValue < strike;

  return (
    <section className="live-option-chain" id="live-data">
      <div className="container">
        <div className="section-header">
          <h2>Live Option Chain</h2>
          <p>Real-time NSE data - No login required</p>

          {/* Market Status Badge */}
          <div className={`market-status ${marketStatus?.isOpen ? 'open' : 'closed'}`}>
            <span className="status-dot"></span>
            <span>{marketStatus?.message || 'Loading...'}</span>
          </div>
        </div>

        {/* Index Selector */}
        <div className="index-selector">
          {INDICES.map(index => (
            <button
              key={index.symbol}
              className={`index-btn ${selectedIndex === index.symbol ? 'active' : ''}`}
              onClick={() => setSelectedIndex(index.symbol)}
              style={{ '--accent': index.color }}
            >
              {index.name}
            </button>
          ))}
        </div>

        {/* Spot Price & Stats */}
        {optionChain && (
          <div className="spot-info">
            <div className="spot-price">
              <span className="label">{optionChain.name}</span>
              <span className="price">{formatPrice(optionChain.underlyingValue)}</span>
            </div>

            <div className="quick-stats">
              <div className="stat">
                <span className="label">PCR</span>
                <span className={`value ${pcr > 1 ? 'bullish' : 'bearish'}`}>{pcr || '-'}</span>
              </div>
              <div className="stat">
                <span className="label">Max Pain</span>
                <span className="value">{maxPain || '-'}</span>
              </div>
              <div className="stat">
                <span className="label">Support</span>
                <span className="value bullish">{support || '-'}</span>
              </div>
              <div className="stat">
                <span className="label">Resistance</span>
                <span className="value bearish">{resistance || '-'}</span>
              </div>
              <div className="stat">
                <span className="label">Lot Size</span>
                <span className="value">{optionChain.lotSize}</span>
              </div>
            </div>
          </div>
        )}

        {/* Expiry Selector */}
        <div className="expiry-selector">
          {expiryDates.slice(0, 4).map(expiry => (
            <button
              key={expiry}
              className={`expiry-btn ${selectedExpiry === expiry ? 'active' : ''}`}
              onClick={() => setSelectedExpiry(expiry)}
            >
              <span className="date">{expiry.split('-').slice(0, 2).join(' ')}</span>
              <span className="days">{getDaysToExpiry(expiry)}D</span>
            </button>
          ))}
        </div>

        {/* Error Message */}
        {error && (
          <div className="error-message">
            <span>⚠️ {error}</span>
            <button onClick={fetchOptionChain}>Retry</button>
          </div>
        )}

        {/* Loading State */}
        {loading && !optionChain && (
          <div className="loading-state">
            <div className="spinner"></div>
            <span>Loading option chain...</span>
          </div>
        )}

        {/* Option Chain Table */}
        {optionChain && (
          <div className="option-chain-wrapper">
            <table className="option-chain-table">
              <thead>
                <tr>
                  <th colSpan="4" className="call-header">CALLS</th>
                  <th className="strike-header">STRIKE</th>
                  <th colSpan="4" className="put-header">PUTS</th>
                </tr>
                <tr>
                  <th>OI</th>
                  <th>Chg OI</th>
                  <th>IV</th>
                  <th>LTP</th>
                  <th></th>
                  <th>LTP</th>
                  <th>IV</th>
                  <th>Chg OI</th>
                  <th>OI</th>
                </tr>
              </thead>
              <tbody>
                {optionChain.data.map((row, idx) => (
                  <tr
                    key={idx}
                    className={`
                      ${isATM(row.strikePrice) ? 'atm-row' : ''}
                      ${isCallITM(row.strikePrice) ? 'call-itm' : ''}
                      ${isPutITM(row.strikePrice) ? 'put-itm' : ''}
                    `}
                  >
                    {/* Call Side */}
                    <td className="oi call">{formatOI(row.CE?.openInterest)}</td>
                    <td className={`chg-oi ${(row.CE?.changeinOpenInterest || 0) >= 0 ? 'positive' : 'negative'}`}>
                      {formatOI(row.CE?.changeinOpenInterest)}
                    </td>
                    <td className="iv">{row.CE?.impliedVolatility?.toFixed(1) || '-'}</td>
                    <td className="ltp call">{formatPrice(row.CE?.lastPrice)}</td>

                    {/* Strike */}
                    <td className="strike">
                      {row.strikePrice}
                      {isATM(row.strikePrice) && <span className="atm-badge">ATM</span>}
                    </td>

                    {/* Put Side */}
                    <td className="ltp put">{formatPrice(row.PE?.lastPrice)}</td>
                    <td className="iv">{row.PE?.impliedVolatility?.toFixed(1) || '-'}</td>
                    <td className={`chg-oi ${(row.PE?.changeinOpenInterest || 0) >= 0 ? 'positive' : 'negative'}`}>
                      {formatOI(row.PE?.changeinOpenInterest)}
                    </td>
                    <td className="oi put">{formatOI(row.PE?.openInterest)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Footer */}
        <div className="chain-footer">
          <span className="last-updated">
            {lastUpdated && `Last updated: ${lastUpdated.toLocaleTimeString()}`}
          </span>
          <span className="data-source">
            {optionChain?.isDemo ? '📊 Demo Data' : '📡 Live NSE Data'}
          </span>
          <button className="refresh-btn" onClick={fetchOptionChain} disabled={loading}>
            {loading ? 'Refreshing...' : '🔄 Refresh'}
          </button>
        </div>

        {/* Disclaimer */}
        <p className="disclaimer">
          Data refreshes automatically every 30 seconds. For educational purposes only.
          Not financial advice.
        </p>
      </div>
    </section>
  );
}
