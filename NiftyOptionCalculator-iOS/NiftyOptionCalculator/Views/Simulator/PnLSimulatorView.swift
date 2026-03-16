import SwiftUI
import Charts

struct PnLSimulatorView: View {
    @ObservedObject var viewModel: PnLSimulatorViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    // Sliders Section
                    SimulatorSlidersCard(viewModel: viewModel)

                    // Total P&L Card
                    TotalPnLCard(
                        totalPnl: viewModel.totalPnl,
                        totalPnlPercent: viewModel.totalPnlPercent
                    )

                    // Per-Leg Breakdown
                    if !viewModel.legResults.isEmpty {
                        LegBreakdownSection(legResults: viewModel.legResults)
                    }

                    // Payoff Chart
                    if !viewModel.payoffPoints.isEmpty {
                        PayoffChartCard(points: viewModel.payoffPoints)
                    }

                    // Reset Button
                    Button {
                        let generator = UIImpactFeedbackGenerator(style: .medium)
                        generator.impactOccurred()
                        viewModel.resetSliders()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.counterclockwise")
                                .font(.system(size: 14, weight: .semibold))
                            Text("Reset Sliders")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundColor(Theme.accentBlue)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Theme.accentBlue.opacity(0.12))
                        .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("P&L Simulator")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22))
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
    }
}

// MARK: - Simulator Sliders Card

private struct SimulatorSlidersCard: View {
    @ObservedObject var viewModel: PnLSimulatorViewModel

    var body: some View {
        VStack(spacing: 16) {
            // Spot Change Slider
            SimulatorSliderRow(
                label: "Spot Change",
                value: $viewModel.spotChangePercent,
                range: -10...10,
                step: 0.1,
                unit: "%",
                format: "%.1f",
                accentColor: Theme.accentBlue
            )

            Divider().background(Theme.border)

            // Days Elapsed Slider
            SimulatorSliderRow(
                label: "Days Elapsed",
                value: $viewModel.daysElapsed,
                range: 0...Double(max(viewModel.maxDTE, 1)),
                step: 1,
                unit: "d",
                format: "%.0f",
                accentColor: Theme.accentOrange
            )

            Divider().background(Theme.border)

            // IV Change Slider
            SimulatorSliderRow(
                label: "IV Change",
                value: $viewModel.ivChangePercent,
                range: -30...30,
                step: 0.5,
                unit: "%",
                format: "%.1f",
                accentColor: Theme.accentPurple
            )
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Theme.borderLight, lineWidth: 1)
                )
        )
    }
}

// MARK: - Simulator Slider Row

private struct SimulatorSliderRow: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double
    let unit: String
    let format: String
    let accentColor: Color

    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Text(label)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)
                Spacer()
                Text("\(String(format: format, value))\(unit)")
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundColor(value == 0 ? Theme.textPrimary : (value > 0 ? Theme.accentGreen : Theme.accentRed))
            }

            Slider(value: $value, in: range, step: step)
                .tint(accentColor)

            HStack {
                Text(String(format: format, range.lowerBound) + unit)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
                Spacer()
                Text(String(format: format, range.upperBound) + unit)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
            }
        }
    }
}

// MARK: - Total P&L Card

private struct TotalPnLCard: View {
    let totalPnl: Double
    let totalPnlPercent: Double

    private var isProfit: Bool { totalPnl >= 0 }

    var body: some View {
        VStack(spacing: 8) {
            Text("Total P&L")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(Theme.textSecondary)

            Text(formatCurrency(totalPnl))
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundColor(isProfit ? Theme.accentGreen : Theme.accentRed)

            Text(String(format: "%@%.1f%%", totalPnlPercent >= 0 ? "+" : "", totalPnlPercent))
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(isProfit ? Theme.accentGreen : Theme.accentRed)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
                .background(
                    (isProfit ? Theme.accentGreen : Theme.accentRed).opacity(0.12)
                )
                .cornerRadius(8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(
                            (isProfit ? Theme.accentGreen : Theme.accentRed).opacity(0.3),
                            lineWidth: 1.5
                        )
                )
        )
    }

    private func formatCurrency(_ value: Double) -> String {
        let sign = value >= 0 ? "+" : ""
        let absValue = abs(value)
        if absValue >= 100_000 {
            return "\(sign)\(String(format: "%.1f", value / 100_000))L"
        } else if absValue >= 1_000 {
            return "\(sign)\(String(format: "%.1f", value / 1_000))K"
        }
        return "\(sign)\(String(format: "%.2f", value))"
    }
}

