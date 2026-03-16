import XCTest
@testable import NiftyOptionCalculator

// MARK: - Helper Functions

private func makeOption(
    strike: Double,
    type: OptionType,
    ltp: Double,
    oi: Int = 200000,
    oiChange: Int = 5000,
    iv: Double = 0.15,
    volume: Int = 50000,
    delta: Double? = nil,
    gamma: Double? = nil,
    theta: Double? = nil,
    vega: Double? = nil,
    spot: Double = 22000
) -> OptionData {
    OptionData(
        strikePrice: strike,
        optionType: type,
        expiryDate: Date().addingTimeInterval(14 * 24 * 3600),
        lastTradedPrice: ltp,
        openInterest: oi,
        changeInOI: oiChange,
        impliedVolatility: iv,
        bidPrice: ltp - 1,
        askPrice: ltp + 1,
        volume: volume,
        underlyingValue: spot,
        delta: delta,
        gamma: gamma,
        theta: theta,
        vega: vega
    )
}

private func makeChain(spot: Double = 22000, numStrikes: Int = 21, interval: Double = 50) -> [OptionChainRow] {
    let baseStrike = (spot / interval).rounded() * interval
    let halfRange = numStrikes / 2

    return (0..<numStrikes).map { i in
        let strike = baseStrike + Double(i - halfRange) * interval
        let callLTP = max(5, spot - strike + 100)
        let putLTP = max(5, strike - spot + 100)
        let callDelta = max(0.05, min(0.95, 0.5 - (strike - spot) / (spot * 0.5)))
        let putDelta = callDelta - 1.0

        let call = makeOption(
            strike: strike, type: .call, ltp: callLTP,
            oi: 200000, oiChange: 5000, iv: 0.15, volume: 50000,
            delta: callDelta, gamma: 0.002, theta: -5, vega: 8, spot: spot
        )
        let put = makeOption(
            strike: strike, type: .put, ltp: putLTP,
            oi: 200000, oiChange: 5000, iv: 0.16, volume: 50000,
            delta: putDelta, gamma: 0.002, theta: -5, vega: 8, spot: spot
        )
        return OptionChainRow(strikePrice: strike, callOption: call, putOption: put)
    }
}

// MARK: - ScoreBreakdown Tests

final class ScoreBreakdownTests: XCTestCase {

    func testInitStoresAllFactors() {
        let sb = ScoreBreakdown(ivAlignment: 80, pop: 70, riskReward: 60, liquidity: 50, regimeFit: 90)
        XCTAssertEqual(sb.ivAlignment, 80)
        XCTAssertEqual(sb.pop, 70)
        XCTAssertEqual(sb.riskReward, 60)
        XCTAssertEqual(sb.liquidity, 50)
        XCTAssertEqual(sb.regimeFit, 90)
    }

    func testEquatableConformance() {
        let a = ScoreBreakdown(ivAlignment: 80, pop: 70, riskReward: 60, liquidity: 50, regimeFit: 90)
        let b = ScoreBreakdown(ivAlignment: 80, pop: 70, riskReward: 60, liquidity: 50, regimeFit: 90)
        let c = ScoreBreakdown(ivAlignment: 50, pop: 70, riskReward: 60, liquidity: 50, regimeFit: 90)
        XCTAssertEqual(a, b)
        XCTAssertNotEqual(a, c)
    }

    func testValuesInRange() {
        let sb = ScoreBreakdown(ivAlignment: 0, pop: 100, riskReward: 50, liquidity: 0, regimeFit: 100)
        XCTAssertGreaterThanOrEqual(sb.ivAlignment, 0)
        XCTAssertLessThanOrEqual(sb.pop, 100)
        XCTAssertGreaterThanOrEqual(sb.riskReward, 0)
        XCTAssertLessThanOrEqual(sb.regimeFit, 100)
    }
}

// MARK: - StrategySuggestion Model Tests

final class StrategySuggestionModelTests: XCTestCase {

