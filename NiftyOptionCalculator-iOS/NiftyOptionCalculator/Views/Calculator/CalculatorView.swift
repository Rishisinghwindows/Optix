import SwiftUI

struct CalculatorView: View {
    @ObservedObject var viewModel: CalculatorViewModel
    @ObservedObject var paperTradingVM: PaperTradingViewModel
    @FocusState private var focusedField: Field?
    @State private var showAdvancedAnalysis = true
    @State private var showSimulator = false
    @StateObject private var simulatorViewModel = PnLSimulatorViewModel()
    @AppStorage("hideCalculatorDisclaimer") private var hideDisclaimer = false

    enum Field: Hashable {
        case spot, strike, days, iv, target, stopLoss, currentPrice
    }

    var body: some View {
        ZStack {
            Theme.backgroundGradient.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 8) {
                    // Header
                    PremiumCalculatorHeader(viewModel: viewModel)

                    // Risk Disclaimer Banner
                    if !hideDisclaimer {
                        RiskDisclaimerBanner(hideDisclaimer: $hideDisclaimer)
                    }

                    // Option Type Selector
                    ModernOptionTypeSelector(
                        selectedType: viewModel.optionType,
                        onTypeSelected: { type in
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                                viewModel.optionType = type
                                viewModel.calculateAll()
                            }
                        }
                    )

                    // Results Section - Theoretical Price
                    if viewModel.calculatedPrice != nil {
                        EnhancedPriceResultCard(viewModel: viewModel)
                            .transition(.asymmetric(
                                insertion: .scale(scale: 0.95).combined(with: .opacity),
                                removal: .opacity
                            ))

                        // What-If Simulator Button
                        Button {
                            let generator = UIImpactFeedbackGenerator(style: .medium)
                            generator.impactOccurred()
                            simulatorViewModel.setupSingleOption(
                                spotPrice: viewModel.spotPriceValue,
                                strikePrice: viewModel.strikePriceValue,
                                optionType: viewModel.optionType,
                                premium: viewModel.calculatedPrice ?? 0,
                                iv: viewModel.ivValue,
                                dte: viewModel.daysToExpiryValue,
                                lotSize: 75,
                                quantity: 1
                            )
                            showSimulator = true
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "slider.horizontal.3")
                                    .font(.system(size: 14, weight: .semibold))
                                Text("What-If Simulator")
                                    .font(.system(size: 14, weight: .semibold))
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .semibold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .background(
                                LinearGradient(
                                    colors: [Theme.accentPurple, Theme.accentBlue],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(12)
                        }
                        .buttonStyle(.plain)
                    }

                    // Price Calculator - Target/SL (Moved UP for beginners)
                    EnhancedTargetStopLossCard(
                        viewModel: viewModel,
                        paperTradingVM: paperTradingVM,
                        focusedField: $focusedField
                    )

                    // Advanced Section Header
                    if viewModel.greeks != nil {
                        AdvancedSectionHeader(showAdvanced: $showAdvancedAnalysis)
                    }

                    // Greeks Section (Collapsible - for advanced users)
                    if showAdvancedAnalysis, let greeks = viewModel.greeks {
                        VStack(spacing: 16) {
                            GreeksDisplayView(greeks: greeks)
                                .transition(.asymmetric(
                                    insertion: .move(edge: .bottom).combined(with: .opacity),
                                    removal: .opacity
                                ))

                            // Probability & Risk Card
                            ProbabilityRiskCard(
                                viewModel: viewModel,
                                greeks: greeks
                            )
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }

                    // Input Parameters Card (moved to bottom)
                    InputParametersCard(
                        viewModel: viewModel,
                        focusedField: $focusedField
                    )

                }
                .padding(.horizontal, 16)
                .padding(.top, 6)
                .padding(.bottom, 100) // Space for tab bar
            }
        }
        .onTapGesture {
            focusedField = nil
        }
        .onChange(of: viewModel.spotPrice) { _, _ in
            viewModel.calculateAll()
        }
        .onChange(of: viewModel.strikePrice) { _, _ in
            viewModel.calculateAll()
        }
        .onChange(of: viewModel.daysToExpiry) { _, _ in
            viewModel.calculateAll()
        }
        .onChange(of: viewModel.impliedVolatility) { _, _ in
            viewModel.calculateAll()
        }
        .onChange(of: viewModel.optionType) { _, _ in
            viewModel.calculateAll()
        }
        .onChange(of: viewModel.targetSpot) { _, _ in
            viewModel.calculateTargetSL()
        }
        .onChange(of: viewModel.stopLossSpot) { _, _ in
            viewModel.calculateTargetSL()
        }
        .sheet(isPresented: $showSimulator) {
            PnLSimulatorView(viewModel: simulatorViewModel)
        }
    }

    private func triggerHaptic(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.impactOccurred()
    }
}

