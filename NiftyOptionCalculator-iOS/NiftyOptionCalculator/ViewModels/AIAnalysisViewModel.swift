import Foundation
import SwiftUI

// MARK: - Insight Tab

enum InsightTab: String, CaseIterable {
    case calls = "Calls"
    case puts = "Puts"
    case market = "Market"

    var localizedName: String {
        switch self {
        case .calls: return L.aiInsightsCallsTab
        case .puts: return L.aiInsightsPutsTab
        case .market: return L.aiInsightsMarketTab
        }
    }

    var icon: String {
        switch self {
        case .calls: return "arrow.up.circle.fill"
        case .puts: return "arrow.down.circle.fill"
        case .market: return "chart.bar.fill"
        }
    }

    var color: Color {
        switch self {
        case .calls: return Color(hex: "00C805")
        case .puts: return Color(hex: "FF3B30")
        case .market: return Color(hex: "007AFF")
        }
    }
}

// MARK: - AI Analysis ViewModel

@MainActor
class AIAnalysisViewModel: ObservableObject {
    // MARK: - Published Properties

    @Published var aiAnalysis: AIAnalysisResult?
    @Published var isAnalyzing: Bool = false
    @Published var selectedTab: InsightTab = .calls
    @Published var error: String?
    @Published var lastAnalysisTime: Date?
    @Published var scorecardStats: ScorecardStats?

    // Data from Option Chain
    @Published var optionChain: [OptionChainRow] = []
    @Published var spotPrice: Double = 0
    @Published var putCallRatio: Double = 0
    @Published var maxPainStrike: Double?
    @Published var atmStrike: Double?
    @Published var atmIV: Double?
    @Published var totalCallOI: Int = 0
    @Published var totalPutOI: Int = 0
    @Published var previousClose: Double = 0  // For intraday movement detection
    @Published var vixChange: Double?  // VIX percentage change
    var selectedIndex: TradingIndex = .nifty50

    // Technical Analysis
    @Published var technicalAnalysis: TechnicalAnalysisResult?
    @Published var historicalCandles: [OHLCData] = []
    @Published var isLoadingChart: Bool = false

    // UI State
    @Published var selectedSuggestion: AITradeSuggestion?
    @Published var showSuggestionDetail: Bool = false

    // MARK: - Computed Properties

    var hasAnalysis: Bool {
        aiAnalysis != nil
    }

    var currentSuggestions: [AITradeSuggestion] {
        guard let analysis = aiAnalysis else { return [] }

        switch selectedTab {
        case .calls:
            return analysis.topCallPicks
        case .puts:
            return analysis.topPutPicks
        case .market:
            return []
        }
    }

    var topPicks: [AITradeSuggestion] {
        currentSuggestions.filter { $0.tier == .topPick }
    }

    var worthWatching: [AITradeSuggestion] {
        currentSuggestions.filter { $0.tier == .worthWatching }
    }

    var marketRegime: MarketRegime? {
        aiAnalysis?.marketRegime
    }

    var expectedMoveRange: (low: Double, high: Double)? {
        guard spotPrice > 0 else { return nil }
        let iv = atmIV ?? 15.0
        let dte: Double
        if let firstSuggestion = aiAnalysis?.topCallPicks.first ?? aiAnalysis?.topPutPicks.first {
            dte = Double(firstSuggestion.score.option.daysToExpiry)
        } else {
            dte = 7.0
        }
        guard dte > 0 else { return nil }
        let move = spotPrice * (iv / 100.0) * sqrt(dte / 365.0)
        return (low: spotPrice - move, high: spotPrice + move)
    }

    var displayExpectedMoveRange: String? {
        guard let range = expectedMoveRange else { return nil }
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        guard let low = formatter.string(from: NSNumber(value: range.low)),
              let high = formatter.string(from: NSNumber(value: range.high)) else { return nil }
        return "\(low) – \(high)"
    }

