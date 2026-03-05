import Foundation
import SwiftUI
import Combine

// MARK: - Paper Trading ViewModel

@MainActor
final class PaperTradingViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var portfolio: PaperPortfolio
    @Published var performanceMetrics: PerformanceMetrics?
    @Published var selectedTab: PaperTradingTab = .positions
    @Published var showTradeSheet: Bool = false
    @Published var tradeRequest: TradeExecutionRequest?
    @Published var tradeHistoryFilter: TradeHistoryFilter = .all
    @Published var errorMessage: String?
    @Published var showError: Bool = false
    @Published var showResetConfirmation: Bool = false
    @Published var lastTradeResult: PaperTrade?

    // MARK: - Private Properties

    private let engine = PaperTradingEngine.shared
    private let apiService = PaperTradingAPIService.shared
    private var cancellables = Set<AnyCancellable>()
    private var isSyncing = false

    // Pending trade for after auth
    private var pendingTradeRequest: (index: TradingIndex, option: OptionData, direction: PaperTradeDirection, expiryDate: Date)?

    // MARK: - Initialization

    init() {
        self.portfolio = engine.loadPortfolio()
        calculateMetrics()

        // Listen for login events to sync data
        setupAuthObserver()
    }

    private func setupAuthObserver() {
        // Observe login state changes
        AuthManager.shared.$isLoggedIn
            .dropFirst()
            .sink { [weak self] isLoggedIn in
                if isLoggedIn {
                    Task {
                        await self?.syncWithServer()
                    }
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Server Sync

    /// Sync portfolio data with server (called after login)
    func syncWithServer() async {
        guard AuthManager.shared.isLoggedIn, !isSyncing else { return }

        isSyncing = true
        defer { isSyncing = false }

        do {
            // Fetch positions from server
            let serverPositions = try await apiService.fetchOpenPositions()
            let serverTrades = try await apiService.fetchTrades(limit: 100)
            let serverPortfolio = try await apiService.fetchPortfolio()

            // Convert server positions to local format
            let localPositions = serverPositions.map { convertServerPosition($0) }

            // Convert server trades to local closed trades (exit trades only)
            let closedTrades = serverTrades
                .filter { $0.tradeType == "exit" }
                .map { convertServerTradeToClosedTrade($0) }

            // Update local portfolio
            portfolio.openPositions = localPositions
            portfolio.closedTrades = closedTrades
            portfolio.cashBalance = serverPortfolio.cashBalance

            // Save locally for offline access
            engine.savePortfolio(portfolio)
            calculateMetrics()

            print("📡 [PaperTrading] Synced with server: \(localPositions.count) positions, \(closedTrades.count) trades")
        } catch {
            print("❌ [PaperTrading] Sync failed: \(error.localizedDescription)")
            // Keep using local data on sync failure
        }
    }

    private func convertServerPosition(_ server: ServerPosition) -> PaperPosition {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd-MMM-yyyy"
        let expiryDate = dateFormatter.date(from: server.expiryDate) ?? Date()

        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let openedAt = isoFormatter.date(from: server.openedAt) ?? Date()

        return PaperPosition(
            index: server.symbol,
            strikePrice: server.strikePrice,
            optionType: server.optionType == "CE" ? .call : .put,
            direction: server.direction == "buy" ? .buy : .sell,
            quantity: server.quantity,
            averageEntryPrice: server.entryPrice,
            entryDate: openedAt,
            expiryDate: expiryDate,
            lotSize: server.lotSize,
            currentLTP: server.currentPrice ?? server.entryPrice,
            serverId: server.id
        )
    }

    private func convertServerTradeToClosedTrade(_ server: ServerTrade) -> ClosedTrade {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd-MMM-yyyy"
        let expiryDate = dateFormatter.date(from: server.expiryDate) ?? Date()

        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let executedAt = isoFormatter.date(from: server.executedAt) ?? Date()

        return ClosedTrade(
            index: server.symbol,
            strikePrice: server.strikePrice,
            optionType: server.optionType == "CE" ? .call : .put,
            direction: server.direction == "buy" ? .sell : .buy, // Exit direction is opposite
            quantity: server.quantity,
            lotSize: server.lotSize,
            entryPrice: server.price,
            exitPrice: server.price,
            entryDate: executedAt,
            exitDate: executedAt,
            expiryDate: expiryDate
        )
    }

    // MARK: - Trade Initiation

    /// Initiates a paper trade. Requires user authentication.
    /// If user is not logged in, shows login sheet and will continue trade after successful login.
    func initiateTrade(
        index: TradingIndex,
        option: OptionData,
        direction: PaperTradeDirection,
        expiryDate: Date
    ) {
        let authManager = AuthManager.shared

        // Check if user is authenticated
        guard authManager.requireAuth(action: { [weak self] in
            // This will be called after successful login
            self?.executeTradeInitiation(
                index: index,
                option: option,
                direction: direction,
                expiryDate: expiryDate
            )
        }) else {
            // User not authenticated, login sheet will be shown
            // Store pending trade info for potential retry
            pendingTradeRequest = (index, option, direction, expiryDate)
            return
        }

        // User is authenticated, proceed with trade
        executeTradeInitiation(
            index: index,
            option: option,
            direction: direction,
            expiryDate: expiryDate
        )
    }

    /// Internal method to actually create the trade request and show the sheet
    private func executeTradeInitiation(
        index: TradingIndex,
        option: OptionData,
        direction: PaperTradeDirection,
        expiryDate: Date
    ) {
        let request = TradeExecutionRequest(
            index: index,
            strikePrice: option.strikePrice,
            optionType: option.optionType,
            direction: direction,
            price: option.lastTradedPrice,
            quantity: 1,
            expiryDate: expiryDate,
            underlyingPrice: option.underlyingValue
        )
        self.tradeRequest = request
        self.showTradeSheet = true
        // Clear pending request
        self.pendingTradeRequest = nil
    }

    func initiateTradeFromPosition(position: PaperPosition, direction: PaperTradeDirection, currentLTP: Double) {
        guard let index = TradingIndex(rawValue: position.index) else { return }

        let request = TradeExecutionRequest(
            index: index,
            strikePrice: position.strikePrice,
            optionType: position.optionType,
            direction: direction,
            price: currentLTP,
            quantity: 1,
            expiryDate: position.expiryDate,
            underlyingPrice: 0
        )
        self.tradeRequest = request
        self.showTradeSheet = true
    }

    // MARK: - Trade Execution

    func executeTrade(_ request: TradeExecutionRequest) {
        // Execute locally first for immediate feedback
        let result = engine.executeTrade(portfolio: &portfolio, request: request)

        switch result {
        case .success(let trade):
            lastTradeResult = trade
            calculateMetrics()
            showTradeSheet = false
            tradeRequest = nil

            // Haptic feedback
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(.success)

            // Sync to server in background
            Task {
                await saveTradeToServer(request: request, localTrade: trade)
            }

        case .failure(let error):
            errorMessage = error.localizedDescription
            showError = true

            // Haptic feedback
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(.error)
        }
    }

    /// Save trade to server for cross-device sync
    private func saveTradeToServer(request: TradeExecutionRequest, localTrade: PaperTrade) async {
        guard AuthManager.shared.isLoggedIn else { return }

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd-MMM-yyyy"
        let expiryString = dateFormatter.string(from: request.expiryDate)

        let createRequest = CreateTradeRequest(
            symbol: request.index.rawValue,
            strikePrice: request.strikePrice,
            optionType: request.optionType == .call ? "CE" : "PE",
            direction: request.direction == .buy ? "buy" : "sell",
            expiryDate: expiryString,
            quantity: request.quantity,
            lotSize: request.index.lotSize,
            price: request.price
        )

        do {
            let serverPosition = try await apiService.createTrade(request: createRequest)
            print("✅ [PaperTrading] Trade saved to server: \(serverPosition.id)")

            // Update local position with server ID for future sync
            // Match by strike price, option type, and entry date since we don't have direct ID mapping
            if let index = portfolio.openPositions.firstIndex(where: {
                $0.strikePrice == request.strikePrice &&
                $0.optionType == request.optionType &&
                $0.serverId == nil
            }) {
                portfolio.openPositions[index].serverId = serverPosition.id
                engine.savePortfolio(portfolio)
            }
        } catch {
            print("❌ [PaperTrading] Failed to save trade to server: \(error.localizedDescription)")
            // Trade is saved locally, will sync on next login
        }
    }

    // MARK: - Square Off

    func squareOff(position: PaperPosition, currentLTP: Double, quantity: Int? = nil) {
        let result = engine.squareOff(
            portfolio: &portfolio,
            position: position,
            currentLTP: currentLTP,
            quantity: quantity
        )

        switch result {
        case .success:
            calculateMetrics()

            // Haptic feedback
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(.success)

            // Sync to server in background
            Task {
                await squareOffOnServer(position: position, exitPrice: currentLTP)
            }

        case .failure(let error):
            errorMessage = error.localizedDescription
            showError = true

            // Haptic feedback
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(.error)
        }
    }

    private func squareOffOnServer(position: PaperPosition, exitPrice: Double) async {
        guard AuthManager.shared.isLoggedIn else { return }

        // Use server ID if available
        guard let serverId = position.serverId else {
            print("⚠️ [PaperTrading] No server ID for position, cannot sync square off")
            return
        }

        do {
            let trade = try await apiService.squareOff(positionId: serverId, exitPrice: exitPrice)
            print("✅ [PaperTrading] Position squared off on server: \(trade.id)")
        } catch {
            print("❌ [PaperTrading] Failed to square off on server: \(error.localizedDescription)")
            // Will sync on next login
        }
    }

    // MARK: - Position Updates

    func updatePositionPrices(from optionChain: [OptionChainRow], spotPrice: Double) {
        var updated = false

        for (index, position) in portfolio.openPositions.enumerated() {
            // Find matching option in chain
            if let row = optionChain.first(where: { $0.strikePrice == position.strikePrice }) {
                let option = position.optionType == .call ? row.callOption : row.putOption
                if let ltp = option?.lastTradedPrice, ltp > 0 {
                    portfolio.openPositions[index].currentLTP = ltp
                    updated = true
                }
            }
        }

        if updated {
            // Take daily snapshot
            engine.takeDailySnapshot(portfolio: &portfolio)
            objectWillChange.send()
        }
    }

    // MARK: - Portfolio Management

    func resetPortfolio() {
        portfolio = engine.resetPortfolio()
        performanceMetrics = nil
        showResetConfirmation = false

        // Haptic feedback
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)

        // Reset on server in background
        Task {
            await resetPortfolioOnServer()
        }
    }

    private func resetPortfolioOnServer() async {
        guard AuthManager.shared.isLoggedIn else { return }

        do {
            try await apiService.resetPortfolio()
            print("✅ [PaperTrading] Portfolio reset on server")
        } catch {
            print("❌ [PaperTrading] Failed to reset portfolio on server: \(error.localizedDescription)")
        }
    }

    func handleExpiredPositions() {
        engine.handleExpiredPositions(portfolio: &portfolio)
        calculateMetrics()
    }

    // MARK: - Analytics

    func calculateMetrics() {
        performanceMetrics = engine.calculatePerformanceMetrics(from: portfolio)
    }

    func getFilteredTrades() -> [ClosedTrade] {
        engine.getFilteredTrades(from: portfolio, filter: tradeHistoryFilter)
    }

    // MARK: - Formatting Helpers

    func formatCurrency(_ value: Double) -> String {
        if abs(value) >= 10_00_00_000 {
            return String(format: "₹%.1fCr", value / 10_00_00_000)
        } else if abs(value) >= 1_00_000 {
            return String(format: "₹%.1fL", value / 1_00_000)
        } else if abs(value) >= 1_000 {
            return String(format: "₹%.1fK", value / 1_000)
        }
        return String(format: "₹%.0f", value)
    }

    func formatPnL(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        return "\(prefix)\(formatCurrency(value))"
    }

    func formatPercent(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        return String(format: "%@%.1f%%", prefix, value)
    }

    func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM, HH:mm"
        return formatter.string(from: date)
    }

    func formatShortDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM"
        return formatter.string(from: date)
    }

    // MARK: - Computed Properties

    var hasOpenPositions: Bool {
        !portfolio.openPositions.isEmpty
    }

    var hasTradeHistory: Bool {
        !portfolio.closedTrades.isEmpty
    }

    var portfolioSummary: (value: String, pnl: String, pnlPercent: String, isProfit: Bool) {
        let value = formatCurrency(portfolio.portfolioValue)
        let pnl = formatPnL(portfolio.totalPnL)
        let pnlPercent = formatPercent(portfolio.totalPnLPercent)
        let isProfit = portfolio.totalPnL >= 0
        return (value, pnl, pnlPercent, isProfit)
    }

    var openPositionsCount: Int {
        portfolio.openPositions.count
    }

    var availableBalanceFormatted: String {
        formatCurrency(portfolio.cashBalance)
    }

    var availableMarginFormatted: String {
        formatCurrency(portfolio.availableMargin)
    }

    // MARK: - Validation

    func canExecuteTrade(_ request: TradeExecutionRequest) -> (canExecute: Bool, reason: String?) {
        if request.price <= 0 {
            return (false, "Invalid price")
        }

        if request.quantity <= 0 {
            return (false, "Invalid quantity")
        }

        let totalValue = request.totalValue

        if request.direction == .buy {
            if portfolio.cashBalance < totalValue {
                return (false, "Insufficient funds")
            }
        } else {
            let marginRequired = request.marginRequired
            if portfolio.availableMargin < marginRequired {
                return (false, "Insufficient margin")
            }
        }

        return (true, nil)
    }

    // MARK: - Request Updates

    func updateTradeQuantity(_ quantity: Int) {
        guard var request = tradeRequest else { return }
        request.quantity = max(1, quantity)
        tradeRequest = request
    }

    func updateStopLoss(_ value: Double?) {
        guard var request = tradeRequest else { return }
        request.stopLoss = value
        tradeRequest = request
    }

    func updateTarget(_ value: Double?) {
        guard var request = tradeRequest else { return }
        request.target = value
        tradeRequest = request
    }
}
