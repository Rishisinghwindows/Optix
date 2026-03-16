import Foundation
import Combine

// MARK: - Simulation Supporting Types

struct SimulationLeg: Identifiable {
    let id = UUID()
    let spotPrice: Double
    let strikePrice: Double
    let optionType: OptionType
    let entryPremium: Double
    let iv: Double              // decimal (e.g. 0.15)
    let daysToExpiry: Int
    let lotSize: Int
    let quantity: Int
}

struct LegSimResult: Identifiable {
    let id = UUID()
    let leg: SimulationLeg
    let newPrice: Double
    let pnl: Double
    let pnlPercent: Double
    let newDelta: Double
    let newGamma: Double
    let newTheta: Double
    let newVega: Double
}

// PayoffPoint defined in Models/Strategy.swift

// MARK: - PnLSimulatorViewModel

@MainActor
final class PnLSimulatorViewModel: ObservableObject {

    // MARK: - Published Properties (Sliders)

    @Published var spotChangePercent: Double = 0     // range -10...10
    @Published var daysElapsed: Double = 0           // range 0...maxDTE
    @Published var ivChangePercent: Double = 0       // range -30...30

    // MARK: - Published Results

    @Published var totalPnl: Double = 0
    @Published var totalPnlPercent: Double = 0
    @Published var legResults: [LegSimResult] = []
    @Published var payoffPoints: [PayoffPoint] = []

    // MARK: - Legs

    var legs: [SimulationLeg] = []

    var maxDTE: Int {
        legs.map(\.daysToExpiry).max() ?? 30
    }

    // MARK: - Private

    private var cancellables = Set<AnyCancellable>()

    // MARK: - Initialization

    init() {
        setupAutoSimulation()
    }

    // MARK: - Public Methods

    func addLeg(_ leg: SimulationLeg) {
        legs.append(leg)
        simulate()
    }

    func removeLeg(at index: Int) {
        guard index >= 0 && index < legs.count else { return }
        legs.remove(at: index)
        simulate()
    }

    /// Convenience for setting up a single option simulation.
    func setupSingleOption(
        spotPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        premium: Double,
        iv: Double,
        dte: Int,
        lotSize: Int,
        quantity: Int = 1
    ) {
        legs.removeAll()
        let leg = SimulationLeg(
            spotPrice: spotPrice,
            strikePrice: strikePrice,
            optionType: optionType,
            entryPremium: premium,
            iv: iv,
            daysToExpiry: dte,
            lotSize: lotSize,
            quantity: quantity
        )
        legs.append(leg)
        // Reset sliders
        spotChangePercent = 0
        daysElapsed = 0
        ivChangePercent = 0
        simulate()
    }

