import Foundation

// MARK: - Greeks Result Model

struct GreeksResult: Equatable {
    let delta: Double
    let gamma: Double
    let theta: Double  // Per day
    let vega: Double   // Per 1% IV change
    let rho: Double    // Per 1% rate change

    init(delta: Double, gamma: Double, theta: Double, vega: Double, rho: Double) {
        self.delta = delta
        self.gamma = gamma
        self.theta = theta
        self.vega = vega
        self.rho = rho
    }

    // MARK: - Formatted Display Values

    var displayDelta: String {
        return String(format: "%.4f", delta)
    }

    var displayGamma: String {
        return String(format: "%.6f", gamma)
    }

    var displayTheta: String {
        return String(format: "%.2f", theta)
    }

    var displayVega: String {
        return String(format: "%.2f", vega)
    }

    var displayRho: String {
        return String(format: "%.2f", rho)
    }

    // MARK: - Greek Interpretations

    var deltaInterpretation: String {
        let absDelta = abs(delta)
        if absDelta > 0.7 {
            return L.greeksDeltaInterpDeepITM
        } else if absDelta > 0.45 && absDelta < 0.55 {
            return L.greeksDeltaInterpATM
        } else if absDelta < 0.3 {
            return L.greeksDeltaInterpDeepOTM
        }
        return L.greeksDeltaInterpModerate
    }

    var thetaInterpretation: String {
        if theta < -5 {
            return L.greeksThetaInterpHigh
        } else if theta < -2 {
            return L.greeksThetaInterpModerate
        }
        return L.greeksThetaInterpLow
    }

    var vegaInterpretation: String {
        if vega > 10 {
            return L.greeksVegaInterpHigh
        } else if vega > 5 {
            return L.greeksVegaInterpModerate
        }
        return L.greeksVegaInterpLow
    }

    // MARK: - Utility

    /// Expected option price change for a 1-point move in underlying
    func expectedPriceChange(underlyingMove: Double) -> Double {
        // First-order approximation: ΔOption ≈ Delta × ΔUnderlying
        // Second-order correction: + 0.5 × Gamma × (ΔUnderlying)²
        let firstOrder = delta * underlyingMove
        let secondOrder = 0.5 * gamma * underlyingMove * underlyingMove
        return firstOrder + secondOrder
    }

    /// Expected option price after a move in underlying
    func expectedPrice(currentPrice: Double, underlyingMove: Double, daysElapsed: Double = 0) -> Double {
        let priceChange = expectedPriceChange(underlyingMove: underlyingMove)
        let timeDecay = theta * daysElapsed
        return max(currentPrice + priceChange + timeDecay, 0)
    }
}

// MARK: - Target Calculation Result

struct TargetCalculation: Identifiable {
    let id: UUID
    let optionType: OptionType
    let strikePrice: Double
    let currentSpot: Double
    let currentOptionPrice: Double
    let targetSpot: Double
    let stopLossSpot: Double
    let targetOptionPrice: Double
    let stopLossOptionPrice: Double
    let targetProfit: Double
    let stopLossLoss: Double
    let riskRewardRatio: Double
    let greeks: GreeksResult
    let impliedVolatility: Double
    let daysToExpiry: Int

    init(
        optionType: OptionType,
        strikePrice: Double,
        currentSpot: Double,
        currentOptionPrice: Double,
        targetSpot: Double,
        stopLossSpot: Double,
        targetOptionPrice: Double,
        stopLossOptionPrice: Double,
        greeks: GreeksResult,
        impliedVolatility: Double,
        daysToExpiry: Int
    ) {
        self.id = UUID()
        self.optionType = optionType
        self.strikePrice = strikePrice
        self.currentSpot = currentSpot
        self.currentOptionPrice = currentOptionPrice
        self.targetSpot = targetSpot
        self.stopLossSpot = stopLossSpot
        self.targetOptionPrice = targetOptionPrice
        self.stopLossOptionPrice = stopLossOptionPrice
        self.targetProfit = targetOptionPrice - currentOptionPrice
        self.stopLossLoss = currentOptionPrice - stopLossOptionPrice
        self.riskRewardRatio = stopLossLoss > 0 ? targetProfit / stopLossLoss : 0
        self.greeks = greeks
        self.impliedVolatility = impliedVolatility
        self.daysToExpiry = daysToExpiry
    }

    // MARK: - Display Values

    var displayTargetPrice: String {
        return String(format: "%.2f", targetOptionPrice)
    }

    var displayStopLossPrice: String {
        return String(format: "%.2f", stopLossOptionPrice)
    }

    var displayProfit: String {
        let sign = targetProfit >= 0 ? "+" : ""
        return sign + String(format: "%.2f", targetProfit)
    }

    var displayLoss: String {
        return String(format: "-%.2f", stopLossLoss)
    }

    var displayRiskReward: String {
        return String(format: "1:%.2f", riskRewardRatio)
    }

    var profitPercentage: Double {
        guard currentOptionPrice > 0 else { return 0 }
        return (targetProfit / currentOptionPrice) * 100
    }

    var lossPercentage: Double {
        guard currentOptionPrice > 0 else { return 0 }
        return (stopLossLoss / currentOptionPrice) * 100
    }

    var displayProfitPercentage: String {
        return String(format: "+%.1f%%", profitPercentage)
    }

    var displayLossPercentage: String {
        return String(format: "-%.1f%%", lossPercentage)
    }

    var isGoodRiskReward: Bool {
        return riskRewardRatio >= 1.5
    }
}

// MARK: - Position Simulation

struct PositionSimulation: Identifiable {
    let id: UUID
    let quantity: Int
    let entryPrice: Double
    let currentPrice: Double
    let targetPrice: Double
    let stopLossPrice: Double
    let lotSize: Int

    init(
        quantity: Int,
        entryPrice: Double,
        currentPrice: Double,
        targetPrice: Double,
        stopLossPrice: Double,
        lotSize: Int = 25 // Nifty lot size
    ) {
        self.id = UUID()
        self.quantity = quantity
        self.entryPrice = entryPrice
        self.currentPrice = currentPrice
        self.targetPrice = targetPrice
        self.stopLossPrice = stopLossPrice
        self.lotSize = lotSize
    }

    var totalQuantity: Int {
        return quantity * lotSize
    }

    var investmentValue: Double {
        return Double(totalQuantity) * entryPrice
    }

    var currentValue: Double {
        return Double(totalQuantity) * currentPrice
    }

    var unrealizedPnL: Double {
        return currentValue - investmentValue
    }

    var targetPnL: Double {
        return Double(totalQuantity) * (targetPrice - entryPrice)
    }

    var stopLossPnL: Double {
        return Double(totalQuantity) * (stopLossPrice - entryPrice)
    }

    var displayInvestment: String {
        return formatCurrency(investmentValue)
    }

    var displayCurrentValue: String {
        return formatCurrency(currentValue)
    }

    var displayUnrealizedPnL: String {
        let sign = unrealizedPnL >= 0 ? "+" : ""
        return sign + formatCurrency(unrealizedPnL)
    }

    var displayTargetPnL: String {
        return "+" + formatCurrency(targetPnL)
    }

    var displayStopLossPnL: String {
        return formatCurrency(stopLossPnL)
    }

    private func formatCurrency(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencySymbol = "₹"
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: value)) ?? "₹0"
    }
}
