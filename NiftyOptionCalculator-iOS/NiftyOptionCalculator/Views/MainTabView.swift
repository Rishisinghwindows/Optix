import SwiftUI

struct MainTabView: View {
    @StateObject private var optionChainVM = OptionChainViewModel()
    @StateObject private var calculatorVM = CalculatorViewModel()
    @StateObject private var paperTradingVM = PaperTradingViewModel()
    @StateObject private var alertManager = AlertManager.shared
    @EnvironmentObject var themeConfig: ThemeConfiguration
    @ObservedObject private var localization = LocalizationManager.shared
    @State private var selectedTab = 0
    @State private var lastCalculatedSpotPrice: Double = 0
    @State private var showNotificationCenter = false
    @ObservedObject private var signalStore = MarketSignalStore.shared

    var body: some View {
        ZStack(alignment: .bottom) {
            // Background gradient
            Theme.backgroundGradient
                .ignoresSafeArea()

            // Content
            TabView(selection: $selectedTab) {
                OptionChainView(viewModel: optionChainVM, paperTradingVM: paperTradingVM, calculatorVM: calculatorVM)
                    .tag(0)
                    .id("oc-\(themeConfig.version)-\(localization.version)")

                AIInsightsView(optionChainViewModel: optionChainVM)
                    .tag(1)
                    .id("ai-\(themeConfig.version)-\(localization.version)")

                PaperTradeDashboardView(optionChainVM: optionChainVM, viewModel: paperTradingVM)
                    .environmentObject(themeConfig)
                    .tag(2)
                    .id("paper-\(themeConfig.version)-\(localization.version)")

                IPODashboardView()
                    .environmentObject(themeConfig)
                    .tag(3)
                    .id("ipo-\(themeConfig.version)-\(localization.version)")

                SettingsView(viewModel: optionChainVM)
                    .environmentObject(themeConfig)
                    .tag(4)
                    .id("settings-\(themeConfig.version)-\(localization.version)")
            }

            // Custom Tab Bar
            CustomTabBar(selectedTab: $selectedTab)

            // Floating AI Chat Button (draggable)
            FloatingChatButton()

            // Notification Bell Button (top-right)
            NotificationBellButton(
                unreadCount: signalStore.unreadCount + alertManager.unreadCount,
                action: { showNotificationCenter = true }
            )
        }
        .overlay(alignment: .top) {
            AlertBannerOverlay(alertManager: alertManager)
        }
        .sheet(isPresented: $showNotificationCenter) {
            NavigationStack {
                NotificationCenterView()
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button("Done") { showNotificationCenter = false }
                                .foregroundColor(Theme.primaryBlue)
                        }
                    }
            }
        }
        .onChange(of: selectedTab) { _, newTab in
            let screenNames = ["option_chain", "ai_insights", "paper_trading", "ipo_dashboard", "settings"]
            if newTab >= 0 && newTab < screenNames.count {
                AnalyticsService.logScreenView(screenName: screenNames[newTab], screenClass: "MainTabView")
            }
        }
        .onChange(of: optionChainVM.spotPrice) { _, newPrice in
            guard newPrice > 0 else { return }
            calculatorVM.spotPrice = String(format: "%.0f", newPrice)
            // Debounce: only recalculate if price changed by more than 1.0 point
            guard abs(newPrice - lastCalculatedSpotPrice) >= 1.0 else { return }
            lastCalculatedSpotPrice = newPrice
            // Auto-fetch LTP when spot price updates
            fetchCurrentLTP()
            calculatorVM.calculateAll()
        }
        .onChange(of: calculatorVM.strikePrice) { _, _ in
            // Auto-fetch LTP when strike price changes
            fetchCurrentLTP()
        }
        .onChange(of: calculatorVM.optionType) { _, _ in
            // Auto-fetch LTP when option type changes
            fetchCurrentLTP()
        }
        .onChange(of: optionChainVM.dataVersion) { _, _ in
            // Update paper trading positions when option chain updates
            paperTradingVM.updatePositionPrices(
                from: optionChainVM.optionChain,
                spotPrice: optionChainVM.spotPrice
            )

            // Evaluate Smart Alerts on each data refresh
            let alerts = MarketAlertService.shared.evaluate(
                optionChain: optionChainVM.optionChain,
                spotPrice: optionChainVM.spotPrice,
                pcr: optionChainVM.putCallRatio,
                vix: optionChainVM.indiaVix,
                maxPain: optionChainVM.maxPainStrike
            )
            if !alerts.isEmpty {
                alertManager.process(alerts)
            }
        }
        .environmentObject(paperTradingVM)
    }

    /// Auto-fetch current LTP from option chain based on strike price and option type
    private func fetchCurrentLTP() {
        guard let strike = Double(calculatorVM.strikePrice),
              !optionChainVM.optionChain.isEmpty else { return }

        // Find matching row in option chain
        if let row = optionChainVM.optionChain.first(where: { $0.strikePrice == strike }) {
            let option = calculatorVM.optionType == .call ? row.callOption : row.putOption
            if let ltp = option?.lastTradedPrice, ltp > 0 {
                calculatorVM.currentOptionPrice = String(format: "%.2f", ltp)
            }
        }
    }
}

