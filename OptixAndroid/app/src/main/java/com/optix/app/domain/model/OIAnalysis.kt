package com.optix.app.domain.model

/**
 * OI Zone (Support/Resistance)
 */
data class OIZone(
    val strikePrice: Double,
    val callOI: Long,
    val putOI: Long,
    val netOI: Long,
    val zoneType: ZoneType,
    val strength: ZoneStrength
)

enum class ZoneType {
    SUPPORT, RESISTANCE
}

enum class ZoneStrength(val displayName: String) {
    STRONG("Strong"),
    MODERATE("Moderate"),
    WEAK("Weak")
}

/**
 * OI Heatmap cell
 */
data class OIHeatmapCell(
    val strikePrice: Double,
    val callOI: Long,
    val putOI: Long,
    val callOIChange: Long,
    val putOIChange: Long,
    val intensity: Float // 0.0 to 1.0
)

/**
 * Smart money activity
 */
data class SmartMoneyActivity(
    val strikePrice: Double,
    val optionType: OptionType,
    val oiChange: Long,
    val oiChangePercent: Double,
    val volumeOIRatio: Double,
    val interpretation: String,
    val isBullish: Boolean
)

/**
 * IV Surface point
 */
data class IVSurfacePoint(
    val strikePrice: Double,
    val expiry: String,
    val callIV: Double,
    val putIV: Double,
    val avgIV: Double
)

/**
 * OI interpretation
 */
data class OIInterpretation(
    val title: String,
    val description: String,
    val bias: MarketSentiment,
    val confidence: ConfidenceLevel
)

/**
 * Complete OI analysis result
 */
data class OIAnalysisResult(
    val symbol: String,
    val spotPrice: Double,
    val atmStrike: Double,
    val pcr: Double,
    val maxPain: Double,
    val supportZones: List<OIZone>,
    val resistanceZones: List<OIZone>,
    val heatmapData: List<OIHeatmapCell>,
    val smartMoneyActivities: List<SmartMoneyActivity>,
    val ivSurface: List<IVSurfacePoint>,
    val interpretation: OIInterpretation,
    val timestamp: Long = System.currentTimeMillis()
) {
    val pcrDisplay: String
        get() = String.format("%.2f", pcr)

    val pcrSentiment: MarketSentiment
        get() = when {
            pcr > 1.2 -> MarketSentiment.BULLISH
            pcr < 0.8 -> MarketSentiment.BEARISH
            else -> MarketSentiment.NEUTRAL
        }

    val maxPainDisplay: String
        get() = String.format("%.0f", maxPain)
}
