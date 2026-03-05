import Foundation
import Combine

// MARK: - WebSocket Service Documentation
/*
 ================================================================================
 GUEST WEBSOCKET SERVICE - Real-time Market Data Streaming
 ================================================================================

 This service provides real-time market data streaming for Guest Mode users
 (users without broker login). It connects to the backend WebSocket server.

 DATA FLOW:
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │  iOS App (GuestWebSocketService)                                            │
│  ├─ Connects to: wss://api.optix.d23ai.in/ws/market                        │
 │  ├─ Subscribes to: spot, ticker, option_chain                              │
 │  └─ Receives: Real-time updates via WebSocket                              │
 │                              │                                              │
 │                              ▼                                              │
 │  Backend WebSocket Service (websocket_service.py)                           │
 │  ├─ Manages connections                                                     │
 │  ├─ Fetches from Upstox (with caching)                                     │
 │  └─ Broadcasts to subscribed clients                                        │
 └─────────────────────────────────────────────────────────────────────────────┘

 SUBSCRIPTION TYPES:
 ┌────────────────────┬───────────┬─────────────────────────────────────────────┐
 │ Type               │ Interval  │ Data Received                               │
 ├────────────────────┼───────────┼─────────────────────────────────────────────┤
 │ ticker             │ 500ms     │ LTP only (minimal bandwidth, fastest)       │
 │ spot               │ 1 second  │ Full spot data (LTP, change, OHLC)          │
 │ option_chain       │ 5 seconds │ Full option chain with all strikes          │
 └────────────────────┴───────────┴─────────────────────────────────────────────┘

 USAGE:
     // Connect to WebSocket
     GuestWebSocketService.shared.connect()

     // Subscribe to fast ticker (500ms updates)
     GuestWebSocketService.shared.subscribeToTicker(symbols: ["NIFTY"])

     // Subscribe to full spot data (1s updates)
     GuestWebSocketService.shared.subscribeToSpot(symbols: ["NIFTY"])

     // Listen for updates
     NotificationCenter.default.publisher(for: .guestTickerUpdated)
         .sink { notification in
             let update = notification.object as? TickerUpdate
         }
 ================================================================================
 */

/// WebSocket Service for real-time market data streaming in Guest Mode
/// Connects to the backend WebSocket for live price updates
@MainActor
class GuestWebSocketService: ObservableObject {

    // MARK: - Singleton

    static let shared = GuestWebSocketService()

    // MARK: - Published Properties

    @Published var isConnected: Bool = false
    @Published var lastSpotUpdate: SpotUpdate?
    @Published var lastTickerUpdate: TickerUpdate?
    @Published var lastOptionChainUpdate: OptionChainUpdate?
    @Published var connectionError: String?
    @Published var messagesReceived: Int = 0

    // MARK: - Private Properties

    private var webSocketTask: URLSessionWebSocketTask?
    private var session: URLSession
    private var pingTimer: Timer?
    private var reconnectTimer: Timer?
    private var reconnectAttempts = 0
    private let maxReconnectAttempts = 5

    private var subscribedSpotSymbols: Set<String> = []
    private var subscribedTickerSymbols: Set<String> = []
    private var subscribedOptionChainSymbols: Set<String> = []
    private var subscribedOptionChainExpiries: [String: String] = [:]

    // WebSocket URL
    private var wsURL: URL {
        return URL(string: AppEnvironment.current.wsBaseURL)!
    }

    // MARK: - Models

    /// Fast ticker update (500ms) - LTP only for minimal bandwidth
    struct TickerUpdate: Codable {
        let type: String
        let symbol: String
        let data: TickerData
        let timestamp: String

        struct TickerData: Codable {
            let ltp: Double
            let change: Double
            let pChange: Double
        }
    }

    /// Full spot update (1 second) - includes OHLC data
    struct SpotUpdate: Codable {
        let type: String
        let symbol: String
        let data: SpotData
        let timestamp: String

        struct SpotData: Codable {
            let lastPrice: Double
            let change: Double
            let pChange: Double
            let open: Double?
            let high: Double?
            let low: Double?
            let previousClose: Double?
            let isLive: Bool?
            let isDemo: Bool?
        }
    }

