//
//  ChatView.swift
//  NiftyOptionCalculator
//
//  Main chat interface view - Enhanced UI
//

import SwiftUI

struct ChatView: View {
    @StateObject private var viewModel = ChatViewModel()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @FocusState private var isInputFocused: Bool
    @State private var showSessionsList = false

    var body: some View {
        VStack(spacing: 0) {
            // Custom Header
            customHeader

            // Messages List
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 16) {
                        // Suggested questions (show only if few messages)
                        if viewModel.messages.count <= 2 {
                            suggestedQuestionsSection
                                .padding(.top, 8)
                        }

                        // Messages
                        ForEach(viewModel.messages) { message in
                            ChatMessageView(message: message, sentiment: viewModel.sentiment)
                                .id(message.id)
                                .transition(.asymmetric(
                                    insertion: .opacity.combined(with: .move(edge: .bottom)),
                                    removal: .opacity
                                ))
                        }

                        // Loading indicator
                        if viewModel.isLoading && !viewModel.isStreaming {
                            loadingIndicator
                        }

                        // Bottom spacer for scroll
                        Color.clear.frame(height: 8)
                    }
                    .padding(.horizontal, 4)
                }
                .onChange(of: viewModel.messages.count) { _, _ in
                    if let lastMessage = viewModel.messages.last {
                        withAnimation(.spring(response: 0.3)) {
                            proxy.scrollTo(lastMessage.id, anchor: .bottom)
                        }
                    }
                }
            }

            // Input Area
            chatInputView
        }
        .background(
            LinearGradient(
                colors: [
                    Theme.backgroundPrimary,
                    Theme.backgroundPrimary.opacity(0.95)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
        .sheet(isPresented: $showSessionsList) {
            ChatSessionsListView(viewModel: viewModel)
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.error?.localizedDescription ?? "An error occurred")
        }
    }

    // MARK: - Custom Header

    private var customHeader: some View {
        HStack(spacing: 0) {
            // Close Button
            Button(action: { dismiss() }) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 32, height: 32)
                    .background(Color.white.opacity(0.15))
                    .clipShape(Circle())
            }
            .frame(width: 50, alignment: .leading)

            Spacer()

            // Centered Title
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Color.white.opacity(0.3), Color.white.opacity(0.15)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 36, height: 36)

                    Image(systemName: "sparkles")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Optixia")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)

                    HStack(spacing: 6) {
                        Circle()
                            .fill(Color.green)
                            .frame(width: 6, height: 6)

                        Text("Online")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white.opacity(0.85))
                    }
                }
            }

            Spacer()

            // Menu Button
            Menu {
                Button(action: { viewModel.clearChat() }) {
                    Label("New Chat", systemImage: "plus.message")
                }

                Button(action: { showSessionsList = true }) {
                    Label("Chat History", systemImage: "clock.arrow.circlepath")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 32, height: 32)
                    .background(Color.white.opacity(0.15))
                    .clipShape(Circle())
            }
            .frame(width: 50, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            LinearGradient(
                colors: [Color(hex: "6366F1"), Color(hex: "8B5CF6")],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
    }

    // MARK: - Suggested Questions Section

    private var suggestedQuestionsSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header
            HStack(spacing: 8) {
                Image(systemName: "lightbulb.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.accentYellow)

                Text("Quick Questions")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)
            }

            // Questions Grid
            LazyVGrid(columns: [GridItem(.flexible())], spacing: 10) {
                ForEach(SuggestedQuestion.suggestions.prefix(6)) { question in
                    Button(action: {
                        Task {
                            await viewModel.sendSuggestedQuestion(question)
                        }
                    }) {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(Theme.accentPrimary.opacity(0.1))
                                    .frame(width: 36, height: 36)

                                Image(systemName: question.icon)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(Theme.accentPrimary)
                            }

                            Text(question.text)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(Theme.textPrimary)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)

                            Spacer()

                            Image(systemName: "arrow.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(Theme.textMuted)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(Theme.surface)
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Theme.border.opacity(0.5), lineWidth: 1)
                        )
                    }
                    .buttonStyle(ScaleButtonStyle())
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Theme.backgroundSecondary.opacity(0.5))
        )
    }

    // MARK: - Loading Indicator

    private var loadingIndicator: some View {
        HStack(spacing: 12) {
            ProgressView()
                .tint(Theme.accentPrimary)
                .scaleEffect(0.9)

            Text("Thinking...")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Theme.textSecondary)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(
            Capsule()
                .fill(Theme.surface)
                .shadow(color: Theme.shadowColor.opacity(0.1), radius: 8, y: 2)
        )
    }

    // MARK: - Chat Input View

    private var chatInputView: some View {
        let sentiment = viewModel.sentiment
        let focusColor = Theme.sentimentColor(for: sentiment)

        return VStack(spacing: 0) {
            // Divider with gradient
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [Theme.border.opacity(0.3), Theme.border.opacity(0.1)],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .frame(height: 1)

            HStack(spacing: 12) {
                // Input Field
                HStack(spacing: 8) {
                    TextField("Ask anything about options...", text: $viewModel.inputText, axis: .vertical)
                        .textFieldStyle(.plain)
                        .font(.system(size: 16))
                        .focused($isInputFocused)
                        .lineLimit(1...5)
                        .submitLabel(.send)
                        .onSubmit {
                            Task {
                                await viewModel.sendMessage()
                            }
                        }
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .background(Theme.surface)
                .cornerRadius(24)
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(
                            isInputFocused ? focusColor.opacity(0.5) : Theme.border.opacity(0.5),
                            lineWidth: isInputFocused ? 2 : 1
                        )
                )
                .animation(.easeInOut(duration: 0.2), value: isInputFocused)

                // Send Button
                Button(action: {
                    Task {
                        await viewModel.sendMessage()
                    }
                }) {
                    SendButtonView(
                        isDisabled: viewModel.inputText.isEmpty || viewModel.isLoading,
                        sentiment: viewModel.sentiment
                    )
                }
                .disabled(viewModel.inputText.isEmpty || viewModel.isLoading)
                .scaleEffect(viewModel.inputText.isEmpty ? 1.0 : 1.05)
                .animation(.spring(response: 0.3), value: viewModel.inputText.isEmpty)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            // SEBI Disclaimer
            Text("⚠️ AI responses are not investment advice. Investment in securities market is subject to market risks. Consult a SEBI-registered advisor.")
                .font(.system(size: 9))
                .foregroundColor(Theme.textMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.bottom, 6)
        }
        .background(Theme.backgroundSecondary)
    }
}

