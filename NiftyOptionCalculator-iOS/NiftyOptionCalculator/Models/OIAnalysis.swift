import Foundation
import SwiftUI

// MARK: - Strike OI Data

struct StrikeOIData: Identifiable {
    let id = UUID()
    let strikePrice: Double
    let callOI: Int
    let putOI: Int
    let callOIChange: Int
    let putOIChange: Int
    let callIV: Double
    let putIV: Double
    let callLTP: Double
    let putLTP: Double

    // MARK: - Computed Properties

    var totalOI: Int {
        return callOI + putOI
    }

    var pcr: Double {
        guard callOI > 0 else { return 0 }
        return Double(putOI) / Double(callOI)
    }

    var netOIChange: Int {
        return callOIChange + putOIChange
    }

    var oiChangeDirection: OIChangeDirection {
        let callBuildup = callOIChange > 0
        let putBuildup = putOIChange > 0

        if callBuildup && putBuildup {
            return .bothBuilding
        } else if callBuildup && !putBuildup {
            return .callBuilding
        } else if !callBuildup && putBuildup {
            return .putBuilding
        } else {
            return .bothUnwinding
        }
    }

    var avgIV: Double {
        return (callIV + putIV) / 2
    }

    var ivSkew: Double {
        // Positive skew means puts are more expensive (fear)
        return putIV - callIV
    }

    var displayStrike: String {
        return String(format: "%.0f", strikePrice)
    }

    var displayPCR: String {
        return String(format: "%.2f", pcr)
    }
}

// MARK: - OI Change Direction

enum OIChangeDirection {
    case callBuilding
    case putBuilding
    case bothBuilding
    case bothUnwinding

    var color: Color {
        switch self {
        case .callBuilding: return Color.red.opacity(0.7)
        case .putBuilding: return Color.green.opacity(0.7)
        case .bothBuilding: return Color.blue.opacity(0.7)
        case .bothUnwinding: return Color.gray.opacity(0.7)
        }
    }

    var description: String {
        switch self {
        case .callBuilding: return "Call Writing"
        case .putBuilding: return "Put Writing"
        case .bothBuilding: return "Both Building"
        case .bothUnwinding: return "Both Unwinding"
        }
    }

    var sentiment: String {
        switch self {
        case .callBuilding: return "Bearish"
        case .putBuilding: return "Bullish"
        case .bothBuilding: return "Neutral/Range"
        case .bothUnwinding: return "Trend Expected"
        }
    }
}

// MARK: - OI Zone

struct OIZone: Identifiable {
    let id = UUID()
    let type: OIZoneType
    let strikePrice: Double
    let strength: OIZoneStrength
    let oiValue: Int
    let percentOfMax: Double

    var displayStrike: String {
        return String(format: "%.0f", strikePrice)
    }

    var displayOI: String {
        if oiValue >= 100_000 {
            return String(format: "%.1fL", Double(oiValue) / 100_000)
        } else if oiValue >= 1000 {
            return String(format: "%.1fK", Double(oiValue) / 1000)
        }
        return "\(oiValue)"
    }
}

enum OIZoneType {
    case support
    case resistance

    var color: Color {
        switch self {
        case .support: return Color.green
        case .resistance: return Color.red
        }
    }

    var label: String {
        switch self {
        case .support: return "Support"
        case .resistance: return "Resistance"
        }
    }

    var icon: String {
        switch self {
        case .support: return "arrow.up.circle.fill"
        case .resistance: return "arrow.down.circle.fill"
        }
    }
}

enum OIZoneStrength: String {
    case strong = "Strong"
    case moderate = "Moderate"
    case weak = "Weak"

    var color: Color {
        switch self {
        case .strong: return Color.green
        case .moderate: return Color.orange
        case .weak: return Color.gray
        }
    }

    var opacity: Double {
        switch self {
        case .strong: return 1.0
        case .moderate: return 0.7
        case .weak: return 0.4
        }
    }
}

// MARK: - OI Heatmap Cell

struct OIHeatmapCell: Identifiable {
    let id = UUID()
    let strikePrice: Double
    let optionType: OptionType
    let oiValue: Int
    let oiChange: Int
    let intensity: Double // 0 to 1
    let isHighlighted: Bool

    var color: Color {
        let baseColor = optionType == .call ? Color.red : Color.green
        return baseColor.opacity(0.2 + (intensity * 0.8))
    }

    var changeColor: Color {
        if oiChange > 0 {
            return Color.green
        } else if oiChange < 0 {
            return Color.red
        }
        return Color.gray
    }
}

// MARK: - IV Surface Point

struct IVSurfacePoint: Identifiable {
    let id = UUID()
    let strikePrice: Double
    let optionType: OptionType
    let iv: Double
    let moneyness: Double // Strike / Spot
    let isATM: Bool

    var displayIV: String {
        return String(format: "%.1f%%", iv * 100)
    }

    var color: Color {
        // Higher IV = warmer color
        if iv > 0.30 {
            return Color.red
        } else if iv > 0.20 {
            return Color.orange
        } else if iv > 0.15 {
            return Color.yellow
        }
        return Color.green
    }
}