    var marketInsights: [MarketInsight] {
        aiAnalysis?.marketInsights ?? []
    }

    var marketBias: MarketBias {
        aiAnalysis?.marketBias ?? .neutral
    }

    var displaySpotPrice: String {
        String(format: "%.2f", spotPrice)
    }

    var displayPCR: String {
        String(format: "%.2f", putCallRatio)
    }

    var displayMaxPain: String? {
        guard let strike = maxPainStrike else { return nil }
        return String(format: "%.0f", strike)
    }

    var totalSuggestions: Int {
        (aiAnalysis?.topCallPicks.count ?? 0) + (aiAnalysis?.topPutPicks.count ?? 0)
    }

    var callSuggestionCount: Int {
        aiAnalysis?.topCallPicks.count ?? 0
    }

    var putSuggestionCount: Int {
        aiAnalysis?.topPutPicks.count ?? 0
    }

    var analysisStatusText: String {
        if isAnalyzing {
            return L.aiInsightsAnalyzing
        } else if let time = lastAnalysisTime {
            let formatter = DateFormatter()
            formatter.dateFormat = "h:mm a"
            return "\(L.aiInsightsUpdated) \(formatter.string(from: time))"
        } else {
            return L.aiInsightsNotAnalyzed
        }
    }

    // MARK: - Technical Analysis Computed Properties

    var hasTechnicalAnalysis: Bool {
        technicalAnalysis != nil
    }

    var trendDirection: TrendDirection {
        technicalAnalysis?.trend ?? .sideways
    }

    var technicalSignal: TechnicalSignal? {
        technicalAnalysis?.signal
    }

    var rsiValue: Double {
        technicalAnalysis?.rsi.value ?? 50
    }

    var rsiCondition: RSICondition {
        technicalAnalysis?.rsi.condition ?? .neutral
    }

    var macdResult: MACDResult? {
        technicalAnalysis?.macd
    }

    var displayRSI: String {
        String(format: "%.0f", rsiValue)
    }

    var displayTrend: String {
        trendDirection.rawValue
    }

    var displaySignal: String {
        technicalSignal?.direction.rawValue ?? "Neutral"
    }

    var displaySMA20: String? {
        guard let sma = technicalAnalysis?.sma20 else { return nil }
        return String(format: "%.0f", sma)
    }

    var displaySMA50: String? {
        guard let sma = technicalAnalysis?.sma50 else { return nil }
        return String(format: "%.0f", sma)
    }

    var displayVWAP: String? {
        guard let vwap = technicalAnalysis?.vwap, vwap > 0 else { return nil }
        return String(format: "%.0f", vwap)
    }

    var technicalInsights: [TechnicalInsight] {
        technicalAnalysis?.insights ?? []
    }

    var supportLevels: [Double] {
        technicalAnalysis?.supportLevels ?? []
    }

    var resistanceLevels: [Double] {
        technicalAnalysis?.resistanceLevels ?? []
    }

    var candlestickPatterns: [CandlestickPattern] {
        technicalAnalysis?.candlestickPatterns ?? []
    }

    var hasCandlestickPatterns: Bool {
        !candlestickPatterns.isEmpty
    }

    var topCandlestickPattern: CandlestickPattern? {
        candlestickPatterns.first
    }

    // MARK: - Methods

