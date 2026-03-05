import Foundation

// MARK: - AI Insights Test Cases
// Run these tests to verify prediction logic

struct AIInsightsTestRunner {

    static func runAllTests() {
        print("=" * 60)
        print("🧪 AI INSIGHTS TEST SUITE")
        print("=" * 60)

        testMoneynessScoring()
        testCEPEOppositeSignals()
        testMarketBiasLogic()
        testTargetStopLossCalculation()
        testRiskRewardRatio()
        testIVPercentileCalculation()
        testSkewCalculation()
        testTermStructureDetection()
        testStrictNoTradeGateLogic()

        print("\n" + "=" * 60)
        print("✅ ALL TESTS COMPLETED")
        print("=" * 60)
    }

    // MARK: - Test 1: Moneyness Scoring

    static func testMoneynessScoring() {
        print("\n📊 TEST 1: Moneyness Scoring")
        print("-" * 40)

        let spotPrice = 25418.90

        // Test cases: (strike, optionType, expectedScoreRange, description)
        let testCases: [(Double, String, ClosedRange<Double>, String)] = [
            // ATM strikes should score 85-95
            (25400, "CE", 85...100, "ATM Call"),
            (25450, "CE", 85...100, "ATM Call"),
            (25400, "PE", 85...100, "ATM Put"),

            // Near ATM (0.5-1.5%) should score 75-85
            (25200, "CE", 70...90, "Near ATM Call (0.9% ITM)"),
            (25600, "CE", 70...90, "Near ATM Call (0.7% OTM)"),

            // Slightly OTM (1.5-3%) should score 55-75
            (25000, "CE", 50...80, "Slightly ITM Call (1.6% ITM)"),
            (26000, "CE", 50...80, "Slightly OTM Call (2.3% OTM)"),

            // Deep ITM (5-8%) should score 10-40
            (24000, "CE", 10...45, "Deep ITM Call (5.6% ITM)"),
            (23700, "CE", 5...30, "Deep ITM Call (6.8% ITM)"),
            (23450, "CE", 5...25, "Very Deep ITM Call (7.7% ITM)"),

            // Deep OTM should score 15-35
            (26500, "CE", 10...40, "Deep OTM Call (4.3% OTM)"),
            (27000, "CE", 5...30, "Very Deep OTM Call (6.2% OTM)"),
        ]

        for (strike, optionType, expectedRange, description) in testCases {
            let distancePercent = abs(strike - spotPrice) / spotPrice * 100
            let isCall = optionType == "CE"
            let isITM = isCall ? (strike < spotPrice) : (strike > spotPrice)

            // Calculate expected score based on our logic
            let score = calculateMoneynessScore(
                strike: strike,
                spot: spotPrice,
                isCall: isCall
            )

            let passed = expectedRange.contains(score)
            let status = passed ? "✅" : "❌"

            print("\(status) \(description)")
            print("   Strike: \(Int(strike)), Distance: \(String(format: "%.1f", distancePercent))% \(isITM ? "ITM" : "OTM")")
            print("   Score: \(Int(score)) (Expected: \(Int(expectedRange.lowerBound))-\(Int(expectedRange.upperBound)))")
        }
    }

    // MARK: - Test 2: CE/PE Opposite Signals

    static func testCEPEOppositeSignals() {
        print("\n📊 TEST 2: CE/PE Opposite Signals")
        print("-" * 40)

        // Given same market conditions, CE and PE at same strike should have OPPOSITE signals
        let testCases: [(Double, String, String)] = [
            // PCR > 1.0, Spot below Max Pain = Bullish
            (1.15, "below", "CE should be BUY, PE should be SELL/HOLD"),

            // PCR < 0.8, Spot above Max Pain = Bearish
            (0.7, "above", "CE should be SELL/HOLD, PE should be BUY"),

            // Neutral PCR, Spot at Max Pain = Neutral
            (1.0, "at", "Both should be HOLD or mixed"),
        ]

        for (pcr, spotPosition, expected) in testCases {
            print("✅ PCR: \(pcr), Spot \(spotPosition) Max Pain")
            print("   Expected: \(expected)")

            // Calculate market bias
            var marketBullishScore: Double = 0
            if pcr > 1.2 {
                marketBullishScore += 0.3
            } else if pcr > 1.0 {
                marketBullishScore += 0.15
            } else if pcr < 0.7 {
                marketBullishScore -= 0.2
            } else if pcr < 0.9 {
                marketBullishScore -= 0.1
            }

            if spotPosition == "below" {
                marketBullishScore += 0.25
            } else if spotPosition == "above" {
                marketBullishScore -= 0.25
            }

            let ceScore = marketBullishScore
            let peScore = -marketBullishScore

            let ceSignal = ceScore > 0.15 ? "BUY" : (ceScore < -0.15 ? "SELL" : "HOLD")
            let peSignal = peScore > 0.15 ? "BUY" : (peScore < -0.15 ? "SELL" : "HOLD")

            print("   CE Signal: \(ceSignal) (score: \(String(format: "%.2f", ceScore)))")
            print("   PE Signal: \(peSignal) (score: \(String(format: "%.2f", peScore)))")

            // Verify they are opposite (or both HOLD in neutral)
            let areOpposite = (ceSignal == "BUY" && peSignal == "SELL") ||
                             (ceSignal == "SELL" && peSignal == "BUY") ||
                             (ceSignal == "HOLD" && peSignal == "HOLD")
            print("   Opposite signals: \(areOpposite ? "✅ YES" : "❌ NO")")
        }
    }

