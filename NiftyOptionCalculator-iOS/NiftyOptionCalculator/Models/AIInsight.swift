import Foundation
import SwiftUI

// MARK: - Confidence Level

enum ConfidenceLevel: String, CaseIterable, Codable {
    case high = "High"
    case medium = "Medium"
    case low = "Low"

    var localizedName: String {
        switch self {
        case .high: return L.aiInsightsHigh
        case .medium: return L.aiInsightsMedium
        case .low: return L.aiInsightsLow
        }
    }

    var color: Color {
        switch self {
        case .high: return Theme.profit
        case .medium: return Theme.accentOrange
        case .low: return Theme.loss
        }
    }

    var icon: String {
        switch self {
        case .high: return "checkmark.seal.fill"
        case .medium: return "exclamationmark.circle.fill"
        case .low: return "questionmark.circle.fill"
        }
    }
}

// MARK: - Suggestion Tier

enum SuggestionTier: String, CaseIterable, Codable {
    case topPick = "Top Pick"
    case worthWatching = "Worth Watching"

    var color: Color {
        switch self {
        case .topPick: return Theme.profit
        case .worthWatching: return Theme.accentOrange
        }
    }

    var icon: String {
        switch self {
        case .topPick: return "star.fill"
        case .worthWatching: return "eye.fill"
        }
    }

    static func from(score: Double) -> SuggestionTier {
        if score >= 62 { return .topPick }
        return .worthWatching
    }
}

// MARK: - Risk Severity

enum RiskSeverity: Int, CaseIterable, Codable, Comparable {
    case minor = 4       // mild theta
    case moderate = 5    // low volume, wide spread
    case severe = 8      // High IV Rank, Deep OTM
    case critical = 10   // EXTREME theta, against strong trend

    static func < (lhs: RiskSeverity, rhs: RiskSeverity) -> Bool {
        lhs.rawValue < rhs.rawValue
    }

    var penalty: Double {
        Double(rawValue)
    }

    var label: String {
        switch self {
        case .minor: return "Minor"
        case .moderate: return "Moderate"
        case .severe: return "Severe"
        case .critical: return "Critical"
        }
    }

    var color: Color {
        switch self {
        case .minor: return Theme.textSecondary
        case .moderate: return Theme.accentOrange
        case .severe: return Theme.loss.opacity(0.8)
        case .critical: return Theme.loss
        }
    }
}

// MARK: - Risk Warning (Structured)

struct RiskWarning: Equatable, Identifiable {
    let id = UUID()
    let message: String
    let severity: RiskSeverity

    var penalty: Double {
        severity.penalty
    }

    static func == (lhs: RiskWarning, rhs: RiskWarning) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Market Regime

enum MarketRegime: String, CaseIterable, Codable {
    case trending = "Trending"
    case rangeBound = "Range-Bound"
    case volatile = "Volatile"
    case flat = "Flat"

    var icon: String {
        switch self {
        case .trending: return "arrow.up.right"
        case .rangeBound: return "arrow.left.arrow.right"
        case .volatile: return "waveform.path.ecg"
        case .flat: return "minus"
        }
    }

    var color: Color {
        switch self {
        case .trending: return Theme.profit
        case .rangeBound: return Theme.primaryBlue
        case .volatile: return Theme.accentOrange
        case .flat: return Theme.textSecondary
        }
    }

    var description: String {
        switch self {
        case .trending: return "Momentum plays favored, OTM options viable"
        case .rangeBound: return "ATM options preferred, avoid deep OTM"
        case .volatile: return "High-liquidity options only, wider stops"
        case .flat: return "Low volatility, ATM straddles/strangles preferred"
        }
    }
}

// MARK: - Score Reasoning

struct ScoreReasoning: Identifiable, Equatable {
    let id: UUID
    let factor: String               // e.g., "OI Buildup", "Volume Surge"
    let description: String          // Human-readable explanation
    let impact: Impact               // Bullish/Bearish/Neutral
    let weight: Double               // How much this factor contributed (0-1)
    let score: Double                // Actual score for this factor

    enum Impact: String, Codable {
        case bullish = "Bullish"
        case bearish = "Bearish"
        case neutral = "Neutral"

        var color: Color {
            switch self {
            case .bullish: return Theme.profit
            case .bearish: return Theme.loss
            case .neutral: return Theme.textSecondary
            }
        }

        var icon: String {
            switch self {
            case .bullish: return "arrow.up.right"
            case .bearish: return "arrow.down.right"
            case .neutral: return "arrow.right"
            }
        }
    }

    init(factor: String, description: String, impact: Impact, weight: Double, score: Double) {
        self.id = UUID()
        self.factor = factor
        self.description = description
        self.impact = impact
        self.weight = weight
        self.score = score
    }
}

// MARK: - OI Signal (4-Quadrant Model)

enum OISignal: String, Codable {
    case longBuildup = "Long Buildup"       // Price ↑ + OI ↑ = Strong Bullish
    case shortBuildup = "Short Buildup"     // Price ↓ + OI ↑ = Strong Bearish
    case shortCovering = "Short Covering"   // Price ↑ + OI ↓ = Weak Bullish
    case longUnwinding = "Long Unwinding"   // Price ↓ + OI ↓ = Weak Bearish
    case neutral = "Neutral"                // No significant change

    var strength: String {
        switch self {
        case .longBuildup, .shortBuildup: return "Strong"
        case .shortCovering, .longUnwinding: return "Weak"
        case .neutral: return "Neutral"
        }
    }

    var isBullish: Bool {
        self == .longBuildup || self == .shortCovering
    }

    var isBearish: Bool {
        self == .shortBuildup || self == .longUnwinding
    }

    var color: Color {
        switch self {
        case .longBuildup: return Theme.profit
        case .shortCovering: return Theme.profit.opacity(0.7)
        case .shortBuildup: return Theme.loss
        case .longUnwinding: return Theme.loss.opacity(0.7)
        case .neutral: return Theme.textSecondary
        }
    }

