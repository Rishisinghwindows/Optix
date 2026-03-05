import SwiftUI

struct IPODashboardView: View {
    @StateObject private var viewModel = IPOViewModel()
    @EnvironmentObject var themeConfig: ThemeConfiguration
    @State private var searchText: String = ""

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                headerView

                // Search Bar
                searchBar

                // Content
                if viewModel.isLoading && viewModel.ipos.isEmpty {
                    loadingView
                } else if let error = viewModel.error, viewModel.ipos.isEmpty {
                    errorView(error)
                } else {
                    ScrollView {
                        VStack(spacing: 16) {
                            // Status Filter
                            statusFilterView

                            // Type Filter
                            typeFilterView

                            // IPO Cards
                            let searchFilteredIPOs = searchText.isEmpty
                                ? viewModel.filteredIPOs
                                : viewModel.filteredIPOs.filter { $0.companyName.localizedCaseInsensitiveContains(searchText) }

                            if searchFilteredIPOs.isEmpty {
                                emptyStateView
                            } else {
                                LazyVStack(spacing: 12) {
                                    ForEach(searchFilteredIPOs) { ipo in
                                        IPOCardView(
                                            ipo: ipo,
                                            analysis: viewModel.analysisCache[ipo.slug],
                                            isAnalyzing: viewModel.analyzingIPO == ipo.slug,
                                            onTap: {
                                                viewModel.selectIPO(ipo)
                                            },
                                            onAnalyze: {
                                                Task {
                                                    await viewModel.getAnalysis(for: ipo)
                                                }
                                            }
                                        )
                                    }
                                }
                                .padding(.horizontal, 16)
                            }
                        }
                        .padding(.bottom, 100)
                    }
                    .refreshable {
                        await viewModel.refresh()
                    }
                }
            }
        }
        .sheet(isPresented: $viewModel.showAnalysisSheet) {
            if let ipo = viewModel.selectedIPO {
                IPODetailSheet(
                    ipo: ipo,
                    analysis: viewModel.analysisCache[ipo.slug],
                    isAnalyzing: viewModel.analyzingIPO == ipo.slug,
                    onAnalyze: {
                        Task {
                            await viewModel.getAnalysis(for: ipo)
                        }
                    },
                    onRefresh: {
                        Task {
                            await viewModel.refreshAnalysis(for: ipo)
                        }
                    }
                )
            }
        }
    }

    // MARK: - Header

    private var headerView: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("IPO Dashboard")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                if let lastUpdated = viewModel.lastUpdated {
                    Text("Updated \(lastUpdated.formatted(date: .omitted, time: .shortened))")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)
                }
            }

            Spacer()

            // Refresh Button
            Button {
                Task {
                    await viewModel.refresh()
                }
            } label: {
                Image(systemName: viewModel.isRefreshing ? "arrow.triangle.2.circlepath" : "arrow.clockwise")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.accentGreen)
                    .rotationEffect(.degrees(viewModel.isRefreshing ? 360 : 0))
                    .animation(viewModel.isRefreshing ? .linear(duration: 1).repeatForever(autoreverses: false) : .default, value: viewModel.isRefreshing)
            }
            .frame(width: 44, height: 44)
            .background(Theme.surface)
            .cornerRadius(12)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    // MARK: - Search Bar

    private var searchBar: some View {
        HStack(spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16))
                    .foregroundColor(Theme.textMuted)

                TextField("Search IPO by company name...", text: $searchText)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textPrimary)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)

                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Theme.surface)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Theme.border, lineWidth: 1)
            )
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    // MARK: - Status Filter

    private var statusFilterView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(
                    title: "All",
                    count: viewModel.ipos.count,
                    isSelected: viewModel.selectedStatus == nil,
                    color: Theme.accentBlue
                ) {
                    viewModel.setStatusFilter(nil)
                }

                ForEach(IPOStatus.allCases, id: \.self) { status in
                    FilterChip(
                        title: status.displayName,
                        count: viewModel.statusCounts[status] ?? 0,
                        isSelected: viewModel.selectedStatus == status,
                        color: status.color
                    ) {
                        viewModel.setStatusFilter(status)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    // MARK: - Type Filter

    private var typeFilterView: some View {
        HStack(spacing: 8) {
            TypeFilterButton(
                title: "All Types",
                isSelected: viewModel.selectedType == nil
            ) {
                viewModel.setTypeFilter(nil)
            }

            ForEach(IPOType.allCases, id: \.self) { type in
                TypeFilterButton(
                    title: type.displayName,
                    isSelected: viewModel.selectedType == type,
                    color: type.color
                ) {
                    viewModel.setTypeFilter(type)
                }
            }

            Spacer()
        }
        .padding(.horizontal, 16)
    }

    // MARK: - Loading View

    private var loadingView: some View {
        VStack(spacing: 16) {
            Spacer()
            ProgressView()
                .scaleEffect(1.2)
                .tint(Theme.accentGreen)
            Text("Loading IPOs...")
                .font(.system(size: 14))
                .foregroundColor(Theme.textMuted)
            Spacer()
        }
    }

    // MARK: - Error View

    private func errorView(_ error: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundColor(Theme.accentOrange)
            Text("Failed to load IPOs")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
            Text(error)
                .font(.system(size: 14))
                .foregroundColor(Theme.textMuted)
                .multilineTextAlignment(.center)
            Button("Try Again") {
                Task {
                    await viewModel.loadIPOs(forceRefresh: true)
                }
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(Theme.accentGreen)
            .cornerRadius(12)
            Spacer()
        }
        .padding()
    }

    // MARK: - Empty State

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 48))
                .foregroundColor(Theme.textMuted)
            Text("No IPOs Found")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
            Text("No IPOs match the current filters")
                .font(.system(size: 14))
                .foregroundColor(Theme.textMuted)
        }
        .padding(.vertical, 60)
    }
}

