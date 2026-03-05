import Foundation
import SwiftUI

// MARK: - AI Analysis Service

final class AIAnalysisService {
    static let shared = AIAnalysisService()
    private let mlPredictor = MLOptionPredictor.shared

    private init() {}

    // Configuration - Professional Trading Thresholds
    private let riskFreeRate: Double = 0.065  // RBI repo rate
    private let minRiskRewardRatio: Double = 1.5  // Minimum 1.5:1 R:R for quality trades
    private let maxSuggestions: Int = 8           // Show top 8 quality suggestions per side
    private let useMLPredictions: Bool = true
    private let minScoreThreshold: Double = 50    // Only show options scoring 50+
    private let minMLConfidence: Double = 50      // Minimum 50% ML confidence
    private let minDelta: Double = 0.20           // Avoid deep OTM (delta < 0.20)
    private let maxDelta: Double = 0.80           // Avoid deep ITM (delta > 0.80)
    private let minLiquidity: Int = 500           // Minimum 500 volume for liquidity
    private let maxBidAskSpreadPercent: Double = 5.0  // Max 5% bid-ask spread
    private let minDaysToExpiry: Int = 0          // Allow 0DTE options for intraday trading
    private let minOpenInterest: Int = 1000       // Minimum OI for reliable data
    private let strictMinScoreThreshold: Double = 60  // Strict no-trade gate

    // Intraday movement thresholds
    private let significantMovePercent: Double = 0.3  // 0.3% is considered significant intraday move
    private let strongMovePercent: Double = 0.5  // 0.5%+ is a strong move

    // MARK: - Main Analysis Entry Point

    func analyzeOptionChain(
        optionChain: [OptionChainRow],
        spotPrice: Double,
        putCallRatio: Double,
        maxPainStrike: Double?,
        atmStrike: Double?,
        atmIV: Double?,
        totalCallOI: Int,
        totalPutOI: Int,
        technicalAnalysis: TechnicalAnalysisResult? = nil,
        indiaVix: Double? = nil,
        previousClose: Double? = nil,  // Previous day's close for intraday movement detection
        lotSize: Int = 75  // Lot size for the index (default: Nifty = 75)
    ) -> AIAnalysisResult {
        guard !optionChain.isEmpty else {
            return AIAnalysisResult(
                marketBias: .neutral,
                topCallPicks: [],
                topPutPicks: [],
                avoidList: [],
                marketInsights: [],
                spotPrice: spotPrice,
                putCallRatio: putCallRatio,
                maxPainStrike: maxPainStrike,
                atmStrike: atmStrike,
                technicalAnalysis: technicalAnalysis,
                indiaVix: indiaVix
            )
        }

        // Build analysis context with VIX and intraday movement
        let context = buildAnalysisContext(
            optionChain: optionChain,
            spotPrice: spotPrice,
            putCallRatio: putCallRatio,
            maxPainStrike: maxPainStrike,
            atmStrike: atmStrike,
            atmIV: atmIV,
            totalCallOI: totalCallOI,
            totalPutOI: totalPutOI,
            indiaVix: indiaVix,
            previousClose: previousClose,
            lotSize: lotSize
        )

        // Score all options
        var callScores: [OptionScore] = []
        var putScores: [OptionScore] = []

        for row in optionChain {
            if let call = row.callOption {
                let score = scoreOption(option: call, context: context)
                callScores.append(score)
            }
            if let put = row.putOption {
                let score = scoreOption(option: put, context: context)
                putScores.append(score)
            }
        }

        // Sort by score (highest first)
        callScores.sort { $0.overallScore > $1.overallScore }
        putScores.sort { $0.overallScore > $1.overallScore }

        // Determine market bias first (needed for suggestion filtering)
        let marketBias = determineMarketBias(context: context)

        // Detect market regime from VIX + intraday move
        let marketRegime = detectMarketRegime(context: context)

        // Generate trade suggestions from top 20 candidates (filter inside, limit at end)
        // BUG FIX: Previously prefix(5) was applied BEFORE filtering, causing fewer suggestions
        let topCallPicks = generateSuggestions(
            scores: Array(callScores.prefix(20)),
            context: context,
            isCall: true,
            marketBias: marketBias,
            regime: marketRegime
        )

        let topPutPicks = generateSuggestions(
            scores: Array(putScores.prefix(20)),
            context: context,
            isCall: false,
            marketBias: marketBias,
            regime: marketRegime
        )

        // Identify options to avoid (low scores with reasons)
        let avoidList = identifyAvoidList(callScores: callScores, putScores: putScores)

        // Generate market insights
        let marketInsights = generateMarketInsights(context: context)

        // Combine market insights with technical insights
        var allInsights = marketInsights
        if let technical = technicalAnalysis {
            // Add technical insights as market insights
            for insight in technical.insights.prefix(5) {
                allInsights.append(MarketInsight(
                    title: insight.title,
                    description: insight.description,
                    icon: insight.type.icon,
                    color: insight.signal.color
                ))
            }
        }

        // NEW: Detect unusual options activity (smart money detection)
        let unusualActivities = detectUnusualActivity(optionChain: optionChain, context: context)
        if !unusualActivities.isEmpty {
            // Unusual activities detected
        }

        // NEW: Generate strategy suggestions based on IV Rank and market conditions
        let strategySuggestions = generateStrategySuggestions(
            optionChain: optionChain,
            context: context,
            marketBias: marketBias
        )
        if !strategySuggestions.isEmpty {
            // Strategy suggestions generated
        }

        return AIAnalysisResult(
            marketBias: marketBias,
            topCallPicks: topCallPicks,
            topPutPicks: topPutPicks,
            avoidList: avoidList,
            marketInsights: allInsights,
            spotPrice: spotPrice,
            putCallRatio: putCallRatio,
            maxPainStrike: maxPainStrike,
            atmStrike: atmStrike,
            technicalAnalysis: technicalAnalysis,
            indiaVix: context.indiaVix,
            unusualActivities: unusualActivities,
            strategySuggestions: strategySuggestions,
            marketRegime: marketRegime
        )
    }

    // MARK: - Analysis Context

    private struct AnalysisContext {
        let spotPrice: Double
        let putCallRatio: Double
        let maxPainStrike: Double?
        let atmStrike: Double?
        let atmIV: Double
        let ivValues: [Double]
        let skew: Double?
        let termStructure: TermStructure?
        let totalCallOI: Int
        let totalPutOI: Int
        let maxOI: Int
        let maxChangeInOI: Int
        let avgVolume: Double
        let supportLevel: Double?  // Highest Put OI strike
        let resistanceLevel: Double?  // Highest Call OI strike
        let totalCallOIChange: Int
        let totalPutOIChange: Int
        let indiaVix: Double?  // India VIX for volatility-based strategy selection
        let previousClose: Double?  // Previous day's close for intraday movement
        let lotSize: Int  // Lot size for the index

        // Configurable thresholds (passed from service)
        let significantMoveThreshold: Double
        let strongMoveThreshold: Double

        // Intraday movement calculation
        var intradayMovePercent: Double {
            guard let prevClose = previousClose, prevClose > 0 else { return 0 }
            return ((spotPrice - prevClose) / prevClose) * 100
        }

        // Generic movement detection using configurable thresholds
        var isBullishMove: Bool {
            return intradayMovePercent >= significantMoveThreshold
        }

        var isBearishMove: Bool {
            return intradayMovePercent <= -significantMoveThreshold
        }

        var isStrongBullishMove: Bool {
            return intradayMovePercent >= strongMoveThreshold
        }

        var isStrongBearishMove: Bool {
            return intradayMovePercent <= -strongMoveThreshold
        }

        var moveDirection: String {
            if isStrongBullishMove { return "strong_bullish" }
            if isBullishMove { return "bullish" }
            if isStrongBearishMove { return "strong_bearish" }
            if isBearishMove { return "bearish" }
            return "neutral"
        }

        // Magnitude of the move (absolute value)
        var moveMagnitude: Double {
            return abs(intradayMovePercent)
        }

        // Returns a score multiplier based on move magnitude (stronger moves = higher multiplier)
        var moveStrengthMultiplier: Double {
            let magnitude = moveMagnitude
            if magnitude >= strongMoveThreshold * 2 {
                return 1.5  // Very strong move (e.g., 1%+)
            } else if magnitude >= strongMoveThreshold {
                return 1.25  // Strong move
            } else if magnitude >= significantMoveThreshold {
                return 1.1  // Significant move
            }
            return 1.0  // No significant move
        }

        // VIX-based strategy guidance
        var vixFavorsSelling: Bool {
            guard let vix = indiaVix else { return false }
            return vix > 18  // High VIX = expensive premiums, good for selling
        }

        var vixFavorsBuying: Bool {
            guard let vix = indiaVix else { return false }
            return vix < 13  // Low VIX = cheap premiums, good for buying
        }

        var vixStatus: String {
            guard let vix = indiaVix else { return "unavailable" }
            if vix < 12 { return "low" }
            if vix < 15 { return "normal" }
            if vix < 20 { return "elevated" }
            if vix < 25 { return "high" }
            return "extreme"
        }
    }

    private func buildAnalysisContext(
        optionChain: [OptionChainRow],
        spotPrice: Double,
        putCallRatio: Double,
        maxPainStrike: Double?,
        atmStrike: Double?,
        atmIV: Double?,
        totalCallOI: Int,
        totalPutOI: Int,
        indiaVix: Double? = nil,
        previousClose: Double? = nil,
        lotSize: Int = 75
    ) -> AnalysisContext {
        var maxOI = 0
        var maxChangeInOI = 0
        var totalVolume = 0
        var optionCount = 0
        var highestCallOI = 0
        var highestPutOI = 0
        var resistanceStrike: Double?
        var supportStrike: Double?
        var totalCallOIChange = 0
        var totalPutOIChange = 0
        var ivValues: [Double] = []

        for row in optionChain {
            if let call = row.callOption {
                maxOI = max(maxOI, call.openInterest)
                maxChangeInOI = max(maxChangeInOI, abs(call.changeInOI))
                totalVolume += call.volume
                optionCount += 1
                totalCallOIChange += call.changeInOI
                if call.impliedVolatility > 0 {
                    ivValues.append(call.impliedVolatility)
                }

                if call.openInterest > highestCallOI {
                    highestCallOI = call.openInterest
                    resistanceStrike = call.strikePrice
                }
            }
            if let put = row.putOption {
                maxOI = max(maxOI, put.openInterest)
                maxChangeInOI = max(maxChangeInOI, abs(put.changeInOI))
                totalVolume += put.volume
                optionCount += 1
                totalPutOIChange += put.changeInOI
                if put.impliedVolatility > 0 {
                    ivValues.append(put.impliedVolatility)
                }

                if put.openInterest > highestPutOI {
                    highestPutOI = put.openInterest
                    supportStrike = put.strikePrice
                }
            }
        }

        let avgVolume = optionCount > 0 ? Double(totalVolume) / Double(optionCount) : 0
        let skew = calculateSkew(optionChain: optionChain, atmStrike: atmStrike)
        let termStructure = determineTermStructure(atmIV: atmIV ?? 15.0, indiaVix: indiaVix)

        return AnalysisContext(
            spotPrice: spotPrice,
            putCallRatio: putCallRatio,
            maxPainStrike: maxPainStrike,
            atmStrike: atmStrike,
            atmIV: atmIV ?? 15.0,
            ivValues: ivValues,
            skew: skew,
            termStructure: termStructure,
            totalCallOI: totalCallOI,
            totalPutOI: totalPutOI,
            maxOI: maxOI,
            maxChangeInOI: maxChangeInOI,
            avgVolume: avgVolume,
            supportLevel: supportStrike,
            resistanceLevel: resistanceStrike,
            totalCallOIChange: totalCallOIChange,
            totalPutOIChange: totalPutOIChange,
            indiaVix: indiaVix,
            previousClose: previousClose,
            lotSize: lotSize,
            significantMoveThreshold: significantMovePercent,
            strongMoveThreshold: strongMovePercent
        )
    }

    // MARK: - Option Scoring

