import SwiftUI

// MARK: - Strategy Detail View

struct StrategyDetailView: View {
    let strategy: OptionStrategy

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Header
                headerSection

                // Strategy Legs
                legsSection

                // Risk/Reward
                riskRewardSection

                // When To Use
                whenToUseSection

                // Pros & Cons
                prosConsSection

                // Payoff Example
                if let example = strategy.example {
                    payoffSection(example)
                }

                Spacer(minLength: 40)
            }
            .padding(16)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(strategy.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Header Section

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Tags
            HStack(spacing: 8) {
                StrategyTypeBadge(type: strategy.type)
                OutlookBadge(outlook: strategy.outlook)
                EducationRiskBadge(level: strategy.riskLevel)
            }

            Text(strategy.description)
                .font(.system(size: 15))
                .foregroundColor(Theme.textSecondary)
                .lineSpacing(4)
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Legs Section

    private var legsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.educationStrategyLegs)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.textPrimary)

            VStack(spacing: 8) {
                ForEach(strategy.legs) { leg in
                    StrategyLegRow(leg: leg)
                }
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Risk/Reward Section

    private var riskRewardSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L.educationRiskReward)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.textPrimary)

            VStack(spacing: 12) {
                RiskRewardRow(
                    label: L.educationMaxProfit,
                    value: strategy.maxProfit,
                    icon: "arrow.up.circle.fill",
                    color: Theme.profit
                )

                RiskRewardRow(
                    label: L.educationMaxLoss,
                    value: strategy.maxLoss,
                    icon: "arrow.down.circle.fill",
                    color: Theme.loss
                )

                RiskRewardRow(
                    label: L.educationBreakeven,
                    value: strategy.breakeven,
                    icon: "equal.circle.fill",
                    color: Theme.accentBlue
                )
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - When To Use Section

    private var whenToUseSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "clock.fill")
                    .foregroundColor(Theme.accentPurple)
                Text(L.educationWhenToUse)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
            }

            VStack(alignment: .leading, spacing: 8) {
                ForEach(strategy.whenToUse, id: \.self) { reason in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Theme.accentPurple)
                            .frame(width: 20)

                        Text(reason)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
        .padding(16)
        .background(Theme.accentPurple.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Pros & Cons Section

    private var prosConsSection: some View {
        HStack(alignment: .top, spacing: 12) {
            // Pros
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "hand.thumbsup.fill")
                        .foregroundColor(Theme.profit)
                    Text(L.educationPros)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Theme.profit)
                }

                VStack(alignment: .leading, spacing: 6) {
                    ForEach(strategy.pros, id: \.self) { pro in
                        Text("• \(pro)")
                            .font(.system(size: 12))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.profit.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12))

            // Cons
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "hand.thumbsdown.fill")
                        .foregroundColor(Theme.loss)
                    Text(L.educationCons)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Theme.loss)
                }

                VStack(alignment: .leading, spacing: 6) {
                    ForEach(strategy.cons, id: \.self) { con in
                        Text("• \(con)")
                            .font(.system(size: 12))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.loss.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    // MARK: - Payoff Section

    private func payoffSection(_ example: EducationStrategyExample) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L.educationPayoffExample)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.textPrimary)

            // Setup Info
            VStack(alignment: .leading, spacing: 6) {
                Text("\(L.educationSpotPrice): \(formatPrice(example.spotPrice))")
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)

                Text("\(L.educationNetCost): \(formatPrice(abs(example.netCost)))\(example.netCost < 0 ? " (Credit)" : " (Debit)")")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(example.netCost < 0 ? Theme.profit : Theme.textPrimary)
            }
            .padding(10)
            .background(Theme.background)
            .clipShape(RoundedRectangle(cornerRadius: 8))

            // Scenarios
            VStack(spacing: 8) {
                ForEach(example.scenarios) { scenario in
                    ScenarioRow(scenario: scenario)
                }
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func formatPrice(_ price: Double) -> String {
        "₹\(String(format: "%.0f", price))"
    }
}

// MARK: - Strategy Type Badge

struct StrategyTypeBadge: View {
    let type: EducationStrategyType

    var body: some View {
        Text(type.rawValue)
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(color)
            .clipShape(Capsule())
    }

    private var color: Color {
        switch type {
        case .bullish: return Theme.profit
        case .bearish: return Theme.loss
        case .neutral: return Theme.accentBlue
        case .volatile: return Theme.accentOrange
        }
    }
}

// MARK: - Outlook Badge

struct OutlookBadge: View {
    let outlook: EducationMarketOutlook

    var body: some View {
        Text(outlook.rawValue)
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(Theme.accentPurple)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Theme.accentPurple.opacity(0.15))
            .clipShape(Capsule())
    }
}

// MARK: - Risk Badge

struct EducationRiskBadge: View {
    let level: EducationRiskLevel

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 10))
            Text(level.rawValue)
                .font(.system(size: 11, weight: .bold))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(level.color)
        .clipShape(Capsule())
    }
}

// MARK: - Strategy Leg Row

struct StrategyLegRow: View {
    let leg: EducationStrategyLeg

    var body: some View {
        HStack(spacing: 12) {
            // Action
            Text(leg.action)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(leg.action == "Buy" ? Theme.profit : Theme.loss)
                .frame(width: 40, alignment: .leading)

            // Quantity
            Text("×\(leg.quantity)")
                .font(.system(size: 13, weight: .medium, design: .monospaced))
                .foregroundColor(Theme.textMuted)

            // Option Type
            Text(leg.optionType)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Theme.textPrimary)

            // Strike
            Text("@ \(leg.strike)")
                .font(.system(size: 13))
                .foregroundColor(Theme.textSecondary)

            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Theme.background)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Risk Reward Row

struct RiskRewardRow: View {
    let label: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        HStack {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(color)
                .frame(width: 30)

            Text(label)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)

            Spacer()

            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Theme.background)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Scenario Row

struct ScenarioRow: View {
    let scenario: ScenarioResult

    var body: some View {
        HStack {
            // Expiry Price
            VStack(alignment: .leading, spacing: 2) {
                Text(L.educationExpiryAt)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
                Text("₹\(String(format: "%.0f", scenario.expiryPrice))")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)
            }

            Spacer()

            // Description
            Text(scenario.description)
                .font(.system(size: 12))
                .foregroundColor(Theme.textSecondary)
                .lineLimit(1)

            Spacer()

            // P&L
            Text(formatPnL(scenario.profit))
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(scenario.profit >= 0 ? Theme.profit : Theme.loss)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(scenario.profit >= 0 ? Theme.profit.opacity(0.05) : Theme.loss.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func formatPnL(_ profit: Double) -> String {
        let prefix = profit >= 0 ? "+" : ""
        return "\(prefix)₹\(String(format: "%.0f", profit))"
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        StrategyDetailView(strategy: EducationDataProvider.shared.strategies[0])
    }
}
