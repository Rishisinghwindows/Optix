import Foundation
import SwiftUI

/// Manager class for Shoonya broker authentication and session management
@MainActor
final class ShoonyaManager: ObservableObject {
    static let shared = ShoonyaManager()

    // MARK: - Published Properties

    @Published private(set) var isLoggedIn: Bool = false
    @Published private(set) var isLoading: Bool = false
    @Published private(set) var userName: String?
    @Published private(set) var userEmail: String?
    @Published private(set) var accountId: String?
    @Published private(set) var exchanges: [String] = []
    @Published var showLoginSheet: Bool = false
    @Published var errorMessage: String?

    // MARK: - Private Properties

    private let service = ShoonyaService.shared
    private let keychain = KeychainService.shared

    private var userId: String? {
        get { keychain.get("shoonya_user_id") }
        set { keychain.set(newValue, forKey: "shoonya_user_id") }
    }

    private var sessionToken: String? {
        get { keychain.get("shoonya_session_token") }
        set { keychain.set(newValue, forKey: "shoonya_session_token") }
    }

    private var vendorCode: String? {
        get { keychain.get("shoonya_vendor_code") }
        set { keychain.set(newValue, forKey: "shoonya_vendor_code") }
    }

    private var apiKey: String? {
        get { keychain.get("shoonya_api_key") }
        set { keychain.set(newValue, forKey: "shoonya_api_key") }
    }

    // MARK: - Initialization

    private init() {
        // Check if we have stored credentials
        if sessionToken != nil && userId != nil {
            isLoggedIn = true
            // Verify session in background
            Task {
                await verifySession()
            }
        }
    }

    // MARK: - Authentication

    /// Login to Shoonya
    /// - Parameters:
    ///   - userId: Shoonya user ID (e.g., "FA12345")
    ///   - password: Account password
    ///   - totp: TOTP code from authenticator app or Shoonya app
    ///   - vendorCode: Vendor code from Shoonya (optional if already saved)
    ///   - apiKey: API key from Shoonya (optional if already saved)
    func login(
        userId: String,
        password: String,
        totp: String,
        vendorCode: String? = nil,
        apiKey: String? = nil
    ) async throws {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // Use provided or stored vendor code/api key
        let vc = vendorCode ?? self.vendorCode ?? ShoonyaConfig.vendorCode
        let key = apiKey ?? self.apiKey ?? ShoonyaConfig.apiKey

        guard !vc.isEmpty, !key.isEmpty else {
            throw ShoonyaError.apiError("Vendor code and API key are required")
        }

        do {
            let response = try await service.login(
                userId: userId,
                password: password,
                totp: totp,
                vendorCode: vc,
                apiKey: key
            )

            // Store credentials
            self.userId = userId
            self.sessionToken = response.susertoken
            self.vendorCode = vc
            self.apiKey = key

            // Update state
            self.userName = response.uname
            self.userEmail = response.email
            self.accountId = response.actid
            self.exchanges = response.exarr ?? []
            self.isLoggedIn = true

            // Notify success
            NotificationCenter.default.post(name: .shoonyaLoginSuccess, object: nil)

        } catch let error as ShoonyaError {
            self.errorMessage = error.localizedDescription
            throw error
        } catch {
            self.errorMessage = error.localizedDescription
            throw ShoonyaError.networkError
        }
    }

    /// Logout from Shoonya
    func logout() async {
        isLoading = true
        defer { isLoading = false }

        // Try to logout from server
        if let userId = userId, let token = sessionToken {
            try? await service.logout(userId: userId, sessionToken: token)
        }

        // Clear local state
        clearSession()

        NotificationCenter.default.post(name: .shoonyaLogoutSuccess, object: nil)
    }

    /// Verify if current session is still valid
    func verifySession() async {
        guard let userId = userId, let token = sessionToken else {
            isLoggedIn = false
            return
        }

        do {
            let userDetails = try await service.getUserDetails(userId: userId, sessionToken: token)
            self.userName = userDetails.uid
            self.userEmail = userDetails.email
            self.accountId = userDetails.actid
            self.exchanges = userDetails.exarr ?? []
            self.isLoggedIn = true
        } catch {
            // Session invalid, clear it
            clearSession()
        }
    }

    // MARK: - Market Data

    /// Get quotes for an index
    func getIndexQuote(_ index: ShoonyaIndex) async throws -> ShoonyaQuotesResponse {
        guard let userId = userId, let token = sessionToken else {
            throw ShoonyaError.notLoggedIn
        }

        return try await service.getIndexQuote(
            userId: userId,
            sessionToken: token,
            index: index
        )
    }

    /// Get quotes for a specific scrip
    func getQuotes(exchange: String, token: String) async throws -> ShoonyaQuotesResponse {
        guard let userId = userId, let sessionToken = sessionToken else {
            throw ShoonyaError.notLoggedIn
        }

        return try await service.getQuotes(
            userId: userId,
            sessionToken: sessionToken,
            exchange: exchange,
            token: token
        )
    }

    /// Search for scrips
    func searchScrip(searchText: String, exchange: String = "NFO") async throws -> [ScripInfo] {
        guard let userId = userId, let token = sessionToken else {
            throw ShoonyaError.notLoggedIn
        }

        return try await service.searchScrip(
            userId: userId,
            sessionToken: token,
            searchText: searchText,
            exchange: exchange
        )
    }

    /// Get option chain
    func getOptionChain(
        symbol: String,
        strikePrice: String,
        count: Int = 10
    ) async throws -> [OptionChainItem] {
        guard let userId = userId, let token = sessionToken else {
            throw ShoonyaError.notLoggedIn
        }

        return try await service.getOptionChain(
            userId: userId,
            sessionToken: token,
            symbol: symbol,
            strikePrice: strikePrice,
            count: count
        )
    }

    // MARK: - Helpers

    /// Check if broker login is required and show login sheet
    func requireBrokerLogin() -> Bool {
        if isLoggedIn {
            return true
        }
        showLoginSheet = true
        return false
    }

    private func clearSession() {
        userId = nil
        sessionToken = nil
        userName = nil
        userEmail = nil
        accountId = nil
        exchanges = []
        isLoggedIn = false
    }

    /// Get current session token (for WebSocket connections)
    var currentSessionToken: String? {
        sessionToken
    }

    /// Get current user ID
    var currentUserId: String? {
        userId
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let shoonyaLoginSuccess = Notification.Name("shoonyaLoginSuccess")
    static let shoonyaLogoutSuccess = Notification.Name("shoonyaLogoutSuccess")
    static let shoonyaSessionExpired = Notification.Name("shoonyaSessionExpired")
}
