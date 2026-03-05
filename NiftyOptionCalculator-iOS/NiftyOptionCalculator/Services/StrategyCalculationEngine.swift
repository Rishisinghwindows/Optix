import Foundation

// MARK: - Strategy Calculation Engine

/// Engine for calculating strategy payoffs, breakevens, and analysis metrics.
final class StrategyCalculationEngine {

    // MARK: - Singleton

    static let shared = StrategyCalculationEngine()
    private init() {}

    // MARK: - Constants

    private let payoffPointCount = 200
    private let breakevenTolerance = 0.01
    private let maxIterations = 100

    // MARK: - Payoff Calculation

    /// Calculate payoff at expiry for a single price point.
    /// - Parameters:
    ///   - strategy: The strategy to evaluate
    ///   - price: The underlying price at expiry
    /// - Returns: Net P&L at the given price
    func calculatePayoffAtExpiry(strategy: Strategy, atPrice price: Double) -> Double {
        var totalPayoff: Double = 0

        for leg in strategy.legs {
            let legPayoff = calculateLegPayoff(leg: leg, atPrice: price, lotSize: strategy.lotSize)
            totalPayoff += legPayoff
        }

        return totalPayoff
    }

    /// Calculate payoff for a single leg at expiry.
    private func calculateLegPayoff(leg: StrategyLeg, atPrice price: Double, lotSize: Int) -> Double {
        let intrinsicValue: Double
        let premium = leg.premium
        let quantity = Double(leg.quantity)
        let lots = Double(lotSize)

        switch leg.optionType {
        case .call:
            intrinsicValue = max(0, price - leg.strikePrice)
        case .put:
            intrinsicValue = max(0, leg.strikePrice - price)
        }

        let payoff: Double
        switch leg.position {
        case .buy:
            // Long: Pay premium, receive intrinsic value
            payoff = (intrinsicValue - premium) * quantity * lots
        case .sell:
            // Short: Receive premium, pay intrinsic value
            payoff = (premium - intrinsicValue) * quantity * lots
        }

        return payoff
    }

    // MARK: - Payoff Curve Generation

    /// Generate a payoff curve for the strategy.
    /// - Parameters:
    ///   - strategy: The strategy to evaluate
    ///   - spotPrice: Current spot price (for centering the range)
    ///   - rangePercent: Range around spot price to evaluate (default 20%)
    /// - Returns: PayoffData containing points and analysis
    func generatePayoffCurve(
        strategy: Strategy,
        spotPrice: Double,
        rangePercent: Double = 0.20
    ) -> PayoffData {
        guard !strategy.legs.isEmpty else {
            return PayoffData(
                points: [],
                maxProfit: 0,
                maxLoss: 0,
                breakevens: [],
                spotPrice: spotPrice,
                payoffAtSpot: 0
            )
        }

        // Determine price range
        let minPrice = spotPrice * (1 - rangePercent)
        let maxPrice = spotPrice * (1 + rangePercent)
        let step = (maxPrice - minPrice) / Double(payoffPointCount - 1)

        // Generate payoff points
        var points: [PayoffPoint] = []
        var maxProfit: Double = -.infinity
        var maxLoss: Double = .infinity

        for i in 0..<payoffPointCount {
            let price = minPrice + (Double(i) * step)
            let payoff = calculatePayoffAtExpiry(strategy: strategy, atPrice: price)

            points.append(PayoffPoint(price: price, payoff: payoff))

            maxProfit = max(maxProfit, payoff)
            maxLoss = min(maxLoss, payoff)
        }

        // Check for unlimited profit/loss at boundaries
        let veryHighPrice = spotPrice * 3.0
        let veryLowPrice = spotPrice * 0.1
        let payoffAtHigh = calculatePayoffAtExpiry(strategy: strategy, atPrice: veryHighPrice)
        let payoffAtLow = calculatePayoffAtExpiry(strategy: strategy, atPrice: veryLowPrice)

        // If payoff keeps increasing, it's unlimited
        if payoffAtHigh > maxProfit * 1.5 {
            maxProfit = Double.infinity
        }
        if payoffAtLow > maxProfit * 1.5 {
            maxProfit = Double.infinity
        }

        // If loss keeps increasing, it's unlimited
        if payoffAtHigh < maxLoss * 1.5 {
            maxLoss = -Double.infinity
        }
        if payoffAtLow < maxLoss * 1.5 {
            maxLoss = -Double.infinity
        }

        // Calculate breakevens
        let breakevens = calculateBreakevens(
            strategy: strategy,
            minPrice: minPrice,
            maxPrice: maxPrice
        )

        // Calculate payoff at current spot
        let payoffAtSpot = calculatePayoffAtExpiry(strategy: strategy, atPrice: spotPrice)

        return PayoffData(
            points: points,
            maxProfit: maxProfit.isInfinite ? 10_000_000 : maxProfit,
            maxLoss: maxLoss.isInfinite ? -10_000_000 : maxLoss,
            breakevens: breakevens,
            spotPrice: spotPrice,
            payoffAtSpot: payoffAtSpot
        )
    }

