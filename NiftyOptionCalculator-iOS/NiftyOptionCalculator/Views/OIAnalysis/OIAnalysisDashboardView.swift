import SwiftUI

// MARK: - OI Analysis Dashboard View

struct OIAnalysisDashboardView: View {
    @StateObject private var viewModel: OIAnalysisViewModel
    @Environment(\.dismiss) private var dismiss

    let symbol: String

    init(
        optionChain: [OptionChainRow],
        spotPrice: Double,
        strikeInterval: Double,
        symbol: String
    ) {
        self.symbol = symbol
        _viewModel = StateObject(wrappedValue: OIAnalysisViewModel(
            optionChain: optionChain,
            spotPrice: spotPrice,
            strikeInterval: strikeInterval
        ))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab selector
                OITabBar(selectedTab: $viewModel.selectedTab)

                // Content
                TabView(selection: $viewModel.selectedTab) {
                    // Heatmap tab
                    if let result = viewModel.analysisResult {
                        OIHeatmapView(
                            strikeData: viewModel.sortedStrikeData,
                            spotPrice: result.spotPrice,
                            onStrikeSelect: { viewModel.selectStrike($0) }
                        )
                        .tag(OIAnalysisTab.heatmap)

                        // Zones tab
                        OIZonesView(
                            supportZones: result.supportZones,
                            resistanceZones: result.resistanceZones,
                            spotPrice: result.spotPrice,
                            interpretation: result.oiInterpretation
                        )
                        .tag(OIAnalysisTab.zones)

                        // IV Surface tab
                        IVSurfaceView(
                            callIVPoints: viewModel.callIVSurface,
                            putIVPoints: viewModel.putIVSurface,
                            spotPrice: result.spotPrice
                        )
                        .tag(OIAnalysisTab.ivSurface)

                        // Activity tab
                        SmartMoneyActivityView(
                            activities: viewModel.smartMoneyActivities,
                            topCallOIStrikes: viewModel.topCallOIStrikes,
                            topPutOIStrikes: viewModel.topPutOIStrikes,
                            topOIChangeStrikes: viewModel.topOIChangeStrikes
                        )
                        .tag(OIAnalysisTab.activity)
                    } else {
                        // Loading state
                        LoadingStateView()
                            .tag(OIAnalysisTab.heatmap)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.2), value: viewModel.selectedTab)
            }
            .background(Theme.background)
            .navigationTitle("OI Analysis")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textSecondary)
                    }
                }

                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text("OI Analysis")
                            .font(.headline)
                            .foregroundColor(Theme.textPrimary)

                        if let result = viewModel.analysisResult {
                            Text("\(symbol) @ \(String(format: "%.0f", result.spotPrice))")
                                .font(.caption)
                                .foregroundColor(Theme.textMuted)
                        }
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { viewModel.performAnalysis() }) {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(Theme.primaryBlue)
                    }
                    .disabled(viewModel.isLoading)
                }
            }
            .sheet(isPresented: $viewModel.showStrikeDetail) {
                if let strike = viewModel.selectedStrike,
                   let data = viewModel.getStrikeData(for: strike) {
                    StrikeDetailSheet(data: data)
                }
            }
        }
    }
}

// MARK: - OI Tab Bar

private struct OITabBar: View {
    @Binding var selectedTab: OIAnalysisTab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(OIAnalysisTab.allCases, id: \.self) { tab in
                OITabItem(
                    tab: tab,
                    isSelected: selectedTab == tab,
                    action: { selectedTab = tab }
                )
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
        .background(Theme.surface)
    }
}

// MARK: - OI Tab Item

private struct OITabItem: View {
    let tab: OIAnalysisTab
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: tab.icon)
                    .font(.system(size: 16, weight: isSelected ? .bold : .regular))

                Text(tab.rawValue)
                    .font(.system(size: 10, weight: isSelected ? .semibold : .regular))
            }
            .foregroundColor(isSelected ? Theme.primaryBlue : Theme.textMuted)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? Theme.primaryBlue.opacity(0.15) : Color.clear)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Loading State View

