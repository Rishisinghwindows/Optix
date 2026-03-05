import SwiftUI

// MARK: - Page Data

private struct OnboardingPageData {
    let title: String
    let subtitle: String
    let accentColor: Color
    let accentGradient: [Color]
    let features: [(icon: String, text: String, color: Color)]
}

private var onboardingPages: [OnboardingPageData] {
    [
        OnboardingPageData(
            title: L.onboarding1Title,
            subtitle: L.onboarding1Subtitle,
            accentColor: Theme.profit,
            accentGradient: [Theme.profit, Theme.profitLight],
            features: [
                ("chart.line.uptrend.xyaxis", L.onboarding1Feature1, Theme.profit),
                ("bolt.fill", L.onboarding1Feature2, Theme.accentOrange),
                ("indianrupeesign.circle.fill", L.onboarding1Feature3, Theme.primaryBlue),
            ]
        ),
        OnboardingPageData(
            title: L.onboarding2Title,
            subtitle: L.onboarding2Subtitle,
            accentColor: Theme.accentOrange,
            accentGradient: [Theme.accentOrange, Color(hex: "FFAB40")],
            features: [
                ("bolt.fill", L.onboarding2Feature1, Theme.accentOrange),
                ("list.bullet.rectangle", L.onboarding2Feature2, Theme.profit),
                ("arrow.triangle.swap", L.onboarding2Feature3, Theme.primaryBlue),
            ]
        ),
        OnboardingPageData(
            title: L.onboarding3Title,
            subtitle: L.onboarding3Subtitle,
            accentColor: Theme.primaryBlue,
            accentGradient: [Theme.primaryBlue, Theme.primaryBlueLight],
            features: [
                ("function", L.onboarding3Feature1, Theme.primaryBlue),
                ("chart.pie.fill", L.onboarding3Feature2, Theme.accentPurple),
                ("waveform.path.ecg", L.onboarding3Feature3, Theme.accentOrange),
            ]
        ),
        OnboardingPageData(
            title: L.onboarding4Title,
            subtitle: L.onboarding4Subtitle,
            accentColor: Theme.accentPurple,
            accentGradient: [Theme.accentPurple, Color(hex: "B388FF")],
            features: [
                ("brain.head.profile", L.onboarding4Feature1, Theme.accentPurple),
                ("sparkles", L.onboarding4Feature2, Theme.accentCyan),
                ("target", L.onboarding4Feature3, Theme.accentOrange),
            ]
        ),
        OnboardingPageData(
            title: L.onboarding5Title,
            subtitle: L.onboarding5Subtitle,
            accentColor: Theme.profit,
            accentGradient: [Theme.profit, Theme.profitLight],
            features: [
                ("checkmark.shield.fill", L.onboarding5Feature1, Theme.profit),
                ("bolt.heart.fill", L.onboarding5Feature2, Theme.loss),
                ("star.fill", L.onboarding5Feature3, Theme.accentYellow),
            ]
        ),
    ]
}

// MARK: - Particle Field

private struct Particle {
    var x: CGFloat
    var y: CGFloat
    let radius: CGFloat
    let opacity: CGFloat
    let speed: CGFloat
    let phase: CGFloat
}

