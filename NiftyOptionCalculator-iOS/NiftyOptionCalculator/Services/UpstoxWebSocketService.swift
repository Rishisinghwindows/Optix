import Foundation
import Combine

// MARK: - WebSocket Message Types

struct WebSocketSubscribeMessage: Codable {
    let guid: String
    let method: String
    let data: SubscribeData

    struct SubscribeData: Codable {
        let mode: String
        let instrumentKeys: [String]

        enum CodingKeys: String, CodingKey {
            case mode
            case instrumentKeys = "instrument_keys"
        }
    }
}

struct WebSocketFeedResponse: Codable {
    let type: String?
    let feeds: [String: FeedData]?
}

struct FeedData: Codable {
    let ff: FullFeed?

    struct FullFeed: Codable {
        let indexFF: IndexFeed?
        let optionFF: OptionFeed?

        enum CodingKeys: String, CodingKey {
            case indexFF = "indexFF"
            case optionFF = "optionFF"
        }
    }
}

struct IndexFeed: Codable {
    let ltpc: LTPC?

    struct LTPC: Codable {
        let ltp: Double?
        let cp: Double?  // close price (previous)
    }
}

struct OptionFeed: Codable {
    let ltpc: LTPC?
    let optionGreeks: Greeks?

    struct LTPC: Codable {
        let ltp: Double?
        let cp: Double?
    }

    struct Greeks: Codable {
        let delta: Double?
        let gamma: Double?
        let theta: Double?
        let vega: Double?
        let iv: Double?
    }
}

// MARK: - Live Price Update

struct LivePriceUpdate {
    let instrumentKey: String
    let lastPrice: Double
    let previousClose: Double
    let change: Double
    let changePercent: Double
    let timestamp: Date
}

// MARK: - Live Option Price Update

struct LiveOptionPriceUpdate {
    let instrumentKey: String
    let lastPrice: Double
    let previousClose: Double
    let iv: Double?
    let delta: Double?
    let gamma: Double?
    let theta: Double?
    let vega: Double?
    let timestamp: Date
}

// MARK: - WebSocket Service

@MainActor
class UpstoxWebSocketService: NSObject, ObservableObject {

    static let shared = UpstoxWebSocketService()

    // MARK: - Published Properties

    @Published var isConnected: Bool = false
    @Published var lastSpotPrice: Double = 0
    @Published var lastSpotChange: Double = 0
    @Published var lastSpotChangePercent: Double = 0
    @Published var connectionStatus: String = "Disconnected"

    // MARK: - Publishers

    let priceUpdatePublisher = PassthroughSubject<LivePriceUpdate, Never>()
    let optionUpdatePublisher = PassthroughSubject<LiveOptionPriceUpdate, Never>()

    // MARK: - Private Properties

    private var webSocket: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var isSubscribed = false
    private var reconnectAttempts = 0
    private let maxReconnectAttempts = 5
    private var pingTimer: Timer?

    // Dynamic instrument subscriptions
    private var currentIndexKey: String = "NSE_INDEX|Nifty 50"
    private var subscribedOptionKeys: [String] = []

    // MARK: - Initialization

    private override init() {
        super.init()
    }

    // MARK: - Connection Management

    func connect() {
        guard let token = UpstoxAPIService.shared.getAccessToken() else {
            print("❌ [WebSocket] No access token available")
            connectionStatus = "No token"
            return
        }

        guard !isConnected else {
            print("⚠️ [WebSocket] Already connected")
            return
        }

        connectionStatus = "Authorizing..."
        print("🔌 [WebSocket] Getting authorized WebSocket URL...")

        // First, get the authorized WebSocket URL from Upstox API
        Task {
            do {
                let authorizedURL = try await getAuthorizedWebSocketURL(token: token)
                await connectToWebSocket(url: authorizedURL, token: token)
            } catch {
                print("❌ [WebSocket] Failed to get authorized URL: \(error)")
                connectionStatus = "Auth failed"
                handleDisconnection()
            }
        }
    }

    private func getAuthorizedWebSocketURL(token: String) async throws -> URL {
        // Using v3 endpoint as v2 is discontinued
        let authURL = "https://api.upstox.com/v3/feed/market-data-feed/authorize"
        guard let url = URL(string: authURL) else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }

        print("📡 [WebSocket] Auth response status: \(httpResponse.statusCode)")

        guard httpResponse.statusCode == 200 else {
            if let errorText = String(data: data, encoding: .utf8) {
                print("❌ [WebSocket] Auth error: \(errorText)")
            }
            throw URLError(.userAuthenticationRequired)
        }

        // Parse response to get authorized URL
        struct AuthResponse: Codable {
            let status: String?
            let data: AuthData?

            struct AuthData: Codable {
                let authorizedRedirectUri: String
            }
        }

        let authResponse = try JSONDecoder().decode(AuthResponse.self, from: data)