// MARK: - Advanced Section Header (Collapsible)

struct AdvancedSectionHeader: View {
    @Binding var showAdvanced: Bool

    var body: some View {
        Button {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()
            withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                showAdvanced.toggle()
            }
        } label: {
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "chart.bar.doc.horizontal")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.accentPurple)

                    Text(L.calculatorAdvancedAnalysis)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    Text(L.calculatorGreeksAndRisk)
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                }

                Spacer()

                HStack(spacing: 6) {
                    Text(showAdvanced ? L.calculatorHide : L.calculatorShow)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Theme.accentPurple)

                    Image(systemName: showAdvanced ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.accentPurple)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background {
                    Capsule()
                        .fill(Theme.accentPurple.opacity(0.15))
                }
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 16)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Theme.accentPurple.opacity(0.2), lineWidth: 1)
                    }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Premium Calculator Header

struct PremiumCalculatorHeader: View {
    @ObservedObject var viewModel: CalculatorViewModel

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "function")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Theme.blueGradient)

                    Text(L.calculatorTitle)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                }

                Text("Black-Scholes Pricing Model")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Theme.textMuted)
            }

            Spacer()

            Button {
                let impact = UIImpactFeedbackGenerator(style: .light)
                impact.impactOccurred()
                withAnimation(.spring(response: 0.3)) {
                    viewModel.reset()
                }
            } label: {
                Image(systemName: "arrow.counterclockwise")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.primaryBlue)
                    .padding(12)
                    .background {
                        Circle()
                            .fill(Theme.primaryBlue.opacity(0.15))
                            .overlay {
                                Circle()
                                    .stroke(Theme.primaryBlue.opacity(0.3), lineWidth: 1)
                            }
                    }
            }
        }
    }
}

// MARK: - Modern Option Type Selector

struct ModernOptionTypeSelector: View {
    let selectedType: OptionType
    let onTypeSelected: (OptionType) -> Void
    @Namespace private var animation

    var body: some View {
        HStack(spacing: 0) {
            ForEach(OptionType.allCases, id: \.self) { type in
                ModernOptionTypeButton(
                    type: type,
                    isSelected: selectedType == type,
                    namespace: animation,
                    action: { onTypeSelected(type) }
                )
            }
        }
        .padding(4)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
        }
    }
}

struct ModernOptionTypeButton: View {
    let type: OptionType
    let isSelected: Bool
    var namespace: Namespace.ID
    let action: () -> Void

    private var color: Color {
        type == .call ? Theme.profit : Theme.loss
    }

    var body: some View {
        Button(action: {
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.impactOccurred()
            action()
        }) {
            HStack(spacing: 8) {
                Image(systemName: type == .call ? "arrow.up.right" : "arrow.down.right")
                    .font(.system(size: 16, weight: .bold))
                    .symbolEffect(.bounce, value: isSelected)

                Text(type.displayName.uppercased())
                    .font(.system(size: 13, weight: .heavy))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .foregroundColor(isSelected ? .white : Theme.textSecondary)
            .background {
                if isSelected {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(
                            LinearGradient(
                                colors: [color, color.opacity(0.8)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .matchedGeometryEffect(id: "optionType", in: namespace)
                        .shadow(color: color.opacity(0.4), radius: 8, x: 0, y: 4)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Input Parameters Card

struct InputParametersCard: View {
    @ObservedObject var viewModel: CalculatorViewModel
    var focusedField: FocusState<CalculatorView.Field?>.Binding
    @State private var showIVInfo = false

    var body: some View {
        VStack(spacing: 10) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.blueGradient)

                    Text(L.calculatorParameters)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                BadgeView(text: "BS Model", color: Theme.accentPurple, size: .small)
            }

            // Input Grid
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 8),
                GridItem(.flexible(), spacing: 8)
            ], spacing: 8) {
                LabeledInputField(
                    label: L.calculatorSpotPrice,
                    value: $viewModel.spotPrice,
                    icon: "indianrupeesign",
                    isFocused: focusedField.wrappedValue == .spot
                )
                .focused(focusedField, equals: .spot)

                LabeledInputField(
                    label: L.calculatorStrikePrice,
                    value: $viewModel.strikePrice,
                    icon: "target",
                    isFocused: focusedField.wrappedValue == .strike
                )
                .focused(focusedField, equals: .strike)

                LabeledInputField(
                    label: L.calculatorDaysToExpiry,
                    value: $viewModel.daysToExpiry,
                    icon: "calendar",
                    isFocused: focusedField.wrappedValue == .days
                )
                .focused(focusedField, equals: .days)

                LabeledInputFieldWithInfo(
                    label: L.calculatorIVPercent,
                    value: $viewModel.impliedVolatility,
                    icon: "waveform.path.ecg",
                    isFocused: focusedField.wrappedValue == .iv,
                    showInfo: $showIVInfo
                )
                .focused(focusedField, equals: .iv)
                .sheet(isPresented: $showIVInfo) {
                    IVEducationalSheet()
                        .presentationDetents([.medium])
                }
            }

            // Current Price with IV Calculator
            HStack(spacing: 12) {
                LabeledInputField(
                    label: "Current LTP (optional)",
                    value: $viewModel.currentOptionPrice,
                    icon: "tag",
                    isFocused: focusedField.wrappedValue == .currentPrice
                )
                .focused(focusedField, equals: .currentPrice)

                Button {
                    let impact = UIImpactFeedbackGenerator(style: .light)
                    impact.impactOccurred()
                    viewModel.calculateImpliedVolatility()
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: "function")
                            .font(.system(size: 18, weight: .bold))
                        Text("IV")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundColor(Theme.accentPurple)
                    .frame(width: 56, height: 56)
                    .background {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Theme.accentPurple.opacity(0.15))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Theme.accentPurple.opacity(0.3), lineWidth: 1)
                            }
                    }
                }
            }
        }
        .solidCard()
    }
}

