package com.optix.app.domain.model

/**
 * Supported trading indices for options trading
 */
enum class TradingIndex(
    val displayName: String,
    val symbol: String,
    val apiSymbol: String,
    val lotSize: Int,
    val tickSize: Double
) {
    NIFTY50(
        displayName = "NIFTY 50",
        symbol = "NIFTY",
        apiSymbol = "nifty",
        lotSize = 75,
        tickSize = 0.05
    ),
    BANKNIFTY(
        displayName = "Bank Nifty",
        symbol = "BANKNIFTY",
        apiSymbol = "banknifty",
        lotSize = 30,
        tickSize = 0.05
    ),
    FINNIFTY(
        displayName = "Fin Nifty",
        symbol = "FINNIFTY",
        apiSymbol = "finnifty",
        lotSize = 25,
        tickSize = 0.05
    ),
    MIDCPNIFTY(
        displayName = "Midcap Nifty",
        symbol = "MIDCPNIFTY",
        apiSymbol = "midcpnifty",
        lotSize = 50,
        tickSize = 0.05
    ),
    SENSEX(
        displayName = "Sensex",
        symbol = "SENSEX",
        apiSymbol = "sensex",
        lotSize = 10,
        tickSize = 0.05
    ),
    BANKEX(
        displayName = "Bankex",
        symbol = "BANKEX",
        apiSymbol = "bankex",
        lotSize = 15,
        tickSize = 0.05
    );

    companion object {
        fun fromSymbol(symbol: String): TradingIndex? {
            return entries.find { it.symbol.equals(symbol, ignoreCase = true) }
        }
    }
}
