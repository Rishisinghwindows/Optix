import Foundation
import SwiftUI

// MARK: - Strategy Category

enum StrategyCategory: String, CaseIterable {
    case singleLeg = "Single Leg"
    case verticalSpreads = "Vertical Spreads"
    case straddles = "Straddles & Strangles"
    case multiLeg = "Multi-Leg"

    var icon: String {
        switch self {
        case .singleLeg: return "1.circle.fill"
        case .verticalSpreads: return "arrow.up.arrow.down"
        case .straddles: return "arrow.left.arrow.right"
        case .multiLeg: return "square.stack.3d.up.fill"
        }
    }
}

// MARK: - Leg Position

enum LegPosition: String, CaseIterable {
    case buy = "BUY"
    case sell = "SELL"

    var displayName: String { rawValue }

    var multiplier: Double {
        switch self {
        case .buy: return 1.0
        case .sell: return -1.0
        }
    }

    var color: Color {
        switch self {
        case .buy: return Color.green
        case .sell: return Color.red
        }
    }
}

// MARK: - Risk Profile

struct RiskProfile {
    enum RiskLevel: String {
        case limited = "Limited"
        case unlimited = "Unlimited"
    }

    enum Outlook: String {
        case bullish = "Bullish"
        case bearish = "Bearish"
        case neutral = "Neutral"
        case volatility = "Volatility"
        case directional = "Directional"
        case bullishNeutral = "Bullish/Neutral"
        case bearishNeutral = "Bearish/Neutral"
    }

    let maxRisk: RiskLevel
    let maxReward: RiskLevel
    let outlook: Outlook

    var riskColor: Color {
        return maxRisk == .unlimited ? Color.red : Color.green
    }

    var rewardColor: Color {
        return maxReward == .unlimited ? Color.green : Color.orange
    }
}

// MARK: - Leg Template

struct LegTemplate {
    let optionType: OptionType
    let position: LegPosition
    let strikeOffset: Int // Offset from ATM in terms of strike intervals
    var quantity: Int = 1
}

// MARK: - Strategy Type

enum StrategyType: String, CaseIterable, Identifiable {
    // Single Leg
    case longCall = "Long Call"
    case shortCall = "Short Call"
    case longPut = "Long Put"
    case shortPut = "Short Put"

    // Vertical Spreads
    case bullCallSpread = "Bull Call Spread"
    case bearCallSpread = "Bear Call Spread"
    case bullPutSpread = "Bull Put Spread"
    case bearPutSpread = "Bear Put Spread"

    // Straddles & Strangles
    case longStraddle = "Long Straddle"
    case shortStraddle = "Short Straddle"
    case longStrangle = "Long Strangle"
    case shortStrangle = "Short Strangle"

    // Multi-Leg
    case ironCondor = "Iron Condor"
    case longButterfly = "Long Butterfly"
    case shortButterfly = "Short Butterfly"

    var id: String { rawValue }

    var category: StrategyCategory {
        switch self {
        case .longCall, .shortCall, .longPut, .shortPut:
            return .singleLeg
        case .bullCallSpread, .bearCallSpread, .bullPutSpread, .bearPutSpread:
            return .verticalSpreads
        case .longStraddle, .shortStraddle, .longStrangle, .shortStrangle:
            return .straddles
        case .ironCondor, .longButterfly, .shortButterfly:
            return .multiLeg
        }
    }

    var icon: String {
        switch self {
        case .longCall: return "arrow.up.right"
        case .shortCall: return "arrow.down.right"
        case .longPut: return "arrow.down.left"
        case .shortPut: return "arrow.up.left"
        case .bullCallSpread, .bullPutSpread: return "chart.line.uptrend.xyaxis"
        case .bearCallSpread, .bearPutSpread: return "chart.line.downtrend.xyaxis"
        case .longStraddle, .shortStraddle: return "arrow.left.and.right"
        case .longStrangle, .shortStrangle: return "arrow.left.arrow.right"
        case .ironCondor: return "bird.fill"
        case .longButterfly, .shortButterfly: return "ladybug.fill"
        }
    }

