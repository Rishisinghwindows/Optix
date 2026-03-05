import SwiftUI
import WebKit

// MARK: - TradingView LightweightCharts WebView

struct TradingViewChart: UIViewRepresentable {
    let candles: [OHLCData]
    let chartType: ChartDisplayType
    let showVolume: Bool
    let showSMA: Bool
    @Binding var selectedCandle: OHLCData?

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(context.coordinator, name: "chartCallback")

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator

        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let html = generateChartHTML()
        webView.loadHTMLString(html, baseURL: nil)
    }

    private func generateChartHTML() -> String {
        let candleData = generateCandleDataJSON()
        let volumeData = generateVolumeDataJSON()
        let smaData = showSMA ? generateSMADataJSON() : "[]"

        let chartTypeJS: String
        switch chartType {
        case .candle:
            chartTypeJS = "Candlestick"
        case .line:
            chartTypeJS = "Line"
        case .area:
            chartTypeJS = "Area"
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <script src="https://unpkg.com/lightweight-charts@4.1.0/dist/lightweight-charts.standalone.production.js"></script>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body {
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: transparent;
                    -webkit-tap-highlight-color: transparent;
                    touch-action: manipulation;
                }
                #chart-container {
                    width: 100%;
                    height: 100%;
                    position: relative;
                }
                #tooltip {
                    position: absolute;
                    left: 12px;
                    top: 12px;
                    z-index: 1000;
                    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', sans-serif;
                    font-size: 11px;
                    background: rgba(30, 30, 35, 0.95);
                    border-radius: 8px;
                    padding: 8px 12px;
                    border: 1px solid rgba(255,255,255,0.1);
                    pointer-events: none;
                    display: none;
                    backdrop-filter: blur(10px);
                    -webkit-backdrop-filter: blur(10px);
                }
                .tooltip-row {
                    display: flex;
                    justify-content: space-between;
                    gap: 16px;
                    margin-bottom: 3px;
                }
                .tooltip-row:last-child { margin-bottom: 0; }
                .tooltip-label { color: #888; }
                .tooltip-value { font-weight: 600; font-family: 'SF Mono', monospace; }
                .tooltip-green { color: #00C805; }
                .tooltip-red { color: #FF3B30; }
                .tooltip-time {
                    color: #666;
                    font-size: 10px;
                    border-top: 1px solid rgba(255,255,255,0.1);
                    padding-top: 6px;
                    margin-top: 6px;
                }
            </style>
        </head>
        <body>
            <div id="chart-container"></div>
            <div id="tooltip"></div>
            <script>
                const container = document.getElementById('chart-container');
                const tooltip = document.getElementById('tooltip');

                // Chart options matching dark trading theme
                const chart = LightweightCharts.createChart(container, {
                    width: container.clientWidth,
                    height: container.clientHeight,
                    layout: {
                        background: { type: 'solid', color: 'transparent' },
                        textColor: '#9CA3AF',
                        fontFamily: '-apple-system, BlinkMacSystemFont, SF Pro Text, sans-serif',
                        fontSize: 11
                    },
                    grid: {
                        vertLines: { color: 'rgba(255, 255, 255, 0.04)', style: 1 },
                        horzLines: { color: 'rgba(255, 255, 255, 0.04)', style: 1 }
                    },
                    crosshair: {
                        mode: LightweightCharts.CrosshairMode.Normal,
                        vertLine: {
                            width: 1,
                            color: 'rgba(255, 255, 255, 0.3)',
                            style: LightweightCharts.LineStyle.Dashed,
                            labelBackgroundColor: '#1E1E23'
                        },
                        horzLine: {
                            width: 1,
                            color: 'rgba(255, 255, 255, 0.3)',
                            style: LightweightCharts.LineStyle.Dashed,
                            labelBackgroundColor: '#1E1E23'
                        }
                    },
                    rightPriceScale: {
                        borderColor: 'rgba(255, 255, 255, 0.1)',
                        scaleMargins: { top: 0.1, bottom: \(showVolume ? "0.25" : "0.1") }
                    },
                    timeScale: {
                        borderColor: 'rgba(255, 255, 255, 0.1)',
                        timeVisible: true,
                        secondsVisible: false,
                        tickMarkFormatter: (time) => {
                            const date = new Date(time * 1000);
                            const hours = date.getHours().toString().padStart(2, '0');
                            const mins = date.getMinutes().toString().padStart(2, '0');
                            return hours + ':' + mins;
                        }
                    },
                    handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
                    handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true }
                });

                // Create main series based on chart type
                let mainSeries;
                const chartType = '\(chartTypeJS)';

                if (chartType === 'Candlestick') {
                    mainSeries = chart.addCandlestickSeries({
                        upColor: '#00C805',
                        downColor: '#FF3B30',
                        borderUpColor: '#00C805',
                        borderDownColor: '#FF3B30',
                        wickUpColor: '#00C805',
                        wickDownColor: '#FF3B30'
                    });
                } else if (chartType === 'Line') {
                    mainSeries = chart.addLineSeries({
                        color: '#007AFF',
                        lineWidth: 2,
                        crosshairMarkerVisible: true,
                        crosshairMarkerRadius: 5,
                        crosshairMarkerBorderColor: '#fff',
                        crosshairMarkerBackgroundColor: '#007AFF'
                    });
                } else if (chartType === 'Area') {
                    mainSeries = chart.addAreaSeries({
                        topColor: 'rgba(0, 122, 255, 0.4)',
                        bottomColor: 'rgba(0, 122, 255, 0.0)',
                        lineColor: '#007AFF',
                        lineWidth: 2
                    });
                }

                // Candle data
                const candleData = \(candleData);

                if (chartType === 'Candlestick') {
                    mainSeries.setData(candleData);
                } else {
                    // For line/area, use close prices
                    const lineData = candleData.map(d => ({ time: d.time, value: d.close }));
                    mainSeries.setData(lineData);
                }

                // SMA overlay
                const showSMA = \(showSMA ? "true" : "false");
                if (showSMA) {
                    const smaData = \(smaData);
                    if (smaData.length > 0) {
                        const smaSeries = chart.addLineSeries({
                            color: '#FF9500',
                            lineWidth: 1,
                            crosshairMarkerVisible: false,
                            priceLineVisible: false,
                            lastValueVisible: false
                        });
                        smaSeries.setData(smaData);
                    }
                }

                // Volume series
                const showVolume = \(showVolume ? "true" : "false");
                if (showVolume) {
                    const volumeSeries = chart.addHistogramSeries({
                        color: '#26a69a',
                        priceFormat: { type: 'volume' },
                        priceScaleId: '',
                        scaleMargins: { top: 0.8, bottom: 0 }
                    });

                    const volumeData = \(volumeData);
                    volumeSeries.setData(volumeData);
                }

                // Crosshair move handler for tooltip
                chart.subscribeCrosshairMove((param) => {
                    if (!param.time || !param.seriesData.size) {
                        tooltip.style.display = 'none';
                        return;
                    }

                    const data = param.seriesData.get(mainSeries);
                    if (!data) {
                        tooltip.style.display = 'none';
                        return;
                    }

                    const date = new Date(param.time * 1000);
                    const timeStr = date.toLocaleDateString('en-IN', {
                        day: '2-digit',
                        month: 'short',
                        hour: '2-digit',
                        minute: '2-digit'
                    });

                    let tooltipContent = '';

                    if (chartType === 'Candlestick') {
                        const change = data.close - data.open;
                        const changePercent = ((change / data.open) * 100).toFixed(2);
                        const colorClass = change >= 0 ? 'tooltip-green' : 'tooltip-red';

                        tooltipContent = `
                            <div class="tooltip-row">
                                <span class="tooltip-label">O</span>
                                <span class="tooltip-value">${data.open.toFixed(2)}</span>
                            </div>
                            <div class="tooltip-row">
                                <span class="tooltip-label">H</span>
                                <span class="tooltip-value tooltip-green">${data.high.toFixed(2)}</span>
                            </div>
                            <div class="tooltip-row">
                                <span class="tooltip-label">L</span>
                                <span class="tooltip-value tooltip-red">${data.low.toFixed(2)}</span>
                            </div>
                            <div class="tooltip-row">
                                <span class="tooltip-label">C</span>
                                <span class="tooltip-value ${colorClass}">${data.close.toFixed(2)}</span>
                            </div>
                            <div class="tooltip-row">
                                <span class="tooltip-label">Chg</span>
                                <span class="tooltip-value ${colorClass}">${change >= 0 ? '+' : ''}${change.toFixed(2)} (${changePercent}%)</span>
                            </div>
                            <div class="tooltip-time">${timeStr}</div>
                        `;
                    } else {
                        tooltipContent = `
                            <div class="tooltip-row">
                                <span class="tooltip-label">Price</span>
                                <span class="tooltip-value">${data.value.toFixed(2)}</span>
                            </div>
                            <div class="tooltip-time">${timeStr}</div>
                        `;
                    }

                    tooltip.innerHTML = tooltipContent;
                    tooltip.style.display = 'block';

                    // Send to Swift
                    if (chartType === 'Candlestick') {
                        window.webkit.messageHandlers.chartCallback.postMessage({
                            type: 'candleSelect',
                            open: data.open,
                            high: data.high,
                            low: data.low,
                            close: data.close,
                            time: param.time
                        });
                    }
                });

                // Fit content
                chart.timeScale().fitContent();

                // Handle resize
                const resizeObserver = new ResizeObserver(entries => {
                    for (let entry of entries) {
                        chart.resize(entry.contentRect.width, entry.contentRect.height);
                    }
                });
                resizeObserver.observe(container);
            </script>
        </body>
        </html>
        """
    }

    private func generateCandleDataJSON() -> String {
        let sortedCandles = candles.sorted { $0.timestamp < $1.timestamp }
        let dataArray = sortedCandles.map { candle -> String in
            let timestamp = Int(candle.timestamp.timeIntervalSince1970)
            return """
            {"time":\(timestamp),"open":\(candle.open),"high":\(candle.high),"low":\(candle.low),"close":\(candle.close)}
            """
        }
        return "[\(dataArray.joined(separator: ","))]"
    }

    private func generateVolumeDataJSON() -> String {
        let sortedCandles = candles.sorted { $0.timestamp < $1.timestamp }
        let dataArray = sortedCandles.map { candle -> String in
            let timestamp = Int(candle.timestamp.timeIntervalSince1970)
            let color = candle.isGreen ? "rgba(0,200,5,0.5)" : "rgba(255,59,48,0.5)"
            return """
            {"time":\(timestamp),"value":\(candle.volume),"color":"\(color)"}
            """
        }
        return "[\(dataArray.joined(separator: ","))]"
    }

    private func generateSMADataJSON() -> String {
        let sortedCandles = candles.sorted { $0.timestamp < $1.timestamp }
        let period = 20
        guard sortedCandles.count >= period else { return "[]" }

        var smaData: [String] = []
        for i in (period - 1)..<sortedCandles.count {
            let slice = sortedCandles[(i - period + 1)...i]
            let avg = slice.map { $0.close }.reduce(0, +) / Double(period)
            let timestamp = Int(sortedCandles[i].timestamp.timeIntervalSince1970)
            smaData.append("{\"time\":\(timestamp),\"value\":\(avg)}")
        }
        return "[\(smaData.joined(separator: ","))]"
    }

    // MARK: - Coordinator

    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: TradingViewChart

        init(_ parent: TradingViewChart) {
            self.parent = parent
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let dict = message.body as? [String: Any],
                  let type = dict["type"] as? String else { return }

            if type == "candleSelect" {
                // Handle candle selection if needed
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            // Chart loaded successfully
        }
    }
}

// MARK: - Investing.com Embedded Chart Widget
// Real-time charts with proper NSE/BSE data licensing

struct InvestingComChart: UIViewRepresentable {
    let pairId: Int      // Investing.com pair ID (e.g., 17940 for NIFTY 50)
    let interval: Int    // Interval in minutes (1, 5, 15, 30, 60, 300=5H, D, W, M)
    let theme: String    // "dark" or "light"

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.mediaTypesRequiringUserActionForPlayback = []

        let webView = WKWebView(frame: .zero, configuration: configuration)
        #if os(iOS)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        #endif
        webView.navigationDelegate = context.coordinator

        let html = generateInvestingComHTML()
        webView.loadHTMLString(html, baseURL: URL(string: "https://www.investing.com"))

        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let html = generateInvestingComHTML()
        webView.loadHTMLString(html, baseURL: URL(string: "https://www.investing.com"))
    }

    private func generateInvestingComHTML() -> String {
        let bgColor = theme == "dark" ? "#0D0D0D" : "#FFFFFF"

        // Investing.com Technical Chart Widget (forexpros.com is their chart backend)
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body {
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: \(bgColor);
                    -webkit-touch-callout: none;
                    -webkit-user-select: none;
                }
                iframe {
                    width: 100%;
                    height: 100%;
                    border: none;
                }
            </style>
        </head>
        <body>
            <iframe
                src="https://tvc4.investing.com/init.php?family_prefix=tvc4&pair_ID=\(pairId)&interval=\(interval)&width=100%25&height=100%25&lang_ID=1&timezone_ID=20"
                frameborder="0"
                scrolling="no"
                allowtransparency="true">
            </iframe>
        </body>
        </html>
        """
    }

    class Coordinator: NSObject, WKNavigationDelegate {
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            print("📊 [Investing.com] Chart loaded successfully")
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            print("📊 [Investing.com] Chart failed to load: \(error.localizedDescription)")
        }
    }
}

