import Foundation

// MARK: - Shoonya (Finvasia) Configuration

struct ShoonyaConfig {
    // Replace with your Shoonya API credentials
    static let userId = "YOUR_SHOONYA_USER_ID"
    static let password = "YOUR_PASSWORD"
    static let vendorCode = "YOUR_VENDOR_CODE"
    static let apiKey = "YOUR_API_KEY"
    static let imei = "YOUR_IMEI"

    static let baseURL = "https://api.shoonya.com/NorenWClientTP"

    // Trading symbols for indices
    static func tradingSymbol(for index: TradingIndex) -> String {
        switch index {
        case .nifty50: return "Nifty 50"
        case .bankNifty: return "Nifty Bank"
        case .niftyFinService: return "Nifty Fin Service"
        case .midcapSelect: return "NIFTY MID SELECT"
        case .sensex: return "SENSEX"
        case .bankex: return "BANKEX"
        }
    }

    // Exchange for index spot
    static func exchange(for index: TradingIndex) -> String {
        switch index {
        case .sensex, .bankex: return "BSE"
        default: return "NSE"
        }
    }

    // Exchange for options
    static func optionExchange(for index: TradingIndex) -> String {
        switch index {
        case .sensex, .bankex: return "BFO"
        default: return "NFO"
        }
    }
}

// MARK: - Shoonya API Response Models

struct ShoonyaLoginResponse: Codable {
    let stat: String
    let susertoken: String?
    let laession: String?
    let uid: String?
    let actid: String?
    let uname: String?
    let emsg: String?
}

struct ShoonyaQuoteResponse: Codable {
    let stat: String
    let lp: String?      // Last price
    let pc: String?      // Percent change
    let c: String?       // Close price
    let o: String?       // Open
    let h: String?       // High
    let l: String?       // Low
    let ti: String?      // Tick size
    let ls: String?      // Lot size
    let emsg: String?
}

struct ShoonyaOptionChainResponse: Codable {
    let stat: String
    let values: [ShoonyaOptionData]?
    let emsg: String?
}

struct ShoonyaOptionData: Codable {
    let strprc: String?  // Strike price
    let exd: String?     // Expiry date
    let optt: String?    // Option type (CE/PE)
    let token: String?   // Token
    let tsym: String?    // Trading symbol
    let lp: String?      // Last price
    let oi: String?      // Open interest
    let iv: String?      // IV
}

struct ShoonyaExpiryResponse: Codable {
    let stat: String
    let values: [String]?
    let emsg: String?
}

// MARK: - Shoonya API Service

@MainActor
class ShoonyaAPIService: ObservableObject, DataProvider {

    // MARK: - DataProvider Protocol Properties

    var providerId: String { "shoonya" }
    var displayName: String { "Shoonya" }
    var iconName: String { "s.circle.fill" }
    var brandColorHex: String { "1E88E5" }

    // MARK: - Published Properties

    @Published var isAuthenticated: Bool = false
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var userName: String?

    // MARK: - Private Properties

    private var susertoken: String? {
        didSet {
            isAuthenticated = susertoken != nil
            if let token = susertoken {
                saveTokenToKeychain(token)
            }
        }
    }

    private var uid: String?

    private let session: URLSession

    // MARK: - Singleton

    static let shared = ShoonyaAPIService()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)

        // Load saved token
        if let savedToken = loadTokenFromKeychain() {
            self.susertoken = savedToken
            self.isAuthenticated = true
        }
    }

    // MARK: - Authentication

    func authenticate() async throws {
        throw ProviderError.notAuthenticated
    }

    func authenticate(userId: String, password: String, totp: String) async throws {
        isLoading = true
        defer { isLoading = false }

        // SHA256 hash of password
        let pwdHash = sha256(password)

        let urlString = "\(ShoonyaConfig.baseURL)/QuickAuth"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "source": "API",
            "apkversion": "1.0.0",
            "uid": userId,
            "pwd": pwdHash,
            "factor2": totp,
            "vc": ShoonyaConfig.vendorCode,
            "appkey": sha256("\(userId)|\(ShoonyaConfig.apiKey)"),
            "imei": ShoonyaConfig.imei
        ]

        // Shoonya uses jData format
        let jData = try? JSONSerialization.data(withJSONObject: body)
        let jString = String(data: jData ?? Data(), encoding: .utf8) ?? ""
        request.httpBody = "jData=\(jString)".data(using: .utf8)

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw ProviderError.invalidResponse
            }

            if httpResponse.statusCode != 200 {
                throw ProviderError.apiError("HTTP \(httpResponse.statusCode)")
            }

            let loginResponse = try JSONDecoder().decode(ShoonyaLoginResponse.self, from: data)

            if loginResponse.stat == "Ok", let token = loginResponse.susertoken {
                self.susertoken = token
                self.uid = loginResponse.uid
                self.userName = loginResponse.uname ?? userId
                self.errorMessage = nil
            } else {
                throw ProviderError.apiError(loginResponse.emsg ?? "Login failed")
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
        susertoken = nil
        uid = nil
        userName = nil
        deleteTokenFromKeychain()
        isAuthenticated = false
    }

    // MARK: - DataProvider Protocol Methods

    func fetchExpiryDates(index: TradingIndex) async throws -> [ExpiryDate] {
        guard susertoken != nil else {
            throw ProviderError.notAuthenticated
        }

        // Generate expiry dates based on index
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
        guard let token = susertoken else {
            throw ProviderError.notAuthenticated
        }

        let urlString = "\(ShoonyaConfig.baseURL)/GetQuotes"

        guard let url = URL(string: urlString) else {
            throw ProviderError.apiError("Invalid URL")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let exchange = ShoonyaConfig.exchange(for: index)
        let tradingSymbol = ShoonyaConfig.tradingSymbol(for: index)

        let body: [String: Any] = [
            "uid": uid ?? "",
            "exch": exchange,
            "token": tradingSymbol
        ]

        let jData = try? JSONSerialization.data(withJSONObject: body)
        let jString = String(data: jData ?? Data(), encoding: .utf8) ?? ""
        request.httpBody = "jData=\(jString)&jKey=\(token)".data(using: .utf8)

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

            let quoteResponse = try JSONDecoder().decode(ShoonyaQuoteResponse.self, from: data)

            if quoteResponse.stat == "Ok",
               let lpStr = quoteResponse.lp,
               let lastPrice = Double(lpStr) {
                let closePrice = Double(quoteResponse.c ?? "0") ?? 0
                let change = lastPrice - closePrice
                let changePercent = Double(quoteResponse.pc ?? "0") ?? 0

                return SpotQuoteResult(
                    lastPrice: lastPrice,
                    change: change,
                    changePercent: changePercent,
                    previousClose: closePrice
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
        guard susertoken != nil else {
            throw ProviderError.notAuthenticated
        }

        throw ProviderError.apiError("Option chain not supported for Shoonya")
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

    private let keychainKey = "com.optix.shoonya.token"

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

// MARK: - CommonCrypto Import

import CommonCrypto