struct LabeledInputField: View {
    let label: String
    @Binding var value: String
    let icon: String
    var isFocused: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)

            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 13))
                    .foregroundColor(isFocused ? Theme.primaryBlue : Theme.textMuted)
                    .frame(width: 18)

                TextField("", text: $value)
                    .font(.system(size: 15, weight: .semibold, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)
                    .keyboardType(.decimalPad)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 10)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Theme.card)
                    .overlay {
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(isFocused ? Theme.primaryBlue : Theme.border, lineWidth: isFocused ? 1.5 : 1)
                    }
            }
        }
    }
}

// MARK: - Premium Calculate Button

struct PremiumCalculateButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.system(size: 16, weight: .semibold))

                Text(L.calculatorCalculate)
                    .font(.system(size: 15, weight: .bold))
            }
            .foregroundColor(Theme.textPrimary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.blueGradient)
                    .shadow(color: Theme.primaryBlue.opacity(0.4), radius: 8, x: 0, y: 4)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Enhanced Price Result Card

struct EnhancedPriceResultCard: View {
    @ObservedObject var viewModel: CalculatorViewModel

    private var priceColor: Color {
        viewModel.optionType == .call ? Theme.profit : Theme.loss
    }

    private var moneyness: String {
        let spot = viewModel.spotPriceValue
        let strike = viewModel.strikePriceValue
        if viewModel.optionType == .call {
            if spot > strike { return "ITM" }
            if spot < strike { return "OTM" }
        } else {
            if spot < strike { return "ITM" }
            if spot > strike { return "OTM" }
        }
        return "ATM"
    }

    private var intrinsicValue: Double {
        let spot = viewModel.spotPriceValue
        let strike = viewModel.strikePriceValue
        if viewModel.optionType == .call {
            return max(spot - strike, 0)
        } else {
            return max(strike - spot, 0)
        }
    }

    private var timeValue: Double {
        (viewModel.calculatedPrice ?? 0) - intrinsicValue
    }

    var body: some View {
        VStack(spacing: 6) {
            // Main Price Display
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(L.calculatorTheoreticalPrice)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Theme.textMuted)

                        BadgeView(text: moneyness, color: moneyness == "ITM" ? Theme.profit : (moneyness == "OTM" ? Theme.loss : Theme.primaryBlue), size: .small)
                    }

                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text("₹")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(priceColor.opacity(0.7))

