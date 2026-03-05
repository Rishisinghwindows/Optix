import XCTest
@testable import NiftyOptionCalculator

final class BlackScholesTests: XCTestCase {

    // MARK: - Black-Scholes Call Pricing Tests

    func testCallPricingITM() {
        let callPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice: 25343,
            strikePrice: 25150,
            timeToExpiry: 7.0 / 365.0,
            volatility: 0.15
        )
        XCTAssertGreaterThan(callPrice, 193, "ITM Call should have price > intrinsic")
        XCTAssertLessThan(callPrice, 400, "Call price should be reasonable")
    }

    func testCallPricingOTM() {
        let callPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice: 25000,
            strikePrice: 25200,
            timeToExpiry: 7.0 / 365.0,
            volatility: 0.15
        )
        XCTAssertGreaterThan(callPrice, 0, "OTM Call should have positive premium")
        XCTAssertLessThan(callPrice, 200, "OTM Call premium should be reasonable")
    }

    func testCallPricingATM() {
        let callPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice: 25000,
            strikePrice: 25000,
            timeToExpiry: 7.0 / 365.0,
            volatility: 0.15
        )
        XCTAssertGreaterThan(callPrice, 0, "ATM Call should have positive premium")
    }

    func testCallPricingAtExpiry() {
        let callPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice: 25343,
            strikePrice: 25150,
            timeToExpiry: 0,
            volatility: 0.15
        )
        let intrinsic = 25343.0 - 25150.0
        XCTAssertEqual(callPrice, intrinsic, accuracy: 1.0, "At expiry, price = intrinsic")
    }

    // MARK: - Put Pricing Tests

    func testPutPricingITM() {
        let putPrice = BlackScholesEngine.calculatePutPrice(
            spotPrice: 25000,
            strikePrice: 25200,
            timeToExpiry: 7.0 / 365.0,
            volatility: 0.15
        )
        XCTAssertGreaterThan(putPrice, 200, "ITM Put should have price > intrinsic")
    }

    func testPutPricingOTM() {
        let putPrice = BlackScholesEngine.calculatePutPrice(
            spotPrice: 25200,
            strikePrice: 25000,
            timeToExpiry: 7.0 / 365.0,
            volatility: 0.15
        )
        XCTAssertGreaterThan(putPrice, 0, "OTM Put should have positive premium")
        XCTAssertLessThan(putPrice, 200, "OTM Put premium should be reasonable")
    }

    // MARK: - Greeks Tests

    func testCallGreeks() {
        let greeks = BlackScholesEngine.calculateCallGreeks(
            spotPrice: 25000,
            strikePrice: 25000,
            timeToExpiry: 7.0 / 365.0,
            volatility: 0.15
        )

        // ATM call delta should be ~0.5
        XCTAssertGreaterThan(greeks.delta, 0.45, "ATM Call delta should be ~0.5")
        XCTAssertLessThan(greeks.delta, 0.55, "ATM Call delta should be ~0.5")

        // Gamma should be positive
        XCTAssertGreaterThan(greeks.gamma, 0, "Gamma should be positive")

        // Theta should be negative
        XCTAssertLessThan(greeks.theta, 0, "Theta should be negative")

        // Vega should be positive
        XCTAssertGreaterThan(greeks.vega, 0, "Vega should be positive")
    }

    func testPutGreeks() {
        let greeks = BlackScholesEngine.calculatePutGreeks(
            spotPrice: 25000,
            strikePrice: 25000,
            timeToExpiry: 7.0 / 365.0,
            volatility: 0.15
        )

        // ATM put delta should be ~-0.5
        XCTAssertGreaterThan(greeks.delta, -0.55, "ATM Put delta should be ~-0.5")
        XCTAssertLessThan(greeks.delta, -0.45, "ATM Put delta should be ~-0.5")
    }

    // MARK: - IV Calculation Tests

    func testImpliedVolatility() {
        let knownIV = 0.15
        let theoreticalPrice = BlackScholesEngine.calculateCallPrice(
            spotPrice: 25000,
            strikePrice: 25000,
            timeToExpiry: 7.0 / 365.0,
            volatility: knownIV
        )

        let calculatedIV = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice: theoreticalPrice,
            spotPrice: 25000,
            strikePrice: 25000,
            timeToExpiry: 7.0 / 365.0,
            isCall: true
        )

        if let iv = calculatedIV {
            XCTAssertEqual(iv, knownIV, accuracy: 0.01, "Should recover original IV")
        }
    }
}

// MARK: - Strategy Tests

final class StrategyTests: XCTestCase {

    func testStrategyTypesExist() {
        let types = StrategyType.allCases
        XCTAssertGreaterThan(types.count, 10, "Should have multiple strategy types")
    }

