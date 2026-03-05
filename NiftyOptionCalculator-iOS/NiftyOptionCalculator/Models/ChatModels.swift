//
//  ChatModels.swift
//  NiftyOptionCalculator
//
//  Chat data models for AI assistant
//

import Foundation

// MARK: - Chat Session
struct ChatSession: Codable, Identifiable {
    let id: String
    let userId: String?
    let title: String
    let createdAt: Date
    let updatedAt: Date
    var messages: [ChatMessage]?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case title
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case messages
    }
}

// MARK: - Chat Message
struct ChatMessage: Codable, Identifiable, Equatable {
    let id: String
    let sessionId: String?
    let role: MessageRole
    let content: String
    let createdAt: Date
    var isStreaming: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case sessionId = "session_id"
        case role
        case content
        case createdAt = "created_at"
        case isStreaming
    }

    static func == (lhs: ChatMessage, rhs: ChatMessage) -> Bool {
        lhs.id == rhs.id && lhs.content == rhs.content && lhs.isStreaming == rhs.isStreaming
    }
}

// MARK: - Message Role
enum MessageRole: String, Codable {
    case user = "user"
    case assistant = "assistant"
    case system = "system"
}

// MARK: - API Request/Response Models

struct SendMessageRequest: Codable {
    let message: String
    let sessionId: String?
    let marketContext: ChatContext?

    enum CodingKeys: String, CodingKey {
        case message
        case sessionId = "session_id"
        case marketContext = "market_context"
    }
}

struct ChatContext: Codable {
    let spotPrice: Double?
    let selectedExpiry: String?
    let selectedStrike: Double?
    let marketTrend: String?
    let ivPercentile: Double?
    let pcr: Double?

    enum CodingKeys: String, CodingKey {
        case spotPrice = "spot_price"
        case selectedExpiry = "selected_expiry"
        case selectedStrike = "selected_strike"
        case marketTrend = "market_trend"
        case ivPercentile = "iv_percentile"
        case pcr
    }
}

struct SendMessageResponse: Codable {
    let response: String
    let sessionId: String
    let messageId: String

    enum CodingKeys: String, CodingKey {
        case response
        case sessionId = "session_id"
        case messageId = "message_id"
    }
}

struct CreateSessionRequest: Codable {
    let title: String?
}

struct CreateSessionResponse: Codable {
    let session: ChatSession
}

struct ChatSessionsResponse: Codable {
    let sessions: [ChatSession]
}

struct QuickChatRequest: Codable {
    let message: String
    let marketContext: ChatContext?

    enum CodingKeys: String, CodingKey {
        case message
        case marketContext = "market_context"
    }
}

struct QuickChatResponse: Codable {
    let response: String
}

// MARK: - Streaming Response
struct StreamChunk: Codable {
    let content: String?
    let done: Bool?
    let error: String?
    let type: String?
    let message: String?
}

// MARK: - Chat Error
enum ChatError: Error, LocalizedError {
    case networkError(String)
    case serverError(String)
    case unauthorized
    case sessionNotFound
    case rateLimited
    case invalidResponse
    case notConfigured

    var errorDescription: String? {
        switch self {
        case .networkError(let message):
            return "Network error: \(message)"
        case .serverError(let message):
            return "Server error: \(message)"
        case .unauthorized:
            return "Please login to use the chat feature"
        case .sessionNotFound:
            return "Chat session not found"
        case .rateLimited:
            return "Too many requests. Please wait a moment."
        case .invalidResponse:
            return "Invalid response from server"
        case .notConfigured:
            return "AI service not configured"
        }
    }

    var userFriendlyMessage: String {
        switch self {
        case .networkError:
            return "I'm having trouble connecting. Please check your internet and try again."
        case .serverError(let message):
            if message.contains("not configured") || message.contains("503") {
                return "I'm currently unavailable. The AI service needs to be configured by the administrator. Please try again later."
            }
            return "Something went wrong on our end. Please try again in a moment."
        case .unauthorized:
            return "Please sign in to continue our conversation."
        case .sessionNotFound:
            return "I lost track of our conversation. Let's start fresh!"
        case .rateLimited:
            return "I'm getting too many requests right now. Please wait a moment and try again."
        case .invalidResponse:
            return "I received an unexpected response. Please try asking again."
        case .notConfigured:
            return "I'm currently unavailable. The AI service needs to be configured by the administrator. Please try again later."
        }
    }
}

// MARK: - Market Sentiment
enum MarketSentiment: String {
    case bullish = "Bullish"
    case bearish = "Bearish"
    case neutral = "Neutral"

    /// Determine sentiment from Put-Call Ratio
    /// PCR > 1.1 = Bullish (more puts = hedging = bullish signal)
    /// PCR < 0.9 = Bearish (more calls = overconfidence = bearish signal)
    static func from(pcr: Double?) -> MarketSentiment {
        guard let pcr = pcr, pcr > 0 else { return .neutral }
        if pcr > 1.1 { return .bullish }
        if pcr < 0.9 { return .bearish }
        return .neutral
    }

    var emoji: String {
        switch self {
        case .bullish: return "📈"
        case .bearish: return "📉"
        case .neutral: return "➖"
        }
    }

    var primaryColor: String {
        switch self {
        case .bullish: return "bullishGreen"
        case .bearish: return "bearishRed"
        case .neutral: return "accentPrimary"
        }
    }
}

// MARK: - Suggested Questions
struct SuggestedQuestion: Identifiable {
    let id = UUID()
    let text: String
    let icon: String
    let category: QuestionCategory
}

enum QuestionCategory: String {
    case basics = "Basics"
    case strategies = "Strategies"
    case greeks = "Greeks"
    case market = "Market"
}

extension SuggestedQuestion {
    static let suggestions: [SuggestedQuestion] = [
        SuggestedQuestion(text: "What is options trading?", icon: "questionmark.circle", category: .basics),
        SuggestedQuestion(text: "Explain call and put options", icon: "arrow.up.arrow.down", category: .basics),
        SuggestedQuestion(text: "What is the best strategy for bullish market?", icon: "chart.line.uptrend.xyaxis", category: .strategies),
        SuggestedQuestion(text: "How does theta decay work?", icon: "clock", category: .greeks),
        SuggestedQuestion(text: "What is implied volatility?", icon: "waveform.path.ecg", category: .greeks),
        SuggestedQuestion(text: "Explain iron condor strategy", icon: "rectangle.split.3x3", category: .strategies),
        SuggestedQuestion(text: "What is PCR ratio?", icon: "chart.pie", category: .market),
        SuggestedQuestion(text: "How to calculate max pain?", icon: "target", category: .market),
    ]
}
