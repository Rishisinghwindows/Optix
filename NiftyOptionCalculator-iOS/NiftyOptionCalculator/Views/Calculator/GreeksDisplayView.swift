import SwiftUI

// MARK: - Greeks Display View

struct GreeksDisplayView: View {
    let greeks: GreeksResult
    @State private var showDetails = false
    @State private var selectedGreek: String? = nil

    var body: some View {
        VStack(spacing: 16) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "function")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.blueGradient)

                    Text(L.calculatorTheGreeks)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                Button(action: {
                    withAnimation(.spring(response: 0.3)) {
                        showDetails.toggle()
                    }
                }) {
                    HStack(spacing: 4) {
                        Text(showDetails ? L.calculatorHide : L.calculatorShow)
                            .font(.system(size: 12, weight: .medium))
                        Image(systemName: showDetails ? "chevron.up" : "chevron.down")
                            .font(.system(size: 10, weight: .semibold))
                    }
                    .foregroundColor(Theme.accentBlue)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background {
                        Capsule()
                            .fill(Theme.accentBlue.opacity(0.15))
                    }
                }
                .buttonStyle(.plain)
            }

            // Greeks Cards Grid
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 10),
                GridItem(.flexible(), spacing: 10)
            ], spacing: 10) {
                ModernGreekCard(
                    symbol: "Δ",
                    name: "Delta",
                    value: greeks.displayDelta,
                    gradient: greeks.delta >= 0 ? Theme.greenGradient : Theme.redGradient,
                    backgroundColor: greeks.delta >= 0 ? Theme.accentGreen : Theme.accentRed,
                    description: L.greeksDeltaDesc,
                    isSelected: selectedGreek == "Delta"
                ) {
                    withAnimation(.spring(response: 0.2)) {
                        selectedGreek = selectedGreek == "Delta" ? nil : "Delta"
                    }
                }

                ModernGreekCard(
                    symbol: "Γ",
                    name: "Gamma",
                    value: greeks.displayGamma,
                    gradient: Theme.blueGradient,
                    backgroundColor: Theme.accentBlue,
                    description: L.greeksGammaDesc,
                    isSelected: selectedGreek == "Gamma"
                ) {
                    withAnimation(.spring(response: 0.2)) {
                        selectedGreek = selectedGreek == "Gamma" ? nil : "Gamma"
                    }
                }

                ModernGreekCard(
                    symbol: "Θ",
                    name: "Theta",
                    value: greeks.displayTheta,
                    gradient: Theme.redGradient,
                    backgroundColor: Theme.accentRed,
                    description: L.greeksThetaDesc,
                    isSelected: selectedGreek == "Theta"
                ) {
                    withAnimation(.spring(response: 0.2)) {
                        selectedGreek = selectedGreek == "Theta" ? nil : "Theta"
                    }
                }

                ModernGreekCard(
                    symbol: "ν",
                    name: "Vega",
                    value: greeks.displayVega,
                    gradient: LinearGradient(colors: [Theme.accentPurple, Theme.accentPurple.opacity(0.7)], startPoint: .leading, endPoint: .trailing),
                    backgroundColor: Theme.accentPurple,
                    description: L.greeksVegaDesc,
                    isSelected: selectedGreek == "Vega"
                ) {
                    withAnimation(.spring(response: 0.2)) {
                        selectedGreek = selectedGreek == "Vega" ? nil : "Vega"
                    }
                }
            }

            // Selected Greek Detail
            if let selected = selectedGreek {
                GreekDetailCard(greekName: selected, greeks: greeks)
                    .transition(.asymmetric(
                        insertion: .opacity.combined(with: .scale(scale: 0.95)),
                        removal: .opacity
                    ))
            }

            // Detailed Interpretations
            if showDetails {
                VStack(spacing: 12) {
                    // Divider
                    Rectangle()
                        .fill(Theme.borderGradient)
                        .frame(height: 1)

                    // Interpretations
                    VStack(alignment: .leading, spacing: 10) {
                        ModernInterpretationRow(
                            greek: "Delta",
                            interpretation: greeks.deltaInterpretation,
                            icon: "arrow.up.right",
                            color: greeks.delta >= 0 ? Theme.accentGreen : Theme.accentRed
                        )

                        ModernInterpretationRow(
                            greek: "Theta",
                            interpretation: greeks.thetaInterpretation,
                            icon: "clock.arrow.circlepath",
                            color: Theme.accentRed
                        )

                        ModernInterpretationRow(
                            greek: "Vega",
                            interpretation: greeks.vegaInterpretation,
                            icon: "waveform.path.ecg",
                            color: Theme.accentPurple
                        )
                    }

                    // Quick Reference Card
                    QuickReferenceCard()
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .solidCard(cornerRadius: 16, padding: 16)
    }
}