    var description: String {
        switch self {
        case .longCall: return "Buy a call option. Profit from price increase."
        case .shortCall: return "Sell a call option. Profit from premium if price stays below strike."
        case .longPut: return "Buy a put option. Profit from price decrease."
        case .shortPut: return "Sell a put option. Profit from premium if price stays above strike."
        case .bullCallSpread: return "Buy lower strike call, sell higher strike call. Limited risk bullish strategy."
        case .bearCallSpread: return "Sell lower strike call, buy higher strike call. Limited risk bearish strategy."
        case .bullPutSpread: return "Sell higher strike put, buy lower strike put. Limited risk bullish strategy."
        case .bearPutSpread: return "Buy higher strike put, sell lower strike put. Limited risk bearish strategy."
        case .longStraddle: return "Buy ATM call and put. Profit from large price movement in either direction."
        case .shortStraddle: return "Sell ATM call and put. Profit from low volatility and time decay."
        case .longStrangle: return "Buy OTM call and put. Cheaper than straddle, needs larger move."
        case .shortStrangle: return "Sell OTM call and put. Wider profit range than short straddle."
        case .ironCondor: return "Combine bull put spread and bear call spread. Profit from low volatility."
        case .longButterfly: return "Buy wings, sell body. Limited profit, limited risk neutral strategy."
        case .shortButterfly: return "Sell wings, buy body. Profit from large price movement."
        }
    }

    var riskProfile: RiskProfile {
        switch self {
        case .longCall, .longPut:
            return RiskProfile(maxRisk: .limited, maxReward: .unlimited, outlook: .directional)
        case .shortCall:
            return RiskProfile(maxRisk: .unlimited, maxReward: .limited, outlook: .bearishNeutral)
        case .shortPut:
            return RiskProfile(maxRisk: .unlimited, maxReward: .limited, outlook: .bullishNeutral)
        case .bullCallSpread, .bullPutSpread:
            return RiskProfile(maxRisk: .limited, maxReward: .limited, outlook: .bullish)
        case .bearCallSpread, .bearPutSpread:
            return RiskProfile(maxRisk: .limited, maxReward: .limited, outlook: .bearish)
        case .longStraddle, .longStrangle:
            return RiskProfile(maxRisk: .limited, maxReward: .unlimited, outlook: .volatility)
        case .shortStraddle, .shortStrangle:
            return RiskProfile(maxRisk: .unlimited, maxReward: .limited, outlook: .neutral)
        case .ironCondor:
            return RiskProfile(maxRisk: .limited, maxReward: .limited, outlook: .neutral)
        case .longButterfly:
            return RiskProfile(maxRisk: .limited, maxReward: .limited, outlook: .neutral)
        case .shortButterfly:
            return RiskProfile(maxRisk: .limited, maxReward: .limited, outlook: .volatility)
        }
    }

    var legCount: Int {
        switch self {
        case .longCall, .shortCall, .longPut, .shortPut:
            return 1
        case .bullCallSpread, .bearCallSpread, .bullPutSpread, .bearPutSpread,
             .longStraddle, .shortStraddle, .longStrangle, .shortStrangle:
            return 2
        case .longButterfly, .shortButterfly:
            return 3
        case .ironCondor:
            return 4
        }
    }