    func testInitWithNilDefaults() {
        let opt = makeOption(strike: 22000, type: .call, ltp: 150, spot: 22000)
        let leg = StrategySuggestion.StrategyLeg(option: opt, action: .buy, quantity: 1)
        let s = StrategySuggestion(
            strategyType: .nakedCall,
            legs: [leg],
            reasoning: ["Test"]
        )
        XCTAssertNil(s.score)
        XCTAssertNil(s.scoreBreakdown)
        XCTAssertNil(s.netDelta)
        XCTAssertNil(s.maxProfit)
        XCTAssertEqual(s.strategyType, .nakedCall)
        XCTAssertEqual(s.legs.count, 1)
    }

    func testInitWithAllFields() {
        let opt = makeOption(strike: 22000, type: .call, ltp: 150, delta: 0.5, spot: 22000)
        let leg = StrategySuggestion.StrategyLeg(option: opt, action: .buy, quantity: 1, lotSize: 75)
        let breakdown = ScoreBreakdown(ivAlignment: 80, pop: 70, riskReward: 60, liquidity: 50, regimeFit: 85)
        let s = StrategySuggestion(
            strategyType: .bullCallSpread,
            legs: [leg],
            reasoning: ["Test reason"],
            maxProfit: 5000,
            maxLoss: 3000,
            breakeven: [22150],
            probability: 0.6,
            ivRankBased: true,
            marketCondition: "Bullish + Low IV",
            score: 75,
            scoreBreakdown: breakdown,
            netDelta: 0.3,
            netTheta: -2.5,
            netGamma: 0.001,
            netVega: 4.0
        )
        XCTAssertEqual(s.score, 75)
        XCTAssertEqual(s.scoreBreakdown, breakdown)
        XCTAssertEqual(s.netDelta, 0.3)
        XCTAssertEqual(s.netTheta, -2.5)
        XCTAssertEqual(s.maxProfit, 5000)
        XCTAssertEqual(s.maxLoss, 3000)
    }

    func testPreferredIVCenterPerStrategyType() {
        // Directional strategies prefer low IV
        XCTAssertEqual(AIStrategyType.nakedCall.preferredIVCenter, 15)
        XCTAssertEqual(AIStrategyType.nakedPut.preferredIVCenter, 15)

        // Spreads prefer moderate IV
        XCTAssertEqual(AIStrategyType.bullCallSpread.preferredIVCenter, 50)
        XCTAssertEqual(AIStrategyType.bearPutSpread.preferredIVCenter, 50)

        // Premium selling strategies prefer high IV
        XCTAssertEqual(AIStrategyType.ironCondor.preferredIVCenter, 75)
        XCTAssertEqual(AIStrategyType.strangle.preferredIVCenter, 75)

        // Straddle prefers low IV (buying cheap options)
        XCTAssertEqual(AIStrategyType.straddle.preferredIVCenter, 15)
    }
}

// MARK: - AIStrategyType Tests

final class AIStrategyTypeTests: XCTestCase {

    func testDirectionClassification() {
        // Bullish strategies
        XCTAssertTrue(AIStrategyType.nakedCall.isBullish)
        XCTAssertTrue(AIStrategyType.bullCallSpread.isBullish)
        XCTAssertFalse(AIStrategyType.nakedCall.isBearish)
        XCTAssertFalse(AIStrategyType.nakedCall.isNeutral)

        // Bearish strategies
        XCTAssertTrue(AIStrategyType.nakedPut.isBearish)
        XCTAssertTrue(AIStrategyType.bearPutSpread.isBearish)
        XCTAssertFalse(AIStrategyType.nakedPut.isBullish)

        // Neutral strategies
        XCTAssertTrue(AIStrategyType.ironCondor.isNeutral)
        XCTAssertTrue(AIStrategyType.strangle.isNeutral)
        XCTAssertTrue(AIStrategyType.straddle.isNeutral)
    }

    func testDirectionString() {
        XCTAssertEqual(AIStrategyType.nakedCall.direction, "Bullish")
        XCTAssertEqual(AIStrategyType.nakedPut.direction, "Bearish")
        XCTAssertEqual(AIStrategyType.ironCondor.direction, "Neutral")
    }

    func testAllTypesHaveDescription() {
        for type in AIStrategyType.allCases {
            XCTAssertFalse(type.description.isEmpty, "\(type) should have a description")
        }
    }

