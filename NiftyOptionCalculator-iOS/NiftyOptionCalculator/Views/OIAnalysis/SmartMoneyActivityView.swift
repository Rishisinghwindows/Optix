import SwiftUI

// MARK: - Smart Money Activity View

struct SmartMoneyActivityView: View {
    let activities: [SmartMoneyActivity]
    let topCallOIStrikes: [StrikeOIData]
    let topPutOIStrikes: [StrikeOIData]
    let topOIChangeStrikes: [StrikeOIData]

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Smart money activity feed
                if !activities.isEmpty {
                    ActivityFeedSection(activities: activities)
                }

                // Top OI strikes
                TopOISection(
                    title: "Highest Call OI",
                    subtitle: "Resistance levels",
                    strikes: topCallOIStrikes,
                    optionType: .call
                )

                TopOISection(
                    title: "Highest Put OI",
                    subtitle: "Support levels",
                    strikes: topPutOIStrikes,
                    optionType: .put
                )

                // Top OI change
                OIChangeSection(strikes: topOIChangeStrikes)
            }
            .padding()
        }
    }
}

// MARK: - Activity Feed Section

private struct ActivityFeedSection: View {
    let activities: [SmartMoneyActivity]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "bolt.fill")
                    .foregroundColor(Theme.accentOrange)

                Text("Smart Money Activity")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                Text("Live")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(Theme.accentGreen)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Theme.accentGreen.opacity(0.2))
                    .cornerRadius(6)
            }

            if activities.isEmpty {
                Text("No significant activity detected")
                    .font(.caption)
                    .foregroundColor(Theme.textMuted)
                    .padding(.vertical, 8)
            } else {
                ForEach(activities.prefix(5)) { activity in
                    ActivityRow(activity: activity)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Activity Row

private struct ActivityRow: View {
    let activity: SmartMoneyActivity

    var body: some View {
        HStack(spacing: 12) {
            // Activity icon
            Image(systemName: activity.activity.icon)
                .font(.body)
                .foregroundColor(activity.activity.color)
                .frame(width: 32, height: 32)
                .background(activity.activity.color.opacity(0.15))
                .cornerRadius(8)

            // Details
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(String(format: "%.0f", activity.strikePrice))
                        .font(.system(.subheadline, design: .monospaced))
                        .fontWeight(.semibold)
                        .foregroundColor(Theme.textPrimary)

                    Text(activity.optionType == .call ? "CE" : "PE")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(activity.optionType == .call ? Theme.loss : Theme.profit)
                }

                Text(activity.activity.description)
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
            }

            Spacer()

            // Change value
            VStack(alignment: .trailing, spacing: 2) {
                Text(activity.displayChange)
                    .font(.system(.caption, design: .monospaced))
                    .fontWeight(.bold)
                    .foregroundColor(activity.activity.color)

                Text(activity.activity.sentiment)
                    .font(.caption2)
                    .foregroundColor(Theme.textMuted)
            }
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Top OI Section

private struct TopOISection: View {
    let title: String
    let subtitle: String
    let strikes: [StrikeOIData]
    let optionType: OptionType

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: optionType == .call ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                    .foregroundColor(optionType == .call ? Theme.loss : Theme.profit)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(Theme.textPrimary)

                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                }

                Spacer()
            }

            ForEach(Array(strikes.enumerated()), id: \.element.id) { index, strike in
                TopOIRow(
                    rank: index + 1,
                    strike: strike,
                    optionType: optionType
                )
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Top OI Row

private struct TopOIRow: View {
    let rank: Int
    let strike: StrikeOIData
    let optionType: OptionType

    private var oi: Int {
        optionType == .call ? strike.callOI : strike.putOI
    }

    private var oiChange: Int {
        optionType == .call ? strike.callOIChange : strike.putOIChange
    }

    var body: some View {
        HStack {
            // Rank
            Text("#\(rank)")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Theme.textMuted)
                .frame(width: 24)

            // Strike
            Text(strike.displayStrike)
                .font(.system(.subheadline, design: .monospaced))
                .fontWeight(.semibold)
                .foregroundColor(Theme.textPrimary)

            Spacer()

            // OI
            Text(formatOI(oi))
                .font(.system(.caption, design: .monospaced))
                .foregroundColor(Theme.textSecondary)

            // Change
            if oiChange != 0 {
                HStack(spacing: 2) {
                    Image(systemName: oiChange > 0 ? "arrow.up" : "arrow.down")
                        .font(.system(size: 10))
                    Text(formatOI(abs(oiChange)))
                        .font(.system(size: 11, design: .monospaced))
                }
                .foregroundColor(oiChange > 0 ? Theme.profit : Theme.loss)
                .frame(width: 60, alignment: .trailing)
            }
        }
        .padding(.vertical, 4)
    }

    private func formatOI(_ oi: Int) -> String {
        if oi >= 100_000 {
            return String(format: "%.1fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.0fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }
}

// MARK: - OI Change Section

private struct OIChangeSection: View {
    let strikes: [StrikeOIData]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "arrow.triangle.swap")
                    .foregroundColor(Theme.accentPurple)

                Text("Highest OI Change")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)

                Spacer()
            }

            ForEach(strikes) { strike in
                OIChangeRow(strike: strike)
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - OI Change Row

private struct OIChangeRow: View {
    let strike: StrikeOIData

    var body: some View {
        HStack {
            // Strike
            Text(strike.displayStrike)
                .font(.system(.subheadline, design: .monospaced))
                .fontWeight(.semibold)
                .foregroundColor(Theme.textPrimary)

            Spacer()

            // Call change
            VStack(alignment: .trailing, spacing: 2) {
                Text("CE")
                    .font(.caption2)
                    .foregroundColor(Theme.loss)
                HStack(spacing: 2) {
                    Image(systemName: strike.callOIChange > 0 ? "plus" : "minus")
                        .font(.system(size: 8))
                    Text(formatOI(abs(strike.callOIChange)))
                        .font(.system(size: 11, design: .monospaced))
                }
                .foregroundColor(strike.callOIChange > 0 ? Theme.profit : Theme.loss)
            }
            .frame(width: 60)

            // Put change
            VStack(alignment: .trailing, spacing: 2) {
                Text("PE")
                    .font(.caption2)
                    .foregroundColor(Theme.profit)
                HStack(spacing: 2) {
                    Image(systemName: strike.putOIChange > 0 ? "plus" : "minus")
                        .font(.system(size: 8))
                    Text(formatOI(abs(strike.putOIChange)))
                        .font(.system(size: 11, design: .monospaced))
                }
                .foregroundColor(strike.putOIChange > 0 ? Theme.profit : Theme.loss)
            }
            .frame(width: 60)

            // Direction
            DirectionBadge(direction: strike.oiChangeDirection)
        }
        .padding(.vertical, 4)
    }

    private func formatOI(_ oi: Int) -> String {
        if oi >= 100_000 {
            return String(format: "%.1fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.0fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }
}

// MARK: - Direction Badge

private struct DirectionBadge: View {
    let direction: OIChangeDirection

    var body: some View {
        Text(direction.sentiment)
            .font(.caption2)
            .fontWeight(.medium)
            .foregroundColor(direction.color)
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(direction.color.opacity(0.15))
            .cornerRadius(4)
    }
}

// MARK: - Preview

#Preview {
    SmartMoneyActivityView(
        activities: [
            SmartMoneyActivity(
                strikePrice: 24000,
                optionType: .call,
                activity: .heavyCallWriting,
                oiChange: 150000,
                percentChange: 0.25,
                timestamp: Date()
            )
        ],
        topCallOIStrikes: [],
        topPutOIStrikes: [],
        topOIChangeStrikes: []
    )
    .background(Theme.background)
    
}