                        Text(String(format: "%.2f", viewModel.calculatedPrice ?? 0))
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(priceColor)
                    }
                }

                Spacer()

                // Option badge
                VStack(alignment: .trailing, spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: viewModel.optionType == .call ? "arrow.up.right" : "arrow.down.right")
                            .font(.system(size: 14, weight: .bold))
                        Text(viewModel.optionType.displayName.uppercased())
                            .font(.system(size: 12, weight: .black))
                    }
                    .foregroundColor(Theme.textPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background {
                        Capsule()
                            .fill(priceColor)
                    }

                    Text("Strike: \(viewModel.strikePrice)")
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundColor(Theme.textSecondary)
                }
            }

            // Value Breakdown
            HStack(spacing: 0) {
                ValueBreakdownItem(
                    label: L.calculatorIntrinsicValue,
                    value: String(format: "₹%.2f", intrinsicValue),
                    color: Theme.profit
                )

                Rectangle()
                    .fill(Theme.textMuted.opacity(0.2))
                    .frame(width: 1, height: 36)

                ValueBreakdownItem(
                    label: L.calculatorTimeValue,
                    value: String(format: "₹%.2f", max(timeValue, 0)),
                    color: Theme.accentOrange
                )

                Rectangle()
                    .fill(Theme.textMuted.opacity(0.2))
                    .frame(width: 1, height: 36)

                ValueBreakdownItem(
                    label: L.calculatorDaysLeft,
                    value: viewModel.daysToExpiry,
                    color: Theme.primaryBlue
                )

                Rectangle()
                    .fill(Theme.textMuted.opacity(0.2))
                    .frame(width: 1, height: 36)

                ValueBreakdownItem(
                    label: "IV",
                    value: "\(viewModel.impliedVolatility)%",
                    color: Theme.accentPurple
                )
            }
            .padding(.vertical, 8)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surfaceElevated.opacity(0.5))
            }

            // Theoretical price note
            HStack(spacing: 4) {
                Image(systemName: "info.circle")
                    .font(.system(size: 9))
                Text(L.calculatorTheoreticalNote)
                    .font(.system(size: 9))
            }
            .foregroundColor(Theme.textMuted)
        }
        .solidCard()
    }
}

struct ValueBreakdownItem: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 9, weight: .medium))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Probability & Risk Card (New Component)

struct ProbabilityRiskCard: View {
    @ObservedObject var viewModel: CalculatorViewModel
    let greeks: GreeksResult

    // Estimate POP based on delta (simplified)
    // |delta| approximates probability of expiring ITM for both calls and puts
    private var estimatedPOP: Double {
        return abs(greeks.delta) * 100
    }

    // Risk level based on days to expiry and moneyness
    private var riskLevel: Double {
        let days = viewModel.daysToExpiryValue
        let spot = viewModel.spotPriceValue
        let strike = viewModel.strikePriceValue
        let distance = abs(spot - strike) / spot * 100

        var risk: Double = 50

        // Closer to expiry = higher risk
        if days <= 3 { risk += 30 }
        else if days <= 7 { risk += 15 }

        // OTM = higher risk
        if viewModel.optionType == .call && spot < strike { risk += distance }
        else if viewModel.optionType == .put && spot > strike { risk += distance }

        return min(risk, 100)
    }

    var body: some View {
        VStack(spacing: 16) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "chart.pie.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.accentCyan)

                    Text(L.calculatorProbabilityRisk)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                POPBadge(probability: estimatedPOP)
            }

            // Probability Curve
            VStack(alignment: .leading, spacing: 8) {
                Text(L.calculatorPriceDistribution)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Theme.textMuted)

                ProbabilityCurveView(
                    currentPrice: viewModel.spotPriceValue,
                    strikePrice: viewModel.strikePriceValue,
                    standardDeviation: viewModel.spotPriceValue * viewModel.ivValue * sqrt(viewModel.timeToExpiry),
                    profitZoneStart: viewModel.optionType == .call ? viewModel.strikePriceValue : viewModel.spotPriceValue * 0.8,
                    profitZoneEnd: viewModel.optionType == .call ? viewModel.spotPriceValue * 1.2 : viewModel.strikePriceValue
                )
                .frame(height: 80)
            }
            .padding(12)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surfaceElevated.opacity(0.5))
            }

            // Risk Meter Row
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(L.calculatorRiskAssessment)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Theme.textMuted)

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(L.calculatorThetaRisk)
                                .font(.system(size: 10))
                                .foregroundColor(Theme.textSecondary)
                            Spacer()
                            Text(viewModel.daysToExpiryValue <= 3 ? L.aiInsightsHigh : (viewModel.daysToExpiryValue <= 7 ? L.aiInsightsMedium : L.aiInsightsLow))
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(viewModel.daysToExpiryValue <= 3 ? Theme.loss : (viewModel.daysToExpiryValue <= 7 ? Theme.accentOrange : Theme.profit))
                        }

                        HStack {
                            Text(L.calculatorVegaRisk)
                                .font(.system(size: 10))
                                .foregroundColor(Theme.textSecondary)
                            Spacer()
                            Text(greeks.vega > 10 ? L.aiInsightsHigh : (greeks.vega > 5 ? L.aiInsightsMedium : L.aiInsightsLow))
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(greeks.vega > 10 ? Theme.loss : (greeks.vega > 5 ? Theme.accentOrange : Theme.profit))
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                RiskMeter(riskLevel: riskLevel)
            }
            .padding(12)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.surfaceElevated.opacity(0.5))
            }
        }
        .solidCard()
    }
}

