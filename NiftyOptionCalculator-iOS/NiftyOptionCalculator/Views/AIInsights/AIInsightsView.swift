import SwiftUI
import UIKit

// MARK: - AI Insights View

struct AIInsightsView: View {
    @StateObject private var viewModel = AIAnalysisViewModel()
    @ObservedObject var optionChainViewModel: OptionChainViewModel
    @State private var showRefreshAnimation = false
    @State private var showIndexPicker = false
    @State private var autoRefreshEnabled = true
    @State private var nextRefreshSeconds = 60

    // Local state for independent index/expiry selection
    @State private var selectedIndex: TradingIndex = .nifty50
    @State private var selectedExpiry: ExpiryDate?
    @State private var availableExpiries: [ExpiryDate] = []

    private let autoRefreshTimer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()
    private let countdownTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            Theme.backgroundGradient
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header with Index Selector
                AIInsightsHeader(
                    viewModel: viewModel,
                    selectedIndex: selectedIndex,
                    nextRefreshSeconds: nextRefreshSeconds,
                    refreshProgress: Double(60 - nextRefreshSeconds) / 60.0,
                    onRefresh: refreshAnalysis,
                    onIndexTap: { showIndexPicker = true }
                )

                // Expiry Selector
                if !availableExpiries.isEmpty {
                    AIExpirySelector(
                        expiries: availableExpiries,
                        selectedExpiry: $selectedExpiry,
                        onExpiryChange: { expiry in
                            loadDataForExpiry(expiry)
                        }
                    )
                }

                if viewModel.isAnalyzing {
                    // Loading State
                    AILoadingView()
                } else if viewModel.hasAnalysis {
                    // Content
                    ScrollView {
                        VStack(spacing: 10) {
                            // Market Overview (Bias + Regime + Expected Range)
                            MarketBiasRiskCard(
                                viewModel: viewModel,
                                alerts: viewModel.riskSentinelAlerts
                            )
                                .padding(.horizontal, 16)

                            // Technical Analysis Card
                            if viewModel.hasTechnicalAnalysis {
                                TechnicalAnalysisCard(viewModel: viewModel)
                                    .padding(.horizontal, 16)
                            }

                            // Tab Selector
                            AITabSelector(
                                selectedTab: $viewModel.selectedTab,
                                onTabChange: { tab in
                                    // Refresh analysis when switching between Calls/Puts
                                    if tab != .market, let expiry = selectedExpiry {
                                        loadDataForExpiry(expiry)
                                    }
                                }
                            )
                                .padding(.horizontal, 16)

                            // Content based on selected tab
                            if viewModel.selectedTab == .market {
                                MarketInsightsSection(insights: viewModel.marketInsights, viewModel: viewModel)
                                    .padding(.horizontal, 16)
                            } else {
                                SuggestionsSection(
                                    suggestions: viewModel.currentSuggestions,
                                    viewModel: viewModel
                                )
                                .padding(.horizontal, 16)
                            }

                            // Disclaimer
                            DisclaimerView()
                                .padding(.horizontal, 16)
                                .padding(.bottom, 100)
                        }
                        .padding(.top, 8)
                    }
                    .refreshable {
                        await refreshAnalysisAsync()
                    }
                } else {
                    // Empty State
                    AIEmptyStateView(onAnalyze: refreshAnalysis)
                }
            }
        }
        .onAppear {
            initializeFromOptionChain()
        }
        .onChange(of: optionChainViewModel.optionChain.count) { _, _ in
            // Only auto-update if we're on the same index/expiry as option chain
            if selectedIndex == optionChainViewModel.selectedIndex &&
               selectedExpiry?.id == optionChainViewModel.selectedExpiry?.id {
                viewModel.updateData(from: optionChainViewModel)
            }
        }
        .onChange(of: optionChainViewModel.spotPrice) { _, _ in
            if selectedIndex == optionChainViewModel.selectedIndex {
                viewModel.updateData(from: optionChainViewModel)
            }
        }
        .onReceive(autoRefreshTimer) { _ in
            guard autoRefreshEnabled else { return }
            guard !viewModel.isAnalyzing else { return }
            guard !viewModel.optionChain.isEmpty else { return }

            Task {
                await refreshAnalysisAsync()
            }
        }
        .onReceive(countdownTimer) { _ in
            guard autoRefreshEnabled else { return }
            nextRefreshSeconds = max(0, nextRefreshSeconds - 1)
        }
        .sheet(isPresented: $showIndexPicker) {
            AIIndexPickerView(
                selectedIndex: $selectedIndex,
                onSelect: { index in
                    selectIndex(index)
                }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $viewModel.selectedSuggestion) { suggestion in
            SuggestionDetailSheet(
                suggestion: suggestion,
                optionChain: viewModel.optionChain,
                spotPrice: viewModel.spotPrice,
                pcr: viewModel.putCallRatio,
                maxPain: viewModel.maxPainStrike,
                atmStrike: viewModel.atmStrike ?? 0,
                indexName: selectedIndex.displayName
            )
        }
    }

    private func initializeFromOptionChain() {
        // Initialize with current option chain data
        selectedIndex = optionChainViewModel.selectedIndex
        availableExpiries = optionChainViewModel.expiryDates
        selectedExpiry = optionChainViewModel.selectedExpiry

        viewModel.updateData(from: optionChainViewModel)
        if !viewModel.optionChain.isEmpty {
            viewModel.runAnalysis()
        }
        nextRefreshSeconds = 60
    }

    private func selectIndex(_ index: TradingIndex) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()

        selectedIndex = index

        // Update the OptionChainViewModel to fetch data for this index
        // This ensures we use the same data source (demo/live) as Option Chain
        viewModel.isAnalyzing = true

        Task {
            // Use async version that waits for data to load
            await optionChainViewModel.selectIndexAsync(index)

            await MainActor.run {
                availableExpiries = optionChainViewModel.expiryDates
                selectedExpiry = optionChainViewModel.selectedExpiry
                viewModel.updateData(from: optionChainViewModel)
                viewModel.runAnalysis()
            }
        }
    }

    private func loadDataForExpiry(_ expiry: ExpiryDate) {
        let impact = UIImpactFeedbackGenerator(style: .light)
        impact.impactOccurred()

        selectedExpiry = expiry
        viewModel.isAnalyzing = true
        nextRefreshSeconds = 60

        Task {
            // Use async version that waits for data to load
            await optionChainViewModel.selectExpiryAsync(expiry)

            await MainActor.run {
                viewModel.updateData(from: optionChainViewModel)
                viewModel.runAnalysis()
            }
        }
    }

    private func refreshAnalysis() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        nextRefreshSeconds = 60

        if let expiry = selectedExpiry {
            loadDataForExpiry(expiry)
        } else {
            viewModel.runAnalysis()
        }
    }

    private func refreshAnalysisAsync() async {
        // Refresh data from OptionChainViewModel
        await optionChainViewModel.refresh()

        await MainActor.run {
            availableExpiries = optionChainViewModel.expiryDates
            selectedExpiry = optionChainViewModel.selectedExpiry
            viewModel.updateData(from: optionChainViewModel)
            viewModel.runAnalysis()
            nextRefreshSeconds = 60
        }
        try? await Task.sleep(nanoseconds: 300_000_000)
    }
}