// MARK: - Custom Tab Bar

struct CustomTabBar: View {
    @Binding var selectedTab: Int
    @Namespace private var animation

    var body: some View {
        HStack(spacing: 0) {
            TabBarButton(
                icon: "chart.bar.doc.horizontal",
                label: L.tabOptionChain,
                isSelected: selectedTab == 0,
                namespace: animation
            ) {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                    selectedTab = 0
                }
            }

            TabBarButton(
                icon: "brain.head.profile",
                label: L.tabAIInsights,
                isSelected: selectedTab == 1,
                namespace: animation,
                accentColor: Color(hex: "BF5AF2")
            ) {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                    selectedTab = 1
                }
            }

            TabBarButton(
                icon: "indianrupeesign.circle.fill",
                label: L.tabPaperTrade,
                isSelected: selectedTab == 2,
                namespace: animation,
                accentColor: Theme.accentOrange
            ) {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                    selectedTab = 2
                }
            }

            TabBarButton(
                icon: "chart.line.uptrend.xyaxis.circle.fill",
                label: "IPO",
                isSelected: selectedTab == 3,
                namespace: animation,
                accentColor: Color(hex: "3B82F6")
            ) {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                    selectedTab = 3
                }
            }

            TabBarButton(
                icon: "gearshape.fill",
                label: L.tabSettings,
                isSelected: selectedTab == 4,
                namespace: animation,
                accentColor: Theme.textSecondary
            ) {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                    selectedTab = 4
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background {
            RoundedRectangle(cornerRadius: 28)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 28)
                        .stroke(Theme.border.opacity(0.5), lineWidth: 1)
                }
                .shadow(color: Theme.shadowColor, radius: 20, x: 0, y: 10)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 0)
    }
}

struct TabBarButton: View {
    let icon: String
    let label: String
    let isSelected: Bool
    var namespace: Namespace.ID
    var accentColor: Color = Theme.accentGreen
    let action: () -> Void

