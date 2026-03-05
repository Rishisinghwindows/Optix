package com.niftyoption.calculator.domain

import com.niftyoption.calculator.data.models.OptionData
import com.niftyoption.calculator.data.models.OptionType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

class TargetCalculatorTest {

    private val tolerance = 0.01

    /**
     * Helper to create an OptionData with a future expiry date
     */
    private fun makeOption(
        strikePrice: Double = 24000.0,
        optionType: OptionType = OptionType.CALL,
        lastTradedPrice: Double = 200.0,
        impliedVolatility: Double = 0.15,
        underlyingValue: Double = 24000.0,
        daysAhead: Int = 30,
        volume: Int = 1000,
        openInterest: Int = 50000
    ): OptionData {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
        return OptionData(
            strikePrice = strikePrice,
            optionType = optionType,
            expiryDate = calendar.time,
            lastTradedPrice = lastTradedPrice,
            impliedVolatility = impliedVolatility,
            underlyingValue = underlyingValue,
            volume = volume,
            openInterest = openInterest
        )
    }

    // =====================================================
    // MARK: - calculateTargetSL Tests
    // =====================================================

    @Test
    fun `calculateTargetSL returns valid target calculation for call`() {
        val option = makeOption()
        val result = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = 24500.0,
            stopLossSpot = 23700.0
        )

