import SwiftUI

// MARK: - Analysis Summary Card

struct AnalysisSummaryCard: View {
    let payoffData: PayoffData
    let analysis: StrategyAnalysis?
    let strategy: Strategy

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header
            Text("Strategy Analysis")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            // Main metrics
            HStack(spacing: 16) {
                MetricCard(
                    title: "Max Profit",
                    value: payoffData.displayMaxProfit,
                    color: Theme.profit,
                    icon: "arrow.up.circle.fill"
                )

                MetricCard(
                    title: "Max Loss",
                    value: payoffData.displayMaxLoss,
                    color: Theme.loss,
                    icon: "arrow.down.circle.fill"
                )
            }

            HStack(spacing: 16) {
                MetricCard(
                    title: "Risk/Reward",
                    value: payoffData.displayRiskReward,
                    color: Theme.accentBlue,
                    icon: "scale.3d"
                )

                MetricCard(
                    title: "P.O.P.",
                    value: analysis?.displayPOP ?? "N/A",
                    color: Theme.accentPurple,
                    icon: "percent"
                )
            }

            // Breakevens
            if !payoffData.breakevens.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Breakeven Points")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(Theme.textSecondary)

                    HStack(spacing: 8) {
                        ForEach(payoffData.breakevens, id: \.self) { be in
                            BreakevenBadge(value: be)
                        }
                    }
                }
            }

            // Net premium
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Net Premium")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                    Text(strategy.displayNetPremium)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(strategy.isDebit ? Theme.loss : Theme.profit)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("Margin Required")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                    Text(analysis?.displayMargin ?? "N/A")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.textPrimary)
                }
            }
            .padding(.top, 8)

            // Greeks summary
            if let analysis = analysis {
                Divider()
                    .background(Theme.textMuted.opacity(0.3))

                GreeksSummaryRow(greeks: analysis.netGreeks)
            }
        }
        .padding(16)
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Metric Card

private struct MetricCard: View {
    let title: String
    let value: String
    let color: Color
    let icon: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(Theme.textMuted)

                Text(value)
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(color)
            }

            Spacer()
        }
        .padding(12)
        .background(color.opacity(0.1))
        .cornerRadius(12)
    }
}

// MARK: - Breakeven Badge

private struct BreakevenBadge: View {
    let value: Double

    var body: some View {
        Text(String(format: "%.0f", value))
            .font(.system(.caption, design: .monospaced))
            .fontWeight(.medium)
            .foregroundColor(Theme.accentOrange)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Theme.accentOrange.opacity(0.15))
            .cornerRadius(8)
    }
}

// MARK: - Greeks Summary Row

private struct GreeksSummaryRow: View {
    let greeks: StrategyAnalysis.NetGreeks

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Net Greeks")
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(Theme.textSecondary)

            HStack(spacing: 0) {
                GreekCell(label: "Delta", value: greeks.displayDelta, color: deltaColor)
                GreekCell(label: "Gamma", value: greeks.displayGamma, color: Theme.textPrimary)
                GreekCell(label: "Theta", value: greeks.displayTheta, color: thetaColor)
                GreekCell(label: "Vega", value: greeks.displayVega, color: Theme.textPrimary)
            }
        }
    }

    private var deltaColor: Color {
        let delta = greeks.delta
        if delta > 0.1 { return Theme.profit }
        if delta < -0.1 { return Theme.loss }
        return Theme.textPrimary
    }

    private var thetaColor: Color {
        let theta = greeks.theta
        if theta > 0 { return Theme.profit }
        if theta < 0 { return Theme.loss }
        return Theme.textPrimary
    }
}

private struct GreekCell: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.caption2)
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(.caption, design: .monospaced))
                .fontWeight(.semibold)
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Preview

#Preview {
    let samplePoints = stride(from: 23000.0, through: 25000.0, by: 20).map { price in
        PayoffPoint(price: price, payoff: (price - 24000) * 25 - 5000)
    }

    return AnalysisSummaryCard(
        payoffData: PayoffData(
            points: samplePoints,
            maxProfit: 25000,
            maxLoss: -5000,
            breakevens: [24200, 23800],
            spotPrice: 24000,
            payoffAtSpot: -5000
        ),
        analysis: StrategyAnalysis(
            maxProfit: 25000,
            maxLoss: -5000,
            breakevens: [24200, 23800],
            probabilityOfProfit: 0.45,
            expectedValue: 2500,
            netGreeks: StrategyAnalysis.NetGreeks(
                delta: 12.5,
                gamma: 0.025,
                theta: -125,
                vega: 200
            ),
            marginRequired: 75000
        ),
        strategy: Strategy(
            type: .bullCallSpread,
            legs: [],
            lotSize: 25,
            underlyingPrice: 24000,
            expiryDate: Date()
        )
    )
    .padding()
    .background(Theme.background)
    
}
