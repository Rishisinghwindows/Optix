import Foundation
import AuthenticationServices

// MARK: - Upstox API Configuration

struct UpstoxConfig {
    static let apiKey = "70cb6dec-5e9c-4b13-8d46-44650f7186e4"
    static let apiSecret = "czpuiy4yc8"

    // Redirect URL registered in Upstox (your website that redirects to app)
    static let redirectURI = AppEnvironment.current.upstoxRedirectURI

    // Custom URL scheme that the website redirects to
    static let callbackURLScheme = "optix"

    static let baseURL = "https://api.upstox.com/v2"
    static let authURL = "https://api.upstox.com/v2/login/authorization/dialog"
    static let tokenURL = "https://api.upstox.com/v2/login/authorization/token"

    // Instrument keys for Nifty
    static let niftyIndexKey = "NSE_INDEX|Nifty 50"
    static let niftyOptionKey = "NSE_FO|NIFTY"
    static let indiaVixKey = "NSE_INDEX|India VIX"
}

// MARK: - Upstox API Errors

enum UpstoxAPIError: Error, LocalizedError {
    case notAuthenticated
    case invalidURL
    case networkError(Error)
    case invalidResponse
    case decodingError(Error)
    case authenticationFailed(String)
    case apiError(String)
    case tokenExpired

    var errorDescription: String? {
        switch self {
        case .notAuthenticated:
            return "Please login to Upstox first"
        case .invalidURL:
            return "Invalid API URL"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .invalidResponse:
            return "Invalid response from server"
        case .decodingError(let error):
            return "Data parsing error: \(error.localizedDescription)"
        case .authenticationFailed(let message):
            return "Authentication failed: \(message)"
        case .apiError(let message):
            return "API error: \(message)"
        case .tokenExpired:
            return "Session expired. Please login again"
        }
    }
}

// MARK: - Upstox API Response Models

struct UpstoxAuthResponse: Codable {
    let accessToken: String
    let tokenType: String?
    let expiresIn: Int?

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case tokenType = "token_type"
        case expiresIn = "expires_in"
    }
}

struct UpstoxOptionChainResponse: Codable {
    let status: String
    let data: [UpstoxOptionData]?
    let error: UpstoxErrorData?
}

struct UpstoxErrorData: Codable {
    let code: String?
    let message: String?
}

struct UpstoxOptionData: Codable {
    let expiry: String
    let strikePrice: Double
    let underlying_spot_price: Double
    let callOptions: UpstoxOptionDetails?
    let putOptions: UpstoxOptionDetails?

    enum CodingKeys: String, CodingKey {
        case expiry
        case strikePrice = "strike_price"
        case underlying_spot_price
        case callOptions = "call_options"
        case putOptions = "put_options"
    }
}

struct UpstoxOptionDetails: Codable {
    let instrumentKey: String?
    let marketData: UpstoxMarketData?
    let optionGreeks: UpstoxGreeks?

    enum CodingKeys: String, CodingKey {
        case instrumentKey = "instrument_key"
        case marketData = "market_data"
        case optionGreeks = "option_greeks"
    }
}

struct UpstoxMarketData: Codable {
    let ltp: Double?
    let volume: Int?
    let oi: Int?
    let closePrice: Double?
    let bidPrice: Double?
    let bidQty: Int?
    let askPrice: Double?
    let askQty: Int?
    let oiDayChange: Int?
    let oiDayChangePercentage: Double?

    enum CodingKeys: String, CodingKey {
        case ltp
        case volume
        case oi
        case closePrice = "close_price"
        case bidPrice = "bid_price"
        case bidQty = "bid_qty"
        case askPrice = "ask_price"
        case askQty = "ask_qty"
        case oiDayChange = "oi_day_change"
        case oiDayChangePercentage = "oi_day_change_percentage"
    }
}

struct UpstoxGreeks: Codable {
    let delta: Double?
    let gamma: Double?
    let theta: Double?
    let vega: Double?
    let iv: Double?
}

struct UpstoxExpiryResponse: Codable {
    let status: String
    let data: [String]?
}

struct UpstoxMarketQuoteResponse: Codable {
    let status: String
    let data: [String: UpstoxQuoteData]?
}

