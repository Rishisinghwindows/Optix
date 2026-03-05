//
//  ChatMessageView.swift
//  NiftyOptionCalculator
//
//  Individual chat message bubble view - Premium UI
//

import SwiftUI

struct ChatMessageView: View {
    let message: ChatMessage
    var sentiment: MarketSentiment = .neutral

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if message.role == .user {
                Spacer(minLength: 24)
            }

            // Avatar for assistant
            if message.role == .assistant {
                assistantAvatar
            }

            // Message Content
            VStack(alignment: message.role == .user ? .trailing : .leading, spacing: 4) {
                if message.role == .user {
                    userBubble
                } else {
                    assistantBubble
                }

                // Timestamp
                Text(formatTime(message.createdAt))
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)
            }

            if message.role == .assistant {
                Spacer(minLength: 8)
            }

            // User avatar
            if message.role == .user {
                userAvatar
            }
        }
    }

    // MARK: - Avatars

    private var assistantAvatar: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color(hex: "6366F1"), Color(hex: "8B5CF6")],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 32, height: 32)

            Image(systemName: "sparkles")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
        }
    }

    private var userAvatar: some View {
        ZStack {
            Circle()
                .fill(Theme.sentimentGradient(for: sentiment))
                .frame(width: 32, height: 32)

            Image(systemName: "person.fill")
                .font(.system(size: 14))
                .foregroundColor(.white)
        }
    }

    // MARK: - Message Bubbles

    private var userBubble: some View {
        Text(message.content)
            .font(.system(size: 15))
            .foregroundColor(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                LinearGradient(
                    colors: [
                        Theme.sentimentColor(for: sentiment),
                        Theme.sentimentColor(for: sentiment).opacity(0.85)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var assistantBubble: some View {
        VStack(alignment: .leading, spacing: 0) {
            if message.content.isEmpty && message.isStreaming == true {
                TypingIndicatorView()
                    .padding(14)
            } else {
                SmartTextView(text: message.content)
                    .padding(14)
            }
        }
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Theme.border.opacity(0.3), lineWidth: 1)
        )
    }

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Smart Text View (Intelligent Formatting)

struct SmartTextView: View {
    let text: String

    // Finance keywords to highlight
    private let highlightTerms = [
        "IV", "implied volatility", "premium", "strike", "expiry",
        "call", "put", "OI", "open interest", "PCR", "delta", "gamma",
        "theta", "vega", "ATM", "ITM", "OTM", "NIFTY", "BANKNIFTY",
        "bullish", "bearish", "neutral", "support", "resistance",
        "breakout", "breakdown", "trend", "reversal", "volatility"
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(parseContent().enumerated()), id: \.offset) { _, block in
                renderBlock(block)
            }
        }
    }

    private func parseContent() -> [ContentBlock] {
        var blocks: [ContentBlock] = []
        let lines = text.components(separatedBy: "\n")
        var currentParagraph: [String] = []
        var inList = false
        var listItems: [String] = []

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            if trimmed.isEmpty {
                // End current paragraph/list
                if !currentParagraph.isEmpty {
                    blocks.append(.paragraph(currentParagraph.joined(separator: " ")))
                    currentParagraph = []
                }
                if inList && !listItems.isEmpty {
                    blocks.append(.bulletList(listItems))
                    listItems = []
                    inList = false
                }
                continue
            }

            // Check for headers
            if trimmed.hasPrefix("## ") {
                if !currentParagraph.isEmpty {
                    blocks.append(.paragraph(currentParagraph.joined(separator: " ")))
                    currentParagraph = []
                }
                let headerText = String(trimmed.dropFirst(3))
                blocks.append(.header(headerText))
                continue
            }

            if trimmed.hasPrefix("### ") {
                if !currentParagraph.isEmpty {
                    blocks.append(.paragraph(currentParagraph.joined(separator: " ")))
                    currentParagraph = []
                }
                let headerText = String(trimmed.dropFirst(4))
                blocks.append(.subheader(headerText))
                continue
            }

            // Check for bullet points
            if trimmed.hasPrefix("- ") || trimmed.hasPrefix("• ") || trimmed.hasPrefix("* ") {
                if !currentParagraph.isEmpty {
                    blocks.append(.paragraph(currentParagraph.joined(separator: " ")))
                    currentParagraph = []
                }
                inList = true
                let item = trimmed
                    .replacingOccurrences(of: "^[-•*]\\s*", with: "", options: .regularExpression)
                listItems.append(item)
                continue
            }

            // Check for numbered list
            if let _ = trimmed.range(of: "^\\d+\\.\\s", options: .regularExpression) {
                if !currentParagraph.isEmpty {
                    blocks.append(.paragraph(currentParagraph.joined(separator: " ")))
                    currentParagraph = []
                }
                let item = trimmed.replacingOccurrences(of: "^\\d+\\.\\s*", with: "", options: .regularExpression)
                if !inList {
                    inList = true
                }
                listItems.append(item)
                continue
            }

            // Check for callout (emoji at start)
            if let firstScalar = trimmed.unicodeScalars.first,
               firstScalar.properties.isEmoji && firstScalar.value > 0x238C {
                if !currentParagraph.isEmpty {
                    blocks.append(.paragraph(currentParagraph.joined(separator: " ")))
                    currentParagraph = []
                }
                blocks.append(.callout(trimmed))
                continue
            }

            // End list if we hit regular text
            if inList && !listItems.isEmpty {
                blocks.append(.bulletList(listItems))
                listItems = []
                inList = false
            }

            // Regular text - add to paragraph
            currentParagraph.append(trimmed)
        }

        // Flush remaining content
        if !currentParagraph.isEmpty {
            blocks.append(.paragraph(currentParagraph.joined(separator: " ")))
        }
        if !listItems.isEmpty {
            blocks.append(.bulletList(listItems))
        }

        return blocks
    }

    @ViewBuilder
    private func renderBlock(_ block: ContentBlock) -> some View {
        switch block {
        case .header(let text):
            headerView(text, isMain: true)
        case .subheader(let text):
            headerView(text, isMain: false)
        case .paragraph(let text):
            paragraphView(text)
        case .bulletList(let items):
            bulletListView(items)
        case .callout(let text):
            calloutView(text)
        }
    }

    private func headerView(_ text: String, isMain: Bool) -> some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 2)
                .fill(
                    LinearGradient(
                        colors: [Color(hex: "6366F1"), Color(hex: "8B5CF6")],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .frame(width: 3, height: isMain ? 20 : 16)

            Text(text)
                .font(.system(size: isMain ? 16 : 15, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
        }
        .padding(.top, 4)
    }

    private func paragraphView(_ text: String) -> some View {
        Text(highlightedText(text))
            .font(.system(size: 15))
            .foregroundColor(Theme.textPrimary)
            .lineSpacing(5)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func bulletListView(_ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(Color(hex: "6366F1"))
                        .frame(width: 6, height: 6)
                        .padding(.top, 7)

                    Text(highlightedText(item))
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textPrimary)
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(.leading, 4)
    }

    private func calloutView(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            if let emoji = text.first {
                Text(String(emoji))
                    .font(.system(size: 18))
            }

            Text(highlightedText(String(text.dropFirst()).trimmingCharacters(in: .whitespaces)))
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Theme.textPrimary)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: "6366F1").opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    // Highlight financial terms
    private func highlightedText(_ text: String) -> AttributedString {
        var result = AttributedString(text)

        // Bold text (**text**)
        let boldPattern = "\\*\\*([^*]+)\\*\\*"
        if let regex = try? NSRegularExpression(pattern: boldPattern) {
            let nsRange = NSRange(text.startIndex..., in: text)
            let matches = regex.matches(in: text, range: nsRange)

            for match in matches.reversed() {
                if let range = Range(match.range(at: 1), in: text) {
                    let boldText = String(text[range])
                    if let attrRange = result.range(of: "**\(boldText)**") {
                        var replacement = AttributedString(boldText)
                        replacement.font = .system(size: 15, weight: .semibold)
                        replacement.foregroundColor = Theme.textPrimary
                        result.replaceSubrange(attrRange, with: replacement)
                    }
                }
            }
        }

        // Highlight finance terms (case insensitive)
        for term in highlightTerms {
            let pattern = "\\b\(NSRegularExpression.escapedPattern(for: term))\\b"
            if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) {
                let nsText = text as NSString
                let matches = regex.matches(in: text, range: NSRange(location: 0, length: nsText.length))

                for match in matches {
                    if let swiftRange = Range(match.range, in: text) {
                        let matchedText = String(text[swiftRange])
                        if let attrRange = result.range(of: matchedText) {
                            result[attrRange].foregroundColor = Color(hex: "6366F1")
                            result[attrRange].font = .system(size: 15, weight: .medium)
                        }
                    }
                }
            }
        }

        return result
    }
}