    var icon: String {
        switch self {
        case .longBuildup: return "arrow.up.right.circle.fill"
        case .shortCovering: return "arrow.up.right"
        case .shortBuildup: return "arrow.down.right.circle.fill"
        case .longUnwinding: return "arrow.down.right"
        case .neutral: return "minus.circle"
        }
    }
}

// MARK: - Theta Decay Zone

enum ThetaDecayZone: String, Codable {
    case safe = "Safe"           // 45+ days - minimal decay
    case moderate = "Moderate"   // 30-45 days - moderate decay
    case caution = "Caution"     // 14-30 days - significant decay
    case danger = "Danger"       // 7-14 days - rapid decay
    case extreme = "Extreme"     // <7 days - extreme decay

    var daysRange: String {
        switch self {
        case .safe: return "45+ days"
        case .moderate: return "30-45 days"
        case .caution: return "14-30 days"
        case .danger: return "7-14 days"
        case .extreme: return "<7 days"
        }
    }

    var color: Color {
        switch self {
        case .safe: return Theme.profit
        case .moderate: return Theme.profit.opacity(0.7)
        case .caution: return Theme.accentOrange
        case .danger: return Theme.loss.opacity(0.7)
        case .extreme: return Theme.loss
        }
    }

    var warning: String? {
        switch self {
        case .safe, .moderate: return nil
        case .caution: return "Theta accelerating - consider shorter holding period"
        case .danger: return "Rapid theta decay - intraday/scalping only"
        case .extreme: return "EXTREME decay - avoid buying premium"
        }
    }

    static func from(daysToExpiry: Int) -> ThetaDecayZone {
        switch daysToExpiry {
        case 45...: return .safe
        case 30..<45: return .moderate
        case 14..<30: return .caution
        case 7..<14: return .danger
        default: return .extreme
        }
    }
}

// MARK: - IV Rank Status

enum IVRankStatus: String, Codable {
    case cheap = "Cheap"         // IV Rank < 30 - good for buying
    case fair = "Fair"           // IV Rank 30-70 - neutral
    case expensive = "Expensive" // IV Rank > 70 - good for selling

    var recommendation: String {
        switch self {
        case .cheap: return "Options are cheap - favor BUYING premium"
        case .fair: return "Options fairly priced - direction matters more"
        case .expensive: return "Options expensive - favor SELLING or AVOID buying"
        }
    }

    var color: Color {
        switch self {
        case .cheap: return Theme.profit
        case .fair: return Theme.accentOrange
        case .expensive: return Theme.loss
        }
    }

    static func from(ivRank: Double) -> IVRankStatus {
        if ivRank < 30 { return .cheap }
        if ivRank > 70 { return .expensive }
        return .fair
    }
}

// MARK: - Term Structure

enum TermStructure: String, Codable {
    case contango = "Contango"
    case flat = "Flat"
    case inverted = "Inverted"

    var color: Color {
        switch self {
        case .contango: return Theme.profit
        case .flat: return Theme.accentOrange
        case .inverted: return Theme.loss
        }
    }

    var description: String {
        switch self {
        case .contango:
            return "Back-month IV higher — favorable for buying"
        case .flat:
            return "IV curve flat — neutral"
        case .inverted:
            return "Front-month IV higher — event premium"
        }
    }
}

// MARK: - Option Score

struct OptionScore: Identifiable, Equatable {
    let id: UUID
    let option: OptionData
    let overallScore: Double          // 0-100
    let oiScore: Double               // OI buildup score
    let volumeScore: Double           // Volume surge score
    let ivScore: Double               // IV valuation score
    let greeksScore: Double           // Delta/Gamma positioning
    let pcrScore: Double              // PCR context score
    let maxPainScore: Double          // Max pain proximity
    let liquidityScore: Double        // Bid-ask spread score
    let confidence: ConfidenceLevel
    let reasoning: [ScoreReasoning]
    let mlPrediction: OptionPrediction?  // ML model prediction

    // NEW: Professional metrics
    let probabilityOfProfit: Double?  // POP - probability option expires ITM
    let ivRank: Double?               // IV Rank (0-100) - where current IV sits in 52-week range
    let ivRankStatus: IVRankStatus?   // Cheap/Fair/Expensive
    let oiSignal: OISignal?           // 4-Quadrant OI signal
    let thetaDecayZone: ThetaDecayZone // Theta decay warning zone
    let riskWarnings: [String]        // List of risk warnings
    let ivPercentile: Double?         // IV Percentile (0-100) within chain
    let ivPercentileScore: Double?    // Score derived from IV Percentile
    let ivRankScore: Double?          // Score derived from IV Rank
    let skewValue: Double?            // Put-call IV skew (positive = put IV higher)
    let skewScore: Double?            // Score derived from skew alignment
    let termStructure: TermStructure? // Term structure regime
    let termStructureScore: Double?   // Score derived from term structure