// MARK: - Leg Breakdown Section

private struct LegBreakdownSection: View {
    let legResults: [LegSimResult]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Per-Leg Breakdown")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
                .padding(.horizontal, 4)

            ForEach(legResults) { result in
                LegResultCard(result: result)
            }
        }
    }
}

// MARK: - Leg Result Card

private struct LegResultCard: View {
    let result: LegSimResult

    private var isProfit: Bool { result.pnl >= 0 }

    var body: some View {
        VStack(spacing: 10) {
            // Header
            HStack {
                Text(String(format: "%.0f", result.leg.strikePrice))
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text(result.leg.optionType.shortName)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(
                        result.leg.optionType == .call
                            ? Theme.accentGreen
                            : Theme.accentRed
                    )
                    .cornerRadius(5)

                Text("\(result.leg.quantity)x\(result.leg.lotSize)")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Theme.textMuted)

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(String(format: "%@%.2f", result.pnl >= 0 ? "+" : "", result.pnl))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(isProfit ? Theme.accentGreen : Theme.accentRed)
                    Text(String(format: "%@%.1f%%", result.pnlPercent >= 0 ? "+" : "", result.pnlPercent))
                        .font(.system(size: 11))
                        .foregroundColor(isProfit ? Theme.accentGreen : Theme.accentRed)
                }
            }

            // Price transition
            HStack(spacing: 6) {
                Text(String(format: "%.2f", result.leg.entryPremium))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Theme.textMuted)
                Image(systemName: "arrow.right")
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
                Text(String(format: "%.2f", result.newPrice))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(isProfit ? Theme.accentGreen : Theme.accentRed)

                Spacer()
            }

            // Greeks
            HStack(spacing: 0) {
                SimGreekItem(label: "Delta", value: String(format: "%.3f", result.newDelta))
                SimGreekItem(label: "Gamma", value: String(format: "%.5f", result.newGamma))
                SimGreekItem(label: "Theta", value: String(format: "%.2f", result.newTheta))
                SimGreekItem(label: "Vega", value: String(format: "%.2f", result.newVega))
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Theme.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Theme.borderLight, lineWidth: 1)
                )
        )
    }
}

// MARK: - Sim Greek Item

private struct SimGreekItem: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)
            Text(value)
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Payoff Chart Card

private struct PayoffChartCard: View {
    let points: [PayoffPoint]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "chart.xyaxis.line")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.accentBlue)
                Text("Payoff Diagram")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
            }

            Chart {
                // Profit area (above zero)
                ForEach(points) { point in
                    AreaMark(
                        x: .value("Spot", point.price),
                        yStart: .value("Zero", 0),
                        yEnd: .value("P&L", max(point.payoff, 0))
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.accentGreen.opacity(0.3), Theme.accentGreen.opacity(0.05)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .interpolationMethod(.catmullRom)
                }

                // Loss area (below zero)
                ForEach(points) { point in
                    AreaMark(
                        x: .value("Spot", point.price),
                        yStart: .value("P&L", min(point.payoff, 0)),
                        yEnd: .value("Zero", 0)
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.accentRed.opacity(0.05), Theme.accentRed.opacity(0.3)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .interpolationMethod(.catmullRom)
                }

                // P&L Line
                ForEach(points) { point in
                    LineMark(
                        x: .value("Spot", point.price),
                        y: .value("P&L", point.payoff)
                    )
                    .foregroundStyle(point.payoff >= 0 ? Theme.accentGreen : Theme.accentRed)
                    .lineStyle(StrokeStyle(lineWidth: 2))
                    .interpolationMethod(.catmullRom)
                }

                // Breakeven line
                RuleMark(y: .value("Breakeven", 0))
                    .foregroundStyle(Theme.textMuted.opacity(0.5))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
            }
            .chartXAxisLabel("Spot Price", position: .bottom, alignment: .center)
            .chartYAxisLabel("P&L", position: .leading, alignment: .center)
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 5)) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(Theme.border)
                    AxisValueLabel()
                        .font(.system(size: 9))
                        .foregroundStyle(Theme.textMuted)
                }
            }
            .chartYAxis {
                AxisMarks(values: .automatic(desiredCount: 5)) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(Theme.border)
                    AxisValueLabel()
                        .font(.system(size: 9))
                        .foregroundStyle(Theme.textMuted)
                }
            }
            .frame(height: 220)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Theme.borderLight, lineWidth: 1)
                )
        )
    }
}
