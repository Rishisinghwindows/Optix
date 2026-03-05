import SwiftUI

// MARK: - OI Zones View

struct OIZonesView: View {
    let supportZones: [OIZone]
    let resistanceZones: [OIZone]
    let spotPrice: Double
    let interpretation: OIInterpretation?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Interpretation card
                if let interpretation = interpretation {
                    InterpretationCard(interpretation: interpretation)
                }

                // Visual range indicator
                RangeIndicatorView(
                    supportZones: supportZones,
                    resistanceZones: resistanceZones,
                    spotPrice: spotPrice
                )

                // Resistance zones
                ZoneSection(
                    title: "Resistance Zones",
                    subtitle: "Call OI Concentration",
                    zones: resistanceZones,
                    type: .resistance
                )

                // Support zones
                ZoneSection(
                    title: "Support Zones",
                    subtitle: "Put OI Concentration",
                    zones: supportZones,
                    type: .support
                )

                // Expected range
                if let range = interpretation?.expectedRange {
                    ExpectedRangeCard(range: range, spotPrice: spotPrice)
                }
            }
            .padding()
        }
    }
}

// MARK: - Interpretation Card

private struct InterpretationCard: View {
    let interpretation: OIInterpretation

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: interpretation.bias.icon)
                    .font(.title2)
                    .foregroundColor(interpretation.bias.color)

                VStack(alignment: .leading, spacing: 2) {
                    Text(interpretation.title)
                        .font(.headline)
                        .foregroundColor(Theme.textPrimary)

                    Text(interpretation.bias.label)
                        .font(.caption)
                        .foregroundColor(interpretation.bias.color)
                }

                Spacer()
            }

            Text(interpretation.description)
                .font(.subheadline)
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            Divider()
                .background(Theme.textMuted.opacity(0.3))

            // Key insights
            VStack(alignment: .leading, spacing: 8) {
                Text("Key Insights")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.textSecondary)

                ForEach(interpretation.keyInsights.prefix(4)) { insight in
                    InsightRow(insight: insight)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Insight Row

private struct InsightRow: View {
    let insight: OIInsight

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: insight.icon)
                .font(.body)
                .foregroundColor(insight.importance.color)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 2) {
                Text(insight.title)
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.textPrimary)

                Text(insight.description)
                    .font(.caption2)
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - Range Indicator View

private struct RangeIndicatorView: View {
    let supportZones: [OIZone]
    let resistanceZones: [OIZone]
    let spotPrice: Double

    private var immediateSupport: Double {
        supportZones.first?.strikePrice ?? (spotPrice - 200)
    }

    private var immediateResistance: Double {
        resistanceZones.first?.strikePrice ?? (spotPrice + 200)
    }

    private var rangeWidth: Double {
        immediateResistance - immediateSupport
    }

    private var spotPosition: CGFloat {
        guard rangeWidth > 0 else { return 0.5 }
        return CGFloat((spotPrice - immediateSupport) / rangeWidth)
    }

    var body: some View {
        VStack(spacing: 12) {
            Text("Current Position")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(Theme.textSecondary)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    // Background bar
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Theme.surface)
                        .frame(height: 40)

                    // Support zone (green)
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Theme.profit.opacity(0.3))
                        .frame(width: geo.size.width * 0.3, height: 40)

