import Foundation

/// Target and Stop-Loss Calculator
/// Calculates expected option prices at different underlying levels
final class TargetCalculator {

    // MARK: - Singleton

    static let shared = TargetCalculator()

    private init() {}

    // MARK: - Main Calculation Methods

    /// Calculate target and stop-loss option prices
    /// - Parameters:
    ///   - option: The option being analyzed
    ///   - targetSpot: Target Nifty spot price
    ///   - stopLossSpot: Stop-loss Nifty spot price
    ///   - daysToTarget: Expected days to reach target (for theta adjustment)
    /// - Returns: TargetCalculation with all price projections
    func calculateTargetSL(
        option: OptionData,
        targetSpot: Double,
        stopLossSpot: Double,
        daysToTarget: Double = 0
    ) -> TargetCalculation {
        let currentSpot = option.underlyingValue
        let strikePrice = option.strikePrice
        let iv = option.impliedVolatility
        let timeToExpiry = option.timeToExpiryYears
        let isCall = option.optionType == .call

        // Adjust time to expiry for theta calculation
        let adjustedTimeToExpiry = max(timeToExpiry - (daysToTarget / 365.0), 0.001)

        // Calculate current Greeks
        let currentGreeks = isCall ?
            BlackScholesEngine.calculateCallGreeks(
                spotPrice: currentSpot,
                strikePrice: strikePrice,
                timeToExpiry: timeToExpiry,
                volatility: iv
            ) :
            BlackScholesEngine.calculatePutGreeks(
                spotPrice: currentSpot,
                strikePrice: strikePrice,
                timeToExpiry: timeToExpiry,
                volatility: iv
            )

        // Calculate option price at target spot
        let targetOptionPrice = isCall ?
            BlackScholesEngine.calculateCallPrice(
                spotPrice: targetSpot,
                strikePrice: strikePrice,
                timeToExpiry: adjustedTimeToExpiry,
                volatility: iv
            ) :
            BlackScholesEngine.calculatePutPrice(
                spotPrice: targetSpot,
                strikePrice: strikePrice,
                timeToExpiry: adjustedTimeToExpiry,
                volatility: iv
            )

        // Calculate option price at stop-loss spot
        let stopLossOptionPrice = isCall ?
            BlackScholesEngine.calculateCallPrice(
                spotPrice: stopLossSpot,
                strikePrice: strikePrice,
                timeToExpiry: adjustedTimeToExpiry,
                volatility: iv
            ) :
            BlackScholesEngine.calculatePutPrice(
                spotPrice: stopLossSpot,
                strikePrice: strikePrice,
                timeToExpiry: adjustedTimeToExpiry,
                volatility: iv
            )

        return TargetCalculation(
            optionType: option.optionType,
            strikePrice: strikePrice,
            currentSpot: currentSpot,
            currentOptionPrice: option.lastTradedPrice,
            targetSpot: targetSpot,
            stopLossSpot: stopLossSpot,
            targetOptionPrice: targetOptionPrice,
            stopLossOptionPrice: stopLossOptionPrice,
            greeks: currentGreeks,
            impliedVolatility: iv,
            daysToExpiry: option.daysToExpiry
        )
    }

    /// Calculate option price at a specific spot level
    func calculateOptionPrice(
        spotPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        timeToExpiry: Double,
        volatility: Double
    ) -> Double {
        switch optionType {
        case .call:
            return BlackScholesEngine.calculateCallPrice(
                spotPrice: spotPrice,
                strikePrice: strikePrice,
                timeToExpiry: timeToExpiry,
                volatility: volatility
            )
        case .put:
            return BlackScholesEngine.calculatePutPrice(
                spotPrice: spotPrice,
                strikePrice: strikePrice,
                timeToExpiry: timeToExpiry,
                volatility: volatility
            )
        }
    }

    /// Calculate Greeks for any option
    func calculateGreeks(
        spotPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        timeToExpiry: Double,
        volatility: Double
    ) -> GreeksResult {
        switch optionType {
        case .call:
            return BlackScholesEngine.calculateCallGreeks(
                spotPrice: spotPrice,
                strikePrice: strikePrice,
                timeToExpiry: timeToExpiry,
                volatility: volatility
            )
        case .put:
            return BlackScholesEngine.calculatePutGreeks(
                spotPrice: spotPrice,
                strikePrice: strikePrice,
                timeToExpiry: timeToExpiry,
                volatility: volatility
            )
        }
    }

    // MARK: - Quick Estimation Methods

    /// Quick estimate of option price change using Delta
    /// Good for small underlying moves
    func quickEstimate(
        currentOptionPrice: Double,
        delta: Double,
        underlyingMove: Double
    ) -> Double {
        return currentOptionPrice + (delta * underlyingMove)
    }