// MARK: - Simple Target Stop Loss Card (Beginner Friendly)

struct EnhancedTargetStopLossCard: View {
    @ObservedObject var viewModel: CalculatorViewModel
    @ObservedObject var paperTradingVM: PaperTradingViewModel
    var focusedField: FocusState<CalculatorView.Field?>.Binding

    var body: some View {
            VStack(spacing: 8) {
                // Header with explanation
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: "target")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.accentOrange)

                        Text(L.calculatorPriceCalculator)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                    }

                    Spacer()

                    BadgeView(text: L.calculatorSimple, color: Theme.profit, size: .small)
                }

                Text(L.calculatorTargetDescription)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }

            // Simple Input Section with Presets
            VStack(spacing: 8) {
                // Target Input with Presets
                VStack(spacing: 4) {
                    SimpleNiftyInput(
                        title: viewModel.optionType == .call ? L.calculatorIfNiftyGoesTo : L.calculatorIfNiftyFallsTo,
                        subtitle: L.calculatorYourTarget,
                        value: $viewModel.targetSpot,
                        icon: viewModel.optionType == .call ? "arrow.up.circle.fill" : "arrow.down.circle.fill",
                        color: Theme.profit,
                        isFocused: focusedField.wrappedValue == .target
                    )
                    .focused(focusedField, equals: .target)

                    // Target Preset Buttons
                    PresetButtonsRow(
                        baseValue: viewModel.spotPriceValue,
                        isCall: viewModel.optionType == .call,
                        isTarget: true,
                        onSelect: { value in
                            viewModel.targetSpot = String(format: "%.0f", value)
                        }
                    )
                }

                // Stop Loss Input with Presets
                VStack(spacing: 4) {
                    SimpleNiftyInput(
                        title: viewModel.optionType == .call ? L.calculatorIfNiftyFallsTo : L.calculatorIfNiftyGoesTo,
                        subtitle: L.calculatorYourStopLoss,
                        value: $viewModel.stopLossSpot,
                        icon: viewModel.optionType == .call ? "arrow.down.circle.fill" : "arrow.up.circle.fill",
                        color: Theme.loss,
                        isFocused: focusedField.wrappedValue == .stopLoss
                    )
                    .focused(focusedField, equals: .stopLoss)

                    // Stop Loss Preset Buttons
                    PresetButtonsRow(
                        baseValue: viewModel.spotPriceValue,
                        isCall: viewModel.optionType == .call,
                        isTarget: false,
                        onSelect: { value in
                            viewModel.stopLossSpot = String(format: "%.0f", value)
                        }
                    )
                }
            }

            // Auto-Calculated Results
            if let calc = viewModel.targetCalculation {
                VStack(spacing: 6) {
                    // Divider with label
                    HStack {
                        Rectangle()
                            .fill(Theme.textMuted.opacity(0.2))
                            .frame(height: 1)

                        Text(L.calculatorYourOptionWillBe)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Theme.textMuted)

                        Rectangle()
                            .fill(Theme.textMuted.opacity(0.2))
                            .frame(height: 1)
                    }
                    .padding(.vertical, 0)

                    // Target Result - Big and Clear
                    SimplePriceResultCard(
                        scenario: "At Target (\(Int(calc.targetSpot)))",
                        icon: "flag.checkered.2.crossed",
                        optionPrice: calc.targetOptionPrice,
                        currentPrice: calc.currentOptionPrice,
                        color: Theme.profit
                    )

                    // Stop Loss Result
                    SimplePriceResultCard(
                        scenario: "At Stop-Loss (\(Int(calc.stopLossSpot)))",
                        icon: "exclamationmark.shield.fill",
                        optionPrice: calc.stopLossOptionPrice,
                        currentPrice: calc.currentOptionPrice,
                        color: Theme.loss
                    )

                    // Risk Reward Summary
                    HStack(spacing: 8) {
                        // Max Profit
                        VStack(spacing: 4) {
                            Text(L.calculatorMaxProfit)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(Theme.textMuted)
                            Text(calc.displayProfit)
                                .font(.system(size: 15, weight: .bold, design: .monospaced))
                                .foregroundColor(Theme.profit)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Theme.profit.opacity(0.1))
                        }

                        // Max Loss
                        VStack(spacing: 4) {
                            Text(L.calculatorMaxLoss)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(Theme.textMuted)
                            Text(calc.displayLoss)
                                .font(.system(size: 15, weight: .bold, design: .monospaced))
                                .foregroundColor(Theme.loss)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Theme.loss.opacity(0.1))
                        }

                        // Risk Reward
                        VStack(spacing: 4) {
                            Text(L.aiInsightsRiskReward)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(Theme.textMuted)
                            HStack(spacing: 4) {
                                Text("1:\(String(format: "%.1f", calc.riskRewardRatio))")
                                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                                    .foregroundColor(calc.isGoodRiskReward ? Theme.profit : Theme.accentOrange)
                                Image(systemName: calc.isGoodRiskReward ? "hand.thumbsup.fill" : "hand.thumbsdown.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(calc.isGoodRiskReward ? Theme.profit : Theme.accentOrange)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Theme.primaryBlue.opacity(0.1))
                        }
                    }

                    // Paper Trade Button
                    PaperTradeButton(
                        viewModel: viewModel,
                        paperTradingVM: paperTradingVM
                    )
                }
            } else {
                // Placeholder when no calculation yet
                VStack(spacing: 8) {
                    Image(systemName: "arrow.up.arrow.down.circle")
                        .font(.system(size: 32))
                        .foregroundColor(Theme.textMuted.opacity(0.5))

                    Text(L.calculatorEnterTargetSL)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
            }
        }
        .solidCard()
    }
}