    var body: some View {
        Button(action: {
            let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
            impactFeedback.impactOccurred()
            action()
        }) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 22, weight: isSelected ? .semibold : .regular))
                    .symbolEffect(.bounce.up, value: isSelected)

                Text(label)
                    .font(.system(size: 10, weight: isSelected ? .bold : .medium))
            }
            .foregroundStyle(isSelected ? accentColor : Theme.textMuted.opacity(0.7))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background {
                if isSelected {
                    Capsule()
                        .fill(accentColor.opacity(0.18))
                        .overlay {
                            Capsule()
                                .stroke(accentColor.opacity(0.3), lineWidth: 1)
                        }
                        .matchedGeometryEffect(id: "activeTab", in: namespace)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Theme System (Multi-Theme Support)

enum Theme {
    private static var current: ThemePreset { ThemeConfiguration.shared.preset }

    // Core Background Colors
    static var background: Color { current.background }
    static var surface: Color { current.surface }
    static var surfaceElevated: Color { current.surfaceElevated }
    static var card: Color { current.card }
    static var cardHover: Color { current.cardHover }

    // Primary Colors
    static var primaryBlue: Color { current.primaryBlue }
    static var primaryBlueLight: Color { current.primaryBlueLight }

    // Semantic Colors
    static var profit: Color { current.profit }
    static var profitLight: Color { current.profitLight }
    static var loss: Color { current.loss }
    static var lossLight: Color { current.lossLight }

    // Accent Colors
    static var accentGreen: Color { current.accentGreen }
    static var accentGreenLight: Color { current.accentGreenLight }
    static var accentRed: Color { current.accentRed }
    static var accentRedLight: Color { current.accentRedLight }
    static var accentBlue: Color { current.accentBlue }
    static var accentPurple: Color { current.accentPurple }
    static var accentOrange: Color { current.accentOrange }
    static var accentCyan: Color { current.accentCyan }
    static var accentYellow: Color { current.accentYellow }
    static var accentPink: Color { current.accentPink }

    // Text Colors
    static var textPrimary: Color { current.textPrimary }
    static var textSecondary: Color { current.textSecondary }
    static var textMuted: Color { current.textMuted }
    static var textDisabled: Color { current.textDisabled }

    // Border Colors
    static var border: Color { current.border }
    static var borderLight: Color { current.borderLight }

    // Shadow
    static var shadowColor: Color { current.shadowColor }

    // Glass Effects
    static var glassBorder: LinearGradient { current.glassBorder }
    static var glassOverlay: Color { current.glassOverlay }

    // Gradients
    static var backgroundGradient: LinearGradient { current.backgroundGradient }
    static var profitGradient: LinearGradient { current.profitGradient }
    static var lossGradient: LinearGradient { current.lossGradient }
    static var blueGradient: LinearGradient { current.blueGradient }
    static var purpleGradient: LinearGradient { current.purpleGradient }
    static var orangeGradient: LinearGradient { current.orangeGradient }
    static var cardGradient: LinearGradient { current.cardGradient }
    static var borderGradient: LinearGradient { current.borderGradient }
    static var shimmerGradient: LinearGradient { current.shimmerGradient }
    static var premiumBackgroundGradient: RadialGradient { current.premiumBackgroundGradient }

    // Legacy aliases for compatibility
    static var greenGradient: LinearGradient { profitGradient }
    static var redGradient: LinearGradient { lossGradient }

    // Chat component aliases
    static var accentPrimary: Color { accentCyan }
    static var accentSecondary: Color { accentBlue }
    static var backgroundPrimary: Color { background }
    static var backgroundSecondary: Color { surface }
    static var backgroundTertiary: Color { surfaceElevated }

    // MARK: - Sentiment Colors (for Chat)
    static var bullishPrimary: Color { Color(hex: "10B981") }
    static var bullishSecondary: Color { Color(hex: "34D399") }
    static var bearishPrimary: Color { Color(hex: "EF4444") }
    static var bearishSecondary: Color { Color(hex: "F87171") }

    static var bullishGradient: LinearGradient {
        LinearGradient(
            colors: [Color(hex: "059669"), Color(hex: "10B981"), Color(hex: "34D399")],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    static var bearishGradient: LinearGradient {
        LinearGradient(
            colors: [Color(hex: "DC2626"), Color(hex: "EF4444"), Color(hex: "F87171")],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    /// Get gradient based on market sentiment
    static func sentimentGradient(for sentiment: MarketSentiment) -> LinearGradient {
        switch sentiment {
        case .bullish: return bullishGradient
        case .bearish: return bearishGradient
        case .neutral:
            return LinearGradient(
                colors: [accentPrimary, accentSecondary],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }

    /// Get primary color based on market sentiment
    static func sentimentColor(for sentiment: MarketSentiment) -> Color {
        switch sentiment {
        case .bullish: return bullishPrimary
        case .bearish: return bearishPrimary
        case .neutral: return accentPrimary
        }
    }
}

// MARK: - Color Extension

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - Card Modifiers

struct GlassCard: ViewModifier {
    var cornerRadius: CGFloat = 16
    var padding: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(.ultraThinMaterial)
                    .overlay {
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .fill(Theme.glassOverlay)
                    }
                    .overlay {
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .stroke(Theme.glassBorder, lineWidth: 1)
                    }
            }
    }
}

struct SolidCard: ViewModifier {
    var cornerRadius: CGFloat = 16
    var padding: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                    }
            }
    }
}

struct ElevatedCard: ViewModifier {
    var cornerRadius: CGFloat = 16
    var padding: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(Theme.surfaceElevated)
                    .shadow(color: .black.opacity(0.4), radius: 16, x: 0, y: 8)
                    .overlay {
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .stroke(Color.white.opacity(0.04), lineWidth: 1)
                    }
            }
    }
}

struct NeumorphicCard: ViewModifier {
    var cornerRadius: CGFloat = 16
    var padding: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(Theme.surface)
                    .shadow(color: .black.opacity(0.5), radius: 8, x: 4, y: 4)
                    .shadow(color: Theme.surfaceElevated.opacity(0.3), radius: 8, x: -4, y: -4)
            }
    }
}

