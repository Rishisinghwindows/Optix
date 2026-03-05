/**
 * IPO Dashboard API Service
 * Fetches IPO data with Grey Market Premium and AI analysis
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

class IPOAPIService {
  constructor() {
    this.baseURL = `${API_BASE_URL}/api/v1/ipo`;
    this.cache = new Map();
    // Cache TTLs (ms)
    this.cacheTTLs = {
      list: 300000,     // 5 minutes
      detail: 300000,   // 5 minutes
      analysis: 600000, // 10 minutes
      gmp: 300000,      // 5 minutes
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
      console.error(`IPO API Error [${endpoint}]:`, error);
      throw error;
    }
  }

  /**
   * Get from cache or fetch
   */
  async cachedFetch(key, fetchFn, ttlMs = 0) {
    const cached = this.cache.get(key);
    if (cached && ttlMs > 0 && Date.now() - cached.timestamp < ttlMs) {
      return cached.data;
    }

    const data = await fetchFn();
    this.cache.set(key, { data, timestamp: Date.now() });
    return data;
  }

  /**
   * Get list of IPOs with GMP data
   * @param {Object} filters - Filter options
   * @param {string} filters.status - Filter by status (upcoming, open, closed, listed)
   * @param {string} filters.ipo_type - Filter by type (mainboard, sme)
   */
  async getIPOList(filters = {}) {
    const params = new URLSearchParams();
    if (filters.status && filters.status !== 'all') {
      params.append('status', filters.status);
    }
    if (filters.ipo_type && filters.ipo_type !== 'all') {
      params.append('ipo_type', filters.ipo_type);
    }

    const queryString = params.toString();
    const endpoint = `/list${queryString ? `?${queryString}` : ''}`;
    const cacheKey = `list_${filters.status || 'all'}_${filters.ipo_type || 'all'}`;

    return this.cachedFetch(
      cacheKey,
      () => this.fetch(endpoint),
      this.cacheTTLs.list
    );
  }

  /**
   * Get detailed information for a specific IPO
   * @param {string} slug - IPO slug
   */
  async getIPODetail(slug) {
    return this.cachedFetch(
      `detail_${slug}`,
      () => this.fetch(`/detail/${slug}`),
      this.cacheTTLs.detail
    );
  }

  /**
   * Get AI analysis for an IPO
   * @param {string} companyName - Company name to analyze
   */
  async analyzeIPO(companyName) {
    const cacheKey = `analysis_${companyName.toLowerCase().replace(/\s+/g, '_')}`;

    return this.cachedFetch(
      cacheKey,
      () => this.fetch('/analyze', {
        method: 'POST',
        body: JSON.stringify({ company_name: companyName }),
      }),
      this.cacheTTLs.analysis
    );
  }

  /**
   * Get GMP-only data for all active IPOs
   */
  async getGMPData() {
    return this.cachedFetch(
      'gmp',
      () => this.fetch('/gmp'),
      this.cacheTTLs.gmp
    );
  }

  /**
   * Force refresh IPO data
   */
  async refreshData() {
    // Clear all cache
    this.clearCache();
    return this.fetch('/refresh', { method: 'POST' });
  }

  /**
   * Clear local cache
   */
  clearCache() {
    this.cache.clear();
  }

  /**
   * Format currency in Indian notation
   */
  formatCurrency(value) {
    if (!value && value !== 0) return '-';
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value);
  }

  /**
   * Format percentage
   */
  formatPercent(value) {
    if (!value && value !== 0) return '-';
    const sign = value > 0 ? '+' : '';
    return `${sign}${value.toFixed(1)}%`;
  }
}

// Export singleton instance
export const ipoAPI = new IPOAPIService();
export default ipoAPI;
