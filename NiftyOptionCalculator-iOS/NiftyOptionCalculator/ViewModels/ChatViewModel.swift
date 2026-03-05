//
//  ChatViewModel.swift
//  NiftyOptionCalculator
//
//  ViewModel for chat functionality
//

import Foundation
import SwiftUI

// MARK: - Timeout Helper

private func withThrowingTimeout<T: Sendable>(
    seconds: TimeInterval,
    operation: @escaping @Sendable () async throws -> T
) async throws -> T {
    try await withThrowingTaskGroup(of: T.self) { group in
        group.addTask {
            try await operation()
        }
        group.addTask {
            try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            throw CancellationError()
        }
        guard let result = try await group.next() else {
            throw CancellationError()
        }
        group.cancelAll()
        return result
    }
}

@MainActor
class ChatViewModel: ObservableObject {
    // MARK: - Published Properties
    @Published var messages: [ChatMessage] = []
    @Published var inputText: String = ""
    @Published var isLoading: Bool = false
    @Published var error: ChatError?
    @Published var showError: Bool = false
    @Published var currentSessionId: String?
    @Published var sessions: [ChatSession] = []
    @Published var isStreaming: Bool = false
    @Published var streamingMessageId: String?

    // MARK: - Private Properties
    private let chatService = ChatService.shared
    private var streamingContent: String = ""

    // MARK: - Market Context (can be injected from OptionChainViewModel)
    var marketContext: ChatContext?

    // MARK: - Market Sentiment (computed from PCR)
    var sentiment: MarketSentiment {
        return MarketSentiment.from(pcr: marketContext?.pcr)
    }

    // MARK: - Initialization

    init() {
        // Add welcome message
        addWelcomeMessage()
    }

    private func addWelcomeMessage() {
        let welcomeMessage = ChatMessage(
            id: UUID().uuidString,
            sessionId: nil,
            role: .assistant,
            content: """
            Hello! I'm Optixia, your intelligent options trading assistant.

            I can help you with:
            • Understanding options concepts
            • Learning about Greeks (Delta, Theta, etc.)
            • Trading strategies (Straddle, Iron Condor, etc.)
            • Market analysis and indicators
            • Risk management tips

            What would you like to know?
            """,
            createdAt: Date(),
            isStreaming: false
        )
        messages.append(welcomeMessage)
    }

    // MARK: - Send Message

    func sendMessage() async {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        // Clear input immediately
        inputText = ""

        // Add user message
        let userMessage = ChatMessage(
            id: UUID().uuidString,
            sessionId: currentSessionId,
            role: .user,
            content: text,
            createdAt: Date(),
            isStreaming: false
        )
        messages.append(userMessage)

        // Add placeholder for assistant response
        let assistantMessageId = UUID().uuidString
        let assistantMessage = ChatMessage(
            id: assistantMessageId,
            sessionId: currentSessionId,
            role: .assistant,
            content: "",
            createdAt: Date(),
            isStreaming: true
        )
        messages.append(assistantMessage)
        streamingMessageId = assistantMessageId

        isLoading = true
        isStreaming = true
        streamingContent = ""

        // Skip streaming entirely — use quickChat directly (more reliable)
        do {
            print("💬 [Chat] Sending via quickChat: \(text)")
            let response = try await chatService.quickChat(
                message: text,
                context: marketContext
            )
            print("💬 [Chat] Got quickChat response: \(response.prefix(80))...")

            if let index = messages.firstIndex(where: { $0.id == assistantMessageId }) {
                messages[index] = ChatMessage(
                    id: assistantMessageId,
                    sessionId: currentSessionId,
                    role: .assistant,
                    content: response,
                    createdAt: Date(),
                    isStreaming: false
                )
            }
        } catch {
            print("❌ [Chat] quickChat failed: \(error)")
            if let index = messages.firstIndex(where: { $0.id == assistantMessageId }) {
                let errorText: String
                if let chatError = error as? ChatError {
                    errorText = chatError.userFriendlyMessage
                } else {
                    errorText = "Sorry, I couldn't connect. Please try again. (\(error.localizedDescription))"
                }
                messages[index] = ChatMessage(
                    id: assistantMessageId,
                    sessionId: currentSessionId,
                    role: .assistant,
                    content: errorText,
                    createdAt: Date(),
                    isStreaming: false
                )
            }
        }

        isLoading = false
        isStreaming = false
        streamingMessageId = nil
    }

    private func appendStreamingContent(_ chunk: String) {
        streamingContent += chunk

        if let index = messages.firstIndex(where: { $0.id == streamingMessageId }) {
            messages[index] = ChatMessage(
                id: streamingMessageId!,
                sessionId: currentSessionId,
                role: .assistant,
                content: streamingContent,
                createdAt: messages[index].createdAt,
                isStreaming: true
            )
        }
    }

    // MARK: - Suggested Questions

    func sendSuggestedQuestion(_ question: SuggestedQuestion) async {
        inputText = question.text
        await sendMessage()
    }

    // MARK: - Session Management

    func loadSessions() async {
        do {
            sessions = try await chatService.getSessions()
        } catch {
            print("Failed to load sessions: \(error)")
        }
    }

    func createNewSession(title: String? = nil) async {
        do {
            let session = try await chatService.createSession(title: title)
            currentSessionId = session.id
            messages = []
            addWelcomeMessage()
            await loadSessions()
        } catch let chatError as ChatError {
            self.error = chatError
            self.showError = true
        } catch {
            self.error = .networkError(error.localizedDescription)
            self.showError = true
        }
    }

    func loadSession(_ session: ChatSession) async {
        do {
            let fullSession = try await chatService.getSession(id: session.id)
            currentSessionId = fullSession.id
            messages = fullSession.messages ?? []
        } catch let chatError as ChatError {
            self.error = chatError
            self.showError = true
        } catch {
            self.error = .networkError(error.localizedDescription)
            self.showError = true
        }
    }

    func deleteSession(_ session: ChatSession) async {
        do {
            try await chatService.deleteSession(id: session.id)
            sessions.removeAll { $0.id == session.id }
            if currentSessionId == session.id {
                currentSessionId = nil
                messages = []
                addWelcomeMessage()
            }
        } catch {
            print("Failed to delete session: \(error)")
        }
    }

    // MARK: - Clear Chat

    func clearChat() {
        currentSessionId = nil
        messages = []
        addWelcomeMessage()
    }

    // MARK: - Update Market Context

    func updateMarketContext(
        spotPrice: Double?,
        selectedExpiry: String? = nil,
        selectedStrike: Double? = nil,
        marketTrend: String? = nil,
        ivPercentile: Double? = nil,
        pcr: Double? = nil
    ) {
        marketContext = ChatContext(
            spotPrice: spotPrice,
            selectedExpiry: selectedExpiry,
            selectedStrike: selectedStrike,
            marketTrend: marketTrend,
            ivPercentile: ivPercentile,
            pcr: pcr
        )
    }
}
