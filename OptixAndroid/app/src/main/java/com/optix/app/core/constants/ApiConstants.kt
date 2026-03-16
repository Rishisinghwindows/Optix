package com.optix.app.core.constants

object ApiConstants {
    // API Endpoints
    // Production server (same as iOS)
    const val BASE_URL = "https://api.optix.d23ai.in/"
    const val UPSTOX_BASE_URL = "https://api.upstox.com/v2/"

    // Google Sign-In (Get from Firebase Console → Authentication → Sign-in method → Google)
    const val GOOGLE_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    // Auth endpoints
    const val AUTH_SEND_OTP = "api/v1/auth/otp/send"
    const val AUTH_VERIFY_OTP = "api/v1/auth/otp/verify"
    const val AUTH_GOOGLE = "api/v1/auth/social/google"
    const val AUTH_REFRESH = "api/v1/auth/refresh"
    const val AUTH_LOGOUT = "api/v1/auth/logout"

    // User endpoints
    const val USER_ME = "api/v1/user/me"

    // Market endpoints
    const val MARKET_OPTION_CHAIN = "api/v1/market/option-chain/{symbol}"
    const val MARKET_EXPIRY_DATES = "api/v1/market/expiry-dates/{symbol}"
    const val MARKET_SPOT_PRICE = "api/v1/market/spot-price/{symbol}"

    // Paper trading endpoints
    const val PAPER_POSITIONS = "api/v1/paper-trading/positions"
    const val PAPER_TRADE = "api/v1/paper-trading/trade"
    const val PAPER_CLOSE = "api/v1/paper-trading/close/{positionId}"
    const val PAPER_HISTORY = "api/v1/paper-trading/history"
    const val PAPER_PERFORMANCE = "api/v1/paper-trading/performance"

    // Alert endpoints
    const val ALERTS = "api/v1/alerts"
    const val ALERT_BY_ID = "api/v1/alerts/{alertId}"
    const val ALERT_STATS = "api/v1/alerts/stats"
    const val NOTIFICATIONS = "api/v1/alerts/notifications/list"
    const val MARK_READ = "api/v1/alerts/notifications/mark-read"
    const val MARK_ALL_READ = "api/v1/alerts/notifications/mark-all-read"

    // AI endpoints
    const val AI_ANALYZE = "api/v1/options-ai/analyze"
    const val AI_INSIGHTS = "api/v1/options-ai/insights"

    // IPO endpoints
    const val IPO_LIST = "api/v1/ipo/list"
    const val IPO_DETAIL = "api/v1/ipo/{id}"

    // Chat endpoints
    const val CHAT_SEND = "api/v1/chatbot/send"
    const val CHAT_SESSIONS = "api/v1/chatbot/sessions"

    // WebSocket
    const val WEBSOCKET_URL = "wss://api.optix.d23ai.in/ws/market"

    // Timeouts (in seconds)
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // Cache durations (in milliseconds)
    const val OPTION_CHAIN_CACHE_DURATION = 5 * 60 * 1000L  // 5 minutes
    const val EXPIRY_CACHE_DURATION = 60 * 60 * 1000L      // 1 hour
    const val IPO_CACHE_DURATION = 30 * 60 * 1000L          // 30 minutes
}

object AppConstants {
    // Default values
    const val DEFAULT_RISK_FREE_RATE = 0.065  // 6.5% RBI repo rate
    const val DEFAULT_PAPER_BALANCE = 1000000.0  // 10 Lakh

    // Calculation constants
    const val TRADING_DAYS_PER_YEAR = 252
    const val MARKET_OPEN_HOUR = 9
    const val MARKET_OPEN_MINUTE = 15
    const val MARKET_CLOSE_HOUR = 15
    const val MARKET_CLOSE_MINUTE = 30

    // UI constants
    const val DEBOUNCE_DELAY = 300L
    const val ANIMATION_DURATION = 300
    const val SHIMMER_DURATION = 1000

    // Pagination
    const val DEFAULT_PAGE_SIZE = 50

    // Limits
    const val MAX_ALERTS = 20
    const val MAX_STRATEGY_LEGS = 4
}
