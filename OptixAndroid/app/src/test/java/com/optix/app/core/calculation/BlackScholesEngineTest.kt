package com.optix.app.core.calculation

import com.optix.app.domain.model.OptionType
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class BlackScholesEngineTest {

    private val spot = 24000.0
    private val atm = 24000.0
    private val iv = 0.15
    private val t = 30.0 / 365.0
    private val r = 0.065
    private val tol = 0.01

    // =====================================================
    // Call Pricing
    // =====================================================

    @Test
    fun `ATM call should have positive price`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue("ATM call price should be positive", result.optionPrice > 0)
    }

    @Test
    fun `ITM call should be more expensive than ATM call`() {
        val itm = BlackScholesEngine.calculate(spot, 23500.0, t, iv, r, OptionType.CALL)
        val atmResult = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(itm.optionPrice > atmResult.optionPrice)
    }

    @Test
    fun `OTM call should be cheaper than ATM call`() {
        val otm = BlackScholesEngine.calculate(spot, 24500.0, t, iv, r, OptionType.CALL)
        val atmResult = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(otm.optionPrice < atmResult.optionPrice)
    }

    @Test
    fun `call at expiry ITM returns intrinsic value`() {
        val result = BlackScholesEngine.calculate(spot, 23500.0, 0.0, iv, r, OptionType.CALL)
        assertEquals(500.0, result.optionPrice, tol)
    }

    @Test
    fun `call at expiry OTM returns zero`() {
        val result = BlackScholesEngine.calculate(spot, 24500.0, 0.0, iv, r, OptionType.CALL)
        assertEquals(0.0, result.optionPrice, tol)
    }

    // =====================================================
    // Put Pricing
    // =====================================================

    @Test
    fun `ATM put should have positive price`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        assertTrue(result.optionPrice > 0)
    }

    @Test
    fun `ITM put should be more expensive than ATM put`() {
        val itm = BlackScholesEngine.calculate(spot, 24500.0, t, iv, r, OptionType.PUT)
        val atmResult = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        assertTrue(itm.optionPrice > atmResult.optionPrice)
    }

    @Test
    fun `put at expiry ITM returns intrinsic value`() {
        val result = BlackScholesEngine.calculate(spot, 24500.0, 0.0, iv, r, OptionType.PUT)
        assertEquals(500.0, result.optionPrice, tol)
    }

    @Test
    fun `put at expiry OTM returns zero`() {
        val result = BlackScholesEngine.calculate(spot, 23500.0, 0.0, iv, r, OptionType.PUT)
        assertEquals(0.0, result.optionPrice, tol)
    }

    // =====================================================
    // Put-Call Parity
    // =====================================================

    @Test
    fun `put-call parity should hold`() {
        val call = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        val put = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        val lhs = call.optionPrice - put.optionPrice
        val rhs = spot - atm * exp(-r * t)
        assertEquals(rhs, lhs, 1.0)
    }

    // =====================================================
    // Call Greeks
    // =====================================================

    @Test
    fun `call delta should be between 0 and 1`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(result.delta in 0.0..1.0)
    }

    @Test
    fun `ATM call delta should be near 0_5`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertEquals(0.5, result.delta, 0.1)
    }

    @Test
    fun `call gamma should be positive`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(result.gamma > 0)
    }

    @Test
    fun `call theta should be negative`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue("Call theta should be < 0, got ${result.theta}", result.theta < 0)
    }

    @Test
    fun `call vega should be positive`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(result.vega > 0)
    }

    @Test
    fun `call rho should be positive`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(result.rho > 0)
    }

    @Test
    fun `deep ITM call delta should be near 1`() {
        val result = BlackScholesEngine.calculate(25000.0, 22000.0, t, iv, r, OptionType.CALL)
        assertTrue(result.delta > 0.95)
    }

    @Test
    fun `deep OTM call delta should be near 0`() {
        val result = BlackScholesEngine.calculate(22000.0, 25000.0, t, iv, r, OptionType.CALL)
        assertTrue(result.delta < 0.05)
    }

    // =====================================================
    // Put Greeks
    // =====================================================

    @Test
    fun `put delta should be between -1 and 0`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        assertTrue(result.delta in -1.0..0.0)
    }

    @Test
    fun `put gamma should equal call gamma`() {
        val call = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        val put = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        assertEquals(call.gamma, put.gamma, 0.0001)
    }

    @Test
    fun `put vega should equal call vega`() {
        val call = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        val put = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        assertEquals(call.vega, put.vega, 0.0001)
    }

    @Test
    fun `put rho should be negative`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        assertTrue(result.rho < 0)
    }

    @Test
    fun `call-put delta relationship`() {
        val call = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        val put = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        assertEquals(1.0, call.delta - put.delta, 0.05)
    }

    // =====================================================
    // Greeks at Expiry
    // =====================================================

    @Test
    fun `ITM call at expiry has delta 1`() {
        val result = BlackScholesEngine.calculate(25000.0, 24000.0, 0.0, iv, r, OptionType.CALL)
        assertEquals(1.0, result.delta, tol)
    }

    @Test
    fun `OTM call at expiry has delta 0`() {
        val result = BlackScholesEngine.calculate(23000.0, 24000.0, 0.0, iv, r, OptionType.CALL)
        assertEquals(0.0, result.delta, tol)
    }

    @Test
    fun `ITM put at expiry has delta -1`() {
        val result = BlackScholesEngine.calculate(23000.0, 24000.0, 0.0, iv, r, OptionType.PUT)
        assertEquals(-1.0, result.delta, tol)
    }

    @Test
    fun `OTM put at expiry has delta 0`() {
        val result = BlackScholesEngine.calculate(25000.0, 24000.0, 0.0, iv, r, OptionType.PUT)
        assertEquals(0.0, result.delta, tol)
    }

    // =====================================================
    // Intrinsic and Time Value
    // =====================================================

    @Test
    fun `ATM call intrinsic value is zero`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertEquals(0.0, result.intrinsicValue, tol)
    }

    @Test
    fun `ITM call intrinsic value equals spot minus strike`() {
        val result = BlackScholesEngine.calculate(spot, 23500.0, t, iv, r, OptionType.CALL)
        assertEquals(500.0, result.intrinsicValue, tol)
    }

    @Test
    fun `time value should be positive for non-expired option`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(result.timeValue > 0)
    }

    @Test
    fun `time value should be zero at expiry`() {
        val result = BlackScholesEngine.calculate(spot, 23500.0, 0.0, iv, r, OptionType.CALL)
        assertEquals(0.0, result.timeValue, tol)
    }

    // =====================================================
    // calculateOptionPrice and calculateGreeks wrappers
    // =====================================================

    @Test
    fun `calculateOptionPrice matches calculate for call`() {
        val direct = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        val wrapper = BlackScholesEngine.calculateOptionPrice(spot, atm, t, r, iv, isCall = true)
        assertEquals(direct.optionPrice, wrapper, tol)
    }

    @Test
    fun `calculateOptionPrice matches calculate for put`() {
        val direct = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.PUT)
        val wrapper = BlackScholesEngine.calculateOptionPrice(spot, atm, t, r, iv, isCall = false)
        assertEquals(direct.optionPrice, wrapper, tol)
    }

    @Test
    fun `calculateGreeks matches calculate`() {
        val direct = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        val wrapper = BlackScholesEngine.calculateGreeks(spot, atm, t, r, iv, isCall = true)
        assertEquals(direct.delta, wrapper.delta, tol)
        assertEquals(direct.gamma, wrapper.gamma, tol)
        assertEquals(direct.optionPrice, wrapper.optionPrice, tol)
    }

    // =====================================================
    // Implied Volatility
    // =====================================================

    @Test
    fun `IV round-trip recovery for call`() {
        val originalIV = 0.20
        val price = BlackScholesEngine.calculateOptionPrice(spot, atm, t, r, originalIV, isCall = true)
        val recovered = BlackScholesEngine.calculateIV(price, spot, atm, t, r, OptionType.CALL)
        assertEquals(originalIV, recovered, 0.005)
    }

    @Test
    fun `IV round-trip recovery for put`() {
        val originalIV = 0.20
        val price = BlackScholesEngine.calculateOptionPrice(spot, atm, t, r, originalIV, isCall = false)
        val recovered = BlackScholesEngine.calculateIV(price, spot, atm, t, r, OptionType.PUT)
        assertEquals(originalIV, recovered, 0.005)
    }

    @Test
    fun `IV returns 0 for zero option price`() {
        val result = BlackScholesEngine.calculateIV(0.0, spot, atm, t, r, OptionType.CALL)
        assertEquals(0.0, result, tol)
    }

    @Test
    fun `IV returns 0 for expired option`() {
        val result = BlackScholesEngine.calculateIV(100.0, spot, atm, 0.0, r, OptionType.CALL)
        assertEquals(0.0, result, tol)
    }

    @Test
    fun `IV result should be bounded`() {
        val price = BlackScholesEngine.calculateOptionPrice(spot, atm, t, r, 0.30, isCall = true)
        val recovered = BlackScholesEngine.calculateIV(price, spot, atm, t, r, OptionType.CALL)
        assertTrue(recovered in 0.01..5.0)
    }

    // =====================================================
    // IV and Time Sensitivity
    // =====================================================

    @Test
    fun `higher IV produces higher price`() {
        val low = BlackScholesEngine.calculateOptionPrice(spot, atm, t, r, 0.10, isCall = true)
        val high = BlackScholesEngine.calculateOptionPrice(spot, atm, t, r, 0.30, isCall = true)
        assertTrue(high > low)
    }

    @Test
    fun `longer time produces higher price`() {
        val short = BlackScholesEngine.calculateOptionPrice(spot, atm, 7.0 / 365.0, r, iv, isCall = true)
        val long = BlackScholesEngine.calculateOptionPrice(spot, atm, 60.0 / 365.0, r, iv, isCall = true)
        assertTrue(long > short)
    }

    // =====================================================
    // GreeksResult display properties
    // =====================================================

    @Test
    fun `GreeksResult display formatting`() {
        val result = BlackScholesEngine.calculate(spot, atm, t, iv, r, OptionType.CALL)
        assertTrue(result.deltaDisplay.isNotEmpty())
        assertTrue(result.gammaDisplay.isNotEmpty())
        assertTrue(result.thetaDisplay.isNotEmpty())
        assertTrue(result.vegaDisplay.isNotEmpty())
        assertTrue(result.priceDisplay.startsWith("₹"))
    }

    @Test
    fun `GreeksResult EMPTY has zero values`() {
        val empty = com.optix.app.domain.model.GreeksResult.EMPTY
        assertEquals(0.0, empty.optionPrice, tol)
        assertEquals(0.0, empty.delta, tol)
        assertEquals(0.0, empty.gamma, tol)
    }
}