struct UpstoxQuoteData: Codable {
    let lastPrice: Double?
    let ohlc: UpstoxOHLC?
    let netChange: Double?
    let percentageChange: Double?
    let previousClose: Double?

    enum CodingKeys: String, CodingKey {
        case lastPrice = "last_price"
        case ohlc
        case netChange = "net_change"
        case percentageChange = "percentage_change"
        case previousClose = "previous_close"
    }
}

struct UpstoxOHLC: Codable {
    let open: Double?
    let high: Double?
    let low: Double?
    let close: Double?
}

// Spot quote result with all data
struct SpotQuoteResult {
    let lastPrice: Double
    let change: Double
    let changePercent: Double
    let previousClose: Double
}

// MARK: - Historical Candle Response Models

struct UpstoxHistoricalCandleResponse: Codable {
    let status: String
    let data: UpstoxCandleData?
    let error: UpstoxErrorData?
}

struct UpstoxCandleData: Codable {
    let candles: [[CandleValue]]?
}

/// Upstox candle format: [timestamp, open, high, low, close, volume, oi]
enum CandleValue: Codable {
    case string(String)
    case double(Double)
    case int(Int)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let stringValue = try? container.decode(String.self) {
            self = .string(stringValue)
        } else if let intValue = try? container.decode(Int.self) {
            self = .int(intValue)
        } else if let doubleValue = try? container.decode(Double.self) {
            self = .double(doubleValue)
        } else {
            throw DecodingError.typeMismatch(CandleValue.self, DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "Expected String, Int, or Double"))
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value):
            try container.encode(value)
        case .double(let value):
            try container.encode(value)
        case .int(let value):
            try container.encode(value)
        }
    }

    var doubleValue: Double? {
        switch self {
        case .double(let value): return value
        case .int(let value): return Double(value)
        case .string(_): return nil
        }
    }

    var intValue: Int? {
        switch self {
        case .int(let value): return value
        case .double(let value): return Int(value)
        case .string(_): return nil
        }
    }

    var stringValue: String? {
        switch self {
        case .string(let value): return value
        default: return nil
        }
    }
}

// MARK: - Upstox API Service

@MainActor
class UpstoxAPIService: ObservableObject, DataProvider {

    // MARK: - DataProvider Protocol Properties

    var providerId: String { "upstox" }
    var displayName: String { "Upstox" }
    var iconName: String { "chart.line.uptrend.xyaxis" }
    var brandColorHex: String { "6B3FA0" }

    // MARK: - Published Properties

    @Published var isAuthenticated: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?

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

    // MARK: - Singleton