// MARK: - Input Field Style

struct ModernInputField: ViewModifier {
    var isActive: Bool = false

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Theme.card)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(isActive ? Theme.primaryBlue : Color.white.opacity(0.06), lineWidth: isActive ? 1.5 : 1)
                    }
            }
            .foregroundColor(Theme.textPrimary)
    }
}

// MARK: - View Extensions

extension View {
    func glassCard(cornerRadius: CGFloat = 16, padding: CGFloat = 16) -> some View {
        modifier(GlassCard(cornerRadius: cornerRadius, padding: padding))
    }

    func solidCard(cornerRadius: CGFloat = 16, padding: CGFloat = 16) -> some View {
        modifier(SolidCard(cornerRadius: cornerRadius, padding: padding))
    }

    func elevatedCard(cornerRadius: CGFloat = 16, padding: CGFloat = 16) -> some View {
        modifier(ElevatedCard(cornerRadius: cornerRadius, padding: padding))
    }

    func neumorphicCard(cornerRadius: CGFloat = 16, padding: CGFloat = 16) -> some View {
        modifier(NeumorphicCard(cornerRadius: cornerRadius, padding: padding))
    }

    func modernInput(isActive: Bool = false) -> some View {
        modifier(ModernInputField(isActive: isActive))
    }

    func glow(color: Color, radius: CGFloat = 8) -> some View {
        self
            .shadow(color: color.opacity(0.5), radius: radius, x: 0, y: 0)
            .shadow(color: color.opacity(0.3), radius: radius * 2, x: 0, y: 0)
    }
}

// MARK: - Shimmer Effect

struct ShimmerEffect: ViewModifier {
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { geo in
                    Theme.shimmerGradient
                        .frame(width: geo.size.width * 2)
                        .offset(x: -geo.size.width + phase * geo.size.width * 2)
                }
                .mask(content)
            }
            .onAppear {
                withAnimation(.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
    }
}

extension View {
    func shimmer() -> some View {
        modifier(ShimmerEffect())
    }
}

// MARK: - Sparkline View (Enhanced)

struct SparklineView: View {
    let data: [Double]
    let lineColor: Color
    let fillColor: Color
    var showGradient: Bool = true
    var animated: Bool = false

    @State private var animationProgress: CGFloat = 0

    init(data: [Double], isPositive: Bool = true, showGradient: Bool = true, animated: Bool = false) {
        self.data = data
        self.lineColor = isPositive ? Theme.profit : Theme.loss
        self.fillColor = isPositive ? Theme.profit.opacity(0.3) : Theme.loss.opacity(0.3)
        self.showGradient = showGradient
        self.animated = animated
    }

