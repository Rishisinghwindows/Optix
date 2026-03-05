import SwiftUI

struct OptionChainView: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @ObservedObject var paperTradingVM: PaperTradingViewModel
    @ObservedObject var calculatorVM: CalculatorViewModel
    @EnvironmentObject var authManager: AuthManager
    @State private var showNearbyOnly = false  // Default to showing ALL strikes
    @State private var selectedStrike: Double? = nil
    @State private var showAccountMenu = false
    @State private var optionToAnalyze: OptionData? = nil
    @State private var showAIAnalysis = false
    @State private var selectedOptionForChart: OptionData? = nil
    @State private var showIndexChart = false
    @State private var showStrategyBuilder = false
    @State private var showOIAnalysis = false
    @State private var showCalculator = false
    @State private var showScreener = false
    @StateObject private var screenerViewModel = OptionScreenerViewModel()

    var body: some View {
        NavigationStack {
        ZStack {
            // Theme-aware background
            Theme.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Robinhood-style Header
                RobinhoodStyleHeader(
                    viewModel: viewModel,
                    showAccountMenu: $showAccountMenu,
                    showIndexChart: $showIndexChart
                )

                // Quick Stats Bar
                QuickStatsBar(viewModel: viewModel)

                // Expiry Pills
                ExpiryPillSelector(
                    viewModel: viewModel,
                    showNearbyOnly: $showNearbyOnly
                )

                // Option Chain Table
                ModernOptionChainTable(
                    viewModel: viewModel,
                    paperTradingVM: paperTradingVM,
                    showNearbyOnly: showNearbyOnly,
                    selectedStrike: $selectedStrike,
                    onAnalyzeOption: { option in
                        optionToAnalyze = option
                        showAIAnalysis = true
                    },
                    onChartOption: { option in
                        selectedOptionForChart = option
                    }
                )
            }

            // Empty/error placeholder
            if case .error(let message) = viewModel.loadingState {
                PlaceholderStateView(message: message)
                    .padding(.horizontal, 24)
            } else if viewModel.optionChain.isEmpty,
                      !isLoadingState(viewModel.loadingState) {
                PlaceholderStateView(message: L.optionChainNoData)
                    .padding(.horizontal, 24)
            }

            // Loading overlay
            if case .loading = viewModel.loadingState {
                ModernLoadingOverlay()
            }

            // Floating Action Buttons
            VStack {
                Spacer()
                HStack(spacing: 12) {
                    // Option Screener FAB
                    ScreenerFAB {
                        screenerViewModel.setData(
                            chain: viewModel.optionChain,
                            spot: viewModel.spotPrice
                        )
                        showScreener = true
                    }

                    // OI Analysis FAB
                    OIAnalysisFAB {
                        showOIAnalysis = true
                    }

                    // Strategy Builder FAB
                    StrategyBuilderFAB {
                        showStrategyBuilder = true
                    }
                }
                .padding(.bottom, 100) // Above tab bar
            }
        }
        .refreshable {
            await viewModel.refresh()
        }
        .onChange(of: viewModel.dataVersion) { _, _ in
            // Set ATM strike as selected by default when data loads
            if selectedStrike == nil && !viewModel.optionChain.isEmpty, let atm = viewModel.atmStrike {
                selectedStrike = atm
            }
        }
        .onAppear {
            // Set ATM strike on initial load
            if selectedStrike == nil && !viewModel.optionChain.isEmpty, let atm = viewModel.atmStrike {
                selectedStrike = atm
            }
        }
        .sheet(isPresented: $viewModel.showLoginSheet) {
            UpstoxLoginView(viewModel: viewModel)
        }
        .sheet(isPresented: $showAccountMenu) {
            AccountMenuView(viewModel: viewModel)
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $viewModel.showIndexPicker) {
            IndexPickerView(viewModel: viewModel)
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showAIAnalysis) {
            if let option = optionToAnalyze {
                StrikeAnalysisSheet(
                    option: option,
                    context: MLPredictionContext(
                        atmIV: viewModel.atmIV ?? 15,
                        pcr: viewModel.putCallRatio,
                        maxPainStrike: viewModel.maxPainStrike,
                        spotPrice: viewModel.spotPrice,
                        supportLevel: nil,
                        resistanceLevel: nil
                    )
                )
            }
        }
        // Full-screen index chart
        .fullScreenCover(isPresented: $showIndexChart) {
            FullScreenIndexChartView(
                index: viewModel.selectedIndex,
                spotPrice: viewModel.spotPrice,
                spotChange: viewModel.spotChange,
                spotChangePercent: viewModel.spotChangePercent
            )
        }
        // Strategy Builder
        .fullScreenCover(isPresented: $showStrategyBuilder) {
            StrategyBuilderView(
                spotPrice: viewModel.spotPrice,
                strikeInterval: viewModel.selectedIndex.strikeInterval,
                lotSize: viewModel.selectedIndex.lotSize,
                expiryDate: viewModel.selectedExpiry?.date ?? Date().addingTimeInterval(7 * 24 * 60 * 60),
                optionChain: viewModel.optionChain
            )
        }
        // OI Analysis Dashboard
        .fullScreenCover(isPresented: $showOIAnalysis) {
            OIAnalysisDashboardView(
                optionChain: viewModel.optionChain,
                spotPrice: viewModel.spotPrice,
                strikeInterval: viewModel.selectedIndex.strikeInterval,
                symbol: viewModel.selectedIndex.shortName
            )
        }
        // Option Screener
        .sheet(isPresented: $showScreener) {
            OptionScreenerView(viewModel: screenerViewModel)
        }
        // Paper Trading Execution Sheet
        .sheet(isPresented: $paperTradingVM.showTradeSheet) {
            TradeExecutionSheet(viewModel: paperTradingVM)
        }
        // Option strike price chart
        .sheet(item: $selectedOptionForChart) { option in
            NavigationView {
                OptionChartView(option: option)
                    .navigationTitle("\(Int(option.strikePrice)) \(option.optionType == .call ? "CE" : "PE")")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button {
                                selectedOptionForChart = nil
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 22))
                                    .foregroundColor(Theme.textMuted)
                            }
                        }
                    }
            }
            .presentationDetents([.large])

        }
        .navigationDestination(isPresented: $showCalculator) {
            CalculatorView(viewModel: calculatorVM, paperTradingVM: paperTradingVM)
                .toolbar(.hidden, for: .tabBar)
        }
        .onChange(of: viewModel.selectedOption) { _, option in
            if let option = option {
                calculatorVM.loadOption(option)
                calculatorVM.spotPrice = String(format: "%.0f", viewModel.spotPrice)
                showCalculator = true
                viewModel.selectedOption = nil
            }
        }
        // Trade Guard - shows login sheet when user tries to trade without being logged in
        .tradeGuard()
        } // NavigationStack
    }
}

