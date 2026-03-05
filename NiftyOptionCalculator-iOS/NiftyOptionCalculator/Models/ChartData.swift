import Foundation

// MARK: - OHLC Candle Data

struct OHLCData: Identifiable, Equatable {
    let id: UUID
    let timestamp: Date
    let open: Double
    let high: Double
    let low: Double
    let close: Double
    let volume: Int

    init(id: UUID = UUID(), timestamp: Date, open: Double, high: Double, low: Double, close: Double, volume: Int = 0) {
        self.id = id
        self.timestamp = timestamp
        self.open = open
        self.high = high
        self.low = low
        self.close = close
        self.volume = volume
    }

    var isGreen: Bool { close >= open }
    var bodyHigh: Double { max(open, close) }
    var bodyLow: Double { min(open, close) }
    var change: Double { close - open }
    var changePercent: Double { open > 0 ? (change / open) * 100 : 0 }
}

// MARK: - Chart Time Frame

enum ChartTimeFrame: String, CaseIterable, Identifiable {
    case oneMin = "1m"
    case fiveMin = "5m"
    case fifteenMin = "15m"
    case thirtyMin = "30m"
    case oneHour = "1H"
    case fourHour = "4H"
    case oneDay = "1D"
    case oneWeek = "1W"

    var id: String { rawValue }

    var minutes: Int {
        switch self {
        case .oneMin: return 1
        case .fiveMin: return 5
        case .fifteenMin: return 15
        case .thirtyMin: return 30
        case .oneHour: return 60
        case .fourHour: return 240
        case .oneDay: return 1440
        case .oneWeek: return 10080
        }
    }

    var candleCount: Int {
        switch self {
        case .oneMin: return 60
        case .fiveMin: return 78  // 6.5 hours of trading
        case .fifteenMin: return 52
        case .thirtyMin: return 26
        case .oneHour: return 30
        case .fourHour: return 30
        case .oneDay: return 60
        case .oneWeek: return 52
        }
    }

    /// Upstox API interval parameter
    var upstoxInterval: String {
        switch self {
        case .oneMin: return "1minute"
        case .fiveMin: return "5minute"
        case .fifteenMin: return "15minute"
        case .thirtyMin: return "30minute"
        case .oneHour: return "60minute"
        case .fourHour: return "60minute"  // Will fetch 4x more and aggregate
        case .oneDay: return "day"
        case .oneWeek: return "week"
        }
    }

    /// Whether this timeframe uses intraday API
    var isIntraday: Bool {
        switch self {
        case .oneMin, .fiveMin, .fifteenMin, .thirtyMin, .oneHour, .fourHour:
            return true
        case .oneDay, .oneWeek:
            return false
        }
    }

    /// Days of historical data to fetch
    var historicalDays: Int {
        switch self {
        case .oneMin: return 1
        case .fiveMin: return 5
        case .fifteenMin: return 10
        case .thirtyMin: return 15
        case .oneHour: return 30
        case .fourHour: return 60
        case .oneDay: return 365
        case .oneWeek: return 730
        }
    }
}

// MARK: - Chart Display Type

enum ChartDisplayType: String, CaseIterable, Identifiable {
    case candle = "Candle"
    case line = "Line"
    case area = "Area"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .candle: return "chart.bar.fill"
        case .line: return "chart.xyaxis.line"
        case .area: return "chart.line.uptrend.xyaxis.circle.fill"
        }
    }

    /// TradingView chart style number (1=Candles, 2=Line, 3=Area)
    var tradingViewStyle: Int {
        switch self {
        case .candle: return 1
        case .line: return 2
        case .area: return 3
        }
    }
}

// MARK: - Option Tick Data (for option price charts)

struct OptionTickData: Identifiable, Equatable {
    let id: UUID
    let timestamp: Date
    let ltp: Double
    let oi: Int
    let oiChange: Int
    let iv: Double
    let volume: Int

    init(id: UUID = UUID(), timestamp: Date, ltp: Double, oi: Int, oiChange: Int = 0, iv: Double, volume: Int = 0) {
        self.id = id
        self.timestamp = timestamp
        self.ltp = ltp
        self.oi = oi
        self.oiChange = oiChange
        self.iv = iv
        self.volume = volume
    }
}

// MARK: - Chart Data Generator (Mock Data - Deterministic)

final class ChartDataGenerator {