    /// Option chain update (5 seconds) - full chain data
    struct OptionChainUpdate: Codable {
        let type: String
        let symbol: String
        let expiry: String
        let data: OptionChainData
        let timestamp: String

        struct OptionChainData: Codable {
            let underlyingValue: Double
            let atmStrike: Double
            let data: [StrikeData]?
            let isLive: Bool?
            let isDemo: Bool?

            struct StrikeData: Codable {
                let strikePrice: Double
                let CE: OptionSideData?
                let PE: OptionSideData?

                struct OptionSideData: Codable {
                    let lastPrice: Double
                    let openInterest: Int
                    let changeinOpenInterest: Int
                    let impliedVolatility: Double
                    let volume: Int?
                    let totalTradedVolume: Int?
                }
            }
        }
    }

    // MARK: - Initialization

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.waitsForConnectivity = true
        self.session = URLSession(configuration: config)
    }

    // MARK: - Connection Management

    /// Connect to WebSocket server
    func connect() {
        guard webSocketTask == nil || webSocketTask?.state != .running else {
            print("📡 [WS] Already connected or connecting")
            return
        }

        print("📡 [WS] Connecting to \(wsURL)")

        webSocketTask = session.webSocketTask(with: wsURL)
        webSocketTask?.resume()

        // Start receiving messages
        receiveMessage()

        // Start ping timer
        startPingTimer()

        reconnectAttempts = 0
    }

    /// Disconnect from WebSocket server
    func disconnect() {
        print("📡 [WS] Disconnecting")

        pingTimer?.invalidate()
        pingTimer = nil

        reconnectTimer?.invalidate()
        reconnectTimer = nil

        webSocketTask?.cancel(with: .normalClosure, reason: nil)
        webSocketTask = nil

        isConnected = false
        subscribedTickerSymbols.removeAll()
        subscribedSpotSymbols.removeAll()
        subscribedOptionChainSymbols.removeAll()
        subscribedOptionChainExpiries.removeAll()
    }

    // MARK: - Subscriptions

    /// Subscribe to fast ticker updates (500ms) - LTP only, minimal bandwidth
    /// Use this for real-time price display where you only need the current price
    func subscribeToTicker(symbols: [String]) {
        let message: [String: Any] = [
            "action": "subscribe",
            "type": "ticker",
            "symbols": symbols.map { $0.uppercased() }
        ]

        sendMessage(message)
        symbols.forEach { subscribedTickerSymbols.insert($0.uppercased()) }
        print("📡 [WS] Subscribed to ticker: \(symbols)")
    }

    /// Unsubscribe from ticker updates
    func unsubscribeFromTicker(symbols: [String]) {
        let message: [String: Any] = [
            "action": "unsubscribe",
            "type": "ticker",
            "symbols": symbols.map { $0.uppercased() }
        ]

        sendMessage(message)
        symbols.forEach { subscribedTickerSymbols.remove($0.uppercased()) }
    }

    /// Subscribe to full spot price updates (1 second) - includes OHLC
    /// Use this when you need complete spot data including day's OHLC
    func subscribeToSpot(symbols: [String]) {
        let message: [String: Any] = [
            "action": "subscribe",
            "type": "spot",
            "symbols": symbols.map { $0.uppercased() }
        ]

        sendMessage(message)
        symbols.forEach { subscribedSpotSymbols.insert($0.uppercased()) }
        print("📡 [WS] Subscribed to spot: \(symbols)")
    }

    /// Unsubscribe from spot price updates
    func unsubscribeFromSpot(symbols: [String]) {
        let message: [String: Any] = [
            "action": "unsubscribe",
            "type": "spot",
            "symbols": symbols.map { $0.uppercased() }
        ]

        sendMessage(message)
        symbols.forEach { subscribedSpotSymbols.remove($0.uppercased()) }
    }

    /// Subscribe to option chain updates (5 seconds) - full chain data
    /// Use this for displaying the complete option chain
    func subscribeToOptionChain(symbols: [String], expiry: String? = nil) {
        var message: [String: Any] = [
            "action": "subscribe",
            "type": "option_chain",
            "symbols": symbols.map { $0.uppercased() }
        ]
        if let expiry {
            message["expiry"] = expiry
        }

        sendMessage(message)
        symbols.forEach { symbol in
            let upper = symbol.uppercased()
            if let expiry {
                subscribedOptionChainExpiries[upper] = expiry
                subscribedOptionChainSymbols.remove(upper)
            } else {
                subscribedOptionChainSymbols.insert(upper)
                subscribedOptionChainExpiries.removeValue(forKey: upper)
            }
        }
        print("📡 [WS] Subscribed to option_chain: \(symbols) \(expiry ?? "")")
    }

    /// Unsubscribe from option chain updates
    func unsubscribeFromOptionChain(symbols: [String]) {
        let message: [String: Any] = [
            "action": "unsubscribe",
            "type": "option_chain",
            "symbols": symbols.map { $0.uppercased() }
        ]

        sendMessage(message)
        symbols.forEach { symbol in
            let upper = symbol.uppercased()
            subscribedOptionChainSymbols.remove(upper)
            subscribedOptionChainExpiries.removeValue(forKey: upper)
        }
    }

    /// Subscribe to all update types for a symbol (ticker + spot + option_chain)
    /// Convenience method for full real-time experience
    func subscribeToAll(symbols: [String]) {
        subscribeToTicker(symbols: symbols)
        subscribeToSpot(symbols: symbols)
        subscribeToOptionChain(symbols: symbols)
    }

    /// Unsubscribe from all update types for a symbol
    func unsubscribeFromAll(symbols: [String]) {
        unsubscribeFromTicker(symbols: symbols)
        unsubscribeFromSpot(symbols: symbols)
        unsubscribeFromOptionChain(symbols: symbols)
    }

    // MARK: - Message Handling

    private func sendMessage(_ message: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: message),
              let string = String(data: data, encoding: .utf8) else {
            print("❌ [WS] Failed to serialize message")
            return
        }

        webSocketTask?.send(.string(string)) { [weak self] error in
            if let error = error {
                print("❌ [WS] Send error: \(error)")
                self?.handleDisconnection()
            }
        }
    }

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            Task { @MainActor in
                guard let self = self else { return }

                switch result {
                case .success(let message):
                    switch message {
                    case .string(let text):
                        self.handleMessage(text)
                    case .data(let data):
                        if let text = String(data: data, encoding: .utf8) {
                            self.handleMessage(text)
                        }
                    @unknown default:
                        break
                    }

                    // Continue receiving
                    self.receiveMessage()

                case .failure(let error):
                    print("❌ [WS] Receive error: \(error)")
                    self.handleDisconnection()
                }
            }
        }
    }

    private func handleMessage(_ text: String) {
        guard let data = text.data(using: .utf8) else { return }

        // Try to parse as generic JSON first
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            return
        }

        Task { @MainActor in
            self.messagesReceived += 1

            switch type {
            case "connected":
                print("📡 [WS] Connected to server")
                if let serverTime = json["server_time"] as? String {
                    print("📡 [WS] Server time: \(serverTime)")
                }
                self.isConnected = true
                self.connectionError = nil
                // Resubscribe to previous subscriptions
                self.resubscribe()

            case "subscribed":
                print("📡 [WS] Subscribed: \(json["symbols"] ?? [])")

            case "ticker_update":
                // Fast LTP-only update (500ms)
                if let tickerUpdate = try? JSONDecoder().decode(TickerUpdate.self, from: data) {
                    self.lastTickerUpdate = tickerUpdate
                    NotificationCenter.default.post(
                        name: .guestTickerUpdated,
                        object: tickerUpdate
                    )
                }

            case "spot_update":
                // Full spot data update (1 second)
                if let spotUpdate = try? JSONDecoder().decode(SpotUpdate.self, from: data) {
                    self.lastSpotUpdate = spotUpdate
                    NotificationCenter.default.post(
                        name: .guestSpotPriceUpdated,
                        object: spotUpdate
                    )
                }

            case "option_chain_update":
                // Full option chain update (5 seconds)
                if let chainUpdate = try? JSONDecoder().decode(OptionChainUpdate.self, from: data) {
                    self.lastOptionChainUpdate = chainUpdate
                    NotificationCenter.default.post(
                        name: .guestOptionChainUpdated,
                        object: chainUpdate
                    )
                }

            case "pong":
                // Ping response received - connection is healthy
                break

            case "error":
                print("❌ [WS] Server error: \(json["message"] ?? "Unknown")")
                self.connectionError = json["message"] as? String

            case "unsubscribed":
                print("📡 [WS] Unsubscribed: \(json["symbols"] ?? [])")

            default:
                print("📡 [WS] Unknown message type: \(type)")
            }
        }
    }

    private func handleDisconnection() {
        isConnected = false
        webSocketTask = nil
        pingTimer?.invalidate()

        // Attempt reconnection
        attemptReconnect()
    }

    // MARK: - Reconnection

    private func attemptReconnect() {
        guard reconnectAttempts < maxReconnectAttempts else {
            print("❌ [WS] Max reconnect attempts reached")
            connectionError = "Connection failed after \(maxReconnectAttempts) attempts"
            return
        }

        reconnectAttempts += 1
        let delay = Double(reconnectAttempts) * 2.0  // Exponential backoff

        print("📡 [WS] Attempting reconnect in \(delay)s (attempt \(reconnectAttempts))")

        reconnectTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.connect()
            }
        }
    }

    private func resubscribe() {
        // Resubscribe to previous subscriptions after reconnect
        if !subscribedTickerSymbols.isEmpty {
            subscribeToTicker(symbols: Array(subscribedTickerSymbols))
        }
        if !subscribedSpotSymbols.isEmpty {
            subscribeToSpot(symbols: Array(subscribedSpotSymbols))
        }
        if !subscribedOptionChainSymbols.isEmpty {
            subscribeToOptionChain(symbols: Array(subscribedOptionChainSymbols))
        }
        if !subscribedOptionChainExpiries.isEmpty {
            for (symbol, expiry) in subscribedOptionChainExpiries {
                subscribeToOptionChain(symbols: [symbol], expiry: expiry)
            }
        }
        print("📡 [WS] Resubscribed after reconnect")
    }

    // MARK: - Ping/Keep-Alive

    private func startPingTimer() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.sendPing()
            }
        }
    }

    private func sendPing() {
        let message: [String: Any] = ["action": "ping"]
        sendMessage(message)
    }
}

