import Foundation
import AuthenticationServices

// MARK: - Angel One Configuration

struct AngelOneConfig {
    // Replace with your Angel One SmartAPI credentials
    static let apiKey = "YOUR_ANGEL_ONE_API_KEY"
    static let clientId = "YOUR_CLIENT_ID"

    static let baseURL = "https://apiconnect.angelbroking.com"
    static let loginURL = "\(baseURL)/rest/auth/angelbroking/user/v1/loginByPassword"
    static let generateTOTPURL = "\(baseURL)/rest/auth/angelbroking/user/v1/generateTOTP"

    // Symbol tokens for indices
    static func symbolToken(for index: TradingIndex) -> String {
        switch index {
        case .nifty50: return "99926000"
        case .bankNifty: return "99926009"
        case .niftyFinService: return "99926011"
        case .midcapSelect: return "99926013"
        case .sensex: return "99919000"
        case .bankex: return "99919015"
        }
    }

    // Trading symbol for options
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
        case .sensex, .bankex: return "BFO"
        default: return "NFO"
        }
    }
}

// MARK: - Angel One API Response Models

struct AngelOneAuthResponse: Codable {
    let status: Bool
    let message: String
    let errorcode: String?
    let data: AngelOneAuthData?
}

struct AngelOneAuthData: Codable {
    let jwtToken: String
    let refreshToken: String
    let feedToken: String?

    enum CodingKeys: String, CodingKey {
        case jwtToken = "jwtToken"
        case refreshToken = "refreshToken"
        case feedToken = "feedToken"
    }
}

struct AngelOneMarketDataResponse: Codable {
    let status: Bool
    let message: String
    let errorcode: String?
    let data: AngelOneMarketDataWrapper?
}

struct AngelOneMarketDataWrapper: Codable {
    let fetched: [AngelOneLTPData]?
    let unfetched: [String]?
}

struct AngelOneLTPData: Codable {
    let exchange: String
    let tradingSymbol: String
    let symbolToken: String
    let ltp: Double
    let open: Double?
    let high: Double?
    let low: Double?
    let close: Double?
    let percentChange: Double?
    let netChange: Double?

    enum CodingKeys: String, CodingKey {
        case exchange
        case tradingSymbol = "tradingsymbol"
        case symbolToken = "symboltoken"
        case ltp
        case open, high, low, close
        case percentChange = "percentchange"
        case netChange = "netchange"
    }
}

struct AngelOneOptionChainResponse: Codable {
    let status: Bool
    let message: String
    let errorcode: String?
    let data: [AngelOneOptionData]?
}

struct AngelOneOptionData: Codable {
    let strikePrice: Double
    let expiryDate: String
    let ceSymbol: String?
    let peSymbol: String?
    let ceLtp: Double?
    let peLtp: Double?
    let ceOi: Int?
    let peOi: Int?
    let ceIv: Double?
    let peIv: Double?

    enum CodingKeys: String, CodingKey {
        case strikePrice = "strikeprice"
        case expiryDate = "expirydate"
        case ceSymbol = "cesymbol"
        case peSymbol = "pesymbol"
        case ceLtp = "celtp"
        case peLtp = "peltp"
        case ceOi = "ceoi"
        case peOi = "peoi"
        case ceIv = "ceiv"
        case peIv = "peiv"
    }
}

// MARK: - Angel One API Service

@MainActor
class AngelOneAPIService: ObservableObject, DataProvider {

    // MARK: - DataProvider Protocol Properties

    var providerId: String { "angelone" }
    var displayName: String { "Angel One" }
    var iconName: String { "bolt.fill" }
    var brandColorHex: String { "FF6B00" }

    // MARK: - Published Properties

    @Published var isAuthenticated: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var userName: String?

    // MARK: - Private Properties

    private var jwtToken: String? {
        didSet {
            isAuthenticated = jwtToken != nil
            if let token = jwtToken {
                saveTokenToKeychain(token)
            }
        }
    }

    private var refreshToken: String?
    private var feedToken: String?

    private let session: URLSession

    // MARK: - Singleton

    static let shared = AngelOneAPIService()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)

        // Load saved token
        if let savedToken = loadTokenFromKeychain() {
            self.jwtToken = savedToken
            self.isAuthenticated = true
        }
    }

    // MARK: - Authentication

    func authenticate() async throws {
        throw ProviderError.notAuthenticated
    }

    func authenticate(clientId: String, password: String, totp: String) async throws {
        isLoading = true
        defer { isLoading = false }

        guard let url = URL(string: AngelOneConfig.loginURL) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(AngelOneConfig.apiKey, forHTTPHeaderField: "X-PrivateKey")
        request.setValue("WEB", forHTTPHeaderField: "X-SourceID")
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-ClientLocalIP")
        request.setValue("Mozilla/5.0", forHTTPHeaderField: "X-ClientPublicIP")
        request.setValue("iOS", forHTTPHeaderField: "X-MACAddress")

        let body: [String: Any] = [
            "clientcode": clientId,
            "password": password,
            "totp": totp
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw ProviderError.invalidResponse
            }

            if httpResponse.statusCode != 200 {
                throw ProviderError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let authResponse = try JSONDecoder().decode(AngelOneAuthResponse.self, from: data)

            if authResponse.status, let authData = authResponse.data {
                self.jwtToken = authData.jwtToken
                self.refreshToken = authData.refreshToken
                self.feedToken = authData.feedToken
                self.userName = clientId
                self.errorMessage = nil
            } else {
                throw ProviderError.apiError(authResponse.message)
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
        jwtToken = nil
        refreshToken = nil
        feedToken = nil
        userName = nil
        deleteTokenFromKeychain()
        isAuthenticated = false
    }

    // MARK: - DataProvider Protocol Methods

    func fetchExpiryDates(index: TradingIndex) async throws -> [ExpiryDate] {
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
        guard let token = jwtToken else {
            throw ProviderError.notAuthenticated
        }

        let urlString = "\(AngelOneConfig.baseURL)/rest/secure/angelbroking/market/v1/quote/"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue(AngelOneConfig.apiKey, forHTTPHeaderField: "X-PrivateKey")

        let exchange = index.exchange == "BSE" ? "BSE" : "NSE"
        let symbolToken = AngelOneConfig.symbolToken(for: index)

        let body: [String: Any] = [
            "mode": "FULL",
            "exchangeTokens": [
                exchange: [symbolToken]
            ]
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

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

            let marketResponse = try JSONDecoder().decode(AngelOneMarketDataResponse.self, from: data)

            if let fetched = marketResponse.data?.fetched?.first {
                let change = fetched.netChange ?? 0
                let changePercent = fetched.percentChange ?? 0
                let prevClose = fetched.close ?? (fetched.ltp - change)

                return SpotQuoteResult(
                    lastPrice: fetched.ltp,
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
        guard jwtToken != nil else {
            throw ProviderError.notAuthenticated
        }

        throw ProviderError.apiError("Option chain not supported for Angel One")
    }

    // MARK: - Keychain Management

    private let keychainKey = "com.optix.angelone.jwttoken"

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
