import Foundation

/// API Service for syncing paper trades with the server
/// Trades are stored on server so they sync across devices
@MainActor
class PaperTradingAPIService {
    static let shared = PaperTradingAPIService()

    private let session: URLSession

    private var baseURL: String {
        return AppEnvironment.current.paperTradingBaseURL
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
    }

    // MARK: - Request Helpers

    private func createRequest(endpoint: String, method: String, body: Data? = nil) throws -> URLRequest {
        guard let url = URL(string: "\(baseURL)\(endpoint)") else {
            throw PaperTradingAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Add auth token
        if let token = AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else {
            throw PaperTradingAPIError.notAuthenticated
        }

        if let body = body {
            request.httpBody = body
        }

        return request
    }

    private func performRequest<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw PaperTradingAPIError.invalidResponse
        }

        if httpResponse.statusCode == 401 {
            throw PaperTradingAPIError.notAuthenticated
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            // Try to decode error message
            if let errorResponse = try? JSONDecoder().decode(ErrorResponse.self, from: data) {
                throw PaperTradingAPIError.serverError(errorResponse.detail)
            }
            throw PaperTradingAPIError.serverError("HTTP \(httpResponse.statusCode)")
        }

        let decoder = JSONDecoder()
        return try decoder.decode(T.self, from: data)
    }

    // MARK: - Portfolio

    func fetchPortfolio() async throws -> ServerPortfolio {
        let request = try createRequest(endpoint: "/portfolio", method: "GET")
        return try await performRequest(request)
    }

    // MARK: - Positions

    func fetchPositions(status: String? = nil) async throws -> [ServerPosition] {
        var endpoint = "/positions"
        if let status = status {
            endpoint += "?status=\(status)"
        }
        let request = try createRequest(endpoint: endpoint, method: "GET")
        return try await performRequest(request)
    }

    func fetchOpenPositions() async throws -> [ServerPosition] {
        return try await fetchPositions(status: "open")
    }

    // MARK: - Trades

    func fetchTrades(limit: Int = 50) async throws -> [ServerTrade] {
        let request = try createRequest(endpoint: "/trades?limit=\(limit)", method: "GET")
        return try await performRequest(request)
    }

    /// Create a new paper trade (opens a position)
    func createTrade(request: CreateTradeRequest) async throws -> ServerPosition {
        let encoder = JSONEncoder()
        let body = try encoder.encode(request)
        let urlRequest = try createRequest(endpoint: "/trades", method: "POST", body: body)
        return try await performRequest(urlRequest)
    }

    // MARK: - Square Off

    func squareOff(positionId: String, exitPrice: Double) async throws -> ServerTrade {
        let body = try JSONEncoder().encode(["exit_price": exitPrice])
        let request = try createRequest(endpoint: "/positions/\(positionId)/square-off", method: "POST", body: body)
        return try await performRequest(request)
    }

    // MARK: - Update Price

    func updatePositionPrice(positionId: String, currentPrice: Double) async throws {
        let request = try createRequest(endpoint: "/positions/\(positionId)/price?current_price=\(currentPrice)", method: "PATCH")
        let _: GenericResponse = try await performRequest(request)
    }

    // MARK: - Reset Portfolio

    func resetPortfolio() async throws {
        let request = try createRequest(endpoint: "/portfolio/reset", method: "POST")
        let _: GenericResponse = try await performRequest(request)
    }

    // MARK: - Performance

    func fetchPerformance() async throws -> ServerPerformance {
        let request = try createRequest(endpoint: "/performance", method: "GET")
        return try await performRequest(request)
    }
}

// MARK: - Request/Response Models

struct CreateTradeRequest: Codable {
    let symbol: String
    let strikePrice: Double
    let optionType: String  // CE or PE
    let direction: String   // buy or sell
    let expiryDate: String
    let quantity: Int
    let lotSize: Int
    let price: Double

    enum CodingKeys: String, CodingKey {
        case symbol
        case strikePrice = "strike_price"
        case optionType = "option_type"
        case direction
        case expiryDate = "expiry_date"
        case quantity
        case lotSize = "lot_size"
        case price
    }
}

struct ServerPortfolio: Codable {
    let cashBalance: Double
    let investedAmount: Double
    let currentValue: Double
    let totalPnl: Double
    let totalPnlPercent: Double
    let openPositionsCount: Int
    let totalTrades: Int

    enum CodingKeys: String, CodingKey {
        case cashBalance = "cash_balance"
        case investedAmount = "invested_amount"
        case currentValue = "current_value"
        case totalPnl = "total_pnl"
        case totalPnlPercent = "total_pnl_percent"
        case openPositionsCount = "open_positions_count"
        case totalTrades = "total_trades"
    }
}

struct ServerPosition: Codable {
    let id: String
    let symbol: String
    let strikePrice: Double
    let optionType: String
    let direction: String
    let expiryDate: String
    let quantity: Int
    let lotSize: Int
    let entryPrice: Double
    let currentPrice: Double?
    let investedAmount: Double
    let currentValue: Double
    let pnl: Double
    let pnlPercent: Double
    let openedAt: String
    let status: String

    enum CodingKeys: String, CodingKey {
        case id, symbol, quantity, direction, pnl, status
        case strikePrice = "strike_price"
        case optionType = "option_type"
        case expiryDate = "expiry_date"
        case lotSize = "lot_size"
        case entryPrice = "entry_price"
        case currentPrice = "current_price"
        case investedAmount = "invested_amount"
        case currentValue = "current_value"
        case pnlPercent = "pnl_percent"
        case openedAt = "opened_at"
    }
}

struct ServerTrade: Codable {
    let id: String
    let symbol: String
    let strikePrice: Double
    let optionType: String
    let direction: String
    let expiryDate: String
    let quantity: Int
    let lotSize: Int
    let price: Double
    let tradeType: String
    let pnl: Double?
    let pnlPercent: Double?
    let executedAt: String

    enum CodingKeys: String, CodingKey {
        case id, symbol, quantity, direction, price, pnl
        case strikePrice = "strike_price"
        case optionType = "option_type"
        case expiryDate = "expiry_date"
        case lotSize = "lot_size"
        case tradeType = "trade_type"
        case pnlPercent = "pnl_percent"
        case executedAt = "executed_at"
    }
}

struct ServerPerformance: Codable {
    let totalTrades: Int
    let winningTrades: Int
    let losingTrades: Int
    let winRate: Double
    let totalPnl: Double
    let bestTrade: Double?
    let worstTrade: Double?
    let averagePnl: Double

    enum CodingKeys: String, CodingKey {
        case winRate = "win_rate"
        case totalTrades = "total_trades"
        case winningTrades = "winning_trades"
        case losingTrades = "losing_trades"
        case totalPnl = "total_pnl"
        case bestTrade = "best_trade"
        case worstTrade = "worst_trade"
        case averagePnl = "average_pnl"
    }
}

struct ErrorResponse: Codable {
    let detail: String
}

struct GenericResponse: Codable {
    let status: String
    let message: String?
}

// MARK: - Errors

enum PaperTradingAPIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case notAuthenticated
    case serverError(String)
    case encodingError

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .invalidResponse:
            return "Invalid response from server"
        case .notAuthenticated:
            return "Please login to access paper trading"
        case .serverError(let message):
            return message
        case .encodingError:
            return "Failed to encode request"
        }
    }
}