private struct ParticleFieldView: View {
    let accentColor: Color
    @State private var particles: [Particle] = []

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let time = timeline.date.timeIntervalSinceReferenceDate
                for i in particles.indices {
                    var p = particles[i]
                    // Upward drift
                    p.y -= p.speed * 0.3
                    if p.y < -10 {
                        p.y = size.height + 10
                    }
                    // Horizontal sine wobble
                    let drawX = p.x + sin(time * 0.5 + p.phase) * 20
                    let drawY = p.y

                    let rect = CGRect(
                        x: drawX - p.radius,
                        y: drawY - p.radius,
                        width: p.radius * 2,
                        height: p.radius * 2
                    )
                    context.opacity = p.opacity
                    context.fill(Circle().path(in: rect), with: .color(accentColor))
                }
            }
        }
        .onAppear {
            guard particles.isEmpty else { return }
            particles = (0..<40).map { _ in
                Particle(
                    x: CGFloat.random(in: 0...UIScreen.main.bounds.width),
                    y: CGFloat.random(in: 0...UIScreen.main.bounds.height),
                    radius: CGFloat.random(in: 2...5),
                    opacity: Double.random(in: 0.05...0.25),
                    speed: CGFloat.random(in: 0.3...1.2),
                    phase: CGFloat.random(in: 0...(.pi * 2))
                )
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Progress Bar

private struct OnboardingProgressBar: View {
    let currentPage: Int
    let totalPages: Int

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<totalPages, id: \.self) { index in
                Capsule()
                    .fill(
                        index <= currentPage
                            ? AnyShapeStyle(LinearGradient(
                                colors: onboardingPages[currentPage].accentGradient,
                                startPoint: .leading,
                                endPoint: .trailing
                            ))
                            : AnyShapeStyle(Theme.textMuted.opacity(0.3))
                    )
                    .frame(width: index == currentPage ? 28 : 14, height: 5)
                    .animation(.spring(response: 0.35, dampingFraction: 0.8), value: currentPage)
            }
        }
    }
}

// MARK: - Feature Row

private struct OnboardingFeatureRow: View {
    let icon: String
    let text: String
    let color: Color

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(color)
                .frame(width: 40, height: 40)
                .background {
                    Circle()
                        .fill(color.opacity(0.15))
                }

            Text(text)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Theme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background {
            RoundedRectangle(cornerRadius: 14)
                .fill(.ultraThinMaterial)
                .environment(\.colorScheme, .dark)
                .overlay {
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                }
        }
    }
}

// MARK: - Page 0 Hero: Animated Stock Chart

private struct HeroWelcomeView: View {
    @State private var chartTrim: CGFloat = 0
    @State private var fillOpacity: CGFloat = 0
    @State private var showDot = false
    @State private var showPrice = false
    @State private var floatPhase: CGFloat = 0

    private let dataPoints: [CGFloat] = [
        0.45, 0.42, 0.50, 0.48, 0.55, 0.53,
        0.60, 0.58, 0.65, 0.72, 0.70, 0.78
    ]

    var body: some View {
        ZStack {
            // Chart area
            chartBody
                .frame(width: 280, height: 180)

            // Floating mini-icons
            TimelineView(.animation) { timeline in
                let t = timeline.date.timeIntervalSinceReferenceDate
                ZStack {
                    floatingIcon("indianrupeesign", offset: CGPoint(x: -120, y: -60), time: t, phase: 0, color: Theme.accentOrange)
                    floatingIcon("arrow.up.right", offset: CGPoint(x: 120, y: -40), time: t, phase: 2, color: Theme.profit)
                    floatingIcon("chart.bar.fill", offset: CGPoint(x: -100, y: 70), time: t, phase: 4, color: Theme.primaryBlue)
                }
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 1.2)) {
                chartTrim = 1.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                withAnimation(.easeIn(duration: 0.5)) {
                    fillOpacity = 1.0
                }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) {
                    showDot = true
                }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                withAnimation(.easeOut(duration: 0.3)) {
                    showPrice = true
                }
            }
        }
    }

    private var chartBody: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let points = dataPoints.enumerated().map { i, v in
                CGPoint(x: w * CGFloat(i) / CGFloat(dataPoints.count - 1), y: h * (1 - v))
            }

            ZStack {
                // Gradient fill below line
                Path { path in
                    path.move(to: CGPoint(x: points[0].x, y: h))
                    for pt in points { path.addLine(to: pt) }
                    path.addLine(to: CGPoint(x: points.last!.x, y: h))
                    path.closeSubpath()
                }
                .fill(
                    LinearGradient(
                        colors: [Theme.profit.opacity(0.25), Theme.profit.opacity(0.0)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .opacity(fillOpacity)

                // Line chart
                Path { path in
                    path.move(to: points[0])
                    for pt in points.dropFirst() { path.addLine(to: pt) }
                }
                .trim(from: 0, to: chartTrim)
                .stroke(
                    LinearGradient(
                        colors: [Theme.profit, Theme.profitLight],
                        startPoint: .leading,
                        endPoint: .trailing
                    ),
                    style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)
                )

                // Pulsing dot at end
                if showDot, let last = points.last {
                    Circle()
                        .fill(Theme.profit)
                        .frame(width: 10, height: 10)
                        .shadow(color: Theme.profit.opacity(0.6), radius: 8)
                        .position(last)

                    // Price label
                    if showPrice {
                        Text("24,850")
                            .font(.system(size: 12, weight: .bold, design: .monospaced))
                            .foregroundColor(Theme.profit)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background {
                                Capsule()
                                    .fill(Theme.profit.opacity(0.15))
                            }
                            .position(x: last.x - 10, y: last.y - 20)
                            .transition(.opacity.combined(with: .scale))
                    }
                }
            }
        }
    }

    private func floatingIcon(_ name: String, offset: CGPoint, time: Double, phase: Double, color: Color) -> some View {
        Image(systemName: name)
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(color.opacity(0.6))
            .offset(
                x: offset.x,
                y: offset.y + sin(time * 1.2 + phase) * 8
            )
    }
}