    init(option: OptionData, oiScore: Double, volumeScore: Double, ivScore: Double,
         greeksScore: Double, pcrScore: Double, maxPainScore: Double, liquidityScore: Double,
         reasoning: [ScoreReasoning], mlPrediction: OptionPrediction? = nil,
         probabilityOfProfit: Double? = nil, ivRank: Double? = nil,
         oiSignal: OISignal? = nil, riskWarnings: [String] = [],
         ivPercentile: Double? = nil, ivPercentileScore: Double? = nil,
         skewValue: Double? = nil, skewScore: Double? = nil,
         termStructure: TermStructure? = nil, termStructureScore: Double? = nil,
         alignmentBonus: Double = 0) {
        self.id = UUID()
        self.option = option
        self.oiScore = oiScore
        self.volumeScore = volumeScore
        self.ivScore = ivScore
        self.greeksScore = greeksScore
        self.pcrScore = pcrScore
        self.maxPainScore = maxPainScore
        self.liquidityScore = liquidityScore
        self.reasoning = reasoning
        self.mlPrediction = mlPrediction

        // NEW: Professional metrics
        self.probabilityOfProfit = probabilityOfProfit
        self.ivRank = ivRank
        self.ivRankStatus = ivRank != nil ? IVRankStatus.from(ivRank: ivRank!) : nil
        self.oiSignal = oiSignal
        self.thetaDecayZone = ThetaDecayZone.from(daysToExpiry: option.daysToExpiry)
        self.riskWarnings = riskWarnings
        self.ivPercentile = ivPercentile
        self.ivPercentileScore = ivPercentileScore
        self.skewValue = skewValue
        self.skewScore = skewScore
        self.termStructure = termStructure
        self.termStructureScore = termStructureScore
        self.ivRankScore = ivRank != nil ? max(0, min(100, 100 - ivRank!)) : nil

        // =====================================================
        // PROFESSIONAL SCORING MODEL (Updated Weights)
        // =====================================================
        // Old weights: OI 25%, Volume 20%, IV 15%, Greeks 15%, PCR 10%, MaxPain 10%, Liquidity 5%
        // New weights (with volatility structure):
        // IV Rank 18%, IV Percentile 6%, OI 18%, POP 14%, Greeks 14%, Volume 9%,
        // PCR 9%, MaxPain 4%, Liquidity 4%, Skew 2%, Term Structure 2%

        var baseScore: Double = 0

        // IV Rank Score (18%) - Most important for premium value
        if let rank = ivRank {
            // For buying: low IV rank is good (cheap options)
            // Score: IV Rank 0 = 100, IV Rank 50 = 50, IV Rank 100 = 0
            let ivRankScore = 100 - rank
            baseScore += ivRankScore * 0.18
        } else {
            baseScore += ivScore * 0.18  // Fallback to old IV score
        }

        // IV Percentile Score (6%)
        if let pctScore = ivPercentileScore {
            baseScore += pctScore * 0.06
        }

        // OI Signal Score (18%)
        baseScore += oiScore * 0.18

        // Probability of Profit Score (14%)
        if let pop = probabilityOfProfit {
            // POP 0-100 maps directly to score
            baseScore += pop * 0.14
        } else {
            baseScore += greeksScore * 0.07 + ivScore * 0.07  // Split fallback
        }

        // Greeks Score (14%)
        baseScore += greeksScore * 0.14

        // Volume Score (9%)
        baseScore += volumeScore * 0.09

        // PCR Score (9%)
        baseScore += pcrScore * 0.09

        // Max Pain Score (4%) - Reduced weight (only 60% accurate)
        baseScore += maxPainScore * 0.04

        // Liquidity Score (4%)
        baseScore += liquidityScore * 0.04

        // Skew Score (2%)
        if let skewScore = skewScore {
            baseScore += skewScore * 0.02
        }

        // Term Structure Score (2%)
        if let termScore = termStructureScore {
            baseScore += termScore * 0.02
        }

        // Blend with ML prediction if available (20% ML weight)
        if let ml = mlPrediction {
            let mlScore = Double(ml.signal.score) * 20.0  // Convert 1-5 to 20-100
            let mlConfidenceWeight = ml.confidence * 0.20  // ML weight scales with confidence
            baseScore = baseScore * (1 - mlConfidenceWeight) + mlScore * mlConfidenceWeight
        }

        // Apply severity-weighted penalties for risk warnings
        // Each warning is categorized: Critical(-10), Severe(-8), Moderate(-5), Minor(-4)
        let totalPenalty = riskWarnings.reduce(0.0) { penalty, warning in
            if warning.contains("EXTREME") || warning.contains("against strong") {
                return penalty + RiskSeverity.critical.penalty
            } else if warning.contains("High IV Rank") || warning.contains("Deep OTM") || warning.contains("against") {
                return penalty + RiskSeverity.severe.penalty
            } else if warning.contains("volume") || warning.contains("spread") {
                return penalty + RiskSeverity.moderate.penalty
            } else {
                return penalty + RiskSeverity.minor.penalty
            }
        }
        baseScore = baseScore - totalPenalty

        // Apply market alignment / momentum / OI signal bonus
        baseScore += alignmentBonus

        self.overallScore = min(100, max(0, baseScore))

        // Determine confidence based on multiple factors
        var confidenceScore = 0
        if overallScore >= 65 { confidenceScore += 2 }
        else if overallScore >= 50 { confidenceScore += 1 }

        if let pop = probabilityOfProfit, pop >= 45 { confidenceScore += 1 }
        if let rank = ivRank, rank < 40 { confidenceScore += 1 }  // Cheap options
        if let percentile = ivPercentile, percentile < 50 { confidenceScore += 1 }
        if oiSignal == .longBuildup || oiSignal == .shortBuildup { confidenceScore += 1 }
        if thetaDecayZone == .safe || thetaDecayZone == .moderate { confidenceScore += 1 }
        if riskWarnings.isEmpty { confidenceScore += 1 }
        if let ml = mlPrediction, ml.confidence >= 0.7 { confidenceScore += 1 }

        if confidenceScore >= 5 {
            self.confidence = .high
        } else if confidenceScore >= 3 {
            self.confidence = .medium
        } else {
            self.confidence = .low
        }
    }

    var displayScore: String {
        String(format: "%.0f", overallScore)
    }

    var displayPOP: String? {
        guard let pop = probabilityOfProfit else { return nil }
        return String(format: "%.0f%%", pop)
    }

    var displayIVRank: String? {
        guard let rank = ivRank else { return nil }
        return String(format: "%.0f", rank)
    }

    var displayIVPercentile: String? {
        guard let percentile = ivPercentile else { return nil }
        return String(format: "%.0f", percentile)
    }

    var displaySkew: String? {
        guard let skewValue = skewValue else { return nil }
        return String(format: "%.1f", skewValue)
    }