    func runAnalysis() {
        guard !optionChain.isEmpty else {
            error = L.aiInsightsNoData
            isAnalyzing = false  // Reset loading state when no data
            return
        }

        isAnalyzing = true
        error = nil

        // Run analysis on background queue to keep UI responsive
        Task {
            // Fetch historical candles for technical analysis
            await fetchHistoricalData()

            // Fetch India VIX for volatility-based strategy selection
            var indiaVix: Double? = nil
            var fetchedVixChange: Double? = nil
            if UpstoxAPIService.shared.isAuthenticated {
                do {
                    if let vixResult = try await UpstoxAPIService.shared.fetchIndiaVix() {
                        indiaVix = vixResult.value
                        fetchedVixChange = vixResult.change
                    }
                } catch {
                    // VIX fetch failed, continuing without it
                }
            } else {
                do {
                    if let vixResult = try await GuestDataService.shared.fetchIndiaVix() {
                        indiaVix = vixResult.value
                        fetchedVixChange = vixResult.change
                    }
                } catch {
                    // Guest VIX fetch failed, continuing without it
                }
            }

            // Run technical analysis on candles
            let techAnalysis: TechnicalAnalysisResult?
            if !historicalCandles.isEmpty {
                techAnalysis = TechnicalAnalysisService.shared.analyze(candles: historicalCandles)
            } else {
                techAnalysis = nil
            }

            // Run AI analysis with technical data, VIX, and previous close for intraday movement detection
            let result = AIAnalysisService.shared.analyzeOptionChain(
                optionChain: optionChain,
                spotPrice: spotPrice,
                putCallRatio: putCallRatio,
                maxPainStrike: maxPainStrike,
                atmStrike: atmStrike,
                atmIV: atmIV,
                totalCallOI: totalCallOI,
                totalPutOI: totalPutOI,
                technicalAnalysis: techAnalysis,
                indiaVix: indiaVix,
                previousClose: previousClose > 0 ? previousClose : nil,  // Pass previous close for intraday movement detection
                lotSize: selectedIndex.lotSize  // Pass lot size for position sizing and strategy calculations
            )

            await MainActor.run {
                self.technicalAnalysis = techAnalysis
                self.aiAnalysis = result
                self.vixChange = fetchedVixChange
                self.isAnalyzing = false
                self.lastAnalysisTime = Date()
                // Keep user's current tab selection - don't auto-switch
                // Default is already .calls (set in property declaration)

                // Resolve existing picks against current prices, then track new ones
                AIScorecardService.shared.resolveFromOptionChain(
                    self.optionChain,
                    indexName: self.selectedIndex.displayName
                )
                AIScorecardService.shared.trackSuggestions(
                    calls: result.topCallPicks,
                    puts: result.topPutPicks,
                    indexName: self.selectedIndex.displayName
                )
                self.updateScorecard()
            }
        }
    }

    /// Refresh India VIX independently (called by timer)
    func refreshVix() async {
        let vixResult: (value: Double, change: Double?)?
        if UpstoxAPIService.shared.isAuthenticated {
            vixResult = try? await UpstoxAPIService.shared.fetchIndiaVix()
        } else {
            vixResult = try? await GuestDataService.shared.fetchIndiaVix()
        }
        guard let result = vixResult else { return }
        // Update VIX in the existing analysis result
        if var analysis = aiAnalysis {
            analysis = AIAnalysisResult(
                marketBias: analysis.marketBias,
                topCallPicks: analysis.topCallPicks,
                topPutPicks: analysis.topPutPicks,
                avoidList: analysis.avoidList,
                marketInsights: analysis.marketInsights,
                spotPrice: analysis.spotPrice,
                putCallRatio: analysis.putCallRatio,
                maxPainStrike: analysis.maxPainStrike,
                atmStrike: analysis.atmStrike,
                technicalAnalysis: analysis.technicalAnalysis,
                indiaVix: result.value,
                unusualActivities: analysis.unusualActivities,
                strategySuggestions: analysis.strategySuggestions,
                marketRegime: analysis.marketRegime
            )
            aiAnalysis = analysis
        }
        vixChange = result.change
    }

    /// Fetch historical candle data for technical analysis
    private func fetchHistoricalData() async {
        isLoadingChart = true

        do {
            // Try to fetch live data from Upstox
            if UpstoxAPIService.shared.isAuthenticated {
                let candles = try await UpstoxAPIService.shared.fetchHistoricalCandles(
                    index: selectedIndex,
                    timeFrame: .fifteenMin
                )
                await MainActor.run {
                    self.historicalCandles = candles
                    self.isLoadingChart = false
                }
                // Live candles loaded for technical analysis
                return
            }
        } catch {
            // Failed to fetch live candles
        }

        await MainActor.run {
            self.historicalCandles = []
            self.isLoadingChart = false
        }
        // Live candles unavailable; skipping technical analysis
    }