    var body: some View {
        GeometryReader { geo in
            let minVal = data.min() ?? 0
            let maxVal = data.max() ?? 1
            let range = max(maxVal - minVal, 0.001)
            let stepX = geo.size.width / CGFloat(max(data.count - 1, 1))

            ZStack {
                // Fill gradient
                if showGradient {
                    Path { path in
                        path.move(to: CGPoint(x: 0, y: geo.size.height))
                        for (index, value) in data.enumerated() {
                            let x = CGFloat(index) * stepX
                            let y = geo.size.height - CGFloat((value - minVal) / range) * geo.size.height
                            path.addLine(to: CGPoint(x: x, y: y))
                        }
                        path.addLine(to: CGPoint(x: geo.size.width, y: geo.size.height))
                        path.closeSubpath()
                    }
                    .fill(
                        LinearGradient(
                            colors: [fillColor, fillColor.opacity(0)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                }

                // Line
                Path { path in
                    for (index, value) in data.enumerated() {
                        let x = CGFloat(index) * stepX
                        let y = geo.size.height - CGFloat((value - minVal) / range) * geo.size.height
                        if index == 0 {
                            path.move(to: CGPoint(x: x, y: y))
                        } else {
                            path.addLine(to: CGPoint(x: x, y: y))
                        }
                    }
                }
                .trim(from: 0, to: animated ? animationProgress : 1)
                .stroke(lineColor, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))

                // End point indicator
                if let lastValue = data.last {
                    let lastX = geo.size.width
                    let lastY = geo.size.height - CGFloat((lastValue - minVal) / range) * geo.size.height

                    Circle()
                        .fill(lineColor)
                        .frame(width: 6, height: 6)
                        .position(x: lastX, y: lastY)
                        .opacity(animated ? Double(animationProgress) : 1)
                }
            }
        }
        .onAppear {
            if animated {
                withAnimation(.easeOut(duration: 1.0)) {
                    animationProgress = 1
                }
            }
        }
    }
}

// MARK: - Progress Bar View

struct HorizontalProgressBar: View {
    let value: Double
    let color: Color
    let height: CGFloat
    var animated: Bool = true

    @State private var animatedValue: Double = 0

    init(value: Double, color: Color = Theme.profit, height: CGFloat = 4, animated: Bool = true) {
        self.value = min(max(value, 0), 1)
        self.color = color
        self.height = height
        self.animated = animated
    }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: height / 2)
                    .fill(color.opacity(0.15))

                RoundedRectangle(cornerRadius: height / 2)
                    .fill(color)
                    .frame(width: geo.size.width * (animated ? animatedValue : value))
            }
        }
        .frame(height: height)
        .onAppear {
            if animated {
                withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                    animatedValue = value
                }
            }
        }
        .onChange(of: value) { _, newValue in
            if animated {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    animatedValue = newValue
                }
            }
        }
    }
}

// MARK: - Badge View

struct BadgeView: View {
    let text: String
    let color: Color
    var size: BadgeSize = .regular

    enum BadgeSize {
        case small, regular, large

        var fontSize: CGFloat {
            switch self {
            case .small: return 9
            case .regular: return 10
            case .large: return 12
            }
        }

        var horizontalPadding: CGFloat {
            switch self {
            case .small: return 6
            case .regular: return 8
            case .large: return 10
            }
        }

        var verticalPadding: CGFloat {
            switch self {
            case .small: return 3
            case .regular: return 4
            case .large: return 5
            }
        }
    }

    var body: some View {
        Text(text)
            .font(.system(size: size.fontSize, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, size.horizontalPadding)
            .padding(.vertical, size.verticalPadding)
            .background {
                Capsule()
                    .fill(color.opacity(0.15))
            }
    }
}

// MARK: - Animated Number

struct AnimatedNumber: View {
    let value: Double
    let format: String
    let color: Color
    var prefix: String = ""
    var suffix: String = ""

    @State private var animatedValue: Double = 0

    var body: some View {
        HStack(spacing: 0) {
            if !prefix.isEmpty {
                Text(prefix)
            }
            Text(String(format: format, animatedValue))
                .contentTransition(.numericText())
            if !suffix.isEmpty {
                Text(suffix)
            }
        }
        .foregroundColor(color)
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                animatedValue = value
            }
        }
        .onChange(of: value) { _, newValue in
            withAnimation(.easeOut(duration: 0.4)) {
                animatedValue = newValue
            }
        }
    }
}

// MARK: - Pulse Animation

struct PulseAnimation: ViewModifier {
    @State private var isPulsing = false

    func body(content: Content) -> some View {
        content
            .scaleEffect(isPulsing ? 1.05 : 1.0)
            .opacity(isPulsing ? 0.85 : 1.0)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                    isPulsing = true
                }
            }
    }
}

extension View {
    func pulse() -> some View {
        modifier(PulseAnimation())
    }
}

// MARK: - Probability Curve View (tastytrade-inspired)

struct ProbabilityCurveView: View {
    let currentPrice: Double
    let strikePrice: Double
    let standardDeviation: Double
    var profitZoneStart: Double? = nil
    var profitZoneEnd: Double? = nil

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let height = geo.size.height
            let range = standardDeviation * 4
            let minPrice = currentPrice - range
            let maxPrice = currentPrice + range