    private func scoreOption(option: OptionData, context: AnalysisContext) -> OptionScore {
        var reasoning: [ScoreReasoning] = []

        // 0. Moneyness Score (CRITICAL - filter out deep ITM/OTM)
        let (moneynessScore, moneynessReasoning) = calculateMoneynessScore(option: option, context: context)
        reasoning.append(moneynessReasoning)

        // =====================================================
        // NEW: Professional Metrics Calculation
        // =====================================================

        // Calculate Probability of Profit (POP)
        let pop = calculateProbabilityOfProfit(option: option, context: context)

        // Calculate IV Rank (estimate based on VIX and ATM IV)
        let ivRank = calculateIVRank(option: option, context: context)

        // Calculate IV Percentile within chain
        let ivPercentile = calculateIVPercentile(option: option, context: context)

        // Skew and term structure (chain-level)
        let skew = context.skew
        let termStructure = context.termStructure

        // Determine OI Signal (4-quadrant model)
        let oiSignal = determineOISignal(option: option, context: context)

        // Generate risk warnings
        let riskWarnings = generateRiskWarnings(option: option, context: context, ivRank: ivRank)

        // If moneyness is very poor, return low score immediately
        if moneynessScore < 20 {
            return OptionScore(
                option: option,
                oiScore: 30,
                volumeScore: 30,
                ivScore: 30,
                greeksScore: moneynessScore,
                pcrScore: 30,
                maxPainScore: 30,
                liquidityScore: 30,
                reasoning: reasoning,
                mlPrediction: nil,
                probabilityOfProfit: pop,
                ivRank: ivRank,
                oiSignal: oiSignal,
                riskWarnings: riskWarnings,
                ivPercentile: ivPercentile,
                ivPercentileScore: nil,
                skewValue: skew,
                skewScore: nil,
                termStructure: termStructure,
                termStructureScore: nil
            )
        }

        // 1. OI Score (20%)
        let (oiScore, oiReasoning) = calculateOIScore(option: option, context: context)
        reasoning.append(oiReasoning)

        // 2. Volume Score (10%)
        let (volumeScore, volumeReasoning) = calculateVolumeScore(option: option, context: context)
        reasoning.append(volumeReasoning)

        // 3. IV Score (using IV Rank now)
        let (ivScore, ivReasoning) = calculateIVScore(option: option, context: context)
        reasoning.append(ivReasoning)

        // 3a. IV Percentile Score
        let (ivPercentileScore, ivPercentileReasoning) = calculateIVPercentileScore(ivPercentile: ivPercentile)
        reasoning.append(ivPercentileReasoning)

        // 3b. Skew Score
        let (skewScore, skewReasoning) = calculateSkewScore(skew: skew, isCall: option.optionType == .call)
        reasoning.append(skewReasoning)

        // 3c. Term Structure Score
        let (termScore, termReasoning) = calculateTermStructureScore(termStructure: termStructure)
        reasoning.append(termReasoning)

        // 4. Greeks Score (15%)
        let (greeksScore, greeksReasoning) = calculateGreeksScore(option: option, context: context)
        reasoning.append(greeksReasoning)

        // 5. PCR Score (10%)
        let (pcrScore, pcrReasoning) = calculatePCRScore(option: option, context: context)
        reasoning.append(pcrReasoning)

        // 6. Max Pain Score (5% - reduced weight)
        let (maxPainScore, maxPainReasoning) = calculateMaxPainScore(option: option, context: context)
        reasoning.append(maxPainReasoning)

        // 7. Liquidity Score (5%)
        let (liquidityScore, liquidityReasoning) = calculateLiquidityScore(option: option, context: context)
        reasoning.append(liquidityReasoning)

        // 8. ML Prediction Score (Bonus)
        if useMLPredictions {
            let (_, mlReasoning) = calculateMLScore(option: option, context: context)
            reasoning.append(mlReasoning)
        }

        // Blend moneyness into Greeks score (reduced moneyness weight from 67% to 50% for more variety)
        let adjustedGreeksScore = (moneynessScore * 0.50) + (greeksScore * 0.50)

        return OptionScore(
            option: option,
            oiScore: oiScore,
            volumeScore: volumeScore,
            ivScore: ivScore,
            greeksScore: adjustedGreeksScore,
            pcrScore: pcrScore,
            maxPainScore: maxPainScore,
            liquidityScore: liquidityScore,
            reasoning: reasoning,
            mlPrediction: useMLPredictions ? getMLPrediction(option: option, context: context) : nil,
            probabilityOfProfit: pop,
            ivRank: ivRank,
            oiSignal: oiSignal,
            riskWarnings: riskWarnings,
            ivPercentile: ivPercentile,
            ivPercentileScore: ivPercentileScore,
            skewValue: skew,
            skewScore: skewScore,
            termStructure: termStructure,
            termStructureScore: termScore
        )
    }

    // =====================================================
    // MARK: - Professional Metrics Calculations
    // =====================================================

    /// Calculate IV Percentile within current chain distribution (0-100)
    private func calculateIVPercentile(option: OptionData, context: AnalysisContext) -> Double? {
        let iv = option.impliedVolatility
        let values = context.ivValues.filter { $0 > 0 }.sorted()
        guard !values.isEmpty else { return nil }

        let count = values.count
        let belowOrEqual = values.prefix { $0 <= iv }.count
        let percentile = (Double(belowOrEqual) / Double(count)) * 100
        return min(100, max(0, percentile))
    }

    /// Calculate put-call IV skew using strikes closest to ATM
    private func calculateSkew(optionChain: [OptionChainRow], atmStrike: Double?) -> Double? {
        guard let atm = atmStrike else { return nil }
        let sorted = optionChain.sorted { $0.strikePrice < $1.strikePrice }

        let puts = sorted.filter { $0.strikePrice < atm && ($0.putOption?.impliedVolatility ?? 0) > 0 }
        let calls = sorted.filter { $0.strikePrice > atm && ($0.callOption?.impliedVolatility ?? 0) > 0 }

        let putSlice = puts.suffix(3)
        let callSlice = calls.prefix(3)

        guard !putSlice.isEmpty, !callSlice.isEmpty else { return nil }

        let avgPutIV = putSlice.compactMap { $0.putOption?.impliedVolatility }.reduce(0, +) / Double(putSlice.count)
        let avgCallIV = callSlice.compactMap { $0.callOption?.impliedVolatility }.reduce(0, +) / Double(callSlice.count)

        return avgPutIV - avgCallIV
    }

    /// Determine term structure using ATM IV vs India VIX (proxy for longer-term vol)
    private func determineTermStructure(atmIV: Double, indiaVix: Double?) -> TermStructure? {
        guard let vix = indiaVix, vix > 0 else { return nil }
        let diff = atmIV - vix
        if diff >= 3 { return .inverted }
        if diff <= -3 { return .contango }
        return .flat
    }

    /// Calculate Probability of Profit using Black-Scholes d2
    /// POP = N(d2) for calls, N(-d2) for puts
    private func calculateProbabilityOfProfit(option: OptionData, context: AnalysisContext) -> Double {
        let spot = context.spotPrice
        let strike = option.strikePrice
        let iv = option.impliedVolatility  // Already stored as decimal (e.g., 0.15 for 15%)
        let daysToExpiry = max(1, option.daysToExpiry)
        let timeToExpiry = Double(daysToExpiry) / 365.0
        let r = riskFreeRate
        let isCall = option.optionType == .call

        guard iv > 0, timeToExpiry > 0 else {
            // Fallback based on moneyness
            let isITM = isCall ? (strike < spot) : (strike > spot)
            return isITM ? 60.0 : 40.0
        }

        let sqrtT = sqrt(timeToExpiry)

        // d2 = [ln(S/K) + (r - σ²/2) * T] / (σ * √T)
        let d2 = (log(spot / strike) + (r - 0.5 * iv * iv) * timeToExpiry) / (iv * sqrtT)

        // N(d2) = probability call expires ITM
        // N(-d2) = probability put expires ITM
        let pop: Double
        if isCall {
            pop = normalCDF(d2) * 100
        } else {
            pop = normalCDF(-d2) * 100
        }

        return min(95, max(5, pop))  // Clamp between 5-95%
    }

    /// Estimate IV Rank based on current IV vs VIX and ATM IV
    /// Without 52-week history, we use VIX as a proxy for market volatility regime
    private func calculateIVRank(option: OptionData, context: AnalysisContext) -> Double {
        let currentIV = option.impliedVolatility
        let atmIV = context.atmIV

        // If we have India VIX, use it as volatility regime indicator
        if let vix = context.indiaVix {
            // VIX typical range: 10 (low) to 30 (high), extreme 40+
            // Map VIX to IV Rank: VIX 10 = Rank 10, VIX 20 = Rank 50, VIX 30 = Rank 90

            // Also compare option IV to ATM IV
            let ivRatioToATM = atmIV > 0 ? currentIV / atmIV : 1.0

            // VIX-based component (60% weight)
            let vixRank = min(100, max(0, (vix - 10) / 25 * 100))

            // IV vs ATM component (40% weight)
            // If option IV > ATM IV, it's relatively expensive
            let ivVsATMRank = min(100, max(0, (ivRatioToATM - 0.8) / 0.4 * 100))

            let combinedRank = (vixRank * 0.6) + (ivVsATMRank * 0.4)
            return min(100, max(0, combinedRank))
        }

        // Fallback: Use IV vs ATM IV ratio only
        if atmIV > 0 {
            let ivRatio = currentIV / atmIV
            // ivRatio 0.8 = rank 0, ivRatio 1.0 = rank 50, ivRatio 1.2 = rank 100
            let rank = (ivRatio - 0.8) / 0.4 * 100
            return min(100, max(0, rank))
        }

        return 50.0  // Default to middle if no reference
    }

    /// Determine OI Signal using the 4-quadrant Price + OI model
    private func determineOISignal(option: OptionData, context: AnalysisContext) -> OISignal {
        let changeInOI = option.changeInOI
        let intradayMove = context.intradayMovePercent
        let isCall = option.optionType == .call

        // Thresholds for significant changes
        let significantOIChange = context.maxChangeInOI > 0 ?
            Double(abs(changeInOI)) / Double(context.maxChangeInOI) > 0.1 : abs(changeInOI) > 1000
        let significantPriceMove = abs(intradayMove) >= context.significantMoveThreshold

        guard significantOIChange || significantPriceMove else {
            return .neutral
        }

        let priceUp = intradayMove > 0
        let oiUp = changeInOI > 0

        // For CALLS:
        // Price Up + OI Up = Long Buildup (Bullish)
        // Price Down + OI Up = Short Buildup (Bearish - call writers)
        // Price Up + OI Down = Short Covering (Weak Bullish)
        // Price Down + OI Down = Long Unwinding (Weak Bearish)

        // For PUTS:
        // Price Down + OI Up = Long Buildup (Bearish - put buyers)
        // Price Up + OI Up = Short Buildup (Bullish - put writers)
        // Price Down + OI Down = Short Covering (Weak Bearish)
        // Price Up + OI Down = Long Unwinding (Weak Bullish)

        if isCall {
            if priceUp && oiUp { return .longBuildup }
            if !priceUp && oiUp { return .shortBuildup }
            if priceUp && !oiUp { return .shortCovering }
            if !priceUp && !oiUp { return .longUnwinding }
        } else {
            // For puts, interpretation is opposite
            if !priceUp && oiUp { return .longBuildup }  // Put buying = bearish
            if priceUp && oiUp { return .shortBuildup }   // Put writing = bullish
            if !priceUp && !oiUp { return .shortCovering }
            if priceUp && !oiUp { return .longUnwinding }
        }

        return .neutral
    }

    /// Generate severity-weighted risk warnings
    private func generateRiskWarnings(option: OptionData, context: AnalysisContext, ivRank: Double) -> [String] {
        return generateStructuredRiskWarnings(option: option, context: context, ivRank: ivRank)
            .map { $0.message }
    }

    /// Generate structured risk warnings with severity levels for weighted penalties
    private func generateStructuredRiskWarnings(option: OptionData, context: AnalysisContext, ivRank: Double) -> [RiskWarning] {
        var warnings: [RiskWarning] = []
        let isCall = option.optionType == .call

        // 1. Theta Decay Warning
        let daysToExpiry = option.daysToExpiry
        if daysToExpiry <= 3 {
            warnings.append(RiskWarning(
                message: "EXTREME theta decay (<3 days) - scalping only",
                severity: .critical
            ))
        } else if daysToExpiry <= 7 {
            warnings.append(RiskWarning(
                message: "Rapid theta decay - avoid holding overnight",
                severity: .minor
            ))
        }

        // 2. IV Rank Warning (buying expensive options)
        if ivRank > 70 {
            warnings.append(RiskWarning(
                message: "High IV Rank (\(Int(ivRank))) - options expensive, IV crush risk",
                severity: .severe
            ))
        }

        // 3. Low Liquidity Warning
        let bidAskSpread = option.askPrice - option.bidPrice
        let spreadPercent = option.lastTradedPrice > 0 ? (bidAskSpread / option.lastTradedPrice) * 100 : 100
        if spreadPercent > 5 {
            warnings.append(RiskWarning(
                message: "Wide spread (\(String(format: "%.1f", spreadPercent))%) - slippage risk",
                severity: .moderate
            ))
        }

        // 4. Deep OTM Warning
        let distancePercent = abs(option.strikePrice - context.spotPrice) / context.spotPrice * 100
        let isOTM = isCall ? (option.strikePrice > context.spotPrice) : (option.strikePrice < context.spotPrice)
        if isOTM && distancePercent > 3 {
            warnings.append(RiskWarning(
                message: "Deep OTM (\(String(format: "%.1f", distancePercent))%) - low probability",
                severity: .severe
            ))
        }

        // 5. Against Trend Warning
        let isBullishTrend = context.isBullishMove
        let isBearishTrend = context.isBearishMove
        if isCall && isBearishTrend && context.isStrongBearishMove {
            warnings.append(RiskWarning(
                message: "Buying call against strong bearish momentum",
                severity: .critical
            ))
        } else if isCall && isBearishTrend {
            warnings.append(RiskWarning(
                message: "Buying call against bearish momentum",
                severity: .severe
            ))
        } else if !isCall && isBullishTrend && context.isStrongBullishMove {
            warnings.append(RiskWarning(
                message: "Buying put against strong bullish momentum",
                severity: .critical
            ))
        } else if !isCall && isBullishTrend {
            warnings.append(RiskWarning(
                message: "Buying put against bullish momentum",
                severity: .severe
            ))
        }

        // 6. Low Volume Warning
        if option.volume < 100 {
            warnings.append(RiskWarning(
                message: "Low volume (\(option.volume)) - liquidity concern",
                severity: .moderate
            ))
        }

        return warnings
    }

    // MARK: - Moneyness Score (Critical for filtering)

    private func calculateMoneynessScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let strike = option.strikePrice
        let spot = context.spotPrice
        let isCall = option.optionType == .call

        // Calculate distance from ATM as percentage
        let distancePercent = abs(strike - spot) / spot * 100

        // Determine if ITM or OTM
        let isITM = isCall ? (strike < spot) : (strike > spot)

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        // =====================================================
        // GENERIC POST-MOVE ADJUSTMENT
        // =====================================================
        // When market has moved significantly in either direction:
        // - ITM options aligned with move are momentum plays (good)
        // - OTM options aligned with move are continuation plays (ideal)
        // - Options against the move direction are penalized

        let intradayMove = context.intradayMovePercent
        let moveMultiplier = context.moveStrengthMultiplier

        // Determine if option aligns with market direction
        let optionAlignsWithMove = (isCall && context.isBullishMove) || (!isCall && context.isBearishMove)
        let optionAlignsWithStrongMove = (isCall && context.isStrongBullishMove) || (!isCall && context.isStrongBearishMove)

        // Format move string for descriptions
        let moveStr = intradayMove >= 0 ? "+\(String(format: "%.2f", intradayMove))" : String(format: "%.2f", intradayMove)

