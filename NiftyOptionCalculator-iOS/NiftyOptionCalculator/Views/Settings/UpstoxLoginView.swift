import SwiftUI

struct UpstoxLoginView: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                VStack(spacing: 24) {
                    // Logo/Header
                    VStack(spacing: 16) {
                        Image(systemName: "chart.line.uptrend.xyaxis.circle.fill")
                            .font(.system(size: 80))
                            .foregroundStyle(Theme.blueGradient)

                        Text(L.upstoxConnectTitle)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Theme.textPrimary)

                        Text(L.upstoxConnectSubtitle)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .padding(.top, 40)

                    Spacer()

                    // Features
                    VStack(alignment: .leading, spacing: 16) {
                        LoginFeatureRow(
                            icon: "bolt.fill",
                            title: L.upstoxLiveData,
                            description: L.upstoxLiveDataDesc,
                            color: Theme.accentOrange
                        )

                        LoginFeatureRow(
                            icon: "chart.bar.fill",
                            title: L.upstoxAccurateGreeks,
                            description: L.upstoxAccurateGreeksDesc,
                            color: Theme.profit
                        )

                        LoginFeatureRow(
                            icon: "clock.fill",
                            title: L.upstoxAutoRefresh,
                            description: L.upstoxAutoRefreshDesc,
                            color: Theme.primaryBlue
                        )
                    }
                    .padding(.horizontal, 24)

                    Spacer()

                    // Error message
                    if let error = errorMessage {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundColor(Theme.loss)
                            .padding(.horizontal, 24)
                            .multilineTextAlignment(.center)
                    }

                    // Login Button
                    Button {
                        Task {
                            await performLogin()
                        }
                    } label: {
                        HStack(spacing: 12) {
                            if isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Image(systemName: "arrow.right.circle.fill")
                                    .font(.system(size: 20))
                            }

                            Text(isLoading ? L.upstoxConnecting : L.upstoxLoginButton)
                                .font(.system(size: 17, weight: .semibold))
                        }
                        .foregroundColor(Theme.textPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background {
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Theme.blueGradient)
                        }
                    }
                    .disabled(isLoading)
                    .padding(.horizontal, 24)

                    // No demo fallback
                    Spacer()
                        .frame(height: 16)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
    }

    private func performLogin() async {
        isLoading = true
        errorMessage = nil

        UpstoxAuthHandler.shared.startAuthentication(
            onSuccess: { [self] in
                Task { @MainActor in
                    viewModel.onUpstoxLoginSuccess()
                    isLoading = false
                    dismiss()
                }
            },
            onFailure: { [self] error in
                Task { @MainActor in
                    errorMessage = error
                    isLoading = false
                }
            }
        )
    }
}

struct LoginFeatureRow: View {
    let icon: String
    let title: String
    let description: String
    let color: Color

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(color)
                .frame(width: 44, height: 44)
                .background {
                    Circle()
                        .fill(color.opacity(0.15))
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                Text(description)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
            }

            Spacer()
        }
    }
}

// MARK: - Data Source Badge (for OptionChainView)

struct DataSourceBadge: View {
    let dataSource: DataSource
    let isAuthenticated: Bool
    var isWebSocketConnected: Bool = false
    let onTap: () -> Void

    @State private var isPulsing = false

    private var statusColor: Color {
        if isWebSocketConnected {
            return Theme.profit
        } else if isAuthenticated {
            return Theme.accentOrange
        } else {
            return Theme.textMuted
        }
    }

    private var statusText: String {
        if isWebSocketConnected {
            return "LIVE"
        } else {
            return dataSource.displayName
        }
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                // Pulsing dot for live connection
                Circle()
                    .fill(statusColor)
                    .frame(width: 8, height: 8)
                    .scaleEffect(isPulsing && isWebSocketConnected ? 1.3 : 1.0)
                    .opacity(isPulsing && isWebSocketConnected ? 0.7 : 1.0)

                Text(statusText)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(statusColor)

                if isWebSocketConnected {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(statusColor)
                } else {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Theme.textMuted)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background {
                Capsule()
                    .fill(statusColor.opacity(0.15))
            }
        }
        .onAppear {
            startPulseAnimation()
        }
        .onChange(of: isWebSocketConnected) { _, connected in
            if connected {
                startPulseAnimation()
            }
        }
    }

    private func startPulseAnimation() {
        guard isWebSocketConnected else { return }
        withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
            isPulsing = true
        }
    }
}