                    // Resistance zone (red)
                    HStack {
                        Spacer()
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Theme.loss.opacity(0.3))
                            .frame(width: geo.size.width * 0.3, height: 40)
                    }

                    // Spot price indicator
                    VStack(spacing: 2) {
                        Circle()
                            .fill(Theme.accentBlue)
                            .frame(width: 12, height: 12)

                        Text("SPOT")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(Theme.accentBlue)
                    }
                    .offset(x: geo.size.width * spotPosition - 6)
                }
            }
            .frame(height: 60)

            // Labels
            HStack {
                VStack(alignment: .leading) {
                    Text("Support")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "%.0f", immediateSupport))
                        .font(.system(.caption, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.profit)
                }

                Spacer()

                VStack {
                    Text("Spot")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "%.0f", spotPrice))
                        .font(.system(.caption, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.accentBlue)
                }

                Spacer()

                VStack(alignment: .trailing) {
                    Text("Resistance")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "%.0f", immediateResistance))
                        .font(.system(.caption, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.loss)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Zone Section

private struct ZoneSection: View {
    let title: String
    let subtitle: String
    let zones: [OIZone]
    let type: OIZoneType

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: type.icon)
                    .foregroundColor(type.color)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(Theme.textPrimary)

                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                }

                Spacer()

                Text("\(zones.count) zones")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
            }

            if zones.isEmpty {
                Text("No significant \(type.label.lowercased()) zones identified")
                    .font(.caption)
                    .foregroundColor(Theme.textMuted)
                    .padding(.vertical, 8)
            } else {
                ForEach(zones.prefix(5)) { zone in
                    ZoneRow(zone: zone)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Zone Row

private struct ZoneRow: View {
    let zone: OIZone

    var body: some View {
        HStack {
            // Strength indicator
            Circle()
                .fill(zone.strength.color)
                .frame(width: 8, height: 8)

            // Strike price
            Text(zone.displayStrike)
                .font(.system(.subheadline, design: .monospaced))
                .fontWeight(.semibold)
                .foregroundColor(Theme.textPrimary)

            Spacer()

            // OI value
            Text(zone.displayOI)
                .font(.system(.caption, design: .monospaced))
                .foregroundColor(Theme.textSecondary)

            // Strength badge
            Text(zone.strength.rawValue)
                .font(.caption2)
                .fontWeight(.medium)
                .foregroundColor(zone.strength.color)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(zone.strength.color.opacity(0.15))
                .cornerRadius(6)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Expected Range Card

private struct ExpectedRangeCard: View {
    let range: ClosedRange<Double>
    let spotPrice: Double

    private var rangeWidth: Double {
        range.upperBound - range.lowerBound
    }

    private var upside: Double {
        range.upperBound - spotPrice
    }

    private var downside: Double {
        spotPrice - range.lowerBound
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "ruler.fill")
                    .foregroundColor(Theme.accentPurple)

                Text("Expected Trading Range")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)

                Spacer()
            }

            HStack(spacing: 16) {
                // Lower bound
                VStack(alignment: .leading) {
                    Text("Lower")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "%.0f", range.lowerBound))
                        .font(.system(.title3, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.profit)
                }

                Spacer()

                // Range width
                VStack {
                    Text("Width")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "%.0f pts", rangeWidth))
                        .font(.system(.subheadline, design: .monospaced))
                        .foregroundColor(Theme.textSecondary)
                }

                Spacer()

                // Upper bound
                VStack(alignment: .trailing) {
                    Text("Upper")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "%.0f", range.upperBound))
                        .font(.system(.title3, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.loss)
                }
            }

            Divider()
                .background(Theme.textMuted.opacity(0.3))

            HStack {
                HStack(spacing: 4) {
                    Image(systemName: "arrow.down")
                        .font(.caption2)
                    Text("Downside: \(String(format: "%.0f", downside)) pts")
                        .font(.caption)
                }
                .foregroundColor(Theme.loss)

                Spacer()

                HStack(spacing: 4) {
                    Text("Upside: \(String(format: "%.0f", upside)) pts")
                        .font(.caption)
                    Image(systemName: "arrow.up")
                        .font(.caption2)
                }
                .foregroundColor(Theme.profit)
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Preview

#Preview {
    OIZonesView(
        supportZones: [
            OIZone(type: .support, strikePrice: 23900, strength: .strong, oiValue: 450000, percentOfMax: 0.9),
            OIZone(type: .support, strikePrice: 23800, strength: .moderate, oiValue: 320000, percentOfMax: 0.7)
        ],
        resistanceZones: [
            OIZone(type: .resistance, strikePrice: 24100, strength: .strong, oiValue: 520000, percentOfMax: 0.95),
            OIZone(type: .resistance, strikePrice: 24200, strength: .moderate, oiValue: 280000, percentOfMax: 0.65)
        ],
        spotPrice: 24000,
        interpretation: OIInterpretation(
            title: "Range-Bound Setup",
            description: "Market showing neutral characteristics. Expected to trade within support and resistance zones.",
            keyInsights: [
                OIInsight(icon: "chart.pie.fill", title: "PCR at 1.15", description: "Balanced sentiment", importance: .medium)
            ],
            expectedRange: 23900...24100,
            bias: .neutral
        )
    )
    .background(Theme.background)
    
}
