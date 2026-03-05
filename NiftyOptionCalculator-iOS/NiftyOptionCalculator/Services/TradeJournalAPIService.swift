import Foundation
import os.log

private let logger = Logger(subsystem: "com.optix.app", category: "TradeJournalAPI")

// MARK: - Trade Journal API Service

@MainActor
class TradeJournalAPIService {
    static let shared = TradeJournalAPIService()

    private let session: URLSession

    private var baseURL: String {
        return "\(AppEnvironment.current.apiBaseURL)/journal"
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
            throw JournalAPIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Add auth token from Keychain via AuthManager
        if let token = AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else {
            throw JournalAPIError.notAuthenticated
        }

        if let body = body {
            request.httpBody = body
        }

        return request
    }

    private func performRequest<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw JournalAPIError.invalidResponse
        }

        if httpResponse.statusCode == 401 {
            throw JournalAPIError.notAuthenticated
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            if let errorResponse = try? JSONDecoder().decode(ErrorResponse.self, from: data) {
                throw JournalAPIError.serverError(errorResponse.detail)
            }
            throw JournalAPIError.serverError("HTTP \(httpResponse.statusCode)")
        }

        let decoder = JSONDecoder()
        return try decoder.decode(T.self, from: data)
    }

    private func performRequestNoContent(_ request: URLRequest) async throws {
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw JournalAPIError.invalidResponse
        }

        if httpResponse.statusCode == 401 {
            throw JournalAPIError.notAuthenticated
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            if let errorResponse = try? JSONDecoder().decode(ErrorResponse.self, from: data) {
                throw JournalAPIError.serverError(errorResponse.detail)
            }
            throw JournalAPIError.serverError("HTTP \(httpResponse.statusCode)")
        }
    }

    // MARK: - Journal Entries

    /// Create a new journal entry
    func createEntry(_ request: JournalCreateRequest) async throws -> JournalEntry {
        let body = try JSONEncoder().encode(request)
        let urlRequest = try createRequest(endpoint: "/entries", method: "POST", body: body)
        logger.info("Creating journal entry for \(request.symbol) \(request.strike_price) \(request.option_type)")
        return try await performRequest(urlRequest)
    }

    /// Get journal entries with optional filters
    func getEntries(
        outcome: String? = nil,
        symbol: String? = nil,
        tag: String? = nil,
        limit: Int = 50
    ) async throws -> JournalListResponse {
        var queryItems: [String] = ["limit=\(limit)"]
        if let outcome = outcome { queryItems.append("outcome=\(outcome)") }
        if let symbol = symbol { queryItems.append("symbol=\(symbol)") }
        if let tag = tag { queryItems.append("tag=\(tag)") }
        let query = queryItems.joined(separator: "&")
        let urlRequest = try createRequest(endpoint: "/entries?\(query)", method: "GET")
        logger.info("Fetching journal entries (limit=\(limit))")
        return try await performRequest(urlRequest)
    }

    /// Get a single journal entry by ID
    func getEntry(id: String) async throws -> JournalEntry {
        let urlRequest = try createRequest(endpoint: "/entries/\(id)", method: "GET")
        return try await performRequest(urlRequest)
    }

    /// Update an existing journal entry
    func updateEntry(id: String, _ request: JournalUpdateRequest) async throws -> JournalEntry {
        let body = try JSONEncoder().encode(request)
        let urlRequest = try createRequest(endpoint: "/entries/\(id)", method: "PUT", body: body)
        logger.info("Updating journal entry \(id)")
        return try await performRequest(urlRequest)
    }

    /// Delete a journal entry
    func deleteEntry(id: String) async throws {
        let urlRequest = try createRequest(endpoint: "/entries/\(id)", method: "DELETE")
        logger.info("Deleting journal entry \(id)")
        try await performRequestNoContent(urlRequest)
    }

    /// Get journal statistics
    func getStats() async throws -> JournalStats {
        let urlRequest = try createRequest(endpoint: "/stats", method: "GET")
        logger.info("Fetching journal stats")
        return try await performRequest(urlRequest)
    }
}

// MARK: - Journal API Errors

enum JournalAPIError: LocalizedError {
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
            return "Please login to access trade journal"
        case .serverError(let message):
            return message
        case .encodingError:
            return "Failed to encode request"
        }
    }
}
