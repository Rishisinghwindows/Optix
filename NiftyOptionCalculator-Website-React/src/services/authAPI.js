/**
 * Authentication API Service
 * Handles user authentication, OTP, and session management
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

class AuthAPIService {
  constructor() {
    this.baseURL = `${API_BASE_URL}/api/v1/auth`;
  }

  /**
   * Get stored access token
   */
  getAccessToken() {
    return localStorage.getItem('access_token');
  }

  /**
   * Get stored refresh token
   */
  getRefreshToken() {
    return localStorage.getItem('refresh_token');
  }

  /**
   * Store tokens
   */
  storeTokens(accessToken, refreshToken) {
    localStorage.setItem('access_token', accessToken);
    if (refreshToken) {
      localStorage.setItem('refresh_token', refreshToken);
    }
  }

  /**
   * Clear tokens
   */
  clearTokens() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user');
  }

  /**
   * Store user data
   */
  storeUser(user) {
    localStorage.setItem('user', JSON.stringify(user));
  }

  /**
   * Get stored user
   */
  getStoredUser() {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  }

  /**
   * Generic fetch with error handling
   */
  async fetch(endpoint, options = {}) {
    const url = `${this.baseURL}${endpoint}`;

    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    // Add authorization header if token exists
    const token = this.getAccessToken();
    if (token && !options.skipAuth) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    try {
      const response = await fetch(url, {
        ...options,
        headers,
      });

      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(data.detail || `HTTP ${response.status}`);
      }

      return data;
    } catch (error) {
      console.error(`Auth API Error [${endpoint}]:`, error);
      throw error;
    }
  }

  /**
   * Send OTP to phone number
   */
  async sendOTP(phone, purpose = 'login') {
    return this.fetch('/otp/send', {
      method: 'POST',
      body: JSON.stringify({ phone, purpose }),
      skipAuth: true,
    });
  }

  /**
   * Verify OTP and login
   */
  async verifyOTP(phone, otp) {
    const deviceInfo = {
      device_name: navigator.userAgent.includes('Mobile') ? 'Mobile Browser' : 'Desktop Browser',
      os: navigator.platform,
      app_version: '1.0.0',
    };

    const response = await this.fetch('/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ phone, otp, device_info: deviceInfo }),
      skipAuth: true,
    });

    // Store tokens and user
    this.storeTokens(response.access_token, response.refresh_token);
    this.storeUser(response.user);

    return response;
  }

  /**
   * Google OAuth login
   */
  async loginWithGoogle(idToken) {
    const deviceInfo = {
      device_name: navigator.userAgent.includes('Mobile') ? 'Mobile Browser' : 'Desktop Browser',
      os: navigator.platform,
      app_version: '1.0.0',
    };

    const response = await this.fetch('/social/google', {
      method: 'POST',
      body: JSON.stringify({ id_token: idToken, device_info: deviceInfo }),
      skipAuth: true,
    });

    this.storeTokens(response.access_token, response.refresh_token);
    this.storeUser(response.user);

    return response;
  }

  /**
   * Facebook OAuth login
   */
  async loginWithFacebook(accessToken) {
    const deviceInfo = {
      device_name: navigator.userAgent.includes('Mobile') ? 'Mobile Browser' : 'Desktop Browser',
      os: navigator.platform,
      app_version: '1.0.0',
    };

    const response = await this.fetch('/social/facebook', {
      method: 'POST',
      body: JSON.stringify({ access_token: accessToken, device_info: deviceInfo }),
      skipAuth: true,
    });

    this.storeTokens(response.access_token, response.refresh_token);
    this.storeUser(response.user);

    return response;
  }

  /**
   * Refresh access token
   */
  async refreshToken() {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }

    const response = await this.fetch('/refresh', {
      method: 'POST',
      body: JSON.stringify({ refresh_token: refreshToken }),
      skipAuth: true,
    });

    this.storeTokens(response.access_token, null);
    return response;
  }

  /**
   * Logout current session
   */
  async logout() {
    try {
      await this.fetch('/logout', { method: 'POST' });
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      this.clearTokens();
    }
  }

  /**
   * Logout from all devices
   */
  async logoutAll() {
    try {
      await this.fetch('/logout-all', { method: 'POST' });
    } catch (error) {
      console.error('Logout all error:', error);
    } finally {
      this.clearTokens();
    }
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated() {
    return !!this.getAccessToken();
  }

  /**
   * Get current user profile
   */
  async getCurrentUser() {
    return this.fetch('/me', {
      method: 'GET',
    }).catch(() => {
      // If auth fails, try to refresh
      return this.refreshToken().then(() => this.fetch('/me'));
    });
  }
}

export const authAPI = new AuthAPIService();
export default authAPI;
