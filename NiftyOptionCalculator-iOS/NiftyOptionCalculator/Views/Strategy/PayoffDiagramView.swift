import SwiftUI
import Charts

// MARK: - Payoff Diagram View

struct PayoffDiagramView: View {
    let payoffData: PayoffData
    let spotPrice: Double
    let breakevens: [Double]

    @Binding var touchedPrice: Double?
    @Binding var touchedPayoff: Double?

    @State private var selectedPoint: PayoffPoint?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack {
                Text("Payoff at Expiry")
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                if let price = touchedPrice, let payoff = touchedPayoff {
                    TouchedValueBadge(price: price, payoff: payoff)
                }
            }

            // Chart
            Chart {
                // Profit area (green) - use full dataset, clamp to zero
                ForEach(payoffData.points) { point in
                    AreaMark(
                        x: .value("Price", point.price),
                        y: .value("P&L", max(0, point.payoff))
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.profit.opacity(0.3), Theme.profit.opacity(0.1)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .interpolationMethod(.catmullRom)
                }

                // Loss area (red) - use full dataset, clamp to zero
                ForEach(payoffData.points) { point in
                    AreaMark(
                        x: .value("Price", point.price),
                        y: .value("P&L", min(0, point.payoff))
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.loss.opacity(0.1), Theme.loss.opacity(0.3)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .interpolationMethod(.catmullRom)
                }

                // Payoff line
                ForEach(payoffData.points) { point in
                    LineMark(
                        x: .value("Price", point.price),
                        y: .value("P&L", point.payoff)
                    )
                    .foregroundStyle(
                        point.payoff >= 0 ? Theme.profit : Theme.loss
                    )
                    .interpolationMethod(.catmullRom)
                    .lineStyle(StrokeStyle(lineWidth: 2.5))
                }

                // Zero line
                RuleMark(y: .value("Zero", 0))
                    .foregroundStyle(Theme.textMuted.opacity(0.5))
                    .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 5]))

                // Spot price line
                RuleMark(x: .value("Spot", spotPrice))
                    .foregroundStyle(Theme.accentBlue.opacity(0.7))
                    .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                    .annotation(position: .top, alignment: .center) {
                        Text("SPOT")
                            .font(.caption2)
                            .fontWeight(.semibold)
                            .foregroundColor(Theme.accentBlue)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(Theme.accentBlue.opacity(0.2))
                            .cornerRadius(4)
                    }

                // Breakeven points
                ForEach(Array(breakevens.enumerated()), id: \.offset) { _, be in
                    PointMark(
                        x: .value("BE", be),
                        y: .value("P&L", 0)
                    )
                    .foregroundStyle(Theme.accentOrange)
                    .symbolSize(80)
                    .annotation(position: .bottom) {
                        Text(String(format: "%.0f", be))
                            .font(.caption2)
                            .foregroundColor(Theme.accentOrange)
                    }
                }

                // Touch indicator
                if let price = touchedPrice, let payoff = touchedPayoff {
                    PointMark(
                        x: .value("Touch", price),
                        y: .value("P&L", payoff)
                    )
                    .foregroundStyle(Theme.textPrimary)
                    .symbolSize(120)

                    RuleMark(x: .value("Touch", price))
                        .foregroundStyle(Theme.textMuted.opacity(0.3))
                        .lineStyle(StrokeStyle(lineWidth: 1))
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                        .foregroundStyle(Theme.textMuted.opacity(0.2))
                    AxisValueLabel {
                        if let val = value.as(Double.self) {
                            Text(formatCurrency(val))
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
            .chartOverlay { proxy in
                GeometryReader { geometry in
                    Rectangle()
                        .fill(Color.clear)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in
                                    let origin = geometry[proxy.plotAreaFrame].origin
                                    let location = CGPoint(
                                        x: value.location.x - origin.x,
                                        y: value.location.y - origin.y
                                    )
                                    if let price: Double = proxy.value(atX: location.x) {
                                        handleTouch(at: price)
                                    }
                                }
                                .onEnded { _ in
                                    clearTouch()
                                }
                        )
                }
            }
            .frame(height: 220)
            .padding(.vertical, 8)

            // Legend
            HStack(spacing: 16) {
                LegendItem(color: Theme.profit, label: "Profit Zone")
                LegendItem(color: Theme.loss, label: "Loss Zone")
                LegendItem(color: Theme.accentOrange, label: "Breakeven")
                LegendItem(color: Theme.accentBlue, label: "Spot Price")
            }
            .font(.caption2)
        }
        .padding(16)
        .background(Theme.card)
        .cornerRadius(16)
    }

    // MARK: - Private Methods

    private func handleTouch(at price: Double) {
        touchedPrice = price
        // Find nearest data point to calculate payoff at touched price
        if let nearest = payoffData.points.min(by: { abs($0.price - price) < abs($1.price - price) }) {
            touchedPayoff = nearest.payoff
        }
    }

    private func clearTouch() {
        touchedPrice = nil
        touchedPayoff = nil
    }

    private func formatCurrency(_ value: Double) -> String {
        if abs(value) >= 100000 {
            return String(format: "%.0fL", value / 100000)
        } else if abs(value) >= 1000 {
            return String(format: "%.0fK", value / 1000)
        }
        return String(format: "%.0f", value)
    }
}

// MARK: - Touched Value Badge

private struct TouchedValueBadge: View {
    let price: Double
    let payoff: Double

    var body: some View {
        HStack(spacing: 8) {
            VStack(alignment: .trailing, spacing: 2) {
                Text(String(format: "%.0f", price))
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(Theme.textPrimary)

                Text(formatPayoff(payoff))
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(payoff >= 0 ? Theme.profit : Theme.loss)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Theme.surface)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(payoff >= 0 ? Theme.profit.opacity(0.5) : Theme.loss.opacity(0.5), lineWidth: 1)
        )
    }

    private func formatPayoff(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        if abs(value) >= 100000 {
            return prefix + String(format: "₹%.1fL", value / 100000)
        } else if abs(value) >= 1000 {
            return prefix + String(format: "₹%.1fK", value / 1000)
        }
        return prefix + String(format: "₹%.0f", value)
    }
}

// MARK: - Legend Item

private struct LegendItem: View {
    let color: Color
    let label: String

    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(label)
                .foregroundColor(Theme.textSecondary)
        }
    }
}

// MARK: - Preview

#Preview {
    let samplePoints = stride(from: 23000.0, through: 25000.0, by: 20).map { price in
        PayoffPoint(price: price, payoff: (price - 24000) * 25 - 5000)
    }

    return PayoffDiagramView(
        payoffData: PayoffData(
            points: samplePoints,
            maxProfit: 20000,
            maxLoss: -5000,
            breakevens: [24200],
            spotPrice: 24000,
            payoffAtSpot: -5000
        ),
        spotPrice: 24000,
        breakevens: [24200],
        touchedPrice: .constant(nil),
        touchedPayoff: .constant(nil)
    )
    
    .padding()
    .background(Theme.background)
}