// MARK: - Page 1 Hero: Option Chain Rows

private struct HeroOptionChainView: View {
    @State private var showHeaders = false
    @State private var rowAppeared: [Bool] = Array(repeating: false, count: 5)
    @State private var showATMGlow = false
    @State private var showLiveBadge = false

    private let strikes = ["24800", "24850", "24900", "24950", "25000"]
    private let cePrices = ["285.50", "245.80", "198.30", "156.40", "118.60"]
    private let pePrices = ["112.70", "148.50", "189.20", "234.60", "282.40"]

    var body: some View {
        VStack(spacing: 0) {
            // LIVE badge
            HStack {
                Spacer()
                if showLiveBadge {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Theme.loss)
                            .frame(width: 6, height: 6)
                        Text("LIVE")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(Theme.loss)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background {
                        Capsule()
                            .fill(Theme.loss.opacity(0.15))
                    }
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .frame(height: 24)
            .padding(.trailing, 4)

            // Headers
            HStack {
                Text("CE LTP")
                    .frame(maxWidth: .infinity)
                Divider().frame(height: 12)
                Text("Strike")
                    .frame(maxWidth: .infinity)
                Divider().frame(height: 12)
                Text("PE LTP")
                    .frame(maxWidth: .infinity)
            }
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(Theme.textMuted)
            .opacity(showHeaders ? 1 : 0)
            .padding(.bottom, 6)

            // Rows
            ForEach(0..<5, id: \.self) { index in
                let isATM = index == 2
                HStack(spacing: 0) {
                    // CE Price (slides from left)
                    Text(cePrices[index])
                        .font(.system(size: 13, weight: .semibold, design: .monospaced))
                        .foregroundColor(Theme.profit)
                        .frame(maxWidth: .infinity)
                        .offset(x: rowAppeared[index] ? 0 : -40)
                        .opacity(rowAppeared[index] ? 1 : 0)

                    // Strike (scales from center)
                    Text(strikes[index])
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)
                        .frame(maxWidth: .infinity)
                        .scaleEffect(rowAppeared[index] ? 1 : 0.5)
                        .opacity(rowAppeared[index] ? 1 : 0)

                    // PE Price (slides from right)
                    Text(pePrices[index])
                        .font(.system(size: 13, weight: .semibold, design: .monospaced))
                        .foregroundColor(Theme.loss)
                        .frame(maxWidth: .infinity)
                        .offset(x: rowAppeared[index] ? 0 : 40)
                        .opacity(rowAppeared[index] ? 1 : 0)
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 8)
                .background {
                    if isATM {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.accentOrange.opacity(showATMGlow ? 0.6 : 0), lineWidth: 1.5)
                            .background {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Theme.accentOrange.opacity(showATMGlow ? 0.08 : 0))
                            }
                    }
                }
            }
        }
        .frame(width: 280)
        .padding(12)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface.opacity(0.6))
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.06), lineWidth: 1)
                }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.3)) {
                showHeaders = true
            }
            for i in 0..<5 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2 + Double(i) * 0.12) {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                        rowAppeared[i] = true
                    }
                }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
                withAnimation(.easeInOut(duration: 0.4)) {
                    showATMGlow = true
                }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) {
                    showLiveBadge = true
                }
            }
        }
    }
}

