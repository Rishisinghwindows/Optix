import Foundation

// MARK: - OI Analysis Engine

/// Engine for analyzing Open Interest data and generating insights.
final class OIAnalysisEngine {

    // MARK: - Singleton

    static let shared = OIAnalysisEngine()
    private init() {}

    // MARK: - Constants

    private let significantOIThreshold = 0.7 // 70% of max OI considered significant
    private let strongZoneThreshold = 0.85
    private let moderateZoneThreshold = 0.6
    private let smartMoneyChangeThreshold = 0.20 // 20% change considered significant

    // MARK: - Main Analysis

    /// Perform comprehensive OI analysis on option chain data.
    func analyzeOI(
        optionChain: [OptionChainRow],
        spotPrice: Double,
        strikeInterval: Double
    ) -> OIAnalysisResult {
        let strikeData = buildStrikeData(from: optionChain)
        let (totalCallOI, totalPutOI) = calculateTotalOI(strikeData: strikeData)
        let pcr = totalCallOI > 0 ? Double(totalPutOI) / Double(totalCallOI) : 0
        let maxPain = calculateMaxPain(optionChain: optionChain, spotPrice: spotPrice)
        let atmStrike = findATMStrike(spotPrice: spotPrice, strikes: strikeData.map { $0.strikePrice })

        let supportZones = identifySupportZones(strikeData: strikeData, spotPrice: spotPrice)
        let resistanceZones = identifyResistanceZones(strikeData: strikeData, spotPrice: spotPrice)
        let heatmapCells = generateHeatmapCells(strikeData: strikeData, spotPrice: spotPrice)
        let ivSurface = generateIVSurface(strikeData: strikeData, spotPrice: spotPrice)
        let sentiment = determineMarketSentiment(pcr: pcr, strikeData: strikeData, spotPrice: spotPrice)
        let interpretation = generateInterpretation(
            strikeData: strikeData,
            spotPrice: spotPrice,
            pcr: pcr,
            maxPain: maxPain,
            supportZones: supportZones,
            resistanceZones: resistanceZones,
            sentiment: sentiment
        )

        return OIAnalysisResult(
            spotPrice: spotPrice,
            atmStrike: atmStrike,
            totalCallOI: totalCallOI,
            totalPutOI: totalPutOI,
            pcr: pcr,
            maxPain: maxPain,
            strikeData: strikeData,
            supportZones: supportZones,
            resistanceZones: resistanceZones,
            heatmapCells: heatmapCells,
            ivSurface: ivSurface,
            marketSentiment: sentiment,
            oiInterpretation: interpretation
        )
    }

    // MARK: - Strike Data Building

    private func buildStrikeData(from optionChain: [OptionChainRow]) -> [StrikeOIData] {
        return optionChain.map { row in
            StrikeOIData(
                strikePrice: row.strikePrice,
                callOI: row.callOption?.openInterest ?? 0,
                putOI: row.putOption?.openInterest ?? 0,
                callOIChange: row.callOption?.changeInOI ?? 0,
                putOIChange: row.putOption?.changeInOI ?? 0,
                callIV: row.callOption?.impliedVolatility ?? 0.15,
                putIV: row.putOption?.impliedVolatility ?? 0.15,
                callLTP: row.callOption?.lastTradedPrice ?? 0,
                putLTP: row.putOption?.lastTradedPrice ?? 0
            )
        }.sorted { $0.strikePrice < $1.strikePrice }
    }

    private func calculateTotalOI(strikeData: [StrikeOIData]) -> (callOI: Int, putOI: Int) {
        let totalCall = strikeData.reduce(0) { $0 + $1.callOI }
        let totalPut = strikeData.reduce(0) { $0 + $1.putOI }
        return (totalCall, totalPut)
    }

    // MARK: - Max Pain Calculation

    private func calculateMaxPain(optionChain: [OptionChainRow], spotPrice: Double) -> Double {
        var minPain = Double.infinity
        var maxPainStrike = spotPrice

        for row in optionChain {
            let strike = row.strikePrice
            var totalPain: Double = 0

            // Calculate pain for all call writers at this strike
            for option in optionChain {
                if let callOI = option.callOption?.openInterest {
                    let callPain = max(0, strike - option.strikePrice) * Double(callOI)
                    totalPain += callPain
                }
                if let putOI = option.putOption?.openInterest {
                    let putPain = max(0, option.strikePrice - strike) * Double(putOI)
                    totalPain += putPain
                }
            }

            if totalPain < minPain {
                minPain = totalPain
                maxPainStrike = strike
            }
        }

        return maxPainStrike
    }

