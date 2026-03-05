import Foundation
import SwiftUI
import Combine

// MARK: - Strategy Builder ViewModel

@MainActor
final class StrategyBuilderViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var selectedStrategyType: StrategyType = .longCall
    @Published var strategy: Strategy
    @Published var payoffData: PayoffData?
    @Published var analysis: StrategyAnalysis?

    @Published var isLoading = false
    @Published var showStrategySelector = false
    @Published var selectedLegIndex: Int?

    @Published var touchedPrice: Double?
    @Published var touchedPayoff: Double?

    // MARK: - Private Properties

    private let calculationEngine = StrategyCalculationEngine.shared
    private var cancellables = Set<AnyCancellable>()

    private var spotPrice: Double
    private var strikeInterval: Double
    private var lotSize: Int
    private var expiryDate: Date
    private var availableStrikes: [Double]
    private var optionChain: [OptionChainRow]

    // MARK: - Initialization

    init(
        spotPrice: Double,
        strikeInterval: Double,
        lotSize: Int,
        expiryDate: Date,
        optionChain: [OptionChainRow]
    ) {
        self.spotPrice = spotPrice
        self.strikeInterval = strikeInterval
        self.lotSize = lotSize
        self.expiryDate = expiryDate
        self.optionChain = optionChain
        self.availableStrikes = optionChain.map { $0.strikePrice }.sorted()

        self.strategy = Strategy(
            type: .longCall,
            lotSize: lotSize,
            underlyingPrice: spotPrice,
            expiryDate: expiryDate
        )

        setupBindings()
        generateStrategyLegs()
    }

    // MARK: - Setup

    private func setupBindings() {
        // Recalculate when strategy changes
        $strategy
            .debounce(for: .milliseconds(100), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.recalculate()
            }
            .store(in: &cancellables)
    }

    // MARK: - Public Methods

    /// Select a new strategy type.
    func selectStrategyType(_ type: StrategyType) {
        selectedStrategyType = type
        strategy.type = type
        strategy.name = type.rawValue
        generateStrategyLegs()
        showStrategySelector = false
    }

    /// Add a new leg to the strategy.
    func addLeg() {
        let atmStrike = findATMStrike()
        let newLeg = StrategyLeg(
            strikePrice: atmStrike,
            optionType: .call,
            position: .buy,
            quantity: 1,
            premium: getPremium(strike: atmStrike, type: .call) ?? 100
        )
        strategy.legs.append(newLeg)
    }

    /// Remove a leg at the specified index.
    func removeLeg(at index: Int) {
        guard index < strategy.legs.count else { return }
        strategy.legs.remove(at: index)
    }

    /// Update a leg's strike price.
    func updateLegStrike(at index: Int, strike: Double) {
        guard index < strategy.legs.count else { return }
        strategy.legs[index].strikePrice = strike

        // Update premium and Greeks for the new strike
        let leg = strategy.legs[index]
        if let data = getOptionData(strike: strike, type: leg.optionType) {
            strategy.legs[index].premium = data.lastTradedPrice
            strategy.legs[index].impliedVolatility = data.impliedVolatility
            strategy.legs[index].delta = data.delta ?? 0
            strategy.legs[index].gamma = data.gamma ?? 0
            strategy.legs[index].theta = data.theta ?? 0
            strategy.legs[index].vega = data.vega ?? 0
        }
    }

    /// Update a leg's option type.
    func updateLegOptionType(at index: Int, type: OptionType) {
        guard index < strategy.legs.count else { return }
        strategy.legs[index].optionType = type

        // Update premium for the new option type
        let leg = strategy.legs[index]
        if let data = getOptionData(strike: leg.strikePrice, type: type) {
            strategy.legs[index].premium = data.lastTradedPrice
            strategy.legs[index].impliedVolatility = data.impliedVolatility
            strategy.legs[index].delta = data.delta ?? 0
            strategy.legs[index].gamma = data.gamma ?? 0
            strategy.legs[index].theta = data.theta ?? 0
            strategy.legs[index].vega = data.vega ?? 0
        }
    }

    /// Update a leg's position (buy/sell).
    func updateLegPosition(at index: Int, position: LegPosition) {
        guard index < strategy.legs.count else { return }
        strategy.legs[index].position = position
    }

    /// Update a leg's quantity.
    func updateLegQuantity(at index: Int, quantity: Int) {
        guard index < strategy.legs.count else { return }
        strategy.legs[index].quantity = max(1, quantity)
    }

    /// Update a leg's premium manually.
    func updateLegPremium(at index: Int, premium: Double) {
        guard index < strategy.legs.count else { return }
        strategy.legs[index].premium = max(0, premium)
    }

    /// Handle touch on payoff chart.
    func handleChartTouch(at price: Double) {
        touchedPrice = price
        touchedPayoff = calculationEngine.calculatePayoffAtExpiry(
            strategy: strategy,
            atPrice: price
        )
    }

    /// Clear touch state.
    func clearChartTouch() {
        touchedPrice = nil
        touchedPayoff = nil
    }

    /// Add an option from the option chain to the strategy.
    func addOptionToStrategy(_ option: OptionData, position: LegPosition) {
        let leg = StrategyLeg(
            strikePrice: option.strikePrice,
            optionType: option.optionType,
            position: position,
            quantity: 1,
            premium: option.lastTradedPrice,
            impliedVolatility: option.impliedVolatility,
            delta: option.delta ?? 0,
            gamma: option.gamma ?? 0,
            theta: option.theta ?? 0,
            vega: option.vega ?? 0
        )
        strategy.legs.append(leg)
    }

    /// Reset strategy to default template.
    func resetToTemplate() {
        generateStrategyLegs()
    }

    /// Clear all legs.
    func clearAllLegs() {
        strategy.legs = []
    }

    // MARK: - Private Methods

    /// Generate legs based on current strategy type.
    private func generateStrategyLegs() {
        let legs = calculationEngine.generateStrategyLegs(
            type: selectedStrategyType,
            spotPrice: spotPrice,
            strikeInterval: strikeInterval,
            availableStrikes: availableStrikes
        ) { [weak self] strike, optionType in
            self?.lookupOptionData(strike: strike, type: optionType)
        }

        strategy.legs = legs
    }

    /// Look up option data for premium and Greeks.
    private func lookupOptionData(
        strike: Double,
        type: OptionType
    ) -> (premium: Double, iv: Double, delta: Double, gamma: Double, theta: Double, vega: Double)? {
        guard let data = getOptionData(strike: strike, type: type) else {
            return nil
        }

        return (
            premium: data.lastTradedPrice,
            iv: data.impliedVolatility,
            delta: data.delta ?? calculateDelta(strike: strike, type: type),
            gamma: data.gamma ?? 0,
            theta: data.theta ?? 0,
            vega: data.vega ?? 0
        )
    }

    /// Get option data from the option chain.
    private func getOptionData(strike: Double, type: OptionType) -> OptionData? {
        guard let row = optionChain.first(where: { $0.strikePrice == strike }) else {
            return nil
        }

        switch type {
        case .call:
            return row.callOption
        case .put:
            return row.putOption
        }
    }

    /// Get premium for a strike and type.
    private func getPremium(strike: Double, type: OptionType) -> Double? {
        return getOptionData(strike: strike, type: type)?.lastTradedPrice
    }

    /// Calculate approximate delta if not provided.
    private func calculateDelta(strike: Double, type: OptionType) -> Double {
        let moneyness = spotPrice / strike

        switch type {
        case .call:
            if moneyness > 1.05 { return 0.8 }
            if moneyness < 0.95 { return 0.2 }
            return 0.5
        case .put:
            if moneyness > 1.05 { return -0.2 }
            if moneyness < 0.95 { return -0.8 }
            return -0.5
        }
    }

    /// Find the ATM strike.
    private func findATMStrike() -> Double {
        guard !availableStrikes.isEmpty else { return spotPrice }
        return availableStrikes.min(by: { abs($0 - spotPrice) < abs($1 - spotPrice) }) ?? spotPrice
    }

    /// Recalculate payoff and analysis.
    private func recalculate() {
        guard strategy.isValid else {
            payoffData = nil
            analysis = nil
            return
        }

        // Get average IV from legs
        let avgIV = strategy.legs.isEmpty ? 0.2 :
            strategy.legs.reduce(0) { $0 + $1.impliedVolatility } / Double(strategy.legs.count)

        payoffData = calculationEngine.generatePayoffCurve(
            strategy: strategy,
            spotPrice: spotPrice,
            rangePercent: 0.20
        )

        analysis = calculationEngine.analyzeStrategy(
            strategy: strategy,
            spotPrice: spotPrice,
            volatility: avgIV
        )
    }

    // MARK: - Computed Properties

    var availableStrikesForPicker: [Double] {
        return availableStrikes
    }

    var currentSpotPrice: Double {
        return spotPrice
    }

    var isStrategyValid: Bool {
        return strategy.isValid
    }

    var strategyDescription: String {
        if strategy.legs.isEmpty {
            return "No legs added"
        }
        return strategy.legs.map { $0.shortDescription }.joined(separator: " + ")
    }
}
