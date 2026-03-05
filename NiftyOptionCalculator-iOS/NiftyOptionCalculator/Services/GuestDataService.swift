import Foundation
import os.log

private let logger = Logger(subsystem: "com.optix.app", category: "GuestDataService")

/// Guest Mode Data Service
/// Fetches live market data from the backend without requiring any broker login
/// Perfect for new learners who don't have a demat account
@MainActor
class GuestDataService: ObservableObject, DataProvider {

    // MARK: - DataProvider Protocol Properties

    var providerId: String { "guest" }
    var displayName: String { "Guest Mode" }
    var iconName: String { "person.circle.fill" }
    var brandColorHex: String { "34C759" }  // Green

    // Always "authenticated" - no login required
    var isAuthenticated: Bool { true }

    // MARK: - Published Properties

    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var lastFetchTime: Date?

    // MARK: - Private Properties

    private let session: URLSession
    private var cache: [String: CachedData] = [:]
    private let cacheTTL: TimeInterval = 30  // 30 seconds cache

    // Backend API URL
    private var baseURL: String {
        return AppEnvironment.current.marketBaseURL
    }

    // MARK: - Cache Structure

    private struct CachedData {
        let data: Any
        let timestamp: Date

        var isValid: Bool {
            Date().timeIntervalSince(timestamp) < 30
        }
    }

    // MARK: - Singleton