// MARK: - Page 2 Hero: Calculator & Greeks Gauges

private struct HeroCalculatorGreeksView: View {
    @State private var gaugeProgress: CGFloat = 0
    @State private var priceValue: Double = 0
    @State private var showFormula = false

    private let greeks: [(name: String, value: CGFloat, color: Color)] = [
        ("Delta", 0.65, Theme.profit),
        ("Gamma", 0.35, Theme.primaryBlue),
        ("Theta", 0.50, Theme.loss),
        ("Vega", 0.45, Theme.accentPurple),
    ]

    private let priceTimer = Timer.publish(every: 0.02, on: .main, in: .common).autoconnect()
    @State private var priceAnimating = false

    var body: some View {
        VStack(spacing: 16) {
            // Price display
            Text("₹\(priceValue, specifier: "%.2f")")
                .font(.system(size: 32, weight: .black, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
                .contentTransition(.numericText())

            // 2x2 gauge grid
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                ForEach(0..<4, id: \.self) { index in
                    let greek = greeks[index]
                    gaugeView(name: greek.name, value: greek.value, color: greek.color)
                }
            }
            .frame(width: 240)

            // Formula
            if showFormula {
                Text("f(x) = S\u{00B7}N(d\u{2081}) - K\u{00B7}e\u{207B}\u{02B3}\u{1D40}\u{00B7}N(d\u{2082})")
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .foregroundColor(Theme.textMuted)
                    .transition(.opacity)
            }
        }
        .onReceive(priceTimer) { _ in
            guard priceAnimating, priceValue < 245.80 else { return }
            priceValue = min(priceValue + 3.2, 245.80)
        }
        .onAppear {
            priceAnimating = true
            withAnimation(.easeOut(duration: 1.0)) {
                gaugeProgress = 1.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                withAnimation(.easeOut(duration: 0.4)) {
                    showFormula = true
                }
            }
        }
    }

    private func gaugeView(name: String, value: CGFloat, color: Color) -> some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .trim(from: 0, to: 0.75)
                    .stroke(color.opacity(0.15), style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .rotationEffect(.degrees(135))

                Circle()
                    .trim(from: 0, to: 0.75 * value * gaugeProgress)
                    .stroke(color, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .rotationEffect(.degrees(135))

                Text("\(Int(value * 100 * gaugeProgress))")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(color)
            }
            .frame(width: 60, height: 60)

            Text(name)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
        }
    }
}

// MARK: - Page 3 Hero: Neural Network

private struct HeroAIInsightsView: View {
    @State private var edgeTrim: CGFloat = 0
    @State private var showOutputCard = false
    @State private var brainGlow: CGFloat = 0

    // Node positions (normalized 0-1 in a 280x200 space)
    private let inputNodes: [CGPoint] = [
        CGPoint(x: 0.05, y: 0.15),
        CGPoint(x: 0.05, y: 0.40),
        CGPoint(x: 0.05, y: 0.65),
        CGPoint(x: 0.05, y: 0.90),
    ]
    private let hiddenNodes: [CGPoint] = [
        CGPoint(x: 0.40, y: 0.10),
        CGPoint(x: 0.40, y: 0.30),
        CGPoint(x: 0.40, y: 0.50),
        CGPoint(x: 0.40, y: 0.70),
        CGPoint(x: 0.40, y: 0.90),
    ]
    private let outputNodes: [CGPoint] = [
        CGPoint(x: 0.75, y: 0.35),
        CGPoint(x: 0.75, y: 0.65),
    ]

