//
//  ChatService.swift
//  NiftyOptionCalculator
//
//  Service for AI chatbot API calls
//

import Foundation

actor ChatService {
    static let shared = ChatService()

    private let baseURL: String
    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    private init() {
        self.baseURL = AppEnvironment.current.apiBaseURL
        self.session = URLSession.shared
        self.decoder = JSONDecoder()
        self.decoder.dateDecodingStrategy = .iso8601
        self.encoder = JSONEncoder()
        self.encoder.dateEncodingStrategy = .iso8601
    }

    // MARK: - Send Message

    func sendMessage(
        message: String,
        sessionId: String?,
        context: ChatContext? = nil
    ) async throws -> SendMessageResponse {
        let url = URL(string: "\(baseURL)/chat/message")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        // Add auth token if available
        if let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let body = SendMessageRequest(
            message: message,
            sessionId: sessionId,
            marketContext: context
        )
        request.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ChatError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200...299:
            return try decoder.decode(SendMessageResponse.self, from: data)
        case 401:
            throw ChatError.unauthorized
        case 429:
            throw ChatError.rateLimited
        default:
            let errorMessage = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw ChatError.serverError(errorMessage)
        }
    }

    // MARK: - Quick Chat (No persistence)

    func quickChat(
        message: String,
        context: ChatContext? = nil
    ) async throws -> String {
        let url = URL(string: "\(baseURL)/chat/quick")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 30 // 30 second timeout

        // Add auth token if available (optional for quick chat)
        if let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let body = QuickChatRequest(message: message, marketContext: context)
        request.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ChatError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200...299:
            let result = try decoder.decode(QuickChatResponse.self, from: data)
            return result.response
        case 401:
            throw ChatError.unauthorized
        case 429:
            throw ChatError.rateLimited
        case 503:
            throw ChatError.notConfigured
        default:
            let errorMessage = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw ChatError.serverError(errorMessage)
        }
    }

    // MARK: - Streaming Message

    func sendMessageStreaming(
        message: String,
        sessionId: String?,
        context: ChatContext? = nil,
        onChunk: @escaping (String) -> Void
    ) async throws -> String {
        let url = URL(string: "\(baseURL)/chat/message/stream")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 30 // 30 second timeout

        if let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let body = SendMessageRequest(
            message: message,
            sessionId: sessionId,
            marketContext: context
        )
        request.httpBody = try encoder.encode(body)

        let (bytes, response) = try await session.bytes(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ChatError.invalidResponse
        }

        // Handle error status codes
        switch httpResponse.statusCode {
        case 200:
            break // Success, continue
        case 401:
            throw ChatError.unauthorized
        case 429:
            throw ChatError.rateLimited
        case 503:
            throw ChatError.notConfigured
        default:
            throw ChatError.serverError("Status code: \(httpResponse.statusCode)")
        }

        var fullResponse = ""

        for try await line in bytes.lines {
            if line.hasPrefix("data: ") {
                let jsonString = String(line.dropFirst(6))
                if jsonString == "[DONE]" {
                    break
                }

                if let data = jsonString.data(using: .utf8),
                   let chunk = try? decoder.decode(StreamChunk.self, from: data) {
                    // Check for error in chunk (support both "error" and "message" keys)
                    if let error = chunk.error ?? (chunk.type == "error" ? chunk.message : nil) {
                        throw ChatError.serverError(error)
                    }
                    if let content = chunk.content {
                        fullResponse += content
                        onChunk(content)
                    }
                    // Check done via explicit field or type
                    if chunk.done == true || chunk.type == "done" {
                        break
                    }
                }
            }
        }

        return fullResponse
    }

    // MARK: - Sessions Management

    func createSession(title: String? = nil) async throws -> ChatSession {
        let url = URL(string: "\(baseURL)/chat/sessions")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        if let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else {
            throw ChatError.unauthorized
        }

        let body = CreateSessionRequest(title: title)
        request.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ChatError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200...299:
            let result = try decoder.decode(CreateSessionResponse.self, from: data)
            return result.session
        case 401:
            throw ChatError.unauthorized
        default:
            throw ChatError.serverError("Failed to create session")
        }
    }

    func getSessions() async throws -> [ChatSession] {
        let url = URL(string: "\(baseURL)/chat/sessions")!
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        if let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else {
            throw ChatError.unauthorized
        }

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ChatError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200...299:
            let result = try decoder.decode(ChatSessionsResponse.self, from: data)
            return result.sessions
        case 401:
            throw ChatError.unauthorized
        default:
            throw ChatError.serverError("Failed to fetch sessions")
        }
    }

    func getSession(id: String) async throws -> ChatSession {
        let url = URL(string: "\(baseURL)/chat/sessions/\(id)")!
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        if let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else {
            throw ChatError.unauthorized
        }

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ChatError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200...299:
            return try decoder.decode(ChatSession.self, from: data)
        case 401:
            throw ChatError.unauthorized
        case 404:
            throw ChatError.sessionNotFound
        default:
            throw ChatError.serverError("Failed to fetch session")
        }
    }

    func deleteSession(id: String) async throws {
        let url = URL(string: "\(baseURL)/chat/sessions/\(id)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"

        if let token = await AuthManager.shared.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        } else {
            throw ChatError.unauthorized
        }

        let (_, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ChatError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 200...299:
            return
        case 401:
            throw ChatError.unauthorized
        case 404:
            throw ChatError.sessionNotFound
        default:
            throw ChatError.serverError("Failed to delete session")
        }
    }
}
