import Foundation

// MARK: - IPO Service Errors

enum IPOServiceError: Error, LocalizedError {
    case invalidURL
    case networkError(Error)
    case invalidResponse
    case decodingError(Error)
    case apiError(String)
    case noData

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        case .invalidResponse:
            return "Invalid server response"
        case .decodingError(let error):
            return "Failed to decode response: \(error.localizedDescription)"
        case .apiError(let message):
            return message
        case .noData:
            return "No data received"
        }
    }
}

// MARK: - IPO Service

actor IPOService {
    static let shared = IPOService()

    private let baseURL: URL
    private let session: URLSession
    private let decoder: JSONDecoder

    // Cache
    private var cachedIPOs: [IPOItem]?
    private var cacheTimestamp: Date?
    private let cacheDuration: TimeInterval = 300 // 5 minutes

    private var analysisCache: [String: IPOAnalysisResponse] = [:]
    private var analysisCacheTimestamp: [String: Date] = [:]
    private let analysisCacheDuration: TimeInterval = 600 // 10 minutes

    private init() {
        self.baseURL = URL(string: AppEnvironment.current.apiBaseURL)!

        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)

        self.decoder = JSONDecoder()
        self.decoder.dateDecodingStrategy = .iso8601
    }

    // MARK: - Public Methods

    /// Fetch IPO list with optional filters
    func getIPOList(status: IPOStatus? = nil, ipoType: IPOType? = nil, forceRefresh: Bool = false) async throws -> IPOListResponse {
        // Check cache
        if !forceRefresh,
           let cached = cachedIPOs,
           let timestamp = cacheTimestamp,
           Date().timeIntervalSince(timestamp) < cacheDuration {
            var filtered = cached
            if let status = status {
                filtered = filtered.filter { $0.status == status }
            }
            if let ipoType = ipoType {
                filtered = filtered.filter { $0.ipoType == ipoType }
            }
            return IPOListResponse(ipos: filtered, total: filtered.count, lastUpdated: nil, source: "cache")
        }

        // Build URL with query params
        var urlComponents = URLComponents(url: baseURL.appendingPathComponent("/ipo/list"), resolvingAgainstBaseURL: false)!
        var queryItems: [URLQueryItem] = []
        if let status = status {
            queryItems.append(URLQueryItem(name: "status", value: status.rawValue))
        }
        if let ipoType = ipoType {
            queryItems.append(URLQueryItem(name: "ipo_type", value: ipoType.rawValue))
        }
        if !queryItems.isEmpty {
            urlComponents.queryItems = queryItems
        }

        guard let url = urlComponents.url else {
            throw IPOServiceError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw IPOServiceError.invalidResponse
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                if let errorJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let detail = errorJson["detail"] as? String {
                    throw IPOServiceError.apiError(detail)
                }
                throw IPOServiceError.apiError("Server error: \(httpResponse.statusCode)")
            }

            let result = try decoder.decode(IPOListResponse.self, from: data)

            // Update cache (only if no filter applied)
            if status == nil && ipoType == nil {
                cachedIPOs = result.ipos
                cacheTimestamp = Date()
            }

            return result
        } catch let error as IPOServiceError {
            throw error
        } catch let error as DecodingError {
            throw IPOServiceError.decodingError(error)
        } catch {
            throw IPOServiceError.networkError(error)
        }
    }

    /// Get IPO detail by slug
    func getIPODetail(slug: String) async throws -> IPOItem? {
        let url = baseURL.appendingPathComponent("/ipo/detail/\(slug)")

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw IPOServiceError.invalidResponse
            }

            if httpResponse.statusCode == 404 {
                return nil
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                throw IPOServiceError.apiError("Server error: \(httpResponse.statusCode)")
            }

            return try decoder.decode(IPOItem.self, from: data)
        } catch let error as IPOServiceError {
            throw error
        } catch let error as DecodingError {
            throw IPOServiceError.decodingError(error)
        } catch {
            throw IPOServiceError.networkError(error)
        }
    }

    /// Get AI analysis for an IPO
    func getAIAnalysis(companyName: String, forceRefresh: Bool = false) async throws -> IPOAnalysisResponse {
        let cacheKey = companyName.lowercased()

        // Check cache
        if !forceRefresh,
           let cached = analysisCache[cacheKey],
           let timestamp = analysisCacheTimestamp[cacheKey],
           Date().timeIntervalSince(timestamp) < analysisCacheDuration {
            return cached
        }

        let url = baseURL.appendingPathComponent("/ipo/analyze")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let body = IPOAnalysisRequest(companyName: companyName)
        request.httpBody = try JSONEncoder().encode(body)

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw IPOServiceError.invalidResponse
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                if let errorJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let detail = errorJson["detail"] as? String {
                    throw IPOServiceError.apiError(detail)
                }
                throw IPOServiceError.apiError("Server error: \(httpResponse.statusCode)")
            }

            let result = try decoder.decode(IPOAnalysisResponse.self, from: data)

            // Update cache
            analysisCache[cacheKey] = result
            analysisCacheTimestamp[cacheKey] = Date()

            return result
        } catch let error as IPOServiceError {
            throw error
        } catch let error as DecodingError {
            throw IPOServiceError.decodingError(error)
        } catch {
            throw IPOServiceError.networkError(error)
        }
    }

    /// Force refresh IPO data
    func refresh() async throws -> IPOListResponse {
        cachedIPOs = nil
        cacheTimestamp = nil
        return try await getIPOList(forceRefresh: true)
    }

    /// Clear all caches
    func clearCache() {
        cachedIPOs = nil
        cacheTimestamp = nil
        analysisCache.removeAll()
        analysisCacheTimestamp.removeAll()
    }
}
