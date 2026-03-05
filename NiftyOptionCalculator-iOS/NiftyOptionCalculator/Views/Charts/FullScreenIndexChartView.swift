import SwiftUI

// MARK: - Full Screen Index Chart View

struct FullScreenIndexChartView: View {
    let index: TradingIndex
    let spotPrice: Double
    let spotChange: Double
    let spotChangePercent: Double

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Theme.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Image(systemName: index.icon)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(hex: index.themeColor))

                            Text(index.displayName)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Theme.textPrimary)
                        }

                        HStack(spacing: 8) {
                            Text(String(format: "%.2f", spotPrice))
                                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                                .foregroundColor(Theme.textPrimary)

                            HStack(spacing: 4) {
                                Image(systemName: spotChange >= 0 ? "arrow.up" : "arrow.down")
                                    .font(.system(size: 10, weight: .bold))
                                Text(String(format: "%+.2f (%.2f%%)", spotChange, abs(spotChangePercent)))
                                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                            }
                            .foregroundColor(spotChange >= 0 ? Theme.profit : Theme.loss)
                        }
                    }

                    Spacer()

                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 28))
                            .foregroundColor(Theme.textMuted)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)

                // Full Index Chart - using Professional TradingView Chart
                ProfessionalIndexChartView(index: index, spotPrice: spotPrice)
            }
        }
        
    }
}

// MARK: - Preview

#Preview {
    FullScreenIndexChartView(
        index: .nifty50,
        spotPrice: 25000,
        spotChange: 125.50,
        spotChangePercent: 0.50
    )
}