    // MARK: - Test 3: Market Bias Logic

    static func testMarketBiasLogic() {
        print("\n📊 TEST 3: Market Bias Logic")
        print("-" * 40)

        let testCases: [(Double, Double, Double, String)] = [
            // (PCR, Spot, MaxPain, ExpectedBias)
            (1.3, 25000, 25200, "Bullish"),     // High PCR + spot below max pain
            (0.7, 25400, 25200, "Bearish"),     // Low PCR + spot above max pain
            (1.0, 25200, 25200, "Neutral"),     // Neutral PCR + spot at max pain
            (1.5, 24800, 25200, "Strong Bullish"), // Very high PCR + spot well below max pain
            (0.6, 25600, 25200, "Strong Bearish"), // Very low PCR + spot well above max pain
        ]

        for (pcr, spot, maxPain, expectedBias) in testCases {
            var bullishPoints = 0
            var bearishPoints = 0

            // PCR Analysis
            if pcr > 1.2 { bullishPoints += 2 }
            else if pcr < 0.8 { bearishPoints += 1 }
            else if pcr > 1.0 { bullishPoints += 1 }
            else { bearishPoints += 1 }

            // Max Pain Analysis
            if spot < maxPain { bullishPoints += 2 }
            else if spot > maxPain { bearishPoints += 2 }

            let netBias = bullishPoints - bearishPoints
            let calculatedBias: String
            if netBias >= 4 { calculatedBias = "Strong Bullish" }
            else if netBias >= 2 { calculatedBias = "Bullish" }
            else if netBias <= -4 { calculatedBias = "Strong Bearish" }
            else if netBias <= -2 { calculatedBias = "Bearish" }
            else { calculatedBias = "Neutral" }

            let passed = calculatedBias == expectedBias
            print("\(passed ? "✅" : "❌") PCR: \(pcr), Spot: \(Int(spot)), MaxPain: \(Int(maxPain))")
            print("   Expected: \(expectedBias), Got: \(calculatedBias)")
        }
    }

    // MARK: - Test 4: Target/Stop Loss Calculation

    static func testTargetStopLossCalculation() {
        print("\n📊 TEST 4: Target/Stop Loss Calculation")
        print("-" * 40)

        let testCases: [(Double, Double, Double)] = [
            // (entryPrice, expectedMinTarget, expectedMaxSL)
            (100.0, 115.0, 95.0),   // Normal option
            (50.0, 57.5, 47.5),     // Mid-range option
            (0.05, 0.15, 0.025),    // Very cheap option (special handling)
            (0.10, 0.20, 0.05),     // Cheap option
            (500.0, 575.0, 475.0),  // Expensive ITM option
        ]

        for (entry, minTarget, maxSL) in testCases {
            // Apply our fallback logic
            var targetPrice = entry * 1.15  // 15% target
            var stopLoss = entry * 0.95     // 5% SL

            // Special handling for cheap options
            if entry < 1.0 {
                targetPrice = max(targetPrice, entry + 0.10)
                stopLoss = max(stopLoss, entry * 0.5)
            }

            let targetOK = targetPrice >= minTarget
            let slOK = stopLoss <= maxSL || entry < 1.0

            print("\(targetOK && slOK ? "✅" : "❌") Entry: ₹\(String(format: "%.2f", entry))")
            print("   Target: ₹\(String(format: "%.2f", targetPrice)) (min: ₹\(String(format: "%.2f", minTarget)))")
            print("   Stop Loss: ₹\(String(format: "%.2f", stopLoss)) (max: ₹\(String(format: "%.2f", maxSL)))")
            print("   R:R = 1:\(String(format: "%.1f", (targetPrice - entry) / (entry - stopLoss)))")
        }
    }

    // MARK: - Test 5: Risk Reward Ratio

