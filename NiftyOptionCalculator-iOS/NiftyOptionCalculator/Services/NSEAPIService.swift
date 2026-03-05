import Foundation

// MARK: - NSE API Error Types

enum NSEAPIError: Error, LocalizedError {
    case invalidURL
    case networkError(Error)
    case invalidResponse
    case decodingError(Error)
    case rateLimited
    case serverError(Int)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid API URL"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .invalidResponse:
            return "Invalid response from server"
        case .decodingError(let error):
            return "Data parsing error: \(error.localizedDescription)"
        case .rateLimited:
            return "Rate limited. Please wait before retrying."
        case .serverError(let code):
            return "Server error: \(code)"
        }
    }
}

// MARK: - NSE API Service

actor NSEAPIService {

    // MARK: - Constants

    private let baseURL = "https://www.nseindia.com"
    private let optionChainEndpoint = "/api/option-chain-indices"
    private let quotesEndpoint = "/api/quote-derivative"

    private let userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // MARK: - Cache

    private var cachedData: NSEOptionChainResponse?
    private var cacheTimestamp: Date?
    private let cacheValiditySeconds: TimeInterval = 30

    // MARK: - Session Management

    private var cookies: [HTTPCookie] = []
    private var lastRequestTime: Date?
    private let minRequestInterval: TimeInterval = 1.0

    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.default
        config.httpCookieAcceptPolicy = .always
        config.httpCookieStorage = HTTPCookieStorage.shared
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        return URLSession(configuration: config)
    }()

    // MARK: - Singleton

    static let shared = NSEAPIService()

    private init() {}

    // MARK: - Public Methods

    /// Fetch Nifty option chain data
    func fetchOptionChain(symbol: String = "NIFTY", forceRefresh: Bool = false) async throws -> NSEOptionChainResponse {
        // Check cache first
        if !forceRefresh, let cached = cachedData, let timestamp = cacheTimestamp {
            if Date().timeIntervalSince(timestamp) < cacheValiditySeconds {
                return cached
            }
        }

        // Rate limiting
        if let lastRequest = lastRequestTime {
            let elapsed = Date().timeIntervalSince(lastRequest)
            if elapsed < minRequestInterval {
                try await Task.sleep(nanoseconds: UInt64((minRequestInterval - elapsed) * 1_000_000_000))
            }
        }

        // First, visit the main page to get cookies
        try await warmupSession()

        // Now fetch the option chain
        let response = try await fetchOptionChainData(symbol: symbol)

        // Update cache
        cachedData = response
        cacheTimestamp = Date()
        lastRequestTime = Date()

        return response
    }

    /// Fetch option chain and convert to app models
    func fetchOptionChainData(for symbol: String = "NIFTY", expiry: String? = nil) async throws -> [OptionChainRow] {
        let response = try await fetchOptionChain(symbol: symbol)
        return parseOptionChainResponse(response, expiry: expiry)
    }

    /// Get available expiry dates
    func fetchExpiryDates(symbol: String = "NIFTY") async throws -> [ExpiryDate] {
        let response = try await fetchOptionChain(symbol: symbol)
        return response.records.expiryDates.map { ExpiryDate(dateString: $0) }
    }

    /// Get current underlying value
    func fetchUnderlyingValue(symbol: String = "NIFTY") async throws -> Double {
        let response = try await fetchOptionChain(symbol: symbol)
        return response.records.underlyingValue
    }

    // MARK: - Private Methods

    private func warmupSession() async throws {
        guard let url = URL(string: baseURL) else {
            throw NSEAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")

        do {
            let (_, response) = try await session.data(for: request)

            if let httpResponse = response as? HTTPURLResponse {
                if let headerFields = httpResponse.allHeaderFields as? [String: String],
                   let url = httpResponse.url {
                    let newCookies = HTTPCookie.cookies(withResponseHeaderFields: headerFields, for: url)
                    cookies.append(contentsOf: newCookies)
                }
            }
        } catch {
            // Warmup failure is not critical, continue anyway
            print("Warmup warning: \(error.localizedDescription)")
        }
    }

    private func fetchOptionChainData(symbol: String) async throws -> NSEOptionChainResponse {
        guard let url = URL(string: "\(baseURL)\(optionChainEndpoint)?symbol=\(symbol)") else {
            throw NSEAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        // Set headers to mimic browser
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        request.setValue(baseURL, forHTTPHeaderField: "Referer")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw NSEAPIError.invalidResponse
            }

            switch httpResponse.statusCode {
            case 200:
                break
            case 429:
                throw NSEAPIError.rateLimited
            case 400...499:
                throw NSEAPIError.invalidResponse
            case 500...599:
                throw NSEAPIError.serverError(httpResponse.statusCode)
            default:
                throw NSEAPIError.serverError(httpResponse.statusCode)
            }

            let decoder = JSONDecoder()
            do {
                return try decoder.decode(NSEOptionChainResponse.self, from: data)
            } catch {
                throw NSEAPIError.decodingError(error)
            }

        } catch let error as NSEAPIError {
            throw error
        } catch {
            throw NSEAPIError.networkError(error)
        }
    }

    private func parseOptionChainResponse(_ response: NSEOptionChainResponse, expiry: String?) -> [OptionChainRow] {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd-MMM-yyyy"
        dateFormatter.locale = Locale(identifier: "en_IN")

        // Filter by expiry if provided
        let filteredData: [NSEOptionChainResponse.DataItem]
        if let expiry = expiry {
            filteredData = response.records.data.filter { $0.expiryDate == expiry }
        } else {
            // Use nearest expiry by default
            let nearestExpiry = response.records.expiryDates.first ?? ""
            filteredData = response.records.data.filter { $0.expiryDate == nearestExpiry }
        }

        // Group by strike price
        var strikeMap: [Double: (call: OptionData?, put: OptionData?)] = [:]

        for item in filteredData {
            let strikePrice = item.strikePrice
            let expiryDate = dateFormatter.date(from: item.expiryDate) ?? Date()

            var entry = strikeMap[strikePrice] ?? (nil, nil)

            if let ce = item.ce {
                entry.call = OptionData(
                    strikePrice: ce.strikePrice,
                    optionType: .call,
                    expiryDate: expiryDate,
                    lastTradedPrice: ce.lastPrice,
                    openInterest: ce.openInterest,
                    changeInOI: ce.changeinOpenInterest,
                    impliedVolatility: ce.impliedVolatility / 100.0, // Convert percentage to decimal
                    bidPrice: ce.bidprice,
                    askPrice: ce.askPrice,
                    bidQty: ce.bidQty,
                    askQty: ce.askQty,
                    volume: ce.totalTradedVolume,
                    underlyingValue: ce.underlyingValue
                )
            }

            if let pe = item.pe {
                entry.put = OptionData(
                    strikePrice: pe.strikePrice,
                    optionType: .put,
                    expiryDate: expiryDate,
                    lastTradedPrice: pe.lastPrice,
                    openInterest: pe.openInterest,
                    changeInOI: pe.changeinOpenInterest,
                    impliedVolatility: pe.impliedVolatility / 100.0,
                    bidPrice: pe.bidprice,
                    askPrice: pe.askPrice,
                    bidQty: pe.bidQty,
                    askQty: pe.askQty,
                    volume: pe.totalTradedVolume,
                    underlyingValue: pe.underlyingValue
                )
            }

            strikeMap[strikePrice] = entry
        }

        // Convert to rows and sort by strike price
        return strikeMap.map { strike, options in
            OptionChainRow(strikePrice: strike, callOption: options.call, putOption: options.put)
        }.sorted { $0.strikePrice < $1.strikePrice }
    }

    // MARK: - Mock Data for Testing

    static func generateMockData(spotPrice: Double = 25000, numStrikes: Int = 21, strikeInterval: Double = 50.0, expiryDate: Date? = nil) -> [OptionChainRow] {
        let strikesInterval = strikeInterval
        let atmStrike = (spotPrice / strikesInterval).rounded() * strikesInterval
        let startStrike = atmStrike - Double(numStrikes / 2) * strikesInterval

        let expiry = expiryDate ?? Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
        let timeToExpiry = BlackScholesEngine.timeToExpiry(from: expiry)
        let baseIV = 0.15

        var rows: [OptionChainRow] = []

        // Deterministic seed based on spot price
        let seed = Int(spotPrice) % 10000

        for i in 0..<numStrikes {
            let strike = startStrike + Double(i) * strikesInterval

            // Calculate theoretical prices
            let callPrice = BlackScholesEngine.calculateCallPrice(
                spotPrice: spotPrice,
                strikePrice: strike,
                timeToExpiry: timeToExpiry,
                volatility: baseIV
            )

            let putPrice = BlackScholesEngine.calculatePutPrice(
                spotPrice: spotPrice,
                strikePrice: strike,
                timeToExpiry: timeToExpiry,
                volatility: baseIV
            )

            // Generate DETERMINISTIC OI (higher near ATM, no random!)
            let distance = abs(strike - atmStrike)
            let oiFactor = max(0.1, 1.0 - distance / 1000.0)

            // Deterministic OI based on strike position
            let baseCallOI = 125000.0 * oiFactor  // Mid-range of 50000-200000
            let basePutOI = 125000.0 * oiFactor

            // Add deterministic variance based on strike index
            let callOIVariance = Double((seed + i * 7) % 100) / 100.0 * 50000  // 0 to 50000
            let putOIVariance = Double((seed + i * 11) % 100) / 100.0 * 50000

            let callOI = Int(baseCallOI + callOIVariance)
            let putOI = Int(basePutOI + putOIVariance)

            // Deterministic LTP adjustment
            let callLTPAdjust = Double((seed + i * 3) % 400) / 100.0 - 2.0  // -2 to +2
            let putLTPAdjust = Double((seed + i * 5) % 400) / 100.0 - 2.0

            let callLTP = max(callPrice + callLTPAdjust, 0.05)
            let putLTP = max(putPrice + putLTPAdjust, 0.05)

            // Deterministic OI change
            let callOIChange = (seed + i * 13) % 15000 - 5000  // -5000 to +10000
            let putOIChange = (seed + i * 17) % 15000 - 5000

            // Deterministic IV adjustment
            let callIVAdjust = Double((seed + i * 19) % 400) / 10000.0 - 0.02  // -0.02 to +0.02
            let putIVAdjust = Double((seed + i * 23) % 400) / 10000.0 - 0.02

            // Deterministic volume and quantities
            let callVolume = 10000 + (seed + i * 29) % 90000
            let putVolume = 10000 + (seed + i * 31) % 90000
            let callBidQty = 100 + (seed + i * 37) % 900
            let putBidQty = 100 + (seed + i * 41) % 900
            let callAskQty = 100 + (seed + i * 43) % 900
            let putAskQty = 100 + (seed + i * 47) % 900

            let callOption = OptionData(
                strikePrice: strike,
                optionType: .call,
                expiryDate: expiry,
                lastTradedPrice: callLTP,
                openInterest: callOI,
                changeInOI: callOIChange,
                impliedVolatility: baseIV + callIVAdjust,
                bidPrice: max(callLTP - 1, 0.05),
                askPrice: callLTP + 1,
                bidQty: callBidQty,
                askQty: callAskQty,
                volume: callVolume,
                underlyingValue: spotPrice
            )

            let putOption = OptionData(
                strikePrice: strike,
                optionType: .put,
                expiryDate: expiry,
                lastTradedPrice: putLTP,
                openInterest: putOI,
                changeInOI: putOIChange,
                impliedVolatility: baseIV + putIVAdjust,
                bidPrice: max(putLTP - 1, 0.05),
                askPrice: putLTP + 1,
                bidQty: putBidQty,
                askQty: putAskQty,
                volume: putVolume,
                underlyingValue: spotPrice
            )

            rows.append(OptionChainRow(strikePrice: strike, callOption: callOption, putOption: putOption))
        }

        return rows
    }
}