    // MARK: - Support/Resistance Identification

    private func identifySupportZones(strikeData: [StrikeOIData], spotPrice: Double) -> [OIZone] {
        // Support zones are strikes below spot with high PUT OI
        let belowSpot = strikeData.filter { $0.strikePrice < spotPrice }
        guard !belowSpot.isEmpty else { return [] }

        let maxPutOI = belowSpot.map { $0.putOI }.max() ?? 1

        return belowSpot
            .filter { $0.putOI > 0 }
            .map { data in
                let percentOfMax = Double(data.putOI) / Double(maxPutOI)
                let strength: OIZoneStrength
                if percentOfMax >= strongZoneThreshold {
                    strength = .strong
                } else if percentOfMax >= moderateZoneThreshold {
                    strength = .moderate
                } else {
                    strength = .weak
                }

                return OIZone(
                    type: .support,
                    strikePrice: data.strikePrice,
                    strength: strength,
                    oiValue: data.putOI,
                    percentOfMax: percentOfMax
                )
            }
            .filter { $0.percentOfMax >= moderateZoneThreshold }
            .sorted { $0.strikePrice > $1.strikePrice } // Nearest to spot first
    }

    private func identifyResistanceZones(strikeData: [StrikeOIData], spotPrice: Double) -> [OIZone] {
        // Resistance zones are strikes above spot with high CALL OI
        let aboveSpot = strikeData.filter { $0.strikePrice > spotPrice }
        guard !aboveSpot.isEmpty else { return [] }

        let maxCallOI = aboveSpot.map { $0.callOI }.max() ?? 1

        return aboveSpot
            .filter { $0.callOI > 0 }
            .map { data in
                let percentOfMax = Double(data.callOI) / Double(maxCallOI)
                let strength: OIZoneStrength
                if percentOfMax >= strongZoneThreshold {
                    strength = .strong
                } else if percentOfMax >= moderateZoneThreshold {
                    strength = .moderate
                } else {
                    strength = .weak
                }

                return OIZone(
                    type: .resistance,
                    strikePrice: data.strikePrice,
                    strength: strength,
                    oiValue: data.callOI,
                    percentOfMax: percentOfMax
                )
            }
            .filter { $0.percentOfMax >= moderateZoneThreshold }
            .sorted { $0.strikePrice < $1.strikePrice } // Nearest to spot first
    }

    // MARK: - Heatmap Generation

    private func generateHeatmapCells(strikeData: [StrikeOIData], spotPrice: Double) -> [OIHeatmapCell] {
        let maxCallOI = strikeData.map { $0.callOI }.max() ?? 1
        let maxPutOI = strikeData.map { $0.putOI }.max() ?? 1
        let atmStrike = findATMStrike(spotPrice: spotPrice, strikes: strikeData.map { $0.strikePrice })

        var cells: [OIHeatmapCell] = []

        for data in strikeData {
            let isHighlighted = abs(data.strikePrice - atmStrike) < 1

            // Call cell
            let callIntensity = maxCallOI > 0 ? Double(data.callOI) / Double(maxCallOI) : 0
            cells.append(OIHeatmapCell(
                strikePrice: data.strikePrice,
                optionType: .call,
                oiValue: data.callOI,
                oiChange: data.callOIChange,
                intensity: callIntensity,
                isHighlighted: isHighlighted
            ))

            // Put cell
            let putIntensity = maxPutOI > 0 ? Double(data.putOI) / Double(maxPutOI) : 0
            cells.append(OIHeatmapCell(
                strikePrice: data.strikePrice,
                optionType: .put,
                oiValue: data.putOI,
                oiChange: data.putOIChange,
                intensity: putIntensity,
                isHighlighted: isHighlighted
            ))
        }

        return cells
    }

    // MARK: - IV Surface Generation

    private func generateIVSurface(strikeData: [StrikeOIData], spotPrice: Double) -> [IVSurfacePoint] {
        let atmStrike = findATMStrike(spotPrice: spotPrice, strikes: strikeData.map { $0.strikePrice })

        var points: [IVSurfacePoint] = []

        for data in strikeData {
            let moneyness = data.strikePrice / spotPrice
            let isATM = abs(data.strikePrice - atmStrike) < 1

            // Call IV point
            points.append(IVSurfacePoint(
                strikePrice: data.strikePrice,
                optionType: .call,
                iv: data.callIV,
                moneyness: moneyness,
                isATM: isATM
            ))

            // Put IV point
            points.append(IVSurfacePoint(
                strikePrice: data.strikePrice,
                optionType: .put,
                iv: data.putIV,
                moneyness: moneyness,
                isATM: isATM
            ))
        }

        return points
    }

