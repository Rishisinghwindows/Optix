import SwiftUI

// MARK: - Trade Suggestion Card

struct TradeSuggestionCard: View {
    let suggestion: AITradeSuggestion
    let showTierBadge: Bool
    let onTap: () -> Void
    @State private var isWhyExpanded = false

    init(suggestion: AITradeSuggestion, showTierBadge: Bool = false, onTap: @escaping () -> Void) {
        self.suggestion = suggestion
        self.showTierBadge = showTierBadge
        self.onTap = onTap
    }

    var body: some View {
        VStack(spacing: 6) {
            // Header Row
            Button(action: onTap) {
                HStack {
                    // Tier Badge (Top Pick star)
                    if showTierBadge && suggestion.tier == .topPick {
                        Image(systemName: "star.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.yellow)
                    }

                    // Strike Badge
                    HStack(spacing: 6) {
                        Text(suggestion.displayStrike)
                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                            .foregroundColor(Theme.textPrimary)

                        Text(suggestion.option.optionType == .call ? "CE" : "PE")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background {
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(suggestion.option.optionType == .call ? Theme.profit : Theme.loss)
                            }
                    }

                    Spacer()

                    // Confidence Badge
                    ConfidenceBadge(confidence: suggestion.score.confidence)

                    // Score Gauge
                    AIScoreGauge(score: suggestion.score.overallScore, size: 40)
                }
            }
            .buttonStyle(.plain)

            // Indicators Row: Theta Dot + OI Signal
            Button(action: onTap) {
                HStack(spacing: 8) {
                    // Theta Zone Dot
                    ThetaZoneDot(zone: suggestion.score.thetaDecayZone)

                    // OI Signal Capsule
                    if let oiSignal = suggestion.score.oiSignal, oiSignal != .neutral {
                        OISignalBadge(signal: oiSignal)
                    }

                    // Term Structure Badge
                    if let ts = suggestion.score.termStructure, ts != .contango {
                        HStack(spacing: 3) {
                            Image(systemName: ts == .inverted ? "exclamationmark.triangle.fill" : "equal")
                                .font(.system(size: 9))
                            Text(ts == .inverted ? "Inverted" : "Flat")
                                .font(.system(size: 10, weight: .semibold))
                        }
                        .foregroundColor(ts == .inverted ? Color(hex: "FF9500") : Theme.textSecondary)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill((ts == .inverted ? Color(hex: "FF9500") : Theme.textMuted).opacity(0.15)))
                    }

                    Spacer()

                    // ML Signal Badge (if available)
                    if let mlPrediction = suggestion.score.mlPrediction {
                        MLSignalBadge(prediction: mlPrediction)
                    }
                }
            }
            .buttonStyle(.plain)

            // Price Row
            Button(action: onTap) {
                HStack(spacing: 12) {
                    PriceItem(
                        label: L.aiInsightsEntry,
                        value: suggestion.displayEntry,
                        color: Theme.textPrimary
                    )

                    PriceItem(
                        label: L.aiInsightsTarget,
                        value: suggestion.displayTarget,
                        color: Theme.profit
                    )

                    PriceItem(
                        label: L.aiInsightsStopLoss,
                        value: suggestion.displayStopLoss,
                        color: Theme.loss
                    )
                }
            }
            .buttonStyle(.plain)

            Divider()
                .background(Color.white.opacity(0.1))
                .padding(.vertical, 1)

            // Stats Row
            Button(action: onTap) {
                HStack(spacing: 6) {
                    // Risk Reward
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.left.arrow.right")
                            .font(.system(size: 11))
                            .foregroundColor(Theme.primaryBlue)

                        Text("R:R \(suggestion.displayRiskReward)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(suggestion.riskRewardRatio >= 2 ? Theme.profit : Theme.textSecondary)
                    }

                    // POP%
                    if let pop = suggestion.score.displayPOP {
                        HStack(spacing: 3) {
                            Image(systemName: "percent")
                                .font(.system(size: 10))
                                .foregroundColor(Theme.primaryBlue)
                            Text("POP \(pop)")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(popColor(suggestion.score.probabilityOfProfit))
                        }
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Theme.surfaceElevated))
                    }

                    Spacer()

                    // Potential Profit
                    HStack(spacing: 3) {
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Theme.profit)

                        Text(suggestion.displayProfitPercentage)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Theme.profit)
                    }