// MARK: - Header

struct AIInsightsHeader: View {
    @ObservedObject var viewModel: AIAnalysisViewModel
    let selectedIndex: TradingIndex
    let nextRefreshSeconds: Int
    let refreshProgress: Double
    let onRefresh: () -> Void
    let onIndexTap: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Image(systemName: "brain.head.profile")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [Color(hex: "BF5AF2"), Color(hex: "FF375F")],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )

                        Text(L.aiInsightsTitle)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                    }

                    // Index Selector Button
                    Button(action: onIndexTap) {
                        HStack(spacing: 6) {
                            Image(systemName: selectedIndex.icon)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: selectedIndex.themeColor))

                            Text(selectedIndex.shortName)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(hex: selectedIndex.themeColor))

                            Image(systemName: "chevron.down")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Theme.textMuted)

                            Text("•")
                                .foregroundColor(Theme.textMuted)

                            Text(viewModel.analysisStatusText)
                                .font(.system(size: 12))
                                .foregroundColor(Theme.textMuted)

                            Text("•")
                                .foregroundColor(Theme.textMuted)

                            Text("Auto \(nextRefreshSeconds)s")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.textMuted)

                            ProgressView(value: refreshProgress)
                                .progressViewStyle(.linear)
                                .frame(width: 52)
                                .tint(Theme.primaryBlue)
                        }
                    }
                }

                Spacer()

                // Refresh Button
                Button(action: onRefresh) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(Theme.primaryBlue)
                        .frame(width: 44, height: 44)
                        .background {
                            Circle()
                                .fill(Theme.primaryBlue.opacity(0.15))
                        }
                }
                .disabled(viewModel.isAnalyzing)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)

            Divider()
                .background(Color.white.opacity(0.1))
        }
        .background(Theme.surface.opacity(0.5))
    }
}

// MARK: - Expiry Selector

struct AIExpirySelector: View {
    let expiries: [ExpiryDate]
    @Binding var selectedExpiry: ExpiryDate?
    let onExpiryChange: (ExpiryDate) -> Void
    @Namespace private var animation

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(expiries) { expiry in
                    AIExpiryPill(
                        expiry: expiry,
                        isSelected: selectedExpiry?.id == expiry.id,
                        namespace: animation
                    ) {
                        withAnimation(.spring(response: 0.3)) {
                            onExpiryChange(expiry)
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 6)
        }
        .background(Theme.surface.opacity(0.3))
    }
}

struct AIExpiryPill: View {
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
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: "BF5AF2"), Color(hex: "FF375F")],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .matchedGeometryEffect(id: "aiSelectedExpiry", in: namespace)
                } else {
                    Capsule()
                        .fill(Theme.card)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Index Picker View

struct AIIndexPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedIndex: TradingIndex
    let onSelect: (TradingIndex) -> Void

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 12) {
                        // Index Options
                        ForEach(TradingIndex.allCases, id: \.self) { index in
                            AIIndexOptionRow(
                                index: index,
                                isSelected: selectedIndex == index
                            ) {
                                selectIndex(index)
                            }
                        }
                    }
                    .padding(20)
                }
            }
            .navigationTitle(L.aiInsightsSelectIndex)
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
        onSelect(index)
        dismiss()
    }
}

struct AIIndexOptionRow: View {
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

// MARK: - Loading View

struct AILoadingView: View {
    @State private var isAnimating = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.1), lineWidth: 4)
                    .frame(width: 80, height: 80)

                Circle()
                    .trim(from: 0, to: 0.3)
                    .stroke(
                        LinearGradient(
                            colors: [Color(hex: "BF5AF2"), Color(hex: "FF375F")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        style: StrokeStyle(lineWidth: 4, lineCap: .round)
                    )
                    .frame(width: 80, height: 80)
                    .rotationEffect(.degrees(isAnimating ? 360 : 0))

                Image(systemName: "brain.head.profile")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(Color(hex: "BF5AF2"))
            }

            VStack(spacing: 8) {
                Text("Analyzing Options")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text("Scanning OI, volume, IV and Greeks...")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
            }

            Spacer()
        }
        .onAppear {
            withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: false)) {
                isAnimating = true
            }
        }
    }
}