// MARK: - Simple Nifty Input Field

struct SimpleNiftyInput: View {
    let title: String
    let subtitle: String
    @Binding var value: String
    let icon: String
    let color: Color
    var isFocused: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            // Icon
            Image(systemName: icon)
                .font(.system(size: 28, weight: .semibold))
                .foregroundColor(color)
                .frame(width: 44)

            // Label and Input
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                Text(subtitle)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
            }

            Spacer()

            // Input Field
            HStack(spacing: 4) {
                Text("₹")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(color.opacity(0.7))

                TextField("", text: $value)
                    .font(.system(size: 20, weight: .bold, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 80)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(color.opacity(0.1))
                    .overlay {
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(isFocused ? color : color.opacity(0.3), lineWidth: isFocused ? 2 : 1)
                    }
            }
        }
        .padding(10)
        .background {
            RoundedRectangle(cornerRadius: 14)
                .fill(Theme.surfaceElevated.opacity(0.5))
        }
    }
}

// MARK: - Preset Buttons Row

struct PresetButtonsRow: View {
    let baseValue: Double
    let isCall: Bool
    let isTarget: Bool
    let onSelect: (Double) -> Void

    // Presets for target (positive for call, negative for put)
    // Presets for stop-loss (negative for call, positive for put)
    private var presets: [Int] {
        if isTarget {
            // Target presets
            return isCall ? [100, 200, 300, 500] : [-100, -200, -300, -500]
        } else {
            // Stop-loss presets
            return isCall ? [-100, -150, -200, -300] : [100, 150, 200, 300]
        }
    }

    private var color: Color {
        isTarget ? Theme.profit : Theme.loss
    }

    var body: some View {
        HStack(spacing: 6) {
            Text(isTarget ? "Quick:" : "Quick:")
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)

            ForEach(presets, id: \.self) { preset in
                PresetButton(
                    label: formatPreset(preset),
                    color: color,
                    action: {
                        let impact = UIImpactFeedbackGenerator(style: .light)
                        impact.impactOccurred()
                        onSelect(baseValue + Double(preset))
                    }
                )
            }

            Spacer()
        }
        .padding(.horizontal, 2)
    }

    private func formatPreset(_ value: Int) -> String {
        if value >= 0 {
            return "+\(value)"
        } else {
            return "\(value)"
        }
    }
}

struct PresetButton: View {
    let label: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                .foregroundColor(color)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(color.opacity(0.12))
                        .overlay {
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(color.opacity(0.25), lineWidth: 1)
                        }
                }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Simple Price Result Card

struct SimplePriceResultCard: View {
    let scenario: String
    let icon: String
    let optionPrice: Double
    let currentPrice: Double
    let color: Color

    private var priceDifference: Double {
        optionPrice - currentPrice
    }

    private var percentChange: Double {
        guard currentPrice > 0 else { return 0 }
        return (priceDifference / currentPrice) * 100
    }

