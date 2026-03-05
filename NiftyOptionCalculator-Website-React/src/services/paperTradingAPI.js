/**
 * Paper Trading API Service
 * Handles paper trading operations with backend
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

class PaperTradingService {
  constructor() {
    this.baseURL = `${API_BASE_URL}/api/v1/paper-trading`;
  }

  /**
   * Get auth token
   */
  getToken() {
    return localStorage.getItem('access_token');
  }

  /**
   * Generic fetch with auth
   */
  async fetch(endpoint, options = {}) {
    const url = `${this.baseURL}${endpoint}`;
    const token = this.getToken();

    // If no token, return null silently (user not logged in)
    if (!token) {
      return null;
    }

    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    headers['Authorization'] = `Bearer ${token}`;

    try {
      const response = await fetch(url, {
        ...options,
        headers,
      });

      // Handle 401 Unauthorized - token expired or invalid
      if (response.status === 401) {
        console.log('Paper Trading: Token expired, clearing auth');
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user');
        return null;
      }

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(data.detail || `HTTP ${response.status}`);
      }

      return data;
    } catch (error) {
      console.error(`Paper Trading API Error [${endpoint}]:`, error);
      throw error;
    }
  }

  /**
   * Get portfolio summary
   */
  async getPortfolio() {
    const data = await this.fetch('/portfolio');
    return data || {
      cash_balance: 1000000,
      invested_amount: 0,
      current_value: 1000000,
      total_pnl: 0,
      total_pnl_percent: 0,
      open_positions_count: 0,
      total_trades: 0,
    };
  }

  /**
   * Get open positions
   */
  async getPositions() {
    const data = await this.fetch('/positions');
    return data || [];
  }

  /**
   * Get trade history
   */
  async getTradeHistory(limit = 50) {
    return this.fetch(`/trades?limit=${limit}`);
  }

  /**
   * Create a new trade
   */
  async createTrade(trade) {
    return this.fetch('/trades', {
      method: 'POST',
      body: JSON.stringify(trade),
    });
  }

  /**
   * Square off a position
   */
  async squareOff(positionId, exitPrice) {
    return this.fetch(`/positions/${positionId}/square-off`, {
      method: 'POST',
      body: JSON.stringify({ exit_price: exitPrice }),
    });
  }

  /**
   * Reset portfolio to initial balance
   */
  async resetPortfolio() {
    return this.fetch('/portfolio/reset', {
      method: 'POST',
    });
  }

  /**
   * Get performance metrics
   */
  async getPerformance() {
    return this.fetch('/performance');
  }
}

export const paperTradingAPI = new PaperTradingService();
export default paperTradingAPI;