// MARK: - Empty State

struct AIEmptyStateView: View {
    let onAnalyze: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "chart.line.uptrend.xyaxis.circle")
                .font(.system(size: 64))
                .foregroundStyle(
                    LinearGradient(
                        colors: [Theme.textMuted, Theme.textMuted.opacity(0.5)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )

            VStack(spacing: 8) {
                Text("No Analysis Yet")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text("Load option chain data and tap analyze\nto get AI-powered trade suggestions")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }

            Button(action: onAnalyze) {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles")
                    Text("Analyze Now")
                }
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
                .padding(.horizontal, 24)
                .padding(.vertical, 14)
                .background {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(
                            LinearGradient(
                                colors: [Color(hex: "BF5AF2"), Color(hex: "FF375F")],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                }
            }

            Spacer()
        }
        .padding(.horizontal, 32)
    }
}

// MARK: - Market Overview Card

struct MarketOverviewCard: View {
    @ObservedObject var viewModel: AIAnalysisViewModel

    var body: some View {
        VStack(spacing: 10) {
            // Bias Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.aiInsightsMarketBias)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Theme.textMuted)

                    HStack(spacing: 6) {
                        Image(systemName: viewModel.marketBias.icon)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(viewModel.marketBias.color)

                        Text(viewModel.marketBias.rawValue)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(viewModel.marketBias.color)
                    }
                }

                Spacer()

                // Suggestions Count
                VStack(alignment: .trailing, spacing: 0) {
                    Text("\(viewModel.totalSuggestions)")
                        .font(.system(size: 22, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)

                    Text("Suggestions")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.textMuted)
                }
            }

            Divider()
                .background(Color.white.opacity(0.1))

            // Quick Stats
            HStack(spacing: 12) {
                QuickStatItem(
                    title: "Spot",
                    value: viewModel.displaySpotPrice,
                    icon: "indianrupeesign.circle.fill",
                    color: Theme.primaryBlue
                )

                QuickStatItem(
                    title: "PCR",
                    value: viewModel.displayPCR,
                    icon: "chart.pie.fill",
                    color: viewModel.putCallRatio > 1 ? Theme.profit : Theme.loss
                )

                if let maxPain = viewModel.displayMaxPain {
                    QuickStatItem(
                        title: "Max Pain",
                        value: maxPain,
                        icon: "target",
                        color: Theme.accentOrange
                    )
                }
            }
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(viewModel.marketBias.color.opacity(0.3), lineWidth: 1)
                }
        }
    }
}

// MARK: - Market Bias + Risk Sentinel Card

struct MarketBiasRiskCard: View {
    @ObservedObject var viewModel: AIAnalysisViewModel
    let alerts: [AIAnalysisViewModel.RiskAlert]

    private func alertColor(_ severity: ConfidenceLevel) -> Color {
        severity.color
    }

    var body: some View {
        VStack(spacing: 10) {
            // Row 1: Market Bias + Suggestions count
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.aiInsightsMarketBias)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Theme.textMuted)

                    HStack(spacing: 6) {
                        Image(systemName: viewModel.marketBias.icon)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(viewModel.marketBias.color)

                        Text(viewModel.marketBias.rawValue)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(viewModel.marketBias.color)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 0) {
                    Text("\(viewModel.totalSuggestions)")
                        .font(.system(size: 22, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)

                    Text("Suggestions")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.textMuted)
                }
            }

            // Row 2: Risk alert
            if let firstAlert = alerts.first {
                HStack(spacing: 8) {
                    Circle()
                        .fill(alertColor(firstAlert.severity))
                        .frame(width: 8, height: 8)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(firstAlert.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)

                        Text(firstAlert.detail)
                            .font(.system(size: 12))
                            .foregroundColor(Theme.textSecondary)
                    }

                    Spacer()

                    Text("Live")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background {
                            Capsule()
                                .fill(Color(hex: "FF8A00"))
                        }
                }
            }

            Divider()
                .background(Color.white.opacity(0.1))

            // Row 3: Spot / PCR / Max Pain
            HStack(spacing: 12) {
                QuickStatItem(
                    title: "Spot",
                    value: viewModel.displaySpotPrice,
                    icon: "indianrupeesign.circle.fill",
                    color: Theme.primaryBlue
                )

                QuickStatItem(
                    title: "PCR",
                    value: viewModel.displayPCR,
                    icon: "chart.pie.fill",
                    color: viewModel.putCallRatio > 1 ? Theme.profit : Theme.loss
                )

                if let maxPain = viewModel.displayMaxPain {
                    QuickStatItem(
                        title: "Max Pain",
                        value: maxPain,
                        icon: "target",
                        color: Theme.accentOrange
                    )
                }

                if let vix = viewModel.aiAnalysis?.indiaVix {
                    QuickStatItem(
                        title: "India VIX",
                        value: String(format: "%.1f", vix),
                        icon: "waveform.path.ecg",
                        color: vix > 20 ? Theme.loss : (vix > 15 ? Theme.accentOrange : Theme.profit)
                    )
                }
            }

            // Row 4: Market Regime + Expected Range
            if viewModel.marketRegime != nil || viewModel.expectedMoveRange != nil {
                Divider()
                    .background(Color.white.opacity(0.1))

                HStack(spacing: 0) {
                    // Market Regime (left)
                    if let regime = viewModel.marketRegime {
                        HStack(spacing: 6) {
                            Image(systemName: regime.icon)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(regime.color)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(regime.rawValue)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Theme.textPrimary)
                                Text(regime.description)
                                    .font(.system(size: 10))
                                    .foregroundColor(Theme.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    // Expected Range (right)
                    if let rangeText = viewModel.displayExpectedMoveRange,
                       let range = viewModel.expectedMoveRange {
                        VStack(alignment: .trailing, spacing: 4) {
                            HStack(spacing: 4) {
                                Image(systemName: "ruler")
                                    .font(.system(size: 10))
                                    .foregroundColor(Theme.primaryBlue)
                                Text(rangeText)
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(Theme.textPrimary)
                            }

                            // Range bar
                            GeometryReader { geo in
                                let totalWidth = geo.size.width
                                let rangeSpan = range.high - range.low
                                let spotFraction = viewModel.spotPrice > 0 && rangeSpan > 0
                                    ? CGFloat((viewModel.spotPrice - range.low) / rangeSpan)
                                    : 0.5
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(Theme.primaryBlue.opacity(0.2))
                                        .frame(height: 4)

                                    Circle()
                                        .fill(Theme.primaryBlue)
                                        .frame(width: 8, height: 8)
                                        .offset(x: max(0, min(totalWidth - 8, totalWidth * spotFraction - 4)))
                                }
                            }
                            .frame(height: 8)
                        }
                        .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                }
            }
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(viewModel.marketBias.color.opacity(0.22), lineWidth: 1)
                }
        }
    }
}