    var body: some View {
        HStack(spacing: 12) {
            // Icon
            Image(systemName: icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundColor(color)
                .frame(width: 40, height: 40)
                .background {
                    Circle()
                        .fill(color.opacity(0.15))
                }

            // Scenario Label
            VStack(alignment: .leading, spacing: 2) {
                Text(scenario)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                Text(L.calculatorOptionPremium)
                    .font(.system(size: 10))
                    .foregroundColor(Theme.textMuted)
            }

            Spacer()

            // Price and Change
            VStack(alignment: .trailing, spacing: 2) {
                Text("₹\(String(format: "%.2f", optionPrice))")
                    .font(.system(size: 20, weight: .heavy, design: .rounded))
                    .foregroundColor(color)

                HStack(spacing: 4) {
                    Image(systemName: priceDifference >= 0 ? "arrow.up.right" : "arrow.down.right")
                        .font(.system(size: 10, weight: .bold))
                    Text("\(priceDifference >= 0 ? "+" : "")\(String(format: "%.0f", priceDifference)) (\(String(format: "%.0f", percentChange))%)")
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundColor(color.opacity(0.8))
            }
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 14)
                .fill(color.opacity(0.08))
                .overlay {
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(color.opacity(0.2), lineWidth: 1)
                }
        }
    }
}

struct TargetInputField: View {
    let label: String
    @Binding var value: String
    let color: Color
    var isFocused: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(color)

            TextField("", text: $value)
                .font(.system(size: 16, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
                .keyboardType(.decimalPad)
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .background {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(color.opacity(0.1))
                        .overlay {
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(isFocused ? color : color.opacity(0.3), lineWidth: isFocused ? 1.5 : 1)
                        }
                }
        }
    }
}

struct EnhancedTargetResultRow: View {
    let label: String
    let icon: String
    let spotValue: Double
    let optionPrice: String
    let pnl: String
    let pnlPercent: String
    let isProfit: Bool

    private var color: Color {
        isProfit ? Theme.profit : Theme.loss
    }

    var body: some View {
        HStack(spacing: 12) {
            // Icon
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(color)
                .frame(width: 36, height: 36)
                .background {
                    Circle()
                        .fill(color.opacity(0.15))
                }

            // Details
            VStack(alignment: .leading, spacing: 4) {
                Text("\(label) @ \(String(format: "%.0f", spotValue))")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Theme.textMuted)

                Text("₹\(optionPrice)")
                    .font(.system(size: 18, weight: .bold, design: .monospaced))
                    .foregroundColor(color)
            }

            Spacer()

            // P&L
            VStack(alignment: .trailing, spacing: 4) {
                Text(pnl)
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundColor(color)

                Text(pnlPercent)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(color.opacity(0.8))
            }
        }
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(color.opacity(0.08))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(color.opacity(0.15), lineWidth: 1)
                }
        }
    }
}

struct RiskRewardBar: View {
    let ratio: Double
    let isGood: Bool

    var body: some View {
        HStack(spacing: 12) {
            // Visual bar
            GeometryReader { geo in
                HStack(spacing: 2) {
                    // Risk portion
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Theme.loss)
                        .frame(width: geo.size.width * 0.4)

                    // Reward portion
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Theme.profit)
                        .frame(width: geo.size.width * 0.6 * min(ratio, 3) / 3)

                    Spacer(minLength: 0)
                }
            }
            .frame(height: 8)

            // Label
            HStack(spacing: 6) {
                Text("R:R")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Theme.textSecondary)

                Text(String(format: "1:%.1f", ratio))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(isGood ? Theme.profit : Theme.loss)

                Image(systemName: isGood ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                    .font(.system(size: 14))
                    .foregroundColor(isGood ? Theme.profit : Theme.accentOrange)
            }
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 10)
                .fill(Theme.card)
        }
    }
}

// MARK: - Risk Disclaimer Banner

struct RiskDisclaimerBanner: View {
    @Binding var hideDisclaimer: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.accentOrange)

            Text(L.calculatorRiskDisclaimerText)
                .font(.system(size: 11))
                .foregroundColor(Theme.accentOrange)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)

            Button {
                withAnimation(.easeOut(duration: 0.2)) {
                    hideDisclaimer = true
                }
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(Theme.accentOrange)
                    .padding(4)
            }
        }
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(Theme.accentOrange.opacity(0.1))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Theme.accentOrange.opacity(0.2), lineWidth: 1)
                }
        }
    }
}

// MARK: - Labeled Input Field With Info Icon

struct LabeledInputFieldWithInfo: View {
    let label: String
    @Binding var value: String
    let icon: String
    var isFocused: Bool = false
    @Binding var showInfo: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                Text(label)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Theme.textMuted)

                Button {
                    showInfo = true
                } label: {
                    Image(systemName: "info.circle")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)
                }
            }

            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundColor(isFocused ? Theme.primaryBlue : Theme.textMuted)
                    .frame(width: 20)

                TextField("", text: $value)
                    .font(.system(size: 16, weight: .semibold, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)
                    .keyboardType(.decimalPad)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Theme.card)
                    .overlay {
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(isFocused ? Theme.primaryBlue : Color.white.opacity(0.06), lineWidth: isFocused ? 1.5 : 1)
                    }
            }
        }
    }
}