    func testAllTypesHaveIcon() {
        for type in AIStrategyType.allCases {
            XCTAssertFalse(type.icon.isEmpty, "\(type) should have an icon")
        }
    }
}

// MARK: - Strategy Engine Integration Tests (via analyzeOptionChain)

final class StrategyEngineIntegrationTests: XCTestCase {

    let service = AIAnalysisService.shared

    func testEmptyChainReturnsEmptyStrategies() {
        let result = service.analyzeOptionChain(
            optionChain: [],
            spotPrice: 22000,
            putCallRatio: 1.0,
            maxPainStrike: 22000,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 0,
            totalPutOI: 0,
            indiaVix: 14
        )
        XCTAssertTrue(result.strategySuggestions.isEmpty, "Empty chain should produce no strategies")
        XCTAssertEqual(result.marketBias, .neutral)
    }

    func testBullishBiasGeneratesStrategies() {
        // High PCR + spot below max pain = bullish
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.5,
            maxPainStrike: 22200,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1500000,
            indiaVix: 12,
            previousClose: 21900,
            lotSize: 75
        )
        // Should have at least some strategies
        XCTAssertFalse(result.strategySuggestions.isEmpty, "Bullish conditions should generate strategies")
    }

    func testNeutralHighIVGeneratesNeutralStrategies() {
        // High VIX = high IV rank → should generate iron condor / strangle
        let chain = makeChain(spot: 22000, numStrikes: 31, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.0,
            maxPainStrike: 22000,
            atmStrike: 22000,
            atmIV: 25,
            totalCallOI: 1000000,
            totalPutOI: 1000000,
            indiaVix: 25,
            lotSize: 75
        )
        let types = result.strategySuggestions.map { $0.strategyType }
        let hasNeutral = types.contains(.ironCondor) || types.contains(.strangle)
        XCTAssertTrue(hasNeutral || result.strategySuggestions.isEmpty,
                      "High IV neutral market should prefer neutral strategies (or none if conditions not met)")
    }

    func testStrategiesSortedByScore() {
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.3,
            maxPainStrike: 22100,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1300000,
            indiaVix: 12,
            previousClose: 21900,
            lotSize: 75
        )
        let strategies = result.strategySuggestions
        for i in 1..<strategies.count {
            XCTAssertGreaterThanOrEqual(
                strategies[i - 1].score ?? 0,
                strategies[i].score ?? 0,
                "Strategies should be sorted by score descending"
            )
        }
    }

    func testMaxFiveStrategiesReturned() {
        let chain = makeChain(spot: 22000, numStrikes: 31, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.0,
            maxPainStrike: 22000,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1000000,
            indiaVix: 15,
            lotSize: 75
        )
        XCTAssertLessThanOrEqual(result.strategySuggestions.count, 5, "Should return max 5 strategies")
    }

    func testStrategyLegsHaveCorrectStructure() {
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.5,
            maxPainStrike: 22200,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1500000,
            indiaVix: 12,
            previousClose: 21900,
            lotSize: 75
        )
        for strategy in result.strategySuggestions {
            XCTAssertFalse(strategy.legs.isEmpty, "Each strategy must have at least one leg")
            XCTAssertFalse(strategy.reasoning.isEmpty, "Each strategy must have reasoning")
            XCTAssertNotNil(strategy.score, "Each strategy should have a score")

            for leg in strategy.legs {
                XCTAssertGreaterThan(leg.option.lastTradedPrice, 0, "Leg option must have a price")
                XCTAssertEqual(leg.quantity, 1, "Default quantity should be 1")
            }
        }
    }

    func testBullCallSpreadStructure() {
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.5,
            maxPainStrike: 22200,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1500000,
            indiaVix: 12,
            previousClose: 21900,
            lotSize: 75
        )
        if let spread = result.strategySuggestions.first(where: { $0.strategyType == .bullCallSpread }) {
            XCTAssertEqual(spread.legs.count, 2, "Bull call spread should have 2 legs")
            XCTAssertEqual(spread.legs[0].action, .buy, "First leg should be BUY")
            XCTAssertEqual(spread.legs[1].action, .sell, "Second leg should be SELL")
            XCTAssertEqual(spread.legs[0].option.optionType, .call, "Both legs should be calls")
            XCTAssertEqual(spread.legs[1].option.optionType, .call, "Both legs should be calls")
            XCTAssertLessThan(spread.legs[0].option.strikePrice, spread.legs[1].option.strikePrice,
                              "Buy leg should have lower strike")
            if let maxProfit = spread.maxProfit, let maxLoss = spread.maxLoss {
                XCTAssertGreaterThanOrEqual(maxProfit, 0, "Max profit should be non-negative")
                XCTAssertGreaterThanOrEqual(maxLoss, 0, "Max loss should be non-negative")
            }
        }
    }

    func testNakedCallStructure() {
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.5,
            maxPainStrike: 22200,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1500000,
            indiaVix: 12,
            previousClose: 21900,
            lotSize: 75
        )
        if let naked = result.strategySuggestions.first(where: { $0.strategyType == .nakedCall }) {
            XCTAssertEqual(naked.legs.count, 1, "Naked call should have 1 leg")
            XCTAssertEqual(naked.legs[0].action, .buy, "Naked call leg should be BUY")
            XCTAssertEqual(naked.legs[0].option.optionType, .call, "Leg should be a call")
            XCTAssertEqual(naked.maxProfit, 999999, "Naked call has unlimited profit")
        }
    }

    func testStraddleStructure() {
        // Volatile regime + low IV → straddle
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.0,
            maxPainStrike: 22000,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1000000,
            indiaVix: 21,    // Volatile regime
            previousClose: 21700,  // Big intraday move
            lotSize: 75
        )
        if let straddle = result.strategySuggestions.first(where: { $0.strategyType == .straddle }) {
            XCTAssertEqual(straddle.legs.count, 2, "Straddle should have 2 legs")
            XCTAssertEqual(straddle.legs[0].action, .buy, "Both legs should be BUY")
            XCTAssertEqual(straddle.legs[1].action, .buy, "Both legs should be BUY")
            // Same strike for both legs
            XCTAssertEqual(straddle.legs[0].option.strikePrice, straddle.legs[1].option.strikePrice,
                           "Straddle legs should be at same strike")
            XCTAssertEqual(straddle.probability, 0.35, "Straddle POP should be 0.35")
            XCTAssertEqual(straddle.maxProfit, 999999, "Straddle has unlimited profit")
        }
    }

    func testScoreBreakdownPopulatedOnStrategies() {
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.5,
            maxPainStrike: 22200,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1500000,
            indiaVix: 12,
            previousClose: 21900,
            lotSize: 75
        )
        for strategy in result.strategySuggestions {
            if let breakdown = strategy.scoreBreakdown {
                XCTAssertGreaterThanOrEqual(breakdown.ivAlignment, 0)
                XCTAssertLessThanOrEqual(breakdown.ivAlignment, 100)
                XCTAssertGreaterThanOrEqual(breakdown.pop, 0)
                XCTAssertLessThanOrEqual(breakdown.pop, 100)
                XCTAssertGreaterThanOrEqual(breakdown.riskReward, 0)
                XCTAssertLessThanOrEqual(breakdown.riskReward, 100)
                XCTAssertGreaterThanOrEqual(breakdown.liquidity, 0)
                XCTAssertLessThanOrEqual(breakdown.liquidity, 100)
                XCTAssertGreaterThanOrEqual(breakdown.regimeFit, 0)
                XCTAssertLessThanOrEqual(breakdown.regimeFit, 100)
            }
        }
    }

    func testNetGreeksPopulatedOnStrategies() {
        let chain = makeChain(spot: 22000, numStrikes: 21, interval: 50)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.5,
            maxPainStrike: 22200,
            atmStrike: 22000,
            atmIV: 15,
            totalCallOI: 1000000,
            totalPutOI: 1500000,
            indiaVix: 12,
            previousClose: 21900,
            lotSize: 75
        )
        for strategy in result.strategySuggestions {
            // Greeks should be populated for scored strategies
            XCTAssertNotNil(strategy.netDelta, "\(strategy.strategyType) should have netDelta")
            XCTAssertNotNil(strategy.netTheta, "\(strategy.strategyType) should have netTheta")
            XCTAssertNotNil(strategy.netGamma, "\(strategy.strategyType) should have netGamma")
            XCTAssertNotNil(strategy.netVega, "\(strategy.strategyType) should have netVega")
        }
    }

    func testMarketRegimeDetected() {
        let chain = makeChain(spot: 22000, numStrikes: 11, interval: 50)

        // Volatile: high VIX
        let volatileResult = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.0,
            maxPainStrike: 22000,
            atmStrike: 22000,
            atmIV: 25,
            totalCallOI: 1000000,
            totalPutOI: 1000000,
            indiaVix: 25,
            lotSize: 75
        )
        XCTAssertEqual(volatileResult.marketRegime, .volatile, "VIX > 20 should be volatile")

        // Low VIX, small move → range-bound
        let calmResult = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 22000,
            putCallRatio: 1.0,
            maxPainStrike: 22000,
            atmStrike: 22000,
            atmIV: 12,
            totalCallOI: 1000000,
            totalPutOI: 1000000,
            indiaVix: 12,
            previousClose: 21990,
            lotSize: 75
        )
        // Low VIX (12) + tiny move → flat regime (VIX < 14 && absMove < 0.15)
        XCTAssertEqual(calmResult.marketRegime, .flat, "Low VIX + small move should be flat")
    }

    func testBankNiftyUsesCorrectInterval() {
        // BANKNIFTY lot size = 30, interval should be 100
        let chain = makeChain(spot: 50000, numStrikes: 21, interval: 100)
        let result = service.analyzeOptionChain(
            optionChain: chain,
            spotPrice: 50000,
            putCallRatio: 1.3,
            maxPainStrike: 50100,
            atmStrike: 50000,
            atmIV: 15,
            totalCallOI: 500000,
            totalPutOI: 650000,
            indiaVix: 12,
            previousClose: 49900,
            lotSize: 30
        )
        for strategy in result.strategySuggestions {
            XCTAssertEqual(strategy.legs[0].lotSize, 30, "BankNifty should use lot size 30")
        }
    }

    func testStrategyLegPremiumCalculation() {
        let opt = makeOption(strike: 22000, type: .call, ltp: 150, spot: 22000)
        let leg = StrategySuggestion.StrategyLeg(option: opt, action: .buy, quantity: 1, lotSize: 75)
        XCTAssertEqual(leg.premium, 150 * 75, "Premium = LTP * lotSize * quantity")
    }

    func testStrategyLegDisplayAction() {
        let opt = makeOption(strike: 22000, type: .call, ltp: 150, spot: 22000)
        let buyLeg = StrategySuggestion.StrategyLeg(option: opt, action: .buy, quantity: 1, lotSize: 75)
        XCTAssertEqual(buyLeg.displayAction, "BUY 1 lot")

        let sellLeg = StrategySuggestion.StrategyLeg(option: opt, action: .sell, quantity: 2, lotSize: 75)
        XCTAssertEqual(sellLeg.displayAction, "SELL 2 lots")
    }
}

// MARK: - MarketRegime Tests

final class MarketRegimeTests: XCTestCase {

    func testAllRegimesExist() {
        XCTAssertEqual(MarketRegime.allCases.count, 4)
        XCTAssertNotNil(MarketRegime.trending)
        XCTAssertNotNil(MarketRegime.rangeBound)
        XCTAssertNotNil(MarketRegime.volatile)
        XCTAssertNotNil(MarketRegime.flat)
    }

    func testRegimeRawValues() {
        XCTAssertEqual(MarketRegime.trending.rawValue, "Trending")
        XCTAssertEqual(MarketRegime.rangeBound.rawValue, "Range-Bound")
        XCTAssertEqual(MarketRegime.volatile.rawValue, "Volatile")
    }

    func testRegimeIcons() {
        XCTAssertFalse(MarketRegime.trending.icon.isEmpty)
        XCTAssertFalse(MarketRegime.rangeBound.icon.isEmpty)
        XCTAssertFalse(MarketRegime.volatile.icon.isEmpty)
    }
}