// MARK: - Market Summary Card

struct MarketSummaryCard: View {
    let items: [(label: String, value: String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Market Summary")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.textPrimary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(items.indices, id: \.self) { idx in
                    let item = items[idx]
                    VStack(alignment: .leading, spacing: 6) {
                        Text(item.label)
                            .font(.system(size: 11))
                            .foregroundColor(Theme.textMuted)

                        Text(item.value)
                            .font(.system(size: 15, weight: .semibold, design: .monospaced))
                            .foregroundColor(Theme.textPrimary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .solidCard()
    }
}

// MARK: - Risk Sentinel Card

struct RiskSentinelCard: View {
    let alerts: [AIAnalysisViewModel.RiskAlert]

    private func alertColor(_ severity: ConfidenceLevel) -> Color {
        severity.color
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("AI Risk Sentinel")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                Text("Live")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background {
                        Capsule()
                            .fill(Color(hex: "FF8A00"))
                    }
            }

            ForEach(alerts) { alert in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(alertColor(alert.severity))
                        .frame(width: 8, height: 8)
                        .padding(.top, 6)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(alert.title)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)

                        Text(alert.detail)
                            .font(.system(size: 12))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
        .solidCard()
    }
}

struct QuickStatItem: View {
    let title: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(color)

            Text(value)
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)

            Text(title)
                .font(.system(size: 9))
                .foregroundColor(Theme.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Technical Analysis Card

struct TechnicalAnalysisCard: View {
    @ObservedObject var viewModel: AIAnalysisViewModel

    var body: some View {
        VStack(spacing: 16) {
            // Header with Trend and Signal
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Technical Analysis")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Theme.textMuted)

                    HStack(spacing: 8) {
                        Image(systemName: viewModel.trendDirection.icon)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(viewModel.trendDirection.color)

                        Text(viewModel.displayTrend)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(viewModel.trendDirection.color)
                    }
                }

                Spacer()

                // Signal Badge
                if let signal = viewModel.technicalSignal {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(signal.direction.rawValue)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(signal.direction.color)

                        Text("\(Int(signal.confidence))% confidence")
                            .font(.system(size: 10))
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(signal.direction.color.opacity(0.15))
                    }
                }
            }

            Divider()
                .background(Color.white.opacity(0.1))

            // Indicators Grid
            HStack(spacing: 12) {
                // RSI
                TechnicalIndicatorItem(
                    title: "RSI",
                    value: viewModel.displayRSI,
                    subtitle: viewModel.rsiCondition.rawValue,
                    color: viewModel.rsiCondition.color
                )

                // MACD
                if let macd = viewModel.macdResult {
                    TechnicalIndicatorItem(
                        title: "MACD",
                        value: macd.histogram > 0 ? "Bullish" : "Bearish",
                        subtitle: macd.crossover == .bullish ? "Crossover ↑" :
                                  macd.crossover == .bearish ? "Crossover ↓" : "No Signal",
                        color: macd.histogram > 0 ? Theme.profit : Theme.loss
                    )
                }

                // SMA 20/50
                if let sma20 = viewModel.displaySMA20 {
                    TechnicalIndicatorItem(
                        title: "SMA 20",
                        value: sma20,
                        subtitle: viewModel.spotPrice > (viewModel.technicalAnalysis?.sma20 ?? 0) ? "Above" : "Below",
                        color: viewModel.spotPrice > (viewModel.technicalAnalysis?.sma20 ?? 0) ? Theme.profit : Theme.loss
                    )
                }

                // VWAP
                if let vwap = viewModel.displayVWAP {
                    TechnicalIndicatorItem(
                        title: "VWAP",
                        value: vwap,
                        subtitle: viewModel.spotPrice > (viewModel.technicalAnalysis?.vwap ?? 0) ? "Above" : "Below",
                        color: viewModel.spotPrice > (viewModel.technicalAnalysis?.vwap ?? 0) ? Theme.profit : Theme.loss
                    )
                }
            }

            // Support & Resistance
            if !viewModel.supportLevels.isEmpty || !viewModel.resistanceLevels.isEmpty {
                Divider()
                    .background(Color.white.opacity(0.1))

                HStack(spacing: 16) {
                    // Support Levels
                    if !viewModel.supportLevels.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.down.to.line")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(Theme.profit)
                                Text("Support")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(Theme.textMuted)
                            }

                            Text(viewModel.supportLevels.prefix(2).map { String(format: "%.0f", $0) }.joined(separator: ", "))
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                .foregroundColor(Theme.profit)
                        }
                    }

                    Spacer()

                    // Resistance Levels
                    if !viewModel.resistanceLevels.isEmpty {
                        VStack(alignment: .trailing, spacing: 4) {
                            HStack(spacing: 4) {
                                Text("Resistance")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(Theme.textMuted)
                                Image(systemName: "arrow.up.to.line")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(Theme.loss)
                            }

                            Text(viewModel.resistanceLevels.prefix(2).map { String(format: "%.0f", $0) }.joined(separator: ", "))
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                .foregroundColor(Theme.loss)
                        }
                    }
                }
            }

            // Candlestick Patterns Section
            if viewModel.hasCandlestickPatterns {
                Divider()
                    .background(Color.white.opacity(0.1))

                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "chart.bar.doc.horizontal")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "BF5AF2"))
                        Text("Candlestick Patterns")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Theme.textMuted)
                    }

                    ForEach(viewModel.candlestickPatterns.prefix(3)) { pattern in
                        CandlestickPatternRow(pattern: pattern)
                    }
                }
            }
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(
                            LinearGradient(
                                colors: [Color(hex: "BF5AF2").opacity(0.3), Color(hex: "FF375F").opacity(0.3)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                }
        }
    }
}