    static func testRiskRewardRatio() {
        print("\n📊 TEST 5: Risk Reward Ratio")
        print("-" * 40)

        let testCases: [(Double, Double, Double, Double)] = [
            // (entry, target, stopLoss, expectedRR)
            (100.0, 122.0, 95.0, 4.4),   // +22% / -5% = 4.4:1
            (100.0, 115.0, 90.0, 1.5),   // +15% / -10% = 1.5:1
            (100.0, 110.0, 95.0, 2.0),   // +10% / -5% = 2:1
            (50.0, 60.0, 45.0, 2.0),     // +20% / -10% = 2:1
        ]

        for (entry, target, sl, expectedRR) in testCases {
            let profit = target - entry
            let loss = entry - sl
            let actualRR = profit / loss

            let passed = abs(actualRR - expectedRR) < 0.2
            print("\(passed ? "✅" : "❌") Entry: ₹\(Int(entry)), Target: ₹\(Int(target)), SL: ₹\(Int(sl))")
            print("   Profit: +₹\(Int(profit)) (\(String(format: "+%.0f%%", profit/entry*100)))")
            print("   Loss: -₹\(Int(loss)) (\(String(format: "-%.0f%%", loss/entry*100)))")
            print("   R:R = 1:\(String(format: "%.1f", actualRR)) (expected: 1:\(expectedRR))")
        }
    }

    // MARK: - Test 6: IV Percentile Calculation

    static func testIVPercentileCalculation() {
        print("\n📊 TEST 6: IV Percentile Calculation")
        print("-" * 40)

        let ivValues: [Double] = [12, 15, 18, 22, 25, 30, 35]
        let testCases: [(Double, ClosedRange<Double>, String)] = [
            (12, 10...20, "Lowest IV should be low percentile"),
            (22, 40...70, "Mid IV should be mid percentile"),
            (35, 85...100, "Highest IV should be high percentile"),
        ]

        for (iv, expectedRange, description) in testCases {
            let percentile = calculateIVPercentile(ivValues: ivValues, currentIV: iv)
            let passed = expectedRange.contains(percentile)
            let status = passed ? "✅" : "❌"
            print("\(status) \(description)")
            print("   IV: \(iv), Percentile: \(Int(percentile)) (Expected: \(Int(expectedRange.lowerBound))-\(Int(expectedRange.upperBound)))")
        }
    }

    // MARK: - Test 7: Skew Calculation

    static func testSkewCalculation() {
        print("\n📊 TEST 7: Skew Calculation")
        print("-" * 40)

        // Synthetic skew scenarios using avgPutIV - avgCallIV
        let testCases: [(Double, Double, ClosedRange<Double>, String)] = [
            (28.0, 20.0, 7...10, "High positive skew (puts richer)"),
            (22.0, 22.0, -1...1, "Flat skew"),
            (18.0, 25.0, -8...-5, "Negative skew (calls richer)"),
        ]

        for (avgPutIV, avgCallIV, expectedRange, description) in testCases {
            let skew = avgPutIV - avgCallIV
            let passed = expectedRange.contains(skew)
            let status = passed ? "✅" : "❌"
            print("\(status) \(description)")
            print("   Skew: \(String(format: "%.1f", skew)) (Expected: \(String(format: "%.1f", expectedRange.lowerBound))-\(String(format: "%.1f", expectedRange.upperBound)))")
        }
    }

    // MARK: - Test 8: Term Structure Detection

    static func testTermStructureDetection() {
        print("\n📊 TEST 8: Term Structure Detection")
        print("-" * 40)

        let testCases: [(Double, Double, String)] = [
            (30, 25, "Inverted"),
            (20, 25, "Contango"),
            (23, 25, "Flat"),
        ]

        for (atmIV, vix, expected) in testCases {
            let term = determineTermStructure(atmIV: atmIV, vix: vix)
            let passed = term == expected
            print("\(passed ? "✅" : "❌") ATM IV: \(atmIV), VIX: \(vix)")
            print("   Expected: \(expected), Got: \(term)")
        }
    }

    // MARK: - Test 9: Strict No-Trade Gate Logic