// MARK: - Account Menu

struct AccountMenuView: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showProviderPicker = false
    @State private var showShoonyaLogin = false

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Current Provider Card
                        VStack(spacing: 12) {
                            HStack {
                                // Show connected status for Guest mode with WebSocket OR Upstox authenticated
                                let isConnected = viewModel.isWebSocketConnected || viewModel.isUpstoxAuthenticated || viewModel.dataSource == .guest

                                Image(systemName: isConnected ? "checkmark.seal.fill" : "xmark.seal.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(isConnected ? Theme.profit : Theme.loss)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(isConnected ? L.upstoxConnected : L.upstoxNotConnected)
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(Theme.textPrimary)

                                    Text(viewModel.dataSource.displayName)
                                        .font(.system(size: 12))
                                        .foregroundColor(Theme.textSecondary)
                                }

                                Spacer()

                                // Show last updated time
                                if let lastUpdated = viewModel.lastUpdated {
                                    Text(lastUpdated, style: .time)
                                        .font(.system(size: 11))
                                        .foregroundColor(Theme.textMuted)
                                }
                            }

                            // WebSocket Status - show for Guest mode too
                            if viewModel.isUpstoxAuthenticated || viewModel.dataSource == .guest {
                                HStack(spacing: 8) {
                                    Image(systemName: viewModel.isWebSocketConnected ? "antenna.radiowaves.left.and.right" : "antenna.radiowaves.left.and.right.slash")
                                        .font(.system(size: 14))
                                        .foregroundColor(viewModel.isWebSocketConnected ? Theme.profit : Theme.textMuted)

                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(L.upstoxRealTimeStream)
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(Theme.textPrimary)

                                        Text(viewModel.webSocketStatus)
                                            .font(.system(size: 11))
                                            .foregroundColor(viewModel.isWebSocketConnected ? Theme.profit : Theme.textSecondary)
                                    }

                                    Spacer()

                                    Button {
                                        if viewModel.isWebSocketConnected {
                                            viewModel.disconnectWebSocket()
                                        } else {
                                            viewModel.connectWebSocket()
                                        }
                                    } label: {
                                        Text(viewModel.isWebSocketConnected ? "Stop" : "Start")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundColor(viewModel.isWebSocketConnected ? Theme.loss : Theme.profit)
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background {
                                                Capsule()
                                                    .fill((viewModel.isWebSocketConnected ? Theme.loss : Theme.profit).opacity(0.15))
                                            }
                                    }
                                }
                                .padding(.top, 8)
                            }

                            if let lastUpdated = viewModel.lastUpdated {
                                HStack {
                                    Image(systemName: "clock")
                                        .font(.system(size: 12))
                                    Text("\(L.optionChainLastUpdated) \(lastUpdated.formatted(date: .omitted, time: .shortened))")
                                        .font(.system(size: 12))
                                }
                                .foregroundColor(Theme.textMuted)
                            }
                        }
                        .solidCard()

                        // Data Providers Section
                        VStack(alignment: .leading, spacing: 12) {
                            Text(L.upstoxDataProviders)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Theme.textMuted)
                                .padding(.horizontal, 4)

                            ForEach(ProviderType.allCases) { provider in
                                ProviderRow(
                                    provider: provider,
                                    isConnected: isProviderConnected(provider),
                                    isSelected: false,
                                    onTap: {
                                        handleProviderTap(provider)
                                    }
                                )
                            }
                        }

                        // Quick Actions
                        VStack(spacing: 12) {
                            if viewModel.isUpstoxAuthenticated {
                                Button {
                                    Task {
                                        await viewModel.refresh()
                                    }
                                    dismiss()
                                } label: {
                                    HStack {
                                        Image(systemName: "arrow.clockwise")
                                        Text(L.upstoxRefreshData)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .foregroundColor(Theme.textMuted)
                                    }
                                    .foregroundColor(Theme.textPrimary)
                                }
                                .solidCard()

                                Button {
                                    viewModel.logoutFromUpstox()
                                    dismiss()
                                } label: {
                                    HStack {
                                        Image(systemName: "rectangle.portrait.and.arrow.right")
                                        Text(L.upstoxDisconnect)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .foregroundColor(Theme.textMuted)
                                    }
                                    .foregroundColor(Theme.loss)
                                }
                                .solidCard()
                            } else {
                                HStack {
                                    Image(systemName: "info.circle")
                                    Text(L.optionChainNoData)
                                    Spacer()
                                }
                                .foregroundColor(Theme.textMuted)
                                .solidCard()
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle(L.upstoxDataSource)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
            .sheet(isPresented: $showShoonyaLogin) {
                ShoonyaLoginView()
            }
        }
    }

    private func isProviderConnected(_ provider: ProviderType) -> Bool {
        switch provider {
        case .guest:
            return true  // Guest mode is always "connected"
        case .upstox:
            return UpstoxAPIService.shared.isAuthenticated
        case .zerodha:
            return ZerodhaAPIService.shared.isAuthenticated
        case .angelOne:
            return AngelOneAPIService.shared.isAuthenticated
        case .dhan:
            return DhanAPIService.shared.isAuthenticated
        case .shoonya:
            return ShoonyaAPIService.shared.isAuthenticated
        }
    }

    private func handleProviderTap(_ provider: ProviderType) {
        let impact = UIImpactFeedbackGenerator(style: .light)
        impact.impactOccurred()

        switch provider {
        case .guest:
            // Switch to guest mode - no login required
            ProviderManager.shared.useGuestMode()
            viewModel.switchToGuestMode()
            dismiss()
        case .upstox:
            if !UpstoxAPIService.shared.isAuthenticated {
                viewModel.showLoginSheet = true
            }
            dismiss()
        case .shoonya:
            if !ShoonyaAPIService.shared.isAuthenticated {
                showShoonyaLogin = true
            } else {
                dismiss()
            }
        default:
            // For other providers, show coming soon or handle login
            // For now, just dismiss
            dismiss()
        }
    }
}