// MARK: - Index Picker View

struct IndexPickerView: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 12) {
                        // NSE Indices Section
                        VStack(alignment: .leading, spacing: 8) {
                            Text(L.optionChainNSEIndices)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Theme.textMuted)
                                .padding(.horizontal, 4)

                            ForEach(TradingIndex.allCases.filter { $0.exchange == "NSE" }) { index in
                                IndexOptionRow(
                                    index: index,
                                    isSelected: viewModel.selectedIndex == index,
                                    onTap: {
                                        selectIndex(index)
                                    }
                                )
                            }
                        }

                        // BSE Indices Section
                        VStack(alignment: .leading, spacing: 8) {
                            Text(L.optionChainBSEIndices)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Theme.textMuted)
                                .padding(.horizontal, 4)
                                .padding(.top, 8)

                            ForEach(TradingIndex.allCases.filter { $0.exchange == "BSE" }) { index in
                                IndexOptionRow(
                                    index: index,
                                    isSelected: viewModel.selectedIndex == index,
                                    onTap: {
                                        selectIndex(index)
                                    }
                                )
                            }
                        }

                        // Info card
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Image(systemName: "info.circle.fill")
                                    .foregroundColor(Theme.accentBlue)
                                Text(L.optionChainIndexInfo)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(Theme.textPrimary)
                            }

                            if let selectedIndex = TradingIndex.allCases.first(where: { $0 == viewModel.selectedIndex }) {
                                VStack(alignment: .leading, spacing: 4) {
                                    InfoRow(label: L.optionChainLotSize, value: "\(selectedIndex.lotSize)")
                                    InfoRow(label: L.optionChainStrikeInterval, value: "₹\(Int(selectedIndex.strikeInterval))")
                                    InfoRow(label: L.optionChainExpiryDay, value: expiryDayName(selectedIndex.expiryDayOfWeek))
                                    InfoRow(label: L.optionChainExchange, value: selectedIndex.exchange)
                                }
                            }
                        }
                        .padding(16)
                        .background {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Theme.surface)
                        }
                        .padding(.top, 8)
                    }
                    .padding(16)
                }
            }
            .navigationTitle(L.optionChainSelectIndex)
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

    private func selectIndex(_ index: TradingIndex) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        viewModel.selectIndex(index)
        dismiss()
    }

    private func expiryDayName(_ day: Int) -> String {
        let days = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
        guard day >= 0 && day < days.count else { return "Unknown" }
        return days[day]
    }
}