// MARK: - Modern Greek Card

struct ModernGreekCard: View {
    let symbol: String
    let name: String
    let value: String
    let gradient: LinearGradient
    let backgroundColor: Color
    let description: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                // Symbol with glow
                ZStack {
                    Text(symbol)
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundStyle(gradient)
                        .blur(radius: 8)
                        .opacity(0.5)

                    Text(symbol)
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundStyle(gradient)
                }

                // Value
                Text(value)
                    .font(.system(size: 16, weight: .bold, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)

                // Name & Description
                VStack(spacing: 2) {
                    Text(name)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textSecondary)

                    Text(description)
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(Theme.textMuted)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .padding(.horizontal, 8)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.card)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(
                                isSelected ? backgroundColor.opacity(0.5) : Color.white.opacity(0.06),
                                lineWidth: isSelected ? 1.5 : 1
                            )
                    }
                    .shadow(color: isSelected ? backgroundColor.opacity(0.2) : .clear, radius: 8, x: 0, y: 4)
            }
        }
        .buttonStyle(.plain)
        .scaleEffect(isSelected ? 1.02 : 1.0)
    }
}

// MARK: - Greek Detail Card

struct GreekDetailCard: View {
    let greekName: String
    let greeks: GreeksResult

    var detailInfo: (value: Double, explanation: String, impact: String) {
        switch greekName {
        case "Delta":
            return (greeks.delta,
                    "For every 1 point move in Nifty, this option moves ₹\(String(format: "%.2f", abs(greeks.delta)))",
                    greeks.delta > 0.7 ? L.greeksHighDelta :
                    greeks.delta < 0.3 ? L.greeksLowDelta : L.greeksModerateDelta)
        case "Gamma":
            return (greeks.gamma,
                    "Delta will change by \(String(format: "%.4f", greeks.gamma)) for every 1 point move",
                    greeks.gamma > 0.001 ? L.greeksHighGamma : L.greeksLowGamma)
        case "Theta":
            return (greeks.theta,
                    "This option loses ₹\(String(format: "%.2f", abs(greeks.theta))) per day to time decay",
                    abs(greeks.theta) > 5 ? L.greeksHighTheta : L.greeksLowTheta)
        case "Vega":
            return (greeks.vega,
                    "For 1% increase in IV, option price changes by ₹\(String(format: "%.2f", greeks.vega))",
                    greeks.vega > 10 ? L.greeksHighVega : L.greeksLowVega)
        default:
            return (0, "", "")
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "info.circle.fill")
                    .foregroundColor(Theme.accentBlue)
                Text("\(greekName) \(L.greeksInsight)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
            }

            Text(detailInfo.explanation)
                .font(.system(size: 12))
                .foregroundColor(Theme.textSecondary)

            HStack(spacing: 6) {
                Image(systemName: "lightbulb.fill")
                    .font(.system(size: 10))
                    .foregroundColor(Theme.accentOrange)
                Text(detailInfo.impact)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Theme.accentOrange)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Theme.accentOrange.opacity(0.1))
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(Theme.surfaceElevated)
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Theme.accentBlue.opacity(0.2), lineWidth: 1)
                }
        }
    }
}

// MARK: - Modern Interpretation Row

struct ModernInterpretationRow: View {
    let greek: String
    let interpretation: String
    let icon: String
    let color: Color

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(color)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text(greek)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(color)

                Text(interpretation)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - Quick Reference Card

struct QuickReferenceCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "book.fill")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(Theme.accentCyan)
                Text(L.calculatorQuickReference)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Theme.accentCyan)
            }

            VStack(alignment: .leading, spacing: 4) {
                ReferenceItem(symbol: "Δ", text: L.greeksQuickRefDeltaCall, color: Theme.accentGreen)
                ReferenceItem(symbol: "Γ", text: L.greeksQuickRefGamma, color: Theme.accentBlue)
                ReferenceItem(symbol: "Θ", text: L.greeksQuickRefTheta, color: Theme.accentRed)
                ReferenceItem(symbol: "ν", text: L.greeksQuickRefVega, color: Theme.accentPurple)
            }

            // Near-expiry warning
            HStack(alignment: .top, spacing: 6) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(Theme.accentOrange)

                Text(L.greeksNearExpiryText)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.accentOrange)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(8)
            .background {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Theme.accentOrange.opacity(0.1))
                    .overlay {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.accentOrange.opacity(0.2), lineWidth: 1)
                    }
            }
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.card.opacity(0.5))
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.white.opacity(0.04), lineWidth: 1)
                }
        }
    }
}