// MARK: - Filter Chip

struct FilterChip: View {
    let title: String
    let count: Int
    let isSelected: Bool
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))

                if count > 0 {
                    Text("\(count)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(isSelected ? .white : color)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(isSelected ? color.opacity(0.3) : color.opacity(0.15))
                        .cornerRadius(8)
                }
            }
            .foregroundColor(isSelected ? .white : Theme.textSecondary)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(isSelected ? color : Theme.surface)
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(isSelected ? color : Theme.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Type Filter Button

struct TypeFilterButton: View {
    let title: String
    let isSelected: Bool
    var color: Color = Theme.accentBlue
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(isSelected ? .white : Theme.textSecondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(isSelected ? color : Theme.surface)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isSelected ? color : Theme.border, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - IPO Card View

struct IPOCardView: View {
    let ipo: IPOItem
    let analysis: IPOAnalysisResponse?
    let isAnalyzing: Bool
    let onTap: () -> Void
    let onAnalyze: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 12) {
                // Header Row
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(ipo.companyName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                            .lineLimit(1)

                        HStack(spacing: 8) {
                            // IPO Type Badge
                            Text(ipo.ipoType.displayName)
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(ipo.ipoType.color)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(ipo.ipoType.color.opacity(0.15))
                                .cornerRadius(6)

                            // Status Badge
                            HStack(spacing: 4) {
                                Image(systemName: ipo.status.icon)
                                    .font(.system(size: 10))
                                Text(ipo.status.displayName)
                                    .font(.system(size: 10, weight: .semibold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(ipo.status.color)
                            .cornerRadius(6)
                        }
                    }

                    Spacer()

                    // GMP Display
                    if ipo.gmp?.gmpValue != nil {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("GMP")
                                .font(.system(size: 10))
                                .foregroundColor(Theme.textMuted)
                            Text(ipo.gmpDisplay)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(ipo.gmp?.isPositive == true ? Theme.profit : Theme.loss)
                            Text(ipo.listingGainDisplay)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(ipo.gmp?.isPositive == true ? Theme.profit : Theme.loss)
                        }
                    }
                }

                Divider()
                    .background(Theme.border)

                // Details Grid
                HStack(spacing: 16) {
                    IPODetailItem(title: "Price Band", value: ipo.priceBandDisplay)
                    IPODetailItem(title: "Lot Size", value: ipo.lotSize != nil ? "\(ipo.lotSize!)" : "-")
                    IPODetailItem(title: "Min Invest", value: ipo.minInvestmentDisplay)
                }

                // Dates
                HStack {
                    Image(systemName: "calendar")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)
                    Text(ipo.dateRangeDisplay)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textSecondary)

                    Spacer()

                    if let exchange = ipo.exchange {
                        Text(exchange)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Theme.textMuted)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Theme.surfaceElevated)
                            .cornerRadius(4)
                    }
                }

                // AI Analysis Section
                if let analysis = analysis {
                    Divider()
                        .background(Theme.border)

                    HStack {
                        VerdictBadge(verdict: analysis.verdictEnum)

                        Spacer()

                        Text("Confidence: \(analysis.confidence)%")
                            .font(.system(size: 11))
                            .foregroundColor(Theme.textMuted)
                    }
                } else {
                    // Animated Analyze Button
                    AnimatedAnalyzeButton(
                        isAnalyzing: isAnalyzing,
                        onAnalyze: onAnalyze
                    )
                }
            }
            .padding(16)
            .background(Theme.surface)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Theme.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - IPO Detail Item

struct IPODetailItem: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)
            Text(value)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Verdict Badge