// MARK: - Index Option Row

struct IndexOptionRow: View {
    let index: TradingIndex
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                // Icon
                Image(systemName: index.icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Color(hex: index.themeColor))
                    .frame(width: 44, height: 44)
                    .background {
                        Circle()
                            .fill(Color(hex: index.themeColor).opacity(0.15))
                    }

                // Index details
                VStack(alignment: .leading, spacing: 2) {
                    Text(index.displayName)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    HStack(spacing: 8) {
                        Text("Lot: \(index.lotSize)")
                            .font(.system(size: 11))
                            .foregroundColor(Theme.textMuted)

                        Text("•")
                            .foregroundColor(Theme.textMuted)

                        Text("Strike: ₹\(Int(index.strikeInterval))")
                            .font(.system(size: 11))
                            .foregroundColor(Theme.textMuted)
                    }
                }

                Spacer()

                // Selection indicator
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(Color(hex: index.themeColor))
                } else {
                    Circle()
                        .stroke(Theme.textMuted.opacity(0.3), lineWidth: 2)
                        .frame(width: 22, height: 22)
                }
            }
            .padding(14)
            .background {
                RoundedRectangle(cornerRadius: 14)
                    .fill(isSelected ? Color(hex: index.themeColor).opacity(0.1) : Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(isSelected ? Color(hex: index.themeColor).opacity(0.3) : Color.clear, lineWidth: 1)
                    }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Info Row

struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(value)
                .font(.system(size: 12, weight: .medium, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
        }
    }
}

// MARK: - Modern Loading Overlay

struct ModernLoadingOverlay: View {
    @State private var rotation: Double = 0

    var body: some View {
        ZStack {
            Color.black.opacity(0.7)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                // Custom spinner
                Circle()
                    .trim(from: 0, to: 0.7)
                    .stroke(Theme.accentGreen, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .frame(width: 40, height: 40)
                    .rotationEffect(.degrees(rotation))
                    .onAppear {
                        withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) {
                            rotation = 360
                        }
                    }

                Text(L.optionChainLoading)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Theme.textSecondary)
            }
            .padding(40)
            .background {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Theme.surface)
            }
        }
    }
}

// MARK: - Robinhood Style Header

