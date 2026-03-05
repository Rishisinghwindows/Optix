import SwiftUI
import Charts

// MARK: - Paper Performance View

struct PaperPerformanceView: View {
    @ObservedObject var viewModel: PaperTradingViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let metrics = viewModel.performanceMetrics, metrics.totalTrades > 0 {
                    // P&L Curve Chart
                    PnLCurveCard(snapshots: viewModel.portfolio.dailyPnLSnapshots, startingBalance: viewModel.portfolio.startingBalance)

                    // Win/Loss Stats
                    WinLossStatsCard(metrics: metrics)

                    // Performance Metrics Grid
                    MetricsGridCard(metrics: metrics, viewModel: viewModel)

                    // Best/Worst Trades
                    if metrics.bestTrade != nil || metrics.worstTrade != nil {
                        BestWorstTradesCard(metrics: metrics)
                    }

                    // Index Breakdown
                    IndexBreakdownCard(metrics: metrics, viewModel: viewModel)
                } else {
                    // Empty State
                    EmptyPerformanceView()
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .padding(.bottom, 120)
        }
    }
}

// MARK: - P&L Curve Card

private struct PnLCurveCard: View {
    let snapshots: [DailyPnLSnapshot]
    let startingBalance: Double

    private var chartData: [(date: Date, value: Double)] {
        guard !snapshots.isEmpty else {
            return [(Date(), 0)]
        }

        return snapshots.sorted { $0.date < $1.date }.map {
            (date: $0.date, value: $0.portfolioValue - startingBalance)
        }
    }

    private var isPositive: Bool {
        (chartData.last?.value ?? 0) >= 0
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("P&L Curve")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                if let lastValue = chartData.last?.value {
                    Text(formatPnL(lastValue))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(lastValue >= 0 ? Theme.profit : Theme.loss)
                }
            }

            if #available(iOS 16.0, *), chartData.count > 1 {
                Chart {
                    ForEach(chartData, id: \.date) { item in
                        LineMark(
                            x: .value("Date", item.date),
                            y: .value("P&L", item.value)
                        )
                        .foregroundStyle(isPositive ? Theme.profit : Theme.loss)
                        .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round))

                        AreaMark(
                            x: .value("Date", item.date),
                            y: .value("P&L", item.value)
                        )
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    (isPositive ? Theme.profit : Theme.loss).opacity(0.3),
                                    (isPositive ? Theme.profit : Theme.loss).opacity(0.0)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                    }

                    // Zero line
                    RuleMark(y: .value("Zero", 0))
                        .foregroundStyle(Theme.textMuted.opacity(0.3))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                }
                .frame(height: 150)
                .chartYAxis {
                    AxisMarks(position: .leading) { value in
                        AxisGridLine()
                            .foregroundStyle(Theme.card)
                        AxisValueLabel {
                            if let doubleValue = value.as(Double.self) {
                                Text(formatShortPnL(doubleValue))
                                    .font(.system(size: 10))
                                    .foregroundColor(Theme.textMuted)
                            }
                        }
                    }
                }
                .chartXAxis {
                    AxisMarks { value in
                        AxisValueLabel {
                            if let date = value.as(Date.self) {
                                Text(formatShortDate(date))
                                    .font(.system(size: 10))
                                    .foregroundColor(Theme.textMuted)
                            }
                        }
                    }
                }
            } else {
                // Fallback for older iOS or single data point
                Text("Not enough data for chart")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)
                    .frame(height: 150)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
        }
    }

    private func formatPnL(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        if abs(value) >= 1_00_000 {
            return String(format: "%@₹%.1fL", prefix, value / 1_00_000)
        }
        return String(format: "%@₹%.0f", prefix, value)
    }

    private func formatShortPnL(_ value: Double) -> String {
        if abs(value) >= 1_00_000 {
            return String(format: "%.0fL", value / 1_00_000)
        } else if abs(value) >= 1_000 {
            return String(format: "%.0fK", value / 1_000)
        }
        return String(format: "%.0f", value)
    }

    private func formatShortDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM"
        return formatter.string(from: date)
    }
}

// MARK: - Win/Loss Stats Card

private struct WinLossStatsCard: View {
    let metrics: PerformanceMetrics

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text(L.paperTradeWinRate)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                Text(String(format: "%.1f%%", metrics.winRate))
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundColor(metrics.winRate >= 50 ? Theme.profit : Theme.loss)
            }

            // Win/Loss Bar
            GeometryReader { geo in
                let winWidth = metrics.totalTrades > 0 ?
                    (CGFloat(metrics.winningTrades) / CGFloat(metrics.totalTrades)) * geo.size.width : 0

                HStack(spacing: 2) {
                    // Win Bar
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Theme.profit)
                        .frame(width: max(winWidth, 4))

                    // Loss Bar
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Theme.loss)
                }
            }
            .frame(height: 12)

            // Stats Row
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(Theme.profit)
                            .frame(width: 8, height: 8)

                        Text("\(metrics.winningTrades) Wins")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Theme.textSecondary)
                    }

                    Text(formatCurrency(metrics.avgWinAmount))
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    HStack(spacing: 6) {
                        Text("\(metrics.losingTrades) Losses")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Theme.textSecondary)

                        Circle()
                            .fill(Theme.loss)
                            .frame(width: 8, height: 8)
                    }

                    Text(formatCurrency(metrics.avgLossAmount))
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                }
            }
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
        }
    }

    private func formatCurrency(_ value: Double) -> String {
        if value >= 1_00_000 {
            return String(format: "Avg: ₹%.1fL", value / 1_00_000)
        }
        return String(format: "Avg: ₹%.0f", value)
    }
}

