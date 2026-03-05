import Foundation
import Combine

// MARK: - Market Alert Service

@MainActor
final class MarketAlertService: ObservableObject {
    static let shared = MarketAlertService()

    @Published var latestAlert: SmartAlert?

    // Previous snapshot for comparison
    private var previousOptionChain: [OptionChainRow] = []
    private var previousSpotPrice: Double = 0
    private var previousPCR: Double = 0
    private var previousVix: Double?
    private var previousMaxPain: Double?
    private var hasBaseline: Bool = false

    private var sensitivity: AlertSensitivity {
        let raw = UserDefaults.standard.string(forKey: "alertSensitivity") ?? AlertSensitivity.normal.rawValue
        return AlertSensitivity(rawValue: raw) ?? .normal
    }

    private init() {}

    // MARK: - Evaluate

    /// Called on each data refresh cycle. Compares current vs previous snapshot and returns detected alerts.
    func evaluate(
        optionChain: [OptionChainRow],
        spotPrice: Double,
        pcr: Double,
        vix: Double?,
        maxPain: Double?
    ) -> [SmartAlert] {
        guard !optionChain.isEmpty, spotPrice > 0 else { return [] }

        // First call — establish baseline, no alerts
        guard hasBaseline else {
            saveSnapshot(optionChain: optionChain, spotPrice: spotPrice, pcr: pcr, vix: vix, maxPain: maxPain)
            hasBaseline = true
            return []
        }

        let multiplier = sensitivity.thresholdMultiplier
        var alerts: [SmartAlert] = []

        // 1. Volume Spike detection
        alerts.append(contentsOf: detectVolumeSpikes(optionChain: optionChain, multiplier: multiplier))

        // 2. OI Surge detection
        alerts.append(contentsOf: detectOISurges(optionChain: optionChain, multiplier: multiplier))

        // 3. Large Premium detection
        alerts.append(contentsOf: detectLargePremiums(optionChain: optionChain, multiplier: multiplier))

        // 4. PCR Shift detection
        alerts.append(contentsOf: detectPCRShift(pcr: pcr, multiplier: multiplier))

        // 5. VIX Spike detection
        alerts.append(contentsOf: detectVIXSpike(vix: vix, multiplier: multiplier))

        // 6. Support/Resistance Break detection
        alerts.append(contentsOf: detectSupportResistanceBreak(spotPrice: spotPrice, optionChain: optionChain))

        // 7. Max Pain Drift detection
        alerts.append(contentsOf: detectMaxPainDrift(maxPain: maxPain, multiplier: multiplier))

        // Save current snapshot for next comparison
        saveSnapshot(optionChain: optionChain, spotPrice: spotPrice, pcr: pcr, vix: vix, maxPain: maxPain)

        // Sort by priority (highest first)
        alerts.sort { $0.type.displayPriority > $1.type.displayPriority }

        return alerts
    }

    /// Reset the baseline (e.g., when switching indices)
    func resetBaseline() {
        hasBaseline = false
        previousOptionChain = []
        previousSpotPrice = 0
        previousPCR = 0
        previousVix = nil
        previousMaxPain = nil
    }

    // MARK: - Snapshot

    private func saveSnapshot(
        optionChain: [OptionChainRow],
        spotPrice: Double,
        pcr: Double,
        vix: Double?,
        maxPain: Double?
    ) {
        previousOptionChain = optionChain
        previousSpotPrice = spotPrice
        previousPCR = pcr
        previousVix = vix
        previousMaxPain = maxPain
    }

    // MARK: - Detection Rules