// MARK: - Notification Names

extension Notification.Name {
    /// Fast ticker update (500ms) - LTP only
    static let guestTickerUpdated = Notification.Name("guestTickerUpdated")

    /// Full spot price update (1 second) - includes OHLC
    static let guestSpotPriceUpdated = Notification.Name("guestSpotPriceUpdated")

    /// Option chain update (5 seconds) - full chain data
    static let guestOptionChainUpdated = Notification.Name("guestOptionChainUpdated")
}

// MARK: - Usage Example
/*
 // In your ViewModel or View:

 // 1. Connect to WebSocket
 GuestWebSocketService.shared.connect()

 // 2. Subscribe to updates (choose based on your needs)
 GuestWebSocketService.shared.subscribeToTicker(symbols: ["NIFTY"])  // Fast 500ms updates
 GuestWebSocketService.shared.subscribeToSpot(symbols: ["NIFTY"])     // Full spot 1s updates
 GuestWebSocketService.shared.subscribeToOptionChain(symbols: ["NIFTY"])  // Chain 5s updates

 // 3. Listen for updates
 NotificationCenter.default.publisher(for: .guestTickerUpdated)
     .compactMap { $0.object as? GuestWebSocketService.TickerUpdate }
     .sink { update in
         print("LTP: \(update.data.ltp)")
     }
     .store(in: &cancellables)

 // 4. Disconnect when done
 GuestWebSocketService.shared.disconnect()
 */