        guard let urlString = authResponse.data?.authorizedRedirectUri,
              let wsURL = URL(string: urlString) else {
            print("❌ [WebSocket] No authorized URL in response")
            if let text = String(data: data, encoding: .utf8) {
                print("📥 [WebSocket] Response: \(text)")
            }
            throw URLError(.cannotParseResponse)
        }

        print("✅ [WebSocket] Got authorized URL: \(wsURL)")
        return wsURL
    }

    private func connectToWebSocket(url: URL, token: String) async {
        print("🔌 [WebSocket] Connecting to \(url)...")
        connectionStatus = "Connecting..."

        let config = URLSessionConfiguration.default
        urlSession = URLSession(configuration: config, delegate: nil, delegateQueue: .main)
        webSocket = urlSession?.webSocketTask(with: url)

        webSocket?.resume()

        // Start receiving messages
        receiveMessage()

        // Subscribe after a short delay to ensure connection is ready
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.subscribeToInstruments()
        }

        // Start ping timer
        startPingTimer()

        isConnected = true
        connectionStatus = "Connected"
        reconnectAttempts = 0

        print("✅ [WebSocket] Connection initiated")
    }

    func disconnect() {
        print("🔌 [WebSocket] Disconnecting...")

        pingTimer?.invalidate()
        pingTimer = nil

        webSocket?.cancel(with: .goingAway, reason: nil)
        webSocket = nil
        urlSession = nil

        isConnected = false
        isSubscribed = false
        subscribedOptionKeys = []
        connectionStatus = "Disconnected"

        print("✅ [WebSocket] Disconnected")
    }

    // MARK: - Subscription

    private func subscribeToInstruments() {
        guard isConnected else { return }

        let allKeys = [currentIndexKey] + subscribedOptionKeys
        print("📤 [WebSocket] Subscribing to \(allKeys.count) instruments (index + \(subscribedOptionKeys.count) options)")

        let subscribeMessage = WebSocketSubscribeMessage(
            guid: UUID().uuidString,
            method: "sub",
            data: WebSocketSubscribeMessage.SubscribeData(
                mode: "full",
                instrumentKeys: allKeys
            )
        )

        do {
            let jsonData = try JSONEncoder().encode(subscribeMessage)
            if let jsonString = String(data: jsonData, encoding: .utf8) {
                webSocket?.send(.string(jsonString)) { [weak self] error in
                    if let error = error {
                        print("❌ [WebSocket] Subscribe error: \(error)")
                    } else {
                        print("✅ [WebSocket] Subscribed to \(allKeys.count) instruments")
                        Task { @MainActor in
                            self?.isSubscribed = true
                        }
                    }
                }
            }
        } catch {
            print("❌ [WebSocket] Failed to encode subscribe message: \(error)")
        }
    }

    /// Update subscriptions for a new index and/or option instruments.
    /// Called by the ViewModel when data loads or index/expiry changes.
    func updateSubscriptions(indexKey: String, optionKeys: [String]) {
        // Cap at 100 option keys to stay within Upstox limits
        let cappedOptionKeys = Array(optionKeys.prefix(100))

        // Unsubscribe old option keys first if connected
        if isConnected && !subscribedOptionKeys.isEmpty {
            sendUnsubscribe(keys: subscribedOptionKeys)
        }

        currentIndexKey = indexKey
        subscribedOptionKeys = cappedOptionKeys

        if isConnected {
            subscribeToInstruments()
        }

        print("📋 [WebSocket] Updated subscriptions: index=\(indexKey), options=\(cappedOptionKeys.count)")
    }

    /// Unsubscribe from all option instruments (keeps index subscription).
    func unsubscribeOptions() {
        guard isConnected, !subscribedOptionKeys.isEmpty else { return }
        sendUnsubscribe(keys: subscribedOptionKeys)
        subscribedOptionKeys = []
        print("📋 [WebSocket] Unsubscribed from all option instruments")
    }

    private func sendUnsubscribe(keys: [String]) {
        let unsubMessage = WebSocketSubscribeMessage(
            guid: UUID().uuidString,
            method: "unsub",
            data: WebSocketSubscribeMessage.SubscribeData(
                mode: "full",
                instrumentKeys: keys
            )
        )

        do {
            let jsonData = try JSONEncoder().encode(unsubMessage)
            if let jsonString = String(data: jsonData, encoding: .utf8) {
                webSocket?.send(.string(jsonString)) { error in
                    if let error = error {
                        print("❌ [WebSocket] Unsubscribe error: \(error)")
                    } else {
                        print("✅ [WebSocket] Unsubscribed from \(keys.count) instruments")
                    }
                }
            }
        } catch {
            print("❌ [WebSocket] Failed to encode unsubscribe message: \(error)")
        }
    }

    // MARK: - Message Handling

    private func receiveMessage() {
        webSocket?.receive { [weak self] result in
            switch result {
            case .success(let message):
                self?.handleMessage(message)
                // Continue receiving
                self?.receiveMessage()

            case .failure(let error):
                print("❌ [WebSocket] Receive error: \(error)")
                Task { @MainActor in
                    self?.handleDisconnection()
                }
            }
        }
    }

    private func handleMessage(_ message: URLSessionWebSocketTask.Message) {
        switch message {
        case .string(let text):
            parseTextMessage(text)

        case .data(let data):
            // Upstox sends binary protobuf data
            parseDataMessage(data)

        @unknown default:
            print("⚠️ [WebSocket] Unknown message type")
        }
    }

    private func parseTextMessage(_ text: String) {
        guard let data = text.data(using: .utf8) else { return }

        do {
            // Try to parse as JSON
            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                // Check for feed data
                if let feeds = json["feeds"] as? [String: Any] {
                    for (key, value) in feeds {
                        guard let feedDict = value as? [String: Any] else { continue }
                        parseFeedData(instrumentKey: key, feed: feedDict)
                    }
                }
            }
        } catch {
            print("⚠️ [WebSocket] JSON parse error: \(error)")
        }
    }

    private func parseDataMessage(_ data: Data) {
        if let text = String(data: data, encoding: .utf8) {
            parseTextMessage(text)
        } else {
            // Protobuf binary data — cannot decode without SwiftProtobuf library.
            // Using REST polling as fallback for real-time updates.
            print("⚠️ [WebSocket] Received protobuf binary data (\(data.count) bytes) — skipping (no SwiftProtobuf decoder). REST polling active as fallback.")
        }
    }

    private func parseFeedData(instrumentKey: String, feed: [String: Any]) {
        guard let ff = feed["ff"] as? [String: Any] else { return }

        // Handle index feeds (e.g. NSE_INDEX|Nifty 50)
        if let indexFF = ff["indexFF"] as? [String: Any],
           let ltpc = indexFF["ltpc"] as? [String: Any] {

            let ltp = ltpc["ltp"] as? Double ?? 0
            let cp = ltpc["cp"] as? Double ?? 0

            let change = ltp - cp
            let changePercent = cp > 0 ? (change / cp) * 100 : 0

            Task { @MainActor in
                self.lastSpotPrice = ltp
                self.lastSpotChange = change
                self.lastSpotChangePercent = changePercent

                let update = LivePriceUpdate(
                    instrumentKey: instrumentKey,
                    lastPrice: ltp,
                    previousClose: cp,
                    change: change,
                    changePercent: changePercent,
                    timestamp: Date()
                )

                self.priceUpdatePublisher.send(update)
            }
        }

        // Handle option feeds (e.g. NSE_FO|NIFTY24...)
        if let optionFF = ff["optionFF"] as? [String: Any],
           let ltpc = optionFF["ltpc"] as? [String: Any] {

            let ltp = ltpc["ltp"] as? Double ?? 0
            let cp = ltpc["cp"] as? Double ?? 0

            // Extract Greeks if available
            let greeksDict = optionFF["optionGreeks"] as? [String: Any]
            let iv = greeksDict?["iv"] as? Double
            let delta = greeksDict?["delta"] as? Double
            let gamma = greeksDict?["gamma"] as? Double
            let theta = greeksDict?["theta"] as? Double
            let vega = greeksDict?["vega"] as? Double

            Task { @MainActor in
                let update = LiveOptionPriceUpdate(
                    instrumentKey: instrumentKey,
                    lastPrice: ltp,
                    previousClose: cp,
                    iv: iv,
                    delta: delta,
                    gamma: gamma,
                    theta: theta,
                    vega: vega,
                    timestamp: Date()
                )

                self.optionUpdatePublisher.send(update)
            }
        }
    }

    // MARK: - Connection Health

    private func startPingTimer() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            self?.sendPing()
        }
    }

    private func sendPing() {
        webSocket?.sendPing { [weak self] error in
            if let error = error {
                print("❌ [WebSocket] Ping failed: \(error)")
                Task { @MainActor in
                    self?.handleDisconnection()
                }
            }
        }
    }

    private func handleDisconnection() {
        isConnected = false
        isSubscribed = false
        connectionStatus = "Disconnected"

        // Attempt reconnection
        if reconnectAttempts < maxReconnectAttempts {
            reconnectAttempts += 1
            let delay = Double(reconnectAttempts) * 2.0

            print("🔄 [WebSocket] Reconnecting in \(delay)s (attempt \(reconnectAttempts)/\(maxReconnectAttempts))")
            connectionStatus = "Reconnecting..."

            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                self?.connect()
            }
        } else {
            print("❌ [WebSocket] Max reconnection attempts reached")
            connectionStatus = "Failed"
        }
    }
}