    // MARK: - Breakeven Calculation

    /// Calculate breakeven points using bisection method.
    func calculateBreakevens(
        strategy: Strategy,
        minPrice: Double,
        maxPrice: Double
    ) -> [Double] {
        var breakevens: [Double] = []
        let step = (maxPrice - minPrice) / 1000.0

        var prevPrice = minPrice
        var prevPayoff = calculatePayoffAtExpiry(strategy: strategy, atPrice: prevPrice)

        var currentPrice = minPrice + step
        while currentPrice <= maxPrice {
            let currentPayoff = calculatePayoffAtExpiry(strategy: strategy, atPrice: currentPrice)

            // Check for sign change (zero crossing)
            if (prevPayoff < 0 && currentPayoff > 0) || (prevPayoff > 0 && currentPayoff < 0) {
                // Use bisection to find precise breakeven
                if let breakeven = findBreakevenBisection(
                    strategy: strategy,
                    low: prevPrice,
                    high: currentPrice
                ) {
                    // Avoid duplicate breakevens
                    if !breakevens.contains(where: { abs($0 - breakeven) < 1 }) {
                        breakevens.append(breakeven)
                    }
                }
            }

            // Check for exact zero
            if abs(currentPayoff) < breakevenTolerance {
                if !breakevens.contains(where: { abs($0 - currentPrice) < 1 }) {
                    breakevens.append(currentPrice)
                }
            }

            prevPrice = currentPrice
            prevPayoff = currentPayoff
            currentPrice += step
        }

        return breakevens.sorted()
    }

    /// Find breakeven using bisection method.
    private func findBreakevenBisection(
        strategy: Strategy,
        low: Double,
        high: Double
    ) -> Double? {
        var lo = low
        var hi = high
        var iterations = 0

        while iterations < maxIterations {
            let mid = (lo + hi) / 2
            let payoff = calculatePayoffAtExpiry(strategy: strategy, atPrice: mid)

            if abs(payoff) < breakevenTolerance {
                return mid
            }

            let payoffLo = calculatePayoffAtExpiry(strategy: strategy, atPrice: lo)
            if (payoffLo < 0 && payoff > 0) || (payoffLo > 0 && payoff < 0) {
                hi = mid
            } else {
                lo = mid
            }

            iterations += 1
        }

        return (lo + hi) / 2
    }

    // MARK: - Greeks Calculation

