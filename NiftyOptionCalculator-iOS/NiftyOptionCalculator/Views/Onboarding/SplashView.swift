import SwiftUI

// MARK: - Animated Logo View

private struct AnimatedLogoView: View {
    @State private var isAnimating = false
    @State private var ringScale: CGFloat = 0.8
    @State private var ringOpacity: CGFloat = 0.8
    @State private var innerRingRotation: Double = 0
    @State private var outerRingRotation: Double = 0

    var body: some View {
        ZStack {
            // Outer pulsing ring
            Circle()
                .stroke(
                    LinearGradient(
                        colors: [Theme.profit.opacity(0.6), Theme.primaryBlue.opacity(0.4)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 3
                )
                .frame(width: 160, height: 160)
                .scaleEffect(ringScale)
                .opacity(ringOpacity)

            // Rotating dashed ring
            Circle()
                .stroke(
                    AngularGradient(
                        colors: [Theme.profit, Theme.primaryBlue, Theme.accentOrange, Theme.profit],
                        center: .center
                    ),
                    style: StrokeStyle(lineWidth: 2, dash: [8, 8])
                )
                .frame(width: 140, height: 140)
                .rotationEffect(.degrees(outerRingRotation))

            // Inner rotating ring
            Circle()
                .stroke(
                    LinearGradient(
                        colors: [Theme.profit, Theme.primaryBlue],
                        startPoint: .top,
                        endPoint: .bottom
                    ),
                    style: StrokeStyle(lineWidth: 2, dash: [4, 12])
                )
                .frame(width: 120, height: 120)
                .rotationEffect(.degrees(innerRingRotation))

            // Main circle background
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Theme.surface, Theme.surfaceElevated],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 100, height: 100)
                .shadow(color: Theme.profit.opacity(0.3), radius: 20, x: 0, y: 10)

            // App icon
            VStack(spacing: 2) {
                Image(systemName: "chart.line.uptrend.xyaxis")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.profit, Theme.profitLight],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Text("f(x)")
                    .font(.system(size: 12, weight: .black, design: .monospaced))
                    .foregroundColor(Theme.primaryBlue)
            }
            .scaleEffect(isAnimating ? 1.05 : 0.95)
        }
        .onAppear {
            // Pulse animation
            withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) {
                isAnimating = true
            }

            // Ring pulse
            withAnimation(.easeInOut(duration: 2).repeatForever(autoreverses: true)) {
                ringScale = 1.1
                ringOpacity = 0.3
            }

            // Rotating rings
            withAnimation(.linear(duration: 8).repeatForever(autoreverses: false)) {
                outerRingRotation = 360
            }

            withAnimation(.linear(duration: 6).repeatForever(autoreverses: false)) {
                innerRingRotation = -360
            }
        }
    }
}

// MARK: - Loading Dots

private struct LoadingDotsView: View {
    @State private var dotScales: [CGFloat] = [1, 1, 1]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(Theme.profit)
                    .frame(width: 8, height: 8)
                    .scaleEffect(dotScales[index])
            }
        }
        .onAppear {
            for i in 0..<3 {
                withAnimation(
                    .easeInOut(duration: 0.6)
                    .repeatForever(autoreverses: true)
                    .delay(Double(i) * 0.2)
                ) {
                    dotScales[i] = 0.5
                }
            }
        }
    }
}

// MARK: - Splash View

struct SplashView: View {
    @State private var logoScale: CGFloat = 0.5
    @State private var logoOpacity: CGFloat = 0
    @State private var textOpacity: CGFloat = 0
    @State private var taglineOpacity: CGFloat = 0
    @State private var dotsOpacity: CGFloat = 0

    private let appName = "Optix"

    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    Color(hex: "0A0B0F"),
                    Color(hex: "0F1117"),
                    Color(hex: "0A0B0F")
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // Subtle background glow
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Theme.profit.opacity(0.15), .clear],
                        center: .center,
                        startRadius: 0,
                        endRadius: 200
                    )
                )
                .frame(width: 400, height: 400)
                .blur(radius: 60)

            // Main content
            VStack(spacing: 32) {
                Spacer()

                // Animated Logo
                AnimatedLogoView()
                    .scaleEffect(logoScale)
                    .opacity(logoOpacity)

                // App name and tagline
                VStack(spacing: 16) {
                    Text(appName)
                        .font(.system(size: 44, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                        .shadow(color: Theme.profit.opacity(0.5), radius: 15, x: 0, y: 0)
                        .opacity(textOpacity)

                    Text(L.splashTitle)
                        .font(.system(size: 12, weight: .bold))
                        .tracking(5)
                        .foregroundColor(Theme.profit)
                        .opacity(taglineOpacity)

                    Text(L.splashSubtitle)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                        .opacity(taglineOpacity)
                }

                Spacer()

                // Loading dots
                LoadingDotsView()
                    .opacity(dotsOpacity)
                    .padding(.bottom, 60)
            }
            .padding(.horizontal, 40)
        }
        .onAppear {
            // Logo animation
            withAnimation(.spring(response: 0.8, dampingFraction: 0.7)) {
                logoScale = 1.0
                logoOpacity = 1.0
            }

            // Text animation
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                withAnimation(.easeOut(duration: 0.5)) {
                    textOpacity = 1.0
                }
            }

            // Tagline animation
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                withAnimation(.easeOut(duration: 0.5)) {
                    taglineOpacity = 1.0
                }
            }

            // Dots animation
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                withAnimation(.easeOut(duration: 0.3)) {
                    dotsOpacity = 1.0
                }
            }
        }
    }
}

#Preview {
    SplashView()
}
