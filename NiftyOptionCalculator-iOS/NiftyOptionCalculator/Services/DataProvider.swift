import Foundation

// MARK: - Data Provider Protocol

/// Protocol that all market data providers must conform to
protocol DataProvider {
    /// Unique identifier for this provider
    var providerId: String { get }

    /// Display name for UI
    var displayName: String { get }

    /// Provider logo/icon name
    var iconName: String { get }

    /// Brand color hex
    var brandColorHex: String { get }

    /// Whether the user is authenticated
    var isAuthenticated: Bool { get }

    /// Authenticate with the provider
    func authenticate() async throws

    /// Logout from the provider
    func logout()

    /// Fetch expiry dates for an index
    func fetchExpiryDates(index: TradingIndex) async throws -> [ExpiryDate]

    /// Fetch spot price for an index
    func fetchSpotPrice(index: TradingIndex) async throws -> SpotQuoteResult

    /// Fetch option chain for an index and expiry
    func fetchOptionChain(index: TradingIndex, expiry: String) async throws -> [OptionChainRow]
}

// MARK: - Provider Type Enum

enum ProviderType: String, CaseIterable, Identifiable, Codable {
    case guest = "guest"
    case upstox = "upstox"
    case zerodha = "zerodha"
    case angelOne = "angelone"
    case dhan = "dhan"
    case shoonya = "shoonya"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .guest: return "Guest Mode"
        case .upstox: return "Upstox"
        case .zerodha: return "Zerodha Kite"
        case .angelOne: return "Angel One"
        case .dhan: return "Dhan"
        case .shoonya: return "Shoonya"
        }
    }

    var tagline: String {
        switch self {
        case .guest: return "No login required"
        case .upstox: return "Investments simplified"
        case .zerodha: return "The trading platform"
        case .angelOne: return "Smart trading"
        case .dhan: return "Trade faster"
        case .shoonya: return "Zero brokerage"
        }
    }

    var iconName: String {
        switch self {
        case .guest: return "person.circle.fill"
        case .upstox: return "chart.line.uptrend.xyaxis"
        case .zerodha: return "leaf.fill"
        case .angelOne: return "bolt.fill"
        case .dhan: return "d.circle.fill"
        case .shoonya: return "s.circle.fill"
        }
    }

    var brandColorHex: String {
        switch self {
        case .guest: return "34C759"  // Green
        case .upstox: return "6B3FA0"  // Purple
        case .zerodha: return "387ED1"  // Kite Blue
        case .angelOne: return "FF6B00"  // Orange
        case .dhan: return "00B386"  // Green
        case .shoonya: return "1E88E5"  // Blue
        }
    }

    var requiresSubscription: Bool {
        switch self {
        case .zerodha: return true  // Kite Connect costs ₹2000/month
        case .guest, .upstox, .angelOne, .dhan, .shoonya: return false
        }
    }

    var subscriptionInfo: String? {
        switch self {
        case .zerodha: return "Requires Kite Connect (₹2000/month)"
        case .guest: return "Free - No demat account needed"
        default: return nil
        }
    }

    /// Whether this provider requires a demat/broker account
    var requiresDematAccount: Bool {
        switch self {
        case .guest: return false
        default: return true
        }
    }
}

// MARK: - Provider Manager

@MainActor
class ProviderManager: ObservableObject {
    static let shared = ProviderManager()

    @Published var currentProviderType: ProviderType {
        didSet {
            UserDefaults.standard.set(currentProviderType.rawValue, forKey: "selectedProvider")
            setupCurrentProvider()
        }
    }

    @Published var currentProvider: (any DataProvider)?

    // All provider instances
    private(set) var guestProvider: GuestDataService
    private(set) var upstoxProvider: UpstoxAPIService
    private(set) var zerodhaProvider: ZerodhaAPIService
    private(set) var angelOneProvider: AngelOneAPIService
    private(set) var dhanProvider: DhanAPIService
    private(set) var shoonyaProvider: ShoonyaAPIService

    private init() {
        // Load saved provider preference - default to guest mode for new users
        let savedProvider = UserDefaults.standard.string(forKey: "selectedProvider") ?? ProviderType.guest.rawValue
        self.currentProviderType = ProviderType(rawValue: savedProvider) ?? .guest

        // Initialize all providers
        self.guestProvider = GuestDataService.shared
        self.upstoxProvider = UpstoxAPIService.shared
        self.zerodhaProvider = ZerodhaAPIService.shared
        self.angelOneProvider = AngelOneAPIService.shared
        self.dhanProvider = DhanAPIService.shared
        self.shoonyaProvider = ShoonyaAPIService.shared

        setupCurrentProvider()
    }

    private func setupCurrentProvider() {
        switch currentProviderType {
        case .guest:
            currentProvider = guestProvider
        case .upstox:
            currentProvider = upstoxProvider
        case .zerodha:
            currentProvider = zerodhaProvider
        case .angelOne:
            currentProvider = angelOneProvider
        case .dhan:
            currentProvider = dhanProvider
        case .shoonya:
            currentProvider = shoonyaProvider
        }
    }

    func getProvider(for type: ProviderType) -> any DataProvider {
        switch type {
        case .guest: return guestProvider
        case .upstox: return upstoxProvider
        case .zerodha: return zerodhaProvider
        case .angelOne: return angelOneProvider
        case .dhan: return dhanProvider
        case .shoonya: return shoonyaProvider
        }
    }

    func isAuthenticated(for type: ProviderType) -> Bool {
        return getProvider(for: type).isAuthenticated
    }

    /// Switch to guest mode (no login required)
    func useGuestMode() {
        currentProviderType = .guest
    }
}

// MARK: - Provider Error

enum ProviderError: Error, LocalizedError {
    case notAuthenticated
    case invalidCredentials
    case networkError(Error)
    case apiError(String)
    case tokenExpired
    case invalidResponse
    case rateLimited
    case marketClosed

    var errorDescription: String? {
        switch self {
        case .notAuthenticated:
            return "Please login first"
        case .invalidCredentials:
            return "Invalid credentials"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .apiError(let message):
            return message
        case .tokenExpired:
            return "Session expired. Please login again"
        case .invalidResponse:
            return "Invalid response from server"
        case .rateLimited:
            return "Too many requests. Please wait"
        case .marketClosed:
            return "Market is closed"
        }
    }
}