// MARK: - IV Educational Sheet

struct IVEducationalSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Header
                        HStack(spacing: 10) {
                            Image(systemName: "waveform.path.ecg")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(Theme.accentPurple)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(L.calculatorIVEducationTitle)
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(Theme.textPrimary)
                                Text(L.calculatorIVEducationText)
                                    .font(.system(size: 13))
                                    .foregroundColor(Theme.textSecondary)
                            }
                        }

                        // Warning card
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 14))
                                .foregroundColor(Theme.accentOrange)

                            Text("IV crush after events (budget, RBI policy) can cause losses even when direction is correct.")
                                .font(.system(size: 13))
                                .foregroundColor(Theme.accentOrange)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(12)
                        .background {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Theme.accentOrange.opacity(0.1))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Theme.accentOrange.opacity(0.2), lineWidth: 1)
                                }
                        }

                        // Key points
                        VStack(alignment: .leading, spacing: 12) {
                            IVInfoPoint(
                                icon: "arrow.up.right.circle.fill",
                                title: L.calculatorHighIVExpensive,
                                description: L.calculatorHighIVExpensiveDesc,
                                color: Theme.loss
                            )

                            IVInfoPoint(
                                icon: "arrow.down.right.circle.fill",
                                title: L.calculatorLowIVCheap,
                                description: L.calculatorLowIVCheapDesc,
                                color: Theme.profit
                            )

                            IVInfoPoint(
                                icon: "calendar.badge.exclamationmark",
                                title: L.calculatorEventDrivenIV,
                                description: L.calculatorEventDrivenIVDesc,
                                color: Theme.accentPurple
                            )
                        }
                    }
                    .padding(20)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22))
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
    }
}

struct IVInfoPoint: View {
    let icon: String
    let title: String
    let description: String
    let color: Color

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(color)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                Text(description)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            RoundedRectangle(cornerRadius: 12)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.05), lineWidth: 1)
                }
        }
    }
}

// MARK: - Paper Trade Button

struct PaperTradeButton: View {
    @ObservedObject var viewModel: CalculatorViewModel
    @ObservedObject var paperTradingVM: PaperTradingViewModel

    var body: some View {
        Button {
            executePaperTrade()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "cart.badge.plus")
                    .font(.system(size: 16, weight: .bold))

                Text(L.paperTradeBuy)
                    .font(.system(size: 15, weight: .bold))

                Spacer()

                if let calc = viewModel.targetCalculation {
                    Text("₹\(String(format: "%.2f", calc.currentOptionPrice))")
                        .font(.system(size: 14, weight: .semibold, design: .monospaced))
                }
            }
            .foregroundColor(Theme.textPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.profit)
                    .shadow(color: Theme.profit.opacity(0.3), radius: 8, x: 0, y: 4)
            }
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $paperTradingVM.showTradeSheet) {
            TradeExecutionSheet(viewModel: paperTradingVM)
        }
    }

    private func executePaperTrade() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()

        // Check authentication - show login sheet if not logged in
        let authManager = AuthManager.shared
        guard authManager.requireAuth(action: { [self] in
            // This will be called after successful login
            self.performTrade()
        }) else {
            // User not authenticated, login sheet will be shown
            return
        }

        // User is authenticated, proceed with trade
        performTrade()
    }

    private func performTrade() {
        // Create trade request from calculator data
        guard let strikePrice = Double(viewModel.strikePrice),
              let currentPrice = viewModel.targetCalculation?.currentOptionPrice,
              currentPrice > 0 else {
            return
        }

        // Determine the index based on spot price (simplified - assume NIFTY for now)
        let index: TradingIndex = viewModel.spotPriceValue > 40000 ? .bankNifty : .nifty50

        // Create expiry date from days to expiry
        let daysToExpiry = Int(viewModel.daysToExpiry) ?? 0
        let expiryDate = Calendar.current.date(byAdding: .day, value: daysToExpiry, to: Date()) ?? Date()

        let request = TradeExecutionRequest(
            index: index,
            strikePrice: strikePrice,
            optionType: viewModel.optionType,
            direction: .buy,
            price: currentPrice,
            quantity: 1,
            expiryDate: expiryDate,
            underlyingPrice: viewModel.spotPriceValue
        )

        paperTradingVM.tradeRequest = request
        paperTradingVM.showTradeSheet = true
    }
}

// MARK: - Preview

#Preview {
    CalculatorView(viewModel: CalculatorViewModel(), paperTradingVM: PaperTradingViewModel())
}
