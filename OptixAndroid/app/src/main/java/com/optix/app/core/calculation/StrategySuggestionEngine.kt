package com.optix.app.core.calculation

import com.optix.app.domain.model.LegPosition
import com.optix.app.domain.model.MarketRegime
import com.optix.app.domain.model.MarketSentiment
import com.optix.app.domain.model.NetGreeks
import com.optix.app.domain.model.OptionChain
import com.optix.app.domain.model.OptionChainRow
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.RecommendedLeg
import com.optix.app.domain.model.RiskLevel
import com.optix.app.domain.model.StrategyRecommendation
import com.optix.app.domain.model.StrategyType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Engine for suggesting multi-leg option strategies based on market conditions.
 *
 * Strategy selection is based on:
 * - Market regime (trending vs range-bound)
 * - IV level (high vs low)
 * - Market sentiment (bullish/bearish/neutral)
 * - IV percentile
 */
@Singleton
class StrategySuggestionEngine @Inject constructor(
    private val blackScholesEngine: BlackScholesEngine
) {

    companion object {
        private const val HIGH_IV_PERCENTILE = 60.0
        private const val LOW_IV_PERCENTILE = 40.0
        private const val STRIKE_DISTANCE_FACTOR = 0.02 // 2% OTM for spreads
    }

    /**
     * Suggest strategies based on current market conditions
     */
    fun suggestStrategies(
        optionChain: OptionChain,
        marketSentiment: MarketSentiment,
        marketRegime: MarketRegime,
        ivPercentile: Double,
        maxStrategies: Int = 5
    ): List<StrategyRecommendation> {
        val strategies = mutableListOf<StrategyRecommendation>()
        val isHighIV = ivPercentile > HIGH_IV_PERCENTILE
        val isLowIV = ivPercentile < LOW_IV_PERCENTILE

        // Determine best strategies based on conditions
        val strategiesToBuild = determineStrategiesToBuild(
            marketSentiment = marketSentiment,
            marketRegime = marketRegime,
            isHighIV = isHighIV,
            isLowIV = isLowIV
        )

        strategiesToBuild.forEach { strategyType ->
            val strategy = buildStrategy(
                strategyType = strategyType,
                optionChain = optionChain,
                marketSentiment = marketSentiment,
                marketRegime = marketRegime,
                ivPercentile = ivPercentile
            )
            if (strategy != null) {
                strategies.add(strategy)
            }
        }

        return strategies
            .sortedByDescending { it.score }
            .take(maxStrategies)
    }

    /**
     * Determine which strategies to build based on market conditions
     */
    private fun determineStrategiesToBuild(
        marketSentiment: MarketSentiment,
        marketRegime: MarketRegime,
        isHighIV: Boolean,
        isLowIV: Boolean
    ): List<StrategyType> {
        return when {
            // Bullish + High IV
            marketSentiment == MarketSentiment.BULLISH && isHighIV -> listOf(
                StrategyType.BULL_PUT_SPREAD,
                StrategyType.COVERED_CALL,
                StrategyType.JADE_LIZARD
            )

            // Bullish + Low IV
            marketSentiment == MarketSentiment.BULLISH && isLowIV -> listOf(
                StrategyType.LONG_CALL,
                StrategyType.BULL_CALL_SPREAD,
                StrategyType.DIAGONAL_SPREAD
            )

            // Bullish + Normal IV
            marketSentiment == MarketSentiment.BULLISH -> listOf(
                StrategyType.BULL_CALL_SPREAD,
                StrategyType.BULL_PUT_SPREAD
            )

            // Bearish + High IV
            marketSentiment == MarketSentiment.BEARISH && isHighIV -> listOf(
                StrategyType.BEAR_CALL_SPREAD,
                StrategyType.PROTECTIVE_PUT
            )

            // Bearish + Low IV
            marketSentiment == MarketSentiment.BEARISH && isLowIV -> listOf(
                StrategyType.LONG_PUT,
                StrategyType.BEAR_PUT_SPREAD
            )

            // Bearish + Normal IV
            marketSentiment == MarketSentiment.BEARISH -> listOf(
                StrategyType.BEAR_PUT_SPREAD,
                StrategyType.BEAR_CALL_SPREAD
            )

            // Neutral + High IV - Sell volatility
            marketSentiment == MarketSentiment.NEUTRAL && isHighIV -> listOf(
                StrategyType.IRON_CONDOR,
                StrategyType.SHORT_STRANGLE,
                StrategyType.SHORT_STRADDLE,
                StrategyType.IRON_BUTTERFLY
            )

            // Neutral + Low IV - Buy volatility
            marketSentiment == MarketSentiment.NEUTRAL && isLowIV -> listOf(
                StrategyType.LONG_STRADDLE,
                StrategyType.LONG_STRANGLE,
                StrategyType.CALENDAR_SPREAD
            )

            // Range-bound market
            marketRegime == MarketRegime.RANGE_BOUND -> listOf(
                StrategyType.IRON_CONDOR,
                StrategyType.SHORT_STRANGLE
            )

            // High volatility regime
            marketRegime == MarketRegime.HIGH_VOLATILITY -> listOf(
                StrategyType.IRON_CONDOR,
                StrategyType.IRON_BUTTERFLY
            )

            // Default neutral strategies
            else -> listOf(
                StrategyType.IRON_CONDOR,
                StrategyType.BULL_PUT_SPREAD,
                StrategyType.BEAR_CALL_SPREAD
            )
        }
    }

    /**
     * Build a specific strategy recommendation
     */
    private fun buildStrategy(
        strategyType: StrategyType,
        optionChain: OptionChain,
        marketSentiment: MarketSentiment,
        marketRegime: MarketRegime,
        ivPercentile: Double
    ): StrategyRecommendation? {
        return when (strategyType) {
            StrategyType.LONG_CALL -> buildLongCall(optionChain, ivPercentile)
            StrategyType.LONG_PUT -> buildLongPut(optionChain, ivPercentile)
            StrategyType.BULL_CALL_SPREAD -> buildBullCallSpread(optionChain, ivPercentile)
            StrategyType.BEAR_PUT_SPREAD -> buildBearPutSpread(optionChain, ivPercentile)
            StrategyType.BULL_PUT_SPREAD -> buildBullPutSpread(optionChain, ivPercentile)
            StrategyType.BEAR_CALL_SPREAD -> buildBearCallSpread(optionChain, ivPercentile)
            StrategyType.IRON_CONDOR -> buildIronCondor(optionChain, ivPercentile)
            StrategyType.LONG_STRADDLE -> buildLongStraddle(optionChain, ivPercentile)
            StrategyType.SHORT_STRADDLE -> buildShortStraddle(optionChain, ivPercentile)
            StrategyType.LONG_STRANGLE -> buildLongStrangle(optionChain, ivPercentile)
            StrategyType.SHORT_STRANGLE -> buildShortStrangle(optionChain, ivPercentile)
            StrategyType.IRON_BUTTERFLY -> buildIronButterfly(optionChain, ivPercentile)
            else -> null // Other strategies not implemented yet
        }
    }

    // ============================
    // Single Leg Strategies
    // ============================

    private fun buildLongCall(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        // Find slightly OTM call
        val strike = findStrike(optionChain, 1, OptionType.CALL) ?: return null
        val callData = optionChain.rows.find { it.strikePrice == strike }?.callData ?: return null

        val leg = RecommendedLeg(
            optionType = OptionType.CALL,
            strikePrice = strike,
            position = LegPosition.BUY,
            quantity = 1,
            premium = callData.lastPrice,
            delta = callData.delta,
            gamma = callData.gamma,
            theta = callData.theta,
            vega = callData.vega
        )

        val maxLoss = callData.lastPrice
        val maxProfit = Double.MAX_VALUE // Unlimited

        return createRecommendation(
            strategyType = StrategyType.LONG_CALL,
            optionChain = optionChain,
            legs = listOf(leg),
            netPremium = -callData.lastPrice,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(strike + callData.lastPrice),
            ivPercentile = ivPercentile,
            marketConditionFit = "Best in low IV, bullish trending markets"
        )
    }

    private fun buildLongPut(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val strike = findStrike(optionChain, -1, OptionType.PUT) ?: return null
        val putData = optionChain.rows.find { it.strikePrice == strike }?.putData ?: return null

        val leg = RecommendedLeg(
            optionType = OptionType.PUT,
            strikePrice = strike,
            position = LegPosition.BUY,
            quantity = 1,
            premium = putData.lastPrice,
            delta = putData.delta,
            gamma = putData.gamma,
            theta = putData.theta,
            vega = putData.vega
        )

        val maxLoss = putData.lastPrice
        val maxProfit = strike - putData.lastPrice

        return createRecommendation(
            strategyType = StrategyType.LONG_PUT,
            optionChain = optionChain,
            legs = listOf(leg),
            netPremium = -putData.lastPrice,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(strike - putData.lastPrice),
            ivPercentile = ivPercentile,
            marketConditionFit = "Best in low IV, bearish trending markets"
        )
    }

    // ============================
    // Vertical Spreads
    // ============================

    private fun buildBullCallSpread(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val longStrike = findStrike(optionChain, 0, OptionType.CALL) ?: return null // ATM
        val shortStrike = findStrike(optionChain, 2, OptionType.CALL) ?: return null // OTM

        val longCall = optionChain.rows.find { it.strikePrice == longStrike }?.callData ?: return null
        val shortCall = optionChain.rows.find { it.strikePrice == shortStrike }?.callData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.CALL, longStrike, LegPosition.BUY, 1, longCall.lastPrice,
                longCall.delta, longCall.gamma, longCall.theta, longCall.vega),
            RecommendedLeg(OptionType.CALL, shortStrike, LegPosition.SELL, 1, shortCall.lastPrice,
                shortCall.delta, shortCall.gamma, shortCall.theta, shortCall.vega)
        )

        val netDebit = longCall.lastPrice - shortCall.lastPrice
        val maxProfit = (shortStrike - longStrike) - netDebit
        val maxLoss = netDebit

        return createRecommendation(
            strategyType = StrategyType.BULL_CALL_SPREAD,
            optionChain = optionChain,
            legs = legs,
            netPremium = -netDebit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(longStrike + netDebit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Moderately bullish with defined risk"
        )
    }

    private fun buildBearPutSpread(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val longStrike = findStrike(optionChain, 0, OptionType.PUT) ?: return null
        val shortStrike = findStrike(optionChain, -2, OptionType.PUT) ?: return null

        val longPut = optionChain.rows.find { it.strikePrice == longStrike }?.putData ?: return null
        val shortPut = optionChain.rows.find { it.strikePrice == shortStrike }?.putData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.PUT, longStrike, LegPosition.BUY, 1, longPut.lastPrice,
                longPut.delta, longPut.gamma, longPut.theta, longPut.vega),
            RecommendedLeg(OptionType.PUT, shortStrike, LegPosition.SELL, 1, shortPut.lastPrice,
                shortPut.delta, shortPut.gamma, shortPut.theta, shortPut.vega)
        )

        val netDebit = longPut.lastPrice - shortPut.lastPrice
        val maxProfit = (longStrike - shortStrike) - netDebit
        val maxLoss = netDebit

        return createRecommendation(
            strategyType = StrategyType.BEAR_PUT_SPREAD,
            optionChain = optionChain,
            legs = legs,
            netPremium = -netDebit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(longStrike - netDebit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Moderately bearish with defined risk"
        )
    }

    private fun buildBullPutSpread(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val shortStrike = findStrike(optionChain, -1, OptionType.PUT) ?: return null
        val longStrike = findStrike(optionChain, -3, OptionType.PUT) ?: return null

        val shortPut = optionChain.rows.find { it.strikePrice == shortStrike }?.putData ?: return null
        val longPut = optionChain.rows.find { it.strikePrice == longStrike }?.putData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.PUT, shortStrike, LegPosition.SELL, 1, shortPut.lastPrice,
                shortPut.delta, shortPut.gamma, shortPut.theta, shortPut.vega),
            RecommendedLeg(OptionType.PUT, longStrike, LegPosition.BUY, 1, longPut.lastPrice,
                longPut.delta, longPut.gamma, longPut.theta, longPut.vega)
        )

        val netCredit = shortPut.lastPrice - longPut.lastPrice
        val maxProfit = netCredit
        val maxLoss = (shortStrike - longStrike) - netCredit

        return createRecommendation(
            strategyType = StrategyType.BULL_PUT_SPREAD,
            optionChain = optionChain,
            legs = legs,
            netPremium = netCredit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(shortStrike - netCredit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Bullish/Neutral with high IV"
        )
    }

    private fun buildBearCallSpread(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val shortStrike = findStrike(optionChain, 1, OptionType.CALL) ?: return null
        val longStrike = findStrike(optionChain, 3, OptionType.CALL) ?: return null

        val shortCall = optionChain.rows.find { it.strikePrice == shortStrike }?.callData ?: return null
        val longCall = optionChain.rows.find { it.strikePrice == longStrike }?.callData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.CALL, shortStrike, LegPosition.SELL, 1, shortCall.lastPrice,
                shortCall.delta, shortCall.gamma, shortCall.theta, shortCall.vega),
            RecommendedLeg(OptionType.CALL, longStrike, LegPosition.BUY, 1, longCall.lastPrice,
                longCall.delta, longCall.gamma, longCall.theta, longCall.vega)
        )

        val netCredit = shortCall.lastPrice - longCall.lastPrice
        val maxProfit = netCredit
        val maxLoss = (longStrike - shortStrike) - netCredit

        return createRecommendation(
            strategyType = StrategyType.BEAR_CALL_SPREAD,
            optionChain = optionChain,
            legs = legs,
            netPremium = netCredit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(shortStrike + netCredit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Bearish/Neutral with high IV"
        )
    }

    // ============================
    // Iron Condor & Butterfly
    // ============================

    private fun buildIronCondor(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        // Bull put spread + Bear call spread
        val shortPutStrike = findStrike(optionChain, -2, OptionType.PUT) ?: return null
        val longPutStrike = findStrike(optionChain, -4, OptionType.PUT) ?: return null
        val shortCallStrike = findStrike(optionChain, 2, OptionType.CALL) ?: return null
        val longCallStrike = findStrike(optionChain, 4, OptionType.CALL) ?: return null

        val shortPut = optionChain.rows.find { it.strikePrice == shortPutStrike }?.putData ?: return null
        val longPut = optionChain.rows.find { it.strikePrice == longPutStrike }?.putData ?: return null
        val shortCall = optionChain.rows.find { it.strikePrice == shortCallStrike }?.callData ?: return null
        val longCall = optionChain.rows.find { it.strikePrice == longCallStrike }?.callData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.PUT, longPutStrike, LegPosition.BUY, 1, longPut.lastPrice,
                longPut.delta, longPut.gamma, longPut.theta, longPut.vega),
            RecommendedLeg(OptionType.PUT, shortPutStrike, LegPosition.SELL, 1, shortPut.lastPrice,
                shortPut.delta, shortPut.gamma, shortPut.theta, shortPut.vega),
            RecommendedLeg(OptionType.CALL, shortCallStrike, LegPosition.SELL, 1, shortCall.lastPrice,
                shortCall.delta, shortCall.gamma, shortCall.theta, shortCall.vega),
            RecommendedLeg(OptionType.CALL, longCallStrike, LegPosition.BUY, 1, longCall.lastPrice,
                longCall.delta, longCall.gamma, longCall.theta, longCall.vega)
        )

        val netCredit = (shortPut.lastPrice - longPut.lastPrice) + (shortCall.lastPrice - longCall.lastPrice)
        val maxProfit = netCredit
        val maxLoss = (shortPutStrike - longPutStrike) - netCredit

        return createRecommendation(
            strategyType = StrategyType.IRON_CONDOR,
            optionChain = optionChain,
            legs = legs,
            netPremium = netCredit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(shortPutStrike - netCredit, shortCallStrike + netCredit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Range-bound market with high IV"
        )
    }

    private fun buildIronButterfly(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val atmStrike = findStrike(optionChain, 0, OptionType.CALL) ?: return null
        val lowerStrike = findStrike(optionChain, -3, OptionType.PUT) ?: return null
        val upperStrike = findStrike(optionChain, 3, OptionType.CALL) ?: return null

        val atmCall = optionChain.rows.find { it.strikePrice == atmStrike }?.callData ?: return null
        val atmPut = optionChain.rows.find { it.strikePrice == atmStrike }?.putData ?: return null
        val lowerPut = optionChain.rows.find { it.strikePrice == lowerStrike }?.putData ?: return null
        val upperCall = optionChain.rows.find { it.strikePrice == upperStrike }?.callData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.PUT, lowerStrike, LegPosition.BUY, 1, lowerPut.lastPrice,
                lowerPut.delta, lowerPut.gamma, lowerPut.theta, lowerPut.vega),
            RecommendedLeg(OptionType.PUT, atmStrike, LegPosition.SELL, 1, atmPut.lastPrice,
                atmPut.delta, atmPut.gamma, atmPut.theta, atmPut.vega),
            RecommendedLeg(OptionType.CALL, atmStrike, LegPosition.SELL, 1, atmCall.lastPrice,
                atmCall.delta, atmCall.gamma, atmCall.theta, atmCall.vega),
            RecommendedLeg(OptionType.CALL, upperStrike, LegPosition.BUY, 1, upperCall.lastPrice,
                upperCall.delta, upperCall.gamma, upperCall.theta, upperCall.vega)
        )

        val netCredit = (atmPut.lastPrice + atmCall.lastPrice) - (lowerPut.lastPrice + upperCall.lastPrice)
        val maxProfit = netCredit
        val maxLoss = (atmStrike - lowerStrike) - netCredit

        return createRecommendation(
            strategyType = StrategyType.IRON_BUTTERFLY,
            optionChain = optionChain,
            legs = legs,
            netPremium = netCredit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(atmStrike - netCredit, atmStrike + netCredit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Expecting spot to stay near current level"
        )
    }

    // ============================
    // Straddles and Strangles
    // ============================

    private fun buildLongStraddle(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val atmStrike = findStrike(optionChain, 0, OptionType.CALL) ?: return null
        val atmCall = optionChain.rows.find { it.strikePrice == atmStrike }?.callData ?: return null
        val atmPut = optionChain.rows.find { it.strikePrice == atmStrike }?.putData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.CALL, atmStrike, LegPosition.BUY, 1, atmCall.lastPrice,
                atmCall.delta, atmCall.gamma, atmCall.theta, atmCall.vega),
            RecommendedLeg(OptionType.PUT, atmStrike, LegPosition.BUY, 1, atmPut.lastPrice,
                atmPut.delta, atmPut.gamma, atmPut.theta, atmPut.vega)
        )

        val netDebit = atmCall.lastPrice + atmPut.lastPrice
        val maxLoss = netDebit
        val maxProfit = Double.MAX_VALUE // Unlimited on upside

        return createRecommendation(
            strategyType = StrategyType.LONG_STRADDLE,
            optionChain = optionChain,
            legs = legs,
            netPremium = -netDebit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(atmStrike - netDebit, atmStrike + netDebit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Expecting big move, direction uncertain, low IV"
        )
    }

    private fun buildShortStraddle(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val atmStrike = findStrike(optionChain, 0, OptionType.CALL) ?: return null
        val atmCall = optionChain.rows.find { it.strikePrice == atmStrike }?.callData ?: return null
        val atmPut = optionChain.rows.find { it.strikePrice == atmStrike }?.putData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.CALL, atmStrike, LegPosition.SELL, 1, atmCall.lastPrice,
                atmCall.delta, atmCall.gamma, atmCall.theta, atmCall.vega),
            RecommendedLeg(OptionType.PUT, atmStrike, LegPosition.SELL, 1, atmPut.lastPrice,
                atmPut.delta, atmPut.gamma, atmPut.theta, atmPut.vega)
        )

        val netCredit = atmCall.lastPrice + atmPut.lastPrice
        val maxProfit = netCredit
        val maxLoss = Double.MAX_VALUE // Unlimited

        return createRecommendation(
            strategyType = StrategyType.SHORT_STRADDLE,
            optionChain = optionChain,
            legs = legs,
            netPremium = netCredit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(atmStrike - netCredit, atmStrike + netCredit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Expecting low volatility, high IV environment"
        )
    }

    private fun buildLongStrangle(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val callStrike = findStrike(optionChain, 2, OptionType.CALL) ?: return null
        val putStrike = findStrike(optionChain, -2, OptionType.PUT) ?: return null
        val call = optionChain.rows.find { it.strikePrice == callStrike }?.callData ?: return null
        val put = optionChain.rows.find { it.strikePrice == putStrike }?.putData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.CALL, callStrike, LegPosition.BUY, 1, call.lastPrice,
                call.delta, call.gamma, call.theta, call.vega),
            RecommendedLeg(OptionType.PUT, putStrike, LegPosition.BUY, 1, put.lastPrice,
                put.delta, put.gamma, put.theta, put.vega)
        )

        val netDebit = call.lastPrice + put.lastPrice
        val maxLoss = netDebit
        val maxProfit = Double.MAX_VALUE

        return createRecommendation(
            strategyType = StrategyType.LONG_STRANGLE,
            optionChain = optionChain,
            legs = legs,
            netPremium = -netDebit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(putStrike - netDebit, callStrike + netDebit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Expecting big move, lower cost than straddle"
        )
    }

    private fun buildShortStrangle(optionChain: OptionChain, ivPercentile: Double): StrategyRecommendation? {
        val callStrike = findStrike(optionChain, 2, OptionType.CALL) ?: return null
        val putStrike = findStrike(optionChain, -2, OptionType.PUT) ?: return null
        val call = optionChain.rows.find { it.strikePrice == callStrike }?.callData ?: return null
        val put = optionChain.rows.find { it.strikePrice == putStrike }?.putData ?: return null

        val legs = listOf(
            RecommendedLeg(OptionType.CALL, callStrike, LegPosition.SELL, 1, call.lastPrice,
                call.delta, call.gamma, call.theta, call.vega),
            RecommendedLeg(OptionType.PUT, putStrike, LegPosition.SELL, 1, put.lastPrice,
                put.delta, put.gamma, put.theta, put.vega)
        )

        val netCredit = call.lastPrice + put.lastPrice
        val maxProfit = netCredit
        val maxLoss = Double.MAX_VALUE

        return createRecommendation(
            strategyType = StrategyType.SHORT_STRANGLE,
            optionChain = optionChain,
            legs = legs,
            netPremium = netCredit,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = listOf(putStrike - netCredit, callStrike + netCredit),
            ivPercentile = ivPercentile,
            marketConditionFit = "Range-bound, high IV, wider profit zone than straddle"
        )
    }

    // ============================
    // Helper Functions
    // ============================

    /**
     * Find strike at offset from ATM
     * @param offset Number of strikes from ATM (positive = OTM calls/ITM puts, negative = ITM calls/OTM puts)
     */
    private fun findStrike(optionChain: OptionChain, offset: Int, optionType: OptionType): Double? {
        val sortedRows = optionChain.rows.sortedBy { it.strikePrice }
        val atmIndex = sortedRows.indexOfFirst { abs(it.strikePrice - optionChain.atmStrike) < 1 }
        if (atmIndex == -1) return null

        val targetIndex = atmIndex + offset
        return sortedRows.getOrNull(targetIndex)?.strikePrice
    }

    /**
     * Create a strategy recommendation with calculated score
     */
    private fun createRecommendation(
        strategyType: StrategyType,
        optionChain: OptionChain,
        legs: List<RecommendedLeg>,
        netPremium: Double,
        maxProfit: Double,
        maxLoss: Double,
        breakevens: List<Double>,
        ivPercentile: Double,
        marketConditionFit: String
    ): StrategyRecommendation {
        // Calculate net Greeks
        val netDelta = legs.sumOf { if (it.position == LegPosition.BUY) it.delta else -it.delta }
        val netGamma = legs.sumOf { if (it.position == LegPosition.BUY) it.gamma else -it.gamma }
        val netTheta = legs.sumOf { if (it.position == LegPosition.BUY) it.theta else -it.theta }
        val netVega = legs.sumOf { if (it.position == LegPosition.BUY) it.vega else -it.vega }

        val netGreeks = NetGreeks(
            delta = netDelta,
            gamma = netGamma,
            theta = netTheta,
            vega = netVega
        )

        // Calculate probability of profit (simplified)
        val pop = calculatePOP(optionChain.spotPrice, breakevens, netPremium > 0)

        // Calculate score
        val score = calculateScore(
            riskReward = if (maxLoss > 0 && maxLoss != Double.MAX_VALUE) maxProfit / maxLoss else 0.0,
            pop = pop,
            isCredit = netPremium > 0,
            ivPercentile = ivPercentile,
            strategyType = strategyType
        )

        // Determine risk level
        val riskLevel = when {
            maxLoss == Double.MAX_VALUE -> RiskLevel.HIGH
            maxLoss > maxProfit * 2 -> RiskLevel.MODERATE
            else -> RiskLevel.LOW
        }

        return StrategyRecommendation(
            id = UUID.randomUUID().toString(),
            strategyType = strategyType,
            symbol = optionChain.index.symbol,
            expiry = optionChain.expiry,
            legs = legs,
            netPremium = netPremium,
            maxProfit = if (maxProfit == Double.MAX_VALUE) 999999.0 else maxProfit,
            maxLoss = if (maxLoss == Double.MAX_VALUE) 999999.0 else maxLoss,
            breakevens = breakevens,
            probabilityOfProfit = pop,
            netGreeks = netGreeks,
            score = score,
            marketConditionFit = marketConditionFit,
            riskLevel = riskLevel
        )
    }

    /**
     * Calculate probability of profit (simplified delta-based approximation)
     */
    private fun calculatePOP(spotPrice: Double, breakevens: List<Double>, isCredit: Boolean): Double {
        if (breakevens.isEmpty()) return 0.5

        return if (breakevens.size == 1) {
            // Single breakeven
            val distance = abs(breakevens[0] - spotPrice) / spotPrice
            if (isCredit) 0.7 - distance else 0.5 - distance
        } else {
            // Two breakevens (range strategy)
            val lowerBE = breakevens.minOrNull() ?: spotPrice
            val upperBE = breakevens.maxOrNull() ?: spotPrice
            val range = (upperBE - lowerBE) / spotPrice

            if (isCredit) {
                // Short strategies profit when price stays in range
                0.6 + (range * 0.5).coerceAtMost(0.3)
            } else {
                // Long strategies profit when price moves outside range
                0.4 - (range * 0.2).coerceAtMost(0.2)
            }
        }.coerceIn(0.1, 0.9)
    }

    /**
     * Calculate strategy score
     */
    private fun calculateScore(
        riskReward: Double,
        pop: Double,
        isCredit: Boolean,
        ivPercentile: Double,
        strategyType: StrategyType
    ): Int {
        var score = 50.0

        // Risk/Reward component (max 20 points)
        score += (riskReward.coerceAtMost(3.0) / 3.0) * 20

        // POP component (max 20 points)
        score += pop * 20

        // IV fit component (max 10 points)
        val ivFit = when {
            isCredit && ivPercentile > 60 -> 10.0
            !isCredit && ivPercentile < 40 -> 10.0
            else -> 5.0
        }
        score += ivFit

        return score.toInt().coerceIn(0, 100)
    }
}