struct ReferenceItem: View {
    let symbol: String
    let text: String
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            Text(symbol)
                .font(.system(size: 10, weight: .bold, design: .rounded))
                .foregroundColor(color)
                .frame(width: 14)

            Text(text)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)
        }
    }
}

// MARK: - Greeks Summary Bar (Compact)

struct GreeksSummaryBar: View {
    let greeks: GreeksResult

    var body: some View {
        HStack(spacing: 16) {
            GreekPill(symbol: "Δ", value: greeks.displayDelta, color: Theme.accentGreen)
            GreekPill(symbol: "Γ", value: greeks.displayGamma, color: Theme.accentBlue)
            GreekPill(symbol: "Θ", value: greeks.displayTheta, color: Theme.accentRed)
            GreekPill(symbol: "ν", value: greeks.displayVega, color: Theme.accentPurple)
        }
    }
}

struct GreekPill: View {
    let symbol: String
    let value: String
    let color: Color

    var body: some View {
        HStack(spacing: 4) {
            Text(symbol)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundColor(color)
            Text(value)
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .foregroundColor(Theme.textSecondary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background {
            Capsule()
                .fill(color.opacity(0.1))
        }
    }
}

// MARK: - Position Impact View

struct PositionImpactView: View {
    let greeks: GreeksResult
    let quantity: Int
    let lotSize: Int = 25

    private var totalQty: Double {
        Double(quantity * lotSize)
    }

    var body: some View {
        VStack(spacing: 14) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.greenGradient)

                    Text("Position Impact")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                BadgeView(text: "\(quantity) LOT", color: Theme.accentBlue)
            }

            // Impact Cards
            VStack(spacing: 8) {
                ImpactCard(
                    icon: "arrow.up.forward",
                    title: "100 pt Nifty move",
                    value: String(format: "₹%.0f", greeks.delta * 100 * totalQty),
                    isPositive: greeks.delta >= 0,
                    description: "Based on current Delta"
                )

                ImpactCard(
                    icon: "clock.fill",
                    title: "Daily Theta decay",
                    value: String(format: "₹%.0f", greeks.theta * totalQty),
                    isPositive: false,
                    description: "Time value erosion"
                )

                ImpactCard(
                    icon: "waveform.path",
                    title: "1% IV increase",
                    value: String(format: "₹%.0f", greeks.vega * totalQty),
                    isPositive: true,
                    description: "Volatility sensitivity"
                )
            }
        }
        .solidCard(cornerRadius: 16, padding: 16)
    }
}

struct ImpactCard: View {
    let icon: String
    let title: String
    let value: String
    let isPositive: Bool
    let description: String

    var body: some View {
        HStack(spacing: 12) {
            // Icon
            Image(systemName: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(isPositive ? Theme.accentGreen : Theme.accentRed)
                .frame(width: 32, height: 32)
                .background {
                    Circle()
                        .fill((isPositive ? Theme.accentGreen : Theme.accentRed).opacity(0.15))
                }

            // Title & Description
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Theme.textPrimary)

                Text(description)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
            }

            Spacer()

            // Value
            Text(value)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundColor(isPositive ? Theme.accentGreen : Theme.accentRed)
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.card)
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.white.opacity(0.04), lineWidth: 1)
                }
        }
    }
}

// MARK: - Preview

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            GreeksDisplayView(greeks: GreeksResult(
                delta: 0.52,
                gamma: 0.00035,
                theta: -4.5,
                vega: 8.2,
                rho: 1.5
            ))

            PositionImpactView(
                greeks: GreeksResult(
                    delta: 0.52,
                    gamma: 0.00035,
                    theta: -4.5,
                    vega: 8.2,
                    rho: 1.5
                ),
                quantity: 2
            )
        }
        .padding()
    }
    .background(Theme.background)
    
}
