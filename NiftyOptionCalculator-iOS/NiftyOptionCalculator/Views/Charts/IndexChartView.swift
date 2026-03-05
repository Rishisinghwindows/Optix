import SwiftUI
import Charts

// MARK: - Index Chart View

struct IndexChartView: View {
    let index: TradingIndex
    let spotPrice: Double

    @State private var candles: [OHLCData] = []
    @State private var selectedTimeFrame: ChartTimeFrame = .fiveMin
    @State private var chartType: ChartDisplayType = .candle
    @State private var selectedCandle: OHLCData?
    @State private var isLoading = true
    @State private var isUsingLiveData = false
    @State private var errorMessage: String?

    @ObservedObject private var upstoxService = UpstoxAPIService.shared

    var body: some View {
        VStack(spacing: 0) {
            // Header
            chartHeader

            // Time frame selector
            timeFrameSelector

            // Main chart
            if isLoading {
                loadingView
            } else {
                chartContent
            }

            // Volume chart
            if !candles.isEmpty {
                volumeChart
            }
        }
        .background(Theme.background)
        .onAppear {
            loadData()
        }
        .onChange(of: selectedTimeFrame) { _, _ in
            loadData()
        }
    }

    // MARK: - Header

    private var chartHeader: some View {
        VStack(spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(index.displayName)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(Theme.textSecondary)

                        // Data source indicator
                        dataSourceBadge
                    }

                    if let lastCandle = candles.last {
                        Text(String(format: "%.2f", lastCandle.close))
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Theme.textPrimary)

                        HStack(spacing: 4) {
                            Image(systemName: lastCandle.isGreen ? "arrow.up" : "arrow.down")
                                .font(.system(size: 12, weight: .bold))
                            Text(String(format: "%+.2f (%.2f%%)", lastCandle.change, lastCandle.changePercent))
                                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                        }
                        .foregroundColor(lastCandle.isGreen ? Theme.profit : Theme.loss)
                    } else {
                        Text(String(format: "%.2f", spotPrice))
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Theme.textPrimary)
                    }
                }

                Spacer()

                // Chart type selector
                chartTypeSelector
            }

            // Selected candle info
            if let candle = selectedCandle {
                selectedCandleInfo(candle)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var chartTypeSelector: some View {
        HStack(spacing: 4) {
            ForEach(ChartDisplayType.allCases) { type in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        chartType = type
                    }
                } label: {
                    Image(systemName: type.icon)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(chartType == type ? .white : Theme.textMuted)
                        .frame(width: 36, height: 32)
                        .background {
                            if chartType == type {
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(Theme.accentBlue)
                            }
                        }
                }
            }
        }
        .padding(4)
        .background {
            RoundedRectangle(cornerRadius: 8)
                .fill(Theme.surface)
        }
    }

    private func selectedCandleInfo(_ candle: OHLCData) -> some View {
        HStack(spacing: 16) {
            infoItem("O", value: String(format: "%.2f", candle.open))
            infoItem("H", value: String(format: "%.2f", candle.high), color: Theme.profit)
            infoItem("L", value: String(format: "%.2f", candle.low), color: Theme.loss)
            infoItem("C", value: String(format: "%.2f", candle.close))
            Spacer()
            Text(formatTime(candle.timestamp))
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(Theme.textMuted)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background {
            RoundedRectangle(cornerRadius: 8)
                .fill(Theme.surface)
        }
    }

    private func infoItem(_ label: String, value: String, color: Color = .white) -> some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)
            Text(value)
                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                .foregroundColor(color)
        }
    }

    // MARK: - Time Frame Selector

    private var timeFrameSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(ChartTimeFrame.allCases) { tf in
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedTimeFrame = tf
                        }
                    } label: {
                        Text(tf.rawValue)
                            .font(.system(size: 13, weight: selectedTimeFrame == tf ? .bold : .medium))
                            .foregroundColor(selectedTimeFrame == tf ? .white : Theme.textSecondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background {
                                if selectedTimeFrame == tf {
                                    Capsule()
                                        .fill(Theme.accentGreen)
                                }
                            }
                    }
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 8)
        .background(Theme.surface.opacity(0.5))
    }

    // MARK: - Chart Content

    private var chartContent: some View {
        Group {
            if candles.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "chart.line.downtrend.xyaxis")
                        .font(.system(size: 28))
                        .foregroundColor(Theme.textMuted)
                    Text(errorMessage ?? L.optionChainNoData)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Theme.textMuted)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Theme.surface.opacity(0.35))
                        .overlay {
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Theme.border.opacity(0.35), lineWidth: 1)
                        }
                }
            } else {
                switch chartType {
                case .candle:
                    candlestickChart
                case .line:
                    lineChart
                case .area:
                    areaChart
                }
            }
        }
        .frame(height: 280)
        .padding(.horizontal, 8)
            }

    // MARK: - Candlestick Chart

    private var candlestickChart: some View {
        Chart(candles) { candle in
            // Wick (high-low line)
            RectangleMark(
                x: .value("Time", candle.timestamp),
                yStart: .value("Low", candle.low),
                yEnd: .value("High", candle.high),
                width: 1
            )
            .foregroundStyle(candle.isGreen ? Theme.profit : Theme.loss)

            // Body (open-close rectangle)
            RectangleMark(
                x: .value("Time", candle.timestamp),
                yStart: .value("Open", candle.bodyLow),
                yEnd: .value("Close", candle.bodyHigh),
                width: .fixed(candleWidth)
            )
            .foregroundStyle(candle.isGreen ? Theme.profit : Theme.loss)
        }
        .chartYScale(domain: yAxisDomain)
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 5)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(formatAxisTime(date))
                            .font(.system(size: 10))
                            .foregroundStyle(Theme.textMuted)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 6)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let price = value.as(Double.self) {
                        Text(String(format: "%.0f", price))
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
        .chartOverlay { proxy in
            GeometryReader { geometry in
                Rectangle()
                    .fill(.clear)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let location = value.location
                                if let date: Date = proxy.value(atX: location.x) {
                                    selectedCandle = candles.min(by: {
                                        abs($0.timestamp.timeIntervalSince(date)) < abs($1.timestamp.timeIntervalSince(date))
                                    })
                                }
                            }
                            .onEnded { _ in
                                // Keep selection visible for a moment
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                    selectedCandle = nil
                                }
                            }
                    )
            }
        }
    }

    // MARK: - Line Chart

    private var lineChart: some View {
        Chart(candles) { candle in
            LineMark(
                x: .value("Time", candle.timestamp),
                y: .value("Price", candle.close)
            )
            .foregroundStyle(
                candles.last?.isGreen == true ? Theme.profit : Theme.loss
            )
            .lineStyle(StrokeStyle(lineWidth: 2))

            if let selected = selectedCandle, selected.id == candle.id {
                PointMark(
                    x: .value("Time", candle.timestamp),
                    y: .value("Price", candle.close)
                )
                .foregroundStyle(.white)
                .symbolSize(60)
            }
        }
        .chartYScale(domain: yAxisDomain)
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 5)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(formatAxisTime(date))
                            .font(.system(size: 10))
                            .foregroundStyle(Theme.textMuted)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 6)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let price = value.as(Double.self) {
                        Text(String(format: "%.0f", price))
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
    }

    // MARK: - Area Chart

    private var areaChart: some View {
        Chart(candles) { candle in
            AreaMark(
                x: .value("Time", candle.timestamp),
                y: .value("Price", candle.close)
            )
            .foregroundStyle(
                LinearGradient(
                    colors: [
                        (candles.last?.isGreen == true ? Theme.profit : Theme.loss).opacity(0.4),
                        (candles.last?.isGreen == true ? Theme.profit : Theme.loss).opacity(0.0)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )

            LineMark(
                x: .value("Time", candle.timestamp),
                y: .value("Price", candle.close)
            )
            .foregroundStyle(candles.last?.isGreen == true ? Theme.profit : Theme.loss)
            .lineStyle(StrokeStyle(lineWidth: 2))
        }
        .chartYScale(domain: yAxisDomain)
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 5)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let date = value.as(Date.self) {
                        Text(formatAxisTime(date))
                            .font(.system(size: 10))
                            .foregroundStyle(Theme.textMuted)
                    }
                }
            }
        }
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 6)) { value in
                AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5, dash: [4, 4]))
                    .foregroundStyle(Theme.textMuted.opacity(0.3))
                AxisValueLabel {
                    if let price = value.as(Double.self) {
                        Text(String(format: "%.0f", price))
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundStyle(Theme.textSecondary)
                    }
                }
            }
        }
    }

    // MARK: - Volume Chart

    private var volumeChart: some View {
        Chart(candles) { candle in
            BarMark(
                x: .value("Time", candle.timestamp),
                y: .value("Volume", candle.volume)
            )
            .foregroundStyle(candle.isGreen ? Theme.profit.opacity(0.6) : Theme.loss.opacity(0.6))
        }
        .frame(height: 60)
        .padding(.horizontal, 8)
        .chartXAxis(.hidden)
        .chartYAxis {
            AxisMarks(position: .trailing, values: .automatic(desiredCount: 2)) { value in
                AxisValueLabel {
                    if let vol = value.as(Int.self) {
                        Text(formatVolume(vol))
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundStyle(Theme.textMuted)
                    }
                }
            }
        }
    }

    // MARK: - Loading View

    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: Theme.accentGreen))
                .scaleEffect(1.2)
            Text(L.chartsLoadingChart)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
        }
        .frame(height: 280)
    }

    // MARK: - Data Source Badge

    private var dataSourceBadge: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(isUsingLiveData ? Theme.profit : Theme.accentOrange)
                .frame(width: 6, height: 6)
            Text(isUsingLiveData ? "LIVE" : "DEMO")
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(isUsingLiveData ? Theme.profit : Theme.accentOrange)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background {
            RoundedRectangle(cornerRadius: 4)
                .fill((isUsingLiveData ? Theme.profit : Theme.accentOrange).opacity(0.15))
        }
    }

    // MARK: - Helpers

    private var candleWidth: CGFloat {
        switch selectedTimeFrame {
        case .oneMin: return 3
        case .fiveMin: return 4
        case .fifteenMin: return 5
        case .thirtyMin: return 6
        case .oneHour: return 7
        case .fourHour: return 8
        case .oneDay: return 6
        case .oneWeek: return 7
        }
    }

    private var yAxisDomain: ClosedRange<Double> {
        guard !candles.isEmpty else { return 0...100 }
        let prices = candles.flatMap { [$0.high, $0.low] }
        let minPrice = prices.min() ?? 0
        let maxPrice = prices.max() ?? 100
        let padding = (maxPrice - minPrice) * 0.05
        return (minPrice - padding)...(maxPrice + padding)
    }

    private func loadData() {
        isLoading = true
        errorMessage = nil

        Task {
            do {
                // Try to fetch live data from Upstox if authenticated
                if upstoxService.isAuthenticated {
                    let liveCandles = try await upstoxService.fetchHistoricalCandles(
                        index: index,
                        timeFrame: selectedTimeFrame
                    )

                    await MainActor.run {
                        if !liveCandles.isEmpty {
                            candles = liveCandles
                            isUsingLiveData = true
                            print("📊 [Chart] Loaded \(liveCandles.count) live candles")
                        } else {
                            candles = []
                            isUsingLiveData = false
                            errorMessage = L.optionChainNoData
                        }
                        isLoading = false
                    }
                } else {
                    // Not authenticated, show placeholder
                    await MainActor.run {
                        candles = []
                        isUsingLiveData = false
                        errorMessage = L.optionChainNoData
                        isLoading = false
                    }
                }
            } catch {
                print("📊 [Chart] Error fetching live data: \(error.localizedDescription)")
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    candles = []
                    isUsingLiveData = false
                    isLoading = false
                }
            }
        }
    }

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = selectedTimeFrame.minutes >= 1440 ? "dd MMM" : "HH:mm"
        return formatter.string(from: date)
    }

    private func formatAxisTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = selectedTimeFrame.minutes >= 1440 ? "dd/MM" : "HH:mm"
        return formatter.string(from: date)
    }

    private func formatVolume(_ volume: Int) -> String {
        if volume >= 1_000_000 {
            return String(format: "%.1fM", Double(volume) / 1_000_000)
        } else if volume >= 1000 {
            return String(format: "%.0fK", Double(volume) / 1000)
        }
        return "\(volume)"
    }
}

// MARK: - Preview

#Preview {
    IndexChartView(index: .nifty50, spotPrice: 25000)
        
}