    /// Core simulation logic, called whenever sliders change.
    func simulate() {
        guard !legs.isEmpty else {
            totalPnl = 0
            totalPnlPercent = 0
            legResults = []
            payoffPoints = []
            return
        }

        var results: [LegSimResult] = []
        var totalPnlAccum: Double = 0
        var totalInvestment: Double = 0

        for leg in legs {
            let newSpot = leg.spotPrice * (1.0 + spotChangePercent / 100.0)
            let newIV = max(0.01, min(5.0, leg.iv * (1.0 + ivChangePercent / 100.0)))
            let remainingDays = max(leg.daysToExpiry - Int(daysElapsed), 0)
            let T = Double(remainingDays) / 365.0

            let newPrice: Double
            let greeks: GreeksResult

            if T > 0 {
                // Use BlackScholesEngine to compute new option price
                switch leg.optionType {
                case .call:
                    newPrice = BlackScholesEngine.calculateCallPrice(
                        spotPrice: newSpot,
                        strikePrice: leg.strikePrice,
                        timeToExpiry: T,
                        riskFreeRate: 0.065,
                        volatility: newIV
                    )
                    greeks = BlackScholesEngine.calculateCallGreeks(
                        spotPrice: newSpot,
                        strikePrice: leg.strikePrice,
                        timeToExpiry: T,
                        riskFreeRate: 0.065,
                        volatility: newIV
                    )
                case .put:
                    newPrice = BlackScholesEngine.calculatePutPrice(
                        spotPrice: newSpot,
                        strikePrice: leg.strikePrice,
                        timeToExpiry: T,
                        riskFreeRate: 0.065,
                        volatility: newIV
                    )
                    greeks = BlackScholesEngine.calculatePutGreeks(
                        spotPrice: newSpot,
                        strikePrice: leg.strikePrice,
                        timeToExpiry: T,
                        riskFreeRate: 0.065,
                        volatility: newIV
                    )
                }
            } else {
                // Expired - intrinsic value only
                switch leg.optionType {
                case .call:
                    newPrice = max(0, newSpot - leg.strikePrice)
                case .put:
                    newPrice = max(0, leg.strikePrice - newSpot)
                }
                greeks = GreeksResult(delta: 0, gamma: 0, theta: 0, vega: 0, rho: 0)
            }

            let positionSize = Double(leg.quantity) * Double(leg.lotSize)
            let legPnl = (newPrice - leg.entryPremium) * positionSize

            let legPnlPercent: Double
            if leg.entryPremium > 0 {
                legPnlPercent = ((newPrice - leg.entryPremium) / leg.entryPremium) * 100.0
            } else {
                legPnlPercent = 0
            }

            totalPnlAccum += legPnl
            totalInvestment += leg.entryPremium * positionSize

            results.append(LegSimResult(
                leg: leg,
                newPrice: newPrice,
                pnl: legPnl,
                pnlPercent: legPnlPercent,
                newDelta: greeks.delta,
                newGamma: greeks.gamma,
                newTheta: greeks.theta,
                newVega: greeks.vega
            ))
        }

        legResults = results
        totalPnl = totalPnlAccum
        if totalInvestment > 0 {
            totalPnlPercent = (totalPnlAccum / totalInvestment) * 100.0
        } else {
            totalPnlPercent = 0
        }

        // Generate payoff chart points
        generatePayoffChart()
    }

    /// Resets all sliders to their default values.
    func resetSliders() {
        spotChangePercent = 0
        daysElapsed = 0
        ivChangePercent = 0
    }

    // MARK: - Private Methods

    private func setupAutoSimulation() {
        // Debounce slider changes and auto-simulate
        Publishers.CombineLatest3(
            $spotChangePercent,
            $daysElapsed,
            $ivChangePercent
        )
        .debounce(for: .milliseconds(50), scheduler: RunLoop.main)
        .sink { [weak self] _, _, _ in
            self?.simulate()
        }
        .store(in: &cancellables)
    }

    private func generatePayoffChart() {
        guard let firstLeg = legs.first else {
            payoffPoints = []
            return
        }

        let baseSpot = firstLeg.spotPrice
        let rangeMin = baseSpot * 0.90
        let rangeMax = baseSpot * 1.10
        let step = (rangeMax - rangeMin) / 40.0

        var points: [PayoffPoint] = []

        for i in 0...40 {
            let simSpot = rangeMin + step * Double(i)
            var totalPnlAtSpot: Double = 0

            for leg in legs {
                let newIV = max(0.01, min(5.0, leg.iv * (1.0 + ivChangePercent / 100.0)))
                let remainingDays = max(leg.daysToExpiry - Int(daysElapsed), 0)
                let T = Double(remainingDays) / 365.0

                let price: Double
                if T > 0 {
                    switch leg.optionType {
                    case .call:
                        price = BlackScholesEngine.calculateCallPrice(
                            spotPrice: simSpot,
                            strikePrice: leg.strikePrice,
                            timeToExpiry: T,
                            riskFreeRate: 0.065,
                            volatility: newIV
                        )
                    case .put:
                        price = BlackScholesEngine.calculatePutPrice(
                            spotPrice: simSpot,
                            strikePrice: leg.strikePrice,
                            timeToExpiry: T,
                            riskFreeRate: 0.065,
                            volatility: newIV
                        )
                    }
                } else {
                    switch leg.optionType {
                    case .call:
                        price = max(0, simSpot - leg.strikePrice)
                    case .put:
                        price = max(0, leg.strikePrice - simSpot)
                    }
                }

                let positionSize = Double(leg.quantity) * Double(leg.lotSize)
                totalPnlAtSpot += (price - leg.entryPremium) * positionSize
            }

            points.append(PayoffPoint(price: simSpot, payoff: totalPnlAtSpot))
        }

        payoffPoints = points
    }
}
