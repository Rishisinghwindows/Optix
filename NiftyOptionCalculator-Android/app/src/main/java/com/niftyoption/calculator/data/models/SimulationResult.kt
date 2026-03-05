package com.niftyoption.calculator.data.models

data class SimulationInput(
    val spotPrice: Double,
    val strikePrice: Double,
    val optionType: OptionType,
    val entryPremium: Double,
    val iv: Double,              // decimal (0.15 for 15%)
    val daysToExpiry: Int,
    val lotSize: Int = 75,
    val quantity: Int = 1
)

data class LegSimResult(
    val input: SimulationInput,
    val newPrice: Double,
    val pnl: Double,
    val pnlPercent: Double,
    val newDelta: Double,
    val newGamma: Double,
    val newTheta: Double,
    val newVega: Double
)

data class SimulationResult(
    val totalPnl: Double,
    val totalPnlPercent: Double,
    val legResults: List<LegSimResult>,
    val payoffPoints: List<PayoffPoint>
)

data class PayoffPoint(
    val spotPrice: Double,
    val pnl: Double
)