                    Text("/")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    // Potential Loss
                    HStack(spacing: 3) {
                        Image(systemName: "arrow.down.right")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Theme.loss)

                        Text(suggestion.displayLossPercentage)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Theme.loss)
                    }

                    // Timeframe
                    Text(suggestion.timeframe)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Theme.textMuted)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background {
                            Capsule()
                                .fill(Theme.surfaceElevated)
                        }
                }
            }
            .buttonStyle(.plain)

            // "Why this trade?" Expandable Section
            WhyThisTradeSection(
                suggestion: suggestion,
                isExpanded: $isWhyExpanded
            )
        }
        .padding(10)
        .background {
            RoundedRectangle(cornerRadius: 14)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(
                            suggestion.tier == .topPick ?
                                Theme.profit.opacity(0.3) :
                                Color.white.opacity(0.05),
                            lineWidth: 1
                        )
                }
        }
    }

    private func popColor(_ pop: Double?) -> Color {
        guard let pop = pop else { return Theme.textSecondary }
        if pop >= 55 { return Theme.profit }
        if pop >= 40 { return Color(hex: "FBBF24") }
        return Theme.loss
    }
}

// MARK: - Confidence Badge

struct ConfidenceBadge: View {
    let confidence: ConfidenceLevel

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: confidence.icon)
                .font(.system(size: 9))
            Text(confidence.localizedName)
                .font(.system(size: 10, weight: .semibold))
        }
        .foregroundColor(confidence.color)
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background {
            Capsule()
                .fill(confidence.color.opacity(0.15))
        }
    }
}

// MARK: - Theta Zone Dot

struct ThetaZoneDot: View {
    let zone: ThetaDecayZone

    var body: some View {
        HStack(spacing: 5) {
            Circle()
                .fill(zone.color)
                .frame(width: 8, height: 8)

            Text("Theta: \(zone.rawValue)")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(zone.color)
        }
    }
}

// MARK: - OI Signal Badge

struct OISignalBadge: View {
    let signal: OISignal

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: signal.icon)
                .font(.system(size: 10))
            Text(signal.rawValue)
                .font(.system(size: 10, weight: .semibold))
        }
        .foregroundColor(signal.color)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background {
            Capsule()
                .fill(signal.color.opacity(0.12))
        }
    }
}

// MARK: - Why This Trade Section

struct WhyThisTradeSection: View {
    let suggestion: AITradeSuggestion
    @Binding var isExpanded: Bool

    var body: some View {
        VStack(spacing: 0) {
            // Toggle button
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "lightbulb.fill")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.accentOrange)

                    Text("Why this trade?")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Theme.textSecondary)

                    Spacer()

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                }
            }
            .buttonStyle(.plain)

            // Expanded content: top 3 score factors
            if isExpanded {
                VStack(spacing: 6) {
                    ForEach(suggestion.topScoreFactors) { factor in
                        HStack(spacing: 8) {
                            Image(systemName: factor.impact.icon)
                                .font(.system(size: 10))
                                .foregroundColor(factor.impact.color)
                                .frame(width: 14)

                            Text(factor.factor)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(Theme.textSecondary)

                            Spacer()

                            Text("\(Int(factor.score))/100")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(factor.score >= 70 ? Theme.profit : (factor.score >= 50 ? Theme.accentOrange : Theme.loss))
                        }
                    }
                }
                .padding(.top, 8)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
}

// MARK: - Compact Suggestion Card (Worth Watching)