struct RobinhoodStyleHeader: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @Binding var showAccountMenu: Bool
    @Binding var showIndexChart: Bool

    var body: some View {
        VStack(spacing: 0) {
            // Top bar with logo and status
            HStack {
                // Index Selector Button
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .light)
                    impact.impactOccurred()
                    viewModel.showIndexPicker = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: viewModel.selectedIndex.icon)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: viewModel.selectedIndex.themeColor))

                        Text(viewModel.selectedIndex.shortName)
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(Theme.textPrimary)

                        Text(L.optionChainTitle)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(Theme.textSecondary)

                        Image(systemName: "chevron.down")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Theme.textMuted)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Spacer()

                // Connection Status Badge
                ConnectionBadge(
                    isConnected: viewModel.isWebSocketConnected,
                    dataSource: viewModel.dataSource,
                    onTap: { showAccountMenu = true }
                )

                // 3-dot Menu
                Menu {
                    // View Chart
                    Button {
                        showIndexChart = true
                    } label: {
                        Label(L.optionChainViewChart, systemImage: "chart.xyaxis.line")
                    }

                    // Change Index
                    Button {
                        viewModel.showIndexPicker = true
                    } label: {
                        Label("Change Index", systemImage: "arrow.triangle.swap")
                    }

                    Divider()

                    // Refresh Data
                    Button {
                        Task {
                            await viewModel.refresh()
                        }
                    } label: {
                        Label("Refresh", systemImage: "arrow.clockwise")
                    }

                    // Account Settings
                    Button {
                        showAccountMenu = true
                    } label: {
                        Label("Account", systemImage: "person.circle")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.textSecondary)
                        .frame(width: 32, height: 32)
                        .background {
                            Circle()
                                .fill(Theme.surface)
                        }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 6)

            // Compact price + mini chart
            VStack(spacing: 4) {
                // Large price
                Text(String(format: "%.2f", viewModel.spotPrice))
                    .font(.system(size: 38, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.textPrimary)
                    .contentTransition(.numericText())

                // Change indicator
                HStack(spacing: 8) {
                    HStack(spacing: 4) {
                        Image(systemName: viewModel.spotChange >= 0 ? "arrow.up" : "arrow.down")
                            .font(.system(size: 12, weight: .bold))

                        Text(String(format: "%.2f", abs(viewModel.spotChange)))
                            .font(.system(size: 14, weight: .semibold, design: .monospaced))

                        Text(String(format: "(%.2f%%)", abs(viewModel.spotChangePercent)))
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundColor(viewModel.spotChange >= 0 ? Theme.profit : Theme.loss)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background {
                        Capsule()
                            .fill((viewModel.spotChange >= 0 ? Theme.profit : Theme.loss).opacity(0.15))
                    }

                    Text(L.optionChainToday)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Theme.textMuted)
                }

                // Mini chart (no overlay)
                MiniSparklineChart(isPositive: viewModel.spotChange >= 0)
                    .frame(height: 30)
                    .padding(.horizontal, 40)
                    .padding(.top, 2)
            }
            .padding(.bottom, 10)
        }
        .background(Theme.background)
    }
}

// MARK: - Connection Badge

struct ConnectionBadge: View {
    let isConnected: Bool
    let dataSource: DataSource
    let onTap: () -> Void
    @State private var isPulsing = false

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                // Pulsing dot
                Circle()
                    .fill(isConnected ? Theme.accentGreen : Theme.accentOrange)
                    .frame(width: 8, height: 8)
                    .scaleEffect(isPulsing && isConnected ? 1.3 : 1.0)
                    .opacity(isPulsing && isConnected ? 0.6 : 1.0)

                Text(isConnected ? "LIVE" : dataSource.displayName)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(isConnected ? Theme.accentGreen : Theme.accentOrange)

                if isConnected {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(Theme.accentGreen)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background {
                Capsule()
                    .fill((isConnected ? Theme.accentGreen : Theme.accentOrange).opacity(0.15))
                    .overlay {
                        Capsule()
                            .stroke((isConnected ? Theme.accentGreen : Theme.accentOrange).opacity(0.3), lineWidth: 1)
                    }
            }
        }
        .onAppear {
            if isConnected {
                withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                    isPulsing = true
                }
            }
        }
    }
}

// MARK: - Mini Sparkline Chart

struct MiniSparklineChart: View {
    let isPositive: Bool
    @State private var animationProgress: CGFloat = 0

    // Generate smooth curve data
    private var data: [Double] {
        if isPositive {
            return [0, 0.2, 0.15, 0.4, 0.35, 0.5, 0.45, 0.7, 0.65, 0.8, 0.75, 0.9, 1.0]
        } else {
            return [1.0, 0.9, 0.85, 0.7, 0.75, 0.5, 0.55, 0.4, 0.35, 0.25, 0.3, 0.15, 0]
        }
    }

