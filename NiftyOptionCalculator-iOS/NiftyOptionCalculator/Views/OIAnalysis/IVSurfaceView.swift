import SwiftUI
import Charts

// MARK: - IV Surface View

struct IVSurfaceView: View {
    let callIVPoints: [IVSurfacePoint]
    let putIVPoints: [IVSurfacePoint]
    let spotPrice: Double

    @State private var selectedPoint: IVSurfacePoint?
    @State private var showCalls = true
    @State private var showPuts = true

    var body: some View {
        VStack(spacing: 16) {
            // Header
            HStack {
                Text("IV Surface")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                // Toggle buttons
                HStack(spacing: 8) {
                    ToggleButton(label: "CE", isOn: $showCalls, color: Theme.loss)
                    ToggleButton(label: "PE", isOn: $showPuts, color: Theme.profit)
                }
            }
            .padding(.horizontal)

            // Chart
            Chart {
                if showCalls {
                    ForEach(callIVPoints) { point in
                        LineMark(
                            x: .value("Strike", point.strikePrice),
                            y: .value("IV", point.iv * 100)
                        )
                        .foregroundStyle(Theme.loss)
                        .interpolationMethod(.catmullRom)
                        .lineStyle(StrokeStyle(lineWidth: 2))

                        if point.isATM {
                            PointMark(
                                x: .value("Strike", point.strikePrice),
                                y: .value("IV", point.iv * 100)
                            )
                            .foregroundStyle(Theme.accentBlue)
                            .symbolSize(80)
                        }
                    }
                }

                if showPuts {
                    ForEach(putIVPoints) { point in
                        LineMark(
                            x: .value("Strike", point.strikePrice),
                            y: .value("IV", point.iv * 100)
                        )
                        .foregroundStyle(Theme.profit)
                        .interpolationMethod(.catmullRom)
                        .lineStyle(StrokeStyle(lineWidth: 2))

                        if point.isATM {
                            PointMark(
                                x: .value("Strike", point.strikePrice),
                                y: .value("IV", point.iv * 100)
                            )
                            .foregroundStyle(Theme.accentBlue)
                            .symbolSize(80)
                        }
                    }
                }

                // Spot price line
                RuleMark(x: .value("Spot", spotPrice))
                    .foregroundStyle(Theme.accentBlue.opacity(0.5))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    .annotation(position: .top) {
                        Text("SPOT")
                            .font(.caption2)
                            .foregroundColor(Theme.accentBlue)
                    }
            }
            .chartYAxis {
                AxisMarks(position: .leading) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(Theme.textMuted.opacity(0.2))
                    AxisValueLabel {
                        if let val = value.as(Double.self) {
                            Text(String(format: "%.0f%%", val))
                                .font(.caption2)
                                .foregroundColor(Theme.textSecondary)
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(Theme.textMuted.opacity(0.2))
                    AxisValueLabel {
                        if let val = value.as(Double.self) {
                            Text(String(format: "%.0f", val))
                                .font(.caption2)
                                .foregroundColor(Theme.textSecondary)
                        }
                    }
                }
            }
            .frame(height: 200)
            .padding(.horizontal)

            // IV Analysis cards
            IVAnalysisSection(
                callIVPoints: callIVPoints,
                putIVPoints: putIVPoints,
                spotPrice: spotPrice
            )
        }
        .padding(.vertical)
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Toggle Button

private struct ToggleButton: View {
    let label: String
    @Binding var isOn: Bool
    let color: Color

    var body: some View {
        Button(action: { isOn.toggle() }) {
            Text(label)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(isOn ? .white : color)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isOn ? color : color.opacity(0.2))
                .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - IV Analysis Section

private struct IVAnalysisSection: View {
    let callIVPoints: [IVSurfacePoint]
    let putIVPoints: [IVSurfacePoint]
    let spotPrice: Double

    private var atmCallIV: Double {
        callIVPoints.first { $0.isATM }?.iv ?? 0
    }

    private var atmPutIV: Double {
        putIVPoints.first { $0.isATM }?.iv ?? 0
    }

    private var ivSkew: Double {
        atmPutIV - atmCallIV
    }

    private var avgCallIV: Double {
        guard !callIVPoints.isEmpty else { return 0 }
        return callIVPoints.reduce(0) { $0 + $1.iv } / Double(callIVPoints.count)
    }

    private var avgPutIV: Double {
        guard !putIVPoints.isEmpty else { return 0 }
        return putIVPoints.reduce(0) { $0 + $1.iv } / Double(putIVPoints.count)
    }

    var body: some View {
        VStack(spacing: 12) {
            // ATM IV comparison
            HStack(spacing: 16) {
                IVMetricCard(
                    title: "ATM Call IV",
                    value: atmCallIV * 100,
                    color: Theme.loss
                )

                IVMetricCard(
                    title: "ATM Put IV",
                    value: atmPutIV * 100,
                    color: Theme.profit
                )
            }
            .padding(.horizontal)

            // IV Skew analysis
            IVSkewCard(skew: ivSkew)
                .padding(.horizontal)

            // IV Smile interpretation
            IVSmileInterpretation(
                callIVPoints: callIVPoints,
                putIVPoints: putIVPoints,
                spotPrice: spotPrice
            )
            .padding(.horizontal)
        }
    }
}

// MARK: - IV Metric Card

private struct IVMetricCard: View {
    let title: String
    let value: Double
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundColor(Theme.textMuted)

            Text(String(format: "%.1f%%", value))
                .font(.system(.title3, design: .monospaced))
                .fontWeight(.bold)
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(color.opacity(0.1))
        .cornerRadius(12)
    }
}

// MARK: - IV Skew Card

private struct IVSkewCard: View {
    let skew: Double

    private var skewDescription: String {
        if skew > 0.02 {
            return "Puts are more expensive than calls. Market shows fear/downside protection demand."
        } else if skew < -0.02 {
            return "Calls are more expensive than puts. Market shows upside speculation."
        }
        return "IV is balanced between puts and calls. Neutral sentiment."
    }

    private var skewColor: Color {
        if skew > 0.02 {
            return Theme.loss
        } else if skew < -0.02 {
            return Theme.profit
        }
        return Theme.accentBlue
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("IV Skew")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                Text(String(format: "%+.2f%%", skew * 100))
                    .font(.system(.subheadline, design: .monospaced))
                    .fontWeight(.bold)
                    .foregroundColor(skewColor)
            }

            Text(skewDescription)
                .font(.caption)
                .foregroundColor(Theme.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding()
        .background(Theme.surface)
        .cornerRadius(12)
    }
}

// MARK: - IV Smile Interpretation

private struct IVSmileInterpretation: View {
    let callIVPoints: [IVSurfacePoint]
    let putIVPoints: [IVSurfacePoint]
    let spotPrice: Double

    private var smileType: String {
        // Analyze IV smile pattern
        let otmCalls = callIVPoints.filter { $0.strikePrice > spotPrice }
        let otmPuts = putIVPoints.filter { $0.strikePrice < spotPrice }
        let atmCallIV: Double = callIVPoints.first { $0.isATM }?.iv ?? 0
        let atmPutIV: Double = putIVPoints.first { $0.isATM }?.iv ?? 0
        let atmIV: Double = (atmCallIV + atmPutIV) / 2.0

        let avgOTMCallIV: Double = otmCalls.isEmpty ? 0 : otmCalls.reduce(0.0) { $0 + $1.iv } / Double(otmCalls.count)
        let avgOTMPutIV: Double = otmPuts.isEmpty ? 0 : otmPuts.reduce(0.0) { $0 + $1.iv } / Double(otmPuts.count)

        if avgOTMCallIV > atmIV && avgOTMPutIV > atmIV {
            return "Smile"
        } else if avgOTMPutIV > avgOTMCallIV {
            return "Put Skew"
        } else if avgOTMCallIV > avgOTMPutIV {
            return "Call Skew"
        }
        return "Flat"
    }

    private var interpretation: String {
        switch smileType {
        case "Smile":
            return "Both OTM calls and puts are expensive. Market expects large move but direction uncertain."
        case "Put Skew":
            return "OTM puts are expensive. Market is hedging downside. Common in equity indices."
        case "Call Skew":
            return "OTM calls are expensive. Market expects upside breakout."
        default:
            return "IV is relatively flat across strikes. Market expects range-bound movement."
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "waveform.path.ecg")
                    .foregroundColor(Theme.accentPurple)

                Text("IV Pattern: \(smileType)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.textPrimary)
            }

            Text(interpretation)
                .font(.caption)
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding()
        .background(Theme.surface)
        .cornerRadius(12)
    }
}

// MARK: - Preview

#Preview {
    let strikes = stride(from: 23500.0, through: 24500.0, by: 50)
    let callPoints = strikes.map { strike in
        IVSurfacePoint(
            strikePrice: strike,
            optionType: .call,
            iv: 0.15 + abs(strike - 24000) * 0.00005,
            moneyness: strike / 24000,
            isATM: abs(strike - 24000) < 25
        )
    }
    let putPoints = strikes.map { strike in
        IVSurfacePoint(
            strikePrice: strike,
            optionType: .put,
            iv: 0.16 + abs(strike - 24000) * 0.00006,
            moneyness: strike / 24000,
            isATM: abs(strike - 24000) < 25
        )
    }

    return IVSurfaceView(
        callIVPoints: callPoints,
        putIVPoints: putPoints,
        spotPrice: 24000
    )
    .padding()
    .background(Theme.background)
    
}
