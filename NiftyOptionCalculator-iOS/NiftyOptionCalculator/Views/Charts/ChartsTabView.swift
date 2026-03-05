import SwiftUI

// MARK: - Charts Tab View

struct ChartsTabView: View {
    @ObservedObject var viewModel: OptionChainViewModel
    @State private var showOptionChart = false
    @State private var selectedOptionForChart: OptionData?

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {
                    // Index Chart Section
                    indexChartSection

                    // Quick Option Charts
                    if let atm = viewModel.atmStrike,
                       let atmRow = viewModel.optionChain.first(where: { abs($0.strikePrice - atm) <= viewModel.selectedIndex.strikeInterval / 2 }) {
                        quickOptionChartsSection(atmRow: atmRow)
                    }

                    // Market Overview
                    marketOverviewSection

                    Spacer(minLength: 100)
                }
                .padding(.top, 8)
            }
        }
        .sheet(item: $selectedOptionForChart) { option in
            NavigationView {
                OptionChartView(option: option)
                    .navigationTitle("\(Int(option.strikePrice)) \(option.optionType == .call ? "CE" : "PE")")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button {
                                selectedOptionForChart = nil
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(Theme.textMuted)
                            }
                        }
                    }
            }
            .presentationDetents([.large])
        }
    }

    // MARK: - Index Chart Section

    private var indexChartSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(title: "\(viewModel.selectedIndex.shortName) \(L.chartsIndexChart)", icon: "chart.xyaxis.line")

            ProfessionalIndexChartView(index: viewModel.selectedIndex, spotPrice: viewModel.spotPrice)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Theme.surface, lineWidth: 1)
                }
        }
        .padding(.horizontal, 16)
    }

    // MARK: - Quick Option Charts Section

    private func quickOptionChartsSection(atmRow: OptionChainRow) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(title: L.chartsATMOptions, icon: "target")

            HStack(spacing: 12) {
                // ATM Call
                if let call = atmRow.callOption {
                    quickOptionCard(option: call, type: "CE")
                }

                // ATM Put
                if let put = atmRow.putOption {
                    quickOptionCard(option: put, type: "PE")
                }
            }
        }
        .padding(.horizontal, 16)
    }

    private func quickOptionCard(option: OptionData, type: String) -> some View {
        Button {
            selectedOptionForChart = option
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("\(Int(option.strikePrice)) \(type)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Theme.textPrimary)

                    Spacer()

                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.accentBlue)
                }

                Text(String(format: "₹%.2f", option.lastTradedPrice))
                    .font(.system(size: 20, weight: .bold, design: .monospaced))
                    .foregroundColor(type == "CE" ? Theme.profit : Theme.loss)

                HStack(spacing: 12) {
                    miniStat(label: "OI", value: formatOI(option.openInterest))
                    miniStat(label: "IV", value: String(format: "%.1f%%", option.impliedVolatility * 100))
                }

                // Mini sparkline
                MiniOptionSparkline(isCall: type == "CE")
                    .frame(height: 30)
            }
            .padding(14)
            .background {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 14)
                            .stroke((type == "CE" ? Theme.profit : Theme.loss).opacity(0.3), lineWidth: 1)
                    }
            }
        }
        .buttonStyle(.plain)
    }

    private func miniStat(label: String, value: String) -> some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)
            Text(value)
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(Theme.textSecondary)
        }
    }

    // MARK: - Market Overview Section

    private var marketOverviewSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(title: L.chartsMarketOverview, icon: "chart.pie.fill")

            VStack(spacing: 12) {
                // PCR Gauge
                overviewRow(
                    title: L.chartsPutCallRatio,
                    value: String(format: "%.2f", viewModel.putCallRatio),
                    subtitle: viewModel.putCallRatio > 1 ? L.chartsBullish : L.chartsBearish,
                    color: viewModel.putCallRatio > 1 ? Theme.profit : Theme.loss
                )

                Divider().background(Theme.textMuted.opacity(0.2))

                // Max Pain
                if let maxPain = viewModel.maxPainStrike {
                    overviewRow(
                        title: L.chartsMaxPain,
                        value: String(format: "%.0f", maxPain),
                        subtitle: maxPain > viewModel.spotPrice ? L.chartsAboveSpot : L.chartsBelowSpot,
                        color: Theme.accentOrange
                    )
                }

                Divider().background(Theme.textMuted.opacity(0.2))

                // Total OI
                overviewRow(
                    title: L.chartsTotalCallOI,
                    value: formatOI(viewModel.totalCallOI),
                    subtitle: "",
                    color: Theme.profit
                )

                overviewRow(
                    title: L.chartsTotalPutOI,
                    value: formatOI(viewModel.totalPutOI),
                    subtitle: "",
                    color: Theme.loss
                )
            }
            .padding(16)
            .background {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Theme.surface)
            }
        }
        .padding(.horizontal, 16)
    }

    private func overviewRow(title: String, value: String, subtitle: String, color: Color) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Theme.textSecondary)

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text(value)
                    .font(.system(size: 16, weight: .bold, design: .monospaced))
                    .foregroundColor(color)

                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Theme.textMuted)
                }
            }
        }
    }

    // MARK: - Helpers

    private func sectionHeader(title: String, icon: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.accentBlue)

            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.textPrimary)
        }
    }

    private func formatOI(_ oi: Int) -> String {
        if oi >= 10_000_000 {
            return String(format: "%.1fCr", Double(oi) / 10_000_000)
        } else if oi >= 100_000 {
            return String(format: "%.1fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.1fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }
}

// MARK: - Mini Option Sparkline

struct MiniOptionSparkline: View {
    let isCall: Bool
    @State private var animationProgress: CGFloat = 0

    private var data: [Double] {
        // Generate random sparkline data
        var values: [Double] = []
        var current = 0.5
        for _ in 0..<15 {
            current += Double.random(in: -0.15...0.15)
            current = max(0.1, min(0.9, current))
            values.append(current)
        }
        return values
    }

    var body: some View {
        GeometryReader { geo in
            let stepX = geo.size.width / CGFloat(data.count - 1)

            Path { path in
                for (index, value) in data.enumerated() {
                    let x = CGFloat(index) * stepX
                    let y = geo.size.height - (CGFloat(value) * geo.size.height)
                    if index == 0 {
                        path.move(to: CGPoint(x: x, y: y))
                    } else {
                        path.addLine(to: CGPoint(x: x, y: y))
                    }
                }
            }
            .trim(from: 0, to: animationProgress)
            .stroke(
                isCall ? Theme.profit : Theme.loss,
                style: StrokeStyle(lineWidth: 1.5, lineCap: .round)
            )
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.8)) {
                animationProgress = 1
            }
        }
    }
}

// MARK: - Preview

#Preview {
    ChartsTabView(viewModel: OptionChainViewModel())
        
}