    static func == (lhs: OptionScore, rhs: OptionScore) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Trade Direction

enum TradeDirection: String, Codable {
    case buy = "BUY"
    case sell = "SELL"

    var color: Color {
        switch self {
        case .buy: return Theme.profit
        case .sell: return Theme.loss
        }
    }

    var icon: String {
        switch self {
        case .buy: return "arrow.up.circle.fill"
        case .sell: return "arrow.down.circle.fill"
        }
    }
}

// MARK: - AI Trade Suggestion

struct AITradeSuggestion: Identifiable, Equatable {
    let id: UUID
    let option: OptionData
    let score: OptionScore
    let direction: TradeDirection
    let entryPrice: Double
    let targetPrice: Double
    let stopLossPrice: Double
    let targetSpot: Double
    let stopLossSpot: Double
    let riskRewardRatio: Double
    let potentialProfit: Double
    let potentialLoss: Double
    let profitPercentage: Double
    let lossPercentage: Double
    let timeframe: String
    let reasoning: [String]

    // NEW: Professional metrics for display
    let whyBuy: [String]      // Bullish reasons to enter
    let riskFactors: [String] // Risk warnings
    let tier: SuggestionTier  // Top Pick or Worth Watching
    let structuredWarnings: [RiskWarning] // Severity-weighted warnings
    let isLowConfidenceFallback: Bool // True when suggestion was added as guaranteed minimum fallback

    init(option: OptionData, score: OptionScore, direction: TradeDirection,
         entryPrice: Double, targetPrice: Double, stopLossPrice: Double,
         targetSpot: Double, stopLossSpot: Double, timeframe: String, reasoning: [String],
         whyBuy: [String] = [], riskFactors: [String] = [],
         structuredWarnings: [RiskWarning] = [],
         isLowConfidenceFallback: Bool = false) {
        self.id = UUID()
        self.option = option
        self.score = score
        self.direction = direction
        self.entryPrice = entryPrice
        self.targetPrice = targetPrice
        self.stopLossPrice = stopLossPrice
        self.targetSpot = targetSpot
        self.stopLossSpot = stopLossSpot
        self.timeframe = timeframe
        self.reasoning = reasoning
        self.whyBuy = whyBuy
        self.riskFactors = riskFactors
        self.tier = SuggestionTier.from(score: score.overallScore)
        self.structuredWarnings = structuredWarnings
        self.isLowConfidenceFallback = isLowConfidenceFallback

        // Calculate P&L
        self.potentialProfit = abs(targetPrice - entryPrice)
        self.potentialLoss = abs(entryPrice - stopLossPrice)
        self.profitPercentage = entryPrice > 0 ? (potentialProfit / entryPrice) * 100 : 0
        self.lossPercentage = entryPrice > 0 ? (potentialLoss / entryPrice) * 100 : 0
        self.riskRewardRatio = potentialLoss > 0 ? potentialProfit / potentialLoss : 0
    }

    var displayStrike: String {
        String(format: "%.0f", option.strikePrice)
    }

    var displayEntry: String {
        String(format: "%.2f", entryPrice)
    }

    var displayTarget: String {
        String(format: "%.2f", targetPrice)
    }

    var displayStopLoss: String {
        String(format: "%.2f", stopLossPrice)
    }

    var displayRiskReward: String {
        String(format: "1:%.1f", riskRewardRatio)
    }

    var displayProfitPercentage: String {
        String(format: "+%.0f%%", profitPercentage)
    }

    var displayLossPercentage: String {
        String(format: "-%.0f%%", lossPercentage)
    }

    // NEW: Professional display properties
    var displayPOP: String? {
        score.displayPOP
    }

    var displayIVRank: String? {
        score.displayIVRank
    }

    var displayOISignal: String? {
        score.oiSignal?.rawValue
    }

    var displayThetaZone: String {
        score.thetaDecayZone.rawValue
    }

    var thetaWarning: String? {
        score.thetaDecayZone.warning
    }

    var ivRankStatus: IVRankStatus? {
        score.ivRankStatus
    }

    var hasRiskWarnings: Bool {
        !riskFactors.isEmpty || !score.riskWarnings.isEmpty
    }

    var allRiskWarnings: [String] {
        var warnings = riskFactors
        warnings.append(contentsOf: score.riskWarnings)
        if let thetaWarn = thetaWarning {
            warnings.append(thetaWarn)
        }
        return warnings
    }

    var isGoodTrade: Bool {
        riskRewardRatio >= 1.5 && score.confidence != .low
    }

    /// Top 3 score factors sorted by impact for "Why this trade?" section
    var topScoreFactors: [ScoreReasoning] {
        Array(score.reasoning
            .sorted { $0.score > $1.score }
            .prefix(3))
    }

    // MARK: - Position Sizing & Capital Awareness
    // SEBI data: avg trader loses ₹26K/year in transaction costs alone
    // 2% rule: max risk per trade = 2% of capital

    /// Estimated capital needed for 1 lot (premium × lot size)
    /// lotSize defaults to 75 (NIFTY); pass actual lot size for other indices
    func capitalPerLot(lotSize: Int = 75) -> Double {
        entryPrice * Double(lotSize)
    }

    /// Max loss per lot in rupees
    func maxLossPerLot(lotSize: Int = 75) -> Double {
        potentialLoss * Double(lotSize)
    }

    /// Estimated transaction costs per lot (brokerage + STT + exchange ≈ 0.4% round trip)
    func estimatedTransactionCost(lotSize: Int = 75) -> Double {
        capitalPerLot(lotSize: lotSize) * 0.004
    }

    /// Net target profit after transaction costs
    func netTargetProfit(lotSize: Int = 75) -> Double {
        let grossProfit = potentialProfit * Double(lotSize)
        return grossProfit - estimatedTransactionCost(lotSize: lotSize)
    }

    /// Minimum capital needed using 2% risk rule
    func minimumCapitalNeeded(lotSize: Int = 75) -> Double {
        maxLossPerLot(lotSize: lotSize) / 0.02  // 2% rule
    }