        // Adjusted scoring based on market movement
        if distancePercent <= 0.5 {
            // ATM (within 0.5% of spot) - BEST
            score = 95
            impact = .bullish
            description = "ATM strike - optimal for directional trades with high gamma"
        } else if distancePercent <= 1.5 {
            // Near ATM (0.5-1.5%)
            score = 85
            impact = .bullish
            description = "Near ATM - good balance of premium and probability"
        } else if distancePercent <= 3.0 {
            // Slightly OTM/ITM (1.5-3%)
            if isITM {
                // ITM options aligned with move are momentum plays
                if optionAlignsWithMove {
                    score = 60 + (10 * moveMultiplier)  // Dynamic boost based on move strength
                    impact = .bullish
                    description = "Slightly ITM \(isCall ? "call" : "put") after \(moveStr)% move - momentum play"
                } else {
                    score = 60
                    impact = .neutral
                    description = "Slightly ITM - higher premium, lower leverage"
                }
            } else {
                // OTM options aligned with move are IDEAL for new entries
                if optionAlignsWithMove {
                    score = 75 + (10 * moveMultiplier)  // Dynamic boost
                    impact = .bullish
                    description = "Slightly OTM \(isCall ? "call" : "put") for momentum continuation after \(moveStr)% move"
                } else {
                    score = 75
                    impact = .bullish
                    description = "Slightly OTM - good risk/reward with reasonable probability"
                }
            }
        } else if distancePercent <= 5.0 {
            // Moderately OTM/ITM (3-5%)
            if isITM {
                // After strong moves, moderately ITM is still reasonable
                if optionAlignsWithStrongMove {
                    score = 40 + (15 * moveMultiplier)  // Boosted for strong momentum
                    impact = .neutral
                    description = "Moderately ITM \(isCall ? "call" : "put") - has intrinsic value after \(moveStr)% move"
                } else {
                    score = 40
                    impact = .bearish
                    description = "Moderately ITM - expensive premium, acts like underlying"
                }
            } else {
                // Moderately OTM - good for new entries after any significant move
                if optionAlignsWithMove {
                    score = 55 + (15 * moveMultiplier)  // Boosted for momentum plays
                    impact = .bullish
                    description = "Moderately OTM \(isCall ? "call" : "put") - cost-effective momentum continuation"
                } else {
                    score = 55
                    impact = .neutral
                    description = "Moderately OTM - lower cost but reduced probability"
                }
            }
        } else if distancePercent <= 8.0 {
            // Deep OTM/ITM (5-8%)
            if isITM {
                // After very strong moves, deep ITM still has value
                if optionAlignsWithStrongMove {
                    score = 20 + (15 * moveMultiplier)  // Boosted from 20
                    impact = .neutral
                    description = "Deep ITM - high intrinsic value, lower gamma"
                } else {
                    score = 20
                    impact = .bearish
                    description = "Deep ITM (\(String(format: "%.1f", distancePercent))% from spot) - poor leverage, avoid"
                }
            } else {
                // Deep OTM — on 0 DTE with strong aligned move, these can still be valid momentum plays
                if optionAlignsWithMove && option.daysToExpiry <= 1 {
                    score = 50 + (10 * moveMultiplier)
                    impact = .neutral
                    description = "Deep OTM \(isCall ? "call" : "put") - momentum scalp on expiry day"
                } else if optionAlignsWithStrongMove {
                    score = 40 + (10 * moveMultiplier)
                    impact = .neutral
                    description = "Deep OTM - speculative momentum play after strong move"
                } else {
                    score = 35
                    impact = .bearish
                    description = "Deep OTM - low probability of profit"
                }
            }
        } else {
            // Very deep OTM/ITM (>8%)
            if isITM {
                score = 15  // At least has intrinsic value
                impact = .bearish
                description = "Very deep ITM (\(String(format: "%.1f", distancePercent))% from spot) - not recommended"
            } else {
                score = 15
                impact = .bearish
                description = "Very deep OTM - extremely low probability, lottery ticket"
            }
        }