// MARK: - Send Button View

struct SendButtonView: View {
    let isDisabled: Bool
    var sentiment: MarketSentiment = .neutral

    var body: some View {
        ZStack {
            if isDisabled {
                Circle()
                    .fill(Theme.surface)
                    .frame(width: 48, height: 48)
                    .overlay(
                        Circle()
                            .stroke(Theme.border.opacity(0.5), lineWidth: 1)
                    )
            } else {
                Circle()
                    .fill(Theme.sentimentGradient(for: sentiment))
                    .frame(width: 48, height: 48)
            }

            Image(systemName: "arrow.up")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(isDisabled ? Theme.textMuted : .white)
        }
    }
}

// MARK: - Scale Button Style

struct ScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1.0)
            .opacity(configuration.isPressed ? 0.9 : 1.0)
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }
}

// MARK: - Flow Layout for Suggested Questions

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = FlowResult(in: proposal.width ?? 0, subviews: subviews, spacing: spacing)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = FlowResult(in: bounds.width, subviews: subviews, spacing: spacing)
        for (index, subview) in subviews.enumerated() {
            subview.place(at: CGPoint(x: bounds.minX + result.positions[index].x,
                                      y: bounds.minY + result.positions[index].y),
                         proposal: .unspecified)
        }
    }

    struct FlowResult {
        var size: CGSize = .zero
        var positions: [CGPoint] = []

        init(in maxWidth: CGFloat, subviews: Subviews, spacing: CGFloat) {
            var x: CGFloat = 0
            var y: CGFloat = 0
            var rowHeight: CGFloat = 0

            for subview in subviews {
                let size = subview.sizeThatFits(.unspecified)

                if x + size.width > maxWidth && x > 0 {
                    x = 0
                    y += rowHeight + spacing
                    rowHeight = 0
                }

                positions.append(CGPoint(x: x, y: y))
                rowHeight = max(rowHeight, size.height)
                x += size.width + spacing
            }

            self.size = CGSize(width: maxWidth, height: y + rowHeight)
        }
    }
}

// MARK: - Preview

#Preview {
    ChatView()
}