struct CandlestickPatternRow: View {
    let pattern: CandlestickPattern

    var body: some View {
        HStack(spacing: 10) {
            // Pattern Icon
            Image(systemName: pattern.icon)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(pattern.color)
                .frame(width: 24, height: 24)
                .background {
                    Circle()
                        .fill(pattern.color.opacity(0.15))
                }

            // Pattern Info
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(pattern.displayName)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    Text("•")
                        .foregroundColor(Theme.textMuted)

                    Text(pattern.signal.label)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(pattern.color)
                }

                Text(pattern.description)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(1)
            }

            Spacer()

            // Importance Badge
            if pattern.importance == .high {
                Text("HIGH")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background {
                        Capsule()
                            .fill(pattern.color)
                    }
            }
        }
        .padding(.vertical, 4)
    }
}

struct TechnicalIndicatorItem: View {
    let title: String
    let value: String
    let subtitle: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(color)

            Text(subtitle)
                .font(.system(size: 9))
                .foregroundColor(color.opacity(0.8))
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Tab Selector

struct AITabSelector: View {
    @Binding var selectedTab: InsightTab
    var onTabChange: ((InsightTab) -> Void)? = nil
    @Namespace private var animation

    var body: some View {
        HStack(spacing: 8) {
            ForEach(InsightTab.allCases, id: \.self) { tab in
                Button {
                    let previousTab = selectedTab
                    // Haptic feedback
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()

                    withAnimation(.spring(response: 0.3)) {
                        selectedTab = tab
                    }
                    // Trigger refresh when switching between Calls/Puts (not just selecting same tab)
                    if previousTab != tab {
                        onTabChange?(tab)
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 14, weight: .semibold))

                        Text(tab.rawValue)
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundColor(selectedTab == tab ? .white : Theme.textSecondary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background {
                        if selectedTab == tab {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(tab.color)
                                .matchedGeometryEffect(id: "tab", in: animation)
                        } else {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Theme.surface)
                        }
                    }
                }
            }
        }
        .padding(4)
        .background {
            RoundedRectangle(cornerRadius: 14)
                .fill(Theme.surfaceElevated)
        }
    }
}

// MARK: - Suggestions Section (Two-Tier)

struct SuggestionsSection: View {
    let suggestions: [AITradeSuggestion]
    @ObservedObject var viewModel: AIAnalysisViewModel

    private var topPicks: [AITradeSuggestion] {
        suggestions.filter { $0.tier == .topPick }
    }

    private var worthWatching: [AITradeSuggestion] {
        suggestions.filter { $0.tier == .worthWatching }
    }

    var body: some View {
        VStack(spacing: 16) {
            if suggestions.isEmpty {
                NoSuggestionsView()
            } else {
                // Top Picks (full cards with star badge)
                if !topPicks.isEmpty {
                    VStack(spacing: 8) {
                        HStack(spacing: 6) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.yellow)
                            Text("Top Picks")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Theme.textPrimary)
                            Spacer()
                            Text("\(topPicks.count)")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(Theme.textMuted)
                        }

                        ForEach(topPicks) { suggestion in
                            TradeSuggestionCard(
                                suggestion: suggestion,
                                showTierBadge: true
                            ) {
                                viewModel.selectSuggestion(suggestion)
                            }
                        }
                    }
                }

                // Worth Watching (compact cards)
                if !worthWatching.isEmpty {
                    VStack(spacing: 8) {
                        HStack(spacing: 6) {
                            Image(systemName: "eye.fill")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.accentOrange)
                            Text("Worth Watching")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Theme.textPrimary)
                            Spacer()
                            Text("\(worthWatching.count)")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(Theme.textMuted)
                        }

                        ForEach(worthWatching) { suggestion in
                            CompactSuggestionCard(suggestion: suggestion) {
                                viewModel.selectSuggestion(suggestion)
                            }
                        }
                    }
                }

                // If ALL are top picks or ALL are worth watching, no section headers needed
                // (handled above with empty checks)
            }
        }
    }
}

