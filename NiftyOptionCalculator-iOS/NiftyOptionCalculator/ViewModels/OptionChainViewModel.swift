import Foundation
import SwiftUI
import Combine

// MARK: - Loading State

enum LoadingState {
    case idle
    case loading
    case loaded
    case error(String)
}

// MARK: - Data Source

enum DataSource {
    case mock
    case upstox
    case guest

    var displayName: String {
        switch self {
        case .mock: return L.upstoxDemoData
        case .upstox: return L.upstoxLive
        case .guest: return "Guest (Live)"
        }
    }

    var isLive: Bool {
        switch self {
        case .mock: return false
        case .upstox, .guest: return true
        }
    }
}

// MARK: - Option Chain ViewModel

@MainActor
final class OptionChainViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var optionChain: [OptionChainRow] = []
    @Published var expiryDates: [ExpiryDate] = []
    @Published var selectedExpiry: ExpiryDate?
    @Published var spotPrice: Double = 0
    @Published var spotChange: Double = 0
    @Published var spotChangePercent: Double = 0
    @Published var previousClose: Double = 0
    @Published var loadingState: LoadingState = .idle
    @Published var searchText: String = ""
    @Published var selectedOption: OptionData?
    @Published var showingCalculator: Bool = false
    @Published var dataSource: DataSource = .guest
    @Published var lastUpdated: Date?
    @Published var dataVersion: Int = 0

    // Full-chain OI totals from backend (for accurate PCR)
    @Published var fullChainCallOI: Int?
    @Published var fullChainPutOI: Int?

    // Index Selection
    @Published var selectedIndex: TradingIndex = .nifty50
    @Published var showIndexPicker: Bool = false

    // Upstox integration
    @Published var isUpstoxAuthenticated: Bool = false
    @Published var showLoginSheet: Bool = false

    // WebSocket status
    @Published var isWebSocketConnected: Bool = false
    @Published var webSocketStatus: String = "Disconnected"

    // VIX (populated from data refresh or AI analysis flow)
    @Published var indiaVix: Double?

    // MARK: - Private Properties

    private var cancellables = Set<AnyCancellable>()
    private var instrumentKeyIndex: [String: (rowIndex: Int, isCall: Bool)] = [:]
    private var refreshTimer: Timer?
    private var spotPriceTimer: Timer?
    private var autoRetryTimer: Timer?
    private var guestRefreshTimer: Timer?

    // MARK: - Helpers

    /// Corrects previousClose when backend returns lastPrice instead of actual previous close.
    /// Computes correct value from spotPrice - spotChange when they don't match.
    private func updatePreviousClose(_ reportedPreviousClose: Double) {
        if spotChange != 0 && abs(reportedPreviousClose - spotPrice) < 1.0 {
            // Backend returned lastPrice as previousClose — compute correct value
            previousClose = spotPrice - spotChange
        } else {
            previousClose = reportedPreviousClose
        }
    }

    // MARK: - Computed Properties

    var filteredOptionChain: [OptionChainRow] {
        guard !searchText.isEmpty else { return optionChain }

        let searchNumber = Double(searchText) ?? 0
        if searchNumber > 0 {
            return optionChain.filter { row in
                row.strikePrice == searchNumber ||
                String(format: "%.0f", row.strikePrice).contains(searchText)
            }
        }
        return optionChain
    }

    var atmStrike: Double? {
        guard spotPrice > 0 else { return nil }
        let strikesInterval = selectedIndex.strikeInterval
        return (spotPrice / strikesInterval).rounded() * strikesInterval
    }

    var nearbyStrikes: [OptionChainRow] {
        guard let atm = atmStrike else { return optionChain }
        // Show strikes within 20 intervals of ATM (1000 points for NIFTY)
        let range = selectedIndex.strikeInterval * 20
        return optionChain.filter { row in
            abs(row.strikePrice - atm) <= range
        }
    }

    
    var totalCallOI: Int {
        // Use full-chain totals from backend if available (accurate PCR across ALL strikes)
        if let fullChain = fullChainCallOI, fullChain > 0 {
            return fullChain
        }
        return optionChain.compactMap { $0.callOption?.openInterest }.reduce(0, +)
    }

    var totalPutOI: Int {
        // Use full-chain totals from backend if available (accurate PCR across ALL strikes)
        if let fullChain = fullChainPutOI, fullChain > 0 {
            return fullChain
        }
        return optionChain.compactMap { $0.putOption?.openInterest }.reduce(0, +)
    }

    var putCallRatio: Double {
        guard totalCallOI > 0 else { return 0 }
        return Double(totalPutOI) / Double(totalCallOI)
    }

    var maxPainStrike: Double? {
        calculateMaxPain()
    }

    var maxOI: Int {
        let callOIs = optionChain.compactMap { $0.callOption?.openInterest }
        let putOIs = optionChain.compactMap { $0.putOption?.openInterest }
        return max(callOIs.max() ?? 0, putOIs.max() ?? 0)
    }

    var atmIV: Double? {
        guard let atm = atmStrike else { return nil }
        let atmRow = optionChain.first { abs($0.strikePrice - atm) <= 25 }
        return atmRow?.callOption?.impliedVolatility ?? atmRow?.putOption?.impliedVolatility
    }

    // MARK: - Initialization

    init() {
        // Load default index from UserDefaults
        if let savedIndex = UserDefaults.standard.string(forKey: "defaultIndex"),
           let index = TradingIndex(rawValue: savedIndex) {
            selectedIndex = index
        }

        // Check Upstox authentication status
        isUpstoxAuthenticated = UpstoxAPIService.shared.isAuthenticated

        // Setup WebSocket observers
        setupWebSocketObservers()

        // Check saved data provider preference
        let savedProviderRaw = UserDefaults.standard.string(forKey: "selectedProvider") ?? "none"
        print("🔧 [Init] Saved provider from UserDefaults: \(savedProviderRaw)")

        let savedProvider = ProviderManager.shared.currentProviderType
        print("🔧 [Init] ProviderManager currentProviderType: \(savedProvider)")

        switch savedProvider {
        case .guest:
            print("🔧 [Init] Starting with GUEST mode")
            dataSource = .guest
            Task {
                await loadGuestData()
            }
        case .upstox:
            if isUpstoxAuthenticated {
                dataSource = .upstox
                Task {
                    await loadData()
                }
            } else {
                dataSource = .guest
                loadingState = .error(L.optionChainNoData)
                Task {
                    await loadGuestData()
                }
            }
        default:
            dataSource = .guest
            Task {
                await loadGuestData()
            }
        }
    }

    // MARK: - WebSocket Integration

    private func setupWebSocketObservers() {
        // Observe Upstox WebSocket connection status
        UpstoxWebSocketService.shared.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] connected in
                self?.isWebSocketConnected = connected
            }
            .store(in: &cancellables)

        // Observe WebSocket status string
        UpstoxWebSocketService.shared.$connectionStatus
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.webSocketStatus = status
            }
            .store(in: &cancellables)

        // Subscribe to live index price updates
        UpstoxWebSocketService.shared.priceUpdatePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] update in
                self?.handleLivePriceUpdate(update)
            }
            .store(in: &cancellables)

        // Subscribe to live option price updates
        UpstoxWebSocketService.shared.optionUpdatePublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] update in
                self?.handleOptionPriceUpdate(update)
            }
            .store(in: &cancellables)

        // Setup Guest Mode WebSocket observers
        setupGuestWebSocketObservers()
    }

    private func setupGuestWebSocketObservers() {
        // Observe Guest WebSocket connection status
        GuestWebSocketService.shared.$isConnected
            .receive(on: DispatchQueue.main)
            .sink { [weak self] connected in
                if self?.dataSource == .guest {
                    self?.isWebSocketConnected = connected
                    self?.webSocketStatus = connected ? "Live" : "Connecting..."
                }
            }
            .store(in: &cancellables)

        // Listen for Guest ticker updates (fast 500ms updates - LTP only)
        NotificationCenter.default.publisher(for: .guestTickerUpdated)
            .receive(on: DispatchQueue.main)
            .compactMap { $0.object as? GuestWebSocketService.TickerUpdate }
            .sink { [weak self] update in
                self?.handleGuestTickerUpdate(update)
            }
            .store(in: &cancellables)

        // Listen for Guest spot price updates (1 second - full data)
        NotificationCenter.default.publisher(for: .guestSpotPriceUpdated)
            .receive(on: DispatchQueue.main)
            .compactMap { $0.object as? GuestWebSocketService.SpotUpdate }
            .sink { [weak self] update in
                self?.handleGuestSpotUpdate(update)
            }
            .store(in: &cancellables)

        // Listen for Guest option chain updates (5 seconds - full chain)
        NotificationCenter.default.publisher(for: .guestOptionChainUpdated)
            .receive(on: DispatchQueue.main)
            .compactMap { $0.object as? GuestWebSocketService.OptionChainUpdate }
            .sink { [weak self] update in
                self?.handleGuestOptionChainUpdate(update)
            }
            .store(in: &cancellables)
    }

    /// Handle fast ticker updates (500ms) - LTP only
    private func handleGuestTickerUpdate(_ update: GuestWebSocketService.TickerUpdate) {
        guard dataSource == .guest else { return }

        // Check if this update is for our selected index
        let symbol = mapIndexToSymbol(selectedIndex)
        guard update.symbol == symbol else { return }

        // Update spot price with ticker data (fast updates)
        spotPrice = update.data.ltp
        spotChange = update.data.change
        spotChangePercent = update.data.pChange

        // Don't update lastUpdated for every ticker (too frequent)
        // lastUpdated is updated by spot/chain updates
    }

    private func handleGuestSpotUpdate(_ update: GuestWebSocketService.SpotUpdate) {
        guard dataSource == .guest else { return }

        // Check if this update is for our selected index
        let symbol = mapIndexToSymbol(selectedIndex)
        guard update.symbol == symbol else { return }

        // Update spot price
        spotPrice = update.data.lastPrice
        spotChange = update.data.change
        spotChangePercent = update.data.pChange
        if let prevClose = update.data.previousClose {
            updatePreviousClose(prevClose)
        }
        lastUpdated = Date()
    }

    private func handleGuestOptionChainUpdate(_ update: GuestWebSocketService.OptionChainUpdate) {
        guard dataSource == .guest else { return }

        // Check if this update is for our selected index
        let symbol = mapIndexToSymbol(selectedIndex)
        guard update.symbol == symbol else { return }
        if let selected = selectedExpiry?.id, !selected.isEmpty, update.expiry != selected {
            return
        }

        // Update underlying value
        spotPrice = update.data.underlyingValue

        // Update option chain if data provided
        if let chainData = update.data.data {
            // Convert and update option chain
            updateOptionChainFromWebSocket(chainData, atmStrike: update.data.atmStrike)
        }

        lastUpdated = Date()
    }

    private func updateOptionChainFromWebSocket(_ data: [GuestWebSocketService.OptionChainUpdate.OptionChainData.StrikeData], atmStrike: Double) {
        // Update LTP values in existing option chain
        for strikeData in data {
            if let index = optionChain.firstIndex(where: { $0.strikePrice == strikeData.strikePrice }) {
                var row = optionChain[index]

                // Update call option if available
                if let ceData = strikeData.CE, var callOption = row.callOption {
                    callOption = OptionData(
                        strikePrice: row.strikePrice,
                        optionType: .call,
                        expiryDate: callOption.expiryDate,
                        lastTradedPrice: ceData.lastPrice,
                        openInterest: ceData.openInterest,
                        changeInOI: ceData.changeinOpenInterest,
                        impliedVolatility: ceData.impliedVolatility,
                        bidPrice: callOption.bidPrice,
                        askPrice: callOption.askPrice,
                        bidQty: callOption.bidQty,
                        askQty: callOption.askQty,
                        volume: ceData.totalTradedVolume ?? ceData.volume ?? callOption.volume,
                        underlyingValue: spotPrice
                    )
                    row = OptionChainRow(strikePrice: row.strikePrice, callOption: callOption, putOption: row.putOption)
                }

                // Update put option if available
                if let peData = strikeData.PE, var putOption = row.putOption {
                    putOption = OptionData(
                        strikePrice: row.strikePrice,
                        optionType: .put,
                        expiryDate: putOption.expiryDate,
                        lastTradedPrice: peData.lastPrice,
                        openInterest: peData.openInterest,
                        changeInOI: peData.changeinOpenInterest,
                        impliedVolatility: peData.impliedVolatility,
                        bidPrice: putOption.bidPrice,
                        askPrice: putOption.askPrice,
                        bidQty: putOption.bidQty,
                        askQty: putOption.askQty,
                        volume: peData.totalTradedVolume ?? peData.volume ?? putOption.volume,
                        underlyingValue: spotPrice
                    )
                    row = OptionChainRow(strikePrice: row.strikePrice, callOption: row.callOption, putOption: putOption)
                }

                optionChain[index] = row
            }
        }

        dataVersion += 1
    }

    private func mapIndexToSymbol(_ index: TradingIndex) -> String {
        switch index {
        case .nifty50: return "NIFTY"
        case .bankNifty: return "BANKNIFTY"
        case .niftyFinService: return "FINNIFTY"
        case .midcapSelect: return "MIDCPNIFTY"
        case .sensex: return "SENSEX"
        case .bankex: return "SENSEX"
        }
    }

    private func handleLivePriceUpdate(_ update: LivePriceUpdate) {
        // Update spot price with live data
        spotPrice = update.lastPrice
        spotChange = update.change
        spotChangePercent = update.changePercent
        updatePreviousClose(update.previousClose)
        lastUpdated = update.timestamp
    }

    private func handleOptionPriceUpdate(_ update: LiveOptionPriceUpdate) {
        guard let lookup = instrumentKeyIndex[update.instrumentKey] else { return }

        let rowIndex = lookup.rowIndex
        let isCall = lookup.isCall
        guard rowIndex < optionChain.count else { return }

        let row = optionChain[rowIndex]
        let existingOption = isCall ? row.callOption : row.putOption
        guard let existing = existingOption else { return }

        // Create updated OptionData with new LTP and optionally new IV/Greeks
        let updatedOption = OptionData(
            id: existing.id,
            strikePrice: existing.strikePrice,
            optionType: existing.optionType,
            expiryDate: existing.expiryDate,
            lastTradedPrice: update.lastPrice,
            openInterest: existing.openInterest,
            changeInOI: existing.changeInOI,
            impliedVolatility: update.iv.map { $0 / 100.0 } ?? existing.impliedVolatility,
            bidPrice: existing.bidPrice,
            askPrice: existing.askPrice,
            bidQty: existing.bidQty,
            askQty: existing.askQty,
            volume: existing.volume,
            underlyingValue: spotPrice > 0 ? spotPrice : existing.underlyingValue,
            delta: update.delta ?? existing.delta,
            gamma: update.gamma ?? existing.gamma,
            theta: update.theta ?? existing.theta,
            vega: update.vega ?? existing.vega,
            instrumentKey: existing.instrumentKey
        )

        // Replace the row with updated option
        let updatedRow: OptionChainRow
        if isCall {
            updatedRow = OptionChainRow(strikePrice: row.strikePrice, callOption: updatedOption, putOption: row.putOption)
        } else {
            updatedRow = OptionChainRow(strikePrice: row.strikePrice, callOption: row.callOption, putOption: updatedOption)
        }

        optionChain[rowIndex] = updatedRow
        lastUpdated = update.timestamp
    }

    /// Build a lookup index mapping instrument keys to their position in the option chain.
    private func buildInstrumentKeyIndex() {
        var index: [String: (rowIndex: Int, isCall: Bool)] = [:]
        for (i, row) in optionChain.enumerated() {
            if let callKey = row.callOption?.instrumentKey {
                index[callKey] = (rowIndex: i, isCall: true)
            }
            if let putKey = row.putOption?.instrumentKey {
                index[putKey] = (rowIndex: i, isCall: false)
            }
        }
        instrumentKeyIndex = index
        print("📋 [ViewModel] Built instrument key index: \(index.count) entries")
    }

    /// Subscribe to nearby option instruments via WebSocket.
    private func subscribeOptionInstruments() {
        guard dataSource == .upstox else { return }

        // Collect instrument keys from nearby strikes (±20 intervals from ATM)
        let rows = nearbyStrikes
        var optionKeys: [String] = []
        for row in rows {
            if let callKey = row.callOption?.instrumentKey {
                optionKeys.append(callKey)
            }
            if let putKey = row.putOption?.instrumentKey {
                optionKeys.append(putKey)
            }
        }

        guard !optionKeys.isEmpty else {
            print("⚠️ [ViewModel] No instrument keys to subscribe")
            return
        }

        UpstoxWebSocketService.shared.updateSubscriptions(
            indexKey: selectedIndex.instrumentKey,
            optionKeys: optionKeys
        )
        print("📋 [ViewModel] Subscribed to \(optionKeys.count) option instruments")
    }

    func connectWebSocket() {
        // Support both Upstox and Guest mode WebSocket
        if dataSource == .guest {
            connectGuestWebSocket()
            return
        }

        guard isUpstoxAuthenticated else {
            print("⚠️ [ViewModel] Cannot connect WebSocket - not authenticated")
            return
        }
        UpstoxWebSocketService.shared.connect()
    }

    func disconnectWebSocket() {
        // Disconnect both WebSocket services
        if dataSource == .guest {
            GuestWebSocketService.shared.disconnect()
        } else {
            UpstoxWebSocketService.shared.disconnect()
        }
    }

    // MARK: - Periodic Refresh

    private func startRefreshTimer() {
        refreshTimer?.invalidate()
        let interval = UserDefaults.standard.object(forKey: "optionChainInterval") as? Double ?? 5.0
        refreshTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            Task { @MainActor in
                await self?.silentRefresh()
            }
        }
    }

    private func stopRefreshTimer() {
        refreshTimer?.invalidate()
        refreshTimer = nil
    }

    private func startSpotPriceTimer() {
        spotPriceTimer?.invalidate()
        let interval = UserDefaults.standard.object(forKey: "spotPriceInterval") as? Double ?? 2.0
        spotPriceTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            Task { @MainActor in
                await self?.refreshSpotPrice()
            }
        }
    }

    private func stopSpotPriceTimer() {
        spotPriceTimer?.invalidate()
        spotPriceTimer = nil
    }

    // MARK: - Auto Retry for Failed Loads

    private func startAutoRetryTimer() {
        autoRetryTimer?.invalidate()
        // Retry every 5 seconds if data failed to load
        autoRetryTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                // Only retry if we're in error state or have no data
                if case .error = self.loadingState {
                    print("🔄 [AutoRetry] Retrying data load...")
                    await self.loadData()
                } else if self.optionChain.isEmpty && self.spotPrice == 0 {
                    print("🔄 [AutoRetry] No data, retrying...")
                    await self.loadData()
                } else {
                    // Data loaded successfully, stop retry timer
                    self.stopAutoRetryTimer()
                }
            }
        }
        print("⏱️ [AutoRetry] Started auto-retry timer (5s interval)")
    }

    private func stopAutoRetryTimer() {
        autoRetryTimer?.invalidate()
        autoRetryTimer = nil
        print("⏱️ [AutoRetry] Stopped auto-retry timer")
    }

    // MARK: - Guest Mode Refresh Timer

    private func startGuestRefreshTimer() {
        guestRefreshTimer?.invalidate()
        // Refresh option chain every 10 seconds in guest mode
        guestRefreshTimer = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                await self?.silentGuestRefresh()
            }
        }
        print("⏱️ [Guest] Started refresh timer (10s interval)")
    }

    private func stopGuestRefreshTimer() {
        guestRefreshTimer?.invalidate()
        guestRefreshTimer = nil
    }

    /// Silently refresh guest data without changing loading state
    private func silentGuestRefresh() async {
        guard dataSource == .guest else { return }

        let guestService = GuestDataService.shared

        // Refresh spot price
        do {
            let spotResult = try await guestService.fetchSpotPrice(index: selectedIndex)
            self.spotPrice = spotResult.lastPrice
            self.spotChange = spotResult.change
            self.spotChangePercent = spotResult.changePercent
            updatePreviousClose(spotResult.previousClose)
        } catch {
            // Silent failure
        }

        // Refresh option chain if we have an expiry selected
        if let expiry = selectedExpiry {
            do {
                let result = try await guestService.fetchOptionChainWithTotals(
                    index: selectedIndex,
                    expiry: expiry.id
                )
                if !result.rows.isEmpty {
                    self.optionChain = result.rows
                    self.fullChainCallOI = result.fullChainCallOI
                    self.fullChainPutOI = result.fullChainPutOI
                    self.loadingState = .loaded
                    print("🔄 [Guest] Silent refresh: \(result.rows.count) strikes")
                }
            } catch {
                // Silent failure
            }
        }

        lastUpdated = Date()
        dataVersion += 1
    }

    private func refreshSpotPrice() async {
        guard dataSource == .upstox, UpstoxAPIService.shared.isAuthenticated else { return }
        do {
            let quote = try await UpstoxAPIService.shared.fetchSpotPrice(index: selectedIndex)
            spotPrice = quote.lastPrice
            spotChange = quote.change
            spotChangePercent = quote.changePercent
            updatePreviousClose(quote.previousClose)
            lastUpdated = Date()
        } catch {
            // Silent failure — don't disrupt UI
        }
    }

    /// Silently refresh option chain via REST without changing loadingState.
    private func silentRefresh() async {
        guard dataSource == .upstox,
              UpstoxAPIService.shared.isAuthenticated,
              let expiry = selectedExpiry else { return }

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let expiryString = dateFormatter.string(from: expiry.date)

        do {
            let fetchedChain = try await UpstoxAPIService.shared.fetchOptionChain(index: selectedIndex, expiry: expiryString)
            if !fetchedChain.isEmpty {
                optionChain = fetchedChain.sorted { $0.strikePrice < $1.strikePrice }
                buildInstrumentKeyIndex()
                lastUpdated = Date()
                print("🔄 [ViewModel] Silent refresh: \(optionChain.count) strikes updated")
            }
        } catch {
            print("⚠️ [ViewModel] Silent refresh failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Data Loading

    func loadData() async {
        // Check data source and load accordingly
        switch dataSource {
        case .guest:
            await loadGuestData()
        case .upstox:
            if UpstoxAPIService.shared.isAuthenticated {
                await loadUpstoxData()
            } else {
                loadingState = .error(L.optionChainNoData)
                dataSource = .guest
                await loadGuestData()
            }
        case .mock:
            loadingState = .error(L.optionChainNoData)
        }
    }

    func loadUpstoxData() async {
        loadingState = .loading
        dataSource = .upstox

        print("📊 [Data] Loading \(selectedIndex.displayName) data...")
        print("📊 [Data] Is authenticated: \(UpstoxAPIService.shared.isAuthenticated)")

        do {
            // Fetch expiry dates for selected index
            print("📊 [Data] Fetching \(selectedIndex.shortName) expiry dates...")
            let dates = try await UpstoxAPIService.shared.fetchExpiryDates(index: selectedIndex)
            print("📊 [Data] Got \(dates.count) expiry dates")
            expiryDates = dates

            // Select first expiry if none selected
            if selectedExpiry == nil, let firstExpiry = dates.first {
                selectedExpiry = firstExpiry
            }

            // Fetch spot price with change data
            print("📊 [Data] Fetching \(selectedIndex.shortName) spot price...")
            let quote = try await UpstoxAPIService.shared.fetchSpotPrice(index: selectedIndex)
            spotPrice = quote.lastPrice
            spotChange = quote.change
            spotChangePercent = quote.changePercent
            updatePreviousClose(quote.previousClose)
            print("📊 [Data] Spot: \(spotPrice), Change: \(spotChange) (\(spotChangePercent)%)")

            // Fetch option chain for selected expiry
            if let expiry = selectedExpiry {
                // Convert expiry to API format (yyyy-MM-dd)
                let dateFormatter = DateFormatter()
                dateFormatter.dateFormat = "yyyy-MM-dd"
                let expiryString = dateFormatter.string(from: expiry.date)

                print("📊 [Data] Fetching \(selectedIndex.shortName) option chain for \(expiryString)...")
                let fetchedChain = try await UpstoxAPIService.shared.fetchOptionChain(index: selectedIndex, expiry: expiryString)
                print("📊 [Data] Got \(fetchedChain.count) strikes")

                if fetchedChain.isEmpty {
                    optionChain = []
                    loadingState = .error(L.optionChainNoData)
                    print("⚠️ [Data] Option chain empty")
                } else {
                    optionChain = fetchedChain.sorted { $0.strikePrice < $1.strikePrice }
                    print("📊 [Data] Loaded \(optionChain.count) strikes")
                }
            }

            dataVersion += 1
            if !optionChain.isEmpty {
                loadingState = .loaded
            }
            lastUpdated = Date()
            isUpstoxAuthenticated = true
            dataSource = .upstox
            print("✅ [Data] Upstox data loaded successfully!")

            // Build instrument key lookup for live updates
            buildInstrumentKeyIndex()

            // Connect WebSocket for real-time updates
            connectWebSocket()

            // Subscribe to option instruments after WebSocket connects
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
                self?.subscribeOptionInstruments()
            }

            // Start periodic REST refresh for OI/volume/bid-ask
            startRefreshTimer()

            // Start fast spot price polling (2s) for near real-time index price
            startSpotPriceTimer()

        } catch let error as UpstoxAPIError {
            print("❌ [Data] UpstoxAPIError: \(error.localizedDescription)")
            stopRefreshTimer()
            stopSpotPriceTimer()
            loadingState = .error(error.localizedDescription)
            optionChain = []
            expiryDates = []

            if case .notAuthenticated = error {
                isUpstoxAuthenticated = false
                // Don't show login sheet automatically, use guest mode instead
                // showLoginSheet = true
                switchToGuestMode()
            } else if case .tokenExpired = error {
                isUpstoxAuthenticated = false
                // Don't show login sheet automatically, use guest mode instead
                // showLoginSheet = true
                switchToGuestMode()
            }
        } catch {
            print("❌ [Data] Error: \(error)")
            stopRefreshTimer()
            stopSpotPriceTimer()
            loadingState = .error(error.localizedDescription)
            optionChain = []
            expiryDates = []
        }
    }

    func refresh() async {
        await loadData()
    }

    /// Switch to Guest Mode - uses backend API without broker login
    func switchToGuestMode() {
        // Stop any existing timers
        stopRefreshTimer()
        stopSpotPriceTimer()
        stopAutoRetryTimer()
        stopGuestRefreshTimer()

        dataSource = .guest
        loadingState = .loading

        Task {
            await loadGuestData()
        }
    }

    /// Load data from Guest Mode backend API
    func loadGuestData() async {
        print("🚀 [Guest] Starting loadGuestData for \(selectedIndex.displayName)")
        dataSource = .guest
        loadingState = .loading

        let guestService = GuestDataService.shared

        // Fetch expiry dates
        do {
            let expiries = try await guestService.fetchExpiryDates(index: selectedIndex)
            self.expiryDates = expiries

            // Select first expiry if none selected
            if selectedExpiry == nil, let firstExpiry = expiries.first {
                selectedExpiry = firstExpiry
            }
        } catch {
            print("❌ [Guest] Error loading expiry dates: \(error.localizedDescription)")
        }

        // Fetch spot price
        do {
            let spotResult = try await guestService.fetchSpotPrice(index: selectedIndex)
            self.spotPrice = spotResult.lastPrice
            self.spotChange = spotResult.change
            self.spotChangePercent = spotResult.changePercent
            updatePreviousClose(spotResult.previousClose)
        } catch {
            print("❌ [Guest] Error loading spot price: \(error.localizedDescription)")
        }

        // Fetch option chain with full-chain totals
        do {
            if let expiry = selectedExpiry {
                let result = try await guestService.fetchOptionChainWithTotals(
                    index: selectedIndex,
                    expiry: expiry.id  // id contains the "dd-MMM-yyyy" format
                )
                self.optionChain = result.rows
                self.fullChainCallOI = result.fullChainCallOI
                self.fullChainPutOI = result.fullChainPutOI
            }
        } catch {
            print("❌ [Guest] Error loading option chain: \(error.localizedDescription)")
            optionChain = []
        }

        if !optionChain.isEmpty {
            loadingState = .loaded
            // Stop auto-retry since we have data
            stopAutoRetryTimer()
            // Start periodic refresh for guest mode
            startGuestRefreshTimer()
        } else if spotPrice > 0 {
            // Spot price loaded but no chain data - show partial data
            loadingState = .loaded
            stopAutoRetryTimer()
            startGuestRefreshTimer()
        } else {
            loadingState = .error("Could not load market data. Pull to refresh.")
            // Start auto-retry timer to reload data
            startAutoRetryTimer()
        }
        lastUpdated = Date()
        dataVersion += 1

        print("📡 [Guest] Loaded data for \(selectedIndex.displayName) — chain: \(optionChain.count) rows, spot: \(spotPrice)")

        // Connect to WebSocket for real-time updates
        connectGuestWebSocket()
    }

    /// Connect to Guest Mode WebSocket for real-time updates
    private func connectGuestWebSocket() {
        let wsService = GuestWebSocketService.shared

        // Connect if not already connected
        if !wsService.isConnected {
            wsService.connect()
        }

        // Subscribe to all update types for current index
        let symbol = mapIndexToSymbol(selectedIndex)

        // Subscribe to ticker for fast LTP updates (500ms)
        wsService.subscribeToTicker(symbols: [symbol])

        // Subscribe to spot for full data (1 second)
        wsService.subscribeToSpot(symbols: [symbol])

        // Subscribe to option chain updates (5 seconds) for selected expiry
        let expiry = selectedExpiry?.id
        wsService.subscribeToOptionChain(symbols: [symbol], expiry: expiry)

        print("📡 [Guest WS] Subscribed to all real-time updates for \(symbol)")
        print("📡 [Guest WS]   - Ticker: 500ms (LTP only)")
        print("📡 [Guest WS]   - Spot: 1s (full data)")
        print("📡 [Guest WS]   - Chain: 5s (full chain)")
    }

    /// Disconnect from Guest WebSocket
    private func disconnectGuestWebSocket() {
        let wsService = GuestWebSocketService.shared
        let symbol = mapIndexToSymbol(selectedIndex)

        // Unsubscribe from all update types
        wsService.unsubscribeFromTicker(symbols: [symbol])
        wsService.unsubscribeFromSpot(symbols: [symbol])
        wsService.unsubscribeFromOptionChain(symbols: [symbol])
    }

    func selectExpiry(_ expiry: ExpiryDate) {
        selectedExpiry = expiry

        // Unsubscribe old option instruments before reloading
        UpstoxWebSocketService.shared.unsubscribeOptions()
        instrumentKeyIndex = [:]

        // Reload data for the new expiry based on current data source
        switch dataSource {
        case .upstox:
            if UpstoxAPIService.shared.isAuthenticated {
                Task {
                    await loadUpstoxData()
                }
            } else {
                // Switch to guest mode instead of showing login
                switchToGuestMode()
            }
        case .guest:
            Task {
                await loadGuestData()
            }
        case .mock:
            loadingState = .error(L.optionChainNoData)
        }
    }

    func selectIndex(_ index: TradingIndex) {
        guard index != selectedIndex else { return }

        // Stop all timers for old index
        stopRefreshTimer()
        stopSpotPriceTimer()
        stopAutoRetryTimer()
        stopGuestRefreshTimer()

        // Unsubscribe old option instruments
        UpstoxWebSocketService.shared.unsubscribeOptions()

        // Reset state for new index
        selectedIndex = index
        selectedExpiry = nil
        expiryDates = []
        optionChain = []
        instrumentKeyIndex = [:]
        spotPrice = 0
        spotChange = 0
        spotChangePercent = 0

        // Reconnect WebSocket for new index
        if isWebSocketConnected {
            disconnectWebSocket()
        }

        // Reload data for new index
        Task {
            await loadData()
        }

        print("📊 [Index] Switched to \(index.displayName)")
    }

    /// Async version of selectIndex that waits for data to load
    func selectIndexAsync(_ index: TradingIndex) async {
        guard index != selectedIndex else { return }

        // Stop timers for old index
        stopRefreshTimer()
        stopSpotPriceTimer()

        // Unsubscribe old option instruments
        UpstoxWebSocketService.shared.unsubscribeOptions()

        // Reset state for new index
        selectedIndex = index
        selectedExpiry = nil
        expiryDates = []
        optionChain = []
        instrumentKeyIndex = [:]
        spotPrice = 0
        spotChange = 0
        spotChangePercent = 0

        // Reconnect WebSocket for new index
        if isWebSocketConnected {
            disconnectWebSocket()
        }

        // Load data and wait for completion
        await loadData()

        print("📊 [Index] Async switched to \(index.displayName)")
    }

    /// Async version of selectExpiry that waits for data to load
    func selectExpiryAsync(_ expiry: ExpiryDate) async {
        selectedExpiry = expiry

        // Unsubscribe old option instruments before reloading
        UpstoxWebSocketService.shared.unsubscribeOptions()
        instrumentKeyIndex = [:]

        // Reload data for the new expiry
        if UpstoxAPIService.shared.isAuthenticated {
            await loadUpstoxData()
        } else {
            // Use guest mode instead of showing login
            await loadGuestData()
        }
    }

    // MARK: - Authentication

    func loginToUpstox() {
        showLoginSheet = true
    }

    func onUpstoxLoginSuccess() {
        isUpstoxAuthenticated = true
        showLoginSheet = false
        Task {
            await loadUpstoxData()
        }
    }

    func logoutFromUpstox() {
        stopRefreshTimer()
        stopSpotPriceTimer()
        stopAutoRetryTimer()
        stopGuestRefreshTimer()
        disconnectWebSocket()
        UpstoxAPIService.shared.logout()
        isUpstoxAuthenticated = false
        instrumentKeyIndex = [:]
        dataSource = .guest
        Task {
            await loadGuestData()
        }
    }

    // MARK: - Option Selection

    func selectOption(_ option: OptionData) {
        selectedOption = option
        showingCalculator = true
    }

    func getRow(for strikePrice: Double) -> OptionChainRow? {
        optionChain.first { $0.strikePrice == strikePrice }
    }

    // MARK: - Analysis Methods

    private func calculateMaxPain() -> Double? {
        guard !optionChain.isEmpty else { return nil }

        var minPain = Double.infinity
        var maxPainStrike = optionChain[optionChain.count / 2].strikePrice

        for row in optionChain {
            var totalPain = 0.0

            // Calculate pain for call writers
            for otherRow in optionChain {
                if let call = otherRow.callOption, row.strikePrice > otherRow.strikePrice {
                    totalPain += Double(call.openInterest) * (row.strikePrice - otherRow.strikePrice)
                }
            }

            // Calculate pain for put writers
            for otherRow in optionChain {
                if let put = otherRow.putOption, row.strikePrice < otherRow.strikePrice {
                    totalPain += Double(put.openInterest) * (otherRow.strikePrice - row.strikePrice)
                }
            }

            if totalPain < minPain {
                minPain = totalPain
                maxPainStrike = row.strikePrice
            }
        }

        return maxPainStrike
    }

    func getOIAnalysis() -> (support: Double?, resistance: Double?) {
        guard !optionChain.isEmpty, let atm = atmStrike else { return (nil, nil) }

        // Find highest Put OI below ATM (support)
        let support = optionChain
            .filter { $0.strikePrice < atm }
            .max { ($0.putOption?.openInterest ?? 0) < ($1.putOption?.openInterest ?? 0) }?
            .strikePrice

        // Find highest Call OI above ATM (resistance)
        let resistance = optionChain
            .filter { $0.strikePrice > atm }
            .max { ($0.callOption?.openInterest ?? 0) < ($1.callOption?.openInterest ?? 0) }?
            .strikePrice

        return (support, resistance)
    }

    
    // MARK: - Formatting Helpers

    func formatOI(_ oi: Int) -> String {
        if oi >= 10_000_000 {
            return String(format: "%.1fCr", Double(oi) / 10_000_000)
        } else if oi >= 100_000 {
            return String(format: "%.1fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.1fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }

    func formatPrice(_ price: Double) -> String {
        return String(format: "%.2f", price)
    }

    func formatSpot(_ price: Double) -> String {
        return String(format: "%.0f", price)
    }
}

// MARK: - Calculator ViewModel

@MainActor
final class CalculatorViewModel: ObservableObject {

    // MARK: - Published Properties

    @Published var spotPrice: String = "25000"
    @Published var strikePrice: String = "25000"
    @Published var optionType: OptionType = .call
    @Published var daysToExpiry: String = "7"
    @Published var impliedVolatility: String = "15"
    @Published var targetSpot: String = "25500"
    @Published var stopLossSpot: String = "24500"
    @Published var currentOptionPrice: String = ""

    @Published var calculatedPrice: Double?
    @Published var greeks: GreeksResult?
    @Published var targetCalculation: TargetCalculation?

    /// Stored expiry date for precise time-to-expiry calculation
    /// when loaded from the option chain.
    private var loadedExpiryDate: Date?
    /// The daysToExpiry string at load time; used to detect manual edits.
    private var loadedDaysString: String?

    // MARK: - Computed Properties

    var spotPriceValue: Double { Double(spotPrice) ?? 0 }
    var strikePriceValue: Double { Double(strikePrice) ?? 0 }
    var daysToExpiryValue: Int { Int(daysToExpiry) ?? 0 }
    var ivValue: Double { (Double(impliedVolatility) ?? 0) / 100.0 }
    var targetSpotValue: Double { Double(targetSpot) ?? 0 }
    var stopLossSpotValue: Double { Double(stopLossSpot) ?? 0 }
    var currentOptionPriceValue: Double { Double(currentOptionPrice) ?? 0 }

    /// Returns precise fractional time-to-expiry (in years) when loaded
    /// from the option chain and the user hasn't manually changed the days
    /// field. Falls back to integer days / 365 otherwise.
    var timeToExpiry: Double {
        if let date = loadedExpiryDate,
           let loadedDays = loadedDaysString,
           daysToExpiry == loadedDays {
            return BlackScholesEngine.preciseTimeToExpiry(from: date)
        }
        return BlackScholesEngine.daysToYears(daysToExpiryValue)
    }

    var isInputValid: Bool {
        spotPriceValue > 0 &&
        strikePriceValue > 0 &&
        timeToExpiry > 0 &&
        ivValue > 0
    }

    var isTargetInputValid: Bool {
        isInputValid &&
        targetSpotValue > 0 &&
        stopLossSpotValue > 0
    }

    // MARK: - Initialization

    init() {}

    init(option: OptionData) {
        loadOption(option)
    }

    // MARK: - Methods

    func loadOption(_ option: OptionData) {
        spotPrice = String(format: "%.0f", option.underlyingValue)
        strikePrice = String(format: "%.0f", option.strikePrice)
        optionType = option.optionType
        daysToExpiry = "\(option.daysToExpiry)"
        currentOptionPrice = String(format: "%.2f", option.lastTradedPrice)

        // Store expiry date for precise fractional time-to-expiry
        loadedExpiryDate = option.expiryDate
        loadedDaysString = daysToExpiry

        // Set default target/SL based on option type
        if option.optionType == .call {
            targetSpot = String(format: "%.0f", option.underlyingValue + 500)
            stopLossSpot = String(format: "%.0f", option.underlyingValue - 300)
        } else {
            targetSpot = String(format: "%.0f", option.underlyingValue - 500)
            stopLossSpot = String(format: "%.0f", option.underlyingValue + 300)
        }

        // Auto-calculate IV from market price so the theoretical price
        // matches the LTP the user saw in the option chain.
        if option.lastTradedPrice > 0, timeToExpiry > 0 {
            if let iv = BlackScholesEngine.calculateImpliedVolatility(
                optionPrice: option.lastTradedPrice,
                spotPrice: option.underlyingValue,
                strikePrice: option.strikePrice,
                timeToExpiry: timeToExpiry,
                isCall: option.optionType == .call
            ) {
                impliedVolatility = String(format: "%.1f", iv * 100)
            } else {
                impliedVolatility = String(format: "%.1f", option.impliedVolatility * 100)
            }
        } else {
            impliedVolatility = String(format: "%.1f", option.impliedVolatility * 100)
        }

        calculateAll()
    }

    func calculatePrice() {
        guard isInputValid else {
            calculatedPrice = nil
            return
        }

        calculatedPrice = TargetCalculator.shared.calculateOptionPrice(
            spotPrice: spotPriceValue,
            strikePrice: strikePriceValue,
            optionType: optionType,
            timeToExpiry: timeToExpiry,
            volatility: ivValue
        )
    }

    func calculateGreeks() {
        guard isInputValid else {
            greeks = nil
            return
        }

        greeks = TargetCalculator.shared.calculateGreeks(
            spotPrice: spotPriceValue,
            strikePrice: strikePriceValue,
            optionType: optionType,
            timeToExpiry: timeToExpiry,
            volatility: ivValue
        )
    }

    func calculateTargetSL() {
        guard isTargetInputValid else {
            targetCalculation = nil
            return
        }

        // Create a mock option for calculation
        let expiryDate = Calendar.current.date(byAdding: .day, value: daysToExpiryValue, to: Date()) ?? Date()

        let option = OptionData(
            strikePrice: strikePriceValue,
            optionType: optionType,
            expiryDate: expiryDate,
            lastTradedPrice: currentOptionPriceValue > 0 ? currentOptionPriceValue : (calculatedPrice ?? 0),
            impliedVolatility: ivValue,
            underlyingValue: spotPriceValue
        )

        targetCalculation = TargetCalculator.shared.calculateTargetSL(
            option: option,
            targetSpot: targetSpotValue,
            stopLossSpot: stopLossSpotValue
        )
    }

    func calculateAll() {
        calculatePrice()
        calculateGreeks()
        calculateTargetSL()
    }

    func reset() {
        spotPrice = "25000"
        strikePrice = "25000"
        optionType = .call
        daysToExpiry = "7"
        impliedVolatility = "15"
        targetSpot = "25500"
        stopLossSpot = "24500"
        currentOptionPrice = ""
        calculatedPrice = nil
        greeks = nil
        targetCalculation = nil
        loadedExpiryDate = nil
        loadedDaysString = nil
    }

    func swapTargetSL() {
        let temp = targetSpot
        targetSpot = stopLossSpot
        stopLossSpot = temp
        calculateTargetSL()
    }

    func calculateImpliedVolatility() {
        guard spotPriceValue > 0,
              strikePriceValue > 0,
              timeToExpiry > 0,
              currentOptionPriceValue > 0 else { return }

        if let iv = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice: currentOptionPriceValue,
            spotPrice: spotPriceValue,
            strikePrice: strikePriceValue,
            timeToExpiry: timeToExpiry,
            isCall: optionType == .call
        ) {
            impliedVolatility = String(format: "%.1f", iv * 100)
            calculateAll()
        }
    }
}



class LoginViewController: UIViewController {
    
    func getToken() -> String {
        return "token_from_Login"
    }
    
    func loginTap() {
        
    }
}

protocol loginToken {
    func getToken() -> String
}

class LoginViewModel:loginToken {
    func getToken() -> String {
        return "token"
    }
    
    func login() {
        // get the token from LoginViewController and check if token == "token_from_Login" then notify Login ViewController
    }
}