struct CompactSuggestionCard: View {
    let suggestion: AITradeSuggestion
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Strike + Type
                HStack(spacing: 4) {
                    Text(suggestion.displayStrike)
                        .font(.system(size: 15, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)

                    Text(suggestion.option.optionType == .call ? "CE" : "PE")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(suggestion.option.optionType == .call ? Theme.profit : Theme.loss)
                        }
                }

                // Score
                AIScoreGauge(score: suggestion.score.overallScore, size: 30)

                VStack(alignment: .leading, spacing: 2) {
                    // Entry + R:R
                    HStack(spacing: 8) {
                        Text("₹\(suggestion.displayEntry)")
                            .font(.system(size: 12, weight: .semibold, design: .monospaced))
                            .foregroundColor(Theme.textPrimary)

                        Text("R:R \(suggestion.displayRiskReward)")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(suggestion.riskRewardRatio >= 2 ? Theme.profit : Theme.textSecondary)
                    }

                    // Confidence
                    HStack(spacing: 4) {
                        Circle()
                            .fill(suggestion.score.confidence.color)
                            .frame(width: 6, height: 6)
                        Text(suggestion.score.confidence.localizedName)
                            .font(.system(size: 10))
                            .foregroundColor(Theme.textMuted)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
        }
        .buttonStyle(.plain)
    }
}

struct PriceItem: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)

            Text("₹\(value)")
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - AI Score Gauge

struct AIScoreGauge: View {
    let score: Double
    let size: CGFloat

    @State private var animatedProgress: Double = 0
    @State private var displayedScore: Int = 0
    @State private var scoreAnimationWork: DispatchWorkItem?

    private var progress: Double {
        score / 100
    }

    private var scoreColor: Color {
        if score >= 70 {
            return Theme.profit
        } else if score >= 50 {
            return Theme.accentOrange
        } else {
            return Theme.loss
        }
    }

    var body: some View {
        ZStack {
            // Background Circle
            Circle()
                .stroke(scoreColor.opacity(0.2), lineWidth: 3)

            // Progress Arc with animation
            Circle()
                .trim(from: 0, to: animatedProgress)
                .stroke(
                    scoreColor,
                    style: StrokeStyle(lineWidth: 3, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))

            // Score Text
            Text("\(displayedScore)")
                .font(.system(size: size * 0.35, weight: .bold, design: .monospaced))
                .foregroundColor(scoreColor)
        }
        .frame(width: size, height: size)
        .onAppear {
            // Animate the circular progress
            withAnimation(.easeOut(duration: 0.8).delay(0.2)) {
                animatedProgress = progress
            }
            // Animate the score number
            animateScoreCount()
        }
        .onChange(of: score) { _, newScore in
            withAnimation(.easeOut(duration: 0.5)) {
                animatedProgress = newScore / 100
            }
            animateScoreCount()
        }
    }

    private func animateScoreCount() {
        // Cancel any previous animation
        scoreAnimationWork?.cancel()

        let targetScore = Int(score)
        let duration: Double = 0.8
        let steps = 20
        let stepDuration = duration / Double(steps)

        let workItem = DispatchWorkItem { [targetScore] in
            for step in 0...steps {
                DispatchQueue.main.asyncAfter(deadline: .now() + stepDuration * Double(step)) {
                    let progress = Double(step) / Double(steps)
                    // Ease out curve
                    let easedProgress = 1 - pow(1 - progress, 3)
                    displayedScore = Int(Double(targetScore) * easedProgress)
                }
            }
        }

        scoreAnimationWork = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2, execute: workItem)
    }
}

// MARK: - Market Bias Badge

struct MarketBiasBadge: View {
    let bias: MarketBias

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: bias.icon)
                .font(.system(size: 12, weight: .bold))

            Text(bias.shortText)
                .font(.system(size: 12, weight: .semibold))
        }
        .foregroundColor(bias.color)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background {
            Capsule()
                .fill(bias.color.opacity(0.15))
        }
    }
}

