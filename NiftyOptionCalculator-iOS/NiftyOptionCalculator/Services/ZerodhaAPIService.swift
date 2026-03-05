import Foundation
import AuthenticationServices

// MARK: - Zerodha Kite Configuration

struct ZerodhaConfig {
    // Replace with your Kite Connect API credentials
    static let apiKey = "YOUR_KITE_API_KEY"
    static let apiSecret = "YOUR_KITE_API_SECRET"

    static let baseURL = "https://api.kite.trade"
    static let loginURL = "https://kite.zerodha.com/connect/login"
    static let tokenURL = "https://api.kite.trade/session/token"

    // Callback URL scheme for OAuth
    static let callbackURLScheme = "optix"
    static let redirectURI = "https://api.d23ai.in/zerodha/callback"

    // Instrument tokens for indices
    static func instrumentToken(for index: TradingIndex) -> String {
        switch index {
        case .nifty50: return "256265"      // NIFTY 50
        case .bankNifty: return "260105"    // BANK NIFTY
        case .niftyFinService: return "257801" // FIN NIFTY
        case .midcapSelect: return "288009" // MIDCAP SELECT
        case .sensex: return "265"          // SENSEX (BSE)
        case .bankex: return "274441"       // BANKEX (BSE)
        }
    }

    // Trading symbol prefix for options
    static func tradingSymbol(for index: TradingIndex) -> String {
        switch index {
        case .nifty50: return "NIFTY"
        case .bankNifty: return "BANKNIFTY"
        case .niftyFinService: return "FINNIFTY"
        case .midcapSelect: return "MIDCPNIFTY"
        case .sensex: return "SENSEX"
        case .bankex: return "BANKEX"
        }
    }

    // Exchange for index
    static func exchange(for index: TradingIndex) -> String {
        switch index {
        case .sensex, .bankex: return "BFO"  // BSE F&O
        default: return "NFO"                 // NSE F&O
        }
    }
}

// MARK: - Zerodha API Response Models

struct ZerodhaAuthResponse: Codable {
    let status: String
    let data: ZerodhaAuthData?
    let message: String?
    let errorType: String?

    enum CodingKeys: String, CodingKey {
        case status, data, message
        case errorType = "error_type"
    }
}

struct ZerodhaAuthData: Codable {
    let accessToken: String
    let refreshToken: String?
    let userId: String
    let userName: String?
    let email: String?

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case userId = "user_id"
        case userName = "user_name"
        case email
    }
}

struct ZerodhaQuoteResponse: Codable {
    let status: String
    let data: [String: ZerodhaQuoteData]?
    let message: String?
}

struct ZerodhaQuoteData: Codable {
    let instrumentToken: Int
    let lastPrice: Double
    let ohlc: ZerodhaOHLC?
    let change: Double?
    let netChange: Double?

    enum CodingKeys: String, CodingKey {
        case instrumentToken = "instrument_token"
        case lastPrice = "last_price"
        case ohlc
        case change
        case netChange = "net_change"
    }
}

struct ZerodhaOHLC: Codable {
    let open: Double
    let high: Double
    let low: Double
    let close: Double
}

struct ZerodhaInstrumentsResponse: Codable {
    // CSV format - parsed separately
}

// MARK: - Zerodha API Service

@MainActor
class ZerodhaAPIService: ObservableObject, DataProvider {

    // MARK: - DataProvider Protocol Properties

    var providerId: String { "zerodha" }
    var displayName: String { "Zerodha Kite" }
    var iconName: String { "leaf.fill" }
    var brandColorHex: String { "387ED1" }

    // MARK: - Published Properties

    @Published var isAuthenticated: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var userName: String?

    // MARK: - Private Properties

    private var accessToken: String? {
        didSet {
            isAuthenticated = accessToken != nil
            if let token = accessToken {
                saveTokenToKeychain(token)
            }
        }
    }

    private let session: URLSession

    // Cached instruments data
    private var instrumentsCache: [String: ZerodhaInstrument] = [:]
    private var instrumentsCacheDate: Date?

    // MARK: - Singleton