    var body: some View {
        VStack(spacing: 16) {
            // Brain icon
            Image(systemName: "brain.head.profile")
                .font(.system(size: 28, weight: .semibold))
                .foregroundColor(Theme.accentPurple)
                .shadow(color: Theme.accentPurple.opacity(brainGlow), radius: 12)

            // Neural network
            ZStack {
                networkCanvas
                    .frame(width: 280, height: 180)

                // Pulse dots traveling along edges
                TimelineView(.animation) { timeline in
                    let t = timeline.date.timeIntervalSinceReferenceDate
                    Canvas { context, size in
                        guard edgeTrim > 0.5 else { return }
                        let frac = t.truncatingRemainder(dividingBy: 2.0) / 2.0
                        // Draw a few traveling dots on input->hidden edges
                        for inp in inputNodes {
                            let hid = hiddenNodes[2] // middle hidden
                            let px = lerp(inp.x * size.width, hid.x * size.width, CGFloat(frac))
                            let py = lerp(inp.y * size.height, hid.y * size.height, CGFloat(frac))
                            let rect = CGRect(x: px - 2, y: py - 2, width: 4, height: 4)
                            context.opacity = 0.8
                            context.fill(Circle().path(in: rect), with: .color(Theme.accentCyan))
                        }
                    }
                }
                .frame(width: 280, height: 180)
                .allowsHitTesting(false)
            }

            // Output card
            if showOutputCard {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.up.circle.fill")
                        .foregroundColor(Theme.profit)
                    Text("Bullish")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Theme.profit)
                    Text("·")
                        .foregroundColor(Theme.textMuted)
                    Text("Buy CE")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background {
                    Capsule()
                        .fill(Theme.profit.opacity(0.12))
                        .overlay {
                            Capsule()
                                .stroke(Theme.profit.opacity(0.3), lineWidth: 1)
                        }
                }
                .transition(.scale.combined(with: .opacity))
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 1.4)) {
                edgeTrim = 1.0
            }
            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                brainGlow = 0.6
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) {
                    showOutputCard = true
                }
            }
        }
    }

    private var networkCanvas: some View {
        Canvas { context, size in
            let nodeRadius: CGFloat = 5

            // Draw edges: input -> hidden
            for inp in inputNodes {
                for hid in hiddenNodes {
                    drawEdge(context: &context, size: size,
                             from: inp, to: hid,
                             color: Theme.accentPurple.opacity(0.2), trim: edgeTrim)
                }
            }
            // hidden -> output
            for hid in hiddenNodes {
                for out in outputNodes {
                    drawEdge(context: &context, size: size,
                             from: hid, to: out,
                             color: Theme.accentCyan.opacity(0.3), trim: edgeTrim)
                }
            }

            // Draw nodes
            let allNodes: [(CGPoint, Color)] =
                inputNodes.map { ($0, Theme.accentPurple) } +
                hiddenNodes.map { ($0, Color(hex: "B388FF")) } +
                outputNodes.map { ($0, Theme.accentCyan) }

            for (node, color) in allNodes {
                let center = CGPoint(x: node.x * size.width, y: node.y * size.height)
                let rect = CGRect(x: center.x - nodeRadius, y: center.y - nodeRadius,
                                  width: nodeRadius * 2, height: nodeRadius * 2)
                context.opacity = Double(edgeTrim)
                context.fill(Circle().path(in: rect), with: .color(color))
            }
        }
    }

    private func drawEdge(context: inout GraphicsContext, size: CGSize,
                          from: CGPoint, to: CGPoint, color: Color, trim: CGFloat) {
        var path = Path()
        path.move(to: CGPoint(x: from.x * size.width, y: from.y * size.height))
        path.addLine(to: CGPoint(x: to.x * size.width, y: to.y * size.height))

        context.stroke(
            path.trimmedPath(from: 0, to: trim),
            with: .color(color),
            lineWidth: 1
        )
    }

    private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat {
        a + (b - a) * t
    }
}

// MARK: - Page 4 Hero: Convergence + Burst

private struct HeroGetStartedView: View {
    @State private var orbitPhase: CGFloat = 0
    @State private var converged = false
    @State private var showBurst = false
    @State private var burstScale: CGFloat = 0
    @State private var showAppIcon = false
    @State private var showCheckmark = false
    @State private var burstOpacity: CGFloat = 1

    private let icons = [
        ("chart.line.uptrend.xyaxis", Theme.profit),
        ("list.bullet.rectangle", Theme.accentOrange),
        ("function", Theme.primaryBlue),
        ("brain.head.profile", Theme.accentPurple),
    ]