// MARK: - ML Signal Badge

struct MLSignalBadge: View {
    let prediction: OptionPrediction

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: prediction.signal.icon)
                .font(.system(size: 10, weight: .bold))

            VStack(alignment: .leading, spacing: 0) {
                Text(prediction.signal.rawValue)
                    .font(.system(size: 9, weight: .bold))

                Text("\(prediction.confidencePercentage)%")
                    .font(.system(size: 8, weight: .medium))
                    .opacity(0.8)
            }
        }
        .foregroundColor(Theme.textPrimary)
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background {
            RoundedRectangle(cornerRadius: 6)
                .fill(
                    LinearGradient(
                        colors: [prediction.signal.color, prediction.signal.color.opacity(0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        }
    }
}

// MARK: - ML Prediction Card

struct MLPredictionCard: View {
    let prediction: OptionPrediction

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "brain.head.profile")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Color(hex: "BF5AF2"))

                    Text("ML Prediction")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                MLSignalBadge(prediction: prediction)
            }

            HStack(spacing: 16) {
                MLMetricItem(
                    title: "Expected",
                    value: prediction.displayExpectedMove,
                    color: prediction.expectedMove >= 0 ? Theme.profit : Theme.loss
                )

                MLMetricItem(
                    title: "Probability",
                    value: prediction.displayProbability,
                    color: prediction.probability >= 0.6 ? Theme.profit : Theme.accentOrange
                )

                MLMetricItem(
                    title: L.aiInsightsConfidence,
                    value: "\(prediction.confidencePercentage)%",
                    color: prediction.confidence >= 0.7 ? Theme.profit : Theme.textSecondary
                )
            }
        }
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(hex: "BF5AF2").opacity(0.1))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "BF5AF2").opacity(0.3), lineWidth: 1)
                }
        }
    }
}

struct MLMetricItem: View {
    let title: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Suggestion Detail Sheet

struct SuggestionDetailSheet: View {
    let suggestion: AITradeSuggestion
    var optionChain: [OptionChainRow] = []
    var spotPrice: Double = 0
    var pcr: Double = 1.0
    var maxPain: Double? = nil
    var atmStrike: Double = 0
    var indexName: String = "NIFTY 50"

    @Environment(\.dismiss) private var dismiss
    @State private var showAIExplanation = false
    @State private var aiExplanation: AIExplanation?
    @State private var isLoadingAI = false

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Header Card
                        DetailHeaderCard(suggestion: suggestion)

                        // Ask AI Button
                        AskAIButton(
                            isLoading: isLoadingAI,
                            hasExplanation: aiExplanation != nil,
                            onTap: {
                                Task {
                                    await fetchAIExplanation()
                                }
                            }
                        )

                        // AI Explanation Card (if available)
                        if let explanation = aiExplanation {
                            AIExplanationCard(explanation: explanation)
                        }

                        // ML Prediction Card (if available)
                        if let mlPrediction = suggestion.score.mlPrediction {
                            MLPredictionCard(prediction: mlPrediction)
                        }

                        // Price Targets Card
                        PriceTargetsCard(suggestion: suggestion)

                        // Score Breakdown Card
                        ScoreBreakdownCard(score: suggestion.score)

                        // Reasoning Card
                        ReasoningCard(score: suggestion.score)

                        // Spot Levels Card
                        SpotLevelsCard(suggestion: suggestion)