struct NoSuggestionsView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 32))
                .foregroundColor(Theme.textMuted)

            Text("No suggestions found")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Theme.textSecondary)

            Text("Try a different expiry or wait for market data to update")
                .font(.system(size: 12))
                .foregroundColor(Theme.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
        .solidCard()
    }
}

// MARK: - Market Insights Section

struct MarketInsightsSection: View {
    let insights: [MarketInsight]
    @ObservedObject var viewModel: AIAnalysisViewModel

    var body: some View {
        VStack(spacing: 16) {
            // Unusual Activity Section
            if viewModel.hasUnusualActivity {
                UnusualActivitySection(activities: viewModel.unusualActivities)
            }

            // Strategy Suggestions Section
            if viewModel.hasStrategySuggestions {
                StrategySuggestionsSection(strategies: viewModel.strategySuggestions)
            }

            // Market Insights
            if insights.isEmpty && !viewModel.hasUnusualActivity && !viewModel.hasStrategySuggestions {
                VStack(spacing: 12) {
                    Image(systemName: "lightbulb")
                        .font(.system(size: 32))
                        .foregroundColor(Theme.textMuted)

                    Text("No special insights at this time")
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textSecondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
                .solidCard()
            } else if !insights.isEmpty {
                ForEach(insights) { insight in
                    MarketInsightCard(insight: insight)
                }
            }
        }
    }
}

// MARK: - Unusual Activity Section

struct UnusualActivitySection: View {
    let activities: [UnusualActivity]
    @State private var isExpanded = true

    var body: some View {
        VStack(spacing: 12) {
            // Header
            Button(action: {
                withAnimation(.spring(response: 0.3)) {
                    isExpanded.toggle()
                }
            }) {
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "FF9500"))

                        Text("Unusual Activity")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.textPrimary)

                        // Count Badge
                        Text("\(activities.count)")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .background {
                                Capsule()
                                    .fill(Color(hex: "FF9500"))
                            }
                    }

                    Spacer()

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textMuted)
                }
            }
            .buttonStyle(.plain)

            if isExpanded {
                VStack(spacing: 10) {
                    ForEach(activities.prefix(5)) { activity in
                        UnusualActivityRow(activity: activity)
                    }
                }
            }
        }
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "FF9500").opacity(0.3), lineWidth: 1)
                }
        }
    }
}

struct UnusualActivityRow: View {
    let activity: UnusualActivity

    var body: some View {
        HStack(spacing: 12) {
            // Activity Type Icon
            Image(systemName: activity.activityType.icon)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(activity.significance.color)
                .frame(width: 32, height: 32)
                .background {
                    Circle()
                        .fill(activity.significance.color.opacity(0.15))
                }

            // Details
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(activity.option.displayStrike)
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)

                    Text(activity.option.optionType == .call ? "CE" : "PE")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background {
                            Capsule()
                                .fill(activity.option.optionType == .call ? Theme.profit : Theme.loss)
                        }

                    if let direction = activity.tradeDirection {
                        Text(direction.rawValue)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(direction == .buy ? Theme.profit : Theme.loss)
                    }
                }

                Text(activity.description)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(1)
            }

            Spacer()

            // Value Badge
            VStack(alignment: .trailing, spacing: 2) {
                Text(activity.displayValue)
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(activity.significance.color)

                Text(activity.activityType.rawValue)
                    .font(.system(size: 9))
                    .foregroundColor(Theme.textMuted)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Strategy Suggestions Section

struct StrategySuggestionsSection: View {
    let strategies: [StrategySuggestion]
    @State private var isExpanded = true

    var body: some View {
        VStack(spacing: 12) {
            // Header
            Button(action: {
                withAnimation(.spring(response: 0.3)) {
                    isExpanded.toggle()
                }
            }) {
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: "rectangle.stack.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "BF5AF2"))

                        Text("Strategy Ideas")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.textPrimary)

                        // Count Badge
                        Text("\(strategies.count)")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .background {
                                Capsule()
                                    .fill(Color(hex: "BF5AF2"))
                            }
                    }

                    Spacer()

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textMuted)
                }
            }
            .buttonStyle(.plain)

            if isExpanded {
                VStack(spacing: 10) {
                    ForEach(strategies.prefix(3)) { strategy in
                        StrategySuggestionRow(strategy: strategy)
                    }
                }
            }
        }
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "BF5AF2").opacity(0.3), lineWidth: 1)
                }
        }
    }
}

struct StrategySuggestionRow: View {
    let strategy: StrategySuggestion
    @State private var showDetail = false