// MARK: - Content Block Types

enum ContentBlock {
    case header(String)
    case subheader(String)
    case paragraph(String)
    case bulletList([String])
    case callout(String)
}

// MARK: - Typing Indicator

struct TypingIndicatorView: View {
    @State private var animating = false

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(Color(hex: "6366F1").opacity(0.7))
                    .frame(width: 8, height: 8)
                    .scaleEffect(animating ? 1.0 : 0.5)
                    .animation(
                        Animation.easeInOut(duration: 0.5)
                            .repeatForever(autoreverses: true)
                            .delay(Double(index) * 0.15),
                        value: animating
                    )
            }
        }
        .onAppear {
            animating = true
        }
    }
}

// MARK: - Preview

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            ChatMessageView(message: ChatMessage(
                id: "1",
                sessionId: nil,
                role: .user,
                content: "What is implied volatility?",
                createdAt: Date(),
                isStreaming: false
            ))

            ChatMessageView(message: ChatMessage(
                id: "2",
                sessionId: nil,
                role: .assistant,
                content: """
                ## Understanding Implied Volatility

                In the context of the Indian stock market, **IV** is crucial for options traders as it impacts option pricing. Higher IV generally leads to higher option premiums because the potential for large price swings increases the likelihood of the option finishing in-the-money.

                Conversely, lower IV can result in reduced option premiums. Traders often monitor IV to gauge market sentiment and to identify potential trading opportunities.

                ### Key Points

                - IV does not indicate direction of price movement
                - It only reflects expected magnitude of movement
                - Higher IV means higher premium costs
                - Useful for timing entries and exits

                💡 Tip: Monitor IV changes around major events like earnings or budget announcements!
                """,
                createdAt: Date(),
                isStreaming: false
            ))
        }
        .padding()
    }
    .background(Theme.background)
}