    /// Rule 1: Volume Spike — Any strike volume > 5x average volume across chain
    private func detectVolumeSpikes(optionChain: [OptionChainRow], multiplier: Double) -> [SmartAlert] {
        var alerts: [SmartAlert] = []

        let allVolumes = optionChain.flatMap { row -> [Int] in
            var vols: [Int] = []
            if let cv = row.callOption?.volume, cv > 0 { vols.append(cv) }
            if let pv = row.putOption?.volume, pv > 0 { vols.append(pv) }
            return vols
        }
        guard !allVolumes.isEmpty else { return [] }
        let avgVolume = Double(allVolumes.reduce(0, +)) / Double(allVolumes.count)
        guard avgVolume > 0 else { return [] }

        let highThreshold = 10.0 * multiplier
        let medThreshold = 5.0 * multiplier

        for row in optionChain {
            // Check call volume
            if let call = row.callOption, call.volume > 0 {
                let ratio = Double(call.volume) / avgVolume
                if ratio > medThreshold {
                    let severity: AlertSeverity = ratio > highThreshold ? .high : .medium
                    alerts.append(SmartAlert(
                        type: .volumeSpike,
                        title: "Volume Spike at \(String(format: "%.0f", row.strikePrice)) CE",
                        message: "\(String(format: "%.0fx", ratio)) avg volume (\(formatVolume(call.volume)) contracts)",
                        strike: row.strikePrice,
                        optionType: "CE",
                        severity: severity,
                        actionHint: "Check for institutional activity"
                    ))
                }
            }

            // Check put volume
            if let put = row.putOption, put.volume > 0 {
                let ratio = Double(put.volume) / avgVolume
                if ratio > medThreshold {
                    let severity: AlertSeverity = ratio > highThreshold ? .high : .medium
                    alerts.append(SmartAlert(
                        type: .volumeSpike,
                        title: "Volume Spike at \(String(format: "%.0f", row.strikePrice)) PE",
                        message: "\(String(format: "%.0fx", ratio)) avg volume (\(formatVolume(put.volume)) contracts)",
                        strike: row.strikePrice,
                        optionType: "PE",
                        severity: severity,
                        actionHint: "Check for institutional activity"
                    ))
                }
            }
        }

        // Limit to top 3 most significant spikes
        return Array(alerts.sorted { $0.severity > $1.severity }.prefix(3))
    }

    /// Rule 2: OI Surge — OI change > 40% of previous OI at any strike
    private func detectOISurges(optionChain: [OptionChainRow], multiplier: Double) -> [SmartAlert] {
        var alerts: [SmartAlert] = []
        guard !previousOptionChain.isEmpty else { return [] }

        let prevByStrike = Dictionary(uniqueKeysWithValues: previousOptionChain.map { ($0.strikePrice, $0) })
        let highThreshold = 0.60 * multiplier
        let medThreshold = 0.40 * multiplier

        for row in optionChain {
            guard let prevRow = prevByStrike[row.strikePrice] else { continue }

            // Check call OI
            if let call = row.callOption, let prevCall = prevRow.callOption, prevCall.openInterest > 0 {
                let change = abs(Double(call.openInterest - prevCall.openInterest)) / Double(prevCall.openInterest)
                if change > medThreshold {
                    let severity: AlertSeverity = change > highThreshold ? .high : .medium
                    let direction = call.openInterest > prevCall.openInterest ? "built up" : "unwound"
                    alerts.append(SmartAlert(
                        type: .oiSurge,
                        title: "OI \(direction) at \(String(format: "%.0f", row.strikePrice)) CE",
                        message: "\(String(format: "%.0f%%", change * 100)) OI change (\(formatNumber(call.openInterest)) contracts)",
                        strike: row.strikePrice,
                        optionType: "CE",
                        severity: severity,
                        actionHint: direction == "built up" ? "Resistance forming" : "Resistance weakening"
                    ))
                }
            }

            // Check put OI
            if let put = row.putOption, let prevPut = prevRow.putOption, prevPut.openInterest > 0 {
                let change = abs(Double(put.openInterest - prevPut.openInterest)) / Double(prevPut.openInterest)
                if change > medThreshold {
                    let severity: AlertSeverity = change > highThreshold ? .high : .medium
                    let direction = put.openInterest > prevPut.openInterest ? "built up" : "unwound"
                    alerts.append(SmartAlert(
                        type: .oiSurge,
                        title: "OI \(direction) at \(String(format: "%.0f", row.strikePrice)) PE",
                        message: "\(String(format: "%.0f%%", change * 100)) OI change (\(formatNumber(put.openInterest)) contracts)",
                        strike: row.strikePrice,
                        optionType: "PE",
                        severity: severity,
                        actionHint: direction == "built up" ? "Support forming" : "Support weakening"
                    ))
                }
            }
        }

        return Array(alerts.sorted { $0.severity > $1.severity }.prefix(3))
    }