    var body: some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()
            showDetail = true
        }) {
            VStack(spacing: 10) {
                // Strategy Header
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: strategy.strategyType.icon)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(strategy.strategyType.color)

                        Text(strategy.strategyType.rawValue)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                    }

                    Spacer()

                    // Score Gauge
                    if let score = strategy.score {
                        ZStack {
                            Circle()
                                .stroke(Theme.surfaceElevated, lineWidth: 3)
                                .frame(width: 32, height: 32)
                            Circle()
                                .trim(from: 0, to: min(1.0, score / 100))
                                .stroke(
                                    score >= 70 ? Theme.profit : score >= 50 ? Theme.accentOrange : Theme.loss,
                                    style: StrokeStyle(lineWidth: 3, lineCap: .round)
                                )
                                .frame(width: 32, height: 32)
                                .rotationEffect(.degrees(-90))
                            Text("\(Int(score))")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundColor(Theme.textPrimary)
                        }
                    }

                    // Direction Badge
                    Text(strategy.strategyType.direction)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background {
                            Capsule()
                                .fill(strategy.strategyType.isBullish ? Theme.profit :
                                      strategy.strategyType.isBearish ? Theme.loss : Theme.primaryBlue)
                        }
                }

                // Legs Summary
                HStack(spacing: 8) {
                    ForEach(strategy.legs.indices, id: \.self) { index in
                        let leg = strategy.legs[index]
                        HStack(spacing: 4) {
                            Text(leg.action == .buy ? "B" : "S")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(leg.action == .buy ? Theme.profit : Theme.loss)

                            Text("\(Int(leg.option.strikePrice))\(leg.option.optionType == .call ? "CE" : "PE")")
                                .font(.system(size: 11, weight: .medium, design: .monospaced))
                                .foregroundColor(Theme.textSecondary)
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(Theme.surfaceElevated)
                        }
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.textMuted)
                }

                // Risk/Reward Info
                HStack {
                    if let maxLoss = strategy.maxLoss, maxLoss.isFinite {
                        HStack(spacing: 4) {
                            Text("Max Loss:")
                                .font(.system(size: 10))
                                .foregroundColor(Theme.textMuted)
                            Text("₹\(Int(maxLoss.isNaN ? 0 : maxLoss))")
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                .foregroundColor(Theme.loss)
                        }
                    }

                    Spacer()

                    if let maxProfit = strategy.maxProfit, maxProfit.isFinite {
                        HStack(spacing: 4) {
                            Text("Max Profit:")
                                .font(.system(size: 10))
                                .foregroundColor(Theme.textMuted)
                            Text(maxProfit > 100000 ? "Unlimited" : "₹\(Int(maxProfit.isNaN ? 0 : maxProfit))")
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                .foregroundColor(Theme.profit)
                        }
                    }

                    if let prob = strategy.probability, prob.isFinite {
                        HStack(spacing: 4) {
                            Text("POP:")
                                .font(.system(size: 10))
                                .foregroundColor(Theme.textMuted)
                            Text("\(Int((prob * 100).isNaN ? 0 : prob * 100))%")
                                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                                .foregroundColor(prob >= 0.5 ? Theme.profit : Theme.accentOrange)
                        }
                    }
                }

                // IV Rank Badge (if applicable)
                if strategy.ivRankBased {
                    HStack(spacing: 4) {
                        Image(systemName: "chart.line.uptrend.xyaxis")
                            .font(.system(size: 10))
                            .foregroundColor(Color(hex: "BF5AF2"))

                        Text("IV Rank Based Strategy")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(Color(hex: "BF5AF2"))

                        Spacer()
                    }
                }
            }
            .padding(10)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Theme.surfaceElevated)
            }
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showDetail) {
            StrategyDetailSheet(strategy: strategy)
        }
    }
}

// MARK: - Strategy Detail Sheet

struct StrategyDetailSheet: View {
    let strategy: StrategySuggestion
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Header
                        VStack(spacing: 12) {
                            HStack(spacing: 12) {
                                Image(systemName: strategy.strategyType.icon)
                                    .font(.system(size: 24, weight: .bold))
                                    .foregroundColor(strategy.strategyType.color)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(strategy.strategyType.rawValue)
                                        .font(.system(size: 20, weight: .bold))
                                        .foregroundColor(Theme.textPrimary)

                                    Text(strategy.marketCondition)
                                        .font(.system(size: 12))
                                        .foregroundColor(Theme.textSecondary)
                                }

                                Spacer()

                                Text(strategy.strategyType.direction)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background {
                                        Capsule()
                                            .fill(strategy.strategyType.isBullish ? Theme.profit :
                                                  strategy.strategyType.isBearish ? Theme.loss : Theme.primaryBlue)
                                    }
                            }
                        }
                        .solidCard()

                        // Legs Detail
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Strategy Legs")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Theme.textPrimary)

                            ForEach(strategy.legs.indices, id: \.self) { index in
                                AIStrategyLegRow(leg: strategy.legs[index], index: index + 1)
                            }
                        }
                        .solidCard()

                        // Risk/Reward
                        VStack(spacing: 16) {
                            Text("Risk & Reward")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Theme.textPrimary)
                                .frame(maxWidth: .infinity, alignment: .leading)

                            HStack(spacing: 0) {
                                if let maxLoss = strategy.maxLoss, maxLoss.isFinite {
                                    VStack(spacing: 4) {
                                        Text("Max Loss")
                                            .font(.system(size: 11))
                                            .foregroundColor(Theme.textMuted)
                                        Text("₹\(Int(maxLoss.isNaN ? 0 : maxLoss))")
                                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                                            .foregroundColor(Theme.loss)
                                    }
                                    .frame(maxWidth: .infinity)
                                }

                                if let maxProfit = strategy.maxProfit, maxProfit.isFinite {
                                    VStack(spacing: 4) {
                                        Text("Max Profit")
                                            .font(.system(size: 11))
                                            .foregroundColor(Theme.textMuted)
                                        Text(maxProfit > 100000 ? "Unlimited" : "₹\(Int(maxProfit.isNaN ? 0 : maxProfit))")
                                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                                            .foregroundColor(Theme.profit)
                                    }
                                    .frame(maxWidth: .infinity)
                                }

                                if let prob = strategy.probability, prob.isFinite {
                                    VStack(spacing: 4) {
                                        Text("Win Prob")
                                            .font(.system(size: 11))
                                            .foregroundColor(Theme.textMuted)
                                        Text("\(Int((prob * 100).isNaN ? 0 : prob * 100))%")
                                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                                            .foregroundColor(prob >= 0.5 ? Theme.profit : Theme.accentOrange)
                                    }
                                    .frame(maxWidth: .infinity)
                                }
                            }

                            // Breakeven
                            if !strategy.breakeven.isEmpty {
                                HStack {
                                    Text("Breakeven:")
                                        .font(.system(size: 12))
                                        .foregroundColor(Theme.textMuted)
                                    Text(strategy.breakeven.map { String(format: "%.0f", $0) }.joined(separator: ", "))
                                        .font(.system(size: 13, weight: .semibold, design: .monospaced))
                                        .foregroundColor(Theme.textPrimary)
                                }
                            }
                        }
                        .solidCard()

                        // Net Greeks
                        if strategy.netDelta != nil || strategy.netTheta != nil {
                            VStack(spacing: 12) {
                                Text("Net Greeks")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(Theme.textPrimary)
                                    .frame(maxWidth: .infinity, alignment: .leading)

                                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                                    StrategyGreekCell(label: "Delta", value: strategy.netDelta, format: "%.3f")
                                    StrategyGreekCell(label: "Theta", value: strategy.netTheta, format: "%.2f")
                                    StrategyGreekCell(label: "Gamma", value: strategy.netGamma, format: "%.4f")
                                    StrategyGreekCell(label: "Vega", value: strategy.netVega, format: "%.2f")
                                }
                            }
                            .solidCard()
                        }

                        // Score Breakdown
                        if let breakdown = strategy.scoreBreakdown {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Text("Score Breakdown")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(Theme.textPrimary)
                                    Spacer()
                                    if let score = strategy.score {
                                        Text("\(Int(score))/100")
                                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                                            .foregroundColor(score >= 70 ? Theme.profit : score >= 50 ? Theme.accentOrange : Theme.loss)
                                    }
                                }

                                ScoreFactorBar(label: "IV Alignment", value: breakdown.ivAlignment)
                                ScoreFactorBar(label: "Probability", value: breakdown.pop)
                                ScoreFactorBar(label: "Risk/Reward", value: breakdown.riskReward)
                                ScoreFactorBar(label: "Liquidity", value: breakdown.liquidity)
                                ScoreFactorBar(label: "Regime Fit", value: breakdown.regimeFit)
                            }
                            .solidCard()
                        }

                        // Reasoning
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Why This Strategy?")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Theme.textPrimary)

                            ForEach(strategy.reasoning, id: \.self) { reason in
                                HStack(alignment: .top, spacing: 10) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 14))
                                        .foregroundColor(Theme.profit)

                                    Text(reason)
                                        .font(.system(size: 13))
                                        .foregroundColor(Theme.textSecondary)
                                }
                            }
                        }
                        .solidCard()

                        // Disclaimer
                        DisclaimerView()
                    }
                    .padding(16)
                    .padding(.bottom, 40)
                }
            }
            .navigationTitle("Strategy Details")
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
}

