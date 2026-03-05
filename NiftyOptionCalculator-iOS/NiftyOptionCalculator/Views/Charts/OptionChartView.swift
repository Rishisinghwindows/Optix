import SwiftUI
import Charts

// MARK: - Option Chart Tab

enum OptionChartTab: String, CaseIterable, Identifiable {
    case price = "Price"
    case oi = "OI"
    case iv = "IV"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .price: return L.chartsPrice
        case .oi: return rawValue
        case .iv: return rawValue
        }
    }

    var icon: String {
        switch self {
        case .price: return "indianrupeesign"
        case .oi: return "chart.bar.doc.horizontal"
        case .iv: return "percent"
        }
    }
}

// MARK: - Option Chart View

struct OptionChartView: View {
    let option: OptionData

    @Environment(\.dismiss) private var dismiss
    @State private var ticks: [OptionTickData] = []
    @State private var selectedTab: OptionChartTab = .price
    @State private var selectedTick: OptionTickData?
    @State private var isLoading = true

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Theme.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Option header info
                optionHeader

                // Tab selector
                tabSelector

                // Chart content - Full screen
                if isLoading {
                    loadingView
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    chartContent
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                // Stats summary
                statsSummary
            }
        }
        .background(Theme.background)
        .edgesIgnoringSafeArea(.bottom)
        .onAppear {
            loadData()
        }
    }

    // MARK: - Option Header

    private var optionHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(String(format: "%.0f", option.strikePrice))
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(Theme.textPrimary)

                    Text(option.optionType == .call ? "CE" : "PE")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(option.optionType == .call ? Theme.profit : Theme.loss)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background {
                            Capsule()
                                .fill((option.optionType == .call ? Theme.profit : Theme.loss).opacity(0.2))
                        }

                    // Live badge
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Theme.profit)
                            .frame(width: 6, height: 6)
                        Text("LIVE")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Theme.profit)
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Theme.profit.opacity(0.15))
                    }
                }

                HStack(spacing: 12) {
                    Text("LTP: \(String(format: "%.2f", option.lastTradedPrice))")
                        .font(.system(size: 18, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.textPrimary)

                    Text("IV: \(String(format: "%.1f%%", option.impliedVolatility * 100))")
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .foregroundColor(Theme.accentPurple)

                    Text("OI: \(formatNumber(option.openInterest))")
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .foregroundColor(Theme.accentOrange)
                }
            }

            Spacer()
        }
        .padding(16)
        .background(Theme.surface)
    }

    // MARK: - Tab Selector

    private var tabSelector: some View {
        HStack(spacing: 0) {
            ForEach(OptionChartTab.allCases) { tab in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selectedTab = tab
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 12, weight: .medium))
                        Text(tab.displayName)
                            .font(.system(size: 13, weight: .semibold))
                    }
                    .foregroundColor(selectedTab == tab ? .white : Theme.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background {
                        if selectedTab == tab {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(Theme.accentBlue)
                                .padding(4)
                        }
                    }
                }
            }
        }
        .background(Theme.surface.opacity(0.5))
    }

    // MARK: - Chart Content

    private var chartContent: some View {
        Group {
            switch selectedTab {
            case .price:
                priceChart
            case .oi:
                oiChart
            case .iv:
                ivChart
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 12)
    }

    // MARK: - Price Chart

    private var priceChart: some View {
        Chart(ticks) { tick in
            AreaMark(
                x: .value("Time", tick.timestamp),
                y: .value("LTP", tick.ltp)
            )
            .foregroundStyle(
                LinearGradient(
                    colors: [Theme.accentBlue.opacity(0.4), Theme.accentBlue.opacity(0.0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )

            LineMark(
                x: .value("Time", tick.timestamp),
                y: .value("LTP", tick.ltp)
            )
            .foregroundStyle(Theme.accentBlue)
            .lineStyle(StrokeStyle(lineWidth: 2))

            if let selected = selectedTick, selected.id == tick.id {
                PointMark(
                    x: .value("Time", tick.timestamp),
                    y: .value("LTP", tick.ltp)
                )
                .foregroundStyle(.white)
                .symbolSize(50)
            }
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 5)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let price = value.as(Double.self) {
                        Text(String(format: "%.2f", price))
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { value in
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(formatTime(date))
                            .font(.system(size: 9))
                            .foregroundStyle(Theme.textMuted)
                    }
                }
            }
        }
        .chartOverlay { proxy in
            chartOverlayGesture(proxy: proxy)
        }
    }

    // MARK: - OI Chart

    private var oiChart: some View {
        Chart(ticks) { tick in
            AreaMark(
                x: .value("Time", tick.timestamp),
                y: .value("OI", tick.oi)
            )
            .foregroundStyle(
                LinearGradient(
                    colors: [Theme.accentOrange.opacity(0.4), Theme.accentOrange.opacity(0.0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )

            LineMark(
                x: .value("Time", tick.timestamp),
                y: .value("OI", tick.oi)
            )
            .foregroundStyle(Theme.accentOrange)
            .lineStyle(StrokeStyle(lineWidth: 2))
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 5)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let oi = value.as(Int.self) {
                        Text(formatNumber(oi))
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { value in
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(formatTime(date))
                            .font(.system(size: 9))
                            .foregroundStyle(Theme.textMuted)
                    }
                }
            }
        }
    }

    // MARK: - IV Chart

    private var ivChart: some View {
        Chart(ticks) { tick in
            LineMark(
                x: .value("Time", tick.timestamp),
                y: .value("IV", tick.iv * 100)
            )
            .foregroundStyle(Theme.accentPurple)
            .lineStyle(StrokeStyle(lineWidth: 2))

            AreaMark(
                x: .value("Time", tick.timestamp),
                y: .value("IV", tick.iv * 100)
            )
            .foregroundStyle(
                LinearGradient(
                    colors: [Theme.accentPurple.opacity(0.3), Theme.accentPurple.opacity(0.0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 5)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let iv = value.as(Double.self) {
                        Text(String(format: "%.1f%%", iv))
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { value in
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(formatTime(date))
                            .font(.system(size: 9))
                            .foregroundStyle(Theme.textMuted)
                    }
                }
            }
        }
    }

    // MARK: - Stats Summary

    private var statsSummary: some View {
        HStack(spacing: 0) {
            statItem(
                title: L.chartsHigh,
                value: String(format: "%.2f", ticks.map(\.ltp).max() ?? 0),
                color: Theme.profit
            )
            Divider().frame(height: 30).background(Theme.textMuted.opacity(0.3))
            statItem(
                title: L.chartsLow,
                value: String(format: "%.2f", ticks.map(\.ltp).min() ?? 0),
                color: Theme.loss
            )
            Divider().frame(height: 30).background(Theme.textMuted.opacity(0.3))
            statItem(
                title: L.chartsAvgOI,
                value: formatNumber(ticks.map(\.oi).reduce(0, +) / max(ticks.count, 1)),
                color: Theme.accentOrange
            )
            Divider().frame(height: 30).background(Theme.textMuted.opacity(0.3))
            statItem(
                title: L.chartsAvgIV,
                value: String(format: "%.1f%%", (ticks.map(\.iv).reduce(0, +) / Double(max(ticks.count, 1))) * 100),
                color: Theme.accentPurple
            )
        }
        .padding(.vertical, 12)
        .background(Theme.surface)
    }

    private func statItem(title: String, value: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)
            Text(value)
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Loading View

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: Theme.accentBlue))
                .scaleEffect(1.2)
            Text(L.chartsLoadingOption)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
        }
    }

    // MARK: - Helpers

    private func chartOverlayGesture(proxy: ChartProxy) -> some View {
        GeometryReader { geometry in
            Rectangle()
                .fill(.clear)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let location = value.location
                            if let date: Date = proxy.value(atX: location.x) {
                                selectedTick = ticks.min(by: {
                                    abs($0.timestamp.timeIntervalSince(date)) < abs($1.timestamp.timeIntervalSince(date))
                                })
                            }
                        }
                        .onEnded { _ in
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                selectedTick = nil
                            }
                        }
                )
        }
    }

    @State private var isLiveData = false
    @State private var errorMessage: String?

    private func loadData() {
        isLoading = true
        isLiveData = false
        errorMessage = nil

        Task {
            // Try to fetch real data from Upstox if instrument key is available
            if let instrumentKey = option.instrumentKey,
               !instrumentKey.isEmpty,
               UpstoxAPIService.shared.isAuthenticated {
                do {
                    let candles = try await UpstoxAPIService.shared.fetchOptionHistoricalCandles(
                        instrumentKey: instrumentKey,
                        timeFrame: .fiveMin
                    )

                    if !candles.isEmpty {
                        await MainActor.run {
                            // Convert OHLCData to OptionTickData
                            ticks = candles.map { candle in
                                OptionTickData(
                                    timestamp: candle.timestamp,
                                    ltp: candle.close,
                                    oi: candle.volume > 0 ? candle.volume * 75 : option.openInterest,
                                    oiChange: 0,
                                    iv: option.impliedVolatility,
                                    volume: candle.volume
                                )
                            }
                            isLiveData = true
                            isLoading = false
                        }
                        print("✅ [OptionChart] Loaded \(candles.count) real candles for \(instrumentKey)")
                        return
                    }
                } catch {
                    print("📊 [OptionChart] Failed to load real data: \(error.localizedDescription)")
                }
            }

            // Fallback to simulated data
            await MainActor.run {
                ticks = ChartDataGenerator.generateOptionTicks(
                    basePrice: option.lastTradedPrice,
                    baseOI: option.openInterest,
                    baseIV: option.impliedVolatility
                )
                isLiveData = false
                isLoading = false
            }
        }
    }

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    private func formatNumber(_ num: Int) -> String {
        if num >= 10_000_000 {
            return String(format: "%.1fCr", Double(num) / 10_000_000)
        } else if num >= 100_000 {
            return String(format: "%.1fL", Double(num) / 100_000)
        } else if num >= 1000 {
            return String(format: "%.1fK", Double(num) / 1000)
        }
        return "\(num)"
    }
}

// MARK: - Preview

#Preview {
    let sampleOption = OptionData(
        strikePrice: 25000,
        optionType: .call,
        expiryDate: Date().addingTimeInterval(7 * 24 * 60 * 60),
        lastTradedPrice: 150.50,
        openInterest: 1500000,
        impliedVolatility: 0.15,
        underlyingValue: 25000
    )
    return OptionChartView(option: sampleOption)
        
}