    /// Rule 3: Large Premium — Single strike premium (volume × LTP) > ₹1 Cr
    private func detectLargePremiums(optionChain: [OptionChainRow], multiplier: Double) -> [SmartAlert] {
        var alerts: [SmartAlert] = []

        let highThresholdCr = 5.0 * multiplier
        let medThresholdCr = 1.0 * multiplier

        for row in optionChain {
            // Check call premium
            if let call = row.callOption, call.volume > 0, call.lastTradedPrice > 0 {
                // Lot-size-adjusted notional (lotSize from the current index isn't available here,
                // so we use raw volume × LTP as a proxy — consistent with plan)
                let premiumCr = Double(call.volume) * call.lastTradedPrice / 1_00_00_000 // 1 Crore
                if premiumCr > medThresholdCr {
                    let severity: AlertSeverity = premiumCr > highThresholdCr ? .high : .medium
                    alerts.append(SmartAlert(
                        type: .largePremium,
                        title: "Large Premium at \(String(format: "%.0f", row.strikePrice)) CE",
                        message: "₹\(String(format: "%.1f", premiumCr)) Cr premium traded",
                        strike: row.strikePrice,
                        optionType: "CE",
                        severity: severity,
                        actionHint: "Big player activity detected"
                    ))
                }
            }

            // Check put premium
            if let put = row.putOption, put.volume > 0, put.lastTradedPrice > 0 {
                let premiumCr = Double(put.volume) * put.lastTradedPrice / 1_00_00_000
                if premiumCr > medThresholdCr {
                    let severity: AlertSeverity = premiumCr > highThresholdCr ? .high : .medium
                    alerts.append(SmartAlert(
                        type: .largePremium,
                        title: "Large Premium at \(String(format: "%.0f", row.strikePrice)) PE",
                        message: "₹\(String(format: "%.1f", premiumCr)) Cr premium traded",
                        strike: row.strikePrice,
                        optionType: "PE",
                        severity: severity,
                        actionHint: "Big player activity detected"
                    ))
                }
            }
        }

        return Array(alerts.sorted { $0.severity > $1.severity }.prefix(2))
    }

    /// Rule 4: PCR Shift — PCR changes by > 0.15 between refreshes
    private func detectPCRShift(pcr: Double, multiplier: Double) -> [SmartAlert] {
        guard previousPCR > 0, pcr > 0 else { return [] }

        let change = pcr - previousPCR
        let absChange = abs(change)
        let highThreshold = 0.25 * multiplier
        let medThreshold = 0.15 * multiplier

        guard absChange > medThreshold else { return [] }

        let severity: AlertSeverity = absChange > highThreshold ? .high : .medium
        let direction = change > 0 ? "increased" : "decreased"
        let sentiment = change > 0 ? "Bearish shift (more puts)" : "Bullish shift (more calls)"

        return [SmartAlert(
            type: .pcrShift,
            title: "PCR \(direction) to \(String(format: "%.2f", pcr))",
            message: "Changed by \(String(format: "%+.2f", change)) from \(String(format: "%.2f", previousPCR)). \(sentiment)",
            severity: severity,
            actionHint: sentiment
        )]
    }

    /// Rule 5: VIX Spike — VIX jumps > 2 points between refreshes
    private func detectVIXSpike(vix: Double?, multiplier: Double) -> [SmartAlert] {
        guard let vix = vix, let prevVix = previousVix, prevVix > 0 else { return [] }

        let change = vix - prevVix
        let absChange = abs(change)
        let highThreshold = 3.0 * multiplier
        let medThreshold = 2.0 * multiplier

        guard absChange > medThreshold else { return [] }

        let severity: AlertSeverity = absChange > highThreshold ? .high : .medium
        let direction = change > 0 ? "spiked" : "dropped"

        return [SmartAlert(
            type: .vixSpike,
            title: "India VIX \(direction) to \(String(format: "%.1f", vix))",
            message: "VIX changed by \(String(format: "%+.1f", change)) points. \(change > 0 ? "Increased fear/volatility" : "Decreased fear/volatility")",
            severity: severity,
            actionHint: change > 0 ? "Consider hedging positions" : "Premiums may shrink"
        )]
    }

