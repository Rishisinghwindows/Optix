import Foundation

// MARK: - Market Signal Model

struct MarketSignal: Identifiable, Codable {
    let id: String
    let signalType: String
    let title: String
    let body: String
    let index: String
    let timestamp: Date
    var isRead: Bool

    var icon: String {
        switch signalType {
        case "long_buildup": return "chart.line.uptrend.xyaxis"
        case "short_buildup": return "chart.line.downtrend.xyaxis"
        case "long_unwinding": return "arrow.down.right.circle"
        case "short_covering": return "arrow.up.forward.circle"
        case "pcr_shift": return "arrow.left.arrow.right"
        case "vix_change": return "bolt.fill"
        case "oi_surge": return "flame.fill"
        case "max_pain_shift": return "scope"
        default: return "chart.bar.fill"
        }
    }

    var color: String {
        switch signalType {
        case "long_buildup", "short_covering": return "00C805"
        case "short_buildup", "long_unwinding": return "FF3B30"
        case "pcr_shift": return "007AFF"
        case "vix_change": return "FF9500"
        case "oi_surge": return "BF5AF2"
        case "max_pain_shift": return "5AC8FA"
        default: return "8E8E93"
        }
    }

    var sentiment: String {
        switch signalType {
        case "long_buildup": return "Bullish"
        case "short_buildup": return "Bearish"
        case "long_unwinding": return "Weak"
        case "short_covering": return "Squeeze"
        case "pcr_shift": return "Shift"
        case "vix_change": return "Volatility"
        case "oi_surge": return "Activity"
        case "max_pain_shift": return "Institutional"
        default: return ""
        }
    }

    var severityLevel: Int {
        switch signalType {
        case "vix_change", "oi_surge": return 3
        case "long_buildup", "short_buildup", "short_covering": return 2
        default: return 1
        }
    }
}

// MARK: - Market Signal Store

class MarketSignalStore: ObservableObject {
    static let shared = MarketSignalStore()

    private let storageKey: String
    private let maxSignals = 200

    @Published var signals: [MarketSignal] = []

    var unreadCount: Int {
        signals.filter { !$0.isRead }.count
    }

    private convenience init() {
        self.init(storageKey: "market_signal_notifications")
    }

    /// Designated initializer — internal for testing with a custom storage key
    init(storageKey: String) {
        self.storageKey = storageKey
        loadSignals()
    }

    func addSignal(from userInfo: [AnyHashable: Any]) {
        let signalType = userInfo["signal"] as? String ?? "unknown"
        let title = (userInfo["aps"] as? [String: Any])?["alert"] as? [String: Any]
        let titleStr = title?["title"] as? String ?? userInfo["title"] as? String ?? "Market Signal"
        let bodyStr = title?["body"] as? String ?? userInfo["body"] as? String ?? ""
        let index = userInfo["index"] as? String ?? ""

        let signal = MarketSignal(
            id: "\(signalType)_\(index)_\(Date().timeIntervalSince1970)",
            signalType: signalType,
            title: titleStr,
            body: bodyStr,
            index: index,
            timestamp: Date(),
            isRead: false
        )

        signals.insert(signal, at: 0)
        if signals.count > maxSignals {
            signals = Array(signals.prefix(maxSignals))
        }
        saveSignals()
    }

    func addFromNotification(title: String, body: String, data: [String: String]) {
        let signal = MarketSignal(
            id: "\(data["signal"] ?? "")_\(data["index"] ?? "")_\(Date().timeIntervalSince1970)",
            signalType: data["signal"] ?? "unknown",
            title: title,
            body: body,
            index: data["index"] ?? "",
            timestamp: Date(),
            isRead: false
        )

        signals.insert(signal, at: 0)
        if signals.count > maxSignals {
            signals = Array(signals.prefix(maxSignals))
        }
        saveSignals()
    }

    func markAsRead(_ signal: MarketSignal) {
        if let idx = signals.firstIndex(where: { $0.id == signal.id }) {
            signals[idx].isRead = true
            saveSignals()
        }
    }

    func markAllAsRead() {
        for i in signals.indices {
            signals[i].isRead = true
        }
        saveSignals()
    }

    func clearAll() {
        signals.removeAll()
        saveSignals()
    }

    func deleteSignal(_ signal: MarketSignal) {
        signals.removeAll { $0.id == signal.id }
        saveSignals()
    }

    private func loadSignals() {
        guard let data = UserDefaults.standard.data(forKey: storageKey) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        signals = (try? decoder.decode([MarketSignal].self, from: data)) ?? []
    }

    private func saveSignals() {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(signals) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }
}
