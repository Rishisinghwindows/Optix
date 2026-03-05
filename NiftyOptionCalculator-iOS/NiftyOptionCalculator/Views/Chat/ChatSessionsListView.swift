//
//  ChatSessionsListView.swift
//  NiftyOptionCalculator
//
//  List of chat sessions/history
//

import SwiftUI

struct ChatSessionsListView: View {
    @ObservedObject var viewModel: ChatViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.sessions.isEmpty {
                    emptyStateView
                } else {
                    sessionsList
                }
            }
            .navigationTitle("Chat History")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        Task {
                            await viewModel.createNewSession()
                            dismiss()
                        }
                    }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .task {
                await viewModel.loadSessions()
            }
        }
    }

    // MARK: - Empty State

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 60))
                .foregroundColor(Theme.textMuted)

            Text("No Chat History")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            Text("Your conversations will appear here")
                .font(.subheadline)
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.center)

            Button(action: {
                Task {
                    await viewModel.createNewSession()
                    dismiss()
                }
            }) {
                Label("Start New Chat", systemImage: "plus.message")
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Theme.accentPrimary)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .padding(.top, 8)
        }
        .padding()
    }

    // MARK: - Sessions List

    private var sessionsList: some View {
        List {
            ForEach(viewModel.sessions) { session in
                SessionRow(session: session)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        Task {
                            await viewModel.loadSession(session)
                            dismiss()
                        }
                    }
            }
            .onDelete(perform: deleteSession)
        }
        .listStyle(.insetGrouped)
    }

    private func deleteSession(at offsets: IndexSet) {
        for index in offsets {
            let session = viewModel.sessions[index]
            Task {
                await viewModel.deleteSession(session)
            }
        }
    }
}

// MARK: - Session Row

struct SessionRow: View {
    let session: ChatSession

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(session.title)
                .font(.headline)
                .foregroundColor(Theme.textPrimary)
                .lineLimit(1)

            HStack {
                Image(systemName: "clock")
                    .font(.caption)
                    .foregroundColor(Theme.textMuted)

                Text(formatDate(session.updatedAt))
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)

                if let messages = session.messages {
                    Spacer()

                    Text("\(messages.count) messages")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

// MARK: - Preview

#Preview {
    ChatSessionsListView(viewModel: ChatViewModel())
}