                        // Position Sizing Card
                        PositionSizingCard(suggestion: suggestion)
                    }
                    .padding(16)
                    .padding(.bottom, 40)
                }
            }
            .navigationTitle("Trade Details")
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
    }

    private func fetchAIExplanation() async {
        isLoadingAI = true

        // Calculate support/resistance from option chain
        var support: Double? = nil
        var resistance: Double? = nil
        let currentSpot = spotPrice > 0 ? spotPrice : suggestion.option.underlyingValue

        if !optionChain.isEmpty {
            // Find highest Put OI below spot (support)
            if let maxPutOI = optionChain.filter({ $0.putOption != nil && $0.strikePrice < currentSpot })
                .max(by: { ($0.putOption?.openInterest ?? 0) < ($1.putOption?.openInterest ?? 0) }) {
                support = maxPutOI.strikePrice
            }

            // Find highest Call OI above spot (resistance)
            if let maxCallOI = optionChain.filter({ $0.callOption != nil && $0.strikePrice > currentSpot })
                .max(by: { ($0.callOption?.openInterest ?? 0) < ($1.callOption?.openInterest ?? 0) }) {
                resistance = maxCallOI.strikePrice
            }
        }

        // Build comprehensive market context
        let context = MarketContext(
            spotPrice: currentSpot,
            pcr: pcr > 0 ? pcr : suggestion.score.pcrScore / 50,
            maxPain: maxPain,
            marketBias: suggestion.option.optionType == .call ? "Bullish" : "Bearish",
            support: support,
            resistance: resistance,
            atmStrike: atmStrike > 0 ? atmStrike : {
                let interval: Double
                switch indexName.uppercased() {
                case let n where n.contains("BANK") && n.contains("NIFTY"): interval = 100
                case let n where n.contains("SENSEX"): interval = 100
                case let n where n.contains("BANKEX"): interval = 100
                case let n where n.contains("MIDCAP"): interval = 25
                default: interval = 50  // NIFTY, FINNIFTY
                }
                return (currentSpot / interval).rounded() * interval
            }(),
            indexName: indexName,
            vix: nil
        )

        do {
            let explanation = try await ClaudeAIService.shared.analyzeTradeWithAI(
                suggestion: suggestion,
                marketContext: context,
                optionChain: optionChain.isEmpty ? nil : optionChain
            )
            await MainActor.run {
                self.aiExplanation = explanation
                self.isLoadingAI = false
            }
        } catch {
            await MainActor.run {
                self.isLoadingAI = false
            }
            print("AI Error: \(error)")
        }
    }
}

// MARK: - Ask AI Button

struct AskAIButton: View {
    let isLoading: Bool
    let hasExplanation: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(0.9)
                } else {
                    Image(systemName: hasExplanation ? "arrow.clockwise" : "sparkles")
                        .font(.system(size: 16, weight: .semibold))
                }

                Text(isLoading ? "Analyzing..." : (hasExplanation ? "Ask AI Again" : "Ask AI: Should I take this trade?"))
                    .font(.system(size: 14, weight: .semibold))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background {
                LinearGradient(
                    colors: [Color(hex: "8B5CF6"), Color(hex: "6366F1")],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            }
            .cornerRadius(12)
            .shadow(color: Color(hex: "8B5CF6").opacity(0.4), radius: 8, x: 0, y: 4)
        }
        .disabled(isLoading)
        .opacity(isLoading ? 0.8 : 1.0)
    }
}

// MARK: - AI Explanation Card

struct AIExplanationCard: View {
    let explanation: AIExplanation

