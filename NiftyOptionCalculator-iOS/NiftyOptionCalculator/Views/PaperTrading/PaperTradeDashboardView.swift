import SwiftUI
import Combine

// MARK: - Paper Trade Dashboard View (Main Container)

struct PaperTradeDashboardView: View {
    @ObservedObject var optionChainVM: OptionChainViewModel
    @ObservedObject var viewModel: PaperTradingViewModel
    @EnvironmentObject var themeConfig: ThemeConfiguration
    @ObservedObject private var localization = LocalizationManager.shared
    @ObservedObject private var authManager = AuthManager.shared
    @Namespace private var tabAnimation

    // Timer for auto-refresh
    @State private var refreshTimer: Timer?
    @State private var showLoginSheet = false

    var body: some View {
        ZStack {
            // Background
            Theme.backgroundGradient.ignoresSafeArea()

            // Show login prompt if not authenticated
            if !authManager.isLoggedIn {
                PaperTradeLoginPromptView(showLoginSheet: $showLoginSheet)
            } else {
                VStack(spacing: 0) {
                    // Header with Portfolio Summary
                    PortfolioSummaryHeader(viewModel: viewModel)

                    // Tab Selector
                    PaperTradingTabBar(
                        selectedTab: $viewModel.selectedTab,
                        namespace: tabAnimation
                    )

                    // Content based on selected tab
                    TabView(selection: $viewModel.selectedTab) {
                        PaperPositionsView(viewModel: viewModel)
                            .tag(PaperTradingTab.positions)

                        PaperTradeHistoryView(viewModel: viewModel)
                            .tag(PaperTradingTab.history)

                        PaperPerformanceView(viewModel: viewModel)
                            .tag(PaperTradingTab.performance)
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                }
            }
        }
        .sheet(isPresented: $showLoginSheet) {
            LoginSheetView()
        }

        .onAppear {
            // Update position prices from option chain
            viewModel.updatePositionPrices(
                from: optionChainVM.optionChain,
                spotPrice: optionChainVM.spotPrice
            )
            // Start auto-refresh timer
            startRefreshTimer()
        }
        .onDisappear {
            // Stop timer when leaving view
            stopRefreshTimer()
        }
        .onChange(of: optionChainVM.dataVersion) { _, _ in
            // Update prices when option chain updates
            viewModel.updatePositionPrices(
                from: optionChainVM.optionChain,
                spotPrice: optionChainVM.spotPrice
            )
        }
        .sheet(isPresented: $viewModel.showTradeSheet) {
            TradeExecutionSheet(viewModel: viewModel)
        }
        .alert(L.paperTradeResetConfirmation, isPresented: $viewModel.showResetConfirmation) {
            Button(L.commonCancel, role: .cancel) {}
            Button(L.paperTradeResetPortfolio, role: .destructive) {
                viewModel.resetPortfolio()
            }
        } message: {
            Text("This will reset your portfolio to ₹10,00,000 and clear all trades and positions.")
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button {
                        viewModel.showResetConfirmation = true
                    } label: {
                        Label(L.paperTradeResetPortfolio, systemImage: "arrow.counterclockwise")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .foregroundColor(Theme.textSecondary)
                }
            }
        }
        .environmentObject(viewModel)
    }

    // MARK: - Auto-Refresh Logic

    private func startRefreshTimer() {
        // Only refresh if we have open positions
        guard viewModel.hasOpenPositions else { return }

        // Refresh every 3 seconds
        refreshTimer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { _ in
            Task { @MainActor in
                await refreshPositionPrices()
            }
        }
    }

    private func stopRefreshTimer() {
        refreshTimer?.invalidate()
        refreshTimer = nil
    }

    private func refreshPositionPrices() async {
        guard viewModel.hasOpenPositions else { return }

        // Get unique indices from open positions
        let positionIndices = Set(viewModel.portfolio.openPositions.compactMap { TradingIndex(rawValue: $0.index) })

        for index in positionIndices {
            // If the option chain is already for this index, just refresh
            if optionChainVM.selectedIndex == index {
                await optionChainVM.refresh()
            } else {
                // Fetch spot price for this index to update position values
                await fetchPricesForIndex(index)
            }
        }

        // Update position prices with current option chain data
        viewModel.updatePositionPrices(
            from: optionChainVM.optionChain,
            spotPrice: optionChainVM.spotPrice
        )
    }

    private func fetchPricesForIndex(_ index: TradingIndex) async {
        // Use UpstoxAPIService to fetch option chain for positions
        let apiService = UpstoxAPIService.shared

        // Get expiry dates for the position's index
        guard let expiry = viewModel.portfolio.openPositions
            .first(where: { $0.index == index.rawValue })?.expiryDate else { return }

        // Format expiry date
        let formatter = DateFormatter()
        formatter.dateFormat = "dd-MMM-yyyy"
        let expiryString = formatter.string(from: expiry)

        // Fetch option chain
        do {
            let optionChain = try await apiService.fetchOptionChain(index: index, expiry: expiryString)
            // Update positions with fetched prices
            for (idx, position) in viewModel.portfolio.openPositions.enumerated() {
                if position.index == index.rawValue {
                    if let row = optionChain.first(where: { $0.strikePrice == position.strikePrice }) {
                        let option = position.optionType == .call ? row.callOption : row.putOption
                        if let ltp = option?.lastTradedPrice, ltp > 0 {
                            viewModel.portfolio.openPositions[idx].currentLTP = ltp
                        }
                    }
                }
            }
            viewModel.objectWillChange.send()
        } catch {
            print("[PaperTradeDashboard] Error fetching prices: \(error)")
        }
    }
}

