import Foundation
import SwiftUI
import AuthenticationServices

@MainActor
final class AuthManager: ObservableObject {
    static let shared = AuthManager()

    // MARK: - Published Properties

    @Published private(set) var isLoggedIn: Bool = false
    @Published private(set) var currentUser: User?
    @Published private(set) var isLoading: Bool = false
    @Published var showLoginSheet: Bool = false

    // MARK: - Private Properties

    private let api = AuthAPIService.shared
    private let keychain = KeychainService.shared

    var accessToken: String? {
        get { keychain.get("access_token") }
        set { keychain.set(newValue, forKey: "access_token") }
    }

    private var refreshToken: String? {
        get { keychain.get("refresh_token") }
        set { keychain.set(newValue, forKey: "refresh_token") }
    }

    private var tokenExpiresAt: Date? {
        get {
            guard let timestamp = UserDefaults.standard.object(forKey: "token_expires_at") as? TimeInterval else {
                return nil
            }
            return Date(timeIntervalSince1970: timestamp)
        }
        set {
            UserDefaults.standard.set(newValue?.timeIntervalSince1970, forKey: "token_expires_at")
        }
    }

    var isGuest: Bool {
        !isLoggedIn
    }

    // MARK: - Initialization

    private init() {
        Task {
            await checkAuthState()
        }
    }

    // MARK: - Auth State

    func checkAuthState() async {
        guard let token = accessToken else {
            isLoggedIn = false
            currentUser = nil
            return
        }

        // Check if token is expired
        if let expiresAt = tokenExpiresAt, expiresAt < Date() {
            // Try to refresh
            do {
                try await refreshAccessToken()
            } catch {
                await logout()
                return
            }
        }

        // Fetch current user
        do {
            let user = try await api.getCurrentUser(accessToken: token)
            currentUser = user
            isLoggedIn = true
        } catch {
            // Token might be invalid, try to refresh
            do {
                try await refreshAccessToken()
                if let newToken = accessToken {
                    let user = try await api.getCurrentUser(accessToken: newToken)
                    currentUser = user
                    isLoggedIn = true
                }
            } catch {
                await logout()
            }
        }
    }

    // MARK: - OTP Authentication

    func sendOTP(phone: String) async throws -> OTPSendResponse {
        isLoading = true
        defer { isLoading = false }

        return try await api.sendOTP(phone: phone)
    }

    func verifyOTP(phone: String, otp: String) async throws {
        isLoading = true
        defer { isLoading = false }

        let response = try await api.verifyOTP(phone: phone, otp: otp)
        handleAuthResponse(response)
    }

    // MARK: - Apple Sign In

    func loginWithApple(authorization: ASAuthorization) async throws {
        isLoading = true
        defer { isLoading = false }

        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let identityToken = credential.identityToken,
              let tokenString = String(data: identityToken, encoding: .utf8) else {
            throw AuthError.invalidCredentials
        }

        let response = try await api.loginWithApple(identityToken: tokenString)
        handleAuthResponse(response)
    }

    // MARK: - Google Sign In

    func loginWithGoogle(idToken: String) async throws {
        isLoading = true
        defer { isLoading = false }

        let response = try await api.loginWithGoogle(idToken: idToken)
        handleAuthResponse(response)
    }

    // MARK: - Token Management

    func refreshAccessToken() async throws {
        guard let refresh = refreshToken else {
            throw AuthError.tokenExpired
        }

        let response = try await api.refreshAccessToken(refreshToken: refresh)
        accessToken = response.accessToken
        tokenExpiresAt = Date().addingTimeInterval(TimeInterval(response.expiresIn))
    }

    func logout() async {
        // Try to notify server (best effort)
        if let token = accessToken {
            try? await api.logout(accessToken: token)
        }

        // Clear local state
        accessToken = nil
        refreshToken = nil
        tokenExpiresAt = nil
        currentUser = nil
        isLoggedIn = false
    }

    func logoutAll() async throws {
        guard let token = accessToken else {
            throw AuthError.notAuthenticated
        }

        try await api.logoutAll(accessToken: token)
        await logout()
    }

    // MARK: - Trade Guard

    /// Call this before any action that requires authentication.
    /// Returns true if user is authenticated, false if login sheet was shown.
    /// When login succeeds, the provided action will be called.
    @discardableResult
    func requireAuth(action: @escaping () async -> Void) -> Bool {
        if isLoggedIn {
            return true
        }

        // Store the action to execute after login
        pendingAction = action
        showLoginSheet = true
        return false
    }

    private var pendingAction: (() async -> Void)?

    func onLoginSuccess() async {
        showLoginSheet = false

        // Execute pending action if any
        if let action = pendingAction {
            pendingAction = nil
            await action()
        }
    }

    func dismissLogin() {
        showLoginSheet = false
        pendingAction = nil
    }

    // MARK: - User Profile

    func updateProfile(fullName: String? = nil, email: String? = nil) async throws {
        guard let token = accessToken else {
            throw AuthError.notAuthenticated
        }

        var updates: [String: Any] = [:]
        if let fullName = fullName { updates["full_name"] = fullName }
        if let email = email { updates["email"] = email }

        let updatedUser = try await api.updateProfile(updates: updates, accessToken: token)
        currentUser = updatedUser
    }

    func getSessions() async throws -> [Session] {
        guard let token = accessToken else {
            throw AuthError.notAuthenticated
        }

        return try await api.getSessions(accessToken: token)
    }

    func revokeSession(sessionId: String) async throws {
        guard let token = accessToken else {
            throw AuthError.notAuthenticated
        }

        try await api.revokeSession(sessionId: sessionId, accessToken: token)
    }

    // MARK: - Private Helpers

    private func handleAuthResponse(_ response: AuthResponse) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken
        tokenExpiresAt = Date().addingTimeInterval(TimeInterval(response.expiresIn))

        currentUser = User(
            id: response.user.id,
            phone: response.user.phone,
            email: response.user.email,
            fullName: response.user.fullName,
            avatarUrl: response.user.avatarUrl,
            authProvider: "phone",
            isVerified: response.user.isVerified,
            paperTradingBalance: response.user.paperTradingBalance,
            isPremium: response.user.isPremium,
            createdAt: nil,
            lastLoginAt: nil
        )
        isLoggedIn = true
    }
}

// MARK: - Keychain Service

class KeychainService {
    static let shared = KeychainService()

    private let service = "com.optix.auth"

    func set(_ value: String?, forKey key: String) {
        if let value = value {
            let data = value.data(using: .utf8)!
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: key,
                kSecValueData as String: data
            ]

            SecItemDelete(query as CFDictionary)
            SecItemAdd(query as CFDictionary, nil)
        } else {
            delete(key: key)
        }
    }

    func get(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let string = String(data: data, encoding: .utf8) else {
            return nil
        }

        return string
    }

    func delete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]

        SecItemDelete(query as CFDictionary)
    }
}