    private var probabilityColor: Color {
        if explanation.winProbability >= 65 {
            return Theme.profit
        } else if explanation.winProbability >= 45 {
            return Theme.accentOrange
        } else {
            return Theme.loss
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header with Verdict and Win Probability
            HStack(spacing: 12) {
                // AI Analysis Title
                HStack(spacing: 6) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Color(hex: "8B5CF6"))

                    Text("AI Analysis")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                // Verdict Badge
                HStack(spacing: 4) {
                    Image(systemName: explanation.verdict.icon)
                        .font(.system(size: 12, weight: .bold))

                    Text(explanation.verdict.rawValue.uppercased())
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background {
                    Capsule()
                        .fill(Color(hex: explanation.verdict.color))
                }
            }

            // Win Probability Gauge
            HStack(spacing: 16) {
                // Circular Progress
                ZStack {
                    Circle()
                        .stroke(probabilityColor.opacity(0.2), lineWidth: 6)
                        .frame(width: 60, height: 60)

                    Circle()
                        .trim(from: 0, to: Double(explanation.winProbability) / 100)
                        .stroke(probabilityColor, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .frame(width: 60, height: 60)
                        .rotationEffect(.degrees(-90))

                    VStack(spacing: 0) {
                        Text("\(explanation.winProbability)%")
                            .font(.system(size: 16, weight: .bold, design: .monospaced))
                            .foregroundColor(probabilityColor)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Win Probability")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textSecondary)

                    Text(probabilityDescription)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(probabilityColor)
                }

                Spacer()
            }
            .padding(12)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(probabilityColor.opacity(0.1))
            }

            // Key Reason
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: "lightbulb.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.accentOrange)
                    Text("Key Insight")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textSecondary)
                }

                Text(explanation.keyReason)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            // Risk Warning
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.loss)
                    Text("Risk Warning")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textSecondary)
                }

                Text(explanation.riskWarning)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            // Better Alternative (if any)
            if let alternative = explanation.betterAlternative, !alternative.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.triangle.swap")
                            .font(.system(size: 12))
                            .foregroundColor(Theme.primaryBlue)
                        Text("Consider Instead")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Theme.textSecondary)
                    }

                    Text(alternative)
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            // Disclaimer
            Text("AI analysis is for educational purposes only. Always do your own research.")
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)
                .padding(.top, 4)
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(hex: "8B5CF6").opacity(0.1))
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "8B5CF6").opacity(0.3), lineWidth: 1)
                }
        }
    }

    private var probabilityDescription: String {
        if explanation.winProbability >= 70 {
            return "High probability trade"
        } else if explanation.winProbability >= 55 {
            return "Moderate probability"
        } else if explanation.winProbability >= 40 {
            return "Low probability"
        } else {
            return "Very risky trade"
        }
    }
}

struct DetailHeaderCard: View {
    let suggestion: AITradeSuggestion

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(suggestion.displayStrike)
                            .font(.system(size: 28, weight: .bold, design: .monospaced))
                            .foregroundColor(Theme.textPrimary)

                        Text(suggestion.option.optionType == .call ? "CE" : "PE")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(suggestion.option.optionType == .call ? Theme.profit : Theme.loss)
                            }
                    }

                    Text("Expiry: \(suggestion.option.daysToExpiry) days")
                        .font(.system(size: 13))
                        .foregroundColor(Theme.textSecondary)
                }

                Spacer()

                AIScoreGauge(score: suggestion.score.overallScore, size: 64)
            }

            HStack(spacing: 12) {
                ConfidenceBadge(confidence: suggestion.score.confidence)

                Text("•")
                    .foregroundColor(Theme.textMuted)

                Text(suggestion.timeframe)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Theme.textMuted)

                Spacer()

                if suggestion.isGoodTrade {
                    HStack(spacing: 4) {
                        Image(systemName: "hand.thumbsup.fill")
                        Text("Good Setup")
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Theme.profit)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background {
                        Capsule()
                            .fill(Theme.profit.opacity(0.15))
                    }
                }
            }
        }
        .solidCard()
    }
}

