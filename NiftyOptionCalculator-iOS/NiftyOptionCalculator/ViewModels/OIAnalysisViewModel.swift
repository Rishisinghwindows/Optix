import Foundation
import SwiftUI
import Combine

// MARK: - OI Analysis ViewModel

@MainActor
final class OIAnalysisViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var analysisResult: OIAnalysisResult?
    @Published var smartMoneyActivities: [SmartMoneyActivity] = []
    @Published var isLoading = false
    @Published var selectedTab: OIAnalysisTab = .heatmap
    @Published var selectedStrike: Double?
    @Published var showStrikeDetail = false

    // Time filter for OI change analysis
    @Published var selectedTimeframe: OITimeframe = .today

    // MARK: - Private Properties

    private let analysisEngine = OIAnalysisEngine.shared
    private var cancellables = Set<AnyCancellable>()
    private var previousStrikeData: [StrikeOIData]?

    private var optionChain: [OptionChainRow]
    private var spotPrice: Double
    private var strikeInterval: Double

    // MARK: - Initialization

    init(
        optionChain: [OptionChainRow],
        spotPrice: Double,
        strikeInterval: Double
    ) {
        self.optionChain = optionChain
        self.spotPrice = spotPrice
        self.strikeInterval = strikeInterval

        performAnalysis()
    }

    // MARK: - Public Methods

    /// Perform OI analysis on current data.
    func performAnalysis() {
        isLoading = true

        // Store previous data for change comparison
        previousStrikeData = analysisResult?.strikeData

        // Perform analysis
        let result = analysisEngine.analyzeOI(
            optionChain: optionChain,
            spotPrice: spotPrice,
            strikeInterval: strikeInterval
        )

        analysisResult = result

        // Set ATM strike as selected by default
        if selectedStrike == nil {
            selectedStrike = result.atmStrike
        }

        // Detect smart money activity
        if let previous = previousStrikeData {
            smartMoneyActivities = analysisEngine.detectSmartMoneyActivity(
                currentData: result.strikeData,
                previousData: previous
            )
        }

        isLoading = false
    }

    /// Update with new option chain data.
    func updateData(
        optionChain: [OptionChainRow],
        spotPrice: Double
    ) {
        self.optionChain = optionChain
        self.spotPrice = spotPrice
        performAnalysis()
    }

    /// Select a strike for detailed view.
    func selectStrike(_ strike: Double) {
        selectedStrike = strike
        showStrikeDetail = true
    }

    /// Get strike data for selected strike.
    func getStrikeData(for strike: Double) -> StrikeOIData? {
        return analysisResult?.strikeData.first { $0.strikePrice == strike }
    }

    /// Get heatmap cells for calls.
    var callHeatmapCells: [OIHeatmapCell] {
        analysisResult?.heatmapCells.filter { $0.optionType == .call } ?? []
    }

    /// Get heatmap cells for puts.
    var putHeatmapCells: [OIHeatmapCell] {
        analysisResult?.heatmapCells.filter { $0.optionType == .put } ?? []
    }

    /// Get IV surface for calls.
    var callIVSurface: [IVSurfacePoint] {
        analysisResult?.ivSurface.filter { $0.optionType == .call } ?? []
    }

    /// Get IV surface for puts.
    var putIVSurface: [IVSurfacePoint] {
        analysisResult?.ivSurface.filter { $0.optionType == .put } ?? []
    }

    /// Get sorted strike data for table view.
    var sortedStrikeData: [StrikeOIData] {
        analysisResult?.strikeData.sorted { $0.strikePrice < $1.strikePrice } ?? []
    }

    /// Get top 5 call OI strikes.
    var topCallOIStrikes: [StrikeOIData] {
        Array((analysisResult?.strikeData ?? [])
            .sorted { $0.callOI > $1.callOI }
            .prefix(5))
    }

    /// Get top 5 put OI strikes.
    var topPutOIStrikes: [StrikeOIData] {
        Array((analysisResult?.strikeData ?? [])
            .sorted { $0.putOI > $1.putOI }
            .prefix(5))
    }

    /// Get strikes with highest OI change.
    var topOIChangeStrikes: [StrikeOIData] {
        Array((analysisResult?.strikeData ?? [])
            .sorted { abs($0.netOIChange) > abs($1.netOIChange) }
            .prefix(5))
    }

    /// Get strikes near ATM for quick reference.
    var nearATMStrikes: [StrikeOIData] {
        guard let result = analysisResult else { return [] }
        return result.strikeData.filter {
            abs($0.strikePrice - result.atmStrike) <= strikeInterval * 5
        }
    }
}

// MARK: - OI Analysis Tab

enum OIAnalysisTab: String, CaseIterable {
    case heatmap = "Heatmap"
    case zones = "Zones"
    case ivSurface = "IV Surface"
    case activity = "Activity"

    var icon: String {
        switch self {
        case .heatmap: return "square.grid.3x3.fill"
        case .zones: return "arrow.up.arrow.down.circle.fill"
        case .ivSurface: return "waveform.path.ecg"
        case .activity: return "bolt.fill"
        }
    }
}

// MARK: - OI Timeframe

enum OITimeframe: String, CaseIterable {
    case today = "Today"
    case lastHour = "Last Hour"
    case last30Min = "30 Min"

    var displayName: String { rawValue }
}