    var body: some View {
        ZStack {
            // Orbiting icons
            TimelineView(.animation) { timeline in
                let t = timeline.date.timeIntervalSinceReferenceDate
                ZStack {
                    ForEach(0..<4, id: \.self) { index in
                        let angle = (Double(index) / 4.0) * .pi * 2 + t * 1.5
                        let orbitRadius: CGFloat = converged ? 0 : 80
                        let x = cos(angle) * orbitRadius
                        let y = sin(angle) * orbitRadius

                        Image(systemName: icons[index].0)
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(icons[index].1)
                            .offset(x: x, y: y)
                            .opacity(converged ? 0 : 1)
                    }
                }
            }

            // Particle burst
            if showBurst {
                ForEach(0..<12, id: \.self) { i in
                    let angle = (Double(i) / 12.0) * .pi * 2
                    Circle()
                        .fill(onboardingPages[4].accentColor)
                        .frame(width: 6, height: 6)
                        .offset(
                            x: cos(angle) * 60 * burstScale,
                            y: sin(angle) * 60 * burstScale
                        )
                        .opacity(burstOpacity)
                }
            }

            // App icon at center
            if showAppIcon {
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .stroke(
                                LinearGradient(
                                    colors: [Theme.profit, Theme.primaryBlue],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 3
                            )
                            .frame(width: 90, height: 90)

                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [Theme.surface, Theme.surfaceElevated],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 76, height: 76)
                            .shadow(color: Theme.profit.opacity(0.3), radius: 16)

                        VStack(spacing: 2) {
                            Image(systemName: "chart.line.uptrend.xyaxis")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundStyle(
                                    LinearGradient(
                                        colors: [Theme.profit, Theme.profitLight],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                            Text("f(x)")
                                .font(.system(size: 10, weight: .black, design: .monospaced))
                                .foregroundColor(Theme.primaryBlue)
                        }
                    }

                    if showCheckmark {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(Theme.profit)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .transition(.scale.combined(with: .opacity))
            }
        }
        .frame(height: 220)
        .onAppear {
            // After 1.2s: converge icons
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) {
                    converged = true
                }

                // Burst after converge
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    showBurst = true
                    withAnimation(.easeOut(duration: 0.6)) {
                        burstScale = 1.0
                    }
                    withAnimation(.easeOut(duration: 0.6).delay(0.2)) {
                        burstOpacity = 0
                    }

                    // Show app icon
                    withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) {
                        showAppIcon = true
                    }

                    // Show checkmark
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) {
                            showCheckmark = true
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Page Container

private struct OnboardingPageContainer: View {
    let pageIndex: Int
    let pageData: OnboardingPageData
    @State private var showHero = false
    @State private var showText = false
    @State private var featureAppeared: [Bool] = [false, false, false]

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            // Hero illustration
            Group {
                switch pageIndex {
                case 0: HeroWelcomeView()
                case 1: HeroOptionChainView()
                case 2: HeroCalculatorGreeksView()
                case 3: HeroAIInsightsView()
                case 4: HeroGetStartedView()
                default: EmptyView()
                }
            }
            .scaleEffect(showHero ? 1.0 : 0.85)
            .opacity(showHero ? 1 : 0)

            // Title & Subtitle
            VStack(spacing: 10) {
                Text(pageData.title)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                    .multilineTextAlignment(.center)

                Text(pageData.subtitle)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .opacity(showText ? 1 : 0)
            .offset(y: showText ? 0 : 15)
            .padding(.horizontal, 24)

            // Features
            VStack(spacing: 10) {
                ForEach(0..<pageData.features.count, id: \.self) { i in
                    let f = pageData.features[i]
                    OnboardingFeatureRow(icon: f.icon, text: f.text, color: f.color)
                        .opacity(featureAppeared[i] ? 1 : 0)
                        .offset(x: featureAppeared[i] ? 0 : -25)
                }
            }
            .padding(.horizontal, 24)

            Spacer()
            Spacer()
        }
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                showHero = true
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                withAnimation(.easeOut(duration: 0.4)) {
                    showText = true
                }
            }
            for i in 0..<pageData.features.count {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3 + Double(i) * 0.1) {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                        featureAppeared[i] = true
                    }
                }
            }
        }
    }
}

