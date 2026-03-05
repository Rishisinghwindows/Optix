import SwiftUI

/// A view modifier that guards trade actions and shows login sheet if needed.
struct TradeGuardModifier: ViewModifier {
    @EnvironmentObject var authManager: AuthManager

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $authManager.showLoginSheet) {
                LoginSheetView()
                    .environmentObject(authManager)
            }
    }
}

extension View {
    /// Adds the trade guard modifier to show login sheet when needed.
    func tradeGuard() -> some View {
        modifier(TradeGuardModifier())
    }
}

// MARK: - Trade Button Component

/// A button that automatically guards trade actions and shows login if needed.
struct TradeButton: View {
    @EnvironmentObject var authManager: AuthManager

    let title: String
    let style: TradeButtonStyle
    let action: () async -> Void

    enum TradeButtonStyle {
        case buy
        case sell
        case primary
        case secondary
    }

    var body: some View {
        Button(action: handleTap) {
            Text(title)
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity)
                .padding()
                .background(backgroundColor)
                .foregroundColor(.white)
                .cornerRadius(12)
        }
    }

    private var backgroundColor: Color {
        switch style {
        case .buy:
            return .green
        case .sell:
            return .red
        case .primary:
            return .blue
        case .secondary:
            return .gray
        }
    }

    private func handleTap() {
        guard authManager.requireAuth(action: action) else {
            // Login sheet will be shown
            return
        }

        // User is authenticated, execute action
        Task {
            await action()
        }
    }
}