    static let shared = UpstoxAPIService()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)

        // Try to load saved token
        if let savedToken = loadTokenFromKeychain() {
            self.accessToken = savedToken
            self.isAuthenticated = true
        }
    }

    // MARK: - Token Access (for WebSocket)

    func getAccessToken() -> String? {
        return accessToken
    }

    // MARK: - Authentication

    func getAuthorizationURL() -> URL? {
        var components = URLComponents(string: UpstoxConfig.authURL)
        components?.queryItems = [
            URLQueryItem(name: "client_id", value: UpstoxConfig.apiKey),
            URLQueryItem(name: "redirect_uri", value: UpstoxConfig.redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "state", value: UUID().uuidString)
        ]
        return components?.url
    }

    /// DataProvider protocol method - requires OAuth flow
    func authenticate() async throws {
        throw UpstoxAPIError.notAuthenticated
    }

    func authenticate(withCode code: String) async throws {
        isLoading = true
        defer { isLoading = false }

        guard let url = URL(string: UpstoxConfig.tokenURL) else {
            throw UpstoxAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let bodyParams = [
            "code": code,
            "client_id": UpstoxConfig.apiKey,
            "client_secret": UpstoxConfig.apiSecret,
            "redirect_uri": UpstoxConfig.redirectURI,
            "grant_type": "authorization_code"
        ]

        let bodyString = bodyParams.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        request.httpBody = bodyString.data(using: .utf8)

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw UpstoxAPIError.invalidResponse
            }

            if httpResponse.statusCode != 200 {
                if let errorResponse = try? JSONDecoder().decode(UpstoxOptionChainResponse.self, from: data),
                   let errorMsg = errorResponse.error?.message {
                    throw UpstoxAPIError.authenticationFailed(errorMsg)
                }
                throw UpstoxAPIError.authenticationFailed("HTTP \(httpResponse.statusCode)")
            }

            let authResponse = try JSONDecoder().decode(UpstoxAuthResponse.self, from: data)
            self.accessToken = authResponse.accessToken
            self.errorMessage = nil

        } catch let error as UpstoxAPIError {
            self.errorMessage = error.localizedDescription
            throw error
        } catch {
            self.errorMessage = error.localizedDescription
            throw UpstoxAPIError.networkError(error)
        }
    }

    func logout() {
        accessToken = nil
        deleteTokenFromKeychain()
        isAuthenticated = false
    }

    // MARK: - Market Data

    func fetchOptionChain(index: TradingIndex = .nifty50, expiry: String) async throws -> [OptionChainRow] {
        guard let token = accessToken else {
            throw UpstoxAPIError.notAuthenticated
        }

        // Use the instrument key from the index
        let instrumentKey = index.instrumentKey
        guard let encodedInstrument = instrumentKey.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw UpstoxAPIError.invalidURL
        }

        // Try the alternative endpoint format
        let urlString = "\(UpstoxConfig.baseURL)/option/chain?instrument_key=\(encodedInstrument)&expiry_date=\(expiry)"
        print("🔍 [API] Option chain URL: \(urlString)")

        guard let url = URL(string: urlString) else {
            throw UpstoxAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)

            // Debug: Print raw response
            if let jsonString = String(data: data, encoding: .utf8) {
                print("🔍 [API] Option chain response: \(jsonString.prefix(1000))...")
            }

            guard let httpResponse = response as? HTTPURLResponse else {
                throw UpstoxAPIError.invalidResponse
            }

            print("🔍 [API] Option chain status: \(httpResponse.statusCode)")

            if httpResponse.statusCode == 401 {
                logout()
                throw UpstoxAPIError.tokenExpired
            }

            if httpResponse.statusCode != 200 {
                if let errorResponse = try? JSONDecoder().decode(UpstoxOptionChainResponse.self, from: data),
                   let errorMsg = errorResponse.error?.message {
                    throw UpstoxAPIError.apiError(errorMsg)
                }
                throw UpstoxAPIError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let optionChainResponse = try JSONDecoder().decode(UpstoxOptionChainResponse.self, from: data)

            guard let optionData = optionChainResponse.data else {
                if let errorMsg = optionChainResponse.error?.message {
                    throw UpstoxAPIError.apiError(errorMsg)
                }
                return []
            }

            print("✅ [API] Got \(optionData.count) option chain items")
            return parseOptionChainData(optionData, expiry: expiry)

        } catch let error as UpstoxAPIError {
            throw error
        } catch {
            print("🔍 [API] Option chain decode error: \(error)")
            throw UpstoxAPIError.networkError(error)
        }
    }

    func fetchExpiryDates(index: TradingIndex = .nifty50) async throws -> [ExpiryDate] {
        // Each index has different expiry day
        // NIFTY - Thursday, BANKNIFTY - Wednesday, FINNIFTY - Tuesday, etc.
        let calendar = Calendar.current
        var expiries: [ExpiryDate] = []
        var currentDate = Date()

        // Get the expiry day for this index (1=Sunday, 2=Monday, ..., 7=Saturday)
        let targetWeekday = index.expiryDayOfWeek + 1 // Convert to Calendar weekday format

        // Find next 4 expiry dates
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

        print("📅 [API] Generated \(index.shortName) expiry dates: \(expiries.map { $0.displayString })")
        return expiries
    }

    func fetchSpotPrice(index: TradingIndex = .nifty50) async throws -> SpotQuoteResult {
        guard let token = accessToken else {
            throw UpstoxAPIError.notAuthenticated
        }

        let instrumentKey = index.instrumentKey
        guard let encodedInstrument = instrumentKey.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw UpstoxAPIError.invalidURL
        }

        // Use full quote endpoint for change data
        let urlString = "\(UpstoxConfig.baseURL)/market-quote/quotes?instrument_key=\(encodedInstrument)"
        print("🔍 [API] Spot quote URL: \(urlString)")

        guard let url = URL(string: urlString) else {
            throw UpstoxAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)

            // Debug: Print raw response
            if let jsonString = String(data: data, encoding: .utf8) {
                print("🔍 [API] Spot quote response: \(jsonString.prefix(800))...")
            }

            guard let httpResponse = response as? HTTPURLResponse else {
                throw UpstoxAPIError.invalidResponse
            }

            if httpResponse.statusCode == 401 {
                logout()
                throw UpstoxAPIError.tokenExpired
            }

            if httpResponse.statusCode != 200 {
                throw UpstoxAPIError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let quoteResponse = try JSONDecoder().decode(UpstoxMarketQuoteResponse.self, from: data)

            if let quoteData = quoteResponse.data?.values.first,
               let ltp = quoteData.lastPrice {
                let change = quoteData.netChange ?? 0
                let changePercent = quoteData.percentageChange ?? 0
                let prevClose = quoteData.ohlc?.close ?? (ltp - change)

                print("📈 [API] LTP: \(ltp), Change: \(change), %: \(changePercent)")

                return SpotQuoteResult(
                    lastPrice: ltp,
                    change: change,
                    changePercent: changePercent,
                    previousClose: prevClose
                )
            }

            throw UpstoxAPIError.invalidResponse

        } catch let error as UpstoxAPIError {
            throw error
        } catch {
            print("🔍 [API] Quote decode error: \(error)")
            throw UpstoxAPIError.networkError(error)
        }
    }

    /// Fetch India VIX value
    func fetchIndiaVix() async throws -> (value: Double, change: Double?)? {
        guard let token = accessToken else {
            throw UpstoxAPIError.notAuthenticated
        }

        let instrumentKey = UpstoxConfig.indiaVixKey
        guard let encodedInstrument = instrumentKey.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw UpstoxAPIError.invalidURL
        }

        let urlString = "\(UpstoxConfig.baseURL)/market-quote/quotes?instrument_key=\(encodedInstrument)"

        guard let url = URL(string: urlString) else {
            throw UpstoxAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw UpstoxAPIError.invalidResponse
            }

            if httpResponse.statusCode == 401 {
                logout()
                throw UpstoxAPIError.tokenExpired
            }

            if httpResponse.statusCode != 200 {
                throw UpstoxAPIError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let quoteResponse = try JSONDecoder().decode(UpstoxMarketQuoteResponse.self, from: data)

            if let quoteData = quoteResponse.data?.values.first,
               let ltp = quoteData.lastPrice {
                return (value: ltp, change: quoteData.percentageChange)
            }

            return nil

        } catch let error as UpstoxAPIError {
            throw error
        } catch {
            return nil
        }
    }

    // MARK: - Historical Candle Data

    /// Fetch historical candle data for chart display
    func fetchHistoricalCandles(index: TradingIndex = .nifty50, timeFrame: ChartTimeFrame) async throws -> [OHLCData] {
        guard let token = accessToken else {
            throw UpstoxAPIError.notAuthenticated
        }

        let instrumentKey = index.instrumentKey
        // Use urlPathAllowed but also encode pipe | character which Upstox requires
        var allowedChars = CharacterSet.urlPathAllowed
        allowedChars.remove(charactersIn: "|")  // Ensure pipe is encoded as %7C
        guard let encodedInstrument = instrumentKey.addingPercentEncoding(withAllowedCharacters: allowedChars) else {
            throw UpstoxAPIError.invalidURL
        }

        let interval = timeFrame.upstoxInterval
        var urlString: String

        if timeFrame.isIntraday {
            // Intraday API: /v2/historical-candle/intraday/{instrument_key}/{interval}
            urlString = "\(UpstoxConfig.baseURL)/historical-candle/intraday/\(encodedInstrument)/\(interval)"
        } else {
            // Historical API: /v2/historical-candle/{instrument_key}/{interval}/{to_date}/{from_date}
            let dateFormatter = DateFormatter()
            dateFormatter.dateFormat = "yyyy-MM-dd"
            let toDate = dateFormatter.string(from: Date())
            let fromDate = dateFormatter.string(from: Calendar.current.date(byAdding: .day, value: -timeFrame.historicalDays, to: Date()) ?? Date())
            urlString = "\(UpstoxConfig.baseURL)/historical-candle/\(encodedInstrument)/\(interval)/\(toDate)/\(fromDate)"
        }

        print("📊 [API] Historical candle URL: \(urlString)")

        guard let url = URL(string: urlString) else {
            throw UpstoxAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)

            // Debug: Print raw response
            if let jsonString = String(data: data, encoding: .utf8) {
                print("📊 [API] Historical candle response: \(jsonString.prefix(500))...")
            }

            guard let httpResponse = response as? HTTPURLResponse else {
                throw UpstoxAPIError.invalidResponse
            }

            print("📊 [API] Historical candle status: \(httpResponse.statusCode)")

            if httpResponse.statusCode == 401 {
                logout()
                throw UpstoxAPIError.tokenExpired
            }

            if httpResponse.statusCode != 200 {
                if let errorResponse = try? JSONDecoder().decode(UpstoxHistoricalCandleResponse.self, from: data),
                   let errorMsg = errorResponse.error?.message {
                    throw UpstoxAPIError.apiError(errorMsg)
                }
                throw UpstoxAPIError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let candleResponse = try JSONDecoder().decode(UpstoxHistoricalCandleResponse.self, from: data)

            guard let candleData = candleResponse.data?.candles else {
                if let errorMsg = candleResponse.error?.message {
                    throw UpstoxAPIError.apiError(errorMsg)
                }
                return []
            }

            print("✅ [API] Got \(candleData.count) historical candles")
            return parseHistoricalCandles(candleData, timeFrame: timeFrame)

        } catch let error as UpstoxAPIError {
            throw error
        } catch {
            print("📊 [API] Historical candle decode error: \(error)")
            throw UpstoxAPIError.networkError(error)
        }
    }

    /// Fetch historical candle data for OPTIONS (CE/PE)
    /// Options have their own instrument keys and historical data IS available from Upstox
    func fetchOptionHistoricalCandles(instrumentKey: String, timeFrame: ChartTimeFrame) async throws -> [OHLCData] {
        guard let token = accessToken else {
            throw UpstoxAPIError.notAuthenticated
        }

        // Use urlPathAllowed but also encode pipe | character
        var allowedChars = CharacterSet.urlPathAllowed
        allowedChars.remove(charactersIn: "|")
        guard let encodedInstrument = instrumentKey.addingPercentEncoding(withAllowedCharacters: allowedChars) else {
            throw UpstoxAPIError.invalidURL
        }

        let interval = timeFrame.upstoxInterval
        var urlString: String

        if timeFrame.isIntraday {
            urlString = "\(UpstoxConfig.baseURL)/historical-candle/intraday/\(encodedInstrument)/\(interval)"
        } else {
            let dateFormatter = DateFormatter()
            dateFormatter.dateFormat = "yyyy-MM-dd"
            let toDate = dateFormatter.string(from: Date())
            let fromDate = dateFormatter.string(from: Calendar.current.date(byAdding: .day, value: -timeFrame.historicalDays, to: Date()) ?? Date())
            urlString = "\(UpstoxConfig.baseURL)/historical-candle/\(encodedInstrument)/\(interval)/\(toDate)/\(fromDate)"
        }

        print("📊 [API] Option historical candle URL: \(urlString)")

        guard let url = URL(string: urlString) else {
            throw UpstoxAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw UpstoxAPIError.invalidResponse
            }

            print("📊 [API] Option candle status: \(httpResponse.statusCode)")

            if httpResponse.statusCode == 401 {
                logout()
                throw UpstoxAPIError.tokenExpired
            }

            if httpResponse.statusCode != 200 {
                if let jsonString = String(data: data, encoding: .utf8) {
                    print("📊 [API] Option candle error: \(jsonString.prefix(300))")
                }
                throw UpstoxAPIError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let candleResponse = try JSONDecoder().decode(UpstoxHistoricalCandleResponse.self, from: data)

            guard let candleData = candleResponse.data?.candles else {
                return []
            }

            print("✅ [API] Got \(candleData.count) option candles")
            return parseHistoricalCandles(candleData, timeFrame: timeFrame)

        } catch let error as UpstoxAPIError {
            throw error
        } catch {
            print("📊 [API] Option candle error: \(error)")
            throw UpstoxAPIError.networkError(error)
        }
    }

    /// Parse Upstox candle data into OHLCData array
    private func parseHistoricalCandles(_ candles: [[CandleValue]], timeFrame: ChartTimeFrame) -> [OHLCData] {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        var ohlcData: [OHLCData] = []

        // Upstox returns newest first, we want oldest first for charts
        for candleArray in candles.reversed() {
            guard candleArray.count >= 6 else { continue }

            // Format: [timestamp, open, high, low, close, volume, oi]
            guard let timestampStr = candleArray[0].stringValue,
                  let open = candleArray[1].doubleValue,
                  let high = candleArray[2].doubleValue,
                  let low = candleArray[3].doubleValue,
                  let close = candleArray[4].doubleValue else {
                continue
            }

            // Parse timestamp - handle both ISO8601 and other formats
            var timestamp: Date?
            timestamp = dateFormatter.date(from: timestampStr)

            // Try alternate format if ISO8601 fails
            if timestamp == nil {
                let altFormatter = DateFormatter()
                altFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ"
                timestamp = altFormatter.date(from: timestampStr)
            }

            // Try simple date format for daily/weekly
            if timestamp == nil {
                let simpleFormatter = DateFormatter()
                simpleFormatter.dateFormat = "yyyy-MM-dd"
                timestamp = simpleFormatter.date(from: timestampStr)
            }

            guard let date = timestamp else { continue }

            let volume = candleArray[5].intValue ?? 0

            ohlcData.append(OHLCData(
                timestamp: date,
                open: open,
                high: high,
                low: low,
                close: close,
                volume: volume
            ))
        }

        // For 4H timeframe, aggregate 60min candles into 4-hour candles
        if timeFrame == .fourHour && !ohlcData.isEmpty {
            ohlcData = aggregateCandles(ohlcData, periodMinutes: 240)
        }

        // Limit candle count based on timeframe
        let maxCount = timeFrame.candleCount
        if ohlcData.count > maxCount {
            ohlcData = Array(ohlcData.suffix(maxCount))
        }

        return ohlcData
    }

    /// Aggregate candles into larger timeframes
    private func aggregateCandles(_ candles: [OHLCData], periodMinutes: Int) -> [OHLCData] {
        guard !candles.isEmpty else { return [] }

        var aggregated: [OHLCData] = []
        var currentGroup: [OHLCData] = []
        let periodSeconds = periodMinutes * 60

        for candle in candles {
            if currentGroup.isEmpty {
                currentGroup.append(candle)
            } else if let first = currentGroup.first {
                let timeDiff = candle.timestamp.timeIntervalSince(first.timestamp)
                if timeDiff < Double(periodSeconds) {
                    currentGroup.append(candle)
                } else {
                    // Finalize current group
                    if let aggregatedCandle = aggregateGroup(currentGroup) {
                        aggregated.append(aggregatedCandle)
                    }
                    currentGroup = [candle]
                }
            }
        }

        // Don't forget the last group
        if let aggregatedCandle = aggregateGroup(currentGroup) {
            aggregated.append(aggregatedCandle)
        }

        return aggregated
    }

    private func aggregateGroup(_ group: [OHLCData]) -> OHLCData? {
        guard let first = group.first, let last = group.last else { return nil }

        let high = group.map { $0.high }.max() ?? first.high
        let low = group.map { $0.low }.min() ?? first.low
        let totalVolume = group.reduce(0) { $0 + $1.volume }

        return OHLCData(
            timestamp: first.timestamp,
            open: first.open,
            high: high,
            low: low,
            close: last.close,
            volume: totalVolume
        )
    }

    // MARK: - Data Parsing

    private func parseOptionChainData(_ data: [UpstoxOptionData], expiry: String) -> [OptionChainRow] {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let expiryDate = dateFormatter.date(from: expiry) ?? Date()

        var rows: [OptionChainRow] = []

        for item in data {
            let spotPrice = item.underlying_spot_price

            var callOption: OptionData? = nil
            var putOption: OptionData? = nil

            if let call = item.callOptions, let marketData = call.marketData {
                callOption = OptionData(
                    strikePrice: item.strikePrice,
                    optionType: .call,
                    expiryDate: expiryDate,
                    lastTradedPrice: marketData.ltp ?? 0,
                    openInterest: marketData.oi ?? 0,
                    changeInOI: marketData.oiDayChange ?? 0,
                    impliedVolatility: (call.optionGreeks?.iv ?? 0) / 100.0,
                    bidPrice: marketData.bidPrice ?? 0,
                    askPrice: marketData.askPrice ?? 0,
                    bidQty: marketData.bidQty ?? 0,
                    askQty: marketData.askQty ?? 0,
                    volume: marketData.volume ?? 0,
                    underlyingValue: spotPrice,
                    delta: call.optionGreeks?.delta,
                    gamma: call.optionGreeks?.gamma,
                    theta: call.optionGreeks?.theta,
                    vega: call.optionGreeks?.vega,
                    instrumentKey: call.instrumentKey
                )
            }

            if let put = item.putOptions, let marketData = put.marketData {
                putOption = OptionData(
                    strikePrice: item.strikePrice,
                    optionType: .put,
                    expiryDate: expiryDate,
                    lastTradedPrice: marketData.ltp ?? 0,
                    openInterest: marketData.oi ?? 0,
                    changeInOI: marketData.oiDayChange ?? 0,
                    impliedVolatility: (put.optionGreeks?.iv ?? 0) / 100.0,
                    bidPrice: marketData.bidPrice ?? 0,
                    askPrice: marketData.askPrice ?? 0,
                    bidQty: marketData.bidQty ?? 0,
                    askQty: marketData.askQty ?? 0,
                    volume: marketData.volume ?? 0,
                    underlyingValue: spotPrice,
                    delta: put.optionGreeks?.delta,
                    gamma: put.optionGreeks?.gamma,
                    theta: put.optionGreeks?.theta,
                    vega: put.optionGreeks?.vega,
                    instrumentKey: put.instrumentKey
                )
            }

            rows.append(OptionChainRow(
                strikePrice: item.strikePrice,
                callOption: callOption,
                putOption: putOption
            ))
        }

        return rows.sorted { $0.strikePrice < $1.strikePrice }
    }

    // MARK: - Keychain Management

    private let keychainKey = "com.optix.upstox.accesstoken"

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
}

