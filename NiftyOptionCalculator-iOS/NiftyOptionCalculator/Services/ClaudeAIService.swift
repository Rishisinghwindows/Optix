import Foundation

// MARK: - Options AI Service (uses Gemini via Backend)

final class ClaudeAIService {
    static let shared = ClaudeAIService()

    private let apiBaseURL: String

    private init() {
        // Use the same base URL as other services
        self.apiBaseURL = AppEnvironment.current.apiBaseURL
    }

    // MARK: - Analyze Trade with AI (via Backend API)

    func analyzeTradeWithAI(
        suggestion: AITradeSuggestion,
        marketContext: MarketContext,
        optionChain: [OptionChainRow]? = nil
    ) async throws -> AIExplanation {
        // Build request body
        let requestBody = buildRequestBody(
            suggestion: suggestion,
            context: marketContext,
            optionChain: optionChain
        )

        // Call backend API
        guard let url = URL(string: "\(apiBaseURL)/options/analyze") else {
            throw AIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 30

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: requestBody)
        } catch {
            throw AIError.parseError
        }

        do {
            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw AIError.apiError
            }

            if httpResponse.statusCode == 200 {
                return try parseAPIResponse(data, suggestion: suggestion, context: marketContext, optionChain: optionChain)
            } else {
                // API error, falling back to local analysis
                // Fall back to local analysis
                return generateLocalAnalysis(suggestion: suggestion, context: marketContext, optionChain: optionChain)
            }
        } catch {
            // Network error, falling back to local analysis
            // Fall back to local analysis
            return generateLocalAnalysis(suggestion: suggestion, context: marketContext, optionChain: optionChain)
        }
    }

    // MARK: - Build Request Body

    private func buildRequestBody(
        suggestion: AITradeSuggestion,
        context: MarketContext,
        optionChain: [OptionChainRow]?
    ) -> [String: Any] {
        // Option data
        let option: [String: Any] = [
            "strike_price": suggestion.option.strikePrice,
            "option_type": suggestion.option.optionType == .call ? "CE" : "PE",
            "ltp": suggestion.option.lastTradedPrice,
            "iv": suggestion.option.impliedVolatility,
            "delta": suggestion.option.delta ?? 0,
            "theta": suggestion.option.theta ?? 0,
            "open_interest": suggestion.option.openInterest,
            "oi_change": suggestion.option.changeInOI,
            "volume": suggestion.option.volume,
            "days_to_expiry": suggestion.option.daysToExpiry
        ]

        // Market context
        let market: [String: Any] = [
            "spot_price": context.spotPrice,
            "pcr": context.pcr,
            "max_pain": context.maxPain as Any,
            "atm_strike": context.atmStrike,
            "index_name": context.indexName,
            "support": context.support as Any,
            "resistance": context.resistance as Any
        ]

        // Suggestion
        let suggestionData: [String: Any] = [
            "entry": suggestion.entryPrice,
            "target": suggestion.targetPrice,
            "stop_loss": suggestion.stopLossPrice,
            "risk_reward": suggestion.displayRiskReward,
            "score": Int(suggestion.score.overallScore)
        ]

        // Option chain (complete data for better AI analysis)
        var chainData: [[String: Any]] = []
        if let chain = optionChain {
            // Send up to 40 rows with complete data
            for row in chain.prefix(40) {
                var rowData: [String: Any] = ["strike": row.strikePrice]

                // Call option complete data
                if let call = row.callOption {
                    rowData["call_oi"] = call.openInterest
                    rowData["call_oi_change"] = call.changeInOI
                    rowData["call_ltp"] = call.lastTradedPrice
                    rowData["call_iv"] = call.impliedVolatility
                    rowData["call_volume"] = call.volume
                    rowData["call_delta"] = call.delta ?? 0
                    rowData["call_theta"] = call.theta ?? 0
                    rowData["call_bid"] = call.bidPrice
                    rowData["call_ask"] = call.askPrice
                }

                // Put option complete data
                if let put = row.putOption {
                    rowData["put_oi"] = put.openInterest
                    rowData["put_oi_change"] = put.changeInOI
                    rowData["put_ltp"] = put.lastTradedPrice
                    rowData["put_iv"] = put.impliedVolatility
                    rowData["put_volume"] = put.volume
                    rowData["put_delta"] = put.delta ?? 0
                    rowData["put_theta"] = put.theta ?? 0
                    rowData["put_bid"] = put.bidPrice
                    rowData["put_ask"] = put.askPrice
                }

                chainData.append(rowData)
            }
        }

        return [
            "option": option,
            "market_context": market,
            "suggestion": suggestionData,
            "option_chain": chainData
        ]
    }

    // MARK: - Parse API Response

    private func parseAPIResponse(_ data: Data, suggestion: AITradeSuggestion, context: MarketContext, optionChain: [OptionChainRow]?) throws -> AIExplanation {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw AIError.parseError
        }

        // Check if AI actually powered the response
        let aiPowered = json["ai_powered"] as? Bool ?? false
        let keyReason = json["key_reason"] as? String ?? "Analysis completed"

        // If AI is not powered or key_reason contains error indicators, use local analysis
        let isError = keyReason.lowercased().contains("unavailable") ||
                      keyReason.lowercased().contains("error") ||
                      keyReason.lowercased().contains("quota") ||
                      keyReason.lowercased().contains("api")

        // Check for generic responses that indicate AI didn't analyze properly
        let isGeneric = keyReason.lowercased() == "analysis completed" ||
                        keyReason.lowercased().contains("unable to") ||
                        keyReason.count < 15

        if !aiPowered || isError || isGeneric {
            // API returned error/generic response, using local analysis
            return generateLocalAnalysis(suggestion: suggestion, context: context, optionChain: optionChain)
        }

        let verdictStr = (json["verdict"] as? String)?.uppercased() ?? "WAIT"
        let apiVerdict: AIVerdict
        switch verdictStr {
        case "YES": apiVerdict = .yes
        case "NO": apiVerdict = .no
        default: apiVerdict = .wait
        }

        let apiWinProbability = json["win_probability"] as? Int ?? 50
        let riskWarning = json["risk_warning"] as? String ?? "Always use stop-loss"
        let alternative = json["better_alternative"] as? String

        // === VALIDATE API RESPONSE AGAINST ML PREDICTION ===
        // If there's a major discrepancy, use local analysis instead
        if let mlPrediction = suggestion.score.mlPrediction {
            let mlProb = Int(mlPrediction.probability * 100)
            let mlSignal = mlPrediction.signal
            let mlConfidence = mlPrediction.confidence

            // Check for major inconsistencies
            var hasInconsistency = false

            // ML says BUY/STRONG BUY with high confidence, but API says NO with low probability
            if (mlSignal == .strongBuy || mlSignal == .buy) && mlConfidence >= 0.7 {
                if apiVerdict == .no || apiWinProbability < 30 {
                    // API inconsistent with ML BUY signal, using local analysis
                    hasInconsistency = true
                }
            }

            // ML says SELL/STRONG SELL with high confidence, but API says YES with high probability
            if (mlSignal == .strongSell || mlSignal == .sell) && mlConfidence >= 0.7 {
                if apiVerdict == .yes || apiWinProbability > 70 {
                    // API inconsistent with ML SELL signal, using local analysis
                    hasInconsistency = true
                }
            }

            // Probability difference is too large (>40%)
            if abs(mlProb - apiWinProbability) > 40 {
                // API probability differs too much from ML, using local analysis
                hasInconsistency = true
            }

            if hasInconsistency {
                return generateLocalAnalysis(suggestion: suggestion, context: context, optionChain: optionChain)
            }
        }

        let fullResponse = """
        **Verdict**: \(apiVerdict.rawValue.uppercased()) (\(apiWinProbability)% win rate)

        **Key Reason**: \(keyReason)

        **Risk Warning**: \(riskWarning)

        \(alternative.map { "**Better Alternative**: \($0)" } ?? "")

        ---
        *Powered by Gemini AI*
        """

        return AIExplanation(
            verdict: apiVerdict,
            winProbability: apiWinProbability,
            keyReason: keyReason,
            riskWarning: riskWarning,
            betterAlternative: alternative,
            fullResponse: fullResponse
        )
    }

    // MARK: - Local Analysis Fallback

    private func generateLocalAnalysis(
        suggestion: AITradeSuggestion,
        context: MarketContext,
        optionChain: [OptionChainRow]?
    ) -> AIExplanation {
        let isCall = suggestion.option.optionType == .call
        let spot = context.spotPrice
        let pcr = context.pcr
        let daysToExpiry = suggestion.option.daysToExpiry
        let oiChange = suggestion.option.changeInOI
        let score = suggestion.score.overallScore
        let riskReward = suggestion.riskRewardRatio
        let selectedStrike = suggestion.option.strikePrice

        // Get ML prediction if available
        let mlPrediction = suggestion.score.mlPrediction

        // === ANALYZE OPTION CHAIN DATA ===
        let chainAnalysis = analyzeOptionChain(
            optionChain: optionChain,
            spot: spot,
            selectedStrike: selectedStrike,
            isCall: isCall
        )

        // Decision logic - align with ML prediction
        var reasons: [String] = []
        var warnings: [String] = []

        // Get ML signal strength
        let mlSignal = mlPrediction?.signal
        let mlConfidence = mlPrediction?.confidence ?? 0.5
        let hasStrongMLSignal = mlConfidence >= 0.7 && (mlSignal == .strongBuy || mlSignal == .buy || mlSignal == .strongSell || mlSignal == .sell)

        // === BASE PROBABILITY FROM ML ===
        var winProbability: Int
        if let ml = mlPrediction {
            // Start with ML's probability as base
            winProbability = Int(ml.probability * 100)

            switch ml.signal {
            case .strongBuy:
                reasons.append("ML predicts STRONG BUY with \(ml.confidencePercentage)% confidence")
            case .buy:
                reasons.append("ML predicts BUY signal")
            case .strongSell:
                warnings.append("ML predicts STRONG SELL signal")
            case .sell:
                warnings.append("ML predicts SELL signal")
            case .hold:
                warnings.append("ML shows neutral/HOLD - uncertain direction")
            }
        } else {
            // No ML prediction, start at 50%
            winProbability = 50
        }

        // === OPTION CHAIN ANALYSIS ADJUSTMENTS ===
        // IV Skew Analysis
        if let ivSkew = chainAnalysis.ivSkew {
            if isCall {
                // For calls, negative skew (call IV > put IV) is bearish
                if ivSkew < -0.02 {
                    winProbability -= 5
                    warnings.append("IV skew unfavorable for calls")
                } else if ivSkew > 0.03 {
                    winProbability += 5
                    reasons.append("IV skew supports call buying (put IV higher)")
                }
            } else {
                // For puts, positive skew (put IV > call IV) indicates fear
                if ivSkew > 0.03 {
                    winProbability += 5
                    reasons.append("High put IV indicates fear - supports puts")
                } else if ivSkew < -0.02 {
                    winProbability -= 5
                    warnings.append("Low put IV - market not fearful")
                }
            }
        }

        // Support/Resistance from OI
        if let resistance = chainAnalysis.resistanceFromCallOI {
            if isCall {
                let distanceToResistance = resistance - spot
                if distanceToResistance < 50 {
                    winProbability -= 8
                    warnings.append("Heavy call OI resistance at \(Int(resistance)) near spot")
                } else if selectedStrike < resistance {
                    winProbability += 3
                    reasons.append("Target below call OI resistance (\(Int(resistance)))")
                }
            }
        }

        if let support = chainAnalysis.supportFromPutOI {
            if !isCall {
                let distanceToSupport = spot - support
                if distanceToSupport < 50 {
                    winProbability -= 8
                    warnings.append("Heavy put OI support at \(Int(support)) near spot")
                } else if selectedStrike > support {
                    winProbability += 3
                    reasons.append("Target above put OI support (\(Int(support)))")
                }
            }
        }

        // Smart Money Activity
        if chainAnalysis.smartMoneyBullish && isCall {
            winProbability += 7
            reasons.append("Smart money accumulating calls")
        } else if chainAnalysis.smartMoneyBearish && !isCall {
            winProbability += 7
            reasons.append("Smart money accumulating puts")
        } else if chainAnalysis.smartMoneyBullish && !isCall {
            winProbability -= 5
            warnings.append("Smart money bullish - against puts")
        } else if chainAnalysis.smartMoneyBearish && isCall {
            winProbability -= 5
            warnings.append("Smart money bearish - against calls")
        }

        // Volume Confirmation
        if chainAnalysis.volumeConfirmation {
            winProbability += 5
            reasons.append("High volume confirms interest at this strike")
        }

        // Strike Position Analysis
        if chainAnalysis.strikeNearMaxOI {
            if isCall && chainAnalysis.maxCallOIStrike ?? 0 <= selectedStrike {
                winProbability -= 5
                warnings.append("Strike at/above max call OI - heavy resistance")
            } else if !isCall && chainAnalysis.maxPutOIStrike ?? 0 >= selectedStrike {
                winProbability -= 5
                warnings.append("Strike at/below max put OI - heavy support")
            }
        }

        // === ADJUST BASED ON SCORE ===
        if score >= 75 {
            winProbability += 8
            reasons.append("High score of \(Int(score))/100")
        } else if score >= 65 {
            winProbability += 4
            reasons.append("Good score of \(Int(score))/100")
        } else if score >= 50 {
            // Neutral, no change
            if reasons.isEmpty {
                reasons.append("Moderate score of \(Int(score))/100")
            }
        } else if score < 40 {
            winProbability -= 8
            warnings.append("Low score of \(Int(score))/100")
        }

        // === ADJUST BASED ON RISK REWARD ===
        if riskReward >= 2.5 {
            winProbability += 6
            reasons.append("Excellent R:R of \(suggestion.displayRiskReward)")
        } else if riskReward >= 2.0 {
            winProbability += 4
        } else if riskReward >= 1.5 {
            winProbability += 2
        } else if riskReward < 1.0 {
            winProbability -= 6
            warnings.append("Poor risk-reward ratio (\(suggestion.displayRiskReward))")
        }

        // === PCR CONTEXT ===
        if isCall {
            if pcr > 1.3 {
                winProbability += 5
                reasons.append("High PCR (\(String(format: "%.2f", pcr))) supports calls")
            } else if pcr < 0.7 {
                winProbability -= 5
                warnings.append("Low PCR - bearish sentiment")
            }
        } else {
            if pcr < 0.7 {
                winProbability += 5
                reasons.append("Low PCR supports puts")
            } else if pcr > 1.3 {
                winProbability -= 5
                warnings.append("High PCR - bullish sentiment")
            }
        }

        // === MAX PAIN ANALYSIS ===
        if let maxPain = context.maxPain {
            let distanceFromMaxPain = spot - maxPain
            let percentFromMaxPain = abs(distanceFromMaxPain) / spot * 100

            if isCall {
                // If spot is below max pain, market tends to move up
                if distanceFromMaxPain < -50 && percentFromMaxPain > 0.5 {
                    winProbability += 5
                    reasons.append("Spot below max pain - bullish pull")
                } else if distanceFromMaxPain > 100 {
                    winProbability -= 3
                    warnings.append("Spot above max pain")
                }
            } else {
                // If spot is above max pain, market tends to move down
                if distanceFromMaxPain > 50 && percentFromMaxPain > 0.5 {
                    winProbability += 5
                    reasons.append("Spot above max pain - bearish pull")
                } else if distanceFromMaxPain < -100 {
                    winProbability -= 3
                    warnings.append("Spot below max pain")
                }
            }
        }

        // === TIME DECAY RISK ===
        if daysToExpiry <= 2 {
            winProbability -= 12
            warnings.append("Critical: Only \(daysToExpiry) days left - extreme theta decay")
        } else if daysToExpiry <= 5 {
            winProbability -= 5
            warnings.append("Short expiry (\(daysToExpiry) days) increases risk")
        }

        // === OI ANALYSIS OF SELECTED OPTION ===
        if oiChange > 100000 {
            winProbability += 3
            reasons.append("Strong OI buildup (\(formatNumber(oiChange)))")
        } else if oiChange < -50000 {
            winProbability -= 5
            warnings.append("Long unwinding detected")
        }

        // === APPLY ML CONFIDENCE AS MODIFIER ===
        // If ML has a strong signal, trust it more
        if mlSignal == .strongBuy && mlConfidence >= 0.9 {
            // Boost for very strong ML signal
            winProbability = max(winProbability, 75)
        } else if mlSignal == .strongSell && mlConfidence >= 0.9 {
            // Cap for very strong sell signal
            winProbability = min(winProbability, 35)
        } else if mlSignal == .hold || mlConfidence < 0.6 {
            // Cap probability between 40-60% for uncertain signals
            winProbability = min(winProbability, 60)
            winProbability = max(winProbability, 40)
        }

        // Clamp final probability
        winProbability = max(20, min(90, winProbability))

        // === DETERMINE VERDICT ===
        // Align verdict with both ML signal and calculated probability
        let verdict: AIVerdict
        let keyReason: String
        let riskWarning: String
        var alternative: String? = nil

        // Strong BUY conditions - ML + Chain Analysis agree
        if (mlSignal == .strongBuy || mlSignal == .buy) && mlConfidence >= 0.7 && winProbability >= 65 {
            verdict = .yes
            keyReason = reasons.first ?? "ML and option chain analysis support this trade"
            riskWarning = warnings.first ?? "Always use strict stop-loss at ₹\(suggestion.displayStopLoss)"
        }
        // Strong SELL conditions
        else if (mlSignal == .strongSell || mlSignal == .sell) && mlConfidence >= 0.7 {
            verdict = .no
            keyReason = warnings.first ?? "ML signals against this trade"
            riskWarning = warnings.count > 1 ? warnings[1] : "Consider opposite direction"
            alternative = isCall ?
                "Consider puts or wait for reversal" :
                "Consider calls or wait for reversal"
        }
        // High probability with chain confirmation
        else if winProbability >= 70 && (chainAnalysis.smartMoneyBullish && isCall || chainAnalysis.smartMoneyBearish && !isCall) {
            verdict = .yes
            keyReason = reasons.first ?? "Strong chain analysis supports trade"
            riskWarning = warnings.first ?? "Use stop-loss at ₹\(suggestion.displayStopLoss)"
        }
        // High probability without strong ML (score-driven)
        else if winProbability >= 65 && score >= 70 && !hasStrongMLSignal {
            verdict = .wait
            keyReason = reasons.first ?? "Good setup but ML is uncertain"
            riskWarning = "ML shows HOLD - wait for clearer signal"
            alternative = "Wait for ML confirmation or paper trade first"
        }
        // Low probability
        else if winProbability <= 40 {
            verdict = .no
            keyReason = warnings.first ?? "Risk factors outweigh potential"
            riskWarning = warnings.count > 1 ? warnings[1] : "Consider waiting for better setup"
            alternative = "Look for higher probability setups"
        }
        // Everything else - WAIT
        else {
            verdict = .wait
            keyReason = reasons.first ?? "Mixed signals - proceed with caution"
            riskWarning = warnings.first ?? "Monitor price action before entering"
            alternative = "Consider paper trading this setup first"
        }

        // Build chain analysis summary
        var chainSummary = ""
        if let ivSkew = chainAnalysis.ivSkew {
            chainSummary += "• IV Skew: \(String(format: "%.1f", ivSkew * 100))%\n"
        }
        if let resistance = chainAnalysis.resistanceFromCallOI {
            chainSummary += "• Call OI Resistance: \(Int(resistance))\n"
        }
        if let support = chainAnalysis.supportFromPutOI {
            chainSummary += "• Put OI Support: \(Int(support))\n"
        }
        if chainAnalysis.smartMoneyBullish {
            chainSummary += "• Smart Money: Bullish\n"
        } else if chainAnalysis.smartMoneyBearish {
            chainSummary += "• Smart Money: Bearish\n"
        }

        let fullResponse = """
        **Verdict**: \(verdict.rawValue.uppercased()) (\(winProbability)% win probability)

        **Key Insight**: \(keyReason)

        **Risk Warning**: \(riskWarning)

        \(alternative.map { "**Consider**: \($0)" } ?? "")

        **Analysis Factors**:
        • Score: \(Int(score))/100
        • R:R: \(suggestion.displayRiskReward)
        • PCR: \(String(format: "%.2f", pcr))
        • Days to Expiry: \(daysToExpiry)
        \(mlPrediction != nil ? "• ML Signal: \(mlPrediction!.signal.rawValue) (\(mlPrediction!.confidencePercentage)%)" : "")
        \(chainSummary.isEmpty ? "" : "\n**Option Chain Analysis**:\n\(chainSummary)")
        ---
        *Local analysis with chain data*
        """

        return AIExplanation(
            verdict: verdict,
            winProbability: winProbability,
            keyReason: keyReason,
            riskWarning: riskWarning,
            betterAlternative: alternative,
            fullResponse: fullResponse
        )
    }

    // MARK: - Option Chain Analysis

    private struct ChainAnalysisResult {
        var ivSkew: Double? = nil  // Put IV - Call IV at ATM
        var resistanceFromCallOI: Double? = nil
        var supportFromPutOI: Double? = nil
        var maxCallOIStrike: Double? = nil
        var maxPutOIStrike: Double? = nil
        var smartMoneyBullish: Bool = false
        var smartMoneyBearish: Bool = false
        var volumeConfirmation: Bool = false
        var strikeNearMaxOI: Bool = false
    }

    private func analyzeOptionChain(
        optionChain: [OptionChainRow]?,
        spot: Double,
        selectedStrike: Double,
        isCall: Bool
    ) -> ChainAnalysisResult {
        var result = ChainAnalysisResult()

        guard let chain = optionChain, !chain.isEmpty else {
            return result
        }

        // Find ATM strike
        let atmStrike = chain.min(by: { abs($0.strikePrice - spot) < abs($1.strikePrice - spot) })?.strikePrice ?? spot

        // === IV SKEW ANALYSIS ===
        // Calculate IV skew at ATM
        if let atmRow = chain.first(where: { $0.strikePrice == atmStrike }) {
            if let callIV = atmRow.callOption?.impliedVolatility,
               let putIV = atmRow.putOption?.impliedVolatility,
               callIV > 0 && putIV > 0 {
                result.ivSkew = putIV - callIV
            }
        }

        // === FIND MAX OI STRIKES (Support/Resistance) ===
        var maxCallOI = 0
        var maxPutOI = 0
        var maxCallOIStrike: Double = 0
        var maxPutOIStrike: Double = 0

        for row in chain {
            if let callOI = row.callOption?.openInterest, callOI > maxCallOI {
                maxCallOI = callOI
                maxCallOIStrike = row.strikePrice
            }
            if let putOI = row.putOption?.openInterest, putOI > maxPutOI {
                maxPutOI = putOI
                maxPutOIStrike = row.strikePrice
            }
        }

        // Max call OI acts as resistance (above spot)
        if maxCallOIStrike > spot {
            result.resistanceFromCallOI = maxCallOIStrike
            result.maxCallOIStrike = maxCallOIStrike
        }

        // Max put OI acts as support (below spot)
        if maxPutOIStrike < spot {
            result.supportFromPutOI = maxPutOIStrike
            result.maxPutOIStrike = maxPutOIStrike
        }

        // Check if selected strike is near max OI
        if abs(selectedStrike - maxCallOIStrike) <= 100 || abs(selectedStrike - maxPutOIStrike) <= 100 {
            result.strikeNearMaxOI = true
        }

        // === SMART MONEY ANALYSIS (OI Change patterns) ===
        var totalCallOIChange = 0
        var totalPutOIChange = 0
        var callOIBuildingCount = 0
        var putOIBuildingCount = 0

        for row in chain {
            if let callChange = row.callOption?.changeInOI {
                totalCallOIChange += callChange
                if callChange > 10000 {
                    callOIBuildingCount += 1
                }
            }
            if let putChange = row.putOption?.changeInOI {
                totalPutOIChange += putChange
                if putChange > 10000 {
                    putOIBuildingCount += 1
                }
            }
        }

        // Smart money is bullish if:
        // - Put writing (OI increasing) at lower strikes
        // - Call unwinding (OI decreasing) at higher strikes
        // OR significant put OI building vs call OI
        if totalPutOIChange > 50000 && totalCallOIChange < totalPutOIChange / 2 {
            result.smartMoneyBullish = true
        }
        // Smart money is bearish if:
        // - Call writing at higher strikes
        // - Put unwinding at lower strikes
        else if totalCallOIChange > 50000 && totalPutOIChange < totalCallOIChange / 2 {
            result.smartMoneyBearish = true
        }
        // Also check OI building count
        else if putOIBuildingCount > callOIBuildingCount + 2 {
            result.smartMoneyBullish = true
        } else if callOIBuildingCount > putOIBuildingCount + 2 {
            result.smartMoneyBearish = true
        }

        // === VOLUME CONFIRMATION ===
        // Check if selected strike has high volume relative to others
        if let selectedRow = chain.first(where: { $0.strikePrice == selectedStrike }) {
            let selectedVolume = isCall ?
                (selectedRow.callOption?.volume ?? 0) :
                (selectedRow.putOption?.volume ?? 0)

            let avgVolume = chain.reduce(0) { sum, row in
                sum + (isCall ? (row.callOption?.volume ?? 0) : (row.putOption?.volume ?? 0))
            } / max(chain.count, 1)

            if selectedVolume > avgVolume * 2 {
                result.volumeConfirmation = true
            }
        }

        return result
    }

    // Helper to format large numbers
    private func formatNumber(_ num: Int) -> String {
        if num >= 100000 {
            return String(format: "%.1fL", Double(num) / 100000)
        } else if num >= 1000 {
            return String(format: "%.1fK", Double(num) / 1000)
        }
        return "\(num)"
    }
}