            ZStack {
                // Bell curve
                Path { path in
                    let points = 100
                    for i in 0...points {
                        let x = CGFloat(i) / CGFloat(points) * width
                        let price = minPrice + (maxPrice - minPrice) * Double(i) / Double(points)
                        let z = (price - currentPrice) / standardDeviation
                        let y = height - (exp(-z * z / 2) * height * 0.8)

                        if i == 0 {
                            path.move(to: CGPoint(x: x, y: y))
                        } else {
                            path.addLine(to: CGPoint(x: x, y: y))
                        }
                    }
                }
                .stroke(Theme.textMuted, lineWidth: 1.5)

                // Profit zone fill
                if let start = profitZoneStart, let end = profitZoneEnd {
                    Path { path in
                        let startX = CGFloat((start - minPrice) / (maxPrice - minPrice)) * width
                        let endX = CGFloat((end - minPrice) / (maxPrice - minPrice)) * width
                        let points = 50

                        path.move(to: CGPoint(x: startX, y: height))

                        for i in 0...points {
                            let x = startX + (endX - startX) * CGFloat(i) / CGFloat(points)
                            let price = start + (end - start) * Double(i) / Double(points)
                            let z = (price - currentPrice) / standardDeviation
                            let y = height - (exp(-z * z / 2) * height * 0.8)
                            path.addLine(to: CGPoint(x: x, y: y))
                        }

                        path.addLine(to: CGPoint(x: endX, y: height))
                        path.closeSubpath()
                    }
                    .fill(Theme.profit.opacity(0.3))
                }

                // Strike price line
                let strikeX = CGFloat((strikePrice - minPrice) / (maxPrice - minPrice)) * width
                Path { path in
                    path.move(to: CGPoint(x: strikeX, y: 0))
                    path.addLine(to: CGPoint(x: strikeX, y: height))
                }
                .stroke(Theme.accentOrange, style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))

                // Current price line
                let currentX = width / 2
                Path { path in
                    path.move(to: CGPoint(x: currentX, y: 0))
                    path.addLine(to: CGPoint(x: currentX, y: height))
                }
                .stroke(Theme.primaryBlue, lineWidth: 2)

                // Labels
                VStack {
                    Spacer()
                    HStack {
                        Text(String(format: "%.0f", minPrice))
                            .font(.system(size: 9))
                            .foregroundColor(Theme.textMuted)
                        Spacer()
                        Text(String(format: "%.0f", currentPrice))
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundColor(Theme.primaryBlue)
                        Spacer()
                        Text(String(format: "%.0f", maxPrice))
                            .font(.system(size: 9))
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
    }
}

// MARK: - P&L Visualization

struct PLVisualizationView: View {
    let maxProfit: Double
    let maxLoss: Double
    let currentPL: Double
    var breakeven: Double? = nil

    var body: some View {
        VStack(spacing: 12) {
            // P&L Bar
            GeometryReader { geo in
                let total = abs(maxProfit) + abs(maxLoss)
                let profitWidth = total > 0 ? (abs(maxProfit) / total) * geo.size.width : geo.size.width / 2
                let lossWidth = total > 0 ? (abs(maxLoss) / total) * geo.size.width : geo.size.width / 2

                HStack(spacing: 2) {
                    // Loss zone
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Theme.loss.opacity(0.3))
                        .frame(width: lossWidth)
                        .overlay(alignment: .leading) {
                            Text(String(format: "-₹%.0f", abs(maxLoss)))
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Theme.loss)
                                .padding(.leading, 4)
                        }

                    // Profit zone
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Theme.profit.opacity(0.3))
                        .frame(width: profitWidth)
                        .overlay(alignment: .trailing) {
                            Text(String(format: "+₹%.0f", maxProfit))
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Theme.profit)
                                .padding(.trailing, 4)
                        }
                }

                // Current P&L indicator
                let indicatorPosition = total > 0 ? ((currentPL + abs(maxLoss)) / total) * geo.size.width : geo.size.width / 2