    func updateData(from optionChainViewModel: OptionChainViewModel) {
        self.optionChain = optionChainViewModel.optionChain
        self.spotPrice = optionChainViewModel.spotPrice
        self.putCallRatio = optionChainViewModel.putCallRatio
        self.maxPainStrike = optionChainViewModel.maxPainStrike
        self.atmStrike = optionChainViewModel.atmStrike
        self.atmIV = optionChainViewModel.atmIV
        self.totalCallOI = optionChainViewModel.totalCallOI
        self.totalPutOI = optionChainViewModel.totalPutOI
        self.previousClose = optionChainViewModel.previousClose  // For intraday movement detection
        self.selectedIndex = optionChainViewModel.selectedIndex
    }

    func selectSuggestion(_ suggestion: AITradeSuggestion) {
        selectedSuggestion = suggestion
        showSuggestionDetail = true

        let impact = UIImpactFeedbackGenerator(style: .light)
        impact.impactOccurred()
    }

    func dismissSuggestionDetail() {
        showSuggestionDetail = false
        selectedSuggestion = nil
    }

    func refresh() {
        runAnalysis()
    }

    func updateScorecard() {
        scorecardStats = AIScorecardService.shared.getStats()
    }

    // MARK: - Helper Methods

    func getSuggestionForStrike(_ strike: Double, optionType: OptionType) -> AITradeSuggestion? {
        guard let analysis = aiAnalysis else { return nil }

        let suggestions = optionType == .call ? analysis.topCallPicks : analysis.topPutPicks

        return suggestions.first { $0.option.strikePrice == strike }
    }

    func hasHighConfidenceSuggestion() -> Bool {
        guard let analysis = aiAnalysis else { return false }

        let allSuggestions = analysis.topCallPicks + analysis.topPutPicks
        return allSuggestions.contains { $0.score.confidence == .high }
    }

    func getBestSuggestion() -> AITradeSuggestion? {
        guard let analysis = aiAnalysis else { return nil }

        let allSuggestions = analysis.topCallPicks + analysis.topPutPicks
        return allSuggestions.max { $0.score.overallScore < $1.score.overallScore }
    }

    // MARK: - Unusual Activity Properties

    var unusualActivities: [UnusualActivity] {
        aiAnalysis?.unusualActivities ?? []
    }

    var hasUnusualActivity: Bool {
        !unusualActivities.isEmpty
    }

    var highSignificanceActivities: [UnusualActivity] {
        unusualActivities.filter { $0.significance == .high }
    }

    var unusualActivityCount: Int {
        unusualActivities.count
    }

    // MARK: - Risk Sentinel & Market Summary

    struct RiskAlert: Identifiable {
        let id = UUID()
        let title: String
        let detail: String
        let severity: ConfidenceLevel
    }

