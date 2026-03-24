import SwiftUI

// MARK: - Strike Analysis Sheet

struct StrikeAnalysisSheet: View {
    let option: OptionData
    let context: MLPredictionContext
    @Environment(\.dismiss) private var dismiss
    @State private var prediction: OptionPrediction?
    @State private var isAnalyzing = true

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient
                    .ignoresSafeArea()

                if isAnalyzing {
                    AnalyzingView()
                } else {
                    ScrollView {
                        VStack(spacing: 20) {
                            // Option Header
                            OptionHeaderCard(option: option)

                            // ML Prediction
                            if let prediction = prediction {
                                MLPredictionCard(prediction: prediction)
                            }

                            // Quick Stats
                            QuickAnalysisStats(option: option)

                            // Trade Setup
                            if let prediction = prediction {
                                TradeSetupCard(option: option, prediction: prediction, context: context)
                            }

                            // Greeks Analysis
                            GreeksAnalysisCard(option: option)

                            // SEBI Disclaimer
                            DisclaimerView()
                        }
                        .padding(16)
                        .padding(.bottom, 40)
                    }
                }
            }
            .navigationTitle(L.aiInsightsStrikeAnalysis)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
        .onAppear {
            analyzeOption()
        }
    }

    private func analyzeOption() {
        isAnalyzing = true

        Task.detached {
            // Brief delay for UI transition
            try? await Task.sleep(nanoseconds: 500_000_000)
            let features = OptionMLFeatures.from(option: option, context: context)
            let result = MLOptionPredictor.shared.predictOption(features)
            await MainActor.run {
                prediction = result
                withAnimation {
                    isAnalyzing = false
                }
            }
        }
    }
}

// MARK: - Analyzing View

struct AnalyzingView: View {
    @State private var rotation: Double = 0

    var body: some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.1), lineWidth: 3)
                    .frame(width: 60, height: 60)

                Circle()
                    .trim(from: 0, to: 0.3)
                    .stroke(
                        LinearGradient(
                            colors: [Color(hex: "BF5AF2"), Color(hex: "FF375F")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        style: StrokeStyle(lineWidth: 3, lineCap: .round)
                    )
                    .frame(width: 60, height: 60)
                    .rotationEffect(.degrees(rotation))

                Image(systemName: "brain.head.profile")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Color(hex: "BF5AF2"))
            }

            Text("Analyzing Strike...")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
        }
        .onAppear {
            withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: false)) {
                rotation = 360
            }
        }
    }
}

// MARK: - Option Header Card

struct OptionHeaderCard: View {
    let option: OptionData

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(String(format: "%.0f", option.strikePrice))
                            .font(.system(size: 32, weight: .bold, design: .monospaced))
                            .foregroundColor(Theme.textPrimary)

                        Text(option.optionType == .call ? "CE" : "PE")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(option.optionType == .call ? Theme.profit : Theme.loss)
                            }
                    }

                    Text("\(option.daysToExpiry) days to expiry")
                        .font(.system(size: 13))
                        .foregroundColor(Theme.textSecondary)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("₹\(String(format: "%.2f", option.lastTradedPrice))")
                        .font(.system(size: 24, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)

                    Text(option.moneyness)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(option.isITM ? Theme.profit : Theme.textMuted)
                }
            }
        }
        .solidCard()
    }
}

// MARK: - Quick Analysis Stats

struct QuickAnalysisStats: View {
    let option: OptionData

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Key Metrics")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Spacer()
            }

            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 12) {
                MetricBox(
                    title: "OI",
                    value: formatNumber(option.openInterest),
                    subtitle: option.changeInOI >= 0 ? "+\(formatNumber(option.changeInOI))" : formatNumber(option.changeInOI),
                    color: option.changeInOI >= 0 ? Theme.profit : Theme.loss
                )

                MetricBox(
                    title: "Volume",
                    value: formatNumber(option.volume),
                    subtitle: "Traded",
                    color: Theme.primaryBlue
                )

                MetricBox(
                    title: "IV",
                    value: String(format: "%.1f%%", option.impliedVolatility * 100),
                    subtitle: "Implied Vol",
                    color: Theme.accentOrange
                )
            }
        }
        .solidCard()
    }

    private func formatNumber(_ number: Int) -> String {
        if abs(number) >= 10000000 {
            return String(format: "%.1fCr", Double(number) / 10000000)
        } else if abs(number) >= 100000 {
            return String(format: "%.1fL", Double(number) / 100000)
        } else if abs(number) >= 1000 {
            return String(format: "%.1fK", Double(number) / 1000)
        } else {
            return "\(number)"
        }
    }
}

struct MetricBox: View {
    let title: String
    let value: String
    let subtitle: String
    let color: Color

    var body: some View {
        VStack(spacing: 6) {
            Text(title)
                .font(.system(size: 11))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 16, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)

            Text(subtitle)
                .font(.system(size: 10))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.surfaceElevated)
        }
    }
}

// MARK: - Trade Setup Card