    static let shared = ZerodhaAPIService()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)

        // Load saved token
        if let savedToken = loadTokenFromKeychain() {
            self.accessToken = savedToken
            self.isAuthenticated = true
        }
    }

    // MARK: - Authentication

    func getAuthorizationURL() -> URL? {
        var components = URLComponents(string: ZerodhaConfig.loginURL)
        components?.queryItems = [
            URLQueryItem(name: "api_key", value: ZerodhaConfig.apiKey),
            URLQueryItem(name: "v", value: "3"),
            URLQueryItem(name: "redirect_params", value: "")
        ]
        return components?.url
    }

    func authenticate() async throws {
        // This will be called after OAuth flow completes with request token
        throw ProviderError.notAuthenticated
    }

    func authenticate(withRequestToken requestToken: String) async throws {
        isLoading = true
        defer { isLoading = false }

        guard let url = URL(string: ZerodhaConfig.tokenURL) else {
            throw ProviderError.apiError("Invalid URL")
        }

        // Create checksum: SHA256(api_key + request_token + api_secret)
        let checksumString = ZerodhaConfig.apiKey + requestToken + ZerodhaConfig.apiSecret
        let checksum = sha256(checksumString)

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let bodyParams = [
            "api_key": ZerodhaConfig.apiKey,
            "request_token": requestToken,
            "checksum": checksum
        ]
        let bodyString = bodyParams.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        request.httpBody = bodyString.data(using: .utf8)

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw ProviderError.invalidResponse
            }

            if httpResponse.statusCode != 200 {
                throw ProviderError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let authResponse = try JSONDecoder().decode(ZerodhaAuthResponse.self, from: data)

            if authResponse.status == "success", let authData = authResponse.data {
                self.accessToken = authData.accessToken
                self.userName = authData.userName ?? authData.userId
                self.errorMessage = nil
            } else {
                throw ProviderError.apiError(authResponse.message ?? "Authentication failed")
            }

        } catch let error as ProviderError {
            self.errorMessage = error.localizedDescription
            throw error
        } catch {
            self.errorMessage = error.localizedDescription
            throw ProviderError.networkError(error)
        }
    }

    func logout() {
        accessToken = nil
        userName = nil
        deleteTokenFromKeychain()
        isAuthenticated = false
    }

    // MARK: - DataProvider Protocol Methods

    func fetchExpiryDates(index: TradingIndex) async throws -> [ExpiryDate] {
        // Zerodha doesn't have a direct API for expiry dates
        // Generate based on index expiry day
        let calendar = Calendar.current
        var expiries: [ExpiryDate] = []
        var currentDate = Date()
        let targetWeekday = index.expiryDayOfWeek + 1

        while expiries.count < 4 {
            let weekday = calendar.component(.weekday, from: currentDate)
            if weekday == targetWeekday {
                let formatter = DateFormatter()
                formatter.dateFormat = "dd-MMM-yyyy"
                let dateString = formatter.string(from: currentDate)
                expiries.append(ExpiryDate(dateString: dateString))
            }
            currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate) ?? currentDate
        }

        return expiries
    }

    func fetchSpotPrice(index: TradingIndex) async throws -> SpotQuoteResult {
        guard let token = accessToken else {
            throw ProviderError.notAuthenticated
        }

        let instrumentToken = ZerodhaConfig.instrumentToken(for: index)
        let exchange = index.exchange == "BSE" ? "BSE" : "NSE"
        let tradingSymbol = index.shortName

        let urlString = "\(ZerodhaConfig.baseURL)/quote?i=\(exchange):\(tradingSymbol)"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("token \(ZerodhaConfig.apiKey):\(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw ProviderError.invalidResponse
            }

            if httpResponse.statusCode == 401 {
                logout()
                throw ProviderError.tokenExpired
            }

            if httpResponse.statusCode != 200 {
                throw ProviderError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let quoteResponse = try JSONDecoder().decode(ZerodhaQuoteResponse.self, from: data)

            if let quoteData = quoteResponse.data?.values.first {
                let change = quoteData.netChange ?? quoteData.change ?? 0
                let prevClose = quoteData.ohlc?.close ?? (quoteData.lastPrice - change)
                let changePercent = prevClose > 0 ? (change / prevClose) * 100 : 0

                return SpotQuoteResult(
                    lastPrice: quoteData.lastPrice,
                    change: change,
                    changePercent: changePercent,
                    previousClose: prevClose
                )
            }

            throw ProviderError.invalidResponse

        } catch let error as ProviderError {
            throw error
        } catch {
            throw ProviderError.networkError(error)
        }
    }

    func fetchOptionChain(index: TradingIndex, expiry: String) async throws -> [OptionChainRow] {
        guard accessToken != nil else {
            throw ProviderError.notAuthenticated
        }

        throw ProviderError.apiError("Option chain not supported for Zerodha")
    }

    // MARK: - Helper Methods

    private func sha256(_ string: String) -> String {
        guard let data = string.data(using: .utf8) else { return "" }
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))

        data.withUnsafeBytes { buffer in
            _ = CC_SHA256(buffer.baseAddress, CC_LONG(buffer.count), &hash)
        }

        return hash.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Keychain Management

    private let keychainKey = "com.optix.zerodha.accesstoken"

    private func saveTokenToKeychain(_ token: String) {
        let data = token.data(using: .utf8)!
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainKey,
            kSecValueData as String: data
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    private func loadTokenFromKeychain() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainKey,
            kSecReturnData as String: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let token = String(data: data, encoding: .utf8) else {
            return nil
        }
        return token
    }

    private func deleteTokenFromKeychain() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainKey
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Historical Candle Data

    /// Fetch historical candle data for index charts
    /// Zerodha Kite Connect API supports historical data for indices
    func fetchHistoricalCandles(index: TradingIndex, timeFrame: ChartTimeFrame) async throws -> [OHLCData] {
        guard let token = accessToken else {
            throw ProviderError.notAuthenticated
        }

        let instrumentToken = ZerodhaConfig.instrumentToken(for: index)
        let interval = zerodhaInterval(for: timeFrame)

        // Calculate date range based on timeframe
        let calendar = Calendar.current
        let toDate = Date()
        let fromDate: Date

        switch timeFrame {
        case .oneMin, .fiveMin:
            fromDate = calendar.date(byAdding: .day, value: -1, to: toDate) ?? toDate
        case .fifteenMin, .thirtyMin:
            fromDate = calendar.date(byAdding: .day, value: -5, to: toDate) ?? toDate
        case .oneHour, .fourHour:
            fromDate = calendar.date(byAdding: .day, value: -30, to: toDate) ?? toDate
        case .oneDay:
            fromDate = calendar.date(byAdding: .year, value: -1, to: toDate) ?? toDate
        case .oneWeek:
            fromDate = calendar.date(byAdding: .year, value: -2, to: toDate) ?? toDate
        }

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let fromString = dateFormatter.string(from: fromDate)
        let toString = dateFormatter.string(from: toDate)

        // Zerodha historical data endpoint
        let urlString = "\(ZerodhaConfig.baseURL)/instruments/historical/\(instrumentToken)/\(interval)?from=\(fromString)&to=\(toString)"

        print("📊 [Zerodha] Historical URL: \(urlString)")

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("token \(ZerodhaConfig.apiKey):\(token)", forHTTPHeaderField: "Authorization")

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw ProviderError.invalidResponse
            }

            if httpResponse.statusCode == 401 {
                logout()
                throw ProviderError.tokenExpired
            }

            if httpResponse.statusCode != 200 {
                if let jsonString = String(data: data, encoding: .utf8) {
                    print("📊 [Zerodha] Error response: \(jsonString)")
                }
                throw ProviderError.apiError("HTTP \(httpResponse.statusCode)")
            }

            // Parse Zerodha historical response
            let historicalResponse = try JSONDecoder().decode(ZerodhaHistoricalResponse.self, from: data)

            guard let candles = historicalResponse.data?.candles else {
                print("📊 [Zerodha] No candles in response")
                return []
            }

            print("✅ [Zerodha] Got \(candles.count) historical candles")
            return parseZerodhaCandles(candles, timeFrame: timeFrame)

        } catch let error as ProviderError {
            throw error
        } catch {
            print("📊 [Zerodha] Error: \(error)")
            throw ProviderError.networkError(error)
        }
    }

    /// Convert ChartTimeFrame to Zerodha interval string
    private func zerodhaInterval(for timeFrame: ChartTimeFrame) -> String {
        switch timeFrame {
        case .oneMin: return "minute"
        case .fiveMin: return "5minute"
        case .fifteenMin: return "15minute"
        case .thirtyMin: return "30minute"
        case .oneHour: return "60minute"
        case .fourHour: return "60minute"  // Will aggregate
        case .oneDay: return "day"
        case .oneWeek: return "day"  // Will aggregate
        }
    }

    /// Parse Zerodha candle array format into OHLCData
    private func parseZerodhaCandles(_ candles: [[ZerodhaHistoricalValue]], timeFrame: ChartTimeFrame) -> [OHLCData] {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withFullDate, .withTime, .withDashSeparatorInDate, .withColonSeparatorInTime]

        var result: [OHLCData] = []

        for candle in candles {
            guard candle.count >= 6 else { continue }

            // Zerodha format: [timestamp, open, high, low, close, volume]
            let timestampValue = candle[0]
            let openValue = candle[1]
            let highValue = candle[2]
            let lowValue = candle[3]
            let closeValue = candle[4]
            let volumeValue = candle[5]

            var timestamp: Date?
            var open: Double = 0
            var high: Double = 0
            var low: Double = 0
            var close: Double = 0
            var volume: Int = 0

            // Parse timestamp
            if case .string(let ts) = timestampValue {
                timestamp = dateFormatter.date(from: ts)
            }

            // Parse OHLCV values
            if case .double(let val) = openValue { open = val }
            if case .double(let val) = highValue { high = val }
            if case .double(let val) = lowValue { low = val }
            if case .double(let val) = closeValue { close = val }
            if case .int(let val) = volumeValue { volume = val }
            if case .double(let val) = volumeValue { volume = Int(val) }

            guard let ts = timestamp else { continue }

            let ohlc = OHLCData(
                timestamp: ts,
                open: open,
                high: high,
                low: low,
                close: close,
                volume: volume
            )
            result.append(ohlc)
        }

        // Aggregate for 4H and weekly if needed
        if timeFrame == .fourHour {
            result = aggregateCandles(result, periodHours: 4)
        } else if timeFrame == .oneWeek {
            result = aggregateToDays(result, days: 7)
        }

        return result.sorted { $0.timestamp < $1.timestamp }
    }

    /// Aggregate candles to larger timeframe
    private func aggregateCandles(_ candles: [OHLCData], periodHours: Int) -> [OHLCData] {
        guard !candles.isEmpty else { return [] }

        var aggregated: [OHLCData] = []
        var currentGroup: [OHLCData] = []
        let periodSeconds = TimeInterval(periodHours * 3600)

        for candle in candles.sorted(by: { $0.timestamp < $1.timestamp }) {
            if currentGroup.isEmpty {
                currentGroup.append(candle)
            } else if let first = currentGroup.first,
                      candle.timestamp.timeIntervalSince(first.timestamp) < periodSeconds {
                currentGroup.append(candle)
            } else {
                if let merged = mergeCandles(currentGroup) {
                    aggregated.append(merged)
                }
                currentGroup = [candle]
            }
        }

        if let merged = mergeCandles(currentGroup) {
            aggregated.append(merged)
        }

        return aggregated
    }

    /// Aggregate daily candles to weekly
    private func aggregateToDays(_ candles: [OHLCData], days: Int) -> [OHLCData] {
        guard !candles.isEmpty else { return [] }

        var aggregated: [OHLCData] = []
        var currentGroup: [OHLCData] = []
        var dayCount = 0
        var lastDate: Date?

        let calendar = Calendar.current

        for candle in candles.sorted(by: { $0.timestamp < $1.timestamp }) {
            let candleDay = calendar.startOfDay(for: candle.timestamp)

            if lastDate == nil || candleDay != lastDate {
                dayCount += 1
                lastDate = candleDay
            }

            if dayCount <= days {
                currentGroup.append(candle)
            } else {
                if let merged = mergeCandles(currentGroup) {
                    aggregated.append(merged)
                }
                currentGroup = [candle]
                dayCount = 1
            }
        }

        if let merged = mergeCandles(currentGroup) {
            aggregated.append(merged)
        }

        return aggregated
    }

    /// Merge multiple candles into one
    private func mergeCandles(_ candles: [OHLCData]) -> OHLCData? {
        guard let first = candles.first, let last = candles.last else { return nil }

        return OHLCData(
            timestamp: first.timestamp,
            open: first.open,
            high: candles.map { $0.high }.max() ?? first.high,
            low: candles.map { $0.low }.min() ?? first.low,
            close: last.close,
            volume: candles.map { $0.volume }.reduce(0, +)
        )
    }
}