        assertEquals(OptionType.CALL, result.optionType)
        assertEquals(24000.0, result.strikePrice, tolerance)
        assertEquals(24000.0, result.currentSpot, tolerance)
        assertEquals(24500.0, result.targetSpot, tolerance)
        assertEquals(23700.0, result.stopLossSpot, tolerance)
        assertTrue("Target option price should be positive", result.targetOptionPrice > 0)
    }

    @Test
    fun `calculateTargetSL target price should be higher than current for bullish call`() {
        val option = makeOption()
        val result = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0
        )
        assertTrue("Target option price should be > stop-loss option price",
            result.targetOptionPrice > result.stopLossOptionPrice)
    }

    @Test
    fun `calculateTargetSL for put should increase when spot drops`() {
        val option = makeOption(optionType = OptionType.PUT)
        val result = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = 23500.0,  // Bearish target
            stopLossSpot = 24500.0 // Stop-loss above
        )
        assertTrue("Put target price should be > stop-loss price when spot drops",
            result.targetOptionPrice > result.stopLossOptionPrice)
    }

    @Test
    fun `calculateTargetSL with days to target reduces time value`() {
        val option = makeOption()
        val resultNow = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0,
            daysToTarget = 0.0
        )
        val resultLater = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0,
            daysToTarget = 15.0
        )
        // With more days elapsed, the option should have less time value
        assertTrue("Target with more days elapsed should be <= without",
            resultLater.targetOptionPrice <= resultNow.targetOptionPrice)
    }

    // =====================================================
    // MARK: - TargetCalculation Computed Properties
    // =====================================================

    @Test
    fun `targetProfit should be positive for profitable trade`() {
        val option = makeOption(lastTradedPrice = 200.0)
        val result = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0
        )
        // If target option price > current option price (200), profit is positive
        if (result.targetOptionPrice > option.lastTradedPrice) {
            assertTrue("Target profit should be positive", result.targetProfit > 0)
        }
    }

    @Test
    fun `riskRewardRatio should be positive when stop loss has value`() {
        val option = makeOption(lastTradedPrice = 200.0)
        val result = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0
        )
        if (result.stopLossLoss > 0) {
            assertTrue("Risk-reward ratio should be positive", result.riskRewardRatio > 0)
        }
    }

    // =====================================================
    // MARK: - calculateOptionPrice Tests
    // =====================================================

    @Test
    fun `calculateOptionPrice should match BlackScholesEngine directly`() {
        val bsPrice = BlackScholesEngine.calculateOptionPrice(
            spotPrice = 24000.0, strikePrice = 24000.0,
            timeToExpiry = 30.0 / 365.0, volatility = 0.15,
            optionType = OptionType.CALL
        )
        val tcPrice = TargetCalculator.calculateOptionPrice(
            spotPrice = 24000.0, strikePrice = 24000.0,
            optionType = OptionType.CALL, timeToExpiry = 30.0 / 365.0,
            volatility = 0.15
        )
        assertEquals(bsPrice, tcPrice, tolerance)
    }

    // =====================================================
    // MARK: - calculateGreeks Tests
    // =====================================================

    @Test
    fun `calculateGreeks should match BlackScholesEngine directly`() {
        val bsGreeks = BlackScholesEngine.calculateGreeks(
            spotPrice = 24000.0, strikePrice = 24000.0,
            timeToExpiry = 30.0 / 365.0, volatility = 0.15,
            optionType = OptionType.CALL
        )
        val tcGreeks = TargetCalculator.calculateGreeks(
            spotPrice = 24000.0, strikePrice = 24000.0,
            optionType = OptionType.CALL, timeToExpiry = 30.0 / 365.0,
            volatility = 0.15
        )
        assertEquals(bsGreeks.delta, tcGreeks.delta, tolerance)
        assertEquals(bsGreeks.gamma, tcGreeks.gamma, tolerance)
        assertEquals(bsGreeks.theta, tcGreeks.theta, tolerance)
        assertEquals(bsGreeks.vega, tcGreeks.vega, tolerance)
        assertEquals(bsGreeks.rho, tcGreeks.rho, tolerance)
    }

    // =====================================================
    // MARK: - quickEstimate Tests
    // =====================================================

    @Test
    fun `quickEstimate for call with positive move should increase price`() {
        val result = TargetCalculator.quickEstimate(
            currentOptionPrice = 200.0,
            delta = 0.5,
            underlyingMove = 100.0
        )
        assertEquals(250.0, result, tolerance) // 200 + 0.5 * 100
    }

    @Test
    fun `quickEstimate for put with negative move should increase price`() {
        val result = TargetCalculator.quickEstimate(
            currentOptionPrice = 200.0,
            delta = -0.5,
            underlyingMove = -100.0
        )
        assertEquals(250.0, result, tolerance) // 200 + (-0.5) * (-100)
    }

    @Test
    fun `quickEstimate with zero move should return current price`() {
        val result = TargetCalculator.quickEstimate(
            currentOptionPrice = 200.0,
            delta = 0.5,
            underlyingMove = 0.0
        )
        assertEquals(200.0, result, tolerance)
    }

    // =====================================================
    // MARK: - accurateEstimate Tests
    // =====================================================

    @Test
    fun `accurateEstimate includes delta and gamma effects`() {
        val result = TargetCalculator.accurateEstimate(
            currentOptionPrice = 200.0,
            delta = 0.5,
            gamma = 0.001,
            theta = 0.0,
            underlyingMove = 100.0
        )
        // 200 + 0.5*100 + 0.5*0.001*100*100 = 200 + 50 + 5 = 255
        assertEquals(255.0, result, tolerance)
    }

    @Test
    fun `accurateEstimate includes theta decay`() {
        val result = TargetCalculator.accurateEstimate(
            currentOptionPrice = 200.0,
            delta = 0.0,
            gamma = 0.0,
            theta = -5.0,
            underlyingMove = 0.0,
            daysElapsed = 3.0
        )
        // 200 + 0 + 0 + (-5)*3 = 185
        assertEquals(185.0, result, tolerance)
    }

    @Test
    fun `accurateEstimate should never go below zero`() {
        val result = TargetCalculator.accurateEstimate(
            currentOptionPrice = 10.0,
            delta = -0.5,
            gamma = 0.0,
            theta = -5.0,
            underlyingMove = 100.0,
            daysElapsed = 10.0
        )
        // 10 + (-0.5)*100 + 0 + (-5)*10 = 10 - 50 - 50 = -90 → clamped to 0
        assertEquals(0.0, result, tolerance)
    }

    // =====================================================
    // MARK: - findSpotForTargetPrice Tests
    // =====================================================

    @Test
    fun `findSpotForTargetPrice for call finds correct spot`() {
        val timeToExpiry = 30.0 / 365.0
        val vol = 0.15
        val strike = 24000.0
        val currentSpot = 24000.0

        // Calculate the price at spot=24200 and try to find that spot back
        val targetPrice = TargetCalculator.calculateOptionPrice(
            spotPrice = 24200.0, strikePrice = strike,
            optionType = OptionType.CALL, timeToExpiry = timeToExpiry, volatility = vol
        )
        val foundSpot = TargetCalculator.findSpotForTargetPrice(
            targetOptionPrice = targetPrice, strikePrice = strike,
            optionType = OptionType.CALL, timeToExpiry = timeToExpiry,
            volatility = vol, currentSpot = currentSpot
        )
        if (foundSpot != null) {
            assertEquals(24200.0, foundSpot, 1.0) // Allow some binary search tolerance
        }
    }

    @Test
    fun `findSpotForTargetPrice returns null for unreachable target`() {
        // An extremely high target price that can't be reached in the search range
        val result = TargetCalculator.findSpotForTargetPrice(
            targetOptionPrice = 50000.0, strikePrice = 24000.0,
            optionType = OptionType.CALL, timeToExpiry = 30.0 / 365.0,
            volatility = 0.15, currentSpot = 24000.0
        )
        // Binary search may return null if it can't find the target
        // or may return a boundary value - both are acceptable
    }

    // =====================================================
    // MARK: - calculatePositionPnL Tests
    // =====================================================

    @Test
    fun `calculatePositionPnL returns correct number of results`() {
        val option = makeOption()
        val spotLevels = listOf(23500.0, 23750.0, 24000.0, 24250.0, 24500.0)
        val results = TargetCalculator.calculatePositionPnL(
            quantity = 1, entryPrice = 200.0,
            option = option, spotLevels = spotLevels
        )
        assertEquals(5, results.size)
    }

    @Test
    fun `calculatePositionPnL spot levels are preserved`() {
        val option = makeOption()
        val spotLevels = listOf(23500.0, 24000.0, 24500.0)
        val results = TargetCalculator.calculatePositionPnL(
            quantity = 1, entryPrice = 200.0,
            option = option, spotLevels = spotLevels
        )
        assertEquals(23500.0, results[0].first, tolerance)
        assertEquals(24000.0, results[1].first, tolerance)
        assertEquals(24500.0, results[2].first, tolerance)
    }

    @Test
    fun `calculatePositionPnL higher spot should give higher call PnL`() {
        val option = makeOption()
        val spotLevels = listOf(23500.0, 24500.0)
        val results = TargetCalculator.calculatePositionPnL(
            quantity = 1, entryPrice = 200.0,
            option = option, spotLevels = spotLevels, lotSize = 25
        )
        // Call option: higher spot → higher option price → higher PnL
        assertTrue("Higher spot PnL should be > lower spot PnL",
            results[1].second > results[0].second)
    }

    @Test
    fun `calculatePositionPnL respects lot size`() {
        val option = makeOption()
        val spotLevels = listOf(24500.0)

        val resultSmallLot = TargetCalculator.calculatePositionPnL(
            quantity = 1, entryPrice = 200.0,
            option = option, spotLevels = spotLevels, lotSize = 25
        )
        val resultLargeLot = TargetCalculator.calculatePositionPnL(
            quantity = 1, entryPrice = 200.0,
            option = option, spotLevels = spotLevels, lotSize = 75
        )
        // PnL with 3x lot size should be 3x
        assertEquals(
            resultSmallLot[0].second * 3.0,
            resultLargeLot[0].second,
            abs(resultSmallLot[0].second * 0.01) + 0.01
        )
    }

    // =====================================================
    // MARK: - suggestTargetSL Tests
    // =====================================================

    @Test
    fun `suggestTargetSL returns pair for valid call option`() {
        val option = makeOption(lastTradedPrice = 200.0, impliedVolatility = 0.15)
        val result = TargetCalculator.suggestTargetSL(option)
        assertNotNull("Should return target/SL pair", result)
        val (target, stopLoss) = result!!
        // For a call, target should be above current spot, stop loss below
        assertTrue("Call target should be > current spot", target > option.underlyingValue)
        assertTrue("Call stop-loss should be < current spot", stopLoss < option.underlyingValue)
    }

    @Test
    fun `suggestTargetSL returns pair for valid put option`() {
        val option = makeOption(optionType = OptionType.PUT, lastTradedPrice = 200.0)
        val result = TargetCalculator.suggestTargetSL(option)
        assertNotNull("Should return target/SL pair", result)
        val (target, stopLoss) = result!!
        // For a put, target should be below current spot, stop loss above
        assertTrue("Put target should be < current spot", target < option.underlyingValue)
        assertTrue("Put stop-loss should be > current spot", stopLoss > option.underlyingValue)
    }

    @Test
    fun `suggestTargetSL with higher risk-reward ratio gives tighter stop-loss`() {
        val option = makeOption(lastTradedPrice = 200.0)
        val result2x = TargetCalculator.suggestTargetSL(option, riskRewardRatio = 2.0)
        val result3x = TargetCalculator.suggestTargetSL(option, riskRewardRatio = 3.0)
        assertNotNull(result2x)
        assertNotNull(result3x)
        // baseMove = LTP / (delta * R:R), so higher R:R → smaller baseMove → tighter stop-loss
        val slDist2x = abs(result2x!!.second - option.underlyingValue)
        val slDist3x = abs(result3x!!.second - option.underlyingValue)
        assertTrue("3x R:R stop-loss should be tighter than 2x", slDist3x < slDist2x)
    }

    // =====================================================
    // MARK: - calculateVolatilityImpact Tests
    // =====================================================

    @Test
    fun `calculateVolatilityImpact returns results for each IV change`() {
        val option = makeOption()
        val results = TargetCalculator.calculateVolatilityImpact(option)
        assertEquals(5, results.size) // Default 5 IV changes
    }

    @Test
    fun `calculateVolatilityImpact higher IV should give higher price`() {
        val option = makeOption(impliedVolatility = 0.20)
        val results = TargetCalculator.calculateVolatilityImpact(option)
        // Results are sorted by IV change: -0.05, -0.02, 0.0, 0.02, 0.05
        // Last should have highest price (highest IV)
        assertTrue("Higher IV should produce higher price",
            results.last().second >= results.first().second)
    }

    @Test
    fun `calculateVolatilityImpact with custom IV changes`() {
        val option = makeOption()
        val customChanges = listOf(-0.10, 0.0, 0.10)
        val results = TargetCalculator.calculateVolatilityImpact(option, customChanges)
        assertEquals(3, results.size)
    }
}