    // MARK: - Market Sentiment

    private func determineMarketSentiment(
        pcr: Double,
        strikeData: [StrikeOIData],
        spotPrice: Double
    ) -> OIMarketSentiment {
        // Analyze PCR
        let pcrSentiment: Double
        if pcr > 1.5 {
            pcrSentiment = 2 // Strongly bullish (high put writing = bullish)
        } else if pcr > 1.2 {
            pcrSentiment = 1 // Bullish
        } else if pcr > 0.8 {
            pcrSentiment = 0 // Neutral
        } else if pcr > 0.5 {
            pcrSentiment = -1 // Bearish
        } else {
            pcrSentiment = -2 // Strongly bearish
        }

        // Analyze OI change
        let totalCallOIChange = strikeData.reduce(0) { $0 + $1.callOIChange }
        let totalPutOIChange = strikeData.reduce(0) { $0 + $1.putOIChange }

        let oiChangeSentiment: Double
        if totalPutOIChange > totalCallOIChange * 2 {
            oiChangeSentiment = 1.5 // Put writing increasing = bullish
        } else if totalCallOIChange > totalPutOIChange * 2 {
            oiChangeSentiment = -1.5 // Call writing increasing = bearish
        } else {
            oiChangeSentiment = 0
        }

        // Combine signals
        let combinedScore = (pcrSentiment + oiChangeSentiment) / 2

        if combinedScore >= 1.5 {
            return .stronglyBullish
        } else if combinedScore >= 0.5 {
            return .bullish
        } else if combinedScore <= -1.5 {
            return .stronglyBearish
        } else if combinedScore <= -0.5 {
            return .bearish
        }
        return .neutral
    }

    // MARK: - Interpretation Generation

    private func generateInterpretation(
        strikeData: [StrikeOIData],
        spotPrice: Double,
        pcr: Double,
        maxPain: Double,
        supportZones: [OIZone],
        resistanceZones: [OIZone],
        sentiment: OIMarketSentiment
    ) -> OIInterpretation {
        var insights: [OIInsight] = []

        // PCR insight
        let pcrInsight: OIInsight
        if pcr > 1.3 {
            pcrInsight = OIInsight(
                icon: "chart.pie.fill",
                title: "High PCR (\(String(format: "%.2f", pcr)))",
                description: "Put writers are aggressive. Market expects support. Bullish undertone.",
                importance: .high
            )
        } else if pcr < 0.7 {
            pcrInsight = OIInsight(
                icon: "chart.pie.fill",
                title: "Low PCR (\(String(format: "%.2f", pcr)))",
                description: "Call writers are aggressive. Market expects resistance. Bearish undertone.",
                importance: .high
            )
        } else {
            pcrInsight = OIInsight(
                icon: "chart.pie.fill",
                title: "Neutral PCR (\(String(format: "%.2f", pcr)))",
                description: "Balanced put/call writing. Market in wait-and-watch mode.",
                importance: .medium
            )
        }
        insights.append(pcrInsight)

        // Max Pain insight
        let maxPainDistance = abs(maxPain - spotPrice)
        let maxPainDirection = maxPain > spotPrice ? "above" : "below"
        insights.append(OIInsight(
            icon: "target",
            title: "Max Pain at \(String(format: "%.0f", maxPain))",
            description: "Max pain is \(String(format: "%.0f", maxPainDistance)) points \(maxPainDirection) spot. Expiry gravitation likely.",
            importance: maxPainDistance > 100 ? .high : .medium
        ))

        // Support/Resistance insights
        if let strongSupport = supportZones.first(where: { $0.strength == .strong }) {
            insights.append(OIInsight(
                icon: "arrow.down.to.line",
                title: "Strong Support at \(strongSupport.displayStrike)",
                description: "Heavy put OI (\(strongSupport.displayOI)) suggests strong buying interest at this level.",
                importance: .high
            ))
        }

        if let strongResistance = resistanceZones.first(where: { $0.strength == .strong }) {
            insights.append(OIInsight(
                icon: "arrow.up.to.line",
                title: "Strong Resistance at \(strongResistance.displayStrike)",
                description: "Heavy call OI (\(strongResistance.displayOI)) suggests strong selling pressure at this level.",
                importance: .high
            ))
        }

        // OI change insight
        let totalCallChange = strikeData.reduce(0) { $0 + $1.callOIChange }
        let totalPutChange = strikeData.reduce(0) { $0 + $1.putOIChange }

        if Double(abs(totalPutChange)) > Double(abs(totalCallChange)) * 1.5 {
            let direction = totalPutChange > 0 ? "building" : "unwinding"
            insights.append(OIInsight(
                icon: totalPutChange > 0 ? "plus.circle.fill" : "minus.circle.fill",
                title: "Put OI \(direction.capitalized)",
                description: totalPutChange > 0 ?
                    "Fresh put writing indicates bullish sentiment." :
                    "Put unwinding suggests profit booking or trend reversal.",
                importance: .medium
            ))
        } else if Double(abs(totalCallChange)) > Double(abs(totalPutChange)) * 1.5 {
            let direction = totalCallChange > 0 ? "building" : "unwinding"
            insights.append(OIInsight(
                icon: totalCallChange > 0 ? "plus.circle.fill" : "minus.circle.fill",
                title: "Call OI \(direction.capitalized)",
                description: totalCallChange > 0 ?
                    "Fresh call writing indicates bearish sentiment." :
                    "Call unwinding suggests short covering rally possible.",
                importance: .medium
            ))
        }

        // Calculate expected range
        let immediateSupport = supportZones.first?.strikePrice ?? (spotPrice - 200)
        let immediateResistance = resistanceZones.first?.strikePrice ?? (spotPrice + 200)
        let expectedRange = immediateSupport...immediateResistance

        // Generate title based on sentiment
        let title: String
        switch sentiment {
        case .stronglyBullish:
            title = "Strong Bullish Setup"
        case .bullish:
            title = "Mildly Bullish Bias"
        case .neutral:
            title = "Range-Bound Setup"
        case .bearish:
            title = "Mildly Bearish Bias"
        case .stronglyBearish:
            title = "Strong Bearish Setup"
        }

        let description = "Based on OI analysis, market is showing \(sentiment.label.lowercased()) characteristics. Expected trading range: \(String(format: "%.0f", immediateSupport)) - \(String(format: "%.0f", immediateResistance))."

        return OIInterpretation(
            title: title,
            description: description,
            keyInsights: insights,
            expectedRange: expectedRange,
            bias: sentiment
        )
    }