struct VerdictBadge: View {
    let verdict: IPOVerdict

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: verdict.icon)
                .font(.system(size: 12))
            Text(verdict.rawValue)
                .font(.system(size: 12, weight: .bold))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(verdict.color)
        .cornerRadius(8)
    }
}

// MARK: - IPO Detail Sheet

struct IPODetailSheet: View {
    let ipo: IPOItem
    let analysis: IPOAnalysisResponse?
    let isAnalyzing: Bool
    let onAnalyze: () -> Void
    let onRefresh: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Company Header
                    VStack(spacing: 8) {
                        Text(ipo.companyName)
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                            .multilineTextAlignment(.center)

                        HStack(spacing: 8) {
                            Text(ipo.ipoType.displayName)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(ipo.ipoType.color)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(ipo.ipoType.color.opacity(0.15))
                                .cornerRadius(8)

                            HStack(spacing: 4) {
                                Image(systemName: ipo.status.icon)
                                Text(ipo.status.displayName)
                            }
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(ipo.status.color)
                            .cornerRadius(8)
                        }
                    }
                    .padding()

                    // GMP Card
                    if let gmp = ipo.gmp, gmp.gmpValue != nil {
                        GMPCard(gmp: gmp, priceBandHigh: ipo.priceBandHigh)
                    }

                    // Details Card
                    IPODetailsCard(ipo: ipo)

                    // AI Analysis
                    if let analysis = analysis {
                        AIAnalysisCard(analysis: analysis, onRefresh: onRefresh)
                    } else {
                        // Get Analysis Button
                        Button(action: onAnalyze) {
                            HStack {
                                if isAnalyzing {
                                    ProgressView()
                                        .tint(.white)
                                } else {
                                    Image(systemName: "sparkles")
                                }
                                Text(isAnalyzing ? "Analyzing..." : "Get AI Analysis")
                                    .font(.system(size: 16, weight: .bold))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(
                                LinearGradient(
                                    colors: [Color(hex: "667EEA"), Color(hex: "764BA2")],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(14)
                        }
                        .disabled(isAnalyzing)
                        .padding(.horizontal)
                    }
                }
                .padding(.bottom, 30)
            }
            .background(Theme.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundColor(Theme.accentGreen)
                }
            }
        }
    }
}

// MARK: - GMP Card

struct GMPCard: View {
    let gmp: GMPData
    let priceBandHigh: Double?

    var body: some View {
        VStack(spacing: 12) {
            Text("Grey Market Premium")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)

            HStack(spacing: 24) {
                VStack(spacing: 4) {
                    Text("GMP")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    Text(gmp.gmpValue != nil ? (gmp.gmpValue! >= 0 ? "+₹\(Int(gmp.gmpValue!))" : "₹\(Int(gmp.gmpValue!))") : "-")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(gmp.isPositive ? Theme.profit : Theme.loss)
                }

                Rectangle()
                    .fill(Theme.border)
                    .frame(width: 1, height: 40)

                VStack(spacing: 4) {
                    Text("Expected Listing")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    if let estimated = gmp.estimatedListingPrice {
                        Text("₹\(Int(estimated))")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                    } else {
                        Text("-")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                    }
                }

                Rectangle()
                    .fill(Theme.border)
                    .frame(width: 1, height: 40)

                VStack(spacing: 4) {
                    Text("Listing Gain")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    if let gain = gmp.listingGainPct {
                        Text("\(gain >= 0 ? "+" : "")\(String(format: "%.1f", gain))%")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(gmp.isPositive ? Theme.profit : Theme.loss)
                    } else {
                        Text("-")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                    }
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Theme.surface)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(gmp.isPositive ? Theme.profit.opacity(0.3) : Theme.loss.opacity(0.3), lineWidth: 2)
        )
        .padding(.horizontal)
    }
}

// MARK: - IPO Details Card

struct IPODetailsCard: View {
    let ipo: IPOItem

    var body: some View {
        VStack(spacing: 16) {
            Text("IPO Details")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            VStack(spacing: 12) {
                DetailRow(label: "Price Band", value: ipo.priceBandDisplay)
                DetailRow(label: "Lot Size", value: ipo.lotSize != nil ? "\(ipo.lotSize!) shares" : "-")
                DetailRow(label: "Min Investment", value: ipo.minInvestmentDisplay)
                DetailRow(label: "Issue Size", value: ipo.issueSizeCr != nil ? "₹\(ipo.issueSizeCr!) Cr" : "-")
                DetailRow(label: "Open Date", value: ipo.openDate ?? "-")
                DetailRow(label: "Close Date", value: ipo.closeDate ?? "-")
                DetailRow(label: "Listing Date", value: ipo.listingDate ?? "-")
                DetailRow(label: "Exchange", value: ipo.exchange ?? "-")
            }
        }
        .padding()
        .background(Theme.surface)
        .cornerRadius(16)
        .padding(.horizontal)
    }
}

