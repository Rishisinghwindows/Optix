import SwiftUI

// MARK: - Paper Trade History View

struct PaperTradeHistoryView: View {
    @ObservedObject var viewModel: PaperTradingViewModel

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.hasTradeHistory {
                // Filter Pills
                FilterPillsBar(selectedFilter: $viewModel.tradeHistoryFilter)

                // Summary Stats
                HistorySummaryHeader(viewModel: viewModel)

                // Trade List
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(viewModel.getFilteredTrades()) { trade in
                            ClosedTradeRow(trade: trade)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .padding(.bottom, 120)
                }
            } else {
                // Empty State
                EmptyHistoryView()
            }
        }
    }
}

// MARK: - Filter Pills Bar

private struct FilterPillsBar: View {
    @Binding var selectedFilter: TradeHistoryFilter
    @Namespace private var animation

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(TradeHistoryFilter.allCases, id: \.self) { filter in
                    FilterPill(
                        filter: filter,
                        isSelected: selectedFilter == filter,
                        namespace: animation
                    ) {
                        withAnimation(.spring(response: 0.3)) {
                            selectedFilter = filter
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Theme.surface.opacity(0.3))
    }
}

private struct FilterPill: View {
    let filter: TradeHistoryFilter
    let isSelected: Bool
    let namespace: Namespace.ID
    let action: () -> Void

    private var pillColor: Color {
        switch filter {
        case .all: return Theme.accentBlue
        case .profitable: return Theme.profit
        case .loss: return Theme.loss
        case .nifty: return Color(hex: "00C805")
        case .bankNifty: return Color(hex: "007AFF")
        }
    }

    var body: some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()
            action()
        }) {
            Text(filter.displayName)
                .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                .foregroundColor(isSelected ? .white : Theme.textSecondary)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background {
                    if isSelected {
                        Capsule()
                            .fill(pillColor)
                            .matchedGeometryEffect(id: "selectedFilter", in: namespace)
                    } else {
                        Capsule()
                            .fill(Theme.card)
                    }
                }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - History Summary Header

private struct HistorySummaryHeader: View {
    @ObservedObject var viewModel: PaperTradingViewModel

    private var filteredTrades: [ClosedTrade] {
        viewModel.getFilteredTrades()
    }

    private var totalPnL: Double {
        filteredTrades.reduce(0) { $0 + $1.realizedPnL }
    }

    private var winCount: Int {
        filteredTrades.filter { $0.isProfitable }.count
    }

    private var lossCount: Int {
        filteredTrades.filter { !$0.isProfitable && $0.realizedPnL != 0 }.count
    }

    var body: some View {
        HStack(spacing: 16) {
            // Total P&L
            VStack(alignment: .leading, spacing: 2) {
                Text(L.paperTradeRealizedPnL)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)

                Text(viewModel.formatPnL(totalPnL))
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(totalPnL >= 0 ? Theme.profit : Theme.loss)
            }

            Spacer()

            // Win/Loss Count
            HStack(spacing: 12) {
                VStack(spacing: 2) {
                    Text("\(winCount)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.profit)
                    Text("Wins")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.textMuted)
                }

                Text("/")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textMuted)

                VStack(spacing: 2) {
                    Text("\(lossCount)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.loss)
                    Text("Loss")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.textMuted)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(Theme.surface.opacity(0.5))
    }
}

// MARK: - Closed Trade Row

private struct ClosedTradeRow: View {
    let trade: ClosedTrade

    var body: some View {
        HStack(spacing: 12) {
            // Direction Indicator
            VStack {
                Image(systemName: trade.direction == .buy ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                    .font(.system(size: 24))
                    .foregroundColor(trade.direction.color)

                // P&L indicator
                Image(systemName: trade.isProfitable ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.system(size: 12))
                    .foregroundColor(trade.isProfitable ? Theme.profit : Theme.loss)
            }

            // Trade Details
            VStack(alignment: .leading, spacing: 6) {
                // Index & Option
                HStack {
                    Text(trade.index)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(indexColor(trade.index))

                    Text(trade.optionSymbol)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    Text("x\(trade.quantity)")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)
                }

                // Entry -> Exit
                HStack(spacing: 6) {
                    Text(String(format: "₹%.2f", trade.entryPrice))
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(Theme.textSecondary)

                    Image(systemName: "arrow.right")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "₹%.2f", trade.exitPrice))
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(Theme.accentGreen)
                }

                // Date & Duration
                HStack(spacing: 8) {
                    Text(formatDate(trade.exitDate))
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text("•")
                        .foregroundColor(Theme.textMuted)

                    Text("\(trade.holdingDays)d hold")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                }
            }

            Spacer()

            // P&L
            VStack(alignment: .trailing, spacing: 4) {
                Text(formatPnL(trade.realizedPnL))
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundColor(trade.isProfitable ? Theme.profit : Theme.loss)

                Text(formatPercent(trade.realizedPnLPercent))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(trade.isProfitable ? Theme.profit : Theme.loss)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background {
                        Capsule()
                            .fill((trade.isProfitable ? Theme.profit : Theme.loss).opacity(0.15))
                    }
            }
        }
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 14)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(trade.isProfitable ? Theme.profit.opacity(0.1) : Theme.loss.opacity(0.1), lineWidth: 1)
                }
        }
    }

    private func indexColor(_ index: String) -> Color {
        if let tradingIndex = TradingIndex(rawValue: index) {
            return Color(hex: tradingIndex.themeColor)
        }
        return Theme.accentBlue
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM, HH:mm"
        return formatter.string(from: date)
    }

    private func formatPnL(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        if abs(value) >= 1_00_000 {
            return String(format: "%@₹%.1fL", prefix, value / 1_00_000)
        }
        return String(format: "%@₹%.0f", prefix, value)
    }

    private func formatPercent(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        return String(format: "%@%.1f%%", prefix, value)
    }
}

// MARK: - Empty History View

private struct EmptyHistoryView: View {
    var body: some View {
        VStack(spacing: 20) {
            Spacer()

            // Icon
            ZStack {
                Circle()
                    .fill(Theme.accentPurple.opacity(0.1))
                    .frame(width: 100, height: 100)

                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 40))
                    .foregroundColor(Theme.accentPurple)
            }

            VStack(spacing: 8) {
                Text(L.paperTradeNoHistory)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text("Your closed trades will appear here")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }

            Spacer()
            Spacer()
        }
    }
}

// MARK: - Preview

#Preview {
    ZStack {
        Theme.backgroundGradient.ignoresSafeArea()
        PaperTradeHistoryView(viewModel: PaperTradingViewModel())
    }
}