    /// Deterministic pseudo-random function based on seed
    private static func deterministicValue(seed: Int, index: Int, range: ClosedRange<Double>) -> Double {
        // Use a simple hash-like function for deterministic "randomness"
        let hash = abs((seed * 31 + index * 17) % 1000)
        let normalized = Double(hash) / 1000.0  // 0.0 to 1.0
        return range.lowerBound + normalized * (range.upperBound - range.lowerBound)
    }

    /// Generate realistic OHLC candle data for index (DETERMINISTIC - no random values)
    static func generateIndexCandles(
        basePrice: Double,
        timeFrame: ChartTimeFrame,
        trend: Double = 0.0001  // Slight upward bias
    ) -> [OHLCData] {
        var candles: [OHLCData] = []

        // Use basePrice as seed for deterministic generation
        let seed = Int(basePrice) % 10000

        // Start slightly below base price (deterministic offset)
        var price = basePrice * 0.985
        let calendar = Calendar.current
        let now = Date()
        let count = timeFrame.candleCount

        // Volatility based on timeframe
        let baseVolatility: Double = {
            switch timeFrame {
            case .oneMin: return 0.0003
            case .fiveMin: return 0.0008
            case .fifteenMin: return 0.0015
            case .thirtyMin: return 0.002
            case .oneHour: return 0.003
            case .fourHour: return 0.005
            case .oneDay: return 0.01
            case .oneWeek: return 0.025
            }
        }()

        // Pre-generate a deterministic price path
        // Create a realistic uptrend with some pullbacks
        for i in (0..<count).reversed() {
            let timestamp = calendar.date(byAdding: .minute, value: -i * timeFrame.minutes, to: now) ?? now
            let candleIndex = count - i - 1  // 0 to count-1

            // Generate deterministic OHLC
            let volatility = baseVolatility * price

            // Deterministic direction based on candle position
            // Creates a pattern: mostly up with occasional down candles
            let directionSeed = deterministicValue(seed: seed, index: candleIndex * 3, range: -1.0...1.0)
            let direction = directionSeed + trend

            let open = price
            let changeMagnitude = deterministicValue(seed: seed, index: candleIndex * 5 + 1, range: 0.5...2.0)
            let change = direction * volatility * changeMagnitude
            let close = price + change

            // High/Low with deterministic wicks
            let wickUpFactor = deterministicValue(seed: seed, index: candleIndex * 7 + 2, range: 0.1...0.5)
            let wickDownFactor = deterministicValue(seed: seed, index: candleIndex * 11 + 3, range: 0.1...0.5)
            let wickUp = wickUpFactor * volatility
            let wickDown = wickDownFactor * volatility
            let high = max(open, close) + wickUp
            let low = min(open, close) - wickDown

            // Deterministic volume
            let volumeMultiplier = 1 + abs(change / price) * 10
            let baseVolume = deterministicValue(seed: seed, index: candleIndex * 13 + 4, range: 50000...200000)
            let volume = Int(baseVolume * volumeMultiplier)

            candles.append(OHLCData(
                timestamp: timestamp,
                open: open,
                high: high,
                low: low,
                close: close,
                volume: volume
            ))

            price = close
        }

        return candles
    }

    /// Generate option price tick data
    static func generateOptionTicks(
        basePrice: Double,
        baseOI: Int,
        baseIV: Double,
        count: Int = 78
    ) -> [OptionTickData] {
        var ticks: [OptionTickData] = []
        var price = basePrice
        var oi = baseOI
        var iv = baseIV
        let calendar = Calendar.current
        let now = Date()

        for i in (0..<count).reversed() {
            let timestamp = calendar.date(byAdding: .minute, value: -i * 5, to: now) ?? now

            // Price movement
            let priceChange = Double.random(in: -0.03...0.03) * price
            price = max(0.05, price + priceChange)

            // OI changes (can be positive or negative)
            let oiChange = Int.random(in: -2000...3000)
            oi = max(0, oi + oiChange)

            // IV changes slowly
            let ivChange = Double.random(in: -0.002...0.002)
            iv = max(0.05, min(1.5, iv + ivChange))

            // Volume
            let volume = Int.random(in: 500...10000)

            ticks.append(OptionTickData(
                timestamp: timestamp,
                ltp: price,
                oi: oi,
                oiChange: oiChange,
                iv: iv,
                volume: volume
            ))
        }

        return ticks
    }
}
