package com.optix.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OptionChainResponse(
    val symbol: String,
    val underlyingValue: Double,
    val atmStrike: Double,
    val data: List<OptionChainRowDto>,
    val timestamp: String? = null,
    val name: String? = null,
    val lotSize: Int? = null,
    val expiryDates: List<String>? = null,
    val strikePrices: List<Double>? = null,
    val isLive: Boolean? = null,
    val fetchedAt: String? = null,
    val totals: ChainTotalsDto? = null
)

@Serializable
data class ChainTotalsDto(
    val CE: SideTotalsDto? = null,
    val PE: SideTotalsDto? = null
)

@Serializable
data class SideTotalsDto(
    val totalOI: Double? = null,
    val totalVolume: Double? = null
)

@Serializable
data class OptionChainRowDto(
    val strikePrice: Double,
    val expiryDate: String? = null,
    @SerialName("CE") val callData: OptionDataDto? = null,
    @SerialName("PE") val putData: OptionDataDto? = null
)

@Serializable
data class OptionDataDto(
    // Support both camelCase and snake_case for API compatibility
    @SerialName("openInterest") val openInterest: Double = 0.0,
    @SerialName("changeinOpenInterest") val changeinOpenInterest: Double = 0.0,
    @SerialName("totalTradedVolume") val totalTradedVolume: Long = 0,
    @SerialName("impliedVolatility") val impliedVolatility: Double = 0.0,
    @SerialName("lastPrice") val lastPrice: Double = 0.0,
    val change: Double = 0.0,
    @SerialName("pChange") val pChange: Double = 0.0,
    @SerialName("bidQty") val bidQty: Long = 0,
    val bidprice: Double = 0.0,
    @SerialName("askQty") val askQty: Long = 0,
    @SerialName("askPrice") val askPrice: Double = 0.0,
    @SerialName("instrumentKey") val instrumentKey: String = "",
    @SerialName("tradingSymbol") val tradingSymbol: String? = null,
    // Alternative field names that some APIs might use
    @SerialName("oi") val oi: Double = 0.0,
    @SerialName("open_interest") val openInterestAlt: Double = 0.0
) {
    // Get OI from whichever field has data
    val effectiveOpenInterest: Double
        get() = when {
            openInterest > 0 -> openInterest
            oi > 0 -> oi
            openInterestAlt > 0 -> openInterestAlt
            else -> 0.0
        }
}

@Serializable
data class GreeksDto(
    val delta: Double = 0.0,
    val gamma: Double = 0.0,
    val theta: Double = 0.0,
    val vega: Double = 0.0,
    val rho: Double = 0.0
)

@Serializable
data class ExpiryDatesResponse(
    val symbol: String,
    val expiryDates: List<String>
)

@Serializable
data class SpotPriceResponse(
    val symbol: String,
    @SerialName("last_price") val price: Double,
    val change: Double = 0.0,
    @SerialName("change_percent") val changePercent: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * India VIX data response
 */
@Serializable
data class IndiaVixResponse(
    val symbol: String = "INDIAVIX",
    @SerialName("last_price") val value: Double,
    val change: Double = 0.0,
    @SerialName("change_percent") val changePercent: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val open: Double = 0.0,
    @SerialName("previous_close") val previousClose: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Market overview with VIX and other indicators
 */
@Serializable
data class MarketOverviewResponse(
    @SerialName("india_vix") val indiaVix: IndiaVixResponse? = null,
    @SerialName("nifty_spot") val niftySpot: SpotPriceResponse? = null,
    @SerialName("banknifty_spot") val bankNiftySpot: SpotPriceResponse? = null,
    @SerialName("market_status") val marketStatus: String = "closed",
    @SerialName("advance_decline") val advanceDecline: AdvanceDeclineDto? = null
)

@Serializable
data class AdvanceDeclineDto(
    val advances: Int = 0,
    val declines: Int = 0,
    val unchanged: Int = 0
)