    /// Rule 6: Support/Resistance Break — Spot crosses key OI-based S/R levels
    private func detectSupportResistanceBreak(spotPrice: Double, optionChain: [OptionChainRow]) -> [SmartAlert] {
        guard previousSpotPrice > 0, abs(spotPrice - previousSpotPrice) > 0.01 else { return [] }

        // Find highest call OI strike (resistance) and highest put OI strike (support)
        var maxCallOI = 0
        var resistanceStrike: Double = 0
        var maxPutOI = 0
        var supportStrike: Double = 0

        for row in optionChain {
            if let callOI = row.callOption?.openInterest, callOI > maxCallOI {
                maxCallOI = callOI
                resistanceStrike = row.strikePrice
            }
            if let putOI = row.putOption?.openInterest, putOI > maxPutOI {
                maxPutOI = putOI
                supportStrike = row.strikePrice
            }
        }

        var alerts: [SmartAlert] = []

        // Check resistance break (spot crossed above highest call OI strike)
        if resistanceStrike > 0 && previousSpotPrice < resistanceStrike && spotPrice >= resistanceStrike {
            alerts.append(SmartAlert(
                type: .supportResistanceBreak,
                title: "Resistance Broken at \(String(format: "%.0f", resistanceStrike))",
                message: "Spot crossed above max call OI strike. Potential breakout.",
                strike: resistanceStrike,
                severity: .high,
                actionHint: "Bullish breakout — watch for follow-through"
            ))
        }

        // Check support break (spot crossed below highest put OI strike)
        if supportStrike > 0 && previousSpotPrice > supportStrike && spotPrice <= supportStrike {
            alerts.append(SmartAlert(
                type: .supportResistanceBreak,
                title: "Support Broken at \(String(format: "%.0f", supportStrike))",
                message: "Spot dropped below max put OI strike. Potential breakdown.",
                strike: supportStrike,
                severity: .high,
                actionHint: "Bearish breakdown — watch for acceleration"
            ))
        }

        return alerts
    }

    /// Rule 7: Max Pain Drift — Max pain shifts > 1% from previous
    private func detectMaxPainDrift(maxPain: Double?, multiplier: Double) -> [SmartAlert] {
        guard let maxPain = maxPain, let prevMaxPain = previousMaxPain, prevMaxPain > 0 else { return [] }

        let changePercent = abs(maxPain - prevMaxPain) / prevMaxPain
        let threshold = 0.01 * multiplier

        guard changePercent > threshold else { return [] }

        let direction = maxPain > prevMaxPain ? "shifted up" : "shifted down"

        return [SmartAlert(
            type: .maxPainDrift,
            title: "Max Pain \(direction) to \(String(format: "%.0f", maxPain))",
            message: "Max pain moved \(String(format: "%.1f%%", changePercent * 100)) from \(String(format: "%.0f", prevMaxPain)). Market positioning changed.",
            severity: .medium,
            actionHint: "Expiry day convergence level shifted"
        )]
    }

    // MARK: - Formatting Helpers

    private func formatVolume(_ vol: Int) -> String {
        if vol >= 1_00_000 { return String(format: "%.1fL", Double(vol) / 1_00_000) }
        if vol >= 1_000 { return String(format: "%.1fK", Double(vol) / 1_000) }
        return "\(vol)"
    }

    private func formatNumber(_ num: Int) -> String {
        if num >= 1_00_00_000 { return String(format: "%.1f Cr", Double(num) / 1_00_00_000) }
        if num >= 1_00_000 { return String(format: "%.1fL", Double(num) / 1_00_000) }
        if num >= 1_000 { return String(format: "%.1fK", Double(num) / 1_000) }
        return "\(num)"
    }
}