struct PriceTargetsCard: View {
    let suggestion: AITradeSuggestion

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Price Targets")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()
            }

            HStack(spacing: 0) {
                // Entry
                VStack(spacing: 8) {
                    Text(L.aiInsightsEntry)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Text("₹\(suggestion.displayEntry)")
                        .font(.system(size: 20, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                }
                .frame(maxWidth: .infinity)

                Rectangle()
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 1)
                    .padding(.vertical, 4)

                // Target
                VStack(spacing: 8) {
                    Text(L.aiInsightsTarget)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Text("₹\(suggestion.displayTarget)")
                        .font(.system(size: 20, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.profit)

                    Text(suggestion.displayProfitPercentage)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.profit)
                }
                .frame(maxWidth: .infinity)

                Rectangle()
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 1)
                    .padding(.vertical, 4)

                // Stop Loss
                VStack(spacing: 8) {
                    Text(L.aiInsightsStopLoss)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Text("₹\(suggestion.displayStopLoss)")
                        .font(.system(size: 20, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.loss)

                    Text(suggestion.displayLossPercentage)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.loss)
                }
                .frame(maxWidth: .infinity)
            }

            // Risk Reward
            HStack {
                Text("Risk:Reward Ratio")
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                Text(suggestion.displayRiskReward)
                    .font(.system(size: 16, weight: .bold, design: .monospaced))
                    .foregroundColor(suggestion.riskRewardRatio >= 2 ? Theme.profit : Theme.primaryBlue)

                if suggestion.riskRewardRatio >= 2 {
                    Image(systemName: "star.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.accentOrange)
                }
            }
            .padding(.top, 8)
        }
        .solidCard()
    }
}

struct ScoreBreakdownCard: View {
    let score: OptionScore

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Score Breakdown")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                Text("\(score.displayScore)/100")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(score.confidence.color)
            }

            VStack(spacing: 10) {
                ScoreRowItem(label: "IV Rank", score: score.ivRankScore ?? score.ivScore, weight: "18%")
                ScoreRowItem(label: "IV Percentile", score: score.ivPercentileScore ?? 50, weight: "6%")
                ScoreRowItem(label: "OI Buildup", score: score.oiScore, weight: "18%")
                ScoreRowItem(label: "POP", score: score.probabilityOfProfit ?? score.greeksScore, weight: "14%")
                ScoreRowItem(label: "Greeks Position", score: score.greeksScore, weight: "14%")
                ScoreRowItem(label: "Volume Surge", score: score.volumeScore, weight: "9%")
                ScoreRowItem(label: "PCR Context", score: score.pcrScore, weight: "9%")
                ScoreRowItem(label: "Max Pain", score: score.maxPainScore, weight: "4%")
                ScoreRowItem(label: "Liquidity", score: score.liquidityScore, weight: "4%")
                ScoreRowItem(label: "Skew", score: score.skewScore ?? 50, weight: "2%")
                ScoreRowItem(label: "Term Structure", score: score.termStructureScore ?? 50, weight: "2%")
            }
        }
        .solidCard()
    }
}

struct ScoreRowItem: View {
    let label: String
    let score: Double
    let weight: String

    private var scoreColor: Color {
        if score >= 70 {
            return Theme.profit
        } else if score >= 50 {
            return Theme.accentOrange
        } else {
            return Theme.loss
        }
    }

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(Theme.textSecondary)

            Text("(\(weight))")
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)

            Spacer()

            // Score Bar
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.white.opacity(0.1))

                    RoundedRectangle(cornerRadius: 3)
                        .fill(scoreColor)
                        .frame(width: geometry.size.width * (score / 100))
                }
            }
            .frame(width: 60, height: 6)

            Text("\(Int(score))")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(scoreColor)
                .frame(width: 30, alignment: .trailing)
        }
    }
}

struct ReasoningCard: View {
    let score: OptionScore

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Analysis Insights")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()
            }

            ForEach(score.reasoning) { reason in
                InsightReasoningRow(reasoning: reason)
            }
        }
        .solidCard()
    }
}

struct InsightReasoningRow: View {
    let reasoning: ScoreReasoning

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: reasoning.impact.icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(reasoning.impact.color)
                .frame(width: 28, height: 28)
                .background {
                    Circle()
                        .fill(reasoning.impact.color.opacity(0.15))
                }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(reasoning.factor)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    Spacer()