    /// Display-friendly capital needed
    func displayCapitalPerLot(lotSize: Int = 75) -> String {
        let cap = capitalPerLot(lotSize: lotSize)
        if cap >= 100000 {
            return "₹\(String(format: "%.1f", cap / 100000))L"
        }
        return "₹\(String(format: "%.0f", cap))"
    }

    /// Display-friendly max loss per lot
    func displayMaxLossPerLot(lotSize: Int = 75) -> String {
        let loss = maxLossPerLot(lotSize: lotSize)
        if loss >= 100000 {
            return "₹\(String(format: "%.1f", loss / 100000))L"
        }
        return "₹\(String(format: "%.0f", loss))"
    }

    /// Trailing stop-loss guidance: move SL to breakeven once 50% of target reached
    var trailingSLTrigger: Double {
        entryPrice + (potentialProfit * 0.5)
    }

    var displayTrailingSLTrigger: String {
        "Move SL to ₹\(String(format: "%.2f", entryPrice)) when price reaches ₹\(String(format: "%.2f", trailingSLTrigger))"
    }

    // Professional trade quality assessment
    var tradeQuality: String {
        var qualityScore = 0

        // Check R:R
        if riskRewardRatio >= 2.0 { qualityScore += 2 }
        else if riskRewardRatio >= 1.5 { qualityScore += 1 }

        // Check POP
        if let pop = score.probabilityOfProfit {
            if pop >= 50 { qualityScore += 2 }
            else if pop >= 40 { qualityScore += 1 }
        }

        // Check IV Rank
        if let rank = score.ivRank {
            if rank < 30 { qualityScore += 2 }  // Cheap
            else if rank < 50 { qualityScore += 1 }
        }

        // Check OI Signal
        if score.oiSignal == .longBuildup || score.oiSignal == .shortBuildup {
            qualityScore += 2
        } else if score.oiSignal == .shortCovering || score.oiSignal == .longUnwinding {
            qualityScore += 1
        }

        // Check Theta
        if score.thetaDecayZone == .safe { qualityScore += 1 }
        else if score.thetaDecayZone == .extreme || score.thetaDecayZone == .danger {
            qualityScore -= 1
        }

        // Confidence
        if score.confidence == .high { qualityScore += 1 }

        // Risk warnings penalty
        qualityScore -= allRiskWarnings.count

        if qualityScore >= 7 { return "Excellent" }
        if qualityScore >= 5 { return "Good" }
        if qualityScore >= 3 { return "Average" }
        return "Below Average"
    }

    static func == (lhs: AITradeSuggestion, rhs: AITradeSuggestion) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Market Bias

enum MarketBias: String, Codable {
    case strongBullish = "Strong Bullish"
    case bullish = "Bullish"
    case neutral = "Neutral"
    case bearish = "Bearish"
    case strongBearish = "Strong Bearish"

    var color: Color {
        switch self {
        case .strongBullish, .bullish:
            return Theme.profit
        case .neutral:
            return Theme.accentOrange
        case .bearish, .strongBearish:
            return Theme.loss
        }
    }

    var icon: String {
        switch self {
        case .strongBullish:
            return "arrow.up.forward.circle.fill"
        case .bullish:
            return "arrow.up.right"
        case .neutral:
            return "arrow.left.arrow.right"
        case .bearish:
            return "arrow.down.right"
        case .strongBearish:
            return "arrow.down.forward.circle.fill"
        }
    }

    var localizedName: String {
        switch self {
        case .strongBullish: return L.marketBiasStrongBullish
        case .bullish: return L.aiInsightsBullish
        case .neutral: return L.aiInsightsNeutral
        case .bearish: return L.aiInsightsBearish
        case .strongBearish: return L.marketBiasStrongBearish
        }
    }

    var shortText: String {
        switch self {
        case .strongBullish: return L.marketBiasStrongBullish
        case .bullish: return L.aiInsightsBullish
        case .neutral: return L.aiInsightsNeutral
        case .bearish: return L.aiInsightsBearish
        case .strongBearish: return L.marketBiasStrongBearish
        }
    }
}

// MARK: - Market Insight

struct MarketInsight: Identifiable, Equatable {
    let id: UUID
    let title: String
    let description: String
    let icon: String
    let color: Color
    let importance: Importance

    enum Importance: Int, Comparable {
        case high = 3
        case medium = 2
        case low = 1

        static func < (lhs: Importance, rhs: Importance) -> Bool {
            lhs.rawValue < rhs.rawValue
        }
    }

    init(title: String, description: String, icon: String, color: Color, importance: Importance = .medium) {
        self.id = UUID()
        self.title = title
        self.description = description
        self.icon = icon
        self.color = color
        self.importance = importance
    }

    static func == (lhs: MarketInsight, rhs: MarketInsight) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Unusual Options Activity

struct UnusualActivity: Identifiable, Equatable {
    let id: UUID
    let option: OptionData
    let activityType: ActivityType
    let volumeMultiple: Double       // How many times above average (e.g., 5.2x)
    let premiumValue: Double         // Total premium traded (Volume × LTP)
    let tradeDirection: TradeDirection? // Buy or Sell if detected
    let significance: Significance
    let description: String

    enum ActivityType: String, Codable {
        case volumeSpike = "Volume Spike"      // Volume > 3x average
        case largePremium = "Large Premium"    // Premium > threshold
        case oiSurge = "OI Surge"              // OI change > 50% of existing
        case blockTrade = "Block Trade"        // Very large single trades
        case sweeps = "Sweeps"                 // Aggressive buying across strikes

        var icon: String {
            switch self {
            case .volumeSpike: return "chart.bar.fill"
            case .largePremium: return "indianrupeesign.circle.fill"
            case .oiSurge: return "arrow.up.right.circle.fill"
            case .blockTrade: return "building.2.fill"
            case .sweeps: return "arrow.right.arrow.left"
            }
        }
    }

