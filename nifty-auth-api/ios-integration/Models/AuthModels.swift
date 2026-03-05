import Foundation

// MARK: - User Model

struct User: Codable, Identifiable {
    let id: String
    let phone: String?
    let email: String?
    var fullName: String?
    var avatarUrl: String?
    let authProvider: String
    let isVerified: Bool
    var paperTradingBalance: Double
    let isPremium: Bool
    let createdAt: Date?
    let lastLoginAt: Date?

    enum CodingKeys: String, CodingKey {
        case id, phone, email
        case fullName = "full_name"
        case avatarUrl = "avatar_url"
        case authProvider = "auth_provider"
        case isVerified = "is_verified"
        case paperTradingBalance = "paper_trading_balance"
        case isPremium = "is_premium"
        case createdAt = "created_at"
        case lastLoginAt = "last_login_at"
    }
}

// MARK: - Auth Request/Response Models

struct DeviceInfo: Codable {
    let deviceName: String?
    let os: String?
    let appVersion: String?

    enum CodingKeys: String, CodingKey {
        case deviceName = "device_name"
        case os
        case appVersion = "app_version"
    }

    static var current: DeviceInfo {
        DeviceInfo(
            deviceName: UIDevice.current.name,
            os: "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)",
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        )
    }
}

struct OTPSendRequest: Codable {
    let phone: String
    let purpose: String

    init(phone: String, purpose: String = "login") {
        self.phone = phone
        self.purpose = purpose
    }
}

struct OTPSendResponse: Codable {
    let success: Bool
    let message: String
    let expiresIn: Int
    let resendAfter: Int

    enum CodingKeys: String, CodingKey {
        case success, message
        case expiresIn = "expires_in"
        case resendAfter = "resend_after"
    }
}

struct OTPVerifyRequest: Codable {
    let phone: String
    let otp: String
    let deviceInfo: DeviceInfo?

    enum CodingKeys: String, CodingKey {
        case phone, otp
        case deviceInfo = "device_info"
    }
}

struct SocialLoginRequest: Codable {
    let idToken: String?
    let accessToken: String?
    let authorizationCode: String?
    let deviceInfo: DeviceInfo?

    enum CodingKeys: String, CodingKey {
        case idToken = "id_token"
        case accessToken = "access_token"
        case authorizationCode = "authorization_code"
        case deviceInfo = "device_info"
    }
}

struct AuthResponse: Codable {
    let accessToken: String
    let refreshToken: String
    let tokenType: String
    let expiresIn: Int
    let user: UserInAuth

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case tokenType = "token_type"
        case expiresIn = "expires_in"
        case user
    }
}

struct UserInAuth: Codable {
    let id: String
    let phone: String?
    let email: String?
    let fullName: String?
    let avatarUrl: String?
    let isVerified: Bool
    let paperTradingBalance: Double
    let isPremium: Bool

    enum CodingKeys: String, CodingKey {
        case id, phone, email
        case fullName = "full_name"
        case avatarUrl = "avatar_url"
        case isVerified = "is_verified"
        case paperTradingBalance = "paper_trading_balance"
        case isPremium = "is_premium"
    }
}

struct RefreshTokenRequest: Codable {
    let refreshToken: String

    enum CodingKeys: String, CodingKey {
        case refreshToken = "refresh_token"
    }
}

struct RefreshTokenResponse: Codable {
    let accessToken: String
    let expiresIn: Int

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case expiresIn = "expires_in"
    }
}

// MARK: - Session Models

struct Session: Codable, Identifiable {
    let id: String
    let deviceInfo: DeviceInfo?
    let ipAddress: String?
    let createdAt: Date
    let expiresAt: Date
    let isCurrent: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case deviceInfo = "device_info"
        case ipAddress = "ip_address"
        case createdAt = "created_at"
        case expiresAt = "expires_at"
        case isCurrent = "is_current"
    }
}

// MARK: - Error Models

struct APIError: Codable, Error {
    let detail: String
}

enum AuthError: Error, LocalizedError {
    case invalidCredentials
    case networkError
    case serverError(String)
    case otpExpired
    case otpInvalid
    case rateLimited(Int)
    case tokenExpired
    case notAuthenticated

    var errorDescription: String? {
        switch self {
        case .invalidCredentials:
            return "Invalid credentials"
        case .networkError:
            return "Network error. Please check your connection."
        case .serverError(let message):
            return message
        case .otpExpired:
            return "OTP has expired. Please request a new one."
        case .otpInvalid:
            return "Invalid OTP. Please try again."
        case .rateLimited(let seconds):
            return "Too many requests. Please try again in \(seconds) seconds."
        case .tokenExpired:
            return "Session expired. Please login again."
        case .notAuthenticated:
            return "Please login to continue."
        }
    }
}