struct AIStrategyLegRow: View {
    let leg: StrategySuggestion.StrategyLeg
    let index: Int

    var body: some View {
        HStack(spacing: 12) {
            // Leg Number
            Text("\(index)")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textMuted)
                .frame(width: 20)

            // Action Badge
            Text(leg.action == .buy ? "BUY" : "SELL")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background {
                    Capsule()
                        .fill(leg.action == .buy ? Theme.profit : Theme.loss)
                }

            // Strike
            Text("\(Int(leg.option.strikePrice))")
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)

            // Option Type
            Text(leg.option.optionType == .call ? "CE" : "PE")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(leg.option.optionType == .call ? Theme.profit : Theme.loss)

            Spacer()

            // Premium
            VStack(alignment: .trailing, spacing: 2) {
                Text("₹\(String(format: "%.1f", leg.premium))")
                    .font(.system(size: 13, weight: .semibold, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)

                Text("x\(leg.quantity) lots")
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
            }
        }
        .padding(10)
        .background {
            RoundedRectangle(cornerRadius: 8)
                .fill(Theme.surfaceElevated)
        }
    }
}

// MARK: - Strategy Greek Cell

struct StrategyGreekCell: View {
    let label: String
    let value: Double?
    let format: String

    var body: some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(Theme.textMuted)
            Text(value != nil ? String(format: format, value!) : "-")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(8)
        .background {
            RoundedRectangle(cornerRadius: 8)
                .fill(Theme.surfaceElevated)
        }
    }
}

// MARK: - Score Factor Bar

struct ScoreFactorBar: View {
    let label: String
    let value: Double

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(Theme.textSecondary)
                .frame(width: 90, alignment: .leading)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Theme.surfaceElevated)
                        .frame(height: 6)

                    RoundedRectangle(cornerRadius: 3)
                        .fill(value >= 70 ? Theme.profit : value >= 40 ? Theme.accentOrange : Theme.loss)
                        .frame(width: geo.size.width * min(1, value / 100), height: 6)
                }
            }
            .frame(height: 6)

            Text("\(Int(value))")
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textSecondary)
                .frame(width: 28, alignment: .trailing)
        }
    }
}

struct MarketInsightCard: View {
    let insight: MarketInsight

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: insight.icon)
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(insight.color)
                .frame(width: 44, height: 44)
                .background {
                    Circle()
                        .fill(insight.color.opacity(0.15))
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(insight.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                Text(insight.description)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
        }
        .padding(14)
        .solidCard()
    }
}

// MARK: - Disclaimer View

struct DisclaimerView: View {
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 14))
                .foregroundColor(Theme.accentOrange)

            Text("AI suggestions are based on technical analysis and should not be considered financial advice. Always do your own research.")
                .font(.system(size: 11))
                .foregroundColor(Theme.textMuted)
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.accentOrange.opacity(0.1))
        }
    }
}

// MARK: - Preview

#Preview {
    AIInsightsView(optionChainViewModel: OptionChainViewModel())
}