    enum Significance: String, Codable {
        case high = "High"
        case medium = "Medium"
        case low = "Low"

        var color: Color {
            switch self {
            case .high: return Theme.profit
            case .medium: return Theme.accentOrange
            case .low: return Theme.textSecondary
            }
        }
    }

    init(option: OptionData, activityType: ActivityType, volumeMultiple: Double,
         premiumValue: Double, tradeDirection: TradeDirection? = nil,
         significance: Significance, description: String) {
        self.id = UUID()
        self.option = option
        self.activityType = activityType
        self.volumeMultiple = volumeMultiple
        self.premiumValue = premiumValue
        self.tradeDirection = tradeDirection
        self.significance = significance
        self.description = description
    }

    var displayVolumeMultiple: String {
        String(format: "%.1fx", volumeMultiple)
    }

    var displayPremium: String {
        if premiumValue >= 10000000 {
            return String(format: "%.2f Cr", premiumValue / 10000000)
        } else if premiumValue >= 100000 {
            return String(format: "%.2f L", premiumValue / 100000)
        } else {
            return String(format: "%.0f", premiumValue)
        }
    }

    var icon: String {
        switch activityType {
        case .volumeSpike: return "chart.bar.fill"
        case .largePremium: return "indianrupeesign.circle.fill"
        case .oiSurge: return "arrow.up.right.circle.fill"
        case .blockTrade: return "building.2.fill"
        case .sweeps: return "arrow.right.arrow.left"
        }
    }

    /// Display value based on activity type (volume multiple or premium)
    var displayValue: String {
        switch activityType {
        case .volumeSpike, .oiSurge:
            return displayVolumeMultiple
        case .largePremium, .blockTrade, .sweeps:
            return displayPremium
        }
    }

    static func == (lhs: UnusualActivity, rhs: UnusualActivity) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Position Sizing

struct PositionSizing: Equatable {
    let riskPercentage: Double       // User's risk % (default 2%)
    let accountSize: Double          // User's account size
    let optionLTP: Double            // Option price
    let stopLossPrice: Double        // Stop loss price
    let lotSize: Int                 // Lot size for the index

    var riskPerTrade: Double {
        accountSize * (riskPercentage / 100)
    }

    var riskPerLot: Double {
        (optionLTP - stopLossPrice) * Double(lotSize)
    }

    var recommendedLots: Int {
        guard riskPerLot > 0 else { return 1 }
        return max(1, Int(riskPerTrade / riskPerLot))
    }

    var totalInvestment: Double {
        optionLTP * Double(lotSize) * Double(recommendedLots)
    }

    var maxLoss: Double {
        riskPerLot * Double(recommendedLots)
    }

    var displayRecommendedLots: String {
        "\(recommendedLots) lot\(recommendedLots > 1 ? "s" : "")"
    }

    var displayTotalInvestment: String {
        if totalInvestment >= 100000 {
            return String(format: "%.2f L", totalInvestment / 100000)
        } else {
            return String(format: "%.0f", totalInvestment)
        }
    }

    var displayMaxLoss: String {
        if maxLoss >= 100000 {
            return String(format: "%.2f L", maxLoss / 100000)
        } else {
            return String(format: "%.0f", maxLoss)
        }
    }

    var displayRiskPerTrade: String {
        if riskPerTrade >= 100000 {
            return String(format: "%.2f L", riskPerTrade / 100000)
        } else {
            return String(format: "%.0f", riskPerTrade)
        }
    }
}

// MARK: - Strategy Type

enum AIStrategyType: String, Codable, CaseIterable {
    case nakedCall = "Naked Call"
    case nakedPut = "Naked Put"
    case bullCallSpread = "Bull Call Spread"
    case bearPutSpread = "Bear Put Spread"
    case ironCondor = "Iron Condor"
    case strangle = "Strangle"
    case straddle = "Straddle"
    case calendarSpread = "Calendar Spread"
    case butterflySpread = "Butterfly Spread"

    var legs: Int {
        switch self {
        case .nakedCall, .nakedPut: return 1
        case .bullCallSpread, .bearPutSpread, .calendarSpread: return 2
        case .strangle, .straddle: return 2
        case .butterflySpread: return 3
        case .ironCondor: return 4
        }
    }

    var isBullish: Bool {
        switch self {
        case .nakedCall, .bullCallSpread: return true
        default: return false
        }
    }

    var isBearish: Bool {
        switch self {
        case .nakedPut, .bearPutSpread: return true
        default: return false
        }
    }

    var isNeutral: Bool {
        switch self {
        case .ironCondor, .strangle, .straddle, .butterflySpread: return true
        default: return false
        }
    }

    var preferredIVRank: String {
        switch self {
        case .nakedCall, .nakedPut:
            return "Low IV (<30)"
        case .bullCallSpread, .bearPutSpread:
            return "Any IV"
        case .ironCondor, .strangle:
            return "High IV (>50)"
        case .straddle:
            return "Low IV (<30) - expecting breakout"
        case .calendarSpread:
            return "Low IV (<30)"
        case .butterflySpread:
            return "High IV (>50)"
        }
    }

    /// Center of preferred IV range (0-100) for scoring alignment
    var preferredIVCenter: Double {
        switch self {
        case .nakedCall, .nakedPut, .straddle, .calendarSpread:
            return 15   // low IV
        case .bullCallSpread, .bearPutSpread:
            return 50   // any IV
        case .ironCondor, .strangle, .butterflySpread:
            return 75   // high IV
        }
    }