    var riskSentinelAlerts: [RiskAlert] {
        var alerts: [RiskAlert] = []

        if let vix = aiAnalysis?.indiaVix {
            if vix > 25 {
                alerts.append(RiskAlert(
                    title: "Extreme VIX",
                    detail: "VIX \(String(format: "%.1f", vix)) — premiums very expensive, IV crush risk",
                    severity: .high
                ))
            } else if vix > 20 {
                alerts.append(RiskAlert(
                    title: "High VIX",
                    detail: "VIX \(String(format: "%.1f", vix)) — volatility elevated, widen stops",
                    severity: .medium
                ))
            }
        }

        let allSuggestions = (aiAnalysis?.topCallPicks ?? []) + (aiAnalysis?.topPutPicks ?? [])
        if let maxIvRank = allSuggestions.compactMap({ $0.score.ivRank }).max(), maxIvRank > 70 {
            alerts.append(RiskAlert(
                title: "High IV Rank",
                detail: "IV Rank \(Int(maxIvRank)) — options priced rich",
                severity: .medium
            ))
        }

        if let maxIvPercentile = allSuggestions.compactMap({ $0.score.ivPercentile }).max(), maxIvPercentile > 70 {
            alerts.append(RiskAlert(
                title: "High IV Percentile",
                detail: "IV Percentile \(Int(maxIvPercentile))% — premiums expensive",
                severity: .medium
            ))
        }

        if allSuggestions.contains(where: { $0.score.thetaDecayZone == .danger || $0.score.thetaDecayZone == .extreme }) {
            alerts.append(RiskAlert(
                title: "Theta Decay Risk",
                detail: "Near‑expiry options — rapid time decay",
                severity: .high
            ))
        }

        if let term = allSuggestions.compactMap({ $0.score.termStructure }).first, term == .inverted {
            alerts.append(RiskAlert(
                title: "Inverted Term Structure",
                detail: "Front‑month IV higher — event premium risk",
                severity: .medium
            ))
        }

        if alerts.isEmpty {
            alerts.append(RiskAlert(
                title: "Risk Normal",
                detail: "No major risk flags detected",
                severity: .low
            ))
        }

        return Array(alerts.prefix(4))
    }

    var marketSummaryItems: [(label: String, value: String)] {
        var items: [(String, String)] = []

        items.append(("Bias", marketBias.rawValue))
        items.append(("Spot", displaySpotPrice))
        items.append(("PCR", displayPCR))

        if let vix = aiAnalysis?.indiaVix {
            items.append(("VIX", String(format: "%.1f", vix)))
        }

        if let maxPain = displayMaxPain {
            items.append(("Max Pain", maxPain))
        }

        if let best = getBestSuggestion(),
           let ivRank = best.score.ivRank {
            items.append(("IV Rank", String(format: "%.0f", ivRank)))
        }

        if let best = getBestSuggestion(),
           let ivPct = best.score.ivPercentile {
            items.append(("IV %ile", String(format: "%.0f", ivPct)))
        }

        return items
    }

    // MARK: - Strategy Suggestions Properties

    var strategySuggestions: [StrategySuggestion] {
        aiAnalysis?.strategySuggestions ?? []
    }

    var hasStrategySuggestions: Bool {
        !strategySuggestions.isEmpty
    }

    var topStrategy: StrategySuggestion? {
        strategySuggestions.max { ($0.score ?? 0) < ($1.score ?? 0) }
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

    // MARK: - Position Sizing Calculator

    /// Calculate position sizing based on 2% risk rule
    func calculatePositionSizing(
        for suggestion: AITradeSuggestion,
        accountSize: Double,
        riskPercentage: Double = 2.0
    ) -> PositionSizing {
        return PositionSizing(
            riskPercentage: riskPercentage,
            accountSize: accountSize,
            optionLTP: suggestion.entryPrice,
            stopLossPrice: suggestion.stopLossPrice,
            lotSize: selectedIndex.lotSize
        )
    }

    /// Get position sizing recommendation text
    func getPositionSizingRecommendation(
        for suggestion: AITradeSuggestion,
        accountSize: Double,
        riskPercentage: Double = 2.0
    ) -> String {
        let sizing = calculatePositionSizing(for: suggestion, accountSize: accountSize, riskPercentage: riskPercentage)
        return "Buy \(sizing.displayRecommendedLots) (based on \(Int(riskPercentage))% risk rule). Investment: \(sizing.displayTotalInvestment), Max Loss: \(sizing.displayMaxLoss)"
    }
}

// MARK: - Preview Helper

extension AIAnalysisViewModel {
    static var preview: AIAnalysisViewModel {
        let vm = AIAnalysisViewModel()
        // Add mock data for previews
        return vm
    }
}
