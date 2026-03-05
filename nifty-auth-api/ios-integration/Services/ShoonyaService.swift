import Foundation

/// Service class for Shoonya API interactions
class ShoonyaService {
    static let shared = ShoonyaService()

    private let baseURL = ShoonyaConfig.baseURL
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
    }

    // MARK: - Authentication

    /// Login to Shoonya
    /// - Parameters:
    ///   - userId: Shoonya user ID
    ///   - password: Plain text password (will be SHA256 encrypted)
    ///   - totp: TOTP/OTP code from authenticator app
    ///   - vendorCode: Vendor code provided by Shoonya
    ///   - apiKey: API key from Shoonya
    /// - Returns: Login response with session token
    func login(
        userId: String,
        password: String,
        totp: String,
        vendorCode: String,
        apiKey: String
    ) async throws -> ShoonyaLoginResponse {
        let endpoint = "/QuickAuth"

        // Create SHA256 hashed credentials
        let hashedPassword = password.sha256()
        let appKey = "\(userId)|\(apiKey)".sha256()

        let params: [String: String] = [
            "source": "API",
            "apkversion": "1.0.0",
            "uid": userId,
            "pwd": hashedPassword,
            "factor2": totp,
            "vc": vendorCode,
            "appkey": appKey,
            "imei": UUID().uuidString
        ]

        let response: ShoonyaLoginResponse = try await postRequest(endpoint: endpoint, params: params)

        if !response.isSuccess {
            throw ShoonyaError.apiError(response.emsg ?? "Login failed")
        }

        return response
    }

    /// Logout from Shoonya
    func logout(userId: String, sessionToken: String) async throws {
        let endpoint = "/Logout"

        let params: [String: String] = [
            "uid": userId,
            "jKey": sessionToken
        ]

        let _: ShoonyaLogoutResponse = try await postRequest(endpoint: endpoint, params: params)
    }

    // MARK: - Market Data

    /// Get quotes for a specific scrip
    func getQuotes(
        userId: String,
        sessionToken: String,
        exchange: String,
        token: String
    ) async throws -> ShoonyaQuotesResponse {
        let endpoint = "/GetQuotes"

        let params: [String: String] = [
            "uid": userId,
            "jKey": sessionToken,
            "exch": exchange,
            "token": token
        ]

        let response: ShoonyaQuotesResponse = try await postRequest(endpoint: endpoint, params: params)

        if response.stat != "Ok" {
            throw ShoonyaError.apiError(response.emsg ?? "Failed to get quotes")
        }

        return response
    }

    /// Get index quote (NIFTY, BANK NIFTY, etc.)
    func getIndexQuote(
        userId: String,
        sessionToken: String,
        index: ShoonyaIndex
    ) async throws -> ShoonyaQuotesResponse {
        return try await getQuotes(
            userId: userId,
            sessionToken: sessionToken,
            exchange: index.exchange,
            token: index.rawValue
        )
    }

    /// Search for scrips
    func searchScrip(
        userId: String,
        sessionToken: String,
        searchText: String,
        exchange: String = "NFO"
    ) async throws -> [ScripInfo] {
        let endpoint = "/SearchScrip"

        let params: [String: String] = [
            "uid": userId,
            "jKey": sessionToken,
            "stext": searchText,
            "exch": exchange
        ]

        let response: ShoonyaSearchResponse = try await postRequest(endpoint: endpoint, params: params)

        if response.stat != "Ok" {
            throw ShoonyaError.apiError(response.emsg ?? "Search failed")
        }

        return response.values ?? []
    }

    /// Get option chain
    func getOptionChain(
        userId: String,
        sessionToken: String,
        symbol: String,
        strikePrice: String,
        count: Int = 10,
        exchange: String = "NFO"
    ) async throws -> [OptionChainItem] {
        let endpoint = "/GetOptionChain"

        let params: [String: String] = [
            "uid": userId,
            "jKey": sessionToken,
            "tsym": symbol,
            "exch": exchange,
            "strprc": strikePrice,
            "cnt": String(count)
        ]

        let response: ShoonyaOptionChainResponse = try await postRequest(endpoint: endpoint, params: params)

        if response.stat != "Ok" {
            throw ShoonyaError.apiError(response.emsg ?? "Failed to get option chain")
        }

        return response.values ?? []
    }

    // MARK: - User

    /// Get user details
    func getUserDetails(
        userId: String,
        sessionToken: String
    ) async throws -> ShoonyaUserDetails {
        let endpoint = "/UserDetails"

        let params: [String: String] = [
            "uid": userId,
            "jKey": sessionToken
        ]

        let response: ShoonyaUserDetails = try await postRequest(endpoint: endpoint, params: params)

        if response.stat != "Ok" {
            throw ShoonyaError.apiError(response.emsg ?? "Failed to get user details")
        }

        return response
    }

    // MARK: - Network Helpers

    private func postRequest<T: Decodable>(endpoint: String, params: [String: String]) async throws -> T {
        guard let url = URL(string: baseURL + endpoint) else {
            throw ShoonyaError.networkError
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        // Convert params to JSON string prefixed with "jData="
        let jsonData = try JSONSerialization.data(withJSONObject: params)
        let jsonString = String(data: jsonData, encoding: .utf8) ?? "{}"
        let bodyString = "jData=\(jsonString)"
        request.httpBody = bodyString.data(using: .utf8)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ShoonyaError.networkError
        }

        guard httpResponse.statusCode == 200 else {
            throw ShoonyaError.apiError("Server returned status \(httpResponse.statusCode)")
        }

        let decoder = JSONDecoder()
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            // Try to parse error message
            if let errorResponse = try? decoder.decode(ShoonyaLoginResponse.self, from: data) {
                throw ShoonyaError.apiError(errorResponse.emsg ?? "Unknown error")
            }
            throw ShoonyaError.invalidResponse
        }
    }
}