    func testStrategyLegCreation() {
        let leg = StrategyLeg(
            id: UUID(),
            strikePrice: 25000,
            optionType: .call,
            position: .buy,
            quantity: 1,
            premium: 100,
            delta: 0.5,
            gamma: 0.001,
            theta: -5,
            vega: 10
        )

        XCTAssertEqual(leg.strikePrice, 25000)
        XCTAssertEqual(leg.optionType, .call)
        XCTAssertEqual(leg.position, .buy)
    }

    func testStrategyCreation() {
        let leg = StrategyLeg(
            id: UUID(),
            strikePrice: 25000,
            optionType: .call,
            position: .buy,
            quantity: 1,
            premium: 100,
            delta: 0.5,
            gamma: 0.001,
            theta: -5,
            vega: 10
        )

        let strategy = Strategy(
            type: .longCall,
            legs: [leg],
            lotSize: 50,
            expiryDate: Date().addingTimeInterval(7 * 24 * 60 * 60)
        )

        XCTAssertEqual(strategy.legs.count, 1)
        XCTAssertEqual(strategy.lotSize, 50)
    }

    func testLongCallPayoff() {
        let engine = StrategyCalculationEngine.shared

        let leg = StrategyLeg(
            id: UUID(),
            strikePrice: 25000,
            optionType: .call,
            position: .buy,
            quantity: 1,
            premium: 100,
            delta: 0.5,
            gamma: 0.001,
            theta: -5,
            vega: 10
        )

        let strategy = Strategy(
            type: .longCall,
            legs: [leg],
            lotSize: 50,
            expiryDate: Date().addingTimeInterval(7 * 24 * 60 * 60)
        )

        // At strike price: Loss = premium
        let payoffAtStrike = engine.calculatePayoffAtExpiry(strategy: strategy, atPrice: 25000)
        XCTAssertEqual(payoffAtStrike, -100 * 50, accuracy: 1.0)

        // Above breakeven: Profit
        let payoffAbove = engine.calculatePayoffAtExpiry(strategy: strategy, atPrice: 25200)
        XCTAssertEqual(payoffAbove, 5000, accuracy: 1.0)
    }

    func testPayoffCurveGeneration() {
        let engine = StrategyCalculationEngine.shared

        let leg = StrategyLeg(
            id: UUID(),
            strikePrice: 25000,
            optionType: .call,
            position: .buy,
            quantity: 1,
            premium: 100,
            delta: 0.5,
            gamma: 0.001,
            theta: -5,
            vega: 10
        )

        let strategy = Strategy(
            type: .longCall,
            legs: [leg],
            lotSize: 50,
            expiryDate: Date().addingTimeInterval(7 * 24 * 60 * 60)
        )

        let payoffData = engine.generatePayoffCurve(strategy: strategy, spotPrice: 25000)

        XCTAssertFalse(payoffData.points.isEmpty, "Should generate payoff points")
        XCTAssertGreaterThan(payoffData.points.count, 50, "Should have many points")
    }
}

// MARK: - OI Analysis Tests

final class OIAnalysisTests: XCTestCase {

    func testOIAnalysisEngineExists() {
        let engine = OIAnalysisEngine.shared
        XCTAssertNotNil(engine)
    }

    func testStrikeOIDataPCR() {
        let data = StrikeOIData(
            strikePrice: 25000,
            callOI: 100000,
            putOI: 120000,
            callOIChange: 5000,
            putOIChange: -3000,
            callIV: 0.15,
            putIV: 0.16,
            callLTP: 100,
            putLTP: 80
        )

        XCTAssertEqual(data.pcr, 1.2, accuracy: 0.01, "PCR = Put OI / Call OI")
    }

    func testStrikeOIDataNetChange() {
        let data = StrikeOIData(
            strikePrice: 25000,
            callOI: 100000,
            putOI: 120000,
            callOIChange: 5000,
            putOIChange: -3000,
            callIV: 0.15,
            putIV: 0.16,
            callLTP: 100,
            putLTP: 80
        )

        XCTAssertEqual(data.netOIChange, 2000)
    }

    func testOIChangeDirection() {
        let bothBuilding = StrikeOIData(
            strikePrice: 25000,
            callOI: 100000,
            putOI: 100000,
            callOIChange: 5000,
            putOIChange: 5000,
            callIV: 0.15,
            putIV: 0.16,
            callLTP: 100,
            putLTP: 80
        )
        XCTAssertEqual(bothBuilding.oiChangeDirection, .bothBuilding)
    }

    func testMarketSentimentLabels() {
        XCTAssertFalse(MarketSentiment.bullish.rawValue.isEmpty)
        XCTAssertFalse(MarketSentiment.bearish.rawValue.isEmpty)
        XCTAssertFalse(MarketSentiment.neutral.rawValue.isEmpty)
    }

    func testOIZoneStrengthColors() {
        XCTAssertNotNil(OIZoneStrength.strong.color)
        XCTAssertNotNil(OIZoneStrength.moderate.color)
        XCTAssertNotNil(OIZoneStrength.weak.color)
    }
}