    static let shared = GuestDataService()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
    }

    // MARK: - DataProvider Protocol Methods

    func authenticate() async throws {
        // No authentication needed for guest mode
    }

    func logout() {
        // No logout needed for guest mode
        cache.removeAll()
    }

    func fetchExpiryDates(index: TradingIndex) async throws -> [ExpiryDate] {
        let symbol = mapIndexToSymbol(index)
        let cacheKey = "expiry_\(symbol)"

        // Check cache
        if let cached = cache[cacheKey], cached.isValid,
           let dates = cached.data as? [ExpiryDate] {
            print("📡 [Guest] Using cached expiry dates for \(symbol)")
            return dates
        }

        let url = URL(string: "\(baseURL)/expiries/\(symbol)")!
        logger.info("📡 Fetching expiry dates from: \(url.absoluteString)")

        let (data, response) = try await session.data(from: url)
        print("📡 [Guest] Got expiry response: \(String(data: data, encoding: .utf8) ?? "nil")")

        guard let httpResponse = response as? HTTPURLResponse else {
            print("❌ [Guest] Not an HTTP response")
            throw ProviderError.apiError("Not an HTTP response")
        }

        print("📡 [Guest] HTTP status: \(httpResponse.statusCode)")

        guard httpResponse.statusCode == 200 else {
            print("❌ [Guest] HTTP error: \(httpResponse.statusCode)")
            throw ProviderError.apiError("Failed to fetch expiry dates: HTTP \(httpResponse.statusCode)")
        }

        struct ExpiryResponse: Codable {
            let symbol: String
            let expiryDates: [String]
        }

        do {
            let expiryResponse = try JSONDecoder().decode(ExpiryResponse.self, from: data)
            print("📡 [Guest] Decoded \(expiryResponse.expiryDates.count) expiry dates")
            let expiries = expiryResponse.expiryDates.compactMap { ExpiryDate(dateString: $0) }
            print("📡 [Guest] Converted to \(expiries.count) ExpiryDate objects")

            // Cache the result
            cache[cacheKey] = CachedData(data: expiries, timestamp: Date())

            return expiries
        } catch {
            print("❌ [Guest] JSON decode error: \(error)")
            throw error
        }
    }

    func fetchSpotPrice(index: TradingIndex) async throws -> SpotQuoteResult {
        let symbol = mapIndexToSymbol(index)
        let cacheKey = "spot_\(symbol)"

        // Check cache
        if let cached = cache[cacheKey], cached.isValid,
           let spot = cached.data as? SpotQuoteResult {
            print("📡 [Guest] Using cached spot price for \(symbol)")
            return spot
        }

        let url = URL(string: "\(baseURL)/spot/\(symbol)")!
        print("📡 [Guest] Fetching spot price from: \(url)")
        let (data, response) = try await session.data(from: url)
        print("📡 [Guest] Got response: \(String(data: data, encoding: .utf8) ?? "nil")")

        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw ProviderError.apiError("Failed to fetch spot price")
        }

        struct SpotResponse: Codable {
            let symbol: String
            let name: String
            let lastPrice: Double
            let change: Double
            let pChange: Double
            let previousClose: Double
            let isDemo: Bool?
        }

        let spotResponse = try JSONDecoder().decode(SpotResponse.self, from: data)
        if spotResponse.isDemo == true {
            throw ProviderError.apiError("Live data unavailable")
        }
        let result = SpotQuoteResult(
            lastPrice: spotResponse.lastPrice,
            change: spotResponse.change,
            changePercent: spotResponse.pChange,
            previousClose: spotResponse.previousClose
        )

        // Cache the result
        cache[cacheKey] = CachedData(data: result, timestamp: Date())
        lastFetchTime = Date()

        return result
    }

    /// Result of fetching option chain, includes rows and full-chain totals
    struct OptionChainResult {
        let rows: [OptionChainRow]
        let fullChainCallOI: Int?
        let fullChainPutOI: Int?
    }

    func fetchOptionChain(index: TradingIndex, expiry: String) async throws -> [OptionChainRow] {
        let result = try await fetchOptionChainWithTotals(index: index, expiry: expiry)
        return result.rows
    }

    func fetchOptionChainWithTotals(index: TradingIndex, expiry: String) async throws -> OptionChainResult {
        let symbol = mapIndexToSymbol(index)
        let cacheKey = "chain_\(symbol)_\(expiry)"

        isLoading = true
        defer { isLoading = false }

        // Check cache
        if let cached = cache[cacheKey], cached.isValid,
           let result = cached.data as? OptionChainResult {
            return result
        }

        // Build URL with query parameters
        var components = URLComponents(string: "\(baseURL)/option-chain/\(symbol)")!
        components.queryItems = [
            URLQueryItem(name: "expiry", value: expiry)
        ]

        print("📡 [Guest] Fetching option chain from: \(components.url!)")
        let (data, response) = try await session.data(from: components.url!)
        print("📡 [Guest] Got option chain response length: \(data.count) bytes")

        guard let httpResponse = response as? HTTPURLResponse else {
            print("❌ [Guest] Not an HTTP response")
            throw ProviderError.apiError("Failed to fetch option chain: not HTTP response")
        }

        print("📡 [Guest] HTTP status: \(httpResponse.statusCode)")

        guard httpResponse.statusCode == 200 else {
            print("❌ [Guest] HTTP error: \(httpResponse.statusCode)")
            throw ProviderError.apiError("Failed to fetch option chain: HTTP \(httpResponse.statusCode)")
        }

        do {
            let chainResponse = try JSONDecoder().decode(GuestOptionChainResponse.self, from: data)
            if chainResponse.isDemo == true {
                throw ProviderError.apiError("Live data unavailable")
            }
            print("📡 [Guest] Decoded option chain: underlying=\(chainResponse.underlyingValue), strikes=\(chainResponse.data.count)")
            let rows = convertToOptionChainRows(chainResponse)

            let result = OptionChainResult(
                rows: rows,
                fullChainCallOI: chainResponse.totals?.CE?.totalOI.map { Int($0) },
                fullChainPutOI: chainResponse.totals?.PE?.totalOI.map { Int($0) }
            )

            // Cache the result
            cache[cacheKey] = CachedData(data: result, timestamp: Date())
            lastFetchTime = Date()

            return result
        } catch {
            print("❌ [Guest] JSON decode error: \(error)")
            throw error
        }
    }

    // MARK: - India VIX

    struct IndiaVixResponse: Decodable {
        let symbol: String?
        let value: Double
        let change: Double?
        let changePercent: Double?
        let high: Double?
        let low: Double?
        let open: Double?
        let previousClose: Double?

        enum CodingKeys: String, CodingKey {
            case symbol
            case value = "last_price"
            case change
            case changePercent = "change_percent"
            case high
            case low
            case open
            case previousClose = "previous_close"
        }
    }

    /// Fetch India VIX from backend market API (no broker login required).
    func fetchIndiaVix() async throws -> Double? {
        let cacheKey = "india_vix"

        if let cached = cache[cacheKey], cached.isValid,
           let vix = cached.data as? Double {
            return vix
        }

        guard let url = URL(string: "\(baseURL)/india-vix") else {
            throw ProviderError.apiError("Invalid VIX URL")
        }

        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ProviderError.apiError("Invalid VIX response")
        }

        guard httpResponse.statusCode == 200 else {
            throw ProviderError.apiError("Failed to fetch VIX: HTTP \(httpResponse.statusCode)")
        }

        let decoded = try JSONDecoder().decode(IndiaVixResponse.self, from: data)
        let vixValue = decoded.value

        cache[cacheKey] = CachedData(data: vixValue, timestamp: Date())
        lastFetchTime = Date()

        return vixValue
    }

    // MARK: - Helper Methods

    private func mapIndexToSymbol(_ index: TradingIndex) -> String {
        switch index {
        case .nifty50: return "NIFTY"
        case .bankNifty: return "BANKNIFTY"
        case .niftyFinService: return "FINNIFTY"
        case .midcapSelect: return "MIDCPNIFTY"
        case .sensex: return "SENSEX"
        case .bankex: return "SENSEX"  // Fallback
        }
    }

    private func convertToOptionChainRows(_ response: GuestOptionChainResponse) -> [OptionChainRow] {
        var rows: [OptionChainRow] = []

        // Parse expiry date
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd-MMM-yyyy"

        for item in response.data {
            let expiryDate = dateFormatter.date(from: item.expiryDate) ?? Date()

            let callOption: OptionData? = item.CE.map { ce in
                OptionData(
                    strikePrice: item.strikePrice,
                    optionType: .call,
                    expiryDate: expiryDate,
                    lastTradedPrice: ce.lastPrice,
                    openInterest: ce.openInterest,
                    changeInOI: ce.changeinOpenInterest,
                    impliedVolatility: ce.impliedVolatility / 100.0, // Backend returns percentage, convert to decimal
                    bidPrice: ce.bidprice,
                    askPrice: ce.askPrice,
                    bidQty: ce.bidQty,
                    askQty: ce.askQty,
                    volume: ce.totalTradedVolume,
                    underlyingValue: response.underlyingValue
                )
            }

            let putOption: OptionData? = item.PE.map { pe in
                OptionData(
                    strikePrice: item.strikePrice,
                    optionType: .put,
                    expiryDate: expiryDate,
                    lastTradedPrice: pe.lastPrice,
                    openInterest: pe.openInterest,
                    changeInOI: pe.changeinOpenInterest,
                    impliedVolatility: pe.impliedVolatility / 100.0, // Backend returns percentage, convert to decimal
                    bidPrice: pe.bidprice,
                    askPrice: pe.askPrice,
                    bidQty: pe.bidQty,
                    askQty: pe.askQty,
                    volume: pe.totalTradedVolume,
                    underlyingValue: response.underlyingValue
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

    /// Clear all cached data
    func clearCache() {
        cache.removeAll()
    }
}

// MARK: - Response Models

private struct GuestOptionChainResponse: Codable {
    let symbol: String
    let name: String
    let lotSize: Int
    let underlyingValue: Double
    let atmStrike: Double
    let expiryDates: [String]
    let strikePrices: [Double]
    let timestamp: String
    let data: [GuestStrikeData]
    let isDemo: Bool?
    let totals: GuestChainTotals?
}

private struct GuestChainTotals: Codable {
    let CE: GuestSideTotals?
    let PE: GuestSideTotals?
}

private struct GuestSideTotals: Codable {
    let totalOI: Double?
    let totalVolume: Double?
}

private struct GuestStrikeData: Codable {
    let strikePrice: Double
    let expiryDate: String
    let CE: GuestOptionData?
    let PE: GuestOptionData?
}

private struct GuestOptionData: Codable {
    let openInterest: Int
    let changeinOpenInterest: Int
    let totalTradedVolume: Int
    let impliedVolatility: Double
    let lastPrice: Double
    let change: Double
    let pChange: Double
    let bidQty: Int
    let bidprice: Double
    let askQty: Int
    let askPrice: Double
}
