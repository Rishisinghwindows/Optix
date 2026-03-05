import Foundation

// MARK: - Paper Trading Engine

final class PaperTradingEngine {

    // MARK: - Singleton

    static let shared = PaperTradingEngine()

    private init() {}

    // MARK: - File Path

    private var portfolioFileURL: URL {
        guard let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
            // Fallback to temp directory (should never happen on iOS)
            return FileManager.default.temporaryDirectory.appendingPathComponent("paper_portfolio.json")
        }
        return documentsDirectory.appendingPathComponent("paper_portfolio.json")
    }

    // MARK: - Persistence

    func loadPortfolio() -> PaperPortfolio {
        guard FileManager.default.fileExists(atPath: portfolioFileURL.path) else {
            return PaperPortfolio()
        }

        do {
            let data = try Data(contentsOf: portfolioFileURL)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let portfolio = try decoder.decode(PaperPortfolio.self, from: data)
            return portfolio
        } catch {
            print("[PaperTradingEngine] Failed to load portfolio: \(error)")
            return PaperPortfolio()
        }
    }

    func savePortfolio(_ portfolio: PaperPortfolio) {
        var updatedPortfolio = portfolio
        updatedPortfolio.lastUpdatedAt = Date()

        do {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            encoder.outputFormatting = .prettyPrinted
            let data = try encoder.encode(updatedPortfolio)
            try data.write(to: portfolioFileURL, options: .atomicWrite)
        } catch {
            print("[PaperTradingEngine] Failed to save portfolio: \(error)")
        }
    }

    // MARK: - Trade Execution

    func executeTrade(
        portfolio: inout PaperPortfolio,
        request: TradeExecutionRequest
    ) -> Result<PaperTrade, PaperTradingError> {

        // Validate price
        guard request.price > 0 else {
            return .failure(.invalidPrice)
        }

        // Validate quantity
        guard request.quantity > 0 else {
            return .failure(.invalidQuantity)
        }

        let totalValue = request.totalValue

        // Check funds based on direction
        if request.direction == .buy {
            guard portfolio.cashBalance >= totalValue else {
                return .failure(.insufficientFunds(required: totalValue, available: portfolio.cashBalance))
            }
        } else {
            // For sell, check margin
            let marginRequired = request.marginRequired
            guard portfolio.availableMargin >= marginRequired else {
                return .failure(.insufficientMargin(required: marginRequired, available: portfolio.availableMargin))
            }
        }

        // Create trade log entry
        let trade = PaperTrade(
            index: request.index.rawValue,
            strikePrice: request.strikePrice,
            optionType: request.optionType,
            direction: request.direction,
            price: request.price,
            quantity: request.quantity,
            lotSize: request.index.lotSize,
            expiryDate: request.expiryDate,
            underlyingPrice: request.underlyingPrice
        )

        // Update cash balance
        if request.direction == .buy {
            portfolio.cashBalance -= totalValue
        } else {
            // For sell, reserve margin but don't deduct cash yet
            // Premium is received but margin is blocked
            portfolio.cashBalance += totalValue // Premium received
        }

        // Update or create position
        if let existingIndex = portfolio.openPositions.firstIndex(where: {
            $0.index == request.index.rawValue &&
            $0.strikePrice == request.strikePrice &&
            $0.optionType == request.optionType &&
            $0.direction == request.direction &&
            $0.expiryDate == request.expiryDate
        }) {
            // Add to existing position
            var position = portfolio.openPositions[existingIndex]
            let totalOldQty = position.quantity
            let totalNewQty = request.quantity
            let newAvgPrice = ((position.averageEntryPrice * Double(totalOldQty)) +
                              (request.price * Double(totalNewQty))) /
                              Double(totalOldQty + totalNewQty)

            position.quantity = totalOldQty + totalNewQty
            position.averageEntryPrice = newAvgPrice
            portfolio.openPositions[existingIndex] = position
        } else {
            // Create new position
            let position = PaperPosition(
                index: request.index.rawValue,
                strikePrice: request.strikePrice,
                optionType: request.optionType,
                direction: request.direction,
                quantity: request.quantity,
                averageEntryPrice: request.price,
                expiryDate: request.expiryDate,
                lotSize: request.index.lotSize,
                stopLoss: request.stopLoss,
                target: request.target,
                currentLTP: request.price
            )
            portfolio.openPositions.append(position)
        }

        // Add to trade log
        portfolio.tradeLog.append(trade)

        // Save portfolio
        savePortfolio(portfolio)

        return .success(trade)
    }

    // MARK: - Square Off Position

    func squareOff(
        portfolio: inout PaperPortfolio,
        position: PaperPosition,
        currentLTP: Double,
        quantity: Int? = nil
    ) -> Result<ClosedTrade, PaperTradingError> {

        // Validate price
        guard currentLTP > 0 else {
            return .failure(.invalidPrice)
        }

        // Find position
        guard let positionIndex = portfolio.openPositions.firstIndex(where: { $0.id == position.id }) else {
            return .failure(.positionNotFound)
        }

        let existingPosition = portfolio.openPositions[positionIndex]
        let quantityToClose = quantity ?? existingPosition.quantity

        // Validate quantity
        guard quantityToClose > 0 && quantityToClose <= existingPosition.quantity else {
            return .failure(.invalidQuantity)
        }

        // Create closed trade
        let closedTrade = ClosedTrade(
            index: existingPosition.index,
            strikePrice: existingPosition.strikePrice,
            optionType: existingPosition.optionType,
            direction: existingPosition.direction,
            quantity: quantityToClose,
            lotSize: existingPosition.lotSize,
            entryPrice: existingPosition.averageEntryPrice,
            exitPrice: currentLTP,
            entryDate: existingPosition.entryDate,
            expiryDate: existingPosition.expiryDate
        )

        // Calculate P&L and update cash balance
        let pnl = closedTrade.realizedPnL

        if existingPosition.direction == .buy {
            // Return exit value to cash
            let exitValue = currentLTP * Double(quantityToClose) * Double(existingPosition.lotSize)
            portfolio.cashBalance += exitValue
        } else {
            // For short positions, settle the difference
            let entryValue = existingPosition.averageEntryPrice * Double(quantityToClose) * Double(existingPosition.lotSize)
            let exitValue = currentLTP * Double(quantityToClose) * Double(existingPosition.lotSize)
            // Premium was already received, now pay/receive the difference
            portfolio.cashBalance -= exitValue // Pay to close
            // Net P&L is: premium received - cost to close = entryValue - exitValue
            // But we already have entryValue in cash, so just deduct exitValue
        }

        // Update or remove position
        if quantityToClose == existingPosition.quantity {
            portfolio.openPositions.remove(at: positionIndex)
        } else {
            portfolio.openPositions[positionIndex].quantity -= quantityToClose
        }

        // Add to closed trades
        portfolio.closedTrades.append(closedTrade)

        // Create reverse trade log entry
        let reverseDirection: PaperTradeDirection = existingPosition.direction == .buy ? .sell : .buy
        let exitTrade = PaperTrade(
            index: existingPosition.index,
            strikePrice: existingPosition.strikePrice,
            optionType: existingPosition.optionType,
            direction: reverseDirection,
            price: currentLTP,
            quantity: quantityToClose,
            lotSize: existingPosition.lotSize,
            expiryDate: existingPosition.expiryDate,
            underlyingPrice: 0 // Not relevant for exit
        )
        portfolio.tradeLog.append(exitTrade)

        // Save portfolio
        savePortfolio(portfolio)

        return .success(closedTrade)
    }

    // MARK: - Performance Metrics

    func calculatePerformanceMetrics(from portfolio: PaperPortfolio) -> PerformanceMetrics {
        let closedTrades = portfolio.closedTrades

        guard !closedTrades.isEmpty else {
            return .empty
        }

        let totalTrades = closedTrades.count
        let winningTrades = closedTrades.filter { $0.isProfitable }
        let losingTrades = closedTrades.filter { !$0.isProfitable && $0.realizedPnL != 0 }

        let winRate = totalTrades > 0 ? (Double(winningTrades.count) / Double(totalTrades)) * 100 : 0

        let totalWins = winningTrades.reduce(0.0) { $0 + $1.realizedPnL }
        let totalLosses = abs(losingTrades.reduce(0.0) { $0 + $1.realizedPnL })

        let avgWinAmount = winningTrades.isEmpty ? 0 : totalWins / Double(winningTrades.count)
        let avgLossAmount = losingTrades.isEmpty ? 0 : totalLosses / Double(losingTrades.count)

        let largestWin = winningTrades.max(by: { $0.realizedPnL < $1.realizedPnL })?.realizedPnL ?? 0
        let largestLoss = abs(losingTrades.min(by: { $0.realizedPnL < $1.realizedPnL })?.realizedPnL ?? 0)

        let bestTrade = winningTrades.max(by: { $0.realizedPnL < $1.realizedPnL })
        let worstTrade = losingTrades.min(by: { $0.realizedPnL < $1.realizedPnL })

        let profitFactor = totalLosses > 0 ? totalWins / totalLosses : (totalWins > 0 ? .infinity : 0)

        let avgHoldingDays = closedTrades.isEmpty ? 0 :
            closedTrades.reduce(0.0) { $0 + Double($1.holdingDays) } / Double(closedTrades.count)

        // Calculate max drawdown from snapshots
        let (maxDrawdown, maxDrawdownPercent) = calculateMaxDrawdown(from: portfolio)

        // Calculate P&L by index
        let niftyPnL = closedTrades.filter { $0.index == "NIFTY" }.reduce(0.0) { $0 + $1.realizedPnL }
        let bankNiftyPnL = closedTrades.filter { $0.index == "BANKNIFTY" }.reduce(0.0) { $0 + $1.realizedPnL }
        let otherIndexPnL = portfolio.totalRealizedPnL - niftyPnL - bankNiftyPnL

        return PerformanceMetrics(
            totalTrades: totalTrades,
            winningTrades: winningTrades.count,
            losingTrades: losingTrades.count,
            winRate: winRate,
            avgWinAmount: avgWinAmount,
            avgLossAmount: avgLossAmount,
            largestWin: largestWin,
            largestLoss: largestLoss,
            maxDrawdown: maxDrawdown,
            maxDrawdownPercent: maxDrawdownPercent,
            profitFactor: profitFactor,
            avgHoldingDays: avgHoldingDays,
            bestTrade: bestTrade,
            worstTrade: worstTrade,
            totalRealizedPnL: portfolio.totalRealizedPnL,
            niftyPnL: niftyPnL,
            bankNiftyPnL: bankNiftyPnL,
            otherIndexPnL: otherIndexPnL
        )
    }

    private func calculateMaxDrawdown(from portfolio: PaperPortfolio) -> (Double, Double) {
        let snapshots = portfolio.dailyPnLSnapshots

        guard !snapshots.isEmpty else {
            // Calculate from closed trades if no snapshots
            var runningPnL = 0.0
            var peak = 0.0
            var maxDrawdown = 0.0

            for trade in portfolio.closedTrades.sorted(by: { $0.exitDate < $1.exitDate }) {
                runningPnL += trade.realizedPnL
                if runningPnL > peak {
                    peak = runningPnL
                }
                let drawdown = peak - runningPnL
                if drawdown > maxDrawdown {
                    maxDrawdown = drawdown
                }
            }

            let maxDrawdownPercent = portfolio.startingBalance > 0 ?
                (maxDrawdown / portfolio.startingBalance) * 100 : 0

            return (maxDrawdown, maxDrawdownPercent)
        }

        var peak = portfolio.startingBalance
        var maxDrawdown = 0.0

        for snapshot in snapshots.sorted(by: { $0.date < $1.date }) {
            if snapshot.portfolioValue > peak {
                peak = snapshot.portfolioValue
            }
            let drawdown = peak - snapshot.portfolioValue
            if drawdown > maxDrawdown {
                maxDrawdown = drawdown
            }
        }

        let maxDrawdownPercent = peak > 0 ? (maxDrawdown / peak) * 100 : 0
        return (maxDrawdown, maxDrawdownPercent)
    }

    // MARK: - Daily Snapshot

    func takeDailySnapshot(portfolio: inout PaperPortfolio) {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())

        // Check if we already have a snapshot for today
        if let lastSnapshot = portfolio.dailyPnLSnapshots.last {
            let lastSnapshotDay = calendar.startOfDay(for: lastSnapshot.date)
            if lastSnapshotDay == today {
                // Update today's snapshot
                if let index = portfolio.dailyPnLSnapshots.firstIndex(where: { calendar.startOfDay(for: $0.date) == today }) {
                    portfolio.dailyPnLSnapshots[index] = DailyPnLSnapshot(
                        date: Date(),
                        portfolioValue: portfolio.portfolioValue,
                        realizedPnL: portfolio.totalRealizedPnL,
                        unrealizedPnL: portfolio.totalUnrealizedPnL
                    )
                }
                return
            }
        }

        // Create new snapshot
        let snapshot = DailyPnLSnapshot(
            date: Date(),
            portfolioValue: portfolio.portfolioValue,
            realizedPnL: portfolio.totalRealizedPnL,
            unrealizedPnL: portfolio.totalUnrealizedPnL
        )
        portfolio.dailyPnLSnapshots.append(snapshot)

        // Keep only last 90 days
        if portfolio.dailyPnLSnapshots.count > 90 {
            portfolio.dailyPnLSnapshots.removeFirst(portfolio.dailyPnLSnapshots.count - 90)
        }

        savePortfolio(portfolio)
    }

    // MARK: - Portfolio Management

    func resetPortfolio(startingBalance: Double = PaperPortfolio.defaultStartingBalance) -> PaperPortfolio {
        let newPortfolio = PaperPortfolio(startingBalance: startingBalance)
        savePortfolio(newPortfolio)
        return newPortfolio
    }

    func handleExpiredPositions(portfolio: inout PaperPortfolio) {
        let now = Date()
        var expiredPositions: [PaperPosition] = []

        for position in portfolio.openPositions {
            if position.expiryDate < now {
                expiredPositions.append(position)
            }
        }

        for position in expiredPositions {
            // Expire at intrinsic value (simplified - assume 0 for OTM)
            let intrinsicValue = max(position.currentLTP, 0.05) // Minimum 0.05 for settlement

            _ = squareOff(
                portfolio: &portfolio,
                position: position,
                currentLTP: intrinsicValue
            )
        }
    }

    // MARK: - Utility

    func getFilteredTrades(
        from portfolio: PaperPortfolio,
        filter: TradeHistoryFilter
    ) -> [ClosedTrade] {
        switch filter {
        case .all:
            return portfolio.closedTrades.sorted { $0.exitDate > $1.exitDate }
        case .profitable:
            return portfolio.closedTrades
                .filter { $0.isProfitable }
                .sorted { $0.exitDate > $1.exitDate }
        case .loss:
            return portfolio.closedTrades
                .filter { !$0.isProfitable && $0.realizedPnL != 0 }
                .sorted { $0.exitDate > $1.exitDate }
        case .nifty:
            return portfolio.closedTrades
                .filter { $0.index == "NIFTY" }
                .sorted { $0.exitDate > $1.exitDate }
        case .bankNifty:
            return portfolio.closedTrades
                .filter { $0.index == "BANKNIFTY" }
                .sorted { $0.exitDate > $1.exitDate }
        }
    }
}