    /// Calculate net Greeks for the entire strategy.
    func calculateNetGreeks(strategy: Strategy) -> StrategyAnalysis.NetGreeks {
        var totalDelta: Double = 0
        var totalGamma: Double = 0
        var totalTheta: Double = 0
        var totalVega: Double = 0

        let lotSize = Double(strategy.lotSize)

        for leg in strategy.legs {
            let multiplier = leg.position.multiplier
            let quantity = Double(leg.quantity)

            totalDelta += leg.delta * quantity * multiplier * lotSize
            totalGamma += leg.gamma * quantity * multiplier * lotSize
            totalTheta += leg.theta * quantity * multiplier * lotSize
            totalVega += leg.vega * quantity * multiplier * lotSize
        }

        return StrategyAnalysis.NetGreeks(
            delta: totalDelta,
            gamma: totalGamma,
            theta: totalTheta,
            vega: totalVega
        )
    }

    // MARK: - Probability of Profit

    /// Calculate probability of profit using normal distribution.
    /// Assumes log-normal distribution of prices.
    func calculateProbabilityOfProfit(
        strategy: Strategy,
        spotPrice: Double,
        volatility: Double,
        daysToExpiry: Int
    ) -> Double {
        guard !strategy.legs.isEmpty && daysToExpiry > 0 else {
            return 0
        }

        let payoffData = generatePayoffCurve(
            strategy: strategy,
            spotPrice: spotPrice,
            rangePercent: 0.30
        )

        guard !payoffData.breakevens.isEmpty else {
            // No breakevens - strategy is always profitable or always losing
            let payoffAtSpot = calculatePayoffAtExpiry(strategy: strategy, atPrice: spotPrice)
            return payoffAtSpot > 0 ? 1.0 : 0.0
        }

        // Calculate probability using normal CDF
        let t = Double(daysToExpiry) / 365.0
        let sqrtT = sqrt(t)

        // Sum probabilities of profitable regions
        var probProfit: Double = 0

        // Sort breakevens
        let sortedBreakevens = payoffData.breakevens.sorted()

        // Check payoff at various points to determine profitable regions
        for i in 0...sortedBreakevens.count {
            let testPrice: Double
            if i == 0 {
                testPrice = sortedBreakevens[0] - 100
            } else if i == sortedBreakevens.count {
                testPrice = sortedBreakevens[i - 1] + 100
            } else {
                testPrice = (sortedBreakevens[i - 1] + sortedBreakevens[i]) / 2
            }

            let payoff = calculatePayoffAtExpiry(strategy: strategy, atPrice: testPrice)

            if payoff > 0 {
                // This region is profitable
                let lowerBound = i == 0 ? 0 : sortedBreakevens[i - 1]
                let upperBound = i == sortedBreakevens.count ? Double.infinity : sortedBreakevens[i]

                let lowerProb: Double
                let upperProb: Double

                if lowerBound == 0 {
                    lowerProb = 0
                } else {
                    let d = (log(lowerBound / spotPrice) + 0.5 * volatility * volatility * t) / (volatility * sqrtT)
                    lowerProb = normalCDF(d)
                }

                if upperBound.isInfinite {
                    upperProb = 1.0
                } else {
                    let d = (log(upperBound / spotPrice) + 0.5 * volatility * volatility * t) / (volatility * sqrtT)
                    upperProb = normalCDF(d)
                }

                probProfit += upperProb - lowerProb
            }
        }

        return min(max(probProfit, 0), 1)
    }

    /// Standard normal cumulative distribution function.
    private func normalCDF(_ x: Double) -> Double {
        return 0.5 * erfc(-x / sqrt(2))
    }

    // MARK: - Margin Estimation