// MARK: - Detail Row

struct DetailRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(Theme.textMuted)
            Spacer()
            Text(value)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
        }
    }
}

// MARK: - AI Analysis Card

struct AIAnalysisCard: View {
    let analysis: IPOAnalysisResponse
    let onRefresh: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            // Header
            HStack {
                Text("AI Analysis")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                Button(action: onRefresh) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 14))
                        .foregroundColor(Theme.accentBlue)
                }
            }

            // Verdict
            HStack {
                VerdictBadge(verdict: analysis.verdictEnum)

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text("Confidence")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    Text("\(analysis.confidence)%")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                }
            }

            // Summary
            Text(analysis.recommendationSummary)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            // Key Positives
            if !analysis.keyPositives.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Key Positives", systemImage: "checkmark.circle.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.profit)

                    ForEach(analysis.keyPositives, id: \.self) { positive in
                        HStack(alignment: .top, spacing: 8) {
                            Circle()
                                .fill(Theme.profit)
                                .frame(width: 6, height: 6)
                                .padding(.top, 6)
                            Text(positive)
                                .font(.system(size: 13))
                                .foregroundColor(Theme.textSecondary)
                        }
                    }
                }
            }

            // Key Risks
            if !analysis.keyRisks.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Key Risks", systemImage: "exclamationmark.triangle.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.loss)

                    ForEach(analysis.keyRisks, id: \.self) { risk in
                        HStack(alignment: .top, spacing: 8) {
                            Circle()
                                .fill(Theme.loss)
                                .frame(width: 6, height: 6)
                                .padding(.top, 6)
                            Text(risk)
                                .font(.system(size: 13))
                                .foregroundColor(Theme.textSecondary)
                        }
                    }
                }
            }

            // Disclaimer
            Text(analysis.disclaimer)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)
                .padding(.top, 8)
        }
        .padding()
        .background(Theme.surface)
        .cornerRadius(16)
        .padding(.horizontal)
    }
}

// MARK: - Animated Analyze Button

struct AnimatedAnalyzeButton: View {
    let isAnalyzing: Bool
    let onAnalyze: () -> Void

    @State private var shimmerOffset: CGFloat = -200
    @State private var isPulsing: Bool = false
    @State private var sparkleRotation: Double = 0
    @State private var sparkleScale: CGFloat = 1.0

    var body: some View {
        Button(action: onAnalyze) {
            ZStack {
                // Background gradient
                RoundedRectangle(cornerRadius: 10)
                    .fill(
                        LinearGradient(
                            colors: [Color(hex: "667EEA"), Color(hex: "764BA2")],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )

                // Shimmer effect (only when not analyzing)
                if !isAnalyzing {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(
                            LinearGradient(
                                colors: [
                                    .clear,
                                    .white.opacity(0.3),
                                    .white.opacity(0.5),
                                    .white.opacity(0.3),
                                    .clear
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .offset(x: shimmerOffset)
                        .mask(
                            RoundedRectangle(cornerRadius: 10)
                        )
                }

                // Content
                HStack(spacing: 8) {
                    if isAnalyzing {
                        ProgressView()
                            .scaleEffect(0.8)
                            .tint(.white)
                    } else {
                        // Animated sparkle icon
                        Image(systemName: "sparkles")
                            .font(.system(size: 14, weight: .semibold))
                            .rotationEffect(.degrees(sparkleRotation))
                            .scaleEffect(sparkleScale)
                    }

                    Text(isAnalyzing ? "Analyzing..." : "Get AI Analysis")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .shadow(color: isPulsing ? Color(hex: "667EEA").opacity(0.6) : Color.clear, radius: isPulsing ? 8 : 0, y: 2)
        }
        .scaleEffect(isPulsing ? 1.02 : 1.0)
        .disabled(isAnalyzing)
        .onAppear {
            startAnimations()
        }
        .onChange(of: isAnalyzing) { _, newValue in
            if !newValue {
                startAnimations()
            }
        }
    }

    private func startAnimations() {
        // Reset shimmer
        shimmerOffset = -200

        // Shimmer animation - continuous
        withAnimation(.linear(duration: 2.0).repeatForever(autoreverses: false)) {
            shimmerOffset = 400
        }

        // Pulse glow animation
        withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) {
            isPulsing = true
        }

        // Sparkle rotation
        withAnimation(.easeInOut(duration: 2.0).repeatForever(autoreverses: true)) {
            sparkleRotation = 15
        }

        // Sparkle scale
        withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
            sparkleScale = 1.15
        }
    }
}

#Preview {
    IPODashboardView()
        .environmentObject(ThemeConfiguration.shared)
}