// MARK: - Portfolio Summary Header

private struct PortfolioSummaryHeader: View {
    @ObservedObject var viewModel: PaperTradingViewModel

    private var summary: (value: String, pnl: String, pnlPercent: String, isProfit: Bool) {
        viewModel.portfolioSummary
    }

    var body: some View {
        VStack(spacing: 12) {
            // Portfolio Value
            VStack(spacing: 4) {
                Text(L.paperTradePortfolioValue)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)

                Text(summary.value)
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.textPrimary)
                    .contentTransition(.numericText())
                    .animation(.easeInOut, value: summary.value)

                // P&L Badge
                HStack(spacing: 6) {
                    Image(systemName: summary.isProfit ? "arrow.up.right" : "arrow.down.right")
                        .font(.system(size: 12, weight: .bold))

                    Text(summary.pnl)
                        .font(.system(size: 14, weight: .semibold))

                    Text(summary.pnlPercent)
                        .font(.system(size: 12, weight: .medium))
                }
                .foregroundColor(summary.isProfit ? Theme.profit : Theme.loss)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background {
                    Capsule()
                        .fill((summary.isProfit ? Theme.profit : Theme.loss).opacity(0.15))
                }
            }

            // Quick Stats Row
            HStack(spacing: 16) {
                PaperQuickStatItem(
                    label: L.paperTradeAvailableMargin,
                    value: viewModel.availableMarginFormatted,
                    color: Theme.accentBlue
                )

                Divider()
                    .frame(height: 30)
                    .background(Theme.card)

                PaperQuickStatItem(
                    label: L.paperTradePositions,
                    value: "\(viewModel.openPositionsCount)",
                    color: Theme.accentOrange
                )

                if let metrics = viewModel.performanceMetrics {
                    Divider()
                        .frame(height: 30)
                        .background(Theme.card)

                    PaperQuickStatItem(
                        label: L.paperTradeWinRate,
                        value: String(format: "%.0f%%", metrics.winRate),
                        color: metrics.winRate >= 50 ? Theme.profit : Theme.loss
                    )
                }
            }
            .padding(.horizontal, 20)
        }
        .padding(.vertical, 16)
        .background(Theme.surface.opacity(0.3))
    }
}

private struct PaperQuickStatItem: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(color)
        }
    }
}

// MARK: - Paper Trading Tab Bar

private struct PaperTradingTabBar: View {
    @Binding var selectedTab: PaperTradingTab
    let namespace: Namespace.ID

    var body: some View {
        HStack(spacing: 0) {
            ForEach([PaperTradingTab.positions, .history, .performance], id: \.self) { tab in
                PaperTabButton(
                    tab: tab,
                    isSelected: selectedTab == tab,
                    namespace: namespace
                ) {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        selectedTab = tab
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Theme.surface.opacity(0.5))
    }
}

private struct PaperTabButton: View {
    let tab: PaperTradingTab
    let isSelected: Bool
    let namespace: Namespace.ID
    let action: () -> Void

    private var tabLabel: String {
        switch tab {
        case .dashboard: return L.paperTradeDashboard
        case .positions: return L.paperTradePositions
        case .history: return L.paperTradeHistory
        case .performance: return L.paperTradePerformance
        }
    }

    var body: some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()
            action()
        }) {
            VStack(spacing: 4) {
                Image(systemName: tab.icon)
                    .font(.system(size: 16, weight: isSelected ? .semibold : .regular))
                    .symbolEffect(.bounce.up, value: isSelected)

                Text(tabLabel)
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundColor(isSelected ? Theme.accentOrange : Theme.textMuted)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background {
                if isSelected {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Theme.accentOrange.opacity(0.15))
                        .matchedGeometryEffect(id: "activeTab", in: namespace)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Paper Trade Login Prompt View

private struct PaperTradeLoginPromptView: View {
    @Binding var showLoginSheet: Bool

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            // Icon
            ZStack {
                Circle()
                    .fill(Theme.accentOrange.opacity(0.15))
                    .frame(width: 100, height: 100)

                Image(systemName: "indianrupeesign.circle.fill")
                    .font(.system(size: 50, weight: .medium))
                    .foregroundColor(Theme.accentOrange)
            }

            // Title
            VStack(spacing: 8) {
                Text(L.paperTradeLoginRequired)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text(L.paperTradeLoginDescription)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            // Features list
            VStack(alignment: .leading, spacing: 12) {
                PaperTradeFeatureRow(icon: "chart.line.uptrend.xyaxis", text: L.paperTradeFeature1)
                PaperTradeFeatureRow(icon: "list.bullet.clipboard", text: L.paperTradeFeature2)
                PaperTradeFeatureRow(icon: "chart.bar.fill", text: L.paperTradeFeature3)
            }
            .padding(.horizontal, 40)
            .padding(.vertical, 16)

            Spacer()

            // Login Button
            Button {
                showLoginSheet = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "person.fill")
                        .font(.system(size: 16, weight: .semibold))

                    Text("Login to Continue")
                        .font(.system(size: 16, weight: .bold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Theme.accentOrange)
                        .shadow(color: Theme.accentOrange.opacity(0.3), radius: 10, x: 0, y: 5)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 100)
        }
    }
}

private struct PaperTradeFeatureRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Theme.accentOrange)
                .frame(width: 24)

            Text(text)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
        }
    }
}

// MARK: - Preview

#Preview {
    PaperTradeDashboardView(optionChainVM: OptionChainViewModel(), viewModel: PaperTradingViewModel())
        .environmentObject(ThemeConfiguration.shared)
}
