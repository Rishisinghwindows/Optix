import Foundation
import SwiftUI

// MARK: - Trade Direction Enum

enum PaperTradeDirection: String, Codable, CaseIterable {
    case buy = "BUY"
    case sell = "SELL"

    var multiplier: Double {
        switch self {
        case .buy: return 1.0
        case .sell: return -1.0
        }
    }

    var color: Color {
        switch self {
        case .buy: return Theme.profit
        case .sell: return Theme.loss
        }
    }

    var displayName: String {
        rawValue
    }

    var icon: String {
        switch self {
        case .buy: return "arrow.up.circle.fill"
        case .sell: return "arrow.down.circle.fill"
        }
    }
}

// MARK: - Paper Trade (Audit Log Entry)

struct PaperTrade: Identifiable, Codable, Equatable {
    let id: UUID
    let timestamp: Date
    let index: String // TradingIndex.rawValue
    let strikePrice: Double
    let optionType: OptionType
    let direction: PaperTradeDirection
    let price: Double // LTP at execution
    let quantity: Int // lots
    let lotSize: Int
    let expiryDate: Date
    let underlyingPrice: Double // spot at execution

    init(
        id: UUID = UUID(),
        timestamp: Date = Date(),
        index: String,
        strikePrice: Double,
        optionType: OptionType,
        direction: PaperTradeDirection,
        price: Double,
        quantity: Int,
        lotSize: Int,
        expiryDate: Date,
        underlyingPrice: Double
    ) {
        self.id = id
        self.timestamp = timestamp
        self.index = index
        self.strikePrice = strikePrice
        self.optionType = optionType
        self.direction = direction
        self.price = price
        self.quantity = quantity
        self.lotSize = lotSize
        self.expiryDate = expiryDate
        self.underlyingPrice = underlyingPrice
    }

    // Computed properties
    var totalValue: Double {
        price * Double(quantity) * Double(lotSize)
    }

    var displayStrike: String {
        String(format: "%.0f", strikePrice)
    }

    var displayPrice: String {
        String(format: "%.2f", price)
    }

    var optionSymbol: String {
        "\(displayStrike) \(optionType.shortName)"
    }
}

// MARK: - Paper Position (Open Position)

struct PaperPosition: Identifiable, Codable, Equatable {
    let id: UUID
    let index: String
    let strikePrice: Double
    let optionType: OptionType
    let direction: PaperTradeDirection
    var quantity: Int // current lots held
    var averageEntryPrice: Double
    let entryDate: Date
    let expiryDate: Date
    let lotSize: Int
    var stopLoss: Double?
    var target: Double?

    // Server sync - ID from backend for cross-device sync
    var serverId: String?

    // Not persisted - updated live
    var currentLTP: Double = 0

    init(
        id: UUID = UUID(),
        index: String,
        strikePrice: Double,
        optionType: OptionType,
        direction: PaperTradeDirection,
        quantity: Int,
        averageEntryPrice: Double,
        entryDate: Date = Date(),
        expiryDate: Date,
        lotSize: Int,
        stopLoss: Double? = nil,
        target: Double? = nil,
        currentLTP: Double = 0,
        serverId: String? = nil
    ) {
        self.id = id
        self.index = index
        self.strikePrice = strikePrice
        self.optionType = optionType
        self.direction = direction
        self.quantity = quantity
        self.averageEntryPrice = averageEntryPrice
        self.entryDate = entryDate
        self.expiryDate = expiryDate
        self.lotSize = lotSize
        self.stopLoss = stopLoss
        self.target = target
        self.currentLTP = currentLTP
        self.serverId = serverId
    }

    // Computed properties
    var totalQuantity: Int {
        quantity * lotSize
    }

    var investmentValue: Double {
        averageEntryPrice * Double(totalQuantity)
    }

    var currentValue: Double {
        currentLTP * Double(totalQuantity)
    }

    var unrealizedPnL: Double {
        (currentLTP - averageEntryPrice) * Double(totalQuantity) * direction.multiplier
    }

    var unrealizedPnLPercent: Double {
        guard investmentValue > 0 else { return 0 }
        return (unrealizedPnL / investmentValue) * 100
    }

    var isExpired: Bool {
        Date() > expiryDate
    }

    var daysToExpiry: Int {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.day], from: Date(), to: expiryDate)
        return max(components.day ?? 0, 0)
    }

    var optionSymbol: String {
        "\(displayStrike) \(optionType.shortName)"
    }

    var displayStrike: String {
        String(format: "%.0f", strikePrice)
    }

    var displayEntryPrice: String {
        String(format: "%.2f", averageEntryPrice)
    }

    var displayCurrentLTP: String {
        String(format: "%.2f", currentLTP)
    }

    // Coding keys to exclude non-persisted properties
    enum CodingKeys: String, CodingKey {
        case id, index, strikePrice, optionType, direction
        case quantity, averageEntryPrice, entryDate, expiryDate, lotSize
        case stopLoss, target
    }
}

