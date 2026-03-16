package com.optix.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class OptionTypeTest {

    @Test
    fun `CALL has correct code and displayName`() {
        assertEquals("CE", OptionType.CALL.code)
        assertEquals("Call", OptionType.CALL.displayName)
    }

    @Test
    fun `PUT has correct code and displayName`() {
        assertEquals("PE", OptionType.PUT.code)
        assertEquals("Put", OptionType.PUT.displayName)
    }
}

class OptionDataTest {

    private fun makeOption(
        strike: Double = 24000.0,
        type: OptionType = OptionType.CALL,
        spot: Double = 24000.0
    ) = OptionData(
        strikePrice = strike,
        optionType = type,
        expiry = "2026-03-26",
        lastPrice = 200.0,
        underlyingSpot = spot,
        impliedVolatility = 0.15
    )

    @Test
    fun `call ATM when spot near strike`() {
        val option = makeOption(strike = 24000.0, spot = 24000.0)
        assertTrue(option.isATM)
        assertEquals("ATM", option.moneyness)
    }

    @Test
    fun `call ITM when spot above strike`() {
        val option = makeOption(strike = 23500.0, spot = 24000.0)
        assertTrue(option.isITM)
        assertFalse(option.isOTM)
        assertEquals("ITM", option.moneyness)
    }

    @Test
    fun `call OTM when spot below strike`() {
        val option = makeOption(strike = 24500.0, spot = 24000.0)
        assertFalse(option.isITM)
        assertTrue(option.isOTM)
        assertEquals("OTM", option.moneyness)
    }

    @Test
    fun `put ITM when spot below strike`() {
        val option = makeOption(strike = 24500.0, spot = 24000.0, type = OptionType.PUT)
        assertTrue(option.isITM)
        assertEquals("ITM", option.moneyness)
    }

    @Test
    fun `put OTM when spot above strike`() {
        val option = makeOption(strike = 23500.0, spot = 24000.0, type = OptionType.PUT)
        assertTrue(option.isOTM)
        assertEquals("OTM", option.moneyness)
    }
}

class GreeksTest {

    @Test
    fun `Greeks EMPTY has all zero values`() {
        val empty = Greeks.EMPTY
        assertEquals(0.0, empty.delta, 0.001)
        assertEquals(0.0, empty.gamma, 0.001)
        assertEquals(0.0, empty.theta, 0.001)
        assertEquals(0.0, empty.vega, 0.001)
        assertEquals(0.0, empty.rho, 0.001)
    }

    @Test
    fun `Greeks stores values correctly`() {
        val g = Greeks(delta = 0.5, gamma = 0.001, theta = -5.0, vega = 10.0, rho = 3.0)
        assertEquals(0.5, g.delta, 0.001)
        assertEquals(0.001, g.gamma, 0.0001)
        assertEquals(-5.0, g.theta, 0.001)
        assertEquals(10.0, g.vega, 0.001)
        assertEquals(3.0, g.rho, 0.001)
    }
}

class GreeksResultTest {

    @Test
    fun `flat accessors delegate to greeks`() {
        val result = GreeksResult(
            optionPrice = 200.0,
            greeks = Greeks(delta = 0.5, gamma = 0.001, theta = -5.0, vega = 10.0, rho = 3.0)
        )
        assertEquals(0.5, result.delta, 0.001)
        assertEquals(0.001, result.gamma, 0.0001)
        assertEquals(-5.0, result.theta, 0.001)
        assertEquals(10.0, result.vega, 0.001)
        assertEquals(3.0, result.rho, 0.001)
    }

    @Test
    fun `display formatting works`() {
        val result = GreeksResult(
            optionPrice = 200.0,
            greeks = Greeks(delta = 0.5234, gamma = 0.000123, theta = -5.67, vega = 12.34, rho = 3.45)
        )
        assertEquals("0.5234", result.deltaDisplay)
        assertEquals("0.000123", result.gammaDisplay)
        assertEquals("-5.6700", result.thetaDisplay)
        assertEquals("12.3400", result.vegaDisplay)
        assertEquals("3.4500", result.rhoDisplay)
        assertEquals("₹200.00", result.priceDisplay)
    }

    @Test
    fun `EMPTY has zero option price`() {
        val empty = GreeksResult.EMPTY
        assertEquals(0.0, empty.optionPrice, 0.001)
    }
}

class StrategyTypeTest {

    @Test
    fun `LONG_CALL is single leg`() {
        assertEquals(1, StrategyType.LONG_CALL.legCount)
        assertEquals(StrategyCategory.SINGLE_LEG, StrategyType.LONG_CALL.category)
    }

    @Test
    fun `IRON_CONDOR is 4 leg multi-leg`() {
        assertEquals(4, StrategyType.IRON_CONDOR.legCount)
        assertEquals(StrategyCategory.MULTI_LEG, StrategyType.IRON_CONDOR.category)
    }

    @Test
    fun `BULL_CALL_SPREAD is vertical spread with 2 legs`() {
        assertEquals(2, StrategyType.BULL_CALL_SPREAD.legCount)
        assertEquals(StrategyCategory.VERTICAL_SPREAD, StrategyType.BULL_CALL_SPREAD.category)
    }

    @Test
    fun `all strategy types have non-empty display names`() {
        StrategyType.entries.forEach {
            assertTrue("${it.name} should have displayName", it.displayName.isNotEmpty())
            assertTrue("${it.name} should have description", it.description.isNotEmpty())
        }
    }

    @Test
    fun `there are 23 strategy types`() {
        assertEquals(23, StrategyType.entries.size)
    }
}

class StrategyLegTest {