    var body: some View {
        GeometryReader { geo in
            let stepX = geo.size.width / CGFloat(data.count - 1)

            ZStack {
                // Gradient fill
                Path { path in
                    path.move(to: CGPoint(x: 0, y: geo.size.height))
                    for (index, value) in data.enumerated() {
                        let x = CGFloat(index) * stepX
                        let y = geo.size.height - (CGFloat(value) * geo.size.height)
                        path.addLine(to: CGPoint(x: x, y: y))
                    }
                    path.addLine(to: CGPoint(x: geo.size.width, y: geo.size.height))
                    path.closeSubpath()
                }
                .fill(
                    LinearGradient(
                        colors: [
                            (isPositive ? Theme.profit : Theme.loss).opacity(0.3),
                            (isPositive ? Theme.profit : Theme.loss).opacity(0.0)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )

                // Line
                Path { path in
                    for (index, value) in data.enumerated() {
                        let x = CGFloat(index) * stepX
                        let y = geo.size.height - (CGFloat(value) * geo.size.height)
                        if index == 0 {
                            path.move(to: CGPoint(x: x, y: y))
                        } else {
                            path.addLine(to: CGPoint(x: x, y: y))
                        }
                    }
                }
                .trim(from: 0, to: animationProgress)
                .stroke(isPositive ? Theme.profit : Theme.loss, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 1.0)) {
                animationProgress = 1
            }
        }
    }
}

// MARK: - Quick Stats Bar

struct QuickStatsBar: View {
    @ObservedObject var viewModel: OptionChainViewModel

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    // PCR
                    StatChip(
                        label: "PCR",
                        value: String(format: "%.2f", viewModel.putCallRatio),
                        color: viewModel.putCallRatio > 1 ? Theme.profit : Theme.loss
                    )

                    // Max Pain
                    if let maxPain = viewModel.maxPainStrike {
                        StatChip(
                            label: "Max Pain",
                            value: String(format: "%.0f", maxPain),
                            color: Theme.accentOrange
                        )
                    }

                    // ATM IV
                    StatChip(
                        label: "ATM IV",
                        value: String(format: "%.1f%%", (viewModel.atmIV ?? 0.15) * 100),
                        color: Theme.accentPurple
                    )

                    // Support
                    let analysis = viewModel.getOIAnalysis()
                    if let support = analysis.support {
                        StatChip(
                            label: L.aiInsightsSupport,
                            value: String(format: "%.0f", support),
                            color: Theme.profit
                        )
                    }

                    // Resistance
                    if let resistance = analysis.resistance {
                        StatChip(
                            label: L.aiInsightsResistance,
                            value: String(format: "%.0f", resistance),
                            color: Theme.loss
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }

        }
        .background(Theme.surface.opacity(0.5))
    }
}

private func isLoadingState(_ state: LoadingState) -> Bool {
    if case .loading = state {
        return true
    }
    return false
}

private struct PlaceholderStateView: View {
    let message: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "bolt.horizontal.circle")
                .font(.system(size: 28))
                .foregroundColor(Theme.textMuted)
            Text(message)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Theme.textMuted)
            Text("Live data will appear here. Connect Upstox or pull to refresh.")
                .font(.system(size: 12, weight: .regular))
                .foregroundColor(Theme.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 14)
                .fill(Theme.surface.opacity(0.7))
                .overlay {
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Theme.border.opacity(0.4), lineWidth: 1)
                }
        }
    }
}

struct StatChip: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 3) {
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundColor(color)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(color.opacity(0.1))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(color.opacity(0.2), lineWidth: 1)
                }
        }
    }
}

// MARK: - Expiry Pill Selector

struct ExpiryPillSelector: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @Binding var showNearbyOnly: Bool
    @Namespace private var animation

    var body: some View {
        HStack(spacing: 12) {
            // Expiry pills
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(viewModel.expiryDates) { expiry in
                        ExpiryPill(
                            expiry: expiry,
                            isSelected: viewModel.selectedExpiry?.id == expiry.id,
                            namespace: animation
                        ) {
                            withAnimation(.spring(response: 0.3)) {
                                viewModel.selectExpiry(expiry)
                            }
                        }
                    }
                }
            }

            // ATM Filter toggle
            Button {
                let impact = UIImpactFeedbackGenerator(style: .light)
                impact.impactOccurred()
                withAnimation(.spring(response: 0.3)) {
                    showNearbyOnly.toggle()
                }
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: showNearbyOnly ? "target" : "list.bullet")
                        .font(.system(size: 12, weight: .semibold))
                    Text(showNearbyOnly ? L.optionChainNearATM : "\(L.optionChainAllStrikes) \(viewModel.optionChain.count)")
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundColor(showNearbyOnly ? Theme.accentBlue : Theme.accentGreen)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background {
                    Capsule()
                        .fill(showNearbyOnly ? Theme.accentBlue.opacity(0.15) : Theme.accentGreen.opacity(0.15))
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
        .background(Theme.background)
    }
}

struct ExpiryPill: View {
    let expiry: ExpiryDate
    let isSelected: Bool
    var namespace: Namespace.ID
    let action: () -> Void