// MARK: - TradingView Embedded Widget Chart (Backup - limited NSE support)
// Note: TradingView widgets don't support NSE symbols due to data licensing

struct TradingViewEmbeddedChart: UIViewRepresentable {
    let symbol: String
    let interval: String
    let theme: String
    let chartStyle: Int  // 1=Candles, 2=Line, 3=Area

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.mediaTypesRequiringUserActionForPlayback = []

        let webView = WKWebView(frame: .zero, configuration: configuration)
        #if os(iOS)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = true
        webView.scrollView.bounces = false
        #endif
        webView.navigationDelegate = context.coordinator

        // Load the chart immediately
        let html = generateTradingViewHTML()
        webView.loadHTMLString(html, baseURL: URL(string: "https://www.tradingview.com"))

        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // Reload when parameters change
        let html = generateTradingViewHTML()
        webView.loadHTMLString(html, baseURL: URL(string: "https://www.tradingview.com"))
    }

    private func generateTradingViewHTML() -> String {
        let bgColor = theme == "dark" ? "#0D0D0D" : "#FFFFFF"
        let gridColor = theme == "dark" ? "rgba(255, 255, 255, 0.06)" : "rgba(0, 0, 0, 0.06)"

        // TradingView Advanced Chart Widget - Free & No Auth Required
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body {
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: \(bgColor);
                    -webkit-touch-callout: none;
                    -webkit-user-select: none;
                }
                .tradingview-widget-container {
                    width: 100%;
                    height: 100%;
                }
                .tradingview-widget-container__widget {
                    width: 100%;
                    height: 100%;
                }
            </style>
        </head>
        <body>
            <div class="tradingview-widget-container">
                <div class="tradingview-widget-container__widget"></div>
                <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js" async>
                {
                    "autosize": true,
                    "symbol": "\(symbol)",
                    "interval": "\(interval)",
                    "timezone": "Asia/Kolkata",
                    "theme": "\(theme)",
                    "style": "\(chartStyle)",
                    "locale": "en",
                    "enable_publishing": false,
                    "allow_symbol_change": false,
                    "hide_top_toolbar": false,
                    "hide_legend": false,
                    "hide_side_toolbar": true,
                    "save_image": false,
                    "calendar": false,
                    "hide_volume": false,
                    "support_host": "https://www.tradingview.com",
                    "backgroundColor": "\(bgColor)",
                    "gridColor": "\(gridColor)"
                }
                </script>
            </div>
        </body>
        </html>
        """
    }

    class Coordinator: NSObject, WKNavigationDelegate {
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            print("📊 [TradingView] Chart loaded successfully")
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            print("📊 [TradingView] Chart failed to load: \(error.localizedDescription)")
        }
    }
}

// MARK: - Professional Index Chart View (Using Investing.com Widget)

struct ProfessionalIndexChartView: View {
    let index: TradingIndex
    let spotPrice: Double

    @State private var selectedTimeFrame: ChartTimeFrame = .fifteenMin
    @State private var chartStyle: Int = 1  // 1=Candles, 2=Line, 3=Area
    @State private var isLoading = true
    @Environment(\.colorScheme) private var colorScheme

    // Map TradingIndex to Investing.com pair ID
    // These are the numeric IDs used by Investing.com's chart API
    private var investingComPairId: Int {
        switch index {
        case .nifty50:
            return 17940      // NIFTY 50
        case .bankNifty:
            return 17950      // Bank Nifty
        case .niftyFinService:
            return 17940      // Using NIFTY 50 (no specific ID found)
        case .midcapSelect:
            return 17940      // Using NIFTY 50 (no specific ID found)
        case .sensex:
            return 17941      // BSE SENSEX
        case .bankex:
            return 17941      // Using SENSEX (no specific ID found)
        }
    }

    // Map ChartTimeFrame to Investing.com interval (in minutes)
    private var investingComInterval: Int {
        switch selectedTimeFrame {
        case .oneMin:
            return 1
        case .fiveMin:
            return 5
        case .fifteenMin:
            return 15
        case .thirtyMin:
            return 30
        case .oneHour:
            return 60
        case .fourHour:
            return 240
        case .oneDay:
            return 1440      // Daily = 1440 minutes
        case .oneWeek:
            return 10080     // Weekly = 10080 minutes
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            chartHeader

            // Time frame selector
            timeFrameSelector

            // Investing.com Chart - Has proper NSE/BSE data licensing
            ZStack {
                InvestingComChart(
                    pairId: investingComPairId,
                    interval: investingComInterval,
                    theme: colorScheme == .dark ? "dark" : "light"
                )

                if isLoading {
                    loadingOverlay
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.background)
            .onAppear {
                // Chart will load automatically
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    isLoading = false
                }
            }
        }
        .background(Theme.background)
        .edgesIgnoringSafeArea(.bottom)
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

                        // TradingView badge
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

                    Text(String(format: "%.2f", spotPrice))
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                // Chart style controls
                chartControls
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var chartControls: some View {
        HStack(spacing: 8) {
            // Chart style selector (Candle/Line/Area)
            HStack(spacing: 2) {
                ForEach(ChartDisplayType.allCases) { type in
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            switch type {
                            case .candle:
                                chartStyle = 1
                            case .line:
                                chartStyle = 2
                            case .area:
                                chartStyle = 3
                            }
                        }
                    } label: {
                        Image(systemName: type.icon)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(chartStyle == type.tradingViewStyle ? .white : Theme.textMuted)
                            .frame(width: 32, height: 28)
                            .background {
                                if chartStyle == type.tradingViewStyle {
                                    RoundedRectangle(cornerRadius: 5)
                                        .fill(Theme.accentBlue)
                                }
                            }
                    }
                }
            }
            .padding(3)
            .background {
                RoundedRectangle(cornerRadius: 7)
                    .fill(Theme.surface)
            }
        }
    }

    // MARK: - Time Frame Selector

    private var timeFrameSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(ChartTimeFrame.allCases) { tf in
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isLoading = true
                            selectedTimeFrame = tf
                            // Give time for chart to reload
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                isLoading = false
                            }
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

    // MARK: - Loading Overlay

    private var loadingOverlay: some View {
        ZStack {
            Theme.background.opacity(0.8)

            VStack(spacing: 16) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Theme.accentGreen))
                    .scaleEffect(1.2)
                Text(L.chartsLoadingChart)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
            }
        }
    }
}

// MARK: - Full Screen Investing.com Chart

struct FullScreenProfessionalChartView: View {
    let index: TradingIndex
    let spotPrice: Double
    let spotChange: Double
    let spotChangePercent: Double

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @State private var selectedTimeFrame: Int = 15
    @State private var chartStyle: Int = 1

    // Map TradingIndex to Investing.com pair ID
    private var investingComPairId: Int {
        switch index {
        case .nifty50:
            return 17940      // NIFTY 50
        case .bankNifty:
            return 17950      // Bank Nifty
        case .niftyFinService:
            return 17940      // Using NIFTY 50 (no specific ID found)
        case .midcapSelect:
            return 17940      // Using NIFTY 50 (no specific ID found)
        case .sensex:
            return 17941      // BSE SENSEX
        case .bankex:
            return 17941      // Using SENSEX (no specific ID found)
        }
    }

    private let timeFrames: [(String, Int)] = [
        ("1m", 1),
        ("5m", 5),
        ("15m", 15),
        ("30m", 30),
        ("1H", 60),
        ("4H", 240),
        ("1D", 1440),
        ("1W", 10080)
    ]

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Theme.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar with dismiss button
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Image(systemName: index.icon)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(hex: index.themeColor))

                            Text(index.displayName)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Theme.textPrimary)

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

                    // Chart style toggle
                    HStack(spacing: 2) {
                        ForEach([1, 2, 3], id: \.self) { style in
                            Button {
                                chartStyle = style
                            } label: {
                                Image(systemName: style == 1 ? "chart.bar.fill" : (style == 2 ? "chart.line.uptrend.xyaxis" : "chart.xyaxis.line"))
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(chartStyle == style ? .white : Theme.textMuted)
                                    .frame(width: 28, height: 24)
                                    .background {
                                        if chartStyle == style {
                                            RoundedRectangle(cornerRadius: 4)
                                                .fill(Theme.accentBlue)
                                        }
                                    }
                            }
                        }
                    }
                    .padding(2)
                    .background {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Theme.surface)
                    }

                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 28))
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(.leading, 8)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)

                // Time frame selector
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 4) {
                        ForEach(timeFrames, id: \.1) { (label, value) in
                            Button {
                                selectedTimeFrame = value
                            } label: {
                                Text(label)
                                    .font(.system(size: 13, weight: selectedTimeFrame == value ? .bold : .medium))
                                    .foregroundColor(selectedTimeFrame == value ? .white : Theme.textSecondary)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background {
                                        if selectedTimeFrame == value {
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

                // Full screen Investing.com chart
                InvestingComChart(
                    pairId: investingComPairId,
                    interval: selectedTimeFrame,
                    theme: colorScheme == .dark ? "dark" : "light"
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

// MARK: - Preview

#Preview {
    ProfessionalIndexChartView(index: .nifty50, spotPrice: 25000)
}
