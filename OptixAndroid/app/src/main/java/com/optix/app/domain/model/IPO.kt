package com.optix.app.domain.model

import java.time.LocalDate

/**
 * IPO status
 */
enum class IPOStatus(val displayName: String) {
    UPCOMING("Upcoming"),
    OPEN("Open"),
    CLOSED("Closed"),
    ALLOTMENT("Allotment"),
    LISTED("Listed")
}

/**
 * IPO type
 */
enum class IPOType(val displayName: String) {
    MAINBOARD("Mainboard"),
    SME("SME")
}

/**
 * AI verdict for IPO
 */
enum class IPOVerdict(val displayName: String, val emoji: String) {
    SUBSCRIBE("Subscribe", "👍"),
    AVOID("Avoid", "👎"),
    NEUTRAL("Neutral", "🤔")
}

/**
 * IPO listing item
 */
data class IPOItem(
    val id: String,
    val name: String,
    val symbol: String? = null,
    val type: IPOType,
    val status: IPOStatus,
    val priceRange: String,
    val minPrice: Double,
    val maxPrice: Double,
    val lotSize: Int,
    val minInvestment: Double,
    val issueSize: String,
    val openDate: LocalDate?,
    val closeDate: LocalDate?,
    val allotmentDate: LocalDate?,
    val listingDate: LocalDate?,
    val gmp: Double? = null,  // Gray Market Premium
    val expectedListing: Double? = null,
    val verdict: IPOVerdict? = null,
    val verdictReason: String? = null,
    val subscriptionStatus: String? = null,
    val qib: Double? = null,      // QIB subscription times
    val nii: Double? = null,      // NII subscription times
    val retail: Double? = null,   // Retail subscription times
    val total: Double? = null,    // Total subscription times
    val listedPrice: Double? = null,
    val currentPrice: Double? = null,
    val listingGain: Double? = null,
    val logoUrl: String? = null,
    val about: String? = null
) {
    val gmpDisplay: String
        get() = gmp?.let { if (it >= 0) "+₹$it" else "₹$it" } ?: "N/A"

    val expectedListingDisplay: String
        get() = expectedListing?.let { "₹${String.format("%.2f", it)}" } ?: "N/A"

    val listingGainDisplay: String
        get() = listingGain?.let {
            val sign = if (it >= 0) "+" else ""
            "$sign${String.format("%.2f", it)}%"
        } ?: "N/A"

    val statusColor: Long
        get() = when (status) {
            IPOStatus.UPCOMING -> 0xFF6366F1  // Indigo
            IPOStatus.OPEN -> 0xFF22C55E      // Green
            IPOStatus.CLOSED -> 0xFFF59E0B    // Amber
            IPOStatus.ALLOTMENT -> 0xFF3B82F6 // Blue
            IPOStatus.LISTED -> 0xFF8B5CF6   // Purple
        }
}