// MARK: - Shoonya Login Sheet Extension

extension AccountMenuView {
    @ViewBuilder
    func shoonyaLoginSheet() -> some View {
        ShoonyaLoginView()
    }
}

// MARK: - Provider Row

struct ProviderRow: View {
    let provider: ProviderType
    let isConnected: Bool
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                // Provider Icon
                Image(systemName: provider.iconName)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Color(hex: provider.brandColorHex))
                    .frame(width: 44, height: 44)
                    .background {
                        Circle()
                            .fill(Color(hex: provider.brandColorHex).opacity(0.15))
                    }

                // Provider Info
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(provider.displayName)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)

                        if isConnected {
                            Text(L.upstoxConnected)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Theme.profit)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background {
                                    Capsule()
                                        .fill(Theme.profit.opacity(0.15))
                                }
                        }
                    }

                    Text(provider.tagline)
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    if let subscriptionInfo = provider.subscriptionInfo {
                        Text(subscriptionInfo)
                            .font(.system(size: 10))
                            .foregroundColor(Theme.accentOrange)
                    }
                }

                Spacer()

                // Action indicator
                Image(systemName: isConnected ? "checkmark.circle.fill" : "arrow.right.circle")
                    .font(.system(size: 20))
                    .foregroundColor(isConnected ? Theme.profit : Theme.textMuted)
            }
            .padding(14)
            .background {
                RoundedRectangle(cornerRadius: 14)
                    .fill(isConnected ? Color(hex: provider.brandColorHex).opacity(0.08) : Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(isConnected ? Color(hex: provider.brandColorHex).opacity(0.2) : Color.clear, lineWidth: 1)
                    }
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    UpstoxLoginView(viewModel: OptionChainViewModel())
}