    var description: String {
        switch self {
        case .nakedCall:
            return "Buy a call when bullish. Simple directional trade."
        case .nakedPut:
            return "Buy a put when bearish. Simple directional trade."
        case .bullCallSpread:
            return "Buy ATM call, sell OTM call. Limited risk & reward."
        case .bearPutSpread:
            return "Buy ATM put, sell OTM put. Limited risk & reward."
        case .ironCondor:
            return "Sell OTM strangle, buy wings. Profit from range-bound market."
        case .strangle:
            return "Sell OTM call and put. Profit from time decay in range."
        case .straddle:
            return "Buy ATM call and put. Profit from big move in either direction."
        case .calendarSpread:
            return "Sell near-term, buy far-term. Profit from time decay."
        case .butterflySpread:
            return "ATM butterfly. Profit from market staying near current price."
        }
    }

    var icon: String {
        switch self {
        case .nakedCall: return "arrow.up.circle.fill"
        case .nakedPut: return "arrow.down.circle.fill"
        case .bullCallSpread: return "arrow.up.right.circle"
        case .bearPutSpread: return "arrow.down.right.circle"
        case .ironCondor: return "rectangle.split.3x1"
        case .strangle: return "arrow.left.arrow.right"
        case .straddle: return "arrow.up.arrow.down"
        case .calendarSpread: return "calendar"
        case .butterflySpread: return "diamond.fill"
        }
    }

    var color: Color {
        if isBullish { return Theme.profit }
        if isBearish { return Theme.loss }
        return Theme.accentOrange
    }

    var direction: String {
        if isBullish { return "Bullish" }
        if isBearish { return "Bearish" }
        return "Neutral"
    }
}

// MARK: - Score Breakdown

struct ScoreBreakdown: Equatable {
    let ivAlignment: Double    // 0-100: how well IV matches strategy preference
    let pop: Double            // 0-100: probability of profit score
    let riskReward: Double     // 0-100: risk/reward quality
    let liquidity: Double      // 0-100: liquidity of legs
    let regimeFit: Double      // 0-100: how well strategy fits current market regime
}

// MARK: - Strategy Suggestion

struct StrategySuggestion: Identifiable, Equatable {
    let id: UUID
    let strategyType: AIStrategyType
    let legs: [StrategyLeg]
    let reasoning: [String]
    let maxProfit: Double?
    let maxLoss: Double?
    let breakeven: [Double]
    let probability: Double?          // Probability of profit
    let ivRankBased: Bool            // Is this suggested based on IV Rank?
    let marketCondition: String      // e.g., "Bullish + Low IV"

    // Scoring
    let score: Double?
    let scoreBreakdown: ScoreBreakdown?

    // Net Greeks
    let netDelta: Double?
    let netTheta: Double?
    let netGamma: Double?
    let netVega: Double?

    struct StrategyLeg: Equatable {
        let option: OptionData
        let action: LegAction
        let quantity: Int
        let lotSize: Int  // Lot size for the index (e.g., 75 for Nifty, 30 for BankNifty)

        enum LegAction: String, Codable {
            case buy = "BUY"
            case sell = "SELL"

            var color: Color {
                self == .buy ? Theme.profit : Theme.loss
            }
        }

        init(option: OptionData, action: LegAction, quantity: Int, lotSize: Int = 75) {
            self.option = option
            self.action = action
            self.quantity = quantity
            self.lotSize = lotSize
        }

        var displayAction: String {
            "\(action.rawValue) \(quantity) lot\(quantity > 1 ? "s" : "")"
        }

        var premium: Double {
            option.lastTradedPrice * Double(lotSize) * Double(quantity)
        }
    }

    init(strategyType: AIStrategyType, legs: [StrategyLeg], reasoning: [String],
         maxProfit: Double? = nil, maxLoss: Double? = nil, breakeven: [Double] = [],
         probability: Double? = nil, ivRankBased: Bool = false, marketCondition: String = "",
         score: Double? = nil, scoreBreakdown: ScoreBreakdown? = nil,
         netDelta: Double? = nil, netTheta: Double? = nil, netGamma: Double? = nil, netVega: Double? = nil) {
        self.id = UUID()
        self.strategyType = strategyType
        self.legs = legs
        self.reasoning = reasoning
        self.maxProfit = maxProfit
        self.maxLoss = maxLoss
        self.breakeven = breakeven
        self.probability = probability
        self.ivRankBased = ivRankBased
        self.marketCondition = marketCondition
        self.score = score
        self.scoreBreakdown = scoreBreakdown
        self.netDelta = netDelta
        self.netTheta = netTheta
        self.netGamma = netGamma
        self.netVega = netVega
    }

    var displayMaxProfit: String? {
        guard let profit = maxProfit else { return nil }
        if profit == .infinity { return "Unlimited" }
        return String(format: "%.0f", profit)
    }

    var displayMaxLoss: String? {
        guard let loss = maxLoss else { return nil }
        return String(format: "%.0f", loss)
    }

    var displayProbability: String? {
        guard let prob = probability else { return nil }
        return String(format: "%.0f%%", prob)
    }

    var displayBreakeven: String? {
        guard !breakeven.isEmpty else { return nil }
        return breakeven.map { String(format: "%.0f", $0) }.joined(separator: " / ")
    }

    var netPremium: Double {
        legs.reduce(0) { total, leg in
            return leg.action == .buy ? total - leg.premium : total + leg.premium
        }
    }

    var isCredit: Bool {
        netPremium > 0
    }

    var displayNetPremium: String {
        let absValue = abs(netPremium)
        let prefix = isCredit ? "Credit: " : "Debit: "
        if absValue >= 100000 {
            return prefix + String(format: "%.2f L", absValue / 100000)
        }
        return prefix + String(format: "%.0f", absValue)
    }

    static func == (lhs: StrategySuggestion, rhs: StrategySuggestion) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - AI Analysis Result

struct AIAnalysisResult: Identifiable {
    let id: UUID
    let timestamp: Date
    let marketBias: MarketBias
    let topCallPicks: [AITradeSuggestion]
    let topPutPicks: [AITradeSuggestion]
    let avoidList: [OptionScore]
    let marketInsights: [MarketInsight]
    let spotPrice: Double
    let putCallRatio: Double
    let maxPainStrike: Double?
    let atmStrike: Double?
    let technicalAnalysis: TechnicalAnalysisResult?
    let indiaVix: Double?
    let vixChange: Double?

