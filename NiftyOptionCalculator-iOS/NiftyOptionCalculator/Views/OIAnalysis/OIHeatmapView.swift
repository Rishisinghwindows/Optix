import SwiftUI

// MARK: - OI Heatmap View

struct OIHeatmapView: View {
    let strikeData: [StrikeOIData]
    let spotPrice: Double
    let onStrikeSelect: (Double) -> Void

    @State private var showCallOI = true
    @State private var showPutOI = true

    private var maxCallOI: Int {
        strikeData.map { $0.callOI }.max() ?? 1
    }

    private var maxPutOI: Int {
        strikeData.map { $0.putOI }.max() ?? 1
    }

    /// Find the ATM strike price (closest to spot price)
    private var atmStrike: Double? {
        strikeData.min(by: { abs($0.strikePrice - spotPrice) < abs($1.strikePrice - spotPrice) })?.strikePrice
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("OI Heatmap")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                // Toggle buttons
                HStack(spacing: 8) {
                    ToggleChip(label: "CE", isOn: $showCallOI, color: Theme.loss)
                    ToggleChip(label: "PE", isOn: $showPutOI, color: Theme.profit)
                }
            }
            .padding()

            // Legend
            HeatmapLegend()
                .padding(.horizontal)
                .padding(.bottom, 8)

            // Heatmap grid with ScrollViewReader for auto-scroll
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 1) {
                        // Header row
                        HStack(spacing: 1) {
                            if showCallOI {
                                Text("Call OI")
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .foregroundColor(Theme.loss)
                                    .frame(maxWidth: .infinity)
                            }

                            Text("Strike")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(Theme.accentBlue)
                                .frame(width: 60)

                            if showPutOI {
                                Text("Put OI")
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .foregroundColor(Theme.profit)
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .padding(.vertical, 8)
                        .background(Theme.surface)

                        // Data rows
                        ForEach(strikeData) { data in
                            HeatmapRow(
                                data: data,
                                spotPrice: spotPrice,
                                maxCallOI: maxCallOI,
                                maxPutOI: maxPutOI,
                                showCallOI: showCallOI,
                                showPutOI: showPutOI,
                                onTap: { onStrikeSelect(data.strikePrice) }
                            )
                            .id(data.strikePrice)
                        }
                    }
                }
                .onAppear {
                    // Auto-scroll to ATM strike on launch
                    if let atm = atmStrike {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            withAnimation {
                                proxy.scrollTo(atm, anchor: .center)
                            }
                        }
                    }
                }
            }
        }
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Heatmap Row

private struct HeatmapRow: View {
    let data: StrikeOIData
    let spotPrice: Double
    let maxCallOI: Int
    let maxPutOI: Int
    let showCallOI: Bool
    let showPutOI: Bool
    let onTap: () -> Void

    private var isATM: Bool {
        abs(data.strikePrice - spotPrice) <= 25
    }

    private var callIntensity: Double {
        maxCallOI > 0 ? Double(data.callOI) / Double(maxCallOI) : 0
    }

    private var putIntensity: Double {
        maxPutOI > 0 ? Double(data.putOI) / Double(maxPutOI) : 0
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 1) {
                // Call OI cell
                if showCallOI {
                    HeatmapCell(
                        value: data.callOI,
                        change: data.callOIChange,
                        intensity: callIntensity,
                        baseColor: Theme.loss
                    )
                }

                // Strike
                VStack(spacing: 2) {
                    Text(data.displayStrike)
                        .font(.system(.caption, design: .monospaced))
                        .fontWeight(isATM ? .black : .medium)
                        .foregroundColor(isATM ? Theme.accentGreen : Theme.textPrimary)

                    if isATM {
                        Text("ATM")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(Theme.accentGreen)
                    }
                }
                .frame(width: 60)
                .padding(.vertical, 8)
                .background(isATM ? Theme.accentGreen.opacity(0.1) : Color.clear)

                // Put OI cell
                if showPutOI {
                    HeatmapCell(
                        value: data.putOI,
                        change: data.putOIChange,
                        intensity: putIntensity,
                        baseColor: Theme.profit
                    )
                }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Heatmap Cell

private struct HeatmapCell: View {
    let value: Int
    let change: Int
    let intensity: Double
    let baseColor: Color

    var body: some View {
        ZStack {
            // Background intensity
            Rectangle()
                .fill(baseColor.opacity(0.1 + (intensity * 0.6)))

            VStack(spacing: 2) {
                // OI value
                Text(formatOI(value))
                    .font(.system(.caption2, design: .monospaced))
                    .fontWeight(.medium)
                    .foregroundColor(intensity > 0.7 ? .white : Theme.textPrimary)

                // Change indicator
                if change != 0 {
                    HStack(spacing: 2) {
                        Image(systemName: change > 0 ? "arrow.up" : "arrow.down")
                            .font(.system(size: 8))
                        Text(formatOI(abs(change)))
                            .font(.system(size: 9, design: .monospaced))
                    }
                    .foregroundColor(change > 0 ? Theme.profit : Theme.loss)
                }
            }
            .padding(.vertical, 6)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 50)
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

// MARK: - Toggle Chip

private struct ToggleChip: View {
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

// MARK: - Heatmap Legend

private struct HeatmapLegend: View {
    var body: some View {
        HStack(spacing: 16) {
            HStack(spacing: 4) {
                Text("Low")
                    .font(.caption2)
                    .foregroundColor(Theme.textMuted)

                HStack(spacing: 2) {
                    ForEach(0..<5) { i in
                        Rectangle()
                            .fill(Theme.accentBlue.opacity(0.1 + (Double(i) * 0.2)))
                            .frame(width: 16, height: 12)
                    }
                }
                .cornerRadius(4)

                Text("High")
                    .font(.caption2)
                    .foregroundColor(Theme.textMuted)
            }

            Spacer()

            HStack(spacing: 8) {
                HStack(spacing: 4) {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.profit)
                    Text("Building")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                }

                HStack(spacing: 4) {
                    Image(systemName: "arrow.down")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.loss)
                    Text("Unwinding")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    let sampleData = stride(from: 23500.0, through: 24500.0, by: 50).map { strike in
        StrikeOIData(
            strikePrice: strike,
            callOI: Int.random(in: 10000...500000),
            putOI: Int.random(in: 10000...500000),
            callOIChange: Int.random(in: -50000...50000),
            putOIChange: Int.random(in: -50000...50000),
            callIV: 0.15 + Double.random(in: -0.03...0.03),
            putIV: 0.15 + Double.random(in: -0.03...0.03),
            callLTP: 100,
            putLTP: 100
        )
    }

    return OIHeatmapView(
        strikeData: sampleData,
        spotPrice: 24000,
        onStrikeSelect: { _ in }
    )
    .padding()
    .background(Theme.background)
    
}
