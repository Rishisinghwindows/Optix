/**
 * Market Data API Service
 * Fetches live NSE option chain data from the backend
 * No authentication required - works for all users including guests
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

class MarketAPIService {
  constructor() {
    this.baseURL = `${API_BASE_URL}/api/v1/market`;
    this.cache = new Map();
    this.pendingFetches = new Map(); // Track in-flight requests
    // Cache TTLs (ms)
    this.cacheTTLs = {
      indices: 30000,
      spot: 1000,
      expiry: 300000,
      chain: 2000,
    };
    // Stale TTLs - how long to show cached data before considering it stale (ms)
    this.staleTTLs = {
      indices: 60000,      // 1 minute
      spot: 30000,         // 30 seconds
      expiry: 600000,      // 10 minutes
      chain: 60000,        // 1 minute
    };
  }

  /**
   * Generic fetch with error handling
   */
  async fetch(endpoint, options = {}) {
    const url = `${this.baseURL}${endpoint}`;

    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          ...options.headers,
        },
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({ detail: 'Network error' }));
        throw new Error(error.detail || `HTTP ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error(`Market API Error [${endpoint}]:`, error);
      throw error;
    }
  }

  /**
   * Get from cache or fetch with stale-while-revalidate pattern
   * @param {string} key - Cache key
   * @param {Function} fetchFn - Function to fetch data
   * @param {number} ttlMs - Cache TTL in ms (data is fresh within this time)
   * @param {number} staleTtlMs - Stale TTL in ms (data is usable but needs refresh)
   * @returns {Object} { data, fromCache, isStale }
   */
  async cachedFetch(key, fetchFn, ttlMs = 0, staleTtlMs = 0) {
    const cached = this.cache.get(key);
    const now = Date.now();

    // If data is fresh (within TTL), return it immediately
    if (cached && ttlMs > 0 && now - cached.timestamp < ttlMs) {
      return { ...cached.data, _fromCache: true, _isStale: false };
    }

    // If data is stale but usable (within stale TTL), return cached and refresh in background
    if (cached && staleTtlMs > 0 && now - cached.timestamp < staleTtlMs) {
      // Only start background fetch if not already in progress
      if (!this.pendingFetches.has(key)) {
        const fetchPromise = fetchFn()
          .then(data => {
            this.cache.set(key, { data, timestamp: Date.now() });
            this.pendingFetches.delete(key);
            return data;
          })
          .catch(err => {
            console.warn(`Background refresh failed for ${key}:`, err.message);
            this.pendingFetches.delete(key);
            throw err;
          });
        this.pendingFetches.set(key, fetchPromise);
      }

      // Return stale data immediately
      return { ...cached.data, _fromCache: true, _isStale: true };
    }

    // No usable cache - must fetch fresh data
    try {
      // Check if there's already a pending fetch for this key
      if (this.pendingFetches.has(key)) {
        const data = await this.pendingFetches.get(key);
        return { ...data, _fromCache: false, _isStale: false };
      }

      const fetchPromise = fetchFn();
      this.pendingFetches.set(key, fetchPromise);

      const data = await fetchPromise;
      this.cache.set(key, { data, timestamp: Date.now() });
      this.pendingFetches.delete(key);

      return { ...data, _fromCache: false, _isStale: false };
    } catch (error) {
      this.pendingFetches.delete(key);

      // If fetch fails but we have any cached data, return it as fallback
      if (cached) {
        console.warn(`Fetch failed, using cached data for ${key}`);
        return { ...cached.data, _fromCache: true, _isStale: true, _fallback: true };
      }

      throw error;
    }
  }

  /**
   * Get list of supported indices
   */
  async getIndices() {
    return this.cachedFetch(
      'indices',
      () => this.fetch('/indices'),
      this.cacheTTLs.indices,
      this.staleTTLs.indices
    );
  }

  /**
   * Get market status (open/closed)
   */
  async getMarketStatus() {
    return this.fetch('/status');
  }

  /**
   * Get spot price for an index
   * @param {string} symbol - NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, SENSEX
   */
  async getSpotPrice(symbol) {
    return this.cachedFetch(
      `spot_${symbol}`,
      () => this.fetch(`/spot/${symbol.toUpperCase()}`),
      this.cacheTTLs.spot,
      this.staleTTLs.spot
    );
  }

  /**
   * Get available expiry dates for an index
   * @param {string} symbol - Index symbol
   */
  async getExpiryDates(symbol) {
    return this.cachedFetch(
      `expiry_${symbol}`,
      () => this.fetch(`/expiries/${symbol.toUpperCase()}`),
      this.cacheTTLs.expiry,
      this.staleTTLs.expiry
    );
  }

  /**
   * Get option chain data
   * @param {string} symbol - Index symbol
   * @param {Object} options - Optional filters
   * @param {string} options.expiry - Filter by expiry date (e.g., "30-Jan-2025")
   * @param {number} options.strikes - Number of strikes around ATM (default: 10)
   * @param {boolean} options.demo - Use demo data (for testing)
   */
  async getOptionChain(symbol, options = {}) {
    const { expiry, strikes = 15, demo = false } = options;

    const params = new URLSearchParams();
    if (expiry) params.append('expiry', expiry);
    if (strikes) params.append('strikes', strikes);
    if (demo) params.append('demo', 'true');

    const queryString = params.toString();
    const endpoint = `/option-chain/${symbol.toUpperCase()}${queryString ? `?${queryString}` : ''}`;

    // Don't cache if demo mode
    if (demo) {
      return this.fetch(endpoint);
    }

    return this.cachedFetch(
      `chain_${symbol}_${expiry || 'all'}_${strikes}`,
      () => this.fetch(endpoint),
      this.cacheTTLs.chain,
      this.staleTTLs.chain
    );
  }

  /**
   * Calculate option Greeks using Black-Scholes
   * @param {Object} params - Greeks calculation parameters
   */
  async calculateGreeks(params) {
    const { spotPrice, strikePrice, timeToExpiry, volatility, riskFreeRate = 6.5, optionType } = params;

    return this.fetch('/calculate-greeks', {
      method: 'POST',
      body: JSON.stringify({
        spotPrice,
        strikePrice,
        timeToExpiry,
        volatility,
        riskFreeRate,
        optionType,
      }),
    });
  }

  /**
   * Clear local cache
   */
  clearCache() {
    this.cache.clear();
  }

  /**
   * Get PCR (Put-Call Ratio) from option chain data
   */
  calculatePCR(optionChainData) {
    if (!optionChainData) return null;

    let putOI = 0;
    let callOI = 0;

    if (optionChainData.totals) {
      putOI = optionChainData.totals.PE?.totalOI || 0;
      callOI = optionChainData.totals.CE?.totalOI || 0;
    } else if (Array.isArray(optionChainData.data)) {
      for (const row of optionChainData.data) {
        const ce = row.CE || row.call || row.callOption;
        const pe = row.PE || row.put || row.putOption;
        callOI += ce?.openInterest || ce?.oi || 0;
        putOI += pe?.openInterest || pe?.oi || 0;
      }
    }

    if (callOI === 0) return null;
    return (putOI / callOI).toFixed(2);
  }

  /**
   * Get Max Pain strike from option chain data
   */
  calculateMaxPain(optionChainData) {
    if (!optionChainData?.data || optionChainData.data.length === 0) return null;

    const strikes = [...new Set(optionChainData.data.map(d => d.strikePrice))].sort((a, b) => a - b);
    let minPain = Infinity;
    let maxPainStrike = strikes[0];

    for (const strike of strikes) {
      let totalPain = 0;

      for (const row of optionChainData.data) {
        const ceOI = row.CE?.openInterest || 0;
        const peOI = row.PE?.openInterest || 0;

        // Call writers pain (if spot > strike)
        if (strike > row.strikePrice) {
          totalPain += ceOI * (strike - row.strikePrice);
        }

        // Put writers pain (if spot < strike)
        if (strike < row.strikePrice) {
          totalPain += peOI * (row.strikePrice - strike);
        }
      }

      if (totalPain < minPain) {
        minPain = totalPain;
        maxPainStrike = strike;
      }
    }

    return maxPainStrike;
  }

  /**
   * Get support and resistance levels from OI data
   */
  getSupportResistance(optionChainData) {
    if (!optionChainData?.data) return { support: null, resistance: null };

    const spotPrice = optionChainData.underlyingValue;
    let maxPutOI = 0, maxCallOI = 0;
    let support = null, resistance = null;

    for (const row of optionChainData.data) {
      // Support: Highest Put OI below spot
      if (row.strikePrice < spotPrice && row.PE?.openInterest > maxPutOI) {
        maxPutOI = row.PE.openInterest;
        support = row.strikePrice;
      }

      // Resistance: Highest Call OI above spot
      if (row.strikePrice > spotPrice && row.CE?.openInterest > maxCallOI) {
        maxCallOI = row.CE.openInterest;
        resistance = row.strikePrice;
      }
    }

    return { support, resistance };
  }
}

// Export singleton instance
export const marketAPI = new MarketAPIService();
export default marketAPI;
