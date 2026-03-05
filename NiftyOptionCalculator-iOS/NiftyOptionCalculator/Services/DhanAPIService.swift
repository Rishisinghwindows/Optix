import Foundation

// MARK: - Dhan Configuration

struct DhanConfig {
    // Replace with your Dhan API credentials
    static let clientId = "YOUR_DHAN_CLIENT_ID"
    static let accessToken = "YOUR_DHAN_ACCESS_TOKEN"  // From Dhan developer portal

    static let baseURL = "https://api.dhan.co/v2"

    // Security IDs for indices
    static func securityId(for index: TradingIndex) -> String {
        switch index {
        case .nifty50: return "13"
        case .bankNifty: return "25"
        case .niftyFinService: return "27"
        case .midcapSelect: return "442"
        case .sensex: return "1"
        case .bankex: return "12"
        }
    }

    // Segment for options
    static func segment(for index: TradingIndex) -> String {
        switch index {
        case .sensex, .bankex: return "BSE_FNO"
        default: return "NSE_FNO"
        }
    }

    // Index segment for spot price
    static func indexSegment(for index: TradingIndex) -> String {
        switch index {
        case .sensex, .bankex: return "BSE_IDX"
        default: return "NSE_IDX"
        }
    }
}

// MARK: - Dhan API Response Models

struct DhanMarketQuoteResponse: Codable {
    let status: String?
    let remarks: String?
    let data: DhanQuoteData?
}

struct DhanQuoteData: Codable {
    let lastPrice: Double?
    let previousClose: Double?
    let change: Double?
    let percentChange: Double?
    let open: Double?
    let high: Double?
    let low: Double?

    enum CodingKeys: String, CodingKey {
        case lastPrice = "last_price"
        case previousClose = "previous_close"
        case change
        case percentChange = "percent_change"
        case open, high, low
    }
}

struct DhanOptionChainResponse: Codable {
    let status: String?
    let remarks: String?
    let data: DhanOptionChainData?
}

struct DhanOptionChainData: Codable {
    let optionChain: [DhanOptionStrike]?

    enum CodingKeys: String, CodingKey {
        case optionChain = "option_chain"
    }
}

struct DhanOptionStrike: Codable {
    let strikePrice: Double
    let expiryDate: String
    let callLtp: Double?
    let putLtp: Double?
    let callOi: Int?
    let putOi: Int?
    let callIv: Double?
    let putIv: Double?
    let callDelta: Double?
    let putDelta: Double?

    enum CodingKeys: String, CodingKey {
        case strikePrice = "strike_price"
        case expiryDate = "expiry_date"
        case callLtp = "call_ltp"
        case putLtp = "put_ltp"
        case callOi = "call_oi"
        case putOi = "put_oi"
        case callIv = "call_iv"
        case putIv = "put_iv"
        case callDelta = "call_delta"
        case putDelta = "put_delta"
    }
}

struct DhanExpiryResponse: Codable {
    let status: String?
    let remarks: String?
    let data: [String]?
}

// MARK: - Dhan API Service

@MainActor
class DhanAPIService: ObservableObject, DataProvider {

    // MARK: - DataProvider Protocol Properties

    var providerId: String { "dhan" }
    var displayName: String { "Dhan" }
    var iconName: String { "d.circle.fill" }
    var brandColorHex: String { "00B386" }

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

    private var clientId: String?

    private let session: URLSession

    // MARK: - Singleton

    static let shared = DhanAPIService()

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

    func authenticate() async throws {
        throw ProviderError.notAuthenticated
    }