    /// Estimate margin requirement (simplified SPAN-like calculation).
    func estimateMargin(
        strategy: Strategy,
        spotPrice: Double,
        volatility: Double
    ) -> Double {
        var margin: Double = 0
        let lotSize = Double(strategy.lotSize)

        // Check if strategy has naked short positions
        let hasShortCall = strategy.legs.contains { $0.position == .sell && $0.optionType == .call }
        let hasShortPut = strategy.legs.contains { $0.position == .sell && $0.optionType == .put }

        if hasShortCall || hasShortPut {
            // Calculate worst-case loss (simplified)
            let downMove = spotPrice * 0.15 // 15% down move
            let upMove = spotPrice * 0.15 // 15% up move

            let lossOnDown = -calculatePayoffAtExpiry(strategy: strategy, atPrice: spotPrice - downMove)
            let lossOnUp = -calculatePayoffAtExpiry(strategy: strategy, atPrice: spotPrice + upMove)

            let worstLoss = max(lossOnDown, lossOnUp, 0)

            // Base margin is worst-case loss plus premium buffer
            margin = worstLoss * 1.2 // 20% buffer

            // Add premium received as additional requirement
            for leg in strategy.legs where leg.position == .sell {
                margin += leg.premium * Double(leg.quantity) * lotSize * 0.5
            }

            // Minimum margin for short options
            let shortLegs = strategy.legs.filter { $0.position == .sell }
            let minMarginPerLot = spotPrice * 0.05 * lotSize // 5% of spot per lot
            margin = max(margin, minMarginPerLot * Double(shortLegs.count))
        } else {
            // All long positions - margin is the premium paid
            margin = abs(strategy.netPremium)
        }

        return margin
    }

    // MARK: - Full Analysis

    /// Generate complete strategy analysis.
    func analyzeStrategy(
        strategy: Strategy,
        spotPrice: Double,
        volatility: Double
    ) -> StrategyAnalysis {
        let payoffData = generatePayoffCurve(
            strategy: strategy,
            spotPrice: spotPrice,
            rangePercent: 0.25
        )

        let netGreeks = calculateNetGreeks(strategy: strategy)

        let pop = calculateProbabilityOfProfit(
            strategy: strategy,
            spotPrice: spotPrice,
            volatility: volatility,
            daysToExpiry: strategy.daysToExpiry
        )

        let margin = estimateMargin(
            strategy: strategy,
            spotPrice: spotPrice,
            volatility: volatility
        )

        // Expected value (simplified)
        let expectedValue = payoffData.payoffAtSpot * pop + payoffData.maxLoss * (1 - pop)

        return StrategyAnalysis(
            maxProfit: payoffData.maxProfit,
            maxLoss: payoffData.maxLoss,
            breakevens: payoffData.breakevens,
            probabilityOfProfit: pop,
            expectedValue: expectedValue,
            netGreeks: netGreeks,
            marginRequired: margin
        )
    }

    // MARK: - Strategy Template Generation

    /// Generate legs for a strategy template given spot price and strikes.
    func generateStrategyLegs(
        type: StrategyType,
        spotPrice: Double,
        strikeInterval: Double,
        availableStrikes: [Double],
        premiumLookup: (Double, OptionType) -> (premium: Double, iv: Double, delta: Double, gamma: Double, theta: Double, vega: Double)?
    ) -> [StrategyLeg] {
        // Find ATM strike
        let atmStrike = findNearestStrike(to: spotPrice, from: availableStrikes)

        var legs: [StrategyLeg] = []

        for template in type.defaultLegs {
            let targetStrike = atmStrike + (Double(template.strikeOffset) * strikeInterval)
            let strike = findNearestStrike(to: targetStrike, from: availableStrikes)

            if let data = premiumLookup(strike, template.optionType) {
                let leg = StrategyLeg(
                    strikePrice: strike,
                    optionType: template.optionType,
                    position: template.position,
                    quantity: template.quantity,
                    premium: data.premium,
                    impliedVolatility: data.iv,
                    delta: data.delta,
                    gamma: data.gamma,
                    theta: data.theta,
                    vega: data.vega
                )
                legs.append(leg)
            }
        }

        return legs
    }

    /// Find the nearest available strike to a target value.
    private func findNearestStrike(to target: Double, from strikes: [Double]) -> Double {
        guard !strikes.isEmpty else { return target }

        return strikes.min(by: { abs($0 - target) < abs($1 - target) }) ?? target
    }
}
