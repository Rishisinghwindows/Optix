package com.niftyoption.calculator.data.models

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

class OptionDataModelTest {

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
        daysAhead: Int = 30
    ): OptionData {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
        return OptionData(
            strikePrice = strikePrice,
            optionType = optionType,
            expiryDate = calendar.time,
            lastTradedPrice = lastTradedPrice,
            impliedVolatility = impliedVolatility,
            underlyingValue = underlyingValue
        )
    }

    // =====================================================
    // MARK: - OptionType Tests
    // =====================================================

    @Test
    fun `OptionType CALL has correct code and display name`() {
        assertEquals("CE", OptionType.CALL.code)
        assertEquals("Call", OptionType.CALL.displayName)
    }

    @Test
    fun `OptionType PUT has correct code and display name`() {
        assertEquals("PE", OptionType.PUT.code)
        assertEquals("Put", OptionType.PUT.displayName)
    }

    @Test
    fun `OptionType fromCode CE returns CALL`() {
        assertEquals(OptionType.CALL, OptionType.fromCode("CE"))
    }

    @Test
    fun `OptionType fromCode PE returns PUT`() {
        assertEquals(OptionType.PUT, OptionType.fromCode("PE"))
    }

    @Test
    fun `OptionType fromCode unknown defaults to CALL`() {
        assertEquals(OptionType.CALL, OptionType.fromCode("UNKNOWN"))
    }

    // =====================================================
    // MARK: - OptionData Moneyness Tests
    // =====================================================

    @Test
    fun `call ATM when spot equals strike`() {
        val option = makeOption(strikePrice = 24000.0, underlyingValue = 24000.0)
        assertTrue(option.isATM)
        assertEquals("ATM", option.moneyness)
    }

    @Test
    fun `call ATM when spot is within 50 of strike`() {
        val option = makeOption(strikePrice = 24000.0, underlyingValue = 24040.0)
        assertTrue(option.isATM)
        assertEquals("ATM", option.moneyness)
    }

    @Test
    fun `call ITM when spot above strike`() {
        val option = makeOption(
            strikePrice = 23500.0,
            underlyingValue = 24000.0,
            optionType = OptionType.CALL
        )
        assertTrue(option.isITM)
        assertEquals("ITM", option.moneyness)
    }

    @Test
    fun `call OTM when spot below strike`() {
        val option = makeOption(
            strikePrice = 24500.0,
            underlyingValue = 24000.0,
            optionType = OptionType.CALL
        )
        assertFalse(option.isITM)
        assertFalse(option.isATM)
        assertEquals("OTM", option.moneyness)
    }

    @Test
    fun `put ITM when spot below strike`() {
        val option = makeOption(
            strikePrice = 24500.0,
            underlyingValue = 24000.0,
            optionType = OptionType.PUT
        )
        assertTrue(option.isITM)
        assertEquals("ITM", option.moneyness)
    }

    @Test
    fun `put OTM when spot above strike`() {
        val option = makeOption(
            strikePrice = 23500.0,
            underlyingValue = 24000.0,
            optionType = OptionType.PUT
        )
        assertFalse(option.isITM)
        assertFalse(option.isATM)
        assertEquals("OTM", option.moneyness)
    }

    // =====================================================
    // MARK: - OptionData Display Properties
    // =====================================================

    @Test
    fun `displayStrike formats without decimals`() {
        val option = makeOption(strikePrice = 24000.0)
        assertEquals("24000", option.displayStrike)
    }

    @Test
    fun `displayLTP formats with 2 decimals`() {
        val option = makeOption(lastTradedPrice = 200.5)
        assertEquals("200.50", option.displayLTP)
    }

    @Test
    fun `displayIV formats as percentage`() {
        val option = makeOption(impliedVolatility = 0.15)
        assertEquals("15.00%", option.displayIV)
    }

    // =====================================================
    // MARK: - OptionData Time Properties
    // =====================================================

    @Test
    fun `daysToExpiry should be approximately correct`() {
        val option = makeOption(daysAhead = 30)
        // Allow 1 day tolerance due to time-of-day differences
        assertTrue("Days to expiry should be near 30", abs(option.daysToExpiry - 30) <= 1)
    }

    @Test
    fun `timeToExpiryYears should be daysToExpiry divided by 365`() {
        val option = makeOption(daysAhead = 365)
        assertEquals(1.0, option.timeToExpiryYears, 0.01)
    }

    // =====================================================
    // MARK: - OptionChainRow Tests
    // =====================================================

    @Test
    fun `OptionChainRow displayStrike formats correctly`() {
        val row = OptionChainRow(
            strikePrice = 24000.0,
            callOption = null,
            putOption = null
        )
        assertEquals("24000", row.displayStrike)
    }

    @Test
    fun `OptionChainRow can hold both call and put`() {
        val call = makeOption(optionType = OptionType.CALL)
        val put = makeOption(optionType = OptionType.PUT)
        val row = OptionChainRow(
            strikePrice = 24000.0,
            callOption = call,
            putOption = put
        )
        assertNotNull(row.callOption)
        assertNotNull(row.putOption)
        assertEquals(OptionType.CALL, row.callOption!!.optionType)
        assertEquals(OptionType.PUT, row.putOption!!.optionType)
    }

    // =====================================================
    // MARK: - GreeksResult Tests
    // =====================================================

    @Test
    fun `GreeksResult display formatting`() {
        val greeks = GreeksResult(
            delta = 0.5234,
            gamma = 0.000123,
            theta = -5.67,
            vega = 12.34,
            rho = 3.45
        )
        assertEquals("0.5234", greeks.displayDelta)
        assertEquals("0.000123", greeks.displayGamma)
        assertEquals("-5.67", greeks.displayTheta)
        assertEquals("12.34", greeks.displayVega)
        assertEquals("3.45", greeks.displayRho)
    }

    @Test
    fun `GreeksResult deltaInterpretation deep ITM`() {
        val greeks = GreeksResult(delta = 0.85, gamma = 0.0, theta = 0.0, vega = 0.0, rho = 0.0)
        assertEquals("Deep ITM - moves almost 1:1 with underlying", greeks.deltaInterpretation)
    }

    @Test
    fun `GreeksResult deltaInterpretation ATM`() {
        val greeks = GreeksResult(delta = 0.50, gamma = 0.0, theta = 0.0, vega = 0.0, rho = 0.0)
        assertEquals("ATM - 50% chance of expiring ITM", greeks.deltaInterpretation)
    }

    @Test
    fun `GreeksResult deltaInterpretation deep OTM`() {
        val greeks = GreeksResult(delta = 0.10, gamma = 0.0, theta = 0.0, vega = 0.0, rho = 0.0)
        assertEquals("Deep OTM - low probability of profit", greeks.deltaInterpretation)
    }

    @Test
    fun `GreeksResult deltaInterpretation moderate`() {
        val greeks = GreeksResult(delta = 0.35, gamma = 0.0, theta = 0.0, vega = 0.0, rho = 0.0)
        assertEquals("Moderate sensitivity to underlying movement", greeks.deltaInterpretation)
    }

    @Test
    fun `GreeksResult thetaInterpretation high decay`() {
        val greeks = GreeksResult(delta = 0.0, gamma = 0.0, theta = -6.0, vega = 0.0, rho = 0.0)
        assertEquals("High time decay - losing value rapidly", greeks.thetaInterpretation)
    }

    @Test
    fun `GreeksResult thetaInterpretation moderate decay`() {
        val greeks = GreeksResult(delta = 0.0, gamma = 0.0, theta = -3.0, vega = 0.0, rho = 0.0)
        assertEquals("Moderate time decay", greeks.thetaInterpretation)
    }

    @Test
    fun `GreeksResult thetaInterpretation low decay`() {
        val greeks = GreeksResult(delta = 0.0, gamma = 0.0, theta = -1.0, vega = 0.0, rho = 0.0)
        assertEquals("Low time decay", greeks.thetaInterpretation)
    }

    @Test
    fun `GreeksResult vegaInterpretation high`() {
        val greeks = GreeksResult(delta = 0.0, gamma = 0.0, theta = 0.0, vega = 15.0, rho = 0.0)
        assertEquals("High volatility sensitivity", greeks.vegaInterpretation)
    }

    @Test
    fun `GreeksResult vegaInterpretation moderate`() {
        val greeks = GreeksResult(delta = 0.0, gamma = 0.0, theta = 0.0, vega = 7.0, rho = 0.0)
        assertEquals("Moderate volatility sensitivity", greeks.vegaInterpretation)
    }

    @Test
    fun `GreeksResult vegaInterpretation low`() {
        val greeks = GreeksResult(delta = 0.0, gamma = 0.0, theta = 0.0, vega = 3.0, rho = 0.0)
        assertEquals("Low volatility sensitivity", greeks.vegaInterpretation)
    }

    @Test
    fun `GreeksResult expectedPriceChange includes delta and gamma`() {
        val greeks = GreeksResult(delta = 0.5, gamma = 0.001, theta = 0.0, vega = 0.0, rho = 0.0)
        val priceChange = greeks.expectedPriceChange(100.0)
        // 0.5*100 + 0.5*0.001*100*100 = 50 + 5 = 55
        assertEquals(55.0, priceChange, tolerance)
    }

    @Test
    fun `GreeksResult expectedPrice includes time decay`() {
        val greeks = GreeksResult(delta = 0.5, gamma = 0.0, theta = -5.0, vega = 0.0, rho = 0.0)
        val expectedPrice = greeks.expectedPrice(
            currentPrice = 200.0,
            underlyingMove = 100.0,
            daysElapsed = 2.0
        )
        // 200 + 0.5*100 + 0 + (-5)*2 = 200 + 50 - 10 = 240
        assertEquals(240.0, expectedPrice, tolerance)
    }

    @Test
    fun `GreeksResult expectedPrice never goes below zero`() {
        val greeks = GreeksResult(delta = -0.5, gamma = 0.0, theta = -10.0, vega = 0.0, rho = 0.0)
        val expectedPrice = greeks.expectedPrice(
            currentPrice = 10.0,
            underlyingMove = 100.0,
            daysElapsed = 10.0
        )
        // 10 + (-0.5)*100 + (-10)*10 = 10 - 50 - 100 = -140 → clamped to 0
        assertEquals(0.0, expectedPrice, tolerance)
    }

    // =====================================================
    // MARK: - TargetCalculation Tests
    // =====================================================

    @Test
    fun `TargetCalculation computed properties`() {
        val greeks = GreeksResult(delta = 0.5, gamma = 0.001, theta = -5.0, vega = 10.0, rho = 3.0)
        val calc = TargetCalculation(
            optionType = OptionType.CALL,
            strikePrice = 24000.0,
            currentSpot = 24000.0,
            currentOptionPrice = 200.0,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0,
            targetOptionPrice = 450.0,
            stopLossOptionPrice = 80.0,
            greeks = greeks,
            impliedVolatility = 0.15,
            daysToExpiry = 30
        )

        assertEquals(250.0, calc.targetProfit, tolerance) // 450 - 200
        assertEquals(120.0, calc.stopLossLoss, tolerance) // 200 - 80
        assertEquals(250.0 / 120.0, calc.riskRewardRatio, tolerance) // ~2.08
        assertTrue(calc.isGoodRiskReward) // > 1.5
        assertEquals(125.0, calc.profitPercentage, tolerance) // (250/200)*100
        assertEquals(60.0, calc.lossPercentage, tolerance) // (120/200)*100
    }

    @Test
    fun `TargetCalculation display strings`() {
        val greeks = GreeksResult(delta = 0.5, gamma = 0.0, theta = 0.0, vega = 0.0, rho = 0.0)
        val calc = TargetCalculation(
            optionType = OptionType.CALL,
            strikePrice = 24000.0,
            currentSpot = 24000.0,
            currentOptionPrice = 200.0,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0,
            targetOptionPrice = 450.0,
            stopLossOptionPrice = 80.0,
            greeks = greeks,
            impliedVolatility = 0.15,
            daysToExpiry = 30
        )

        assertEquals("450.00", calc.displayTargetPrice)
        assertEquals("80.00", calc.displayStopLossPrice)
        assertEquals("+250.00", calc.displayProfit)
        assertEquals("-120.00", calc.displayLoss)
        assertTrue(calc.displayRiskReward.startsWith("1:"))
        assertEquals("+125.0%", calc.displayProfitPercentage)
        assertEquals("-60.0%", calc.displayLossPercentage)
    }

    @Test
    fun `TargetCalculation riskRewardRatio zero when no loss`() {
        val greeks = GreeksResult(delta = 0.5, gamma = 0.0, theta = 0.0, vega = 0.0, rho = 0.0)
        val calc = TargetCalculation(
            optionType = OptionType.CALL,
            strikePrice = 24000.0,
            currentSpot = 24000.0,
            currentOptionPrice = 200.0,
            targetSpot = 24500.0,
            stopLossSpot = 23500.0,
            targetOptionPrice = 450.0,
            stopLossOptionPrice = 200.0, // No loss
            greeks = greeks,
            impliedVolatility = 0.15,
            daysToExpiry = 30
        )
        assertEquals(0.0, calc.stopLossLoss, tolerance)
        assertEquals(0.0, calc.riskRewardRatio, tolerance)
    }

    @Test
    fun `TargetCalculation isGoodRiskReward false when below 1_5`() {
        val greeks = GreeksResult(delta = 0.5, gamma = 0.0, theta = 0.0, vega = 0.0, rho = 0.0)
        val calc = TargetCalculation(
            optionType = OptionType.CALL,
            strikePrice = 24000.0,
            currentSpot = 24000.0,
            currentOptionPrice = 200.0,
            targetSpot = 24200.0,
            stopLossSpot = 23500.0,
            targetOptionPrice = 250.0, // 50 profit
            stopLossOptionPrice = 80.0, // 120 loss → R:R = 50/120 = 0.42
            greeks = greeks,
            impliedVolatility = 0.15,
            daysToExpiry = 30
        )
        assertFalse(calc.isGoodRiskReward)
    }

    // =====================================================
    // MARK: - LoadingState Tests
    // =====================================================

    @Test
    fun `LoadingState Idle is correct type`() {
        val state: LoadingState = LoadingState.Idle
        assertTrue(state is LoadingState.Idle)
    }

    @Test
    fun `LoadingState Loading is correct type`() {
        val state: LoadingState = LoadingState.Loading
        assertTrue(state is LoadingState.Loading)
    }

    @Test
    fun `LoadingState Loaded is correct type`() {
        val state: LoadingState = LoadingState.Loaded
        assertTrue(state is LoadingState.Loaded)
    }

    @Test
    fun `LoadingState Error carries message`() {
        val state: LoadingState = LoadingState.Error("Something went wrong")
        assertTrue(state is LoadingState.Error)
        assertEquals("Something went wrong", (state as LoadingState.Error).message)
    }
}