// MARK: - Web Authentication Helper

@MainActor
class UpstoxAuthManager: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {

    @Published var isAuthenticating = false

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = scene.windows.first else {
            return ASPresentationAnchor()
        }
        return window
    }

    func startAuthentication() async throws {
        guard let authURL = UpstoxAPIService.shared.getAuthorizationURL() else {
            throw UpstoxAPIError.invalidURL
        }

        isAuthenticating = true
        defer { isAuthenticating = false }

        return try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authURL,
                callbackURLScheme: UpstoxConfig.callbackURLScheme
            ) { callbackURL, error in
                if let error = error {
                    if (error as NSError).code == ASWebAuthenticationSessionError.canceledLogin.rawValue {
                        continuation.resume(throwing: UpstoxAPIError.authenticationFailed("Login cancelled"))
                    } else {
                        continuation.resume(throwing: UpstoxAPIError.authenticationFailed(error.localizedDescription))
                    }
                    return
                }

                guard let url = callbackURL,
                      let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
                      let code = components.queryItems?.first(where: { $0.name == "code" })?.value else {
                    continuation.resume(throwing: UpstoxAPIError.authenticationFailed("No authorization code received"))
                    return
                }

                Task { @MainActor in
                    do {
                        try await UpstoxAPIService.shared.authenticate(withCode: code)
                        continuation.resume()
                    } catch {
                        continuation.resume(throwing: error)
                    }
                }
            }

            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false

            if !session.start() {
                continuation.resume(throwing: UpstoxAPIError.authenticationFailed("Failed to start authentication session"))
            }
        }
    }
}