    @Test
    fun `BUY leg isLong and not isShort`() {
        val leg = StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.BUY, 1, 200.0)
        assertTrue(leg.isLong)
        assertFalse(leg.isShort)
    }

    @Test
    fun `SELL leg isShort and not isLong`() {
        val leg = StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.SELL, 1, 200.0)
        assertFalse(leg.isLong)
        assertTrue(leg.isShort)
    }

    @Test
    fun `BUY leg netPremium is negative`() {
        val leg = StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.BUY, 1, 200.0)
        assertEquals(-200.0, leg.netPremium, 0.01)
    }

    @Test
    fun `SELL leg netPremium is positive`() {
        val leg = StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.SELL, 1, 200.0)
        assertEquals(200.0, leg.netPremium, 0.01)
    }

    @Test
    fun `positionText format`() {
        val leg = StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.BUY, 2, 200.0)
        assertEquals("Buy 2 Call @ 24000.0", leg.positionText)
    }
}

class StrategyTest {

    private fun makeStrategy(legs: List<StrategyLeg>) = Strategy(
        id = "test",
        type = StrategyType.BULL_CALL_SPREAD,
        symbol = "NIFTY",
        expiry = "2026-03-26",
        legs = legs,
        spotPrice = 24000.0
    )

    @Test
    fun `netPremium sums all leg premiums`() {
        val legs = listOf(
            StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.BUY, 1, 200.0),
            StrategyLeg("2", OptionType.CALL, 24100.0, LegPosition.SELL, 1, 150.0)
        )
        val strategy = makeStrategy(legs)
        assertEquals(-50.0, strategy.netPremium, 0.01) // -200 + 150
    }

    @Test
    fun `debit strategy when netPremium negative`() {
        val legs = listOf(
            StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.BUY, 1, 200.0),
            StrategyLeg("2", OptionType.CALL, 24100.0, LegPosition.SELL, 1, 150.0)
        )
        val strategy = makeStrategy(legs)
        assertTrue(strategy.isDebit)
        assertFalse(strategy.isCredit)
    }

    @Test
    fun `credit strategy when netPremium positive`() {
        val legs = listOf(
            StrategyLeg("1", OptionType.PUT, 24000.0, LegPosition.SELL, 1, 200.0),
            StrategyLeg("2", OptionType.PUT, 23900.0, LegPosition.BUY, 1, 150.0)
        )
        val strategy = makeStrategy(legs)
        assertTrue(strategy.isCredit)
        assertFalse(strategy.isDebit)
    }

    @Test
    fun `net Greeks computed from legs`() {
        val g1 = Greeks(delta = 0.5, gamma = 0.01, theta = -5.0, vega = 10.0)
        val g2 = Greeks(delta = 0.3, gamma = 0.008, theta = -4.0, vega = 8.0)
        val legs = listOf(
            StrategyLeg("1", OptionType.CALL, 24000.0, LegPosition.BUY, 1, 200.0, g1),
            StrategyLeg("2", OptionType.CALL, 24100.0, LegPosition.SELL, 1, 150.0, g2)
        )
        val strategy = makeStrategy(legs)
        // BUY: +0.5, SELL: -0.3 = 0.2
        assertEquals(0.2, strategy.netDelta, 0.01)
        assertEquals(0.002, strategy.netGamma, 0.001)
        assertEquals(-1.0, strategy.netTheta, 0.01) // -5 - (-4) = -1
        assertEquals(2.0, strategy.netVega, 0.01)
    }
}

class MarketEnumsTest {

    @Test
    fun `ConfidenceLevel has 3 values`() {
        assertEquals(3, ConfidenceLevel.entries.size)
    }

    @Test
    fun `MarketSentiment has 3 values`() {
        assertEquals(3, MarketSentiment.entries.size)
        assertEquals("Bullish", MarketSentiment.BULLISH.displayName)
    }

    @Test
    fun `MarketBias has 6 values`() {
        assertEquals(6, MarketBias.entries.size)
    }

    @Test
    fun `TradeDirection has 5 values`() {
        assertEquals(5, TradeDirection.entries.size)
        assertEquals("STRONG BUY", TradeDirection.STRONG_BUY.displayText)
    }

    @Test
    fun `RiskLevel values`() {
        assertEquals(3, RiskLevel.entries.size)
    }

    @Test
    fun `MarketRegime has 5 values`() {
        assertEquals(5, MarketRegime.entries.size)
    }
}

class PayoffDataTest {

    @Test
    fun `limited risk when maxLoss is finite`() {
        val data = PayoffData(
            points = emptyList(), maxProfit = 500.0, maxLoss = 200.0,
            breakevens = listOf(24100.0), currentSpot = 24000.0, riskRewardRatio = 2.5
        )
        assertTrue(data.isLimitedRisk)
        assertTrue(data.isLimitedProfit)
    }
}

class ExpiryDateTest {

    @Test
    fun `displayString returns displayDate`() {
        val expiry = ExpiryDate("2026-03-26", "26 Mar", 23, isWeekly = true)
        assertEquals("26 Mar", expiry.displayString)
        assertEquals("26 Mar", expiry.shortString)
    }
}

class OptionChainRowTest {

    @Test
    fun `can hold both call and put`() {
        val row = OptionChainRow(
            strikePrice = 24000.0,
            callData = OptionData(24000.0, OptionType.CALL, "2026-03-26", 200.0),
            putData = OptionData(24000.0, OptionType.PUT, "2026-03-26", 180.0)
        )
        assertNotNull(row.callData)
        assertNotNull(row.putData)
        assertEquals(OptionType.CALL, row.callData!!.optionType)
        assertEquals(OptionType.PUT, row.putData!!.optionType)
    }
}