    /// More accurate estimate using Delta and Gamma
    /// Better for larger underlying moves
    func accurateEstimate(
        currentOptionPrice: Double,
        delta: Double,
        gamma: Double,
        theta: Double,
        underlyingMove: Double,
        daysElapsed: Double = 0
    ) -> Double {
        let deltaEffect = delta * underlyingMove
        let gammaEffect = 0.5 * gamma * underlyingMove * underlyingMove
        let thetaEffect = theta * daysElapsed

        return max(currentOptionPrice + deltaEffect + gammaEffect + thetaEffect, 0)
    }

    // MARK: - Price Level Finder

    /// Find the spot price at which option reaches target price
    /// Uses binary search
    func findSpotForTargetPrice(
        targetOptionPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        timeToExpiry: Double,
        volatility: Double,
        currentSpot: Double
    ) -> Double? {
        let isCall = optionType == .call

        // Define search range
        let minSpot = max(strikePrice * 0.7, currentSpot * 0.9)
        let maxSpot = min(strikePrice * 1.3, currentSpot * 1.1)

        var low = isCall ? currentSpot : minSpot
        var high = isCall ? maxSpot : currentSpot

        // Binary search
        for _ in 0..<50 {
            let mid = (low + high) / 2
            let price = calculateOptionPrice(
                spotPrice: mid,
                strikePrice: strikePrice,
                optionType: optionType,
                timeToExpiry: timeToExpiry,
                volatility: volatility
            )

            if abs(price - targetOptionPrice) < 0.1 {
                return mid
            }

            if isCall {
                if price > targetOptionPrice {
                    high = mid
                } else {
                    low = mid
                }
            } else {
                if price > targetOptionPrice {
                    low = mid
                } else {
                    high = mid
                }
            }
        }

        return nil
    }

    // MARK: - Position P&L Calculator

    /// Calculate P&L for a position at different spot levels
    func calculatePositionPnL(
        quantity: Int,
        entryPrice: Double,
        option: OptionData,
        spotLevels: [Double],
        lotSize: Int = 25
    ) -> [(spot: Double, pnl: Double)] {
        let totalQty = Double(quantity * lotSize)

        return spotLevels.map { spot in
            let optionPrice = calculateOptionPrice(
                spotPrice: spot,
                strikePrice: option.strikePrice,
                optionType: option.optionType,
                timeToExpiry: option.timeToExpiryYears,
                volatility: option.impliedVolatility
            )
            let pnl = (optionPrice - entryPrice) * totalQty
            return (spot: spot, pnl: pnl)
        }
    }

    // MARK: - Suggested Targets

    /// Suggest target and stop-loss based on risk-reward preferences
    func suggestTargetSL(
        option: OptionData,
        riskRewardRatio: Double = 2.0,
        maxRisk: Double? = nil
    ) -> (target: Double, stopLoss: Double)? {
        let currentSpot = option.underlyingValue
        let delta = abs(option.optionType == .call ?
            BlackScholesEngine.calculateCallGreeks(
                spotPrice: currentSpot,
                strikePrice: option.strikePrice,
                timeToExpiry: option.timeToExpiryYears,
                volatility: option.impliedVolatility
            ).delta :
            BlackScholesEngine.calculatePutGreeks(
                spotPrice: currentSpot,
                strikePrice: option.strikePrice,
                timeToExpiry: option.timeToExpiryYears,
                volatility: option.impliedVolatility
            ).delta)

        // Base movement calculation
        let baseMove = option.lastTradedPrice / (delta * riskRewardRatio)

        let isCall = option.optionType == .call

        // Calculate target and stop-loss spot levels
        let targetSpot: Double
        let stopLossSpot: Double

        if isCall {
            targetSpot = currentSpot + (baseMove * riskRewardRatio)
            stopLossSpot = currentSpot - baseMove
        } else {
            targetSpot = currentSpot - (baseMove * riskRewardRatio)
            stopLossSpot = currentSpot + baseMove
        }

        return (target: targetSpot, stopLoss: stopLossSpot)
    }

    // MARK: - Volatility Impact

    /// Calculate option prices at different IV levels
    func calculateVolatilityImpact(
        option: OptionData,
        ivChanges: [Double] = [-0.05, -0.02, 0, 0.02, 0.05]
    ) -> [(iv: Double, price: Double)] {
        let baseIV = option.impliedVolatility

        return ivChanges.map { change in
            let newIV = max(baseIV + change, 0.01)
            let price = calculateOptionPrice(
                spotPrice: option.underlyingValue,
                strikePrice: option.strikePrice,
                optionType: option.optionType,
                timeToExpiry: option.timeToExpiryYears,
                volatility: newIV
            )
            return (iv: newIV, price: price)
        }
    }
}
