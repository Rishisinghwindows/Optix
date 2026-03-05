import Foundation

enum AppEnvironment: String {
    case dev
    case qa
    case prod

    static var current: AppEnvironment {
        if let override = ProcessInfo.processInfo.environment["OPTIX_ENV"],
           let env = AppEnvironment(rawValue: override.lowercased()) {
            return env
        }
        if let override = UserDefaults.standard.string(forKey: "optix.env"),
           let env = AppEnvironment(rawValue: override.lowercased()) {
            return env
        }
        if let plistValue = Bundle.main.object(forInfoDictionaryKey: "OPTIX_ENV") as? String,
           let env = AppEnvironment(rawValue: plistValue.lowercased()) {
            return env
        }
        // Default to dev for local testing
        return .dev
    }

    var apiBaseURL: String {
        switch self {
        case .dev:
            return "http://127.0.0.1:8000/api/v1"
        case .qa:
            return "https://api.optix.d23ai.in/api/v1"
        case .prod:
            return "https://api.optix.d23ai.in/api/v1"
        }
    }

    var marketBaseURL: String {
        return "\(apiBaseURL)/market"
    }

    var paperTradingBaseURL: String {
        return "\(apiBaseURL)/paper-trading"
    }

    var wsBaseURL: String {
        switch self {
        case .dev:
            return "ws://127.0.0.1:8000/ws/market"
        case .qa:
            return "wss://api.optix.d23ai.in/ws/market"
        case .prod:
            return "wss://api.optix.d23ai.in/ws/market"
        }
    }

    var upstoxRedirectURI: String {
        switch self {
        case .dev:
            return "http://127.0.0.1:8000/api/v1/market/upstox/callback"
        case .qa:
            return "https://api.optix.d23ai.in/api/v1/market/upstox/callback"
        case .prod:
            return "https://api.optix.d23ai.in/api/v1/market/upstox/callback"
        }
    }
}
