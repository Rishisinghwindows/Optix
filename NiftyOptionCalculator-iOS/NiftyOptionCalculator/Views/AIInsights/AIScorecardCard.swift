import SwiftUI

struct AIScorecardCard: View {
    let stats: ScorecardStats
    @State private var showAllPicks = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack {
                Image(systemName: "target")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.accentBlue)
                Text("AI Scorecard")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                Text("\(stats.totalPicks) picks tracked")
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)
            }

            if stats.totalPicks == 0 {
                Text("Scorecard will populate as AI suggestions are generated.")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
                    .padding(.vertical, 8)
            } else {
                // Stats Row
                HStack(spacing: 0) {
                    ScorecardStatPill(
                        label: "Win Rate",
                        value: String(format: "%.0f%%", stats.winRate),
                        color: stats.winRate >= 50 ? Theme.profit : Theme.loss
                    )
                    Spacer()
                    ScorecardStatPill(
                        label: "Avg Return",
                        value: String(format: "%+.1f%%", stats.avgReturn),
                        color: stats.avgReturn >= 0 ? Theme.profit : Theme.loss
                    )
                    Spacer()
                    ScorecardStatPill(
                        label: "Top Pick WR",
                        value: String(format: "%.0f%%", stats.topPickWinRate),
                        color: stats.topPickWinRate >= 50 ? Theme.profit : Theme.loss
                    )
                }

                // Outcome bar
                if stats.wins + stats.losses + stats.expired > 0 {
                    OutcomeBar(wins: stats.wins, losses: stats.losses, expired: stats.expired)
                }

                // Active picks count
                if stats.activePicks > 0 {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color(hex: "007AFF"))
                            .frame(width: 6, height: 6)
                        Text("\(stats.activePicks) active picks being tracked")
                            .font(.system(size: 11))
                            .foregroundColor(Theme.textSecondary)
                    }
                }

                // Recent picks list
                if !stats.recentPicks.isEmpty {
                    Divider()
                        .background(Theme.textMuted.opacity(0.3))

                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Recent Picks")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(Theme.textSecondary)
                            Spacer()
                            if stats.recentPicks.count > 5 {
                                Button(action: { showAllPicks.toggle() }) {
                                    Text(showAllPicks ? "Show Less" : "Show All")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(Theme.accentBlue)
                                }
                            }
                        }

                        let picksToShow = showAllPicks ? stats.recentPicks : Array(stats.recentPicks.prefix(5))
                        ForEach(picksToShow, id: \.id) { pick in
                            RecentPickRow(pick: pick)
                        }
                    }
                }
            }
        }
        .padding(14)
        .solidCard()
    }
}

// MARK: - Sub-components

struct ScorecardStatPill: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(color)
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

struct OutcomeBar: View {
    let wins: Int
    let losses: Int
    let expired: Int

    var total: Int { wins + losses + expired }

    var body: some View {
        VStack(spacing: 4) {
            GeometryReader { geo in
                HStack(spacing: 1) {
                    if wins > 0 {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color(hex: "00C805"))
                            .frame(width: geo.size.width * CGFloat(wins) / CGFloat(total))
                    }
                    if losses > 0 {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color(hex: "FF3B30"))
                            .frame(width: geo.size.width * CGFloat(losses) / CGFloat(total))
                    }
                    if expired > 0 {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color(hex: "8E8E93"))
                            .frame(width: geo.size.width * CGFloat(expired) / CGFloat(total))
                    }
                }
            }
            .frame(height: 8)
            .clipShape(RoundedRectangle(cornerRadius: 4))

            HStack(spacing: 12) {
                Label("\(wins)W", systemImage: "checkmark.circle.fill")
                    .font(.system(size: 10))
                    .foregroundColor(Color(hex: "00C805"))
                Label("\(losses)L", systemImage: "xmark.circle.fill")
                    .font(.system(size: 10))
                    .foregroundColor(Color(hex: "FF3B30"))
                Label("\(expired)E", systemImage: "calendar.badge.clock")
                    .font(.system(size: 10))
                    .foregroundColor(Color(hex: "8E8E93"))
                Spacer()
            }
        }
    }
}

struct RecentPickRow: View {
    let pick: TrackedPick

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: pick.outcome.icon)
                .font(.system(size: 12))
                .foregroundColor(pick.outcome.color)
                .frame(width: 18)

            Text("\(pick.indexName) \(String(format: "%.0f", pick.strikePrice)) \(pick.optionType)")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Theme.textPrimary)

            Spacer()

            if let ret = pick.returnPct {
                Text(String(format: "%+.1f%%", ret))
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(ret >= 0 ? Theme.profit : Theme.loss)
            } else {
                Text("Active")
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: "007AFF"))
            }

            Text("S:\(Int(pick.score))")
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)
        }
        .padding(.vertical, 2)
    }
}