// MARK: - OI Analysis Result

struct OIAnalysisResult {
    let spotPrice: Double
    let atmStrike: Double
    let totalCallOI: Int
    let totalPutOI: Int
    let pcr: Double
    let maxPain: Double
    let strikeData: [StrikeOIData]
    let supportZones: [OIZone]
    let resistanceZones: [OIZone]
    let heatmapCells: [OIHeatmapCell]
    let ivSurface: [IVSurfacePoint]
    let marketSentiment: OIMarketSentiment
    let oiInterpretation: OIInterpretation

    var displayPCR: String {
        return String(format: "%.2f", pcr)
    }

    var displayMaxPain: String {
        return String(format: "%.0f", maxPain)
    }

    var immediateSupport: Double? {
        return supportZones.first(where: { $0.strikePrice < spotPrice })?.strikePrice
    }

    var immediateResistance: Double? {
        return resistanceZones.first(where: { $0.strikePrice > spotPrice })?.strikePrice
    }
}

// MARK: - Market Sentiment

enum OIMarketSentiment {
    case stronglyBullish
    case bullish
    case neutral
    case bearish
    case stronglyBearish

    var color: Color {
        switch self {
        case .stronglyBullish: return Color.green
        case .bullish: return Color.green.opacity(0.7)
        case .neutral: return Color.blue
        case .bearish: return Color.red.opacity(0.7)
        case .stronglyBearish: return Color.red
        }
    }

    var label: String {
        switch self {
        case .stronglyBullish: return "Strongly Bullish"
        case .bullish: return "Bullish"
        case .neutral: return "Neutral"
        case .bearish: return "Bearish"
        case .stronglyBearish: return "Strongly Bearish"
        }
    }

    var icon: String {
        switch self {
        case .stronglyBullish: return "arrow.up.circle.fill"
        case .bullish: return "arrow.up.right.circle.fill"
        case .neutral: return "equal.circle.fill"
        case .bearish: return "arrow.down.right.circle.fill"
        case .stronglyBearish: return "arrow.down.circle.fill"
        }
    }
}

// MARK: - OI Interpretation

struct OIInterpretation {
    let title: String
    let description: String
    let keyInsights: [OIInsight]
    let expectedRange: ClosedRange<Double>?
    let bias: OIMarketSentiment
}

struct OIInsight: Identifiable {
    let id = UUID()
    let icon: String
    let title: String
    let description: String
    let importance: InsightImportance
}

enum InsightImportance {
    case high
    case medium
    case low

    var color: Color {
        switch self {
        case .high: return Color.red
        case .medium: return Color.orange
        case .low: return Color.blue
        }
    }
}

// MARK: - OI Time Series

struct OITimeSeriesPoint: Identifiable {
    let id = UUID()
    let timestamp: Date
    let strikePrice: Double
    let callOI: Int
    let putOI: Int
    let spotPrice: Double
}

// MARK: - Smart Money Activity

struct SmartMoneyActivity: Identifiable {
    let id = UUID()
    let strikePrice: Double
    let optionType: OptionType
    let activity: SmartMoneyActivityType
    let oiChange: Int
    let percentChange: Double
    let timestamp: Date

    var displayChange: String {
        let sign = oiChange >= 0 ? "+" : ""
        if abs(oiChange) >= 100_000 {
            return sign + String(format: "%.1fL", Double(oiChange) / 100_000)
        } else if abs(oiChange) >= 1000 {
            return sign + String(format: "%.1fK", Double(oiChange) / 1000)
        }
        return sign + "\(oiChange)"
    }
}

enum SmartMoneyActivityType {
    case heavyCallWriting
    case heavyPutWriting
    case callUnwinding
    case putUnwinding
    case freshLongBuild
    case shortCovering

    var description: String {
        switch self {
        case .heavyCallWriting: return "Heavy Call Writing"
        case .heavyPutWriting: return "Heavy Put Writing"
        case .callUnwinding: return "Call Unwinding"
        case .putUnwinding: return "Put Unwinding"
        case .freshLongBuild: return "Fresh Long Build"
        case .shortCovering: return "Short Covering"
        }
    }

    var sentiment: String {
        switch self {
        case .heavyCallWriting: return "Bearish"
        case .heavyPutWriting: return "Bullish"
        case .callUnwinding: return "Bullish"
        case .putUnwinding: return "Bearish"
        case .freshLongBuild: return "Bullish"
        case .shortCovering: return "Bullish"
        }
    }

    var color: Color {
        switch self {
        case .heavyCallWriting, .putUnwinding: return Color.red
        case .heavyPutWriting, .callUnwinding, .freshLongBuild, .shortCovering: return Color.green
        }
    }

    var icon: String {
        switch self {
        case .heavyCallWriting: return "arrow.down.doc.fill"
        case .heavyPutWriting: return "arrow.up.doc.fill"
        case .callUnwinding: return "arrow.uturn.up"
        case .putUnwinding: return "arrow.uturn.down"
        case .freshLongBuild: return "plus.circle.fill"
        case .shortCovering: return "checkmark.circle.fill"
        }
    }
}
