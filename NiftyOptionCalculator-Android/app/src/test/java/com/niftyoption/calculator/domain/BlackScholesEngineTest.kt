package com.niftyoption.calculator.domain

import com.niftyoption.calculator.data.models.OptionType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.exp

class BlackScholesEngineTest {

    // Common test parameters
    private val spotPrice = 24000.0
    private val atmStrike = 24000.0
    private val itmCallStrike = 23500.0
    private val otmCallStrike = 24500.0
    private val iv = 0.15
    private val timeToExpiry = 30.0 / 365.0 // 30 days
    private val riskFreeRate = BlackScholesEngine.DEFAULT_RISK_FREE_RATE
    private val tolerance = 0.01

    // =====================================================
    // MARK: - Call Pricing Tests
    // =====================================================

    @Test
    fun `ATM call should have positive price`() {
        val price = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        assertTrue("ATM call price should be positive, got $price", price > 0)
    }

    @Test
    fun `ITM call should be more expensive than ATM call`() {
        val itmPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = itmCallStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        val atmPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        assertTrue("ITM call ($itmPrice) should be > ATM call ($atmPrice)", itmPrice > atmPrice)
    }

    @Test
    fun `OTM call should be cheaper than ATM call`() {
        val otmPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = otmCallStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        val atmPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        assertTrue("OTM call ($otmPrice) should be < ATM call ($atmPrice)", otmPrice < atmPrice)
    }