        return (score, ScoreReasoning(
            factor: "Strike Position",
            description: description,
            impact: impact,
            weight: 0.20,
            score: score
        ))
    }

    // MARK: - ML Prediction

    private func getMLPrediction(option: OptionData, context: AnalysisContext) -> OptionPrediction {
        let mlContext = MLPredictionContext(
            atmIV: context.atmIV,
            pcr: context.putCallRatio,
            maxPainStrike: context.maxPainStrike,
            spotPrice: context.spotPrice,
            supportLevel: context.supportLevel,
            resistanceLevel: context.resistanceLevel
        )

        let features = OptionMLFeatures.from(option: option, context: mlContext)
        return mlPredictor.predictOption(features)
    }

    private func calculateMLScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let prediction = getMLPrediction(option: option, context: context)

        let score: Double
        let impact: ScoreReasoning.Impact
        var description: String

        switch prediction.signal {
        case .strongBuy:
            score = 95
            impact = .bullish
            description = "ML Model: STRONG BUY signal"
        case .buy:
            score = 75
            impact = .bullish
            description = "ML Model: BUY signal"
        case .hold:
            score = 50
            impact = .neutral
            description = "ML Model: HOLD signal"
        case .sell:
            score = 30
            impact = .bearish
            description = "ML Model: SELL signal - consider avoiding"
        case .strongSell:
            score = 10
            impact = .bearish
            description = "ML Model: STRONG SELL signal - avoid"
        }

        description += " (\(prediction.confidencePercentage)% confidence)"

        if prediction.probability > 0.6 {
            description += ". \(prediction.displayProbability) profit probability"
        }

        return (score, ScoreReasoning(
            factor: "ML Prediction",
            description: description,
            impact: impact,
            weight: 0.15,  // ML gets 15% weight as bonus
            score: score
        ))
    }

    // MARK: - Individual Score Calculations

    private func calculateOIScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let isCall = option.optionType == .call
        let changeInOI = option.changeInOI
        let oi = option.openInterest

        var score: Double = 50  // Base score
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        // Normalize change in OI
        let normalizedChange = context.maxChangeInOI > 0 ?
            Double(abs(changeInOI)) / Double(context.maxChangeInOI) : 0

        if changeInOI > 0 {
            // Positive OI buildup
            if isCall {
                // Call OI buildup = bullish sentiment
                score = 50 + (normalizedChange * 50)
                impact = .bullish
                description = "Strong OI buildup (+\(formatNumber(changeInOI))) indicates buying interest"
            } else {
                // Put OI buildup = bearish for underlying, but provides support
                if option.strikePrice < context.spotPrice {
                    // Put OI below spot = support
                    score = 60 + (normalizedChange * 30)
                    impact = .bullish
                    description = "Put OI buildup below spot provides support at \(Int(option.strikePrice))"
                } else {
                    score = 40 - (normalizedChange * 20)
                    impact = .bearish
                    description = "Put OI buildup above spot indicates bearish pressure"
                }
            }
        } else if changeInOI < 0 {
            // OI unwinding
            score = 40 - (normalizedChange * 20)
            impact = .neutral
            description = "OI unwinding (\(formatNumber(changeInOI))) suggests position closure"
        } else {
            description = "No significant OI change"
        }

        // Bonus for high absolute OI (liquidity)
        if context.maxOI > 0 {
            let oiRatio = Double(oi) / Double(context.maxOI)
            if oiRatio > 0.5 {
                score += 10
                description += ". High OI indicates good liquidity"
            }
        }

        score = min(100, max(0, score))

        return (score, ScoreReasoning(
            factor: "OI Buildup",
            description: description,
            impact: impact,
            weight: 0.25,
            score: score
        ))
    }

    private func calculateVolumeScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let volume = option.volume
        let oi = option.openInterest

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        // Volume to OI ratio
        let volumeOIRatio = oi > 0 ? Double(volume) / Double(oi) : 0

        // Volume vs average
        let volumeVsAvg = context.avgVolume > 0 ? Double(volume) / context.avgVolume : 1

        if volumeOIRatio > 1.0 {
            // High activity - more volume than OI
            score = 70 + min(30, volumeOIRatio * 10)
            impact = .bullish
            description = "Volume surge! Volume/OI ratio of \(String(format: "%.1f", volumeOIRatio))x indicates strong momentum"
        } else if volumeVsAvg > 2.0 {
            score = 65 + min(25, (volumeVsAvg - 2) * 5)
            impact = .bullish
            description = "Volume \(String(format: "%.1f", volumeVsAvg))x above average signals increased interest"
        } else if volumeVsAvg > 1.0 {
            score = 55 + (volumeVsAvg - 1) * 10
            impact = .neutral
            description = "Moderate volume activity"
        } else if volume < 100 {
            score = 30
            impact = .bearish
            description = "Low volume (\(volume)) - poor liquidity"
        } else {
            description = "Average volume levels"
        }

        score = min(100, max(0, score))

        return (score, ScoreReasoning(
            factor: "Volume Surge",
            description: description,
            impact: impact,
            weight: 0.20,
            score: score
        ))
    }

    private func calculateIVScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let iv = option.impliedVolatility
        let atmIV = context.atmIV

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        let ivRatio = atmIV > 0 ? iv / atmIV : 1

        if ivRatio < 0.85 {
            // Undervalued (IV significantly lower than ATM)
            score = 80 + (1 - ivRatio) * 50
            impact = .bullish
            description = "IV \(String(format: "%.1f", iv))% is below ATM (\(String(format: "%.1f", atmIV))%) - potentially undervalued"
        } else if ivRatio < 0.95 {
            score = 65
            impact = .bullish
            description = "IV slightly below ATM - fair value"
        } else if ivRatio > 1.15 {
            // Overvalued
            score = 30 - (ivRatio - 1.15) * 30
            impact = .bearish
            description = "IV \(String(format: "%.1f", iv))% is elevated vs ATM - potentially overvalued"
        } else if ivRatio > 1.05 {
            score = 45
            impact = .neutral
            description = "IV slightly above ATM"
        } else {
            score = 55
            description = "IV near ATM level - fairly valued"
        }

        score = min(100, max(0, score))

        return (score, ScoreReasoning(
            factor: "IV Valuation",
            description: description,
            impact: impact,
            weight: 0.15,
            score: score
        ))
    }

    private func calculateIVPercentileScore(ivPercentile: Double?) -> (Double, ScoreReasoning) {
        guard let percentile = ivPercentile else {
            return (50, ScoreReasoning(
                factor: "IV Percentile",
                description: "IV percentile unavailable",
                impact: .neutral,
                weight: 0.06,
                score: 50
            ))
        }

        // Lower percentile = cheaper premium (better for buying)
        let score = min(100, max(0, 100 - percentile))
        let impact: ScoreReasoning.Impact = percentile < 40 ? .bullish : (percentile > 70 ? .bearish : .neutral)
        let description = "IV at \(Int(percentile))th percentile within chain"

        return (score, ScoreReasoning(
            factor: "IV Percentile",
            description: description,
            impact: impact,
            weight: 0.06,
            score: score
        ))
    }

    private func calculateSkewScore(skew: Double?, isCall: Bool) -> (Double, ScoreReasoning) {
        guard let skew = skew else {
            return (50, ScoreReasoning(
                factor: "Skew",
                description: "Skew unavailable",
                impact: .neutral,
                weight: 0.02,
                score: 50
            ))
        }

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        if isCall {
            // High positive skew = puts overpriced, calls relatively cheaper
            if skew <= 2 {
                score = 65
                impact = .bullish
                description = "Low put skew (\(String(format: "%.1f", skew))) favors calls"
            } else if skew >= 8 {
                score = 35
                impact = .bearish
                description = "High put skew (\(String(format: "%.1f", skew))) — calls less favored"
            } else {
                score = 52
                description = "Moderate put skew (\(String(format: "%.1f", skew)))"
            }
        } else {
            // For puts, higher skew is favorable (put premiums richer)
            if skew >= 6 {
                score = 70
                impact = .bullish
                description = "High put skew (\(String(format: "%.1f", skew))) favors puts"
            } else if skew <= 0 {
                score = 35
                impact = .bearish
                description = "Negative skew (\(String(format: "%.1f", skew))) — puts less favored"
            } else {
                score = 52
                description = "Moderate put skew (\(String(format: "%.1f", skew)))"
            }
        }

        return (score, ScoreReasoning(
            factor: "Skew",
            description: description,
            impact: impact,
            weight: 0.02,
            score: score
        ))
    }

    private func calculateTermStructureScore(termStructure: TermStructure?) -> (Double, ScoreReasoning) {
        guard let termStructure = termStructure else {
            return (50, ScoreReasoning(
                factor: "Term Structure",
                description: "Term structure unavailable",
                impact: .neutral,
                weight: 0.02,
                score: 50
            ))
        }

        let score: Double
        let impact: ScoreReasoning.Impact
        switch termStructure {
        case .contango:
            score = 70
            impact = .bullish
        case .flat:
            score = 55
            impact = .neutral
        case .inverted:
            score = 35
            impact = .bearish
        }

        return (score, ScoreReasoning(
            factor: "Term Structure",
            description: termStructure.description,
            impact: impact,
            weight: 0.02,
            score: score
        ))
    }

    private func calculateGreeksScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let delta = abs(option.delta ?? 0.5)
        let gamma = option.gamma ?? 0
        let theta = abs(option.theta ?? 0)
        let ltp = option.lastTradedPrice

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        // Delta scoring - prefer 0.3 to 0.7 range for directional trades
        if delta >= 0.4 && delta <= 0.6 {
            score = 80
            impact = .bullish
            description = "Optimal delta (\(String(format: "%.2f", delta))) for directional trades"
        } else if delta >= 0.3 && delta <= 0.7 {
            score = 65
            impact = .bullish
            description = "Good delta range (\(String(format: "%.2f", delta)))"
        } else if delta > 0.8 {
            score = 45
            impact = .neutral
            description = "Deep ITM (delta \(String(format: "%.2f", delta))) - high premium, lower leverage"
        } else if delta < 0.2 {
            score = 35
            impact = .bearish
            description = "Deep OTM (delta \(String(format: "%.2f", delta))) - low probability of profit"
        }

        // Gamma bonus for near-ATM
        if gamma > 0.01 {
            score += 10
            description += ". High gamma for accelerated gains"
        }

        // Theta penalty relative to premium
        let thetaRatio = ltp > 0 ? theta / ltp : 0
        if thetaRatio > 0.03 {
            score -= 10
            description += ". High theta decay relative to premium"
        }

        score = min(100, max(0, score))

        return (score, ScoreReasoning(
            factor: "Greeks Position",
            description: description,
            impact: impact,
            weight: 0.15,
            score: score
        ))
    }

    private func calculatePCRScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let pcr = context.putCallRatio
        let isCall = option.optionType == .call

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        if pcr > 1.2 {
            // High PCR - bearish sentiment, but contrarian bullish for calls
            if isCall {
                score = 70
                impact = .bullish
                description = "High PCR (\(String(format: "%.2f", pcr))) suggests oversold - bullish for calls"
            } else {
                score = 55
                impact = .neutral
                description = "High PCR aligns with put buying"
            }
        } else if pcr > 1.0 {
            if isCall {
                score = 60
                impact = .bullish
                description = "Elevated PCR supports call positions"
            } else {
                score = 50
                description = "Neutral PCR for puts"
            }
        } else if pcr < 0.8 {
            // Low PCR - bullish sentiment
            if isCall {
                score = 55
                description = "Low PCR indicates bullish crowd - be cautious"
            } else {
                score = 65
                impact = .bullish
                description = "Low PCR (\(String(format: "%.2f", pcr))) - contrarian opportunity for puts"
            }
        } else {
            description = "PCR near neutral (\(String(format: "%.2f", pcr)))"
        }

        score = min(100, max(0, score))

        return (score, ScoreReasoning(
            factor: "PCR Context",
            description: description,
            impact: impact,
            weight: 0.10,
            score: score
        ))
    }

    private func calculateMaxPainScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        guard let maxPain = context.maxPainStrike else {
            return (50, ScoreReasoning(
                factor: "Max Pain",
                description: "Max pain data not available",
                impact: .neutral,
                weight: 0.10,
                score: 50
            ))
        }

        let strike = option.strikePrice
        let spot = context.spotPrice
        let isCall = option.optionType == .call

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        let distanceFromMaxPain = abs(strike - maxPain)
        let distancePercentage = maxPain > 0 ? (distanceFromMaxPain / maxPain) * 100 : 0

        // Spot vs Max Pain analysis
        let spotVsMaxPain = spot - maxPain

        if distancePercentage < 1 {
            // Strike very close to max pain
            score = 70
            impact = .bullish
            description = "Strike near max pain (\(Int(maxPain))) - high probability zone"
        } else if distancePercentage < 2 {
            score = 60
            description = "Strike within 2% of max pain"
        }

        // Directional alignment
        if isCall && spotVsMaxPain < 0 {
            // Spot below max pain - bullish pull towards max pain
            score += 15
            impact = .bullish
            description += ". Spot below max pain suggests upward gravitational pull"
        } else if !isCall && spotVsMaxPain > 0 {
            // Spot above max pain - bearish pull towards max pain
            score += 15
            impact = .bullish
            description += ". Spot above max pain suggests downward gravitational pull"
        }

        score = min(100, max(0, score))

        return (score, ScoreReasoning(
            factor: "Max Pain",
            description: description,
            impact: impact,
            weight: 0.10,
            score: score
        ))
    }

    private func calculateLiquidityScore(option: OptionData, context: AnalysisContext) -> (Double, ScoreReasoning) {
        let bidAskSpread = option.askPrice - option.bidPrice
        let ltp = option.lastTradedPrice
        let spreadPercentage = ltp > 0 ? (bidAskSpread / ltp) * 100 : 100

        var score: Double = 50
        var impact: ScoreReasoning.Impact = .neutral
        var description = ""

        if spreadPercentage < 1 {
            score = 90
            impact = .bullish
            description = "Excellent liquidity - tight spread (\(String(format: "%.1f", spreadPercentage))%)"
        } else if spreadPercentage < 3 {
            score = 70
            impact = .bullish
            description = "Good liquidity - reasonable spread"
        } else if spreadPercentage < 5 {
            score = 50
            description = "Average liquidity"
        } else if spreadPercentage < 10 {
            score = 35
            impact = .bearish
            description = "Wide spread (\(String(format: "%.1f", spreadPercentage))%) - poor liquidity"
        } else {
            score = 20
            impact = .bearish
            description = "Very wide spread - avoid due to slippage risk"
        }

        score = min(100, max(0, score))

        return (score, ScoreReasoning(
            factor: "Liquidity",
            description: description,
            impact: impact,
            weight: 0.05,
            score: score
        ))
    }

    // MARK: - Trade Suggestion Generation

    private func passesStrictFilters(score: OptionScore, option: OptionData, context: AnalysisContext, isCall: Bool) -> (Bool, String?) {
        // On 0 DTE with strong aligned move, lower the strict threshold for momentum plays
        let isBearishAligned = !isCall && context.isBearishMove
        let isBullishAligned = isCall && context.isBullishMove
        let alignsWithMove = isBearishAligned || isBullishAligned
        let effectiveStrictMin: Double
        if option.daysToExpiry <= 1 && alignsWithMove && context.moveStrengthMultiplier >= 1.25 {
            effectiveStrictMin = 45  // Relaxed for strong momentum on expiry day
        } else {
            effectiveStrictMin = strictMinScoreThreshold  // 60
        }
        if score.overallScore < effectiveStrictMin {
            return (false, "strict score <\(Int(effectiveStrictMin))")
        }
        if score.confidence == .low && score.overallScore < 55 {
            return (false, "low confidence + low score")
        }
        // Allow options with minor warnings (theta, spread) — only block on 3+ critical warnings
        // On expiry day (0 DTE), exclude theta warnings since extreme decay is expected
        let criticalWarnings = score.riskWarnings.filter { warning in
            if option.daysToExpiry <= 1 && warning.contains("theta") {
                return false  // Theta decay is expected on expiry day, not a risk signal
            }
            return warning.contains("EXTREME") || warning.contains("Deep OTM") || warning.contains("against")
        }
        if criticalWarnings.count >= 2 {
            return (false, "multiple critical warnings")
        }
        if let ivRank = score.ivRank, ivRank > 85 {
            return (false, "IV Rank too high")
        }
        // Only reject inverted term structure for 1-3 DTE; on expiry day (0 DTE)
        // term structure is irrelevant — all value is intrinsic
        if let termStructure = score.termStructure,
           termStructure == .inverted,
           option.daysToExpiry >= 1, option.daysToExpiry <= 3 {
            return (false, "inverted term structure near expiry")
        }
        return (true, nil)
    }

    private func generateSuggestions(scores: [OptionScore], context: AnalysisContext, isCall: Bool, marketBias: MarketBias, regime: MarketRegime? = nil) -> [AITradeSuggestion] {
        var suggestions: [AITradeSuggestion] = []

        // Determine if option type aligns with market direction
        let isBullishMarket = marketBias == .bullish || marketBias == .strongBullish
        let isBearishMarket = marketBias == .bearish || marketBias == .strongBearish
        let optionAlignsWithMarket = (isCall && isBullishMarket) || (!isCall && isBearishMarket)

        // Generic movement detection - works for any move magnitude
        let moveMultiplier = context.moveStrengthMultiplier
        let optionsAlignWithMove = (isCall && context.isBullishMove) || (!isCall && context.isBearishMove)

        // Generate suggestions for calls/puts
        for score in scores {
            let option = score.option

            // ==========================================
            // PROFESSIONAL QUALITY FILTERS
            // ==========================================

            // 1. Minimum Score Threshold (adjusted for market alignment and regime)
            var effectiveMinScore: Double = minScoreThreshold  // 50 base

            if optionsAlignWithMove {
                effectiveMinScore = max(40.0, minScoreThreshold - (10.0 * moveMultiplier))
            }

            // Market regime adjustments
            if let regime = regime {
                switch regime {
                case .trending:
                    if optionsAlignWithMove { effectiveMinScore -= 5 }  // More permissive for momentum
                case .rangeBound:
                    // Penalize deep OTM in range-bound
                    let distPct = abs(option.strikePrice - context.spotPrice) / context.spotPrice * 100
                    if distPct > 3 { effectiveMinScore += 5 }
                case .volatile:
                    // Require higher liquidity in volatile markets
                    if option.volume < 1000 { effectiveMinScore += 5 }
                }
            }

            if score.overallScore < effectiveMinScore {
                continue
            }

            // 2. Skip options with no price data
            if option.lastTradedPrice <= 0 {
                continue
            }

            // 3. Minimum Volume Filter (liquidity)
            if option.volume < minLiquidity {
                continue
            }

            // 4. Minimum Open Interest Filter (reliable data)
            if option.openInterest < minOpenInterest {
                continue
            }

            // 5. Delta Range Filter (avoid deep ITM/OTM)
            let absDelta = abs(option.delta ?? 0.5)
            if absDelta < minDelta || absDelta > maxDelta {
                continue
            }

            // 6. Days to Expiry Filter (avoid theta crush)
            if option.daysToExpiry < minDaysToExpiry {
                continue
            }

            // Calculate spread percent for display
            let spreadPercent = option.lastTradedPrice > 0 ?
                ((option.askPrice - option.bidPrice) / option.lastTradedPrice) * 100 : 0

            // 7. Bid-Ask Spread Filter
            if spreadPercent > maxBidAskSpreadPercent {
                continue
            }

            // Market alignment bonus
            var alignmentBonus = 0.0
            if optionAlignsWithMarket {
                alignmentBonus = 5.0
            }
            if optionsAlignWithMove {
                alignmentBonus += 5.0 * moveMultiplier
            }

            // OI Signal bonus/penalty for scoring variety
            if let oiSignal = score.oiSignal {
                switch oiSignal {
                case .longBuildup:
                    alignmentBonus += isCall ? 15 : 0
                case .shortBuildup:
                    alignmentBonus += isCall ? -10 : 10
                case .shortCovering:
                    alignmentBonus += isCall ? 8 : 0
                case .longUnwinding:
                    alignmentBonus += isCall ? -5 : 5
                case .neutral:
                    break
                }
            }

            // Calculate target and stop-loss
            let (targetPrice, stopLossPrice, targetSpot, stopLossSpot) = calculateTargetAndStopLoss(
                option: option,
                score: score,
                context: context,
                isCall: isCall
            )

            // 8. Risk-Reward calculation with minimum threshold
            let potentialProfit = abs(targetPrice - option.lastTradedPrice)
            let potentialLoss = abs(option.lastTradedPrice - stopLossPrice)
            let riskReward = potentialLoss > 0 ? potentialProfit / potentialLoss : 1.5

            let effectiveMinRR: Double
            if option.daysToExpiry <= 0 {
                effectiveMinRR = 0.8
            } else if option.daysToExpiry <= 1 {
                effectiveMinRR = 1.0
            } else {
                effectiveMinRR = minRiskRewardRatio
            }

            if riskReward < effectiveMinRR {
                continue
            }

            // 9. Strict no-trade gate — relax threshold when aligned with significant move
            let effectiveStrictThreshold: Double
            if optionsAlignWithMove && context.moveMagnitude >= context.significantMoveThreshold {
                effectiveStrictThreshold = 50  // Relaxed from 60 when aligned with significant move
            } else {
                effectiveStrictThreshold = strictMinScoreThreshold  // 60
            }

            // Use relaxed threshold for strict check
            if score.overallScore < effectiveStrictThreshold {
                let strictCheck = passesStrictFilters(score: score, option: option, context: context, isCall: isCall)
                if !strictCheck.0 {
                    continue
                }
            }

            // Generate reasoning summary
            var reasoning = generateReasoningSummary(score: score)
            reasoning.insert(generateRiskAssessment(option: option, riskReward: riskReward, spreadPercent: spreadPercent), at: 0)

            let timeframe = determineTimeframe(option: option, score: score)

            let whyBuy = generateWhyBuyReasons(
                option: option,
                score: score,
                context: context,
                isCall: isCall,
                riskReward: riskReward
            )

            let riskFactors = generateRiskFactors(
                option: option,
                score: score,
                context: context,
                spreadPercent: spreadPercent
            )

            // Generate structured risk warnings for the suggestion
            let ivRank = score.ivRank ?? 50
            let structuredWarnings = generateStructuredRiskWarnings(option: option, context: context, ivRank: ivRank)

            let suggestion = AITradeSuggestion(
                option: option,
                score: score,
                direction: .buy,
                entryPrice: option.lastTradedPrice,
                targetPrice: targetPrice,
                stopLossPrice: stopLossPrice,
                targetSpot: targetSpot,
                stopLossSpot: stopLossSpot,
                timeframe: timeframe,
                reasoning: reasoning,
                whyBuy: whyBuy,
                riskFactors: riskFactors,
                structuredWarnings: structuredWarnings
            )

            suggestions.append(suggestion)
        }

        // Sort by score, best first — then apply maxSuggestions limit AT THE END (bug fix)
        return Array(suggestions
            .sorted { $0.score.overallScore > $1.score.overallScore }
            .prefix(maxSuggestions))
    }

    private func generateRiskAssessment(option: OptionData, riskReward: Double, spreadPercent: Double) -> String {
        var riskLevel: String
        var assessment: String

        // Determine risk level based on multiple factors
        let absDelta = abs(option.delta ?? 0.5)
        let daysToExpiry = option.daysToExpiry

        if absDelta >= 0.4 && absDelta <= 0.6 && daysToExpiry >= 5 && riskReward >= 2.5 && spreadPercent < 2 {
            riskLevel = "LOW RISK"
            assessment = "ATM option with good liquidity, favorable R:R (\(String(format: "%.1f", riskReward)):1)"
        } else if absDelta >= 0.3 && daysToExpiry >= 3 && riskReward >= 2.0 {
            riskLevel = "MODERATE RISK"
            assessment = "Good setup with R:R \(String(format: "%.1f", riskReward)):1, \(daysToExpiry) days to expiry"
        } else {
            riskLevel = "HIGHER RISK"
            assessment = "Trade with caution - R:R \(String(format: "%.1f", riskReward)):1"
        }

        return "[\(riskLevel)] \(assessment)"
    }

    private func calculateTargetAndStopLoss(
        option: OptionData,
        score: OptionScore,
        context: AnalysisContext,
        isCall: Bool
    ) -> (targetPrice: Double, stopLossPrice: Double, targetSpot: Double, stopLossSpot: Double) {
        let currentSpot = context.spotPrice
        let entryPrice = option.lastTradedPrice

        // Calculate spot targets based on support/resistance
        let minSpotMove = currentSpot * 0.015  // 1.5% move
        var targetSpot: Double
        var stopLossSpot: Double

        if isCall {
            targetSpot = context.resistanceLevel ?? (currentSpot + minSpotMove)
            stopLossSpot = context.supportLevel ?? (currentSpot - minSpotMove * 0.5)
        } else {
            targetSpot = context.supportLevel ?? (currentSpot - minSpotMove)
            stopLossSpot = context.resistanceLevel ?? (currentSpot + minSpotMove * 0.5)
        }

        // =====================================================
        // VOLATILITY-ADJUSTED TARGET/SL FOR REALISTIC TRADING
        // =====================================================
        // Stop losses are now based on IV and time to expiry
        // Higher volatility = wider stop losses to avoid whipsaws

        var targetPrice: Double
        var stopLossPrice: Double

        // Get delta and IV for better calculation
        let absDelta = abs(option.delta ?? 0.5)
        let iv = option.impliedVolatility > 0 ? option.impliedVolatility : context.atmIV

        // Calculate days to expiry
        let daysToExpiry = max(1, option.daysToExpiry)

        // =====================================================
        // VOLATILITY MULTIPLIER
        // =====================================================
        // Base volatility is considered 15% (normal market)
        // Higher IV = wider SL, Lower IV = tighter SL
        let baseIV: Double = 0.15
        let ivMultiplier = max(1.0, min(2.5, iv / baseIV))  // 1.0 to 2.5x

        // Expiry multiplier - options on expiry day are extremely volatile
        var expiryMultiplier: Double = 1.0
        if daysToExpiry == 0 {
            expiryMultiplier = 2.0  // Expiry day - double the buffer
        } else if daysToExpiry <= 1 {
            expiryMultiplier = 1.5  // 1 day to expiry
        } else if daysToExpiry <= 3 {
            expiryMultiplier = 1.25  // 2-3 days to expiry
        }

        // Combined volatility factor
        let volatilityFactor = ivMultiplier * expiryMultiplier

        // =====================================================
        // BASE STOP LOSS PERCENTAGES (will be adjusted by volatility)
        // =====================================================
        var baseSLPercent: Double  // Base stop loss percentage
        var baseTargetPercent: Double  // Base target percentage

        if entryPrice >= 200 {
            // Expensive ATM/ITM options
            baseTargetPercent = 0.20    // +20% target
            baseSLPercent = 0.15        // -15% base stop loss
        } else if entryPrice >= 100 {
            // Mid-high range options
            baseTargetPercent = 0.25    // +25% target
            baseSLPercent = 0.18        // -18% base stop loss
        } else if entryPrice >= 50 {
            // Mid-range options
            baseTargetPercent = 0.30    // +30% target
            baseSLPercent = 0.20        // -20% base stop loss
        } else if entryPrice >= 20 {
            // Lower mid-range
            baseTargetPercent = 0.40    // +40% target
            baseSLPercent = 0.25        // -25% base stop loss
        } else if entryPrice >= 10 {
            // Cheaper options
            baseTargetPercent = 0.50    // +50% target
            baseSLPercent = 0.30        // -30% base stop loss
        } else if entryPrice >= 5 {
            // Low-priced options
            baseTargetPercent = 0.60    // +60% target
            baseSLPercent = 0.35        // -35% base stop loss
        } else {
            // Very cheap options
            baseTargetPercent = 1.00    // +100% target
            baseSLPercent = 0.40        // -40% base stop loss
        }

        // =====================================================
        // APPLY VOLATILITY ADJUSTMENT
        // =====================================================
        // Stop loss is widened based on volatility
        // Target is also increased proportionally
        let adjustedSLPercent = baseSLPercent * volatilityFactor
        let adjustedTargetPercent = baseTargetPercent * volatilityFactor

        // Cap maximum SL at 50% and minimum at 15%
        let finalSLPercent = max(0.15, min(0.50, adjustedSLPercent))
        // Cap maximum target at 150% and minimum at 20%
        let finalTargetPercent = max(0.20, min(1.50, adjustedTargetPercent))

        targetPrice = entryPrice * (1 + finalTargetPercent)
        stopLossPrice = entryPrice * (1 - finalSLPercent)

        // Adjust based on delta (higher delta = lower target %)
        if absDelta > 0.6 {
            // ITM options move more like underlying - reduce target slightly
            targetPrice = entryPrice + (targetPrice - entryPrice) * 0.85
        } else if absDelta < 0.3 {
            // OTM options - wider SL due to gamma risk
            stopLossPrice = stopLossPrice * 0.95  // 5% more buffer for OTM
        }

        // Ensure minimum R:R of 1.5:1 for practical trading
        let potentialProfit = targetPrice - entryPrice
        let potentialLoss = entryPrice - stopLossPrice
        if potentialLoss > 0 && potentialProfit / potentialLoss < 1.5 {
            // Adjust target up to achieve 1.5:1 R:R minimum
            targetPrice = entryPrice + (potentialLoss * 1.5)
        }

        // Final sanity checks
        if stopLossPrice >= entryPrice {
            stopLossPrice = entryPrice * 0.70
        }
        if targetPrice <= entryPrice {
            targetPrice = entryPrice * 1.25
        }

        return (targetPrice, stopLossPrice, targetSpot, stopLossSpot)
    }

    private func calculateOptionPrice(spot: Double, strike: Double, timeToExpiry: Double, iv: Double, isCall: Bool) -> Double {
        // Simplified Black-Scholes
        guard timeToExpiry > 0, iv > 0 else {
            return max(0, isCall ? spot - strike : strike - spot)
        }

        let r = riskFreeRate
        let sqrtT = sqrt(timeToExpiry)

        let d1 = (log(spot / strike) + (r + 0.5 * iv * iv) * timeToExpiry) / (iv * sqrtT)
        let d2 = d1 - iv * sqrtT

        let nd1 = normalCDF(d1)
        let nd2 = normalCDF(d2)
        let nMinusD1 = normalCDF(-d1)
        let nMinusD2 = normalCDF(-d2)

        if isCall {
            return spot * nd1 - strike * exp(-r * timeToExpiry) * nd2
        } else {
            return strike * exp(-r * timeToExpiry) * nMinusD2 - spot * nMinusD1
        }
    }

    private func normalCDF(_ x: Double) -> Double {
        let a1 = 0.254829592
        let a2 = -0.284496736
        let a3 = 1.421413741
        let a4 = -1.453152027
        let a5 = 1.061405429
        let p = 0.3275911

        let sign = x < 0 ? -1.0 : 1.0
        let absX = abs(x)
        let t = 1.0 / (1.0 + p * absX)
        let y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-absX * absX / 2)

        return 0.5 * (1.0 + sign * y)
    }

    private func generateReasoningSummary(score: OptionScore) -> [String] {
        var reasons: [String] = []

        // Get top 3 positive factors
        let positiveReasons = score.reasoning
            .filter { $0.impact == .bullish && $0.score > 60 }
            .sorted { $0.score > $1.score }
            .prefix(3)

        for reason in positiveReasons {
            reasons.append(reason.description)
        }

        if reasons.isEmpty {
            reasons.append("Moderate overall score based on technical factors")
        }

        return reasons
    }

    private func determineTimeframe(option: OptionData, score: OptionScore) -> String {
        let daysToExpiry = option.daysToExpiry

        if daysToExpiry <= 1 {
            return "Intraday"
        } else if daysToExpiry <= 3 {
            return "1-2 Days"
        } else if daysToExpiry <= 7 {
            return "3-5 Days"
        } else {
            return "1 Week+"
        }
    }

    // =====================================================
    // MARK: - Professional Reasoning Generation
    // =====================================================

    /// Generate "Why Buy" reasons for professional suggestion display
    private func generateWhyBuyReasons(
        option: OptionData,
        score: OptionScore,
        context: AnalysisContext,
        isCall: Bool,
        riskReward: Double
    ) -> [String] {
        var reasons: [String] = []

        // 1. OI Signal reason
        if let oiSignal = score.oiSignal {
            switch oiSignal {
            case .longBuildup:
                reasons.append("✓ \(oiSignal.rawValue): Price ↑ + OI ↑ (Strong \(isCall ? "bullish" : "bearish") signal)")
            case .shortBuildup:
                if !isCall {
                    reasons.append("✓ \(oiSignal.rawValue): Price ↓ + OI ↑ (Strong bearish signal)")
                }
            case .shortCovering:
                if isCall {
                    reasons.append("✓ \(oiSignal.rawValue): Price ↑ + OI ↓ (Shorts exiting)")
                }
            case .longUnwinding:
                if !isCall {
                    reasons.append("✓ \(oiSignal.rawValue): Price ↓ + OI ↓ (Longs exiting)")
                }
            case .neutral:
                break
            }
        }

        // 2. IV Rank reason
        if let ivRank = score.ivRank {
            if ivRank < 30 {
                reasons.append("✓ IV Rank \(Int(ivRank)): Options are CHEAP vs 52-week range")
            } else if ivRank < 50 {
                reasons.append("✓ IV Rank \(Int(ivRank)): Options fairly priced")
            }
        }

        // 2b. IV Percentile reason
        if let ivPercentile = score.ivPercentile {
            if ivPercentile < 40 {
                reasons.append("✓ IV Percentile \(Int(ivPercentile))%: Premiums relatively cheap")
            } else if ivPercentile < 60 {
                reasons.append("✓ IV Percentile \(Int(ivPercentile))%: Neutral premium level")
            }
        }

        // 3. Probability of Profit reason
        if let pop = score.probabilityOfProfit {
            if pop >= 45 {
                reasons.append("✓ POP \(Int(pop))%: Good probability of profit")
            } else if pop >= 35 {
                reasons.append("✓ POP \(Int(pop))%: Moderate probability")
            }
        }

        // 4. Delta reason
        if let delta = option.delta {
            let absDelta = abs(delta)
            if absDelta >= 0.4 && absDelta <= 0.6 {
                reasons.append("✓ Delta \(String(format: "%.2f", absDelta)): Optimal for directional trade")
            } else if absDelta >= 0.3 && absDelta <= 0.7 {
                reasons.append("✓ Delta \(String(format: "%.2f", absDelta)): Good directional exposure")
            }
        }

        // 5. Days to expiry reason
        let daysToExpiry = option.daysToExpiry
        if daysToExpiry >= 14 {
            reasons.append("✓ \(daysToExpiry) days to expiry: Manageable theta decay")
        }

        // 6. Risk-Reward reason
        if riskReward >= 2.0 {
            reasons.append("✓ R:R \(String(format: "%.1f", riskReward)):1: Excellent risk-reward")
        } else if riskReward >= 1.5 {
            reasons.append("✓ R:R \(String(format: "%.1f", riskReward)):1: Good risk-reward")
        }

        // 7. Momentum alignment
        let intradayMove = context.intradayMovePercent
        if isCall && context.isBullishMove {
            reasons.append("✓ Momentum: +\(String(format: "%.2f", intradayMove))% bullish move supports calls")
        } else if !isCall && context.isBearishMove {
            reasons.append("✓ Momentum: \(String(format: "%.2f", intradayMove))% bearish move supports puts")
        }

        // 8. Support/Resistance proximity
        if isCall {
            if let support = context.supportLevel {
                let distanceToSupport = ((context.spotPrice - support) / context.spotPrice) * 100
                if distanceToSupport < 2 {
                    reasons.append("✓ Near support at \(Int(support)) (Put OI wall)")
                }
            }
        } else {
            if let resistance = context.resistanceLevel {
                let distanceToResistance = ((resistance - context.spotPrice) / context.spotPrice) * 100
                if distanceToResistance < 2 {
                    reasons.append("✓ Near resistance at \(Int(resistance)) (Call OI wall)")
                }
            }
        }

        // 9. Skew / Term structure context
        if let skew = score.skewValue {
            if isCall && skew <= 2 {
                reasons.append("✓ Low put skew (\(String(format: "%.1f", skew))) — calls relatively cheaper")
            } else if !isCall && skew >= 6 {
                reasons.append("✓ High put skew (\(String(format: "%.1f", skew))) — puts favored")
            }
        }
        if let term = score.termStructure {
            if term == .contango {
                reasons.append("✓ Term structure in contango — favorable for buying")
            }
        }

        // Ensure at least one reason
        if reasons.isEmpty {
            reasons.append("✓ Score \(Int(score.overallScore)): Meets minimum criteria")
        }

        return Array(reasons.prefix(5))  // Max 5 reasons
    }

    /// Generate risk factors for professional suggestion display
    private func generateRiskFactors(
        option: OptionData,
        score: OptionScore,
        context: AnalysisContext,
        spreadPercent: Double
    ) -> [String] {
        var risks: [String] = []
        let isCall = option.optionType == .call
        let daysToExpiry = option.daysToExpiry

        // 1. Theta decay risk
        if daysToExpiry <= 3 {
            risks.append("⚠️ EXTREME theta decay - \(daysToExpiry) days left")
        } else if daysToExpiry <= 7 {
            risks.append("⚠️ Rapid theta decay - \(daysToExpiry) days left")
        } else if daysToExpiry <= 14 {
            risks.append("⚠️ Accelerating theta - monitor closely")
        }

        // 2. IV Rank risk
        if let ivRank = score.ivRank, ivRank > 70 {
            risks.append("⚠️ High IV Rank (\(Int(ivRank))) - IV crush risk after events")
        }

        // 2b. IV Percentile risk
        if let ivPercentile = score.ivPercentile, ivPercentile > 70 {
            risks.append("⚠️ High IV Percentile (\(Int(ivPercentile))%) - premiums expensive")
        }

        // 3. Liquidity risk
        if spreadPercent > 5 {
            risks.append("⚠️ Wide spread (\(String(format: "%.1f", spreadPercent))%) - slippage on exit")
        }

        // 4. Against momentum risk
        if isCall && context.isBearishMove {
            risks.append("⚠️ Buying call against bearish momentum")
        } else if !isCall && context.isBullishMove {
            risks.append("⚠️ Buying put against bullish momentum")
        }

        // 5. OI Signal warning
        if let oiSignal = score.oiSignal {
            if isCall && oiSignal == .shortBuildup {
                risks.append("⚠️ Short buildup in calls - resistance forming")
            } else if !isCall && oiSignal == .longBuildup {
                risks.append("⚠️ Long buildup in puts - may be crowded trade")
            }
        }

        // 5b. Skew / Term structure risk
        if let skew = score.skewValue {
            if isCall && skew > 8 {
                risks.append("⚠️ Put skew high (\(String(format: "%.1f", skew))) - calls less favored")
            } else if !isCall && skew < 0 {
                risks.append("⚠️ Negative skew — puts less favored")
            }
        }
        if let term = score.termStructure, term == .inverted {
            risks.append("⚠️ Inverted term structure - event premium risk")
        }

        // 6. Low probability warning
        if let pop = score.probabilityOfProfit, pop < 35 {
            risks.append("⚠️ Low POP (\(Int(pop))%) - high risk trade")
        }

        // 7. Deep OTM warning
        let distancePercent = abs(option.strikePrice - context.spotPrice) / context.spotPrice * 100
        let isOTM = isCall ? (option.strikePrice > context.spotPrice) : (option.strikePrice < context.spotPrice)
        if isOTM && distancePercent > 3 {
            risks.append("⚠️ Deep OTM (\(String(format: "%.1f", distancePercent))% away) - needs big move")
        }

        // 8. Low volume warning
        if option.volume < 500 {
            risks.append("⚠️ Low volume (\(option.volume)) - exit may be difficult")
        }

        return Array(risks.prefix(4))  // Max 4 risk factors
    }

    // MARK: - Market Analysis

    private func identifyAvoidList(callScores: [OptionScore], putScores: [OptionScore]) -> [OptionScore] {
        var avoidList: [OptionScore] = []

        // Add options with low scores that have specific red flags
        let allScores = callScores + putScores

        for score in allScores {
            if score.overallScore < 35 {
                let hasRedFlag = score.reasoning.contains { $0.impact == .bearish && $0.score < 30 }
                if hasRedFlag {
                    avoidList.append(score)
                }
            }
        }

        return Array(avoidList.prefix(5))
    }

    private func generateMarketInsights(context: AnalysisContext) -> [MarketInsight] {
        var insights: [MarketInsight] = []

        // =====================================================
        // INTRADAY MOVEMENT INSIGHT (Highest Priority)
        // Generic - works for any index (Nifty, BankNifty, etc.)
        // =====================================================
        let intradayMove = context.intradayMovePercent
        let movePercent = String(format: "%.2f", abs(intradayMove))

        if context.isStrongBullishMove {
            insights.append(MarketInsight(
                title: "Strong Bullish Move (+\(movePercent)%)",
                description: "Index up \(movePercent)% today. Momentum favors calls. Look for slightly OTM calls for continuation trades.",
                icon: "arrow.up.right.circle.fill",
                color: Color(hex: "00C805"),
                importance: .high
            ))
        } else if context.isBullishMove {
            insights.append(MarketInsight(
                title: "Bullish Momentum (+\(movePercent)%)",
                description: "Index gaining \(movePercent)%. Calls are favored for trend continuation.",
                icon: "arrow.up.circle.fill",
                color: Color(hex: "00C805"),
                importance: .high
            ))
        } else if context.isStrongBearishMove {
            insights.append(MarketInsight(
                title: "Strong Bearish Move (-\(movePercent)%)",
                description: "Index down \(movePercent)% today. Momentum favors puts. Look for slightly OTM puts for continuation trades.",
                icon: "arrow.down.right.circle.fill",
                color: Color(hex: "FF3B30"),
                importance: .high
            ))
        } else if context.isBearishMove {
            insights.append(MarketInsight(
                title: "Bearish Momentum (-\(movePercent)%)",
                description: "Index declining \(movePercent)%. Puts are favored for trend continuation.",
                icon: "arrow.down.circle.fill",
                color: Color(hex: "FF3B30"),
                importance: .high
            ))
        }

        // India VIX Insight (first as it's a key market indicator)
        if let vix = context.indiaVix {
            if vix > 25 {
                insights.append(MarketInsight(
                    title: "Extreme Fear (VIX \(String(format: "%.1f", vix)))",
                    description: "India VIX indicates panic. Option premiums are expensive. Consider selling strategies or wait for VIX to cool.",
                    icon: "flame.fill",
                    color: Color(hex: "FF3B30"),
                    importance: .high
                ))
            } else if vix > 20 {
                insights.append(MarketInsight(
                    title: "High Volatility (VIX \(String(format: "%.1f", vix)))",
                    description: "Elevated fear in markets. Good opportunity for option sellers - premiums are rich.",
                    icon: "waveform.path.ecg",
                    color: Color(hex: "FF9F0A"),
                    importance: .high
                ))
            } else if vix > 15 {
                insights.append(MarketInsight(
                    title: "Moderate VIX (\(String(format: "%.1f", vix)))",
                    description: "Normal volatility range. Both buying and selling strategies are viable.",
                    icon: "gauge.medium",
                    color: Color(hex: "007AFF"),
                    importance: .medium
                ))
            } else if vix > 12 {
                insights.append(MarketInsight(
                    title: "Low Volatility (VIX \(String(format: "%.1f", vix)))",
                    description: "Calm markets with cheap premiums. Good time for buying options.",
                    icon: "leaf.fill",
                    color: Color(hex: "00C805"),
                    importance: .medium
                ))
            } else {
                insights.append(MarketInsight(
                    title: "Extreme Calm (VIX \(String(format: "%.1f", vix)))",
                    description: "Historically low VIX often precedes volatility spikes. Consider buying cheap options for potential moves.",
                    icon: "exclamationmark.triangle.fill",
                    color: Color(hex: "00C805"),
                    importance: .high
                ))
            }
        }

        // PCR Insight
        let pcr = context.putCallRatio
        if pcr > 1.3 {
            insights.append(MarketInsight(
                title: "High Put-Call Ratio",
                description: "PCR at \(String(format: "%.2f", pcr)) indicates bearish sentiment. Contrarian traders may see this as bullish.",
                icon: "arrow.up.arrow.down.circle.fill",
                color: Color(hex: "FF9F0A"),
                importance: .high
            ))
        } else if pcr < 0.7 {
            insights.append(MarketInsight(
                title: "Low Put-Call Ratio",
                description: "PCR at \(String(format: "%.2f", pcr)) shows bullish sentiment. Market may be overconfident.",
                icon: "exclamationmark.triangle.fill",
                color: Color(hex: "FF9F0A"),
                importance: .high
            ))
        }

        // Max Pain Insight
        if let maxPain = context.maxPainStrike {
            let distanceToMaxPain = context.spotPrice - maxPain
            let percentage = (abs(distanceToMaxPain) / maxPain) * 100

            if percentage > 1 {
                let direction = distanceToMaxPain > 0 ? "above" : "below"
                insights.append(MarketInsight(
                    title: "Max Pain Level",
                    description: "Spot is \(String(format: "%.1f", percentage))% \(direction) max pain at \(Int(maxPain)). Expect gravitational pull.",
                    icon: "target",
                    color: Color(hex: "007AFF"),
                    importance: .medium
                ))
            }
        }

        // Support/Resistance Insight
        if let support = context.supportLevel, let resistance = context.resistanceLevel {
            insights.append(MarketInsight(
                title: "Key Levels",
                description: "Support at \(Int(support)) (Put OI), Resistance at \(Int(resistance)) (Call OI).",
                icon: "arrow.up.and.down.circle.fill",
                color: Color(hex: "BF5AF2"),
                importance: .medium
            ))
        }

        // OI Change Insight
        if context.totalCallOIChange > 100000 {
            insights.append(MarketInsight(
                title: "Call OI Buildup",
                description: "Significant call OI addition of \(formatNumber(context.totalCallOIChange)). Bullish positioning.",
                icon: "plus.circle.fill",
                color: Color(hex: "00C805"),
                importance: .high
            ))
        } else if context.totalPutOIChange > 100000 {
            insights.append(MarketInsight(
                title: "Put OI Buildup",
                description: "Significant put OI addition of \(formatNumber(context.totalPutOIChange)). Bearish positioning.",
                icon: "plus.circle.fill",
                color: Color(hex: "FF3B30"),
                importance: .high
            ))
        }

        return insights.sorted { $0.importance > $1.importance }
    }

    // MARK: - Market Regime Detection

    /// Detect market regime from VIX + intraday move for strategy adjustments
    private func detectMarketRegime(context: AnalysisContext) -> MarketRegime {
        let vix = context.indiaVix ?? 14.0
        let moveMagnitude = context.moveMagnitude

        // High VIX (>20) = Volatile regime
        if vix > 20 {
            return .volatile
        }

        // Strong directional move (>0.5%) = Trending
        if moveMagnitude >= context.strongMoveThreshold {
            return .trending
        }

        // Moderate move with moderate VIX = Trending
        if moveMagnitude >= context.significantMoveThreshold && vix >= 14 {
            return .trending
        }

        // Low VIX + small move = Range-Bound
        if vix < 14 && moveMagnitude < context.significantMoveThreshold {
            return .rangeBound
        }

        // Default: Range-Bound for calm markets
        if moveMagnitude < context.significantMoveThreshold {
            return .rangeBound
        }

        return .trending
    }

    private func determineMarketBias(context: AnalysisContext) -> MarketBias {
        var bullishPoints = 0
        var bearishPoints = 0

        // =====================================================
        // GENERIC: Intraday Price Movement Analysis (Highest Priority)
        // Points scale with move magnitude using moveStrengthMultiplier
        // =====================================================
        let intradayMove = context.intradayMovePercent
        let moveMultiplier = context.moveStrengthMultiplier

        // Calculate points based on move direction and strength (generic, not hardcoded)
        if context.isBullishMove {
            // Points scale with move strength: 1.0x = 2pts, 1.25x = 2.5pts, 1.5x = 3pts
            let points = Int(2.0 * moveMultiplier)
            bullishPoints += points
        } else if context.isBearishMove {
            let points = Int(2.0 * moveMultiplier)
            bearishPoints += points
        }

        // PCR Analysis
        if context.putCallRatio > 1.2 {
            bullishPoints += 2  // Contrarian bullish
        } else if context.putCallRatio < 0.8 {
            bearishPoints += 1  // Too bullish, potential reversal
        } else if context.putCallRatio > 1.0 {
            bullishPoints += 1
        } else {
            bearishPoints += 1
        }

        // Max Pain Analysis
        if let maxPain = context.maxPainStrike {
            if context.spotPrice < maxPain {
                bullishPoints += 2  // Spot below max pain = bullish pull
            } else if context.spotPrice > maxPain {
                bearishPoints += 2  // Spot above max pain = bearish pull
            }
        }

        // OI Change Analysis
        if context.totalCallOIChange > context.totalPutOIChange {
            bullishPoints += 1
        } else if context.totalPutOIChange > context.totalCallOIChange {
            bearishPoints += 1
        }

        // India VIX Analysis (contrarian indicator)
        if let vix = context.indiaVix {
            if vix > 25 {
                // Extreme fear often marks bottoms - contrarian bullish
                bullishPoints += 1
            } else if vix < 12 {
                // Complacency often precedes corrections - contrarian bearish
                bearishPoints += 1
            }
        }

        // Determine bias
        let netBias = bullishPoints - bearishPoints

        if netBias >= 4 {
            return .strongBullish
        } else if netBias >= 2 {
            return .bullish
        } else if netBias <= -4 {
            return .strongBearish
        } else if netBias <= -2 {
            return .bearish
        } else {
            return .neutral
        }
    }

    // MARK: - Unusual Options Activity Detection

    /// Detect unusual options activity that may indicate smart money flow
    private func detectUnusualActivity(optionChain: [OptionChainRow], context: AnalysisContext) -> [UnusualActivity] {
        var activities: [UnusualActivity] = []

        // Configuration for unusual activity thresholds (professional standards)
        let volumeMultipleThreshold: Double = 4.0     // Volume > 4x average is unusual
        let highVolumeMultiple: Double = 7.0          // Volume > 7x is very unusual (institutional)
        let premiumThreshold: Double = 10000000       // 1 Crore premium is significant
        let highPremiumThreshold: Double = 25000000   // 2.5 Crore+ is very significant (block trade)
        let oiChangeThreshold: Double = 0.4           // 40%+ OI change is unusual
        let minVolumeForActivity: Int = 1000          // Minimum volume to consider

        let avgVolume = context.avgVolume

        for row in optionChain {
            // Check calls
            if let call = row.callOption {
                let activities_ = checkOptionForUnusualActivity(
                    option: call,
                    avgVolume: avgVolume,
                    context: context,
                    volumeMultipleThreshold: volumeMultipleThreshold,
                    highVolumeMultiple: highVolumeMultiple,
                    premiumThreshold: premiumThreshold,
                    highPremiumThreshold: highPremiumThreshold,
                    oiChangeThreshold: oiChangeThreshold,
                    minVolumeForActivity: minVolumeForActivity
                )
                activities.append(contentsOf: activities_)
            }

            // Check puts
            if let put = row.putOption {
                let activities_ = checkOptionForUnusualActivity(
                    option: put,
                    avgVolume: avgVolume,
                    context: context,
                    volumeMultipleThreshold: volumeMultipleThreshold,
                    highVolumeMultiple: highVolumeMultiple,
                    premiumThreshold: premiumThreshold,
                    highPremiumThreshold: highPremiumThreshold,
                    oiChangeThreshold: oiChangeThreshold,
                    minVolumeForActivity: minVolumeForActivity
                )
                activities.append(contentsOf: activities_)
            }
        }

        // Sort by significance (high first) and then by premium value
        return activities.sorted { activity1, activity2 in
            if activity1.significance == activity2.significance {
                return activity1.premiumValue > activity2.premiumValue
            }
            return activity1.significance == .high
        }.prefix(10).map { $0 }  // Top 10 unusual activities
    }

    private func checkOptionForUnusualActivity(
        option: OptionData,
        avgVolume: Double,
        context: AnalysisContext,
        volumeMultipleThreshold: Double,
        highVolumeMultiple: Double,
        premiumThreshold: Double,
        highPremiumThreshold: Double,
        oiChangeThreshold: Double,
        minVolumeForActivity: Int = 1000
    ) -> [UnusualActivity] {
        var activities: [UnusualActivity] = []

        // Skip options with insufficient volume (noise filter)
        guard option.volume >= minVolumeForActivity else { return activities }

        // Skip options with very low OI (unreliable data)
        guard option.openInterest >= 500 else { return activities }

        let isCall = option.optionType == .call
        let optionTypeName = isCall ? "CE" : "PE"

        // Calculate metrics using context's lotSize
        let volumeMultiple = avgVolume > 0 ? Double(option.volume) / avgVolume : 0
        let premiumValue = Double(option.volume) * option.lastTradedPrice * Double(context.lotSize)
        let oiChangePercent = option.openInterest > 0 ?
            Double(abs(option.changeInOI)) / Double(option.openInterest) : 0

        // Determine trade direction based on OI change (since we don't have option's previous close)
        // OI increase with volume = new positions, OI decrease with volume = closing positions
        var tradeDirection: TradeDirection? = nil
        if option.changeInOI > 0 && isCall && context.isBullishMove {
            tradeDirection = .buy  // Call OI buildup in bullish market = buying
        } else if option.changeInOI > 0 && !isCall && context.isBearishMove {
            tradeDirection = .buy  // Put OI buildup in bearish market = buying
        } else if option.changeInOI < 0 {
            tradeDirection = .sell  // OI reduction = position closure
        }

        // 1. Check for Volume Spike
        if volumeMultiple >= volumeMultipleThreshold {
            let significance: UnusualActivity.Significance = volumeMultiple >= highVolumeMultiple ? .high : .medium
            let description = "\(Int(option.strikePrice)) \(optionTypeName) volume \(String(format: "%.1f", volumeMultiple))x above average. " +
                "Premium: \(formatLargeNumber(premiumValue)). " +
                (tradeDirection == .buy ? "Aggressive buying detected." : tradeDirection == .sell ? "Heavy selling pressure." : "Direction unclear.")

            activities.append(UnusualActivity(
                option: option,
                activityType: .volumeSpike,
                volumeMultiple: volumeMultiple,
                premiumValue: premiumValue,
                tradeDirection: tradeDirection,
                significance: significance,
                description: description
            ))
        }

        // 2. Check for Large Premium (even without volume spike)
        if premiumValue >= premiumThreshold && volumeMultiple < volumeMultipleThreshold {
            let significance: UnusualActivity.Significance = premiumValue >= highPremiumThreshold ? .high : .medium
            let description = "Large premium flow of \(formatLargeNumber(premiumValue)) in \(Int(option.strikePrice)) \(optionTypeName). " +
                (tradeDirection == .buy ? "Institutional buying suspected." : "Institutional selling suspected.")

            activities.append(UnusualActivity(
                option: option,
                activityType: .largePremium,
                volumeMultiple: volumeMultiple,
                premiumValue: premiumValue,
                tradeDirection: tradeDirection,
                significance: significance,
                description: description
            ))
        }

        // 3. Check for OI Surge (large OI change relative to existing OI)
        if oiChangePercent >= oiChangeThreshold && option.changeInOI > 0 {
            let significance: UnusualActivity.Significance = oiChangePercent >= 0.5 ? .high : .medium
            let description = "\(Int(option.strikePrice)) \(optionTypeName) OI surged by \(String(format: "%.0f", oiChangePercent * 100))%. " +
                "New OI: \(formatNumber(option.changeInOI)). " +
                (isCall ? "Call writers/buyers adding positions." : "Put writers/buyers adding positions.")

            activities.append(UnusualActivity(
                option: option,
                activityType: .oiSurge,
                volumeMultiple: volumeMultiple,
                premiumValue: premiumValue,
                tradeDirection: option.changeInOI > 0 ? .buy : .sell,
                significance: significance,
                description: description
            ))
        }

        return activities
    }

    // MARK: - Strategy Suggestions

    /// Strike interval per index — used for dynamic OTM offsets
    private func strikeInterval(for lotSize: Int) -> Double {
        // BANKNIFTY lot=30 → 100pt intervals; all others → 50pt
        return lotSize == 30 ? 100 : 50
    }

    /// Calculate net Greeks across all legs (buy = +1, sell = -1)
    private func calculateNetGreeks(legs: [StrategySuggestion.StrategyLeg]) -> (delta: Double, theta: Double, gamma: Double, vega: Double) {
        var d = 0.0, t = 0.0, g = 0.0, v = 0.0
        for leg in legs {
            let sign: Double = leg.action == .buy ? 1.0 : -1.0
            let qty = Double(leg.quantity)
            d += sign * qty * (leg.option.delta ?? 0)
            t += sign * qty * (leg.option.theta ?? 0)
            g += sign * qty * (leg.option.gamma ?? 0)
            v += sign * qty * (leg.option.vega ?? 0)
        }
        return (d, t, g, v)
    }

    /// Score a strategy on 5 weighted factors (0-100)
    private func scoreStrategy(
        strategyType: AIStrategyType,
        ivRank: Double,
        probability: Double?,
        maxProfit: Double?,
        maxLoss: Double?,
        legs: [StrategySuggestion.StrategyLeg],
        regime: MarketRegime?
    ) -> (score: Double, breakdown: ScoreBreakdown) {
        // 1. IV Alignment (25%) — how close ivRank is to strategy's preferred center
        let ivDistance = abs(ivRank - strategyType.preferredIVCenter)
        let ivAlignment = max(0, min(100, 100 - ivDistance * 1.5))

        // 2. POP (20%)
        let popScore: Double = {
            guard let p = probability, p > 0 else { return 50 }
            return min(100, p * 100)
        }()

        // 3. Risk:Reward (20%)
        let rrScore: Double = {
            guard let profit = maxProfit, let loss = maxLoss, loss > 0, profit > 0 else { return 50 }
            if profit > 100000 { return 80 } // unlimited profit
            let rr = profit / loss
            return min(95, max(20, rr * 30))
        }()

        // 4. Liquidity (15%) — average volume across legs
        let avgVol = legs.isEmpty ? 0 : legs.reduce(0.0) { $0 + Double($1.option.volume) } / Double(legs.count)
        let liqScore = min(100, max(10, avgVol / 50.0)) // 5000 vol → 100

        // 5. Regime Fit (20%)
        let regimeFitScore: Double = {
            guard let r = regime else { return 50 }
            switch r {
            case .rangeBound:
                return strategyType.isNeutral ? 90 : (strategyType == .bullCallSpread || strategyType == .bearPutSpread ? 60 : 30)
            case .volatile:
                return (strategyType == .straddle || strategyType == .strangle) ? 85 : (strategyType.isNeutral ? 50 : 40)
            case .trending:
                return (strategyType.isBullish || strategyType.isBearish) ? 85 : (strategyType == .ironCondor ? 25 : 50)
            }
        }()

        let breakdown = ScoreBreakdown(
            ivAlignment: ivAlignment,
            pop: popScore,
            riskReward: rrScore,
            liquidity: liqScore,
            regimeFit: regimeFitScore
        )

        let score = ivAlignment * 0.25 + popScore * 0.20 + rrScore * 0.20 + liqScore * 0.15 + regimeFitScore * 0.20
        return (min(100, max(0, score)), breakdown)
    }

    /// Generate strategy suggestions based on IV Rank, market conditions, regime, and option chain
    private func generateStrategySuggestions(
        optionChain: [OptionChainRow],
        context: AnalysisContext,
        marketBias: MarketBias
    ) -> [StrategySuggestion] {
        var suggestions: [StrategySuggestion] = []

        guard let atmStrike = context.atmStrike else { return suggestions }
        let atmRow = optionChain.first { abs($0.strikePrice - atmStrike) < 10 }

        let ivRank = context.indiaVix != nil ? calculateEstimatedIVRank(vix: context.indiaVix!) : 50.0
        let marketCondition = determineMarketCondition(bias: marketBias, ivRank: ivRank)
        let regime = detectMarketRegime(context: context)
        let interval = strikeInterval(for: context.lotSize)

        // 1. Directional strategies
        if marketBias == .bullish || marketBias == .strongBullish {
            if ivRank < 30 {
                if let atmCall = atmRow?.callOption {
                    suggestions.append(createNakedOptionStrategy(
                        option: atmCall, isCall: true, marketCondition: marketCondition,
                        ivRank: ivRank, lotSize: context.lotSize, regime: regime
                    ))
                }
            }
            // Always try a bull call spread for bullish (higher IV or as alternative)
            if let spread = createBullCallSpread(optionChain: optionChain, atmStrike: atmStrike, context: context, ivRank: ivRank, interval: interval, regime: regime) {
                suggestions.append(spread)
            }
        } else if marketBias == .bearish || marketBias == .strongBearish {
            if ivRank < 30 {
                if let atmPut = atmRow?.putOption {
                    suggestions.append(createNakedOptionStrategy(
                        option: atmPut, isCall: false, marketCondition: marketCondition,
                        ivRank: ivRank, lotSize: context.lotSize, regime: regime
                    ))
                }
            }
            if let spread = createBearPutSpread(optionChain: optionChain, atmStrike: atmStrike, context: context, ivRank: ivRank, interval: interval, regime: regime) {
                suggestions.append(spread)
            }
        }

        // 2. Neutral / range-bound strategies
        if regime == .rangeBound || marketBias == .neutral || ivRank > 50 {
            if ivRank > 50 {
                if let ironCondor = createIronCondor(optionChain: optionChain, context: context, ivRank: ivRank, interval: interval, regime: regime) {
                    suggestions.append(ironCondor)
                }
            }
            if ivRank > 40 {
                if let strangle = createStrangle(optionChain: optionChain, context: context, ivRank: ivRank, isBuying: false, interval: interval, regime: regime) {
                    suggestions.append(strangle)
                }
            }
        }

        // 3. Volatile regime → straddle at moderate IV
        if regime == .volatile && ivRank < 60 {
            if let straddle = createStraddle(optionChain: optionChain, context: context, ivRank: ivRank, regime: regime) {
                suggestions.append(straddle)
            }
        } else if ivRank < 30 && (marketBias == .neutral || abs(context.intradayMovePercent) < 0.2) {
            if let straddle = createStraddle(optionChain: optionChain, context: context, ivRank: ivRank, regime: regime) {
                suggestions.append(straddle)
            }
        }

        // Sort by score descending, limit to 5
        suggestions.sort { ($0.score ?? 0) > ($1.score ?? 0) }
        return Array(suggestions.prefix(5))
    }

    private func calculateEstimatedIVRank(vix: Double) -> Double {
        return min(100, max(0, (vix - 10) / 20 * 100))
    }

    private func determineMarketCondition(bias: MarketBias, ivRank: Double) -> String {
        let biasStr: String
        switch bias {
        case .strongBullish: biasStr = "Strong Bullish"
        case .bullish: biasStr = "Bullish"
        case .neutral: biasStr = "Neutral"
        case .bearish: biasStr = "Bearish"
        case .strongBearish: biasStr = "Strong Bearish"
        }
        let ivStr: String
        if ivRank < 30 { ivStr = "Low IV" }
        else if ivRank < 60 { ivStr = "Moderate IV" }
        else { ivStr = "High IV" }
        return "\(biasStr) + \(ivStr)"
    }

    private func createNakedOptionStrategy(option: OptionData, isCall: Bool, marketCondition: String, ivRank: Double, lotSize: Int, regime: MarketRegime?) -> StrategySuggestion {
        let leg = StrategySuggestion.StrategyLeg(option: option, action: .buy, quantity: 1, lotSize: lotSize)
        let premium = option.lastTradedPrice * Double(lotSize)

        var reasoning: [String] = []
        reasoning.append("✓ \(isCall ? "Bullish" : "Bearish") directional trade")
        reasoning.append("✓ IV Rank \(Int(ivRank)) - options are cheap")
        reasoning.append("✓ Simple single-leg strategy")
        reasoning.append("✓ Maximum loss limited to premium: \(formatLargeNumber(premium))")

        let delta = option.delta ?? 0.5
        let winProb = isCall ? abs(delta) : (1 - abs(delta))
        let safeProb = min(1.0, max(0.0, winProb))

        let greeks = calculateNetGreeks(legs: [leg])
        let scoring = scoreStrategy(
            strategyType: isCall ? .nakedCall : .nakedPut,
            ivRank: ivRank, probability: safeProb,
            maxProfit: 999999, maxLoss: premium,
            legs: [leg], regime: regime
        )

        return StrategySuggestion(
            strategyType: isCall ? .nakedCall : .nakedPut,
            legs: [leg],
            reasoning: reasoning,
            maxProfit: 999999,
            maxLoss: max(0, premium),
            breakeven: [isCall ? option.strikePrice + option.lastTradedPrice : option.strikePrice - option.lastTradedPrice],
            probability: safeProb,
            ivRankBased: true,
            marketCondition: marketCondition,
            score: scoring.score,
            scoreBreakdown: scoring.breakdown,
            netDelta: greeks.delta, netTheta: greeks.theta, netGamma: greeks.gamma, netVega: greeks.vega
        )
    }

    private func createBullCallSpread(optionChain: [OptionChainRow], atmStrike: Double, context: AnalysisContext, ivRank: Double, interval: Double, regime: MarketRegime?) -> StrategySuggestion? {
        let atmRow = optionChain.first { abs($0.strikePrice - atmStrike) < 10 }
        let otmStrike = atmStrike + interval * 2
        let otmRow = optionChain.first { $0.strikePrice > atmStrike && abs($0.strikePrice - otmStrike) <= interval }

        guard let buyCall = atmRow?.callOption, let sellCall = otmRow?.callOption else { return nil }

        let lotSize = context.lotSize
        let buyLeg = StrategySuggestion.StrategyLeg(option: buyCall, action: .buy, quantity: 1, lotSize: lotSize)
        let sellLeg = StrategySuggestion.StrategyLeg(option: sellCall, action: .sell, quantity: 1, lotSize: lotSize)
        let legs = [buyLeg, sellLeg]

        let netDebit = (buyCall.lastTradedPrice - sellCall.lastTradedPrice) * Double(lotSize)
        let maxProfit = (sellCall.strikePrice - buyCall.strikePrice) * Double(lotSize) - netDebit
        let breakeven = buyCall.strikePrice + (buyCall.lastTradedPrice - sellCall.lastTradedPrice)

        // POP from sell leg delta: pop = 1 - |sellDelta|
        let sellDelta = abs(sellCall.delta ?? 0.3)
        let pop = min(1.0, max(0.0, 1.0 - sellDelta))

        var reasoning: [String] = []
        reasoning.append("✓ Bullish spread - limited risk and reward")
        reasoning.append("✓ Net debit: \(formatLargeNumber(netDebit))")
        reasoning.append("✓ Sell call reduces cost when IV is elevated")
        reasoning.append("✓ Max profit at \(Int(sellCall.strikePrice))")

        let greeks = calculateNetGreeks(legs: legs)
        let scoring = scoreStrategy(
            strategyType: .bullCallSpread, ivRank: ivRank, probability: pop,
            maxProfit: maxProfit, maxLoss: netDebit, legs: legs, regime: regime
        )

        return StrategySuggestion(
            strategyType: .bullCallSpread,
            legs: legs,
            reasoning: reasoning,
            maxProfit: max(0, maxProfit.isFinite ? maxProfit : 0),
            maxLoss: max(0, netDebit.isFinite ? netDebit : 0),
            breakeven: [breakeven],
            probability: pop,
            ivRankBased: ivRank > 40,
            marketCondition: "Bullish + \(ivRank > 40 ? "Elevated" : "Low") IV",
            score: scoring.score,
            scoreBreakdown: scoring.breakdown,
            netDelta: greeks.delta, netTheta: greeks.theta, netGamma: greeks.gamma, netVega: greeks.vega
        )
    }

    private func createBearPutSpread(optionChain: [OptionChainRow], atmStrike: Double, context: AnalysisContext, ivRank: Double, interval: Double, regime: MarketRegime?) -> StrategySuggestion? {
        let atmRow = optionChain.first { abs($0.strikePrice - atmStrike) < 10 }
        let otmStrike = atmStrike - interval * 2
        let otmRow = optionChain.first { $0.strikePrice < atmStrike && abs($0.strikePrice - otmStrike) <= interval }

        guard let buyPut = atmRow?.putOption, let sellPut = otmRow?.putOption else { return nil }

        let lotSize = context.lotSize
        let buyLeg = StrategySuggestion.StrategyLeg(option: buyPut, action: .buy, quantity: 1, lotSize: lotSize)
        let sellLeg = StrategySuggestion.StrategyLeg(option: sellPut, action: .sell, quantity: 1, lotSize: lotSize)
        let legs = [buyLeg, sellLeg]

        let netDebit = (buyPut.lastTradedPrice - sellPut.lastTradedPrice) * Double(lotSize)
        let maxProfit = (buyPut.strikePrice - sellPut.strikePrice) * Double(lotSize) - netDebit
        let breakeven = buyPut.strikePrice - (buyPut.lastTradedPrice - sellPut.lastTradedPrice)

        // POP from sell leg delta
        let sellDelta = abs(sellPut.delta ?? 0.3)
        let pop = min(1.0, max(0.0, 1.0 - sellDelta))

        var reasoning: [String] = []
        reasoning.append("✓ Bearish spread - limited risk and reward")
        reasoning.append("✓ Net debit: \(formatLargeNumber(netDebit))")
        reasoning.append("✓ Sell put reduces cost when IV is elevated")
        reasoning.append("✓ Max profit at \(Int(sellPut.strikePrice))")

        let greeks = calculateNetGreeks(legs: legs)
        let scoring = scoreStrategy(
            strategyType: .bearPutSpread, ivRank: ivRank, probability: pop,
            maxProfit: maxProfit, maxLoss: netDebit, legs: legs, regime: regime
        )

        return StrategySuggestion(
            strategyType: .bearPutSpread,
            legs: legs,
            reasoning: reasoning,
            maxProfit: max(0, maxProfit.isFinite ? maxProfit : 0),
            maxLoss: max(0, netDebit.isFinite ? netDebit : 0),
            breakeven: [breakeven],
            probability: pop,
            ivRankBased: ivRank > 40,
            marketCondition: "Bearish + \(ivRank > 40 ? "Elevated" : "Low") IV",
            score: scoring.score,
            scoreBreakdown: scoring.breakdown,
            netDelta: greeks.delta, netTheta: greeks.theta, netGamma: greeks.gamma, netVega: greeks.vega
        )
    }

    private func createIronCondor(optionChain: [OptionChainRow], context: AnalysisContext, ivRank: Double, interval: Double, regime: MarketRegime?) -> StrategySuggestion? {
        guard let atmStrike = context.atmStrike else { return nil }

        let sellCallStrike = atmStrike + interval * 4
        let buyCallStrike = atmStrike + interval * 6
        let sellPutStrike = atmStrike - interval * 4
        let buyPutStrike = atmStrike - interval * 6

        guard let sellCallRow = optionChain.first(where: { abs($0.strikePrice - sellCallStrike) <= interval }),
              let buyCallRow = optionChain.first(where: { abs($0.strikePrice - buyCallStrike) <= interval }),
              let sellPutRow = optionChain.first(where: { abs($0.strikePrice - sellPutStrike) <= interval }),
              let buyPutRow = optionChain.first(where: { abs($0.strikePrice - buyPutStrike) <= interval }),
              let sellCall = sellCallRow.callOption,
              let buyCall = buyCallRow.callOption,
              let sellPut = sellPutRow.putOption,
              let buyPut = buyPutRow.putOption else { return nil }

        let lotSize = context.lotSize
        let legs = [
            StrategySuggestion.StrategyLeg(option: sellCall, action: .sell, quantity: 1, lotSize: lotSize),
            StrategySuggestion.StrategyLeg(option: buyCall, action: .buy, quantity: 1, lotSize: lotSize),
            StrategySuggestion.StrategyLeg(option: sellPut, action: .sell, quantity: 1, lotSize: lotSize),
            StrategySuggestion.StrategyLeg(option: buyPut, action: .buy, quantity: 1, lotSize: lotSize)
        ]

        let netCredit = (sellCall.lastTradedPrice + sellPut.lastTradedPrice - buyCall.lastTradedPrice - buyPut.lastTradedPrice) * Double(lotSize)
        let wingWidth = (buyCallRow.strikePrice - sellCallRow.strikePrice) * Double(lotSize)
        let maxLoss = wingWidth - netCredit

        // POP from sell deltas
        let sellCallDelta = abs(sellCall.delta ?? 0.2)
        let sellPutDelta = abs(sellPut.delta ?? 0.2)
        let pop = min(1.0, max(0.0, 1.0 - sellCallDelta - sellPutDelta))

        var reasoning: [String] = []
        reasoning.append("✓ High IV (\(Int(ivRank))) - premium selling strategy")
        reasoning.append("✓ Net credit: \(formatLargeNumber(netCredit))")
        reasoning.append("✓ Profit if market stays between \(Int(sellPutRow.strikePrice)) - \(Int(sellCallRow.strikePrice))")
        reasoning.append("✓ Defined risk with wing protection")

        let greeks = calculateNetGreeks(legs: legs)
        let scoring = scoreStrategy(
            strategyType: .ironCondor, ivRank: ivRank, probability: pop,
            maxProfit: netCredit, maxLoss: maxLoss, legs: legs, regime: regime
        )

        return StrategySuggestion(
            strategyType: .ironCondor,
            legs: legs,
            reasoning: reasoning,
            maxProfit: max(0, netCredit),
            maxLoss: max(0, maxLoss),
            breakeven: [sellPutRow.strikePrice - (netCredit / Double(lotSize)), sellCallRow.strikePrice + (netCredit / Double(lotSize))],
            probability: pop,
            ivRankBased: true,
            marketCondition: "Neutral + High IV",
            score: scoring.score,
            scoreBreakdown: scoring.breakdown,
            netDelta: greeks.delta, netTheta: greeks.theta, netGamma: greeks.gamma, netVega: greeks.vega
        )
    }

    private func createStrangle(optionChain: [OptionChainRow], context: AnalysisContext, ivRank: Double, isBuying: Bool, interval: Double, regime: MarketRegime?) -> StrategySuggestion? {
        guard let atmStrike = context.atmStrike else { return nil }

        let callStrike = atmStrike + interval * 3
        let putStrike = atmStrike - interval * 3

        guard let callRow = optionChain.first(where: { abs($0.strikePrice - callStrike) <= interval }),
              let putRow = optionChain.first(where: { abs($0.strikePrice - putStrike) <= interval }),
              let call = callRow.callOption,
              let put = putRow.putOption else { return nil }

        let action: StrategySuggestion.StrategyLeg.LegAction = isBuying ? .buy : .sell
        let lotSize = context.lotSize
        let legs = [
            StrategySuggestion.StrategyLeg(option: call, action: action, quantity: 1, lotSize: lotSize),
            StrategySuggestion.StrategyLeg(option: put, action: action, quantity: 1, lotSize: lotSize)
        ]

        let totalPremium = (call.lastTradedPrice + put.lastTradedPrice) * Double(lotSize)
        let pop = isBuying ? 0.30 : min(1.0, max(0.0, 1.0 - abs(call.delta ?? 0.2) - abs(put.delta ?? 0.2)))

        var reasoning: [String] = []
        if isBuying {
            reasoning.append("✓ Low IV (\(Int(ivRank))) - options are cheap")
            reasoning.append("✓ Profit from big move in either direction")
            reasoning.append("✓ Max loss limited to premium: \(formatLargeNumber(totalPremium))")
        } else {
            reasoning.append("✓ High IV (\(Int(ivRank))) - premium selling opportunity")
            reasoning.append("✓ Net credit: \(formatLargeNumber(totalPremium))")
            reasoning.append("✓ Profit if market stays between \(Int(putRow.strikePrice)) - \(Int(callRow.strikePrice))")
        }

        let greeks = calculateNetGreeks(legs: legs)
        let scoring = scoreStrategy(
            strategyType: .strangle, ivRank: ivRank, probability: pop,
            maxProfit: isBuying ? 999999 : totalPremium,
            maxLoss: isBuying ? totalPremium : 999999,
            legs: legs, regime: regime
        )

        return StrategySuggestion(
            strategyType: .strangle,
            legs: legs,
            reasoning: reasoning,
            maxProfit: isBuying ? 999999 : max(0, totalPremium),
            maxLoss: isBuying ? max(0, totalPremium) : 999999,
            breakeven: [putRow.strikePrice - (totalPremium / Double(lotSize)), callRow.strikePrice + (totalPremium / Double(lotSize))],
            probability: pop,
            ivRankBased: true,
            marketCondition: isBuying ? "Expecting Breakout + Low IV" : "Neutral + High IV",
            score: scoring.score,
            scoreBreakdown: scoring.breakdown,
            netDelta: greeks.delta, netTheta: greeks.theta, netGamma: greeks.gamma, netVega: greeks.vega
        )
    }

    private func createStraddle(optionChain: [OptionChainRow], context: AnalysisContext, ivRank: Double, regime: MarketRegime?) -> StrategySuggestion? {
        guard let atmStrike = context.atmStrike else { return nil }

        let atmRow = optionChain.first { abs($0.strikePrice - atmStrike) < 10 }
        guard let call = atmRow?.callOption, let put = atmRow?.putOption else { return nil }

        let lotSize = context.lotSize
        let legs = [
            StrategySuggestion.StrategyLeg(option: call, action: .buy, quantity: 1, lotSize: lotSize),
            StrategySuggestion.StrategyLeg(option: put, action: .buy, quantity: 1, lotSize: lotSize)
        ]

        let totalPremium = (call.lastTradedPrice + put.lastTradedPrice) * Double(lotSize)
        let breakevenDistance = totalPremium / Double(lotSize)

        var reasoning: [String] = []
        reasoning.append("✓ Low IV (\(Int(ivRank))) - options are cheap")
        reasoning.append("✓ ATM straddle for maximum gamma")
        reasoning.append("✓ Profit from big move in either direction")
        reasoning.append("✓ Need \(String(format: "%.0f", breakevenDistance)) point move to breakeven")

        let greeks = calculateNetGreeks(legs: legs)
        let scoring = scoreStrategy(
            strategyType: .straddle, ivRank: ivRank, probability: 0.35,
            maxProfit: 999999, maxLoss: totalPremium,
            legs: legs, regime: regime
        )

        return StrategySuggestion(
            strategyType: .straddle,
            legs: legs,
            reasoning: reasoning,
            maxProfit: 999999,
            maxLoss: max(0, totalPremium),
            breakeven: [atmStrike - breakevenDistance, atmStrike + breakevenDistance],
            probability: 0.35,
            ivRankBased: true,
            marketCondition: "Expecting Big Move + Low IV",
            score: scoring.score,
            scoreBreakdown: scoring.breakdown,
            netDelta: greeks.delta, netTheta: greeks.theta, netGamma: greeks.gamma, netVega: greeks.vega
        )
    }

    private func formatLargeNumber(_ number: Double) -> String {
        if abs(number) >= 10000000 {
            return String(format: "%.2f Cr", number / 10000000)
        } else if abs(number) >= 100000 {
            return String(format: "%.2f L", number / 100000)
        } else {
            return String(format: "%.0f", number)
        }
    }

    // MARK: - Helpers

    private func formatNumber(_ number: Int) -> String {
        if abs(number) >= 10000000 {
            return String(format: "%.1fCr", Double(number) / 10000000)
        } else if abs(number) >= 100000 {
            return String(format: "%.1fL", Double(number) / 100000)
        } else if abs(number) >= 1000 {
            return String(format: "%.1fK", Double(number) / 1000)
        } else {
            return "\(number)"
        }
    }
}
