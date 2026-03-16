package com.optix.app.core.calculation

import com.optix.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StrategySuggestionEngineTest {

    private lateinit var engine: StrategySuggestionEngine
    private val bs = BlackScholesEngine

    @Before
    fun setup() {
        engine = StrategySuggestionEngine(bs)
    }

    /**
     * Build a mock option chain centered around spotPrice
     */
    private fun makeChain(
        spotPrice: Double = 24000.0,
        numStrikes: Int = 11,
        interval: Double = 50.0
    ): OptionChain {
        val atmStrike = (spotPrice / interval).toLong() * interval.toLong()
        val startStrike = atmStrike - ((numStrikes / 2) * interval).toLong()

        val rows = (0 until numStrikes).map { i ->
            val strike = startStrike + i * interval
            val t = 30.0 / 365.0
            val iv = 0.15
            val r = 0.065

            val callResult = bs.calculate(spotPrice, strike.toDouble(), t, iv, r, OptionType.CALL)
            val putResult = bs.calculate(spotPrice, strike.toDouble(), t, iv, r, OptionType.PUT)

            OptionChainRow(
                strikePrice = strike.toDouble(),
                callData = OptionData(
                    strikePrice = strike.toDouble(),
                    optionType = OptionType.CALL,
                    expiry = "2026-03-26",
                    lastPrice = callResult.optionPrice,
                    openInterest = 50000,
                    volume = 1000,
                    impliedVolatility = iv,
                    delta = callResult.delta,
                    gamma = callResult.gamma,
                    theta = callResult.theta,
                    vega = callResult.vega,
                    underlyingSpot = spotPrice
                ),
                putData = OptionData(
                    strikePrice = strike.toDouble(),
                    optionType = OptionType.PUT,
                    expiry = "2026-03-26",
                    lastPrice = putResult.optionPrice,
                    openInterest = 50000,
                    volume = 1000,
                    impliedVolatility = iv,
                    delta = putResult.delta,
                    gamma = putResult.gamma,
                    theta = putResult.theta,
                    vega = putResult.vega,
                    underlyingSpot = spotPrice
                ),
                isATM = kotlin.math.abs(strike - atmStrike) < 1
            )
        }

        return OptionChain(
            index = TradingIndex.NIFTY50,
            spotPrice = spotPrice,
            expiry = "2026-03-26",
            rows = rows,
            atmStrike = atmStrike.toDouble()
        )
    }

    // =====================================================
    // Strategy Selection by Market Conditions
    // =====================================================

    @Test
    fun `bullish low IV suggests long call and bull call spread`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        assertTrue("Should return strategies", results.isNotEmpty())
        val types = results.map { it.strategyType }
        assertTrue("Should include LONG_CALL", types.contains(StrategyType.LONG_CALL))
        assertTrue("Should include BULL_CALL_SPREAD", types.contains(StrategyType.BULL_CALL_SPREAD))
    }

    @Test
    fun `bullish high IV suggests bull put spread`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 75.0
        )
        assertTrue(results.isNotEmpty())
        val types = results.map { it.strategyType }
        assertTrue("Should include BULL_PUT_SPREAD", types.contains(StrategyType.BULL_PUT_SPREAD))
    }

    @Test
    fun `bearish low IV suggests long put and bear put spread`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BEARISH, MarketRegime.TRENDING_BEARISH,
            ivPercentile = 30.0
        )
        assertTrue(results.isNotEmpty())
        val types = results.map { it.strategyType }
        assertTrue("Should include LONG_PUT", types.contains(StrategyType.LONG_PUT))
        assertTrue("Should include BEAR_PUT_SPREAD", types.contains(StrategyType.BEAR_PUT_SPREAD))
    }

    @Test
    fun `bearish high IV suggests bear call spread`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BEARISH, MarketRegime.TRENDING_BEARISH,
            ivPercentile = 75.0
        )
        assertTrue(results.isNotEmpty())
        val types = results.map { it.strategyType }
        assertTrue("Should include BEAR_CALL_SPREAD", types.contains(StrategyType.BEAR_CALL_SPREAD))
    }

    @Test
    fun `neutral high IV suggests iron condor and short strangle`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        assertTrue(results.isNotEmpty())
        val types = results.map { it.strategyType }
        assertTrue("Should include IRON_CONDOR", types.contains(StrategyType.IRON_CONDOR))
    }

    @Test
    fun `neutral low IV suggests long straddle or strangle`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.LOW_VOLATILITY,
            ivPercentile = 25.0
        )
        assertTrue(results.isNotEmpty())
        val types = results.map { it.strategyType }
        val hasVolBuy = types.contains(StrategyType.LONG_STRADDLE) || types.contains(StrategyType.LONG_STRANGLE)
        assertTrue("Should include long straddle or strangle", hasVolBuy)
    }

    // =====================================================
    // Strategy Structure Tests
    // =====================================================

    @Test
    fun `bull call spread has 2 legs BUY and SELL`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        val bcs = results.find { it.strategyType == StrategyType.BULL_CALL_SPREAD }
        assertNotNull(bcs)
        assertEquals(2, bcs!!.legs.size)
        assertTrue(bcs.legs.any { it.position == LegPosition.BUY })
        assertTrue(bcs.legs.any { it.position == LegPosition.SELL })
        assertTrue("Buy leg should have lower strike",
            bcs.legs.first { it.position == LegPosition.BUY }.strikePrice <
                bcs.legs.first { it.position == LegPosition.SELL }.strikePrice)
    }

    @Test
    fun `iron condor has 4 legs`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        val ic = results.find { it.strategyType == StrategyType.IRON_CONDOR }
        assertNotNull("Iron condor should be suggested", ic)
        assertEquals(4, ic!!.legs.size)
        assertEquals(2, ic.legs.count { it.position == LegPosition.BUY })
        assertEquals(2, ic.legs.count { it.position == LegPosition.SELL })
    }

    @Test
    fun `iron condor is credit strategy`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        val ic = results.find { it.strategyType == StrategyType.IRON_CONDOR }
        assertNotNull(ic)
        assertTrue("Iron condor should be credit", ic!!.netPremium > 0)
    }

    @Test
    fun `long straddle has 2 BUY legs at same strike`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.LOW_VOLATILITY,
            ivPercentile = 25.0
        )
        val straddle = results.find { it.strategyType == StrategyType.LONG_STRADDLE }
        assertNotNull(straddle)
        assertEquals(2, straddle!!.legs.size)
        assertTrue(straddle.legs.all { it.position == LegPosition.BUY })
        assertEquals(straddle.legs[0].strikePrice, straddle.legs[1].strikePrice, 0.01)
    }

    @Test
    fun `long call is debit strategy`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        val lc = results.find { it.strategyType == StrategyType.LONG_CALL }
        assertNotNull(lc)
        assertTrue("Long call should be debit (negative netPremium)", lc!!.netPremium < 0)
    }

    // =====================================================
    // Score and Risk
    // =====================================================

    @Test
    fun `results are sorted by score descending`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].score >= results[i + 1].score)
        }
    }

    @Test
    fun `max strategies defaults to 5`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        assertTrue(results.size <= 5)
    }

    @Test
    fun `strategy scores are between 0 and 100`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        results.forEach {
            assertTrue("Score should be >= 0", it.score >= 0)
            assertTrue("Score should be <= 100", it.score <= 100)
        }
    }

    @Test
    fun `unlimited risk strategies have HIGH risk level`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        val strangle = results.find { it.strategyType == StrategyType.SHORT_STRANGLE }
        if (strangle != null) {
            assertEquals(com.optix.app.domain.model.RiskLevel.HIGH, strangle.riskLevel)
        }
    }

    // =====================================================
    // Net Greeks
    // =====================================================

    @Test
    fun `iron condor should have near-zero net delta`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        val ic = results.find { it.strategyType == StrategyType.IRON_CONDOR }
        assertNotNull(ic)
        assertTrue("Iron condor net delta should be near zero",
            kotlin.math.abs(ic!!.netGreeks.delta) < 0.3)
    }

    @Test
    fun `long call should have positive net delta`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        val lc = results.find { it.strategyType == StrategyType.LONG_CALL }
        assertNotNull(lc)
        assertTrue("Long call net delta should be positive", lc!!.netGreeks.delta > 0)
    }

    // =====================================================
    // Breakevens
    // =====================================================

    @Test
    fun `iron condor has two breakevens`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        val ic = results.find { it.strategyType == StrategyType.IRON_CONDOR }
        assertNotNull(ic)
        assertEquals(2, ic!!.breakevens.size)
        assertTrue("Lower BE should be below spot", ic.breakevens.min() < chain.spotPrice)
        assertTrue("Upper BE should be above spot", ic.breakevens.max() > chain.spotPrice)
    }

    @Test
    fun `bull call spread has one breakeven above buy strike`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        val bcs = results.find { it.strategyType == StrategyType.BULL_CALL_SPREAD }
        assertNotNull(bcs)
        assertEquals(1, bcs!!.breakevens.size)
        val buyStrike = bcs.legs.first { it.position == LegPosition.BUY }.strikePrice
        assertTrue("Breakeven should be above buy strike", bcs.breakevens[0] > buyStrike)
    }

    // =====================================================
    // POP (Probability of Profit)
    // =====================================================

    @Test
    fun `POP should be between 0_1 and 0_9`() {
        val chain = makeChain()
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        results.forEach {
            assertTrue("POP ${it.probabilityOfProfit} should be >= 0.1", it.probabilityOfProfit >= 0.1)
            assertTrue("POP ${it.probabilityOfProfit} should be <= 0.9", it.probabilityOfProfit <= 0.9)
        }
    }

    // =====================================================
    // Edge Cases
    // =====================================================

    @Test
    fun `empty option chain returns empty strategies`() {
        val chain = OptionChain(
            index = TradingIndex.NIFTY50,
            spotPrice = 24000.0,
            expiry = "2026-03-26",
            rows = emptyList(),
            atmStrike = 24000.0
        )
        val results = engine.suggestStrategies(
            chain, MarketSentiment.BULLISH, MarketRegime.TRENDING_BULLISH,
            ivPercentile = 30.0
        )
        assertTrue("Empty chain should produce empty results", results.isEmpty())
    }

    @Test
    fun `chain with too few strikes may return fewer strategies`() {
        val chain = makeChain(numStrikes = 3)
        val results = engine.suggestStrategies(
            chain, MarketSentiment.NEUTRAL, MarketRegime.RANGE_BOUND,
            ivPercentile = 75.0
        )
        // Iron condor needs 9 strikes (offset ±4), so with only 3 it should fail
        val ic = results.find { it.strategyType == StrategyType.IRON_CONDOR }
        assertNull("Iron condor should not be built with only 3 strikes", ic)
    }
}