// MARK: - Closed Trade

struct ClosedTrade: Identifiable, Codable, Equatable {
    let id: UUID
    let index: String
    let strikePrice: Double
    let optionType: OptionType
    let direction: PaperTradeDirection
    let quantity: Int
    let lotSize: Int
    let entryPrice: Double
    let exitPrice: Double
    let entryDate: Date
    let exitDate: Date
    let expiryDate: Date

    init(
        id: UUID = UUID(),
        index: String,
        strikePrice: Double,
        optionType: OptionType,
        direction: PaperTradeDirection,
        quantity: Int,
        lotSize: Int,
        entryPrice: Double,
        exitPrice: Double,
        entryDate: Date,
        exitDate: Date = Date(),
        expiryDate: Date
    ) {
        self.id = id
        self.index = index
        self.strikePrice = strikePrice
        self.optionType = optionType
        self.direction = direction
        self.quantity = quantity
        self.lotSize = lotSize
        self.entryPrice = entryPrice
        self.exitPrice = exitPrice
        self.entryDate = entryDate
        self.exitDate = exitDate
        self.expiryDate = expiryDate
    }

    // Computed properties
    var totalQuantity: Int {
        quantity * lotSize
    }

    var realizedPnL: Double {
        (exitPrice - entryPrice) * Double(totalQuantity) * direction.multiplier
    }

    var realizedPnLPercent: Double {
        guard entryPrice > 0 else { return 0 }
        let entryValue = entryPrice * Double(totalQuantity)
        return (realizedPnL / entryValue) * 100
    }

    var isProfitable: Bool {
        realizedPnL > 0
    }

    var holdingDuration: TimeInterval {
        exitDate.timeIntervalSince(entryDate)
    }

    var holdingDays: Int {
        Int(holdingDuration / (24 * 60 * 60))
    }

    var optionSymbol: String {
        "\(displayStrike) \(optionType.shortName)"
    }

    var displayStrike: String {
        String(format: "%.0f", strikePrice)
    }
}

// MARK: - Daily P&L Snapshot

struct DailyPnLSnapshot: Identifiable, Codable, Equatable {
    let id: UUID
    let date: Date
    let portfolioValue: Double
    let realizedPnL: Double
    let unrealizedPnL: Double

    init(
        id: UUID = UUID(),
        date: Date = Date(),
        portfolioValue: Double,
        realizedPnL: Double,
        unrealizedPnL: Double
    ) {
        self.id = id
        self.date = date
        self.portfolioValue = portfolioValue
        self.realizedPnL = realizedPnL
        self.unrealizedPnL = unrealizedPnL
    }

    var totalPnL: Double {
        realizedPnL + unrealizedPnL
    }
}

// MARK: - Paper Portfolio (Root Container)

struct PaperPortfolio: Codable, Equatable {
    let startingBalance: Double
    var cashBalance: Double
    var openPositions: [PaperPosition]
    var closedTrades: [ClosedTrade]
    var tradeLog: [PaperTrade]
    var dailyPnLSnapshots: [DailyPnLSnapshot]
    var createdAt: Date
    var lastUpdatedAt: Date

    static let defaultStartingBalance: Double = 10_00_000 // ₹10 Lakh

    init(
        startingBalance: Double = PaperPortfolio.defaultStartingBalance,
        cashBalance: Double? = nil,
        openPositions: [PaperPosition] = [],
        closedTrades: [ClosedTrade] = [],
        tradeLog: [PaperTrade] = [],
        dailyPnLSnapshots: [DailyPnLSnapshot] = [],
        createdAt: Date = Date(),
        lastUpdatedAt: Date = Date()
    ) {
        self.startingBalance = startingBalance
        self.cashBalance = cashBalance ?? startingBalance
        self.openPositions = openPositions
        self.closedTrades = closedTrades
        self.tradeLog = tradeLog
        self.dailyPnLSnapshots = dailyPnLSnapshots
        self.createdAt = createdAt
        self.lastUpdatedAt = lastUpdatedAt
    }

    // Computed properties
    var totalUnrealizedPnL: Double {
        openPositions.reduce(0) { $0 + $1.unrealizedPnL }
    }

    var totalRealizedPnL: Double {
        closedTrades.reduce(0) { $0 + $1.realizedPnL }
    }

    var openPositionsValue: Double {
        openPositions.reduce(0) { $0 + $1.currentValue }
    }

    var portfolioValue: Double {
        cashBalance + openPositionsValue
    }

    var totalPnL: Double {
        portfolioValue - startingBalance
    }

    var totalPnLPercent: Double {
        guard startingBalance > 0 else { return 0 }
        return (totalPnL / startingBalance) * 100
    }

    var marginUsed: Double {
        // Simplified margin calculation for sell positions
        openPositions
            .filter { $0.direction == .sell }
            .reduce(0) { $0 + ($1.averageEntryPrice * Double($1.totalQuantity) * 2) }
    }