    @Test
    fun `call at expiry should equal intrinsic value`() {
        val itmPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = itmCallStrike,
            timeToExpiry = 0.0,
            volatility = iv
        )
        assertEquals(spotPrice - itmCallStrike, itmPrice, tolerance)
    }

    @Test
    fun `OTM call at expiry should be zero`() {
        val otmPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = otmCallStrike,
            timeToExpiry = 0.0,
            volatility = iv
        )
        assertEquals(0.0, otmPrice, tolerance)
    }

    // =====================================================
    // MARK: - Put Pricing Tests
    // =====================================================

    @Test
    fun `ATM put should have positive price`() {
        val price = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        assertTrue("ATM put price should be positive, got $price", price > 0)
    }

    @Test
    fun `ITM put should be more expensive than ATM put`() {
        val itmPutStrike = 24500.0 // Put is ITM when strike > spot
        val itmPrice = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = itmPutStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        val atmPrice = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        assertTrue("ITM put ($itmPrice) should be > ATM put ($atmPrice)", itmPrice > atmPrice)
    }

    @Test
    fun `OTM put should be cheaper than ATM put`() {
        val otmPutStrike = 23500.0 // Put is OTM when strike < spot
        val otmPrice = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = otmPutStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        val atmPrice = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        assertTrue("OTM put ($otmPrice) should be < ATM put ($atmPrice)", otmPrice < atmPrice)
    }

    @Test
    fun `put at expiry ITM should equal intrinsic value`() {
        val itmPutStrike = 24500.0
        val price = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = itmPutStrike,
            timeToExpiry = 0.0,
            volatility = iv
        )
        assertEquals(itmPutStrike - spotPrice, price, tolerance)
    }

    @Test
    fun `OTM put at expiry should be zero`() {
        val otmPutStrike = 23500.0
        val price = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = otmPutStrike,
            timeToExpiry = 0.0,
            volatility = iv
        )
        assertEquals(0.0, price, tolerance)
    }

    // =====================================================
    // MARK: - Put-Call Parity Test
    // =====================================================

    @Test
    fun `put-call parity should hold`() {
        val callPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        val putPrice = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice,
            strikePrice = atmStrike,
            timeToExpiry = timeToExpiry,
            volatility = iv
        )
        // C - P = S - K*e^(-rT)
        val lhs = callPrice - putPrice
        val rhs = spotPrice - atmStrike * exp(-riskFreeRate * timeToExpiry)
        assertEquals(rhs, lhs, 1.0) // Allow small tolerance for numerical precision
    }

    // =====================================================
    // MARK: - calculateOptionPrice dispatch
    // =====================================================

    @Test
    fun `calculateOptionPrice dispatches to call`() {
        val direct = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        val dispatched = BlackScholesEngine.calculateOptionPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv, optionType = OptionType.CALL
        )
        assertEquals(direct, dispatched, tolerance)
    }

    @Test
    fun `calculateOptionPrice dispatches to put`() {
        val direct = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        val dispatched = BlackScholesEngine.calculateOptionPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv, optionType = OptionType.PUT
        )
        assertEquals(direct, dispatched, tolerance)
    }

    // =====================================================
    // MARK: - Call Greeks Tests
    // =====================================================

    @Test
    fun `call delta should be between 0 and 1`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Call delta should be >= 0, got ${greeks.delta}", greeks.delta >= 0)
        assertTrue("Call delta should be <= 1, got ${greeks.delta}", greeks.delta <= 1)
    }

    @Test
    fun `ATM call delta should be near 0_5`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertEquals(0.5, greeks.delta, 0.1) // ATM delta approximately 0.5
    }

    @Test
    fun `call gamma should be positive`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Call gamma should be > 0, got ${greeks.gamma}", greeks.gamma > 0)
    }

    @Test
    fun `call theta should be negative`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Call theta should be < 0, got ${greeks.theta}", greeks.theta < 0)
    }

    @Test
    fun `call vega should be positive`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Call vega should be > 0, got ${greeks.vega}", greeks.vega > 0)
    }

    @Test
    fun `call rho should be positive`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Call rho should be > 0, got ${greeks.rho}", greeks.rho > 0)
    }

    @Test
    fun `deep ITM call delta should be near 1`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = 25000.0, strikePrice = 22000.0,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Deep ITM call delta should be > 0.95, got ${greeks.delta}", greeks.delta > 0.95)
    }

    @Test
    fun `deep OTM call delta should be near 0`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = 22000.0, strikePrice = 25000.0,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Deep OTM call delta should be < 0.05, got ${greeks.delta}", greeks.delta < 0.05)
    }

    // =====================================================
    // MARK: - Put Greeks Tests
    // =====================================================

    @Test
    fun `put delta should be between -1 and 0`() {
        val greeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Put delta should be >= -1, got ${greeks.delta}", greeks.delta >= -1)
        assertTrue("Put delta should be <= 0, got ${greeks.delta}", greeks.delta <= 0)
    }

    @Test
    fun `put gamma should equal call gamma`() {
        val callGreeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        val putGreeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertEquals(callGreeks.gamma, putGreeks.gamma, 0.0001)
    }

    @Test
    fun `put vega should equal call vega`() {
        val callGreeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        val putGreeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertEquals(callGreeks.vega, putGreeks.vega, 0.0001)
    }

    @Test
    fun `put rho should be negative`() {
        val greeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        assertTrue("Put rho should be < 0, got ${greeks.rho}", greeks.rho < 0)
    }

    @Test
    fun `call-put delta relationship should hold`() {
        val callGreeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        val putGreeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        // Call delta - Put delta ≈ 1
        assertEquals(1.0, callGreeks.delta - putGreeks.delta, 0.05)
    }

    // =====================================================
    // MARK: - Greeks at Expiry Edge Cases
    // =====================================================

    @Test
    fun `call greeks at expiry ITM should have delta 1`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = 25000.0, strikePrice = 24000.0,
            timeToExpiry = 0.0, volatility = iv
        )
        assertEquals(1.0, greeks.delta, tolerance)
        assertEquals(0.0, greeks.gamma, tolerance)
        assertEquals(0.0, greeks.theta, tolerance)
        assertEquals(0.0, greeks.vega, tolerance)
    }

    @Test
    fun `call greeks at expiry OTM should have delta 0`() {
        val greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice = 23000.0, strikePrice = 24000.0,
            timeToExpiry = 0.0, volatility = iv
        )
        assertEquals(0.0, greeks.delta, tolerance)
    }

    @Test
    fun `put greeks at expiry ITM should have delta -1`() {
        val greeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice = 23000.0, strikePrice = 24000.0,
            timeToExpiry = 0.0, volatility = iv
        )
        assertEquals(-1.0, greeks.delta, tolerance)
    }

    @Test
    fun `put greeks at expiry OTM should have delta 0`() {
        val greeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice = 25000.0, strikePrice = 24000.0,
            timeToExpiry = 0.0, volatility = iv
        )
        assertEquals(0.0, greeks.delta, tolerance)
    }

    // =====================================================
    // MARK: - calculateGreeks dispatch
    // =====================================================

    @Test
    fun `calculateGreeks dispatches to call greeks`() {
        val direct = BlackScholesEngine.calculateCallGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        val dispatched = BlackScholesEngine.calculateGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv, optionType = OptionType.CALL
        )
        assertEquals(direct.delta, dispatched.delta, tolerance)
        assertEquals(direct.gamma, dispatched.gamma, tolerance)
    }

    @Test
    fun `calculateGreeks dispatches to put greeks`() {
        val direct = BlackScholesEngine.calculatePutGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv
        )
        val dispatched = BlackScholesEngine.calculateGreeks(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = iv, optionType = OptionType.PUT
        )
        assertEquals(direct.delta, dispatched.delta, tolerance)
        assertEquals(direct.gamma, dispatched.gamma, tolerance)
    }

    // =====================================================
    // MARK: - Implied Volatility Tests
    // =====================================================

    @Test
    fun `IV round-trip recovery for call`() {
        val originalIV = 0.20
        val price = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = originalIV
        )
        val recoveredIV = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice = price, spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, isCall = true
        )
        assertNotNull(recoveredIV)
        assertEquals(originalIV, recoveredIV!!, 0.001)
    }

    @Test
    fun `IV round-trip recovery for put`() {
        val originalIV = 0.20
        val price = BlackScholesEngine.calculatePutPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = originalIV
        )
        val recoveredIV = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice = price, spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, isCall = false
        )
        assertNotNull(recoveredIV)
        assertEquals(originalIV, recoveredIV!!, 0.001)
    }

    @Test
    fun `IV recovery for deep OTM call`() {
        val originalIV = 0.25
        val price = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = 25000.0,
            timeToExpiry = timeToExpiry, volatility = originalIV
        )
        val recoveredIV = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice = price, spotPrice = spotPrice, strikePrice = 25000.0,
            timeToExpiry = timeToExpiry, isCall = true
        )
        assertNotNull(recoveredIV)
        assertEquals(originalIV, recoveredIV!!, 0.01)
    }

    @Test
    fun `IV returns null for zero option price`() {
        val result = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice = 0.0, spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, isCall = true
        )
        assertNull(result)
    }

    @Test
    fun `IV returns null for expired option`() {
        val result = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice = 100.0, spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = 0.0, isCall = true
        )
        assertNull(result)
    }

    @Test
    fun `IV result should be bounded between 0_01 and 5_0`() {
        val price = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = 0.30
        )
        val recoveredIV = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice = price, spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, isCall = true
        )
        assertNotNull(recoveredIV)
        assertTrue("IV should be >= 0.01, got $recoveredIV", recoveredIV!! >= 0.01)
        assertTrue("IV should be <= 5.0, got $recoveredIV", recoveredIV <= 5.0)
    }

    // =====================================================
    // MARK: - Utility Method Tests
    // =====================================================

    @Test
    fun `daysToYears should convert correctly`() {
        assertEquals(1.0, BlackScholesEngine.daysToYears(365), tolerance)
        assertEquals(0.5, BlackScholesEngine.daysToYears(182), 0.01)
        assertEquals(0.0, BlackScholesEngine.daysToYears(0), tolerance)
    }

    @Test
    fun `timeToExpiry should calculate days correctly`() {
        val calendar = Calendar.getInstance()
        val now = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, 30)
        val thirtyDaysLater = calendar.time

        val result = BlackScholesEngine.timeToExpiry(thirtyDaysLater, now)
        assertEquals(30.0 / 365.0, result, 0.01)
    }

    @Test
    fun `timeToExpiry should be zero for past date`() {
        val calendar = Calendar.getInstance()
        val now = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -10)
        val pastDate = calendar.time

        val result = BlackScholesEngine.timeToExpiry(pastDate, now)
        assertEquals(0.0, result, tolerance)
    }

    // =====================================================
    // MARK: - Higher IV Tests
    // =====================================================

    @Test
    fun `higher IV should produce higher option price`() {
        val lowIVPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = 0.10
        )
        val highIVPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = timeToExpiry, volatility = 0.30
        )
        assertTrue("Higher IV ($highIVPrice) should produce higher price than lower IV ($lowIVPrice)",
            highIVPrice > lowIVPrice)
    }

    @Test
    fun `longer time to expiry should produce higher option price`() {
        val shortTermPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = 7.0 / 365.0, volatility = iv
        )
        val longTermPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice = spotPrice, strikePrice = atmStrike,
            timeToExpiry = 60.0 / 365.0, volatility = iv
        )
        assertTrue("Longer expiry ($longTermPrice) should produce higher price ($shortTermPrice)",
            longTermPrice > shortTermPrice)
    }

    @Test
    fun `default risk free rate should be 0_07`() {
        assertEquals(0.07, BlackScholesEngine.DEFAULT_RISK_FREE_RATE, tolerance)
    }
}