// MARK: - Shimmer Modifier

private struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { geo in
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0),
                            Color.white.opacity(0.15),
                            Color.white.opacity(0),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: geo.size.width * 0.5)
                    .offset(x: phase * geo.size.width)
                    .onAppear {
                        withAnimation(.linear(duration: 2.0).repeatForever(autoreverses: false)) {
                            phase = 1.5
                        }
                    }
                }
                .mask(content)
            }
    }
}

// MARK: - Onboarding View

struct OnboardingView: View {
    @Binding var hasCompletedOnboarding: Bool
    @State private var currentPage = 0
    @State private var dragOffset: CGFloat = 0
    @State private var direction: Int = 1 // 1 = forward, -1 = backward

    private let totalPages = 5

    var body: some View {
        ZStack {
            // Background
            Theme.backgroundGradient
                .ignoresSafeArea()

            // Particle field
            ParticleFieldView(accentColor: onboardingPages[currentPage].accentColor)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar: Skip button
                HStack {
                    Spacer()
                    if currentPage < totalPages - 1 {
                        Button {
                            completeOnboarding()
                        } label: {
                            Text(L.onboardingSkip)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(Theme.textMuted)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .frame(height: 44)

                // Page content with custom transitions
                ZStack {
                    OnboardingPageContainer(
                        pageIndex: currentPage,
                        pageData: onboardingPages[currentPage]
                    )
                    .id(currentPage)
                    .transition(.asymmetric(
                        insertion: .move(edge: direction >= 0 ? .trailing : .leading)
                            .combined(with: .opacity),
                        removal: .move(edge: direction >= 0 ? .leading : .trailing)
                            .combined(with: .opacity)
                    ))
                }
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            dragOffset = value.translation.width
                        }
                        .onEnded { value in
                            let threshold: CGFloat = 80
                            let velocity = value.predictedEndTranslation.width - value.translation.width

                            if value.translation.width < -threshold || velocity < -200 {
                                // Swipe left → next page
                                if currentPage < totalPages - 1 {
                                    direction = 1
                                    withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                                        currentPage += 1
                                    }
                                }
                            } else if value.translation.width > threshold || velocity > 200 {
                                // Swipe right → previous page
                                if currentPage > 0 {
                                    direction = -1
                                    withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                                        currentPage -= 1
                                    }
                                }
                            }
                            dragOffset = 0
                        }
                )

                // Progress bar
                OnboardingProgressBar(currentPage: currentPage, totalPages: totalPages)
                    .padding(.vertical, 16)

                // Action button
                Button {
                    if currentPage < totalPages - 1 {
                        direction = 1
                        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
                            currentPage += 1
                        }
                    } else {
                        completeOnboarding()
                    }
                } label: {
                    HStack(spacing: 10) {
                        Text(currentPage < totalPages - 1 ? L.onboardingNext : L.onboardingGetStarted)
                            .font(.system(size: 17, weight: .bold))

                        Image(systemName: currentPage < totalPages - 1 ? "arrow.right" : "checkmark")
                            .font(.system(size: 16, weight: .bold))
                    }
                    .foregroundColor(Theme.textPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 18)
                    .background {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(
                                LinearGradient(
                                    colors: onboardingPages[currentPage].accentGradient,
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .shadow(
                                color: onboardingPages[currentPage].accentColor.opacity(0.4),
                                radius: 12, x: 0, y: 6
                            )
                    }
                    .modifier(ShimmerModifier())
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .buttonStyle(ScalePressStyle())
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: currentPage)
    }

    private func completeOnboarding() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()

        withAnimation(.easeInOut(duration: 0.3)) {
            hasCompletedOnboarding = true
        }
        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
    }
}

// MARK: - Scale Press Button Style

private struct ScalePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1.0)
            .animation(.spring(response: 0.2), value: configuration.isPressed)
    }
}

#Preview {
    OnboardingView(hasCompletedOnboarding: .constant(false))
}
