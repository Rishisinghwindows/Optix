import Foundation

actor AuthAPIService {
    static let shared = AuthAPIService()

    private let baseURL: URL
    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    private init() {
        self.baseURL = URL(string: AppEnvironment.current.apiBaseURL)!

        // Configure URLSession
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)

        // Configure JSON decoder
        self.decoder = JSONDecoder()
        self.decoder.dateDecodingStrategy = .iso8601

        // Configure JSON encoder
        self.encoder = JSONEncoder()
        self.encoder.dateEncodingStrategy = .iso8601
    }

    // MARK: - OTP Authentication

    func sendOTP(phone: String, purpose: String = "login") async throws -> OTPSendResponse {
        let request = OTPSendRequest(phone: phone, purpose: purpose)
        return try await post(endpoint: "/auth/otp/send", body: request)
    }

    func verifyOTP(phone: String, otp: String) async throws -> AuthResponse {
        let request = OTPVerifyRequest(
            phone: phone,
            otp: otp,
            deviceInfo: DeviceInfo.current
        )
        return try await post(endpoint: "/auth/otp/verify", body: request)
    }

    // MARK: - Social Authentication

    func loginWithGoogle(idToken: String) async throws -> AuthResponse {
        let request = SocialLoginRequest(
            idToken: idToken,
            accessToken: nil,
            authorizationCode: nil,
            deviceInfo: DeviceInfo.current
        )
        return try await post(endpoint: "/auth/social/google", body: request)
    }

    func loginWithApple(identityToken: String) async throws -> AuthResponse {
        let request = SocialLoginRequest(
            idToken: identityToken,
            accessToken: nil,
            authorizationCode: nil,
            deviceInfo: DeviceInfo.current
        )
        return try await post(endpoint: "/auth/social/apple", body: request)
    }

    func loginWithFacebook(accessToken: String) async throws -> AuthResponse {
        let request = SocialLoginRequest(
            idToken: nil,
            accessToken: accessToken,
            authorizationCode: nil,
            deviceInfo: DeviceInfo.current
        )
        return try await post(endpoint: "/auth/social/facebook", body: request)
    }

    // MARK: - Token Management

    func refreshAccessToken(refreshToken: String) async throws -> RefreshTokenResponse {
        let request = RefreshTokenRequest(refreshToken: refreshToken)
        return try await post(endpoint: "/auth/refresh", body: request)
    }

    func logout(accessToken: String) async throws {
        let _: EmptyResponse = try await post(
            endpoint: "/auth/logout",
            body: EmptyRequest(),
            accessToken: accessToken
        )
    }

    func logoutAll(accessToken: String) async throws {
        let _: EmptyResponse = try await post(
            endpoint: "/auth/logout-all",
            body: EmptyRequest(),
            accessToken: accessToken
        )
    }

    // MARK: - User Profile

    func getCurrentUser(accessToken: String) async throws -> User {
        return try await get(endpoint: "/user/me", accessToken: accessToken)
    }

    func updateProfile(updates: [String: Any], accessToken: String) async throws -> User {
        return try await patch(endpoint: "/user/me", body: updates, accessToken: accessToken)
    }

    func getSessions(accessToken: String) async throws -> [Session] {
        return try await get(endpoint: "/user/sessions", accessToken: accessToken)
    }

    func revokeSession(sessionId: String, accessToken: String) async throws {
        let _: EmptyResponse = try await delete(
            endpoint: "/user/sessions/\(sessionId)",
            accessToken: accessToken
        )
    }

    // MARK: - Private Helpers

    private func get<T: Decodable>(
        endpoint: String,
        accessToken: String? = nil
    ) async throws -> T {
        var request = URLRequest(url: baseURL.appendingPathComponent(endpoint))
        request.httpMethod = "GET"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")

        if let token = accessToken {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        return try await execute(request: request)
    }

    private func post<T: Decodable, B: Encodable>(
        endpoint: String,
        body: B,
        accessToken: String? = nil
    ) async throws -> T {
        var request = URLRequest(url: baseURL.appendingPathComponent(endpoint))
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)

        if let token = accessToken {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        return try await execute(request: request)
    }

    private func patch<T: Decodable>(
        endpoint: String,
        body: [String: Any],
        accessToken: String
    ) async throws -> T {
        var request = URLRequest(url: baseURL.appendingPathComponent(endpoint))
        request.httpMethod = "PATCH"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        return try await execute(request: request)
    }

    private func delete<T: Decodable>(
        endpoint: String,
        accessToken: String
    ) async throws -> T {
        var request = URLRequest(url: baseURL.appendingPathComponent(endpoint))
        request.httpMethod = "DELETE"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        return try await execute(request: request)
    }

    private func execute<T: Decodable>(request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthError.networkError
        }

        switch httpResponse.statusCode {
        case 200...299:
            // Handle empty response
            if T.self == EmptyResponse.self {
                return EmptyResponse() as! T
            }
            return try decoder.decode(T.self, from: data)

        case 400:
            let error = try? decoder.decode(APIError.self, from: data)
            if error?.detail.contains("expired") == true {
                throw AuthError.otpExpired
            } else if error?.detail.contains("Invalid OTP") == true {
                throw AuthError.otpInvalid
            }
            throw AuthError.serverError(error?.detail ?? "Bad request")

        case 401:
            throw AuthError.tokenExpired

        case 403:
            throw AuthError.notAuthenticated

        case 429:
            let error = try? decoder.decode(APIError.self, from: data)
            // Extract seconds from error message
            if let message = error?.detail,
               let range = message.range(of: #"\d+"#, options: .regularExpression),
               let seconds = Int(message[range]) {
                throw AuthError.rateLimited(seconds)
            }
            throw AuthError.rateLimited(60)

        default:
            let error = try? decoder.decode(APIError.self, from: data)
            throw AuthError.serverError(error?.detail ?? "Server error")
        }
    }
}

// Helper types for empty requests/responses
private struct EmptyRequest: Encodable {}
private struct EmptyResponse: Decodable {}