                VStack(spacing: 0) {
                    Triangle()
                        .fill(currentPL >= 0 ? Theme.profit : Theme.loss)
                        .frame(width: 10, height: 6)

                    Rectangle()
                        .fill(currentPL >= 0 ? Theme.profit : Theme.loss)
                        .frame(width: 2, height: geo.size.height - 6)
                }
                .position(x: min(max(indicatorPosition, 10), geo.size.width - 10), y: geo.size.height / 2)
            }
            .frame(height: 32)

            // Current P&L value
            HStack {
                Text("Current P&L")
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textSecondary)
                Spacer()
                Text(String(format: "%@₹%.0f", currentPL >= 0 ? "+" : "", currentPL))
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundColor(currentPL >= 0 ? Theme.profit : Theme.loss)
            }

            // Breakeven
            if let be = breakeven {
                HStack {
                    Text("Breakeven")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textSecondary)
                    Spacer()
                    Text(String(format: "₹%.0f", be))
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                }
            }
        }
    }
}

struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

// MARK: - Probability of Profit Badge

struct POPBadge: View {
    let probability: Double // 0-100

    var color: Color {
        if probability >= 70 { return Theme.profit }
        if probability >= 50 { return Theme.accentOrange }
        return Theme.loss
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "percent")
                .font(.system(size: 10, weight: .bold))

            Text(String(format: "%.0f%% POP", probability))
                .font(.system(size: 11, weight: .bold))
        }
        .foregroundColor(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background {
            Capsule()
                .fill(color.opacity(0.15))
                .overlay {
                    Capsule()
                        .stroke(color.opacity(0.3), lineWidth: 1)
                }
        }
    }
}

// MARK: - Risk Meter

struct RiskMeter: View {
    let riskLevel: Double // 0-100

    var riskColor: Color {
        if riskLevel <= 30 { return Theme.profit }
        if riskLevel <= 60 { return Theme.accentOrange }
        return Theme.loss
    }

    var riskLabel: String {
        if riskLevel <= 30 { return "Low" }
        if riskLevel <= 60 { return "Medium" }
        return "High"
    }

    var body: some View {
        VStack(spacing: 8) {
            // Arc meter
            ZStack {
                // Background arc
                Circle()
                    .trim(from: 0.25, to: 0.75)
                    .stroke(Theme.card, lineWidth: 8)
                    .rotationEffect(.degrees(90))

                // Value arc
                Circle()
                    .trim(from: 0.25, to: 0.25 + (0.5 * riskLevel / 100))
                    .stroke(riskColor, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(90))

                // Center text
                VStack(spacing: 2) {
                    Text(String(format: "%.0f", riskLevel))
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(riskColor)

                    Text(riskLabel)
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(Theme.textMuted)
                }
            }
            .frame(width: 80, height: 50)
        }
    }
}

// MARK: - Live Indicator

struct LiveIndicator: View {
    @State private var isAnimating = false

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(Theme.profit)
                .frame(width: 8, height: 8)
                .scaleEffect(isAnimating ? 1.2 : 0.8)
                .opacity(isAnimating ? 0.6 : 1.0)

            Text("LIVE")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(Theme.profit)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background {
            Capsule()
                .fill(Theme.profit.opacity(0.15))
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }
}

// MARK: - Skeleton Loading

struct SkeletonView: View {
    var height: CGFloat = 16
    var cornerRadius: CGFloat = 4

    @State private var isAnimating = false

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(Theme.card)
            .frame(height: height)
            .overlay {
                GeometryReader { geo in
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .fill(
                            LinearGradient(
                                colors: [.clear, Color.white.opacity(0.1), .clear],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .offset(x: isAnimating ? geo.size.width : -geo.size.width)
                }
                .mask(RoundedRectangle(cornerRadius: cornerRadius))
            }
            .onAppear {
                withAnimation(.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                    isAnimating = true
                }
            }
    }
}

// MARK: - Trend Arrow

struct TrendArrow: View {
    let change: Double
    var size: CGFloat = 12

    var body: some View {
        Image(systemName: change >= 0 ? "arrow.up.right" : "arrow.down.right")
            .font(.system(size: size, weight: .bold))
            .foregroundColor(change >= 0 ? Theme.profit : Theme.loss)
    }
}

// MARK: - Preview

#Preview {
    MainTabView()
}