    var defaultLegs: [LegTemplate] {
        switch self {
        case .longCall:
            return [LegTemplate(optionType: .call, position: .buy, strikeOffset: 0)]
        case .shortCall:
            return [LegTemplate(optionType: .call, position: .sell, strikeOffset: 0)]
        case .longPut:
            return [LegTemplate(optionType: .put, position: .buy, strikeOffset: 0)]
        case .shortPut:
            return [LegTemplate(optionType: .put, position: .sell, strikeOffset: 0)]
        case .bullCallSpread:
            return [
                LegTemplate(optionType: .call, position: .buy, strikeOffset: 0),
                LegTemplate(optionType: .call, position: .sell, strikeOffset: 2)
            ]
        case .bearCallSpread:
            return [
                LegTemplate(optionType: .call, position: .sell, strikeOffset: 0),
                LegTemplate(optionType: .call, position: .buy, strikeOffset: 2)
            ]
        case .bullPutSpread:
            return [
                LegTemplate(optionType: .put, position: .sell, strikeOffset: 0),
                LegTemplate(optionType: .put, position: .buy, strikeOffset: -2)
            ]
        case .bearPutSpread:
            return [
                LegTemplate(optionType: .put, position: .buy, strikeOffset: 0),
                LegTemplate(optionType: .put, position: .sell, strikeOffset: -2)
            ]
        case .longStraddle:
            return [
                LegTemplate(optionType: .call, position: .buy, strikeOffset: 0),
                LegTemplate(optionType: .put, position: .buy, strikeOffset: 0)
            ]
        case .shortStraddle:
            return [
                LegTemplate(optionType: .call, position: .sell, strikeOffset: 0),
                LegTemplate(optionType: .put, position: .sell, strikeOffset: 0)
            ]
        case .longStrangle:
            return [
                LegTemplate(optionType: .call, position: .buy, strikeOffset: 2),
                LegTemplate(optionType: .put, position: .buy, strikeOffset: -2)
            ]
        case .shortStrangle:
            return [
                LegTemplate(optionType: .call, position: .sell, strikeOffset: 2),
                LegTemplate(optionType: .put, position: .sell, strikeOffset: -2)
            ]
        case .ironCondor:
            return [
                LegTemplate(optionType: .put, position: .buy, strikeOffset: -4),
                LegTemplate(optionType: .put, position: .sell, strikeOffset: -2),
                LegTemplate(optionType: .call, position: .sell, strikeOffset: 2),
                LegTemplate(optionType: .call, position: .buy, strikeOffset: 4)
            ]
        case .longButterfly:
            return [
                LegTemplate(optionType: .call, position: .buy, strikeOffset: -2),
                LegTemplate(optionType: .call, position: .sell, strikeOffset: 0, quantity: 2),
                LegTemplate(optionType: .call, position: .buy, strikeOffset: 2)
            ]
        case .shortButterfly:
            return [
                LegTemplate(optionType: .call, position: .sell, strikeOffset: -2),
                LegTemplate(optionType: .call, position: .buy, strikeOffset: 0, quantity: 2),
                LegTemplate(optionType: .call, position: .sell, strikeOffset: 2)
            ]
        }
    }
}

// MARK: - Strategy Leg

struct StrategyLeg: Identifiable, Equatable {
    let id: UUID
    var strikePrice: Double
    var optionType: OptionType
    var position: LegPosition
    var quantity: Int
    var premium: Double // Per unit premium
    var impliedVolatility: Double

    // Greeks (per unit)
    var delta: Double
    var gamma: Double
    var theta: Double
    var vega: Double

    init(
        id: UUID = UUID(),
        strikePrice: Double,
        optionType: OptionType,
        position: LegPosition,
        quantity: Int = 1,
        premium: Double,
        impliedVolatility: Double = 0.20,
        delta: Double = 0,
        gamma: Double = 0,
        theta: Double = 0,
        vega: Double = 0
    ) {
        self.id = id
        self.strikePrice = strikePrice
        self.optionType = optionType
        self.position = position
        self.quantity = quantity
        self.premium = premium
        self.impliedVolatility = impliedVolatility
        self.delta = delta
        self.gamma = gamma
        self.theta = theta
        self.vega = vega
    }

    // MARK: - Computed Properties

    var totalPremium: Double {
        return premium * Double(quantity)
    }

    var netDelta: Double {
        return delta * Double(quantity) * position.multiplier
    }

    var netGamma: Double {
        return gamma * Double(quantity) * position.multiplier
    }

    var netTheta: Double {
        return theta * Double(quantity) * position.multiplier
    }

    var netVega: Double {
        return vega * Double(quantity) * position.multiplier
    }

    var displayStrike: String {
        return String(format: "%.0f", strikePrice)
    }

    var displayPremium: String {
        return String(format: "%.2f", premium)
    }

    var shortDescription: String {
        return "\(position.rawValue) \(quantity) \(displayStrike) \(optionType.shortName)"
    }

    static func == (lhs: StrategyLeg, rhs: StrategyLeg) -> Bool {
        return lhs.id == rhs.id
    }
}

// MARK: - Strategy

struct Strategy: Identifiable {
    let id: UUID
    var name: String
    var type: StrategyType
    var legs: [StrategyLeg]
    var lotSize: Int
    var underlyingPrice: Double
    var expiryDate: Date