// MARK: - Metrics Grid Card

private struct MetricsGridCard: View {
    let metrics: PerformanceMetrics
    let viewModel: PaperTradingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Performance Metrics")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)

            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 12) {
                MetricCell(label: "Total Trades", value: "\(metrics.totalTrades)", icon: "chart.bar.fill", color: Theme.accentBlue)

                MetricCell(label: L.paperTradeProfitFactor, value: formatProfitFactor(metrics.profitFactor), icon: "arrow.up.arrow.down", color: metrics.profitFactor >= 1 ? Theme.profit : Theme.loss)

                MetricCell(label: L.paperTradeMaxDrawdown, value: viewModel.formatCurrency(metrics.maxDrawdown), icon: "arrow.down.right", color: Theme.loss)

                MetricCell(label: L.paperTradeAvgHoldingDays, value: String(format: "%.1f days", metrics.avgHoldingDays), icon: "clock.fill", color: Theme.accentPurple)

                MetricCell(label: L.paperTradeBestTrade, value: viewModel.formatCurrency(metrics.largestWin), icon: "arrow.up.circle.fill", color: Theme.profit)

                MetricCell(label: L.paperTradeWorstTrade, value: "-" + viewModel.formatCurrency(metrics.largestLoss), icon: "arrow.down.circle.fill", color: Theme.loss)
            }
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
        }
    }

    private func formatProfitFactor(_ value: Double) -> String {
        if value.isInfinite {
            return "∞"
        }
        return String(format: "%.2f", value)
    }
}

private struct MetricCell: View {
    let label: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(color)

                Text(label)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)
            }

            Text(value)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(Theme.card)
        }
    }
}

// MARK: - Best/Worst Trades Card

private struct BestWorstTradesCard: View {
    let metrics: PerformanceMetrics

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Notable Trades")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)

            HStack(spacing: 12) {
                // Best Trade
                if let best = metrics.bestTrade {
                    NotableTradeCell(
                        trade: best,
                        type: "Best",
                        color: Theme.profit
                    )
                }

                // Worst Trade
                if let worst = metrics.worstTrade {
                    NotableTradeCell(
                        trade: worst,
                        type: "Worst",
                        color: Theme.loss
                    )
                }
            }
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
        }
    }
}

private struct NotableTradeCell: View {
    let trade: ClosedTrade
    let type: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(type)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(color)

                Spacer()

                Image(systemName: type == "Best" ? "crown.fill" : "exclamationmark.triangle.fill")
                    .font(.system(size: 12))
                    .foregroundColor(color)
            }

            Text(trade.optionSymbol)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textPrimary)

            Text(trade.index)
                .font(.system(size: 11))
                .foregroundColor(Theme.textMuted)

            Text(formatPnL(trade.realizedPnL))
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(color.opacity(0.1))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(color.opacity(0.2), lineWidth: 1)
                }
        }
    }

    private func formatPnL(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        if abs(value) >= 1_00_000 {
            return String(format: "%@₹%.1fL", prefix, value / 1_00_000)
        }
        return String(format: "%@₹%.0f", prefix, value)
    }
}

// MARK: - Index Breakdown Card

private struct IndexBreakdownCard: View {
    let metrics: PerformanceMetrics
    let viewModel: PaperTradingViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("P&L by Index")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)

            VStack(spacing: 8) {
                IndexPnLRow(index: "NIFTY", pnl: metrics.niftyPnL, color: Color(hex: "00C805"), viewModel: viewModel)
                IndexPnLRow(index: "BANKNIFTY", pnl: metrics.bankNiftyPnL, color: Color(hex: "007AFF"), viewModel: viewModel)

                if metrics.otherIndexPnL != 0 {
                    IndexPnLRow(index: "Others", pnl: metrics.otherIndexPnL, color: Theme.accentPurple, viewModel: viewModel)
                }
            }
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
        }
    }
}

private struct IndexPnLRow: View {
    let index: String
    let pnl: Double
    let color: Color
    let viewModel: PaperTradingViewModel

    var body: some View {
        HStack {
            Circle()
                .fill(color)
                .frame(width: 10, height: 10)

            Text(index)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Theme.textSecondary)

            Spacer()

            Text(viewModel.formatPnL(pnl))
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundColor(pnl >= 0 ? Theme.profit : Theme.loss)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.card)
        }
    }
}

// MARK: - Empty Performance View

private struct EmptyPerformanceView: View {
    var body: some View {
        VStack(spacing: 20) {
            Spacer()

            // Icon
            ZStack {
                Circle()
                    .fill(Theme.accentBlue.opacity(0.1))
                    .frame(width: 100, height: 100)

                Image(systemName: "chart.line.uptrend.xyaxis")
                    .font(.system(size: 40))
                    .foregroundColor(Theme.accentBlue)
            }

            VStack(spacing: 8) {
                Text("No Performance Data")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text("Complete some trades to see your performance analytics")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }

            Spacer()
        }
    }
}

// MARK: - Preview

#Preview {
    ZStack {
        Theme.backgroundGradient.ignoresSafeArea()
        PaperPerformanceView(viewModel: PaperTradingViewModel())
    }
}