    func authenticate(clientId: String, accessToken: String) async throws {
        isLoading = true
        defer { isLoading = false }

        // Dhan uses pre-generated access tokens from developer portal
        // Validate by making a test API call

        let urlString = "\(DhanConfig.baseURL)/funds/limits"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(clientId, forHTTPHeaderField: "client-id")

        do {
            let (_, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw ProviderError.invalidResponse
            }

            if httpResponse.statusCode == 200 {
                self.accessToken = accessToken
                self.clientId = clientId
                self.errorMessage = nil
            } else if httpResponse.statusCode == 401 {
                throw ProviderError.invalidCredentials
            } else {
                throw ProviderError.apiError("HTTP \(httpResponse.statusCode)")
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
        clientId = nil
        deleteTokenFromKeychain()
        isAuthenticated = false
    }

    // MARK: - DataProvider Protocol Methods

    func fetchExpiryDates(index: TradingIndex) async throws -> [ExpiryDate] {
        guard let token = accessToken, let client = clientId else {
            throw ProviderError.notAuthenticated
        }

        let segment = DhanConfig.segment(for: index)
        let securityId = DhanConfig.securityId(for: index)

        let urlString = "\(DhanConfig.baseURL)/optionchain/expirylist?underlyingScrip=\(securityId)&segment=\(segment)"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue(client, forHTTPHeaderField: "client-id")

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

            let expiryResponse = try JSONDecoder().decode(DhanExpiryResponse.self, from: data)

            if let expiries = expiryResponse.data {
                return expiries.prefix(4).map { ExpiryDate(dateString: $0) }
            }

            // Fallback to generated expiries
            return generateExpiryDates(for: index)

        } catch let error as ProviderError {
            throw error
        } catch {
            throw ProviderError.networkError(error)
        }
    }

    func fetchSpotPrice(index: TradingIndex) async throws -> SpotQuoteResult {
        guard let token = accessToken, let client = clientId else {
            throw ProviderError.notAuthenticated
        }

        let segment = DhanConfig.indexSegment(for: index)
        let securityId = DhanConfig.securityId(for: index)

        let urlString = "\(DhanConfig.baseURL)/marketfeed/ltp?securityId=\(securityId)&exchangeSegment=\(segment)"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue(client, forHTTPHeaderField: "client-id")

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

            let quoteResponse = try JSONDecoder().decode(DhanMarketQuoteResponse.self, from: data)

            if let quoteData = quoteResponse.data,
               let lastPrice = quoteData.lastPrice {
                return SpotQuoteResult(
                    lastPrice: lastPrice,
                    change: quoteData.change ?? 0,
                    changePercent: quoteData.percentChange ?? 0,
                    previousClose: quoteData.previousClose ?? lastPrice
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
        guard let token = accessToken, let client = clientId else {
            throw ProviderError.notAuthenticated
        }

        let segment = DhanConfig.segment(for: index)
        let securityId = DhanConfig.securityId(for: index)

        let urlString = "\(DhanConfig.baseURL)/optionchain?underlyingScrip=\(securityId)&segment=\(segment)&expiryDate=\(expiry)"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue(client, forHTTPHeaderField: "client-id")

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

            let chainResponse = try JSONDecoder().decode(DhanOptionChainResponse.self, from: data)

            if let strikes = chainResponse.data?.optionChain {
                return parseOptionChain(strikes, expiry: expiry, index: index)
            }

            throw ProviderError.apiError("No option chain data")

        } catch let error as ProviderError {
            throw error
        } catch {
            throw ProviderError.networkError(error)
        }
    }

    // MARK: - Helper Methods

    private func generateExpiryDates(for index: TradingIndex) -> [ExpiryDate] {
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

    private func parseOptionChain(_ strikes: [DhanOptionStrike], expiry: String, index: TradingIndex) -> [OptionChainRow] {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let expiryDate = dateFormatter.date(from: expiry) ?? Date()

        var rows: [OptionChainRow] = []

        for strike in strikes {
            var callOption: OptionData? = nil
            var putOption: OptionData? = nil

            if let callLtp = strike.callLtp {
                callOption = OptionData(
                    strikePrice: strike.strikePrice,
                    optionType: .call,
                    expiryDate: expiryDate,
                    lastTradedPrice: callLtp,
                    openInterest: strike.callOi ?? 0,
                    impliedVolatility: (strike.callIv ?? 15) / 100,
                    underlyingValue: 0,
                    delta: strike.callDelta
                )
            }

            if let putLtp = strike.putLtp {
                putOption = OptionData(
                    strikePrice: strike.strikePrice,
                    optionType: .put,
                    expiryDate: expiryDate,
                    lastTradedPrice: putLtp,
                    openInterest: strike.putOi ?? 0,
                    impliedVolatility: (strike.putIv ?? 15) / 100,
                    underlyingValue: 0,
                    delta: strike.putDelta
                )
            }

            rows.append(OptionChainRow(
                strikePrice: strike.strikePrice,
                callOption: callOption,
                putOption: putOption
            ))
        }

        return rows.sorted { $0.strikePrice < $1.strikePrice }
    }

    // MARK: - Keychain Management

    private let keychainKey = "com.optix.dhan.accesstoken"

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