    var availableMargin: Double {
        cashBalance - marginUsed
    }
}

// MARK: - Performance Metrics (Computed, Not Persisted)

struct PerformanceMetrics {
    let totalTrades: Int
    let winningTrades: Int
    let losingTrades: Int
    let winRate: Double // 0-100
    let avgWinAmount: Double
    let avgLossAmount: Double
    let largestWin: Double
    let largestLoss: Double
    let maxDrawdown: Double
    let maxDrawdownPercent: Double
    let profitFactor: Double // gross profit / gross loss
    let avgHoldingDays: Double
    let bestTrade: ClosedTrade?
    let worstTrade: ClosedTrade?
    let totalRealizedPnL: Double
    let niftyPnL: Double
    let bankNiftyPnL: Double
    let otherIndexPnL: Double

    static var empty: PerformanceMetrics {
        PerformanceMetrics(
            totalTrades: 0,
            winningTrades: 0,
            losingTrades: 0,
            winRate: 0,
            avgWinAmount: 0,
            avgLossAmount: 0,
            largestWin: 0,
            largestLoss: 0,
            maxDrawdown: 0,
            maxDrawdownPercent: 0,
            profitFactor: 0,
            avgHoldingDays: 0,
            bestTrade: nil,
            worstTrade: nil,
            totalRealizedPnL: 0,
            niftyPnL: 0,
            bankNiftyPnL: 0,
            otherIndexPnL: 0
        )
    }
}

// MARK: - Trade Execution Request

struct TradeExecutionRequest {
    let index: TradingIndex
    let strikePrice: Double
    let optionType: OptionType
    let direction: PaperTradeDirection
    let price: Double
    var quantity: Int
    let expiryDate: Date
    let underlyingPrice: Double
    var stopLoss: Double?
    var target: Double?

    var totalValue: Double {
        price * Double(quantity) * Double(index.lotSize)
    }

    var marginRequired: Double {
        if direction == .sell {
            // Simplified margin: 2x premium for sell
            return totalValue * 2
        }
        return totalValue
    }

    // Expected loss if stop loss is hit
    var stopLossPnL: Double? {
        guard let sl = stopLoss else { return nil }
        let priceDiff = sl - price
        return priceDiff * Double(quantity) * Double(index.lotSize) * direction.multiplier
    }

    // Expected profit if target is hit
    var targetPnL: Double? {
        guard let tgt = target else { return nil }
        let priceDiff = tgt - price
        return priceDiff * Double(quantity) * Double(index.lotSize) * direction.multiplier
    }

    // Risk to reward ratio
    var riskRewardRatio: Double? {
        guard let slPnL = stopLossPnL, let tgtPnL = targetPnL,
              slPnL < 0, tgtPnL > 0 else { return nil }
        return tgtPnL / abs(slPnL)
    }
}

// MARK: - Paper Trading Error

enum PaperTradingError: LocalizedError {
    case insufficientFunds(required: Double, available: Double)
    case insufficientMargin(required: Double, available: Double)
    case invalidPrice
    case invalidQuantity
    case positionNotFound
    case positionExpired

    var errorDescription: String? {
        switch self {
        case .insufficientFunds(let required, let available):
            return "Insufficient funds. Required: \(formatCurrency(required)), Available: \(formatCurrency(available))"
        case .insufficientMargin(let required, let available):
            return "Insufficient margin. Required: \(formatCurrency(required)), Available: \(formatCurrency(available))"
        case .invalidPrice:
            return "Invalid price. Price must be greater than zero."
        case .invalidQuantity:
            return "Invalid quantity. Quantity must be at least 1 lot."
        case .positionNotFound:
            return "Position not found."
        case .positionExpired:
            return "Position has expired and cannot be modified."
        }
    }

    private func formatCurrency(_ value: Double) -> String {
        if value >= 10_00_00_000 {
            return String(format: "₹%.1fCr", value / 10_00_00_000)
        } else if value >= 1_00_000 {
            return String(format: "₹%.1fL", value / 1_00_000)
        } else if value >= 1_000 {
            return String(format: "₹%.1fK", value / 1_000)
        }
        return String(format: "₹%.0f", value)
    }
}

// MARK: - Trade History Filter

enum TradeHistoryFilter: String, CaseIterable {
    case all = "All"
    case profitable = "Profitable"
    case loss = "Loss"
    case nifty = "NIFTY"
    case bankNifty = "BANKNIFTY"

    var displayName: String {
        rawValue
    }
}

// MARK: - Paper Trading Tab

enum PaperTradingTab: String, CaseIterable {
    case dashboard = "Dashboard"
    case positions = "Positions"
    case history = "History"
    case performance = "Performance"

    var icon: String {
        switch self {
        case .dashboard: return "chart.pie.fill"
        case .positions: return "list.bullet.rectangle.fill"
        case .history: return "clock.fill"
        case .performance: return "chart.line.uptrend.xyaxis"
        }
    }
}