// MARK: - Models

struct AIExplanation {
    let verdict: AIVerdict
    let winProbability: Int
    let keyReason: String
    let riskWarning: String
    let betterAlternative: String?
    let fullResponse: String
}

enum AIVerdict: String {
    case yes = "Yes"
    case no = "No"
    case wait = "Wait"

    var color: String {
        switch self {
        case .yes: return "00C805"
        case .no: return "FF3B30"
        case .wait: return "FF9F0A"
        }
    }

    var icon: String {
        switch self {
        case .yes: return "checkmark.circle.fill"
        case .no: return "xmark.circle.fill"
        case .wait: return "clock.fill"
        }
    }
}

struct MarketContext {
    let spotPrice: Double
    let pcr: Double
    let maxPain: Double?
    let marketBias: String
    let support: Double?
    let resistance: Double?
    let atmStrike: Double
    let indexName: String
    let vix: Double?

    init(
        spotPrice: Double,
        pcr: Double,
        maxPain: Double? = nil,
        marketBias: String,
        support: Double? = nil,
        resistance: Double? = nil,
        atmStrike: Double = 0,
        indexName: String = "NIFTY 50",
        vix: Double? = nil
    ) {
        self.spotPrice = spotPrice
        self.pcr = pcr
        self.maxPain = maxPain
        self.marketBias = marketBias
        self.support = support
        self.resistance = resistance
        self.atmStrike = atmStrike > 0 ? atmStrike : (spotPrice / 50).rounded() * 50
        self.indexName = indexName
        self.vix = vix
    }
}

enum AIError: Error {
    case noAPIKey
    case invalidURL
    case apiError
    case parseError
}