    init(
        id: UUID = UUID(),
        name: String = "",
        type: StrategyType,
        legs: [StrategyLeg] = [],
        lotSize: Int = 75,
        underlyingPrice: Double = 0,
        expiryDate: Date = Date()
    ) {
        self.id = id
        self.name = name.isEmpty ? type.rawValue : name
        self.type = type
        self.legs = legs
        self.lotSize = lotSize
        self.underlyingPrice = underlyingPrice
        self.expiryDate = expiryDate
    }

    // MARK: - Computed Properties

    var netPremium: Double {
        return legs.reduce(0) { result, leg in
            let legPremium = leg.totalPremium * Double(lotSize)
            return result + (leg.position == .buy ? -legPremium : legPremium)
        }
    }

    var isDebit: Bool {
        return netPremium < 0
    }

    var netDelta: Double {
        return legs.reduce(0) { $0 + $1.netDelta } * Double(lotSize)
    }

    var netGamma: Double {
        return legs.reduce(0) { $0 + $1.netGamma } * Double(lotSize)
    }

    var netTheta: Double {
        return legs.reduce(0) { $0 + $1.netTheta } * Double(lotSize)
    }

    var netVega: Double {
        return legs.reduce(0) { $0 + $1.netVega } * Double(lotSize)
    }

    var displayNetPremium: String {
        let absValue = abs(netPremium)
        let prefix = isDebit ? "Debit: " : "Credit: "
        return prefix + String(format: "₹%.0f", absValue)
    }

    var daysToExpiry: Int {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.day], from: Date(), to: expiryDate)
        return max(components.day ?? 0, 0)
    }

    var isValid: Bool {
        return !legs.isEmpty && legs.allSatisfy { $0.strikePrice > 0 && $0.premium >= 0 }
    }
}

// MARK: - Payoff Point

struct PayoffPoint: Identifiable {
    let id = UUID()
    let price: Double
    let payoff: Double

    var isProfit: Bool {
        return payoff > 0
    }
}

// MARK: - Payoff Data

struct PayoffData {
    let points: [PayoffPoint]
    let maxProfit: Double
    let maxLoss: Double
    let breakevens: [Double]
    let spotPrice: Double
    let payoffAtSpot: Double

    var isMaxProfitUnlimited: Bool {
        return maxProfit > 1_000_000 // Treat very large values as unlimited
    }

    var isMaxLossUnlimited: Bool {
        return abs(maxLoss) > 1_000_000
    }

    var displayMaxProfit: String {
        if isMaxProfitUnlimited {
            return "Unlimited"
        }
        return String(format: "₹%.0f", maxProfit)
    }

    var displayMaxLoss: String {
        if isMaxLossUnlimited {
            return "Unlimited"
        }
        return String(format: "₹%.0f", abs(maxLoss))
    }

    var riskRewardRatio: Double {
        guard !isMaxProfitUnlimited && !isMaxLossUnlimited && maxLoss != 0 else {
            return 0
        }
        return maxProfit / abs(maxLoss)
    }

    var displayRiskReward: String {
        if isMaxProfitUnlimited || isMaxLossUnlimited {
            return "N/A"
        }
        return String(format: "1:%.2f", riskRewardRatio)
    }
}

// MARK: - Strategy Analysis

struct StrategyAnalysis {
    let maxProfit: Double
    let maxLoss: Double
    let breakevens: [Double]
    let probabilityOfProfit: Double
    let expectedValue: Double
    let netGreeks: NetGreeks
    let marginRequired: Double

    struct NetGreeks {
        let delta: Double
        let gamma: Double
        let theta: Double
        let vega: Double

        var displayDelta: String {
            return String(format: "%.2f", delta)
        }

        var displayGamma: String {
            return String(format: "%.4f", gamma)
        }

        var displayTheta: String {
            return String(format: "%.2f", theta)
        }

        var displayVega: String {
            return String(format: "%.2f", vega)
        }
    }

    var displayPOP: String {
        return String(format: "%.1f%%", probabilityOfProfit * 100)
    }

    var displayExpectedValue: String {
        let prefix = expectedValue >= 0 ? "+" : ""
        return prefix + String(format: "₹%.0f", expectedValue)
    }

    var displayMargin: String {
        return String(format: "₹%.0f", marginRequired)
    }
}