    // MARK: - Smart Money Activity Detection

    func detectSmartMoneyActivity(
        currentData: [StrikeOIData],
        previousData: [StrikeOIData]?
    ) -> [SmartMoneyActivity] {
        guard let previous = previousData else { return [] }

        var activities: [SmartMoneyActivity] = []

        for current in currentData {
            guard let prev = previous.first(where: { $0.strikePrice == current.strikePrice }) else {
                continue
            }

            // Check call activity
            let callChange = current.callOI - prev.callOI
            let callPercentChange = prev.callOI > 0 ? Double(callChange) / Double(prev.callOI) : 0

            if abs(callPercentChange) > smartMoneyChangeThreshold {
                let activity: SmartMoneyActivityType
                if callChange > 0 {
                    activity = .heavyCallWriting
                } else {
                    activity = .callUnwinding
                }

                activities.append(SmartMoneyActivity(
                    strikePrice: current.strikePrice,
                    optionType: .call,
                    activity: activity,
                    oiChange: callChange,
                    percentChange: callPercentChange,
                    timestamp: Date()
                ))
            }

            // Check put activity
            let putChange = current.putOI - prev.putOI
            let putPercentChange = prev.putOI > 0 ? Double(putChange) / Double(prev.putOI) : 0

            if abs(putPercentChange) > smartMoneyChangeThreshold {
                let activity: SmartMoneyActivityType
                if putChange > 0 {
                    activity = .heavyPutWriting
                } else {
                    activity = .putUnwinding
                }

                activities.append(SmartMoneyActivity(
                    strikePrice: current.strikePrice,
                    optionType: .put,
                    activity: activity,
                    oiChange: putChange,
                    percentChange: putPercentChange,
                    timestamp: Date()
                ))
            }
        }

        return activities.sorted { abs($0.percentChange) > abs($1.percentChange) }
    }

    // MARK: - Helper Methods

    private func findATMStrike(spotPrice: Double, strikes: [Double]) -> Double {
        guard !strikes.isEmpty else { return spotPrice }
        return strikes.min(by: { abs($0 - spotPrice) < abs($1 - spotPrice) }) ?? spotPrice
    }
}