    // NEW: Professional features
    let unusualActivities: [UnusualActivity]
    let strategySuggestions: [StrategySuggestion]
    let marketRegime: MarketRegime?

    init(marketBias: MarketBias, topCallPicks: [AITradeSuggestion], topPutPicks: [AITradeSuggestion],
         avoidList: [OptionScore], marketInsights: [MarketInsight], spotPrice: Double,
         putCallRatio: Double, maxPainStrike: Double?, atmStrike: Double?,
         technicalAnalysis: TechnicalAnalysisResult? = nil, indiaVix: Double? = nil, vixChange: Double? = nil,
         unusualActivities: [UnusualActivity] = [], strategySuggestions: [StrategySuggestion] = [],
         marketRegime: MarketRegime? = nil) {
        self.id = UUID()
        self.timestamp = Date()
        self.marketBias = marketBias
        self.topCallPicks = topCallPicks
        self.topPutPicks = topPutPicks
        self.avoidList = avoidList
        self.marketInsights = marketInsights
        self.spotPrice = spotPrice
        self.putCallRatio = putCallRatio
        self.maxPainStrike = maxPainStrike
        self.atmStrike = atmStrike
        self.technicalAnalysis = technicalAnalysis
        self.indiaVix = indiaVix
        self.vixChange = vixChange
        self.unusualActivities = unusualActivities
        self.strategySuggestions = strategySuggestions
        self.marketRegime = marketRegime
    }

    var displayVix: String? {
        guard let vix = indiaVix else { return nil }
        return String(format: "%.2f", vix)
    }

    var vixStatus: String? {
        guard let vix = indiaVix else { return nil }
        if vix < 12 { return "Low - Calm Markets" }
        if vix < 15 { return "Normal" }
        if vix < 20 { return "Elevated" }
        if vix < 25 { return "High Fear" }
        return "Extreme Fear"
    }

    var displayTimestamp: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return formatter.string(from: timestamp)
    }

    var displayPCR: String {
        String(format: "%.2f", putCallRatio)
    }

    var displayMaxPain: String? {
        guard let strike = maxPainStrike else { return nil }
        return String(format: "%.0f", strike)
    }

    var hasCallSuggestions: Bool {
        !topCallPicks.isEmpty
    }

    var hasPutSuggestions: Bool {
        !topPutPicks.isEmpty
    }

    var totalSuggestions: Int {
        topCallPicks.count + topPutPicks.count
    }

    // NEW: Unusual Activity computed properties
    var hasUnusualActivity: Bool {
        !unusualActivities.isEmpty
    }

    var highSignificanceActivities: [UnusualActivity] {
        unusualActivities.filter { $0.significance == .high }
    }

    var unusualActivityCount: Int {
        unusualActivities.count
    }

    // NEW: Strategy Suggestions computed properties
    var hasStrategySuggestions: Bool {
        !strategySuggestions.isEmpty
    }

    var bullishStrategies: [StrategySuggestion] {
        strategySuggestions.filter { $0.strategyType.isBullish }
    }

    var bearishStrategies: [StrategySuggestion] {
        strategySuggestions.filter { $0.strategyType.isBearish }
    }

    var neutralStrategies: [StrategySuggestion] {
        strategySuggestions.filter { $0.strategyType.isNeutral }
    }

    var topStrategy: StrategySuggestion? {
        strategySuggestions.first
    }
}

// MARK: - AI Scorecard

enum PickOutcome: String, Codable {
    case active = "Active"
    case win = "Win"
    case loss = "Loss"
    case expired = "Expired"

    var color: Color {
        switch self {
        case .active: return Color(hex: "007AFF")
        case .win: return Color(hex: "00C805")
        case .loss: return Color(hex: "FF3B30")
        case .expired: return Color(hex: "8E8E93")
        }
    }

    var icon: String {
        switch self {
        case .active: return "clock.fill"
        case .win: return "checkmark.circle.fill"
        case .loss: return "xmark.circle.fill"
        case .expired: return "calendar.badge.clock"
        }
    }
}

struct TrackedPick: Codable, Identifiable {
    let id: String
    let indexName: String
    let strikePrice: Double
    let optionType: String  // "CE" or "PE"
    let entryPrice: Double
    let targetPrice: Double
    let stopLossPrice: Double
    let score: Double
    let tier: String  // "topPick" or "worthWatching"
    let createdAt: Date
    let expiryDate: String
    var outcome: PickOutcome
    var exitPrice: Double?
    var returnPct: Double?
    var highWaterMark: Double?
    var resolvedAt: Date?

    static func compositeKey(indexName: String, strike: Double, optionType: String, expiry: String) -> String {
        "\(indexName)_\(Int(strike))_\(optionType)_\(expiry)"
    }

    static func from(suggestion: AITradeSuggestion, indexName: String) -> TrackedPick {
        let optType = suggestion.option.optionType == .call ? "CE" : "PE"
        let formatter = DateFormatter()
        formatter.dateFormat = "dd-MMM-yyyy"
        let expiryString = formatter.string(from: suggestion.option.expiryDate)
        let key = compositeKey(indexName: indexName, strike: suggestion.option.strikePrice, optionType: optType, expiry: expiryString)
        return TrackedPick(
            id: key,
            indexName: indexName,
            strikePrice: suggestion.option.strikePrice,
            optionType: optType,
            entryPrice: suggestion.entryPrice,
            targetPrice: suggestion.targetPrice,
            stopLossPrice: suggestion.stopLossPrice,
            score: suggestion.score.overallScore,
            tier: suggestion.tier == .topPick ? "topPick" : "worthWatching",
            createdAt: Date(),
            expiryDate: expiryString,
            outcome: .active,
            exitPrice: nil,
            returnPct: nil,
            highWaterMark: suggestion.entryPrice,
            resolvedAt: nil
        )
    }
}