                    Text("\(Int(reasoning.score))")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(reasoning.impact.color)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background {
                            Capsule()
                                .fill(reasoning.impact.color.opacity(0.15))
                        }
                }

                Text(reasoning.description)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct SpotLevelsCard: View {
    let suggestion: AITradeSuggestion

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text("Underlying Levels")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()
            }

            HStack(spacing: 20) {
                VStack(spacing: 8) {
                    Text("Current Spot")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.2f", suggestion.option.underlyingValue))
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                }
                .frame(maxWidth: .infinity)

                VStack(spacing: 8) {
                    Text("Target Spot")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.0f", suggestion.targetSpot))
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.profit)
                }
                .frame(maxWidth: .infinity)

                VStack(spacing: 8) {
                    Text("SL Spot")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.0f", suggestion.stopLossSpot))
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.loss)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .solidCard()
    }
}

// MARK: - Position Sizing Card

struct PositionSizingCard: View {
    let suggestion: AITradeSuggestion
    @State private var accountSize: Double = 100000 // Default 1 lakh
    @State private var riskPercentage: Double = 2.0
    @State private var lotSize: Int = 75 // Default Nifty lot size

    private var positioning: PositionSizing {
        PositionSizing(
            riskPercentage: riskPercentage,
            accountSize: accountSize,
            optionLTP: suggestion.entryPrice,
            stopLossPrice: suggestion.stopLossPrice,
            lotSize: lotSize
        )
    }

    var body: some View {
        VStack(spacing: 16) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "chart.pie.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Color(hex: "BF5AF2"))

                    Text("Position Sizing")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                // Risk % Badge
                Text("\(Int(riskPercentage))% Risk")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background {
                        Capsule()
                            .fill(Color(hex: "BF5AF2"))
                    }
            }

            // Account Size Slider
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Account Size")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Spacer()

                    Text("₹\(formatAmount(accountSize))")
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                }

                Slider(value: $accountSize, in: 50000...1000000, step: 25000)
                    .tint(Color(hex: "BF5AF2"))
            }

            // Risk Percentage Slider
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Risk Per Trade")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Spacer()

                    Text("\(String(format: "%.1f", riskPercentage))%")
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                }

                Slider(value: $riskPercentage, in: 0.5...5.0, step: 0.5)
                    .tint(Color(hex: "BF5AF2"))
            }

            Divider()
                .background(Color.white.opacity(0.1))

            // Results
            HStack(spacing: 0) {
                VStack(spacing: 4) {
                    Text("Buy")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text(positioning.displayRecommendedLots)
                        .font(.system(size: 18, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.profit)
                }
                .frame(maxWidth: .infinity)

                Rectangle()
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 1)
                    .padding(.vertical, 4)

                VStack(spacing: 4) {
                    Text("Investment")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text("₹\(positioning.displayTotalInvestment)")
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                }
                .frame(maxWidth: .infinity)

                Rectangle()
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 1)
                    .padding(.vertical, 4)

                VStack(spacing: 4) {
                    Text("Max Loss")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text("₹\(positioning.displayMaxLoss)")
                        .font(.system(size: 16, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.loss)
                }
                .frame(maxWidth: .infinity)
            }

            // Risk Amount Info
            HStack {
                Image(systemName: "info.circle.fill")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.primaryBlue)

                Text("Risk per trade: ₹\(positioning.displayRiskPerTrade) (\(Int(riskPercentage))% of capital)")
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textSecondary)

                Spacer()
            }
            .padding(.top, 4)
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "BF5AF2").opacity(0.3), lineWidth: 1)
                }
        }
    }

    private func formatAmount(_ amount: Double) -> String {
        if amount >= 100000 {
            return String(format: "%.2f L", amount / 100000)
        } else if amount >= 1000 {
            return String(format: "%.1f K", amount / 1000)
        } else {
            return String(format: "%.0f", amount)
        }
    }
}

// MARK: - Preview

#Preview {
    AIInsightsView(optionChainViewModel: OptionChainViewModel())
}
