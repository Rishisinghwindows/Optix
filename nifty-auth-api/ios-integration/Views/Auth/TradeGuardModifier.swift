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

// MARK: - Example Usage in Paper Trading View

/*
struct PaperTradingView: View {
    @EnvironmentObject var authManager: AuthManager
    @State private var quantity: Int = 1

    var body: some View {
        VStack {
            // Option details...

            Button("Buy") {
                executeBuy()
            }
            .buttonStyle(.borderedProminent)

            Button("Sell") {
                executeSell()
            }
            .buttonStyle(.bordered)
        }
        .tradeGuard() // Add this modifier to the view
    }

    private func executeBuy() {
        // Check if user is authenticated before executing trade
        guard authManager.requireAuth(action: { [self] in
            await executeBuyAsync()
        }) else {
            // Login sheet will be shown, trade will execute after login
            return
        }

        // User is authenticated, execute trade
        Task {
            await executeBuyAsync()
        }
    }

    private func executeBuyAsync() async {
        // Execute the actual trade
        // API call to place order...
    }

    private func executeSell() {
        guard authManager.requireAuth(action: { [self] in
            await executeSellAsync()
        }) else {
            return
        }

        Task {
            await executeSellAsync()
        }
    }

    private func executeSellAsync() async {
        // Execute the actual trade
    }
}
*/

// MARK: - Alternative: Trade Button Component

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

// MARK: - Usage Example with TradeButton

/*
struct OptionTradeSheet: View {
    @EnvironmentObject var authManager: AuthManager
    @State private var selectedOption: Option
    @State private var quantity: Int = 1

    var body: some View {
        VStack {
            // Option details UI...

            HStack(spacing: 16) {
                TradeButton(title: "Buy", style: .buy) {
                    await placeOrder(side: .buy)
                }

                TradeButton(title: "Sell", style: .sell) {
                    await placeOrder(side: .sell)
                }
            }
            .padding()
        }
        .tradeGuard()
    }

    private func placeOrder(side: OrderSide) async {
        // Place the order via API
        // This will only be called if user is authenticated
    }
}
*/