struct TradeSetupCard: View {
    let option: OptionData
    let prediction: OptionPrediction
    let context: MLPredictionContext

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "target")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.accentOrange)

                    Text("Suggested Trade Setup")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                if prediction.isActionable {
                    Text("Actionable")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(Theme.profit)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background {
                            Capsule()
                                .fill(Theme.profit.opacity(0.15))
                        }
                }
            }

            HStack(spacing: 0) {
                TradeSetupItem(
                    title: L.aiInsightsEntry,
                    value: "₹\(String(format: "%.2f", option.lastTradedPrice))",
                    color: Theme.textPrimary
                )

                Divider()
                    .frame(height: 40)
                    .background(Color.white.opacity(0.1))

                TradeSetupItem(
                    title: L.aiInsightsTarget,
                    value: "₹\(String(format: "%.2f", prediction.targetPrice))",
                    color: Theme.profit
                )

                Divider()
                    .frame(height: 40)
                    .background(Color.white.opacity(0.1))

                TradeSetupItem(
                    title: L.aiInsightsStopLoss,
                    value: "₹\(String(format: "%.2f", prediction.stopLoss))",
                    color: Theme.loss
                )
            }

            // Risk Reward
            HStack {
                Text(L.aiInsightsRiskReward)
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                let rr = calculateRiskReward()
                Text("1:\(String(format: "%.1f", rr))")
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundColor(rr >= 1.5 ? Theme.profit : Theme.accentOrange)

                if rr >= 2.0 {
                    Image(systemName: "star.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.accentOrange)
                }
            }
            .padding(.top, 4)
        }
        .solidCard()
    }

    private func calculateRiskReward() -> Double {
        let profit = abs(prediction.targetPrice - option.lastTradedPrice)
        let loss = abs(option.lastTradedPrice - prediction.stopLoss)
        return loss > 0 ? profit / loss : 0
    }
}

struct TradeSetupItem: View {
    let title: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 11))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Greeks Analysis Card

struct GreeksAnalysisCard: View {
    let option: OptionData

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "function")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.primaryBlue)

                    Text("Greeks Analysis")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()
            }

            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 12) {
                GreekItem(
                    symbol: "Δ",
                    name: "Delta",
                    value: String(format: "%.3f", option.delta ?? 0),
                    interpretation: deltaInterpretation,
                    color: Theme.profit
                )

                GreekItem(
                    symbol: "Γ",
                    name: "Gamma",
                    value: String(format: "%.4f", option.gamma ?? 0),
                    interpretation: gammaInterpretation,
                    color: Theme.primaryBlue
                )

                GreekItem(
                    symbol: "Θ",
                    name: "Theta",
                    value: String(format: "%.2f", option.theta ?? 0),
                    interpretation: thetaInterpretation,
                    color: Theme.loss
                )

                GreekItem(
                    symbol: "ν",
                    name: "Vega",
                    value: String(format: "%.2f", option.vega ?? 0),
                    interpretation: vegaInterpretation,
                    color: Theme.accentOrange
                )
            }
        }
        .solidCard()
    }

    private var deltaInterpretation: String {
        let delta = abs(option.delta ?? 0.5)
        if delta > 0.7 { return "Deep ITM" }
        else if delta > 0.4 { return "Near ATM" }
        else { return "OTM" }
    }

    private var gammaInterpretation: String {
        let gamma = option.gamma ?? 0
        if gamma > 0.01 { return "High acceleration" }
        else if gamma > 0.005 { return "Moderate" }
        else { return "Low acceleration" }
    }

    private var thetaInterpretation: String {
        let theta = abs(option.theta ?? 0)
        if theta > 10 { return "High decay" }
        else if theta > 5 { return "Moderate decay" }
        else { return "Low decay" }
    }

    private var vegaInterpretation: String {
        let vega = option.vega ?? 0
        if vega > 15 { return "IV sensitive" }
        else if vega > 8 { return "Moderate sensitivity" }
        else { return "Low sensitivity" }
    }
}

struct GreekItem: View {
    let symbol: String
    let name: String
    let value: String
    let interpretation: String
    let color: Color

    var body: some View {
        HStack(spacing: 12) {
            Text(symbol)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(color)
                .frame(width: 32, height: 32)
                .background {
                    Circle()
                        .fill(color.opacity(0.15))
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)

                Text(value)
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)

                Text(interpretation)
                    .font(.system(size: 10))
                    .foregroundColor(color)
            }

            Spacer()
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.surfaceElevated)
        }
    }
}

#Preview {
    StrikeAnalysisSheet(
        option: OptionData(
            strikePrice: 25000,
            optionType: .call,
            expiryDate: Date().addingTimeInterval(7 * 24 * 3600),
            lastTradedPrice: 150,
            openInterest: 50000,
            changeInOI: 5000,
            impliedVolatility: 15,
            bidPrice: 148,
            askPrice: 152,
            bidQty: 100,
            askQty: 100,
            volume: 10000,
            underlyingValue: 25050
        ),
        context: MLPredictionContext(
            atmIV: 14,
            pcr: 1.2,
            maxPainStrike: 25000,
            spotPrice: 25050,
            supportLevel: 24800,
            resistanceLevel: 25200
        )
    )
}