    private var daysRemaining: Int {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.day], from: Date(), to: expiry.date)
        return max(components.day ?? 0, 0)
    }

    var body: some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()
            action()
        }) {
            VStack(spacing: 2) {
                Text(expiry.displayString)
                    .font(.system(size: 13, weight: isSelected ? .bold : .medium))

                Text("\(daysRemaining)D")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(daysRemaining <= 2 ? Theme.loss : (isSelected ? .white.opacity(0.7) : Theme.textMuted))
            }
            .foregroundColor(isSelected ? .white : Theme.textSecondary)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background {
                if isSelected {
                    Capsule()
                        .fill(Theme.accentGreen)
                        .matchedGeometryEffect(id: "selectedExpiry", in: namespace)
                } else {
                    Capsule()
                        .fill(Theme.card)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Modern Option Chain Table

struct ModernOptionChainTable: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @ObservedObject var paperTradingVM: PaperTradingViewModel
    let showNearbyOnly: Bool
    @Binding var selectedStrike: Double?
    var onAnalyzeOption: ((OptionData) -> Void)? = nil
    var onChartOption: ((OptionData) -> Void)? = nil

    // Track if we've already scrolled to ATM (only do it once on initial load)
    @State private var hasScrolledToATM = false
    // Track if user has manually scrolled - if so, don't auto-scroll
    @State private var userHasScrolled = false

    private var displayedChain: [OptionChainRow] {
        showNearbyOnly ? viewModel.nearbyStrikes : viewModel.filteredOptionChain
    }

    var body: some View {
        VStack(spacing: 0) {
            // Column Headers
            OptionChainHeader()

            // Data rows - LazyVStack handles efficient rendering
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(displayedChain) { row in
                            ModernOptionRow(
                                row: row,
                                spotPrice: viewModel.spotPrice,
                                maxOI: viewModel.maxOI,
                                isSelected: selectedStrike == row.strikePrice,
                                onCallTap: {
                                    if let call = row.callOption {
                                        triggerSelection(row.strikePrice)
                                        viewModel.selectOption(call)
                                    }
                                },
                                onPutTap: {
                                    if let put = row.putOption {
                                        triggerSelection(row.strikePrice)
                                        viewModel.selectOption(put)
                                    }
                                }
                            )
                            .id(row.strikePrice)  // Required for scrollTo to work
                            .contextMenu {
                                if let call = row.callOption {
                                    // Paper Trading - Call
                                    Section("Paper Trade CE") {
                                        Button {
                                            if let expiry = viewModel.selectedExpiry?.date {
                                                paperTradingVM.initiateTrade(
                                                    index: viewModel.selectedIndex,
                                                    option: call,
                                                    direction: .buy,
                                                    expiryDate: expiry
                                                )
                                            }
                                        } label: {
                                            Label("Paper Buy \(row.displayStrike) CE", systemImage: "cart.badge.plus")
                                        }

                                        Button {
                                            if let expiry = viewModel.selectedExpiry?.date {
                                                paperTradingVM.initiateTrade(
                                                    index: viewModel.selectedIndex,
                                                    option: call,
                                                    direction: .sell,
                                                    expiryDate: expiry
                                                )
                                            }
                                        } label: {
                                            Label("Paper Sell \(row.displayStrike) CE", systemImage: "cart.badge.minus")
                                        }
                                    }

                                    // Analysis - Call
                                    Section("Analysis") {
                                        // Chart option hidden for now
                                        // Button {
                                        //     onChartOption?(call)
                                        // } label: {
                                        //     Label("\(row.displayStrike) CE Chart", systemImage: "chart.xyaxis.line")
                                        // }
                                        Button {
                                            onAnalyzeOption?(call)
                                        } label: {
                                            Label("Analyze \(row.displayStrike) CE", systemImage: "brain.head.profile")
                                        }
                                    }
                                }

                                if let put = row.putOption {
                                    // Paper Trading - Put
                                    Section("Paper Trade PE") {
                                        Button {
                                            if let expiry = viewModel.selectedExpiry?.date {
                                                paperTradingVM.initiateTrade(
                                                    index: viewModel.selectedIndex,
                                                    option: put,
                                                    direction: .buy,
                                                    expiryDate: expiry
                                                )
                                            }
                                        } label: {
                                            Label("Paper Buy \(row.displayStrike) PE", systemImage: "cart.badge.plus")
                                        }

                                        Button {
                                            if let expiry = viewModel.selectedExpiry?.date {
                                                paperTradingVM.initiateTrade(
                                                    index: viewModel.selectedIndex,
                                                    option: put,
                                                    direction: .sell,
                                                    expiryDate: expiry
                                                )
                                            }
                                        } label: {
                                            Label("Paper Sell \(row.displayStrike) PE", systemImage: "cart.badge.minus")
                                        }
                                    }

                                    // Analysis - Put
                                    Section("Analysis") {
                                        // Chart option hidden for now
                                        // Button {
                                        //     onChartOption?(put)
                                        // } label: {
                                        //     Label("\(row.displayStrike) PE Chart", systemImage: "chart.xyaxis.line")
                                        // }
                                        Button {
                                            onAnalyzeOption?(put)
                                        } label: {
                                            Label("Analyze \(row.displayStrike) PE", systemImage: "brain.head.profile")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(.bottom, 120)
                }
                .simultaneousGesture(
                    DragGesture(minimumDistance: 5)
                        .onChanged { _ in
                            // User has manually scrolled, disable auto-scroll
                            if !userHasScrolled {
                                userHasScrolled = true
                            }
                        }
                )
                .onAppear {
                    print("📋 [ScrollView] onAppear - optionChain.count: \(viewModel.optionChain.count), hasScrolledToATM: \(hasScrolledToATM), userHasScrolled: \(userHasScrolled)")
                    // Scroll to ATM on first appear if data is already loaded
                    if !hasScrolledToATM && !userHasScrolled && !viewModel.optionChain.isEmpty {
                        if let atm = viewModel.atmStrike {
                            print("📋 [ScrollView] Will scroll to ATM: \(atm) in 0.3s")
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                if !hasScrolledToATM && !userHasScrolled {
                                    scrollToATM(proxy: proxy)
                                    hasScrolledToATM = true
                                }
                            }
                        }
                    }
                }
                .onChange(of: viewModel.optionChain.count) { oldCount, newCount in
                    print("📋 [ScrollView] onChange count - from \(oldCount) to \(newCount), hasScrolledToATM: \(hasScrolledToATM), userHasScrolled: \(userHasScrolled)")
                    // Scroll to ATM when option chain first loads (only if user hasn't scrolled)
                    if !hasScrolledToATM && !userHasScrolled && newCount > 0 && viewModel.atmStrike != nil {
                        print("📋 [ScrollView] Will scroll to ATM in 0.5s")
                        // Wait for layout to complete
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                            if !hasScrolledToATM && !userHasScrolled {
                                scrollToATM(proxy: proxy)
                                hasScrolledToATM = true
                            }
                        }
                    }
                }
                .onChange(of: viewModel.selectedExpiry?.id) { oldExpiry, newExpiry in
                    // When expiry changes, reset scroll tracking so we scroll to ATM for new expiry
                    if oldExpiry != nil && newExpiry != nil && oldExpiry != newExpiry {
                        print("📋 [ScrollView] Expiry changed, resetting scroll tracking")
                        hasScrolledToATM = false
                        userHasScrolled = false
                    }
                }
            }
        }
    }

    private func scrollToATM(proxy: ScrollViewProxy) {
        guard let targetATM = viewModel.atmStrike else {
            print("⚠️ [Scroll] No ATM strike available")
            return
        }

        // Find the exact strike price from displayedChain that matches ATM
        // This avoids floating-point comparison issues
        let nearestStrike = displayedChain.min(by: { abs($0.strikePrice - targetATM) < abs($1.strikePrice - targetATM) })

        guard let strikeToScrollTo = nearestStrike?.strikePrice else {
            print("⚠️ [Scroll] No strikes in displayed chain")
            return
        }

        print("📍 [Scroll] Scrolling to ATM strike: \(strikeToScrollTo) (target was: \(targetATM))")

        // Immediate scroll without animation for faster positioning
        proxy.scrollTo(strikeToScrollTo, anchor: .center)
        // Then smooth animation to fine-tune
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            withAnimation(.easeOut(duration: 0.3)) {
                proxy.scrollTo(strikeToScrollTo, anchor: .center)
            }
        }
    }

    private func triggerSelection(_ strike: Double) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(.easeOut(duration: 0.15)) {
            selectedStrike = strike
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            withAnimation {
                selectedStrike = nil
            }
        }
    }
}

// MARK: - Option Chain Header

struct OptionChainHeader: View {
    var body: some View {
        HStack(spacing: 0) {
            // Call side
            HStack(spacing: 0) {
                Text("OI")
                    .frame(maxWidth: .infinity)
                Text("LTP")
                    .frame(maxWidth: .infinity)
            }
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(Theme.profit)

            // Strike
            Text("STRIKE")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(Theme.accentBlue)
                .frame(width: 70)

            // Put side
            HStack(spacing: 0) {
                Text("LTP")
                    .frame(maxWidth: .infinity)
                Text("OI")
                    .frame(maxWidth: .infinity)
            }
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(Theme.loss)
        }
        .padding(.vertical, 12)
        .padding(.horizontal, 8)
        .background(Theme.surface)
    }
}

// MARK: - Modern Option Row

struct ModernOptionRow: View {
    let row: OptionChainRow
    let spotPrice: Double
    let maxOI: Int
    let isSelected: Bool
    let onCallTap: () -> Void
    let onPutTap: () -> Void

    private var isATM: Bool {
        abs(row.strikePrice - spotPrice) <= 25
    }

    private var callIsITM: Bool {
        spotPrice > row.strikePrice
    }

    private var putIsITM: Bool {
        spotPrice < row.strikePrice
    }

    private func oiRatio(oi: Int) -> CGFloat {
        guard maxOI > 0 else { return 0 }
        return min(CGFloat(oi) / CGFloat(maxOI), 1.0)
    }

    var body: some View {
        HStack(spacing: 0) {
            // Call Side
            Button(action: onCallTap) {
                HStack(spacing: 0) {
                    // OI with bar
                    ZStack(alignment: .trailing) {
                        GeometryReader { geo in
                            HStack {
                                Spacer()
                                Rectangle()
                                    .fill(Theme.profit.opacity(0.2))
                                    .frame(width: geo.size.width * oiRatio(oi: row.callOption?.openInterest ?? 0))
                            }
                        }

                        Text(formatOI(row.callOption?.openInterest))
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                            .foregroundColor(Theme.textSecondary)
                            .frame(maxWidth: .infinity)
                    }

                    // LTP
                    Text(row.callOption?.displayLTP ?? "-")
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                        .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 16)
                .background(callIsITM ? Theme.profit.opacity(0.08) : Color.clear)
            }
            .buttonStyle(OptionTapStyle())

            // Strike Price
            ZStack {
                if isATM {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Theme.accentGreen.opacity(0.2))
                        .padding(4)
                }

                VStack(spacing: 2) {
                    Text(row.displayStrike)
                        .font(.system(size: 14, weight: .heavy, design: .monospaced))
                        .foregroundColor(isATM ? Theme.accentGreen : Theme.textPrimary)

                    if isATM {
                        Text("ATM")
                            .font(.system(size: 8, weight: .black))
                            .foregroundColor(Theme.accentGreen)
                    }
                }
            }
            .frame(width: 70)

            // Put Side
            Button(action: onPutTap) {
                HStack(spacing: 0) {
                    // LTP
                    Text(row.putOption?.displayLTP ?? "-")
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                        .frame(maxWidth: .infinity)

                    // OI with bar
                    ZStack(alignment: .leading) {
                        GeometryReader { geo in
                            Rectangle()
                                .fill(Theme.loss.opacity(0.2))
                                .frame(width: geo.size.width * oiRatio(oi: row.putOption?.openInterest ?? 0))
                        }

                        Text(formatOI(row.putOption?.openInterest))
                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                            .foregroundColor(Theme.textSecondary)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.vertical, 16)
                .background(putIsITM ? Theme.loss.opacity(0.08) : Color.clear)
            }
            .buttonStyle(OptionTapStyle())
        }
        .background(isATM ? Theme.accentGreen.opacity(0.05) : (isSelected ? Theme.accentBlue.opacity(0.1) : Theme.background))
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Theme.card.opacity(0.5))
                .frame(height: 1)
        }
    }

    private func formatOI(_ oi: Int?) -> String {
        guard let oi = oi else { return "-" }
        if oi >= 100_000 {
            return String(format: "%.0fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.0fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }
}

struct OptionTapStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1.0)
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

// MARK: - Preview

#Preview {
    OptionChainView(viewModel: OptionChainViewModel(), paperTradingVM: PaperTradingViewModel(), calculatorVM: CalculatorViewModel())
        .environmentObject(AuthManager.shared)
}