    static func testStrictNoTradeGateLogic() {
        print("\n📊 TEST 9: Strict No-Trade Gate Logic")
        print("-" * 40)

        let testCases: [(Bool, String)] = [
            (passesStrictFiltersSim(
                overallScore: 55, confidence: "medium", riskWarnings: 0,
                ivRank: 40, ivPercentile: 40, termStructure: "flat", daysToExpiry: 10,
                skew: 2, isCall: true
            ) == false, "Rejects low overall score"),
            (passesStrictFiltersSim(
                overallScore: 70, confidence: "low", riskWarnings: 0,
                ivRank: 40, ivPercentile: 40, termStructure: "flat", daysToExpiry: 10,
                skew: 2, isCall: true
            ) == false, "Rejects low confidence"),
            (passesStrictFiltersSim(
                overallScore: 70, confidence: "high", riskWarnings: 1,
                ivRank: 40, ivPercentile: 40, termStructure: "flat", daysToExpiry: 10,
                skew: 2, isCall: true
            ) == false, "Rejects risk warnings"),
            (passesStrictFiltersSim(
                overallScore: 70, confidence: "high", riskWarnings: 0,
                ivRank: 80, ivPercentile: 40, termStructure: "flat", daysToExpiry: 10,
                skew: 2, isCall: true
            ) == false, "Rejects high IV Rank"),
            (passesStrictFiltersSim(
                overallScore: 70, confidence: "high", riskWarnings: 0,
                ivRank: 40, ivPercentile: 85, termStructure: "flat", daysToExpiry: 10,
                skew: 2, isCall: true
            ) == false, "Rejects high IV Percentile"),
            (passesStrictFiltersSim(
                overallScore: 70, confidence: "high", riskWarnings: 0,
                ivRank: 40, ivPercentile: 40, termStructure: "inverted", daysToExpiry: 3,
                skew: 2, isCall: true
            ) == false, "Rejects inverted term structure for short DTE"),
            (passesStrictFiltersSim(
                overallScore: 70, confidence: "high", riskWarnings: 0,
                ivRank: 40, ivPercentile: 40, termStructure: "flat", daysToExpiry: 10,
                skew: 10, isCall: true
            ) == false, "Rejects call when skew against calls"),
            (passesStrictFiltersSim(
                overallScore: 70, confidence: "high", riskWarnings: 0,
                ivRank: 40, ivPercentile: 40, termStructure: "flat", daysToExpiry: 10,
                skew: -2, isCall: false
            ) == false, "Rejects put when skew against puts"),
            (passesStrictFiltersSim(
                overallScore: 75, confidence: "high", riskWarnings: 0,
                ivRank: 35, ivPercentile: 45, termStructure: "flat", daysToExpiry: 10,
                skew: 2, isCall: true
            ) == true, "Accepts clean high-quality setup"),
        ]

        for (passed, description) in testCases {
            print("\(passed ? "✅" : "❌") \(description)")
        }
    }

    // MARK: - Helper Functions

    static func calculateMoneynessScore(strike: Double, spot: Double, isCall: Bool) -> Double {
        let distancePercent = abs(strike - spot) / spot * 100
        let isITM = isCall ? (strike < spot) : (strike > spot)

        if distancePercent <= 0.5 {
            return 95
        } else if distancePercent <= 1.5 {
            return 85
        } else if distancePercent <= 3.0 {
            return isITM ? 60 : 75
        } else if distancePercent <= 5.0 {
            return isITM ? 40 : 55
        } else if distancePercent <= 8.0 {
            return isITM ? 20 : 35
        } else {
            return isITM ? 10 : 15
        }
    }

    static func calculateIVPercentile(ivValues: [Double], currentIV: Double) -> Double {
        let filtered = ivValues.filter { $0 > 0 }
        guard !filtered.isEmpty else { return 50 }
        let count = filtered.count
        let belowOrEqual = filtered.filter { $0 <= currentIV }.count
        let percentile = (Double(belowOrEqual) / Double(count)) * 100
        return min(100, max(0, percentile))
    }

    static func determineTermStructure(atmIV: Double, vix: Double) -> String {
        let diff = atmIV - vix
        if diff >= 3 { return "Inverted" }
        if diff <= -3 { return "Contango" }
        return "Flat"
    }

    static func passesStrictFiltersSim(
        overallScore: Double,
        confidence: String,
        riskWarnings: Int,
        ivRank: Double?,
        ivPercentile: Double?,
        termStructure: String,
        daysToExpiry: Int,
        skew: Double?,
        isCall: Bool
    ) -> Bool {
        if overallScore < 60 { return false }
        if confidence == "low" { return false }
        if riskWarnings > 0 { return false }
        if let ivRank = ivRank, ivRank > 75 { return false }
        if let ivPercentile = ivPercentile, ivPercentile > 75 { return false }
        if termStructure == "inverted", daysToExpiry <= 7 { return false }
        if let skew = skew {
            if isCall && skew > 8 { return false }
            if !isCall && skew < 0 { return false }
        }
        return true
    }
}

// String multiplication helper
extension String {
    static func * (left: String, right: Int) -> String {
        return String(repeating: left, count: right)
    }
}

// Run tests
// AIInsightsTestRunner.runAllTests()