private struct LoadingStateView: View {
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.5)
                .tint(Theme.primaryBlue)

            Text("Analyzing OI Data...")
                .font(.subheadline)
                .foregroundColor(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.background)
    }
}

// MARK: - Strike Detail Sheet

private struct StrikeDetailSheet: View {
    let data: StrikeOIData
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Strike header
                    StrikeHeader(data: data)

                    // OI comparison
                    OIComparisonCard(data: data)

                    // OI change
                    OIChangeCard(data: data)

                    // IV comparison
                    IVComparisonCard(data: data)

                    // LTP
                    LTPCard(data: data)
                }
                .padding()
            }
            .background(Theme.background)
            .navigationTitle("Strike Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(Theme.primaryBlue)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Strike Header

private struct StrikeHeader: View {
    let data: StrikeOIData

    var body: some View {
        VStack(spacing: 8) {
            Text(data.displayStrike)
                .font(.system(.largeTitle, design: .monospaced))
                .fontWeight(.bold)
                .foregroundColor(Theme.textPrimary)

            HStack(spacing: 16) {
                // PCR
                VStack(spacing: 2) {
                    Text("PCR")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.2f", data.pcr))
                        .font(.system(.subheadline, design: .monospaced))
                        .fontWeight(.semibold)
                        .foregroundColor(pcrColor(data.pcr))
                }

                Divider()
                    .frame(height: 30)

                // Net OI Change
                VStack(spacing: 2) {
                    Text("Net OI Change")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)

                    HStack(spacing: 4) {
                        Image(systemName: data.netOIChange > 0 ? "arrow.up" : "arrow.down")
                            .font(.caption)
                        Text(formatOI(abs(data.netOIChange)))
                            .font(.system(.subheadline, design: .monospaced))
                            .fontWeight(.semibold)
                    }
                    .foregroundColor(data.netOIChange > 0 ? Theme.profit : Theme.loss)
                }

                Divider()
                    .frame(height: 30)

                // Direction
                VStack(spacing: 2) {
                    Text("Sentiment")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)

                    Text(data.oiChangeDirection.sentiment)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(data.oiChangeDirection.color)
                }
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }

    private func pcrColor(_ pcr: Double) -> Color {
        if pcr > 1.2 { return Theme.profit }
        if pcr < 0.8 { return Theme.loss }
        return Theme.accentBlue
    }

    private func formatOI(_ oi: Int) -> String {
        if oi >= 100_000 {
            return String(format: "%.1fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.0fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }
}

// MARK: - OI Comparison Card

private struct OIComparisonCard: View {
    let data: StrikeOIData

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Open Interest")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            HStack(spacing: 16) {
                // Call OI
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Circle()
                            .fill(Theme.loss)
                            .frame(width: 8, height: 8)
                        Text("Call OI")
                            .font(.caption)
                            .foregroundColor(Theme.textMuted)
                    }

                    Text(formatOI(data.callOI))
                        .font(.system(.title2, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.loss)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Put OI
                VStack(alignment: .trailing, spacing: 4) {
                    HStack {
                        Text("Put OI")
                            .font(.caption)
                            .foregroundColor(Theme.textMuted)
                        Circle()
                            .fill(Theme.profit)
                            .frame(width: 8, height: 8)
                    }

                    Text(formatOI(data.putOI))
                        .font(.system(.title2, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.profit)
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }

            // Visual bar
            GeometryReader { geo in
                let total = data.callOI + data.putOI
                let callWidth = total > 0 ? CGFloat(data.callOI) / CGFloat(total) * geo.size.width : geo.size.width / 2

                HStack(spacing: 2) {
                    Rectangle()
                        .fill(Theme.loss)
                        .frame(width: callWidth)

                    Rectangle()
                        .fill(Theme.profit)
                }
                .frame(height: 8)
                .cornerRadius(4)
            }
            .frame(height: 8)
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }

    private func formatOI(_ oi: Int) -> String {
        if oi >= 100_000 {
            return String(format: "%.2fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.1fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }
}

// MARK: - OI Change Card

private struct OIChangeCard: View {
    let data: StrikeOIData

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("OI Change")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            HStack(spacing: 16) {
                // Call OI Change
                ChangeMetric(
                    label: "Call OI Change",
                    value: data.callOIChange,
                    baseColor: Theme.loss
                )

                // Put OI Change
                ChangeMetric(
                    label: "Put OI Change",
                    value: data.putOIChange,
                    baseColor: Theme.profit
                )
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Change Metric

private struct ChangeMetric: View {
    let label: String
    let value: Int
    let baseColor: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(Theme.textMuted)

            HStack(spacing: 4) {
                Image(systemName: value >= 0 ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                    .foregroundColor(value >= 0 ? Theme.profit : Theme.loss)

                Text(formatOI(abs(value)))
                    .font(.system(.title3, design: .monospaced))
                    .fontWeight(.bold)
                    .foregroundColor(value >= 0 ? Theme.profit : Theme.loss)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func formatOI(_ oi: Int) -> String {
        if oi >= 100_000 {
            return String(format: "%.2fL", Double(oi) / 100_000)
        } else if oi >= 1000 {
            return String(format: "%.1fK", Double(oi) / 1000)
        }
        return "\(oi)"
    }
}

// MARK: - IV Comparison Card

private struct IVComparisonCard: View {
    let data: StrikeOIData

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Implied Volatility")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            HStack(spacing: 16) {
                // Call IV
                VStack(alignment: .leading, spacing: 4) {
                    Text("Call IV")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.1f%%", data.callIV * 100))
                        .font(.system(.title3, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.loss)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Put IV
                VStack(alignment: .trailing, spacing: 4) {
                    Text("Put IV")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.1f%%", data.putIV * 100))
                        .font(.system(.title3, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.profit)
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }

            // IV Skew
            HStack {
                Text("IV Skew (Put - Call)")
                    .font(.caption)
                    .foregroundColor(Theme.textMuted)

                Spacer()

                let skew = (data.putIV - data.callIV) * 100
                Text(String(format: "%+.2f%%", skew))
                    .font(.system(.subheadline, design: .monospaced))
                    .fontWeight(.semibold)
                    .foregroundColor(skew > 0 ? Theme.loss : Theme.profit)
            }
            .padding(.top, 4)
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - LTP Card

private struct LTPCard: View {
    let data: StrikeOIData

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Last Traded Price")
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            HStack(spacing: 16) {
                // Call LTP
                VStack(alignment: .leading, spacing: 4) {
                    Text("Call LTP")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.2f", data.callLTP))
                        .font(.system(.title3, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.loss)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Put LTP
                VStack(alignment: .trailing, spacing: 4) {
                    Text("Put LTP")
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.2f", data.putLTP))
                        .font(.system(.title3, design: .monospaced))
                        .fontWeight(.bold)
                        .foregroundColor(Theme.profit)
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - OI Analysis FAB

struct OIAnalysisFAB: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "chart.bar.doc.horizontal.fill")
                    .font(.system(size: 16, weight: .semibold))

                Text("OI Analysis")
                    .font(.system(size: 14, weight: .semibold))
            }
            .foregroundColor(Theme.textPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                LinearGradient(
                    colors: [Theme.accentPurple, Theme.primaryBlue],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(24)
            .shadow(color: Theme.primaryBlue.opacity(0.4), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Preview

#Preview {
    let sampleChain: [OptionChainRow] = []

    OIAnalysisDashboardView(
        optionChain: sampleChain,
        spotPrice: 24000,
        strikeInterval: 50,
        symbol: "NIFTY"
    )
    
}
