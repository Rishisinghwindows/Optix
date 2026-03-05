import Foundation
import Combine

// MARK: - Screener Supporting Types

enum ScreenerOptionType: String, CaseIterable, Identifiable {
    case both = "Both"
    case ce = "CE"
    case pe = "PE"

    var id: String { rawValue }
}

enum MoneynessFilter: String, CaseIterable, Identifiable {
    case all = "All"
    case atm = "ATM"
    case itm = "ITM"
    case otm = "OTM"

    var id: String { rawValue }
}

enum ScreenerSortBy: String, CaseIterable, Identifiable {
    case delta = "Delta"
    case iv = "IV"
    case oi = "OI"
    case volume = "Volume"
    case premium = "Premium"

    var id: String { rawValue }
}

struct ScreenerFilter {
    var optionType: ScreenerOptionType = .both
    var moneyness: MoneynessFilter = .all
    var deltaMin: Double = 0.0
    var deltaMax: Double = 1.0
    var ivMin: Double = 0.0       // percentage (e.g. 0 for 0%)
    var ivMax: Double = 200.0     // percentage (e.g. 200 for 200%)
    var oiMin: Int = 0
    var volumeMin: Int = 0
    var premiumMin: Double = 0.0
    var premiumMax: Double = 100000.0
    var sortBy: ScreenerSortBy = .oi
    var sortAscending: Bool = false
}

struct ScreenerResult: Identifiable {
    let id = UUID()
    let strikePrice: Double
    let optionType: OptionType
    let ltp: Double
    let iv: Double          // decimal (e.g. 0.15)
    let delta: Double
    let gamma: Double
    let theta: Double
    let vega: Double
    let oi: Int
    let oiChange: Int
    let volume: Int
}

// MARK: - OptionScreenerViewModel

@MainActor
final class OptionScreenerViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var filters = ScreenerFilter()
    @Published var results: [ScreenerResult] = []
    @Published var resultCount: Int = 0

    // MARK: - Private Properties

    private var optionChain: [OptionChainRow] = []
    private var spotPrice: Double = 0

    // MARK: - Public Methods

    /// Loads option chain data and triggers filtering.
    func setData(chain: [OptionChainRow], spot: Double) {
        optionChain = chain
        spotPrice = spot
        applyFilters()
    }

    /// Updates filters and reapplies them.
    func updateFilters(_ newFilters: ScreenerFilter) {
        filters = newFilters
        applyFilters()
    }

    /// Resets all filters to defaults.
    func resetFilters() {
        filters = ScreenerFilter()
        applyFilters()
    }

    // MARK: - Private Methods

    private func applyFilters() {
        var filtered: [ScreenerResult] = []

        for row in optionChain {
            // Process call options
            if filters.optionType == .both || filters.optionType == .ce {
                if let call = row.callOption {
                    if let result = evaluateOption(call) {
                        filtered.append(result)
                    }
                }
            }

            // Process put options
            if filters.optionType == .both || filters.optionType == .pe {
                if let put = row.putOption {
                    if let result = evaluateOption(put) {
                        filtered.append(result)
                    }
                }
            }
        }

        // Sort results
        filtered.sort { a, b in
            let comparison: Bool
            switch filters.sortBy {
            case .delta:
                comparison = abs(a.delta) < abs(b.delta)
            case .iv:
                comparison = a.iv < b.iv
            case .oi:
                comparison = a.oi < b.oi
            case .volume:
                comparison = a.volume < b.volume
            case .premium:
                comparison = a.ltp < b.ltp
            }
            return filters.sortAscending ? comparison : !comparison
        }

        results = filtered
        resultCount = filtered.count
    }

    private func evaluateOption(_ option: OptionData) -> ScreenerResult? {
        // Moneyness filter
        switch filters.moneyness {
        case .all:
            break
        case .atm:
            guard option.isATM else { return nil }
        case .itm:
            guard option.isITM && !option.isATM else { return nil }
        case .otm:
            guard !option.isITM && !option.isATM else { return nil }
        }

        // Compute Greeks via BlackScholesEngine
        let iv = option.impliedVolatility > 0 ? option.impliedVolatility : 0.15
        let T = option.preciseTimeToExpiryYears

        let greeks: GreeksResult
        if T > 0 && iv > 0 {
            switch option.optionType {
            case .call:
                greeks = BlackScholesEngine.calculateCallGreeks(
                    spotPrice: spotPrice,
                    strikePrice: option.strikePrice,
                    timeToExpiry: T,
                    riskFreeRate: 0.065,
                    volatility: iv
                )
            case .put:
                greeks = BlackScholesEngine.calculatePutGreeks(
                    spotPrice: spotPrice,
                    strikePrice: option.strikePrice,
                    timeToExpiry: T,
                    riskFreeRate: 0.065,
                    volatility: iv
                )
            }
        } else {
            greeks = GreeksResult(delta: 0, gamma: 0, theta: 0, vega: 0, rho: 0)
        }

        // Delta filter (use absolute value for comparison)
        let absDelta = abs(greeks.delta)
        guard absDelta >= filters.deltaMin && absDelta <= filters.deltaMax else { return nil }

        // IV filter (compare as percentage)
        let ivPercent = iv * 100.0
        guard ivPercent >= filters.ivMin && ivPercent <= filters.ivMax else { return nil }

        // OI filter
        guard option.openInterest >= filters.oiMin else { return nil }

        // Volume filter
        guard option.volume >= filters.volumeMin else { return nil }

        // Premium filter
        guard option.lastTradedPrice >= filters.premiumMin &&
              option.lastTradedPrice <= filters.premiumMax else { return nil }

        return ScreenerResult(
            strikePrice: option.strikePrice,
            optionType: option.optionType,
            ltp: option.lastTradedPrice,
            iv: iv,
            delta: greeks.delta,
            gamma: greeks.gamma,
            theta: greeks.theta,
            vega: greeks.vega,
            oi: option.openInterest,
            oiChange: option.changeInOI,
            volume: option.volume
        )
    }
}