// MARK: - Zerodha Historical Response Models

struct ZerodhaHistoricalResponse: Codable {
    let status: String
    let data: ZerodhaHistoricalData?
    let message: String?
}

struct ZerodhaHistoricalData: Codable {
    let candles: [[ZerodhaHistoricalValue]]?
}

/// Enum to handle mixed types in Zerodha candle arrays
enum ZerodhaHistoricalValue: Codable {
    case string(String)
    case int(Int)
    case double(Double)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let intValue = try? container.decode(Int.self) {
            self = .int(intValue)
        } else if let doubleValue = try? container.decode(Double.self) {
            self = .double(doubleValue)
        } else if let stringValue = try? container.decode(String.self) {
            self = .string(stringValue)
        } else {
            throw DecodingError.typeMismatch(ZerodhaHistoricalValue.self, DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "Unknown type"))
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value): try container.encode(value)
        case .int(let value): try container.encode(value)
        case .double(let value): try container.encode(value)
        }
    }
}

// MARK: - Zerodha Instrument Model

struct ZerodhaInstrument {
    let instrumentToken: Int
    let exchangeToken: Int
    let tradingSymbol: String
    let name: String
    let expiry: Date?
    let strike: Double
    let lotSize: Int
    let instrumentType: String
    let exchange: String
}

// MARK: - CommonCrypto Import

import CommonCrypto
