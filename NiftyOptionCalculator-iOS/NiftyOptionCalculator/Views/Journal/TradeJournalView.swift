import SwiftUI

// MARK: - Trade Journal View

struct TradeJournalView: View {
    @StateObject private var viewModel = TradeJournalViewModel()
    @ObservedObject private var authManager = AuthManager.shared
    @State private var showLoginSheet = false

    var body: some View {
        ZStack {
            Theme.backgroundGradient.ignoresSafeArea()

            if !authManager.isLoggedIn {
                journalLoginPrompt
            } else {
                VStack(spacing: 0) {
                    // Tab Selector
                    journalTabBar

                    // Content
                    switch viewModel.activeTab {
                    case .entries:
                        entriesListView
                    case .stats:
                        statsView
                    }
                }
            }
        }
        .navigationTitle("Trade Journal")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if authManager.isLoggedIn {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        viewModel.startNewEntry()
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(Theme.primaryBlue)
                    }
                }
            }
        }
        .sheet(isPresented: $viewModel.showForm) {
            JournalEntryFormView(viewModel: viewModel)
        }
        .sheet(isPresented: $showLoginSheet) {
            LoginSheetView()
        }
        .alert("Error", isPresented: $viewModel.showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "An unknown error occurred")
        }
        .task {
            await viewModel.loadEntries()
            await viewModel.loadStats()
        }
    }

    // MARK: - Login Prompt

    private var journalLoginPrompt: some View {
        VStack(spacing: 20) {
            Image(systemName: "book.closed.fill")
                .font(.system(size: 60))
                .foregroundStyle(Theme.blueGradient)
                .padding(.bottom, 8)

            Text("Trade Journal")
                .font(.title2.bold())
                .foregroundColor(Theme.textPrimary)

            Text("Login to record and review your trades. Track your performance, emotions, and improve your strategy.")
                .font(.subheadline)
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button {
                showLoginSheet = true
            } label: {
                Text("Login to Continue")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Theme.blueGradient)
                    .cornerRadius(12)
            }
            .padding(.horizontal, 40)
            .padding(.top, 12)
        }
    }

    // MARK: - Tab Bar

    private var journalTabBar: some View {
        HStack(spacing: 0) {
            ForEach(JournalTab.allCases, id: \.self) { tab in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        viewModel.activeTab = tab
                    }
                } label: {
                    VStack(spacing: 6) {
                        Text(tab.rawValue)
                            .font(.subheadline.bold())
                            .foregroundColor(viewModel.activeTab == tab ? Theme.primaryBlue : Theme.textSecondary)

                        Rectangle()
                            .fill(viewModel.activeTab == tab ? Theme.primaryBlue : Color.clear)
                            .frame(height: 2)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
        .background(Theme.surface)
    }

    // MARK: - Entries List

    private var entriesListView: some View {
        Group {
            if viewModel.isLoading && viewModel.entries.isEmpty {
                loadingView
            } else if viewModel.entries.isEmpty {
                emptyEntriesView
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        // Filter bar
                        filterBar

                        ForEach(viewModel.entries) { entry in
                            JournalEntryCard(entry: entry, viewModel: viewModel)
                                .contextMenu {
                                    Button {
                                        viewModel.populateFormForEditing(entry)
                                    } label: {
                                        Label("Edit", systemImage: "pencil")
                                    }

                                    Button(role: .destructive) {
                                        Task {
                                            await viewModel.deleteEntry(entry)
                                        }
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                        }
                    }
                    .padding()
                }
                .refreshable {
                    await viewModel.loadEntries()
                }
            }
        }
    }

    // MARK: - Filter Bar

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                // Outcome filter
                Menu {
                    Button("All") { viewModel.filterOutcome = nil }
                    ForEach(TradeOutcome.allCases, id: \.self) { outcome in
                        Button(outcome.rawValue.capitalized) {
                            viewModel.filterOutcome = outcome
                        }
                    }
                } label: {
                    filterChip(
                        text: viewModel.filterOutcome?.rawValue.capitalized ?? "Outcome",
                        isActive: viewModel.filterOutcome != nil
                    )
                }

                // Symbol filter
                Menu {
                    Button("All") { viewModel.filterSymbol = "" }
                    ForEach(viewModel.availableSymbols, id: \.self) { symbol in
                        Button(symbol) {
                            viewModel.filterSymbol = symbol
                        }
                    }
                } label: {
                    filterChip(
                        text: viewModel.filterSymbol.isEmpty ? "Symbol" : viewModel.filterSymbol,
                        isActive: !viewModel.filterSymbol.isEmpty
                    )
                }

                // Tag filter
                Menu {
                    Button("All") { viewModel.filterTag = nil }
                    ForEach(JournalTag.allCases, id: \.self) { tag in
                        Button(tag.displayName) {
                            viewModel.filterTag = tag
                        }
                    }
                } label: {
                    filterChip(
                        text: viewModel.filterTag?.displayName ?? "Tag",
                        isActive: viewModel.filterTag != nil
                    )
                }

                // Apply filters button
                if viewModel.filterOutcome != nil || !viewModel.filterSymbol.isEmpty || viewModel.filterTag != nil {
                    Button {
                        viewModel.filterOutcome = nil
                        viewModel.filterSymbol = ""
                        viewModel.filterTag = nil
                        Task { await viewModel.loadEntries() }
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
        .onChange(of: viewModel.filterOutcome) { _, _ in
            Task { await viewModel.loadEntries() }
        }
        .onChange(of: viewModel.filterSymbol) { _, _ in
            Task { await viewModel.loadEntries() }
        }
        .onChange(of: viewModel.filterTag) { _, _ in
            Task { await viewModel.loadEntries() }
        }
    }

    private func filterChip(text: String, isActive: Bool) -> some View {
        Text(text)
            .font(.caption.bold())
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isActive ? Theme.primaryBlue.opacity(0.2) : Theme.surface)
            .foregroundColor(isActive ? Theme.primaryBlue : Theme.textSecondary)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isActive ? Theme.primaryBlue.opacity(0.5) : Theme.border, lineWidth: 1)
            )
    }

    // MARK: - Empty State

    private var emptyEntriesView: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "note.text.badge.plus")
                .font(.system(size: 48))
                .foregroundColor(Theme.textMuted)

            Text("No Journal Entries")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            Text("Start recording your trades to track your progress and identify patterns.")
                .font(.subheadline)
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                viewModel.startNewEntry()
            } label: {
                Text("Add First Entry")
                    .font(.subheadline.bold())
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(Theme.blueGradient)
                    .cornerRadius(10)
            }
            .padding(.top, 4)
            Spacer()
        }
    }

    // MARK: - Stats View

    private var statsView: some View {
        Group {
            if viewModel.isLoading && viewModel.stats == nil {
                loadingView
            } else if let stats = viewModel.stats {
                ScrollView {
                    VStack(spacing: 16) {
                        // Overview card
                        statsOverviewCard(stats: stats)

                        // P&L card
                        pnlCard(stats: stats)

                        // Win rate card
                        winRateCard(stats: stats)

                        // Most traded
                        if let symbol = stats.most_traded_symbol {
                            infoCard(title: "Most Traded", value: symbol, icon: "chart.bar.fill")
                        }

                        // Average holding
                        if let days = stats.average_holding_days {
                            infoCard(title: "Avg Holding Period", value: String(format: "%.1f days", days), icon: "clock.fill")
                        }

                        // Mood distribution
                        if !stats.trades_by_mood.isEmpty {
                            moodDistributionCard(stats: stats)
                        }

                        // Market condition distribution
                        if !stats.trades_by_condition.isEmpty {
                            conditionDistributionCard(stats: stats)
                        }
                    }
                    .padding()
                }
                .refreshable {
                    await viewModel.loadStats()
                }
            } else {
                VStack(spacing: 16) {
                    Spacer()
                    Image(systemName: "chart.pie")
                        .font(.system(size: 48))
                        .foregroundColor(Theme.textMuted)
                    Text("No stats available")
                        .font(.headline)
                        .foregroundColor(Theme.textSecondary)
                    Text("Add some journal entries to see your statistics.")
                        .font(.subheadline)
                        .foregroundColor(Theme.textMuted)
                        .multilineTextAlignment(.center)
                    Spacer()
                }
            }
        }
    }

    // MARK: - Stats Cards

    private func statsOverviewCard(stats: JournalStats) -> some View {
        VStack(spacing: 12) {
            HStack {
                Text("Overview")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)
                Spacer()
                Text("\(stats.total_trades) trades")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
            }

            HStack(spacing: 16) {
                statItem(label: "Wins", value: "\(stats.winning_trades)", color: Theme.profit)
                statItem(label: "Losses", value: "\(stats.losing_trades)", color: Theme.loss)
                statItem(label: "Breakeven", value: "\(stats.breakeven_trades)", color: Theme.textMuted)
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func pnlCard(stats: JournalStats) -> some View {
        VStack(spacing: 12) {
            HStack {
                Text("P&L Summary")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)
                Spacer()
            }

            HStack(spacing: 16) {
                VStack(spacing: 4) {
                    Text("Total P&L")
                        .font(.caption)
                        .foregroundColor(Theme.textSecondary)
                    Text(formatCurrency(stats.total_pnl))
                        .font(.title3.bold())
                        .foregroundColor(stats.total_pnl >= 0 ? Theme.profit : Theme.loss)
                }
                .frame(maxWidth: .infinity)

                VStack(spacing: 4) {
                    Text("Average P&L")
                        .font(.caption)
                        .foregroundColor(Theme.textSecondary)
                    Text(formatCurrency(stats.average_pnl))
                        .font(.subheadline.bold())
                        .foregroundColor(stats.average_pnl >= 0 ? Theme.profit : Theme.loss)
                }
                .frame(maxWidth: .infinity)
            }

            HStack(spacing: 16) {
                if let best = stats.best_trade {
                    VStack(spacing: 4) {
                        Text("Best Trade")
                            .font(.caption)
                            .foregroundColor(Theme.textSecondary)
                        Text(formatCurrency(best))
                            .font(.subheadline.bold())
                            .foregroundColor(Theme.profit)
                    }
                    .frame(maxWidth: .infinity)
                }

                if let worst = stats.worst_trade {
                    VStack(spacing: 4) {
                        Text("Worst Trade")
                            .font(.caption)
                            .foregroundColor(Theme.textSecondary)
                        Text(formatCurrency(worst))
                            .font(.subheadline.bold())
                            .foregroundColor(Theme.loss)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func winRateCard(stats: JournalStats) -> some View {
        VStack(spacing: 12) {
            HStack {
                Text("Win Rate")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)
                Spacer()
                Text(String(format: "%.1f%%", stats.win_rate * 100))
                    .font(.title2.bold())
                    .foregroundColor(stats.win_rate >= 0.5 ? Theme.profit : Theme.loss)
            }

            // Win rate bar
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Theme.loss.opacity(0.3))
                        .frame(height: 12)

                    RoundedRectangle(cornerRadius: 6)
                        .fill(Theme.profit)
                        .frame(width: geometry.size.width * CGFloat(min(stats.win_rate, 1.0)), height: 12)
                }
            }
            .frame(height: 12)
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func moodDistributionCard(stats: JournalStats) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Trades by Mood")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            ForEach(Array(stats.trades_by_mood.sorted(by: { $0.value > $1.value })), id: \.key) { mood, count in
                HStack {
                    if let m = TradeMood(rawValue: mood) {
                        Text(m.emoji)
                        Text(mood.capitalized)
                            .font(.subheadline)
                            .foregroundColor(Theme.textPrimary)
                    } else {
                        Text(mood.capitalized)
                            .font(.subheadline)
                            .foregroundColor(Theme.textPrimary)
                    }
                    Spacer()
                    Text("\(count)")
                        .font(.subheadline.bold())
                        .foregroundColor(Theme.textSecondary)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func conditionDistributionCard(stats: JournalStats) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Trades by Market Condition")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            ForEach(Array(stats.trades_by_condition.sorted(by: { $0.value > $1.value })), id: \.key) { condition, count in
                HStack {
                    Text(condition.replacingOccurrences(of: "_", with: " ").capitalized)
                        .font(.subheadline)
                        .foregroundColor(Theme.textPrimary)
                    Spacer()
                    Text("\(count)")
                        .font(.subheadline.bold())
                        .foregroundColor(Theme.textSecondary)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func infoCard(title: String, value: String, icon: String) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(Theme.primaryBlue)
                .font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
                Text(value)
                    .font(.subheadline.bold())
                    .foregroundColor(Theme.textPrimary)
            }
            Spacer()
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func statItem(label: String, value: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title3.bold())
                .foregroundColor(color)
            Text(label)
                .font(.caption)
                .foregroundColor(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Loading

    private var loadingView: some View {
        VStack {
            Spacer()
            ProgressView()
                .scaleEffect(1.2)
                .tint(Theme.primaryBlue)
            Text("Loading...")
                .font(.subheadline)
                .foregroundColor(Theme.textSecondary)
                .padding(.top, 8)
            Spacer()
        }
    }

    // MARK: - Helpers

    private func formatCurrency(_ value: Double) -> String {
        let absValue = abs(value)
        let sign = value < 0 ? "-" : "+"
        if value == 0 { return "\u{20B9}0" }
        if absValue >= 1_00_00_000 {
            return "\(sign)\u{20B9}\(String(format: "%.2f", absValue / 1_00_00_000)) Cr"
        }
        if absValue >= 1_00_000 {
            return "\(sign)\u{20B9}\(String(format: "%.2f", absValue / 1_00_000))L"
        }
        return "\(sign)\u{20B9}\(String(format: "%.0f", absValue))"
    }
}

// MARK: - Journal Entry Card

struct JournalEntryCard: View {
    let entry: JournalEntry
    @ObservedObject var viewModel: TradeJournalViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Top row: Symbol + Strike + Type + Direction
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(entry.symbol)
                            .font(.headline)
                            .foregroundColor(Theme.textPrimary)

                        Text("\(String(format: "%.0f", entry.strike_price)) \(entry.option_type)")
                            .font(.subheadline)
                            .foregroundColor(Theme.textSecondary)
                    }

                    Text(entry.direction.uppercased())
                        .font(.caption.bold())
                        .foregroundColor(entry.direction == "buy" ? Theme.profit : Theme.loss)
                }

                Spacer()

                // P&L
                if let pnl = viewModel.computePnL(for: entry) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(pnl >= 0 ? "+\u{20B9}\(String(format: "%.0f", abs(pnl)))" : "-\u{20B9}\(String(format: "%.0f", abs(pnl)))")
                            .font(.headline)
                            .foregroundColor(viewModel.pnlColor(for: entry))

                        if let pct = entry.realized_pnl_percent {
                            Text(String(format: "%+.1f%%", pct))
                                .font(.caption)
                                .foregroundColor(viewModel.pnlColor(for: entry))
                        }
                    }
                } else {
                    Text("Open")
                        .font(.caption.bold())
                        .foregroundColor(Theme.accentOrange)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Theme.accentOrange.opacity(0.15))
                        .cornerRadius(6)
                }
            }

            // Price info
            HStack(spacing: 16) {
                HStack(spacing: 4) {
                    Text("Entry:")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                    Text("\u{20B9}\(String(format: "%.2f", entry.entry_price))")
                        .font(.caption.bold())
                        .foregroundColor(Theme.textSecondary)
                }

                if let exitPrice = entry.exit_price {
                    HStack(spacing: 4) {
                        Text("Exit:")
                            .font(.caption)
                            .foregroundColor(Theme.textMuted)
                        Text("\u{20B9}\(String(format: "%.2f", exitPrice))")
                            .font(.caption.bold())
                            .foregroundColor(Theme.textSecondary)
                    }
                }

                HStack(spacing: 4) {
                    Text("Qty:")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                    Text("\(entry.quantity)x\(entry.lot_size)")
                        .font(.caption.bold())
                        .foregroundColor(Theme.textSecondary)
                }
            }

            // Tags, Mood, Condition row
            HStack(spacing: 8) {
                // Tags
                if let tagsStr = entry.tags, !tagsStr.isEmpty {
                    ForEach(tagsStr.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }, id: \.self) { tag in
                        Text(tag.replacingOccurrences(of: "_", with: " "))
                            .font(.system(size: 10, weight: .medium))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Theme.primaryBlue.opacity(0.15))
                            .foregroundColor(Theme.primaryBlue)
                            .cornerRadius(4)
                    }
                }

                // Mood emoji
                if let moodStr = entry.mood, let mood = TradeMood(rawValue: moodStr) {
                    Text(mood.emoji)
                        .font(.caption)
                }

                Spacer()

                // Date
                Text(entry.entry_date)
                    .font(.caption2)
                    .foregroundColor(Theme.textMuted)
            }

            // Title or notes preview
            if let title = entry.title, !title.isEmpty {
                Text(title)
                    .font(.caption)
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
            }

            if let notes = entry.notes, !notes.isEmpty {
                Text(notes)
                    .font(.caption2)
                    .foregroundColor(Theme.textMuted)
                    .lineLimit(2)
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }
}
