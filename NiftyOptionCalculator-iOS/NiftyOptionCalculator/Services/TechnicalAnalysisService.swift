import Foundation
import SwiftUI

// MARK: - Technical Analysis Service

final class TechnicalAnalysisService {
    static let shared = TechnicalAnalysisService()

    private init() {}

    // MARK: - Main Analysis Entry Point

    func analyze(candles: [OHLCData]) -> TechnicalAnalysisResult {
        guard candles.count >= 20 else {
            return TechnicalAnalysisResult.empty
        }

        let closes = candles.map { $0.close }
        let highs = candles.map { $0.high }
        let lows = candles.map { $0.low }
        let volumes = candles.map { Double($0.volume) }

        // Calculate all indicators
        let sma20 = calculateSMA(prices: closes, period: 20)
        let sma50 = calculateSMA(prices: closes, period: 50)
        let sma200 = calculateSMA(prices: closes, period: 200)
        let ema9 = calculateEMA(prices: closes, period: 9)
        let ema21 = calculateEMA(prices: closes, period: 21)

        let rsi = calculateRSI(prices: closes, period: 14)
        let macd = calculateMACD(prices: closes)
        let atr = calculateATR(highs: highs, lows: lows, closes: closes, period: 14)
        let bollingerBands = calculateBollingerBands(prices: closes, period: 20)
        let vwap = calculateVWAP(candles: candles)
        let volumeAnalysis = analyzeVolume(volumes: volumes)

        // Determine trend
        let trend = determineTrend(
            currentPrice: closes.last ?? 0,
            sma20: sma20,
            sma50: sma50,
            sma200: sma200,
            ema9: ema9,
            ema21: ema21
        )

        // Calculate support and resistance from price action
        let supportResistance = calculateSupportResistance(
            highs: highs,
            lows: lows,
            closes: closes
        )

        // Generate overall signal
        let signal = generateOverallSignal(
            rsi: rsi,
            macd: macd,
            trend: trend,
            currentPrice: closes.last ?? 0,
            bollingerBands: bollingerBands
        )

        // Generate technical insights
        let insights = generateInsights(
            rsi: rsi,
            macd: macd,
            trend: trend,
            bollingerBands: bollingerBands,
            currentPrice: closes.last ?? 0,
            sma20: sma20,
            sma50: sma50,
            volumeAnalysis: volumeAnalysis
        )

        // Detect candlestick patterns
        let candlestickPatterns = CandlestickPatternService.shared.detectPatterns(candles: candles)

        return TechnicalAnalysisResult(
            trend: trend,
            signal: signal,
            rsi: rsi,
            macd: macd,
            sma20: sma20,
            sma50: sma50,
            sma200: sma200,
            ema9: ema9,
            ema21: ema21,
            atr: atr,
            bollingerBands: bollingerBands,
            vwap: vwap,
            supportLevels: supportResistance.supports,
            resistanceLevels: supportResistance.resistances,
            volumeAnalysis: volumeAnalysis,
            insights: insights,
            candlestickPatterns: candlestickPatterns
        )
    }

    // MARK: - Simple Moving Average (SMA)

    func calculateSMA(prices: [Double], period: Int) -> Double? {
        guard prices.count >= period else { return nil }
        let slice = prices.suffix(period)
        return slice.reduce(0, +) / Double(period)
    }

    // MARK: - Exponential Moving Average (EMA)

    func calculateEMA(prices: [Double], period: Int) -> Double? {
        guard prices.count >= period else { return nil }

        let multiplier = 2.0 / Double(period + 1)

        // Start with SMA for first EMA value
        var ema = prices.prefix(period).reduce(0, +) / Double(period)

        // Calculate EMA for remaining prices
        for i in period..<prices.count {
            ema = (prices[i] - ema) * multiplier + ema
        }

        return ema
    }

    // MARK: - Relative Strength Index (RSI)

    func calculateRSI(prices: [Double], period: Int = 14) -> RSIResult {
        guard prices.count > period else {
            return RSIResult(value: 50, condition: .neutral)
        }

        var gains: [Double] = []
        var losses: [Double] = []

        for i in 1..<prices.count {
            let change = prices[i] - prices[i - 1]
            if change > 0 {
                gains.append(change)
                losses.append(0)
            } else {
                gains.append(0)
                losses.append(abs(change))
            }
        }

        // Calculate average gain and loss
        let avgGain = gains.suffix(period).reduce(0, +) / Double(period)
        let avgLoss = losses.suffix(period).reduce(0, +) / Double(period)

        guard avgLoss > 0 else {
            return RSIResult(value: 100, condition: .overbought)
        }

        let rs = avgGain / avgLoss
        let rsi = 100 - (100 / (1 + rs))

        let condition: RSICondition
        if rsi >= 70 {
            condition = .overbought
        } else if rsi >= 60 {
            condition = .slightlyOverbought
        } else if rsi <= 30 {
            condition = .oversold
        } else if rsi <= 40 {
            condition = .slightlyOversold
        } else {
            condition = .neutral
        }

        return RSIResult(value: rsi, condition: condition)
    }

    // MARK: - MACD (Moving Average Convergence Divergence)

    func calculateMACD(prices: [Double], fastPeriod: Int = 12, slowPeriod: Int = 26, signalPeriod: Int = 9) -> MACDResult {
        guard prices.count >= slowPeriod + signalPeriod else {
            return MACDResult(macdLine: 0, signalLine: 0, histogram: 0, crossover: .none)
        }

        // Calculate fast and slow EMAs
        let fastEMA = calculateEMA(prices: prices, period: fastPeriod) ?? 0
        let slowEMA = calculateEMA(prices: prices, period: slowPeriod) ?? 0

        let macdLine = fastEMA - slowEMA

        // Calculate MACD values for signal line
        var macdValues: [Double] = []
        for i in slowPeriod..<prices.count {
            let slice = Array(prices.prefix(i + 1))
            let fast = calculateEMA(prices: slice, period: fastPeriod) ?? 0
            let slow = calculateEMA(prices: slice, period: slowPeriod) ?? 0
            macdValues.append(fast - slow)
        }

        // Signal line is EMA of MACD
        let signalLine = calculateEMA(prices: macdValues, period: signalPeriod) ?? 0
        let histogram = macdLine - signalLine

        // Determine crossover
        var crossover: MACDCrossover = .none
        if macdValues.count >= 2 {
            let prevMACD = macdValues[macdValues.count - 2]
            let prevSignal = calculateEMA(prices: Array(macdValues.dropLast()), period: signalPeriod) ?? 0

            if prevMACD <= prevSignal && macdLine > signalLine {
                crossover = .bullish
            } else if prevMACD >= prevSignal && macdLine < signalLine {
                crossover = .bearish
            }
        }

        return MACDResult(macdLine: macdLine, signalLine: signalLine, histogram: histogram, crossover: crossover)
    }

    // MARK: - Average True Range (ATR)

    func calculateATR(highs: [Double], lows: [Double], closes: [Double], period: Int = 14) -> Double {
        guard highs.count >= period + 1, lows.count >= period + 1, closes.count >= period + 1 else {
            return 0
        }

        var trueRanges: [Double] = []

        for i in 1..<highs.count {
            let highLow = highs[i] - lows[i]
            let highClose = abs(highs[i] - closes[i - 1])
            let lowClose = abs(lows[i] - closes[i - 1])
            let tr = max(highLow, highClose, lowClose)
            trueRanges.append(tr)
        }

        return trueRanges.suffix(period).reduce(0, +) / Double(period)
    }

    // MARK: - Bollinger Bands

    func calculateBollingerBands(prices: [Double], period: Int = 20, standardDeviations: Double = 2.0) -> BollingerBands {
        guard prices.count >= period else {
            let current = prices.last ?? 0
            return BollingerBands(upper: current, middle: current, lower: current, bandwidth: 0, position: .middle)
        }

        let slice = Array(prices.suffix(period))
        let middle = slice.reduce(0, +) / Double(period)

        // Calculate standard deviation
        let variance = slice.map { pow($0 - middle, 2) }.reduce(0, +) / Double(period)
        let stdDev = sqrt(variance)

        let upper = middle + (standardDeviations * stdDev)
        let lower = middle - (standardDeviations * stdDev)
        let bandwidth = (upper - lower) / middle * 100

        let currentPrice = prices.last ?? middle
        let position: BollingerPosition
        if currentPrice >= upper {
            position = .aboveUpper
        } else if currentPrice <= lower {
            position = .belowLower
        } else if currentPrice > middle {
            position = .upperHalf
        } else {
            position = .lowerHalf
        }

        return BollingerBands(upper: upper, middle: middle, lower: lower, bandwidth: bandwidth, position: position)
    }

    // MARK: - VWAP (Volume Weighted Average Price)

    func calculateVWAP(candles: [OHLCData]) -> Double {
        guard !candles.isEmpty else { return 0 }

        var cumulativeTPV: Double = 0  // Typical Price * Volume
        var cumulativeVolume: Double = 0

        for candle in candles {
            let typicalPrice = (candle.high + candle.low + candle.close) / 3
            cumulativeTPV += typicalPrice * Double(candle.volume)
            cumulativeVolume += Double(candle.volume)
        }

        return cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : 0
    }

    // MARK: - Volume Analysis

    func analyzeVolume(volumes: [Double]) -> VolumeAnalysis {
        guard volumes.count >= 20 else {
            return VolumeAnalysis(trend: .normal, relativeVolume: 1.0, averageVolume: 0)
        }

        let avgVolume = volumes.suffix(20).reduce(0, +) / 20.0
        let currentVolume = volumes.last ?? 0
        let relativeVolume = avgVolume > 0 ? currentVolume / avgVolume : 1.0

        let trend: VolumeTrend
        if relativeVolume >= 2.0 {
            trend = .veryHigh
        } else if relativeVolume >= 1.5 {
            trend = .high
        } else if relativeVolume <= 0.5 {
            trend = .low
        } else {
            trend = .normal
        }

        return VolumeAnalysis(trend: trend, relativeVolume: relativeVolume, averageVolume: avgVolume)
    }

    // MARK: - Trend Determination

    private func determineTrend(
        currentPrice: Double,
        sma20: Double?,
        sma50: Double?,
        sma200: Double?,
        ema9: Double?,
        ema21: Double?
    ) -> TrendDirection {
        var bullishPoints = 0
        var bearishPoints = 0

        // Price vs Moving Averages
        if let sma20 = sma20 {
            if currentPrice > sma20 { bullishPoints += 1 }
            else { bearishPoints += 1 }
        }

        if let sma50 = sma50 {
            if currentPrice > sma50 { bullishPoints += 2 }
            else { bearishPoints += 2 }
        }

        if let sma200 = sma200 {
            if currentPrice > sma200 { bullishPoints += 3 }
            else { bearishPoints += 3 }
        }

        // EMA Crossovers
        if let ema9 = ema9, let ema21 = ema21 {
            if ema9 > ema21 { bullishPoints += 2 }
            else { bearishPoints += 2 }
        }

        // Golden/Death Cross
        if let sma50 = sma50, let sma200 = sma200 {
            if sma50 > sma200 { bullishPoints += 3 }
            else { bearishPoints += 3 }
        }

        let netScore = bullishPoints - bearishPoints

        if netScore >= 8 {
            return .strongUptrend
        } else if netScore >= 4 {
            return .uptrend
        } else if netScore <= -8 {
            return .strongDowntrend
        } else if netScore <= -4 {
            return .downtrend
        } else {
            return .sideways
        }
    }

    // MARK: - Support & Resistance

    private func calculateSupportResistance(
        highs: [Double],
        lows: [Double],
        closes: [Double]
    ) -> (supports: [Double], resistances: [Double]) {
        guard !highs.isEmpty else { return ([], []) }

        // Find local highs and lows (pivot points)
        var pivotHighs: [Double] = []
        var pivotLows: [Double] = []

        for i in 2..<(highs.count - 2) {
            // Local high
            if highs[i] > highs[i-1] && highs[i] > highs[i-2] &&
               highs[i] > highs[i+1] && highs[i] > highs[i+2] {
                pivotHighs.append(highs[i])
            }

            // Local low
            if lows[i] < lows[i-1] && lows[i] < lows[i-2] &&
               lows[i] < lows[i+1] && lows[i] < lows[i+2] {
                pivotLows.append(lows[i])
            }
        }

        // Cluster nearby levels
        let resistances = clusterLevels(pivotHighs, threshold: 0.005)
        let supports = clusterLevels(pivotLows, threshold: 0.005)

        // Return top 3 of each, sorted by proximity to current price
        let currentPrice = closes.last ?? 0

        let topResistances = resistances
            .filter { $0 > currentPrice }
            .sorted { abs($0 - currentPrice) < abs($1 - currentPrice) }
            .prefix(3)

        let topSupports = supports
            .filter { $0 < currentPrice }
            .sorted { abs($0 - currentPrice) < abs($1 - currentPrice) }
            .prefix(3)

        return (Array(topSupports), Array(topResistances))
    }

    private func clusterLevels(_ levels: [Double], threshold: Double) -> [Double] {
        guard !levels.isEmpty else { return [] }

        var clustered: [Double] = []
        var sorted = levels.sorted()

        var currentCluster: [Double] = [sorted[0]]

        for i in 1..<sorted.count {
            let diff = (sorted[i] - sorted[i-1]) / sorted[i-1]
            if diff < threshold {
                currentCluster.append(sorted[i])
            } else {
                clustered.append(currentCluster.reduce(0, +) / Double(currentCluster.count))
                currentCluster = [sorted[i]]
            }
        }
        clustered.append(currentCluster.reduce(0, +) / Double(currentCluster.count))

        return clustered
    }

    // MARK: - Overall Signal Generation

    private func generateOverallSignal(
        rsi: RSIResult,
        macd: MACDResult,
        trend: TrendDirection,
        currentPrice: Double,
        bollingerBands: BollingerBands
    ) -> TechnicalSignal {
        var bullishPoints = 0
        var bearishPoints = 0

        // RSI
        switch rsi.condition {
        case .oversold: bullishPoints += 3
        case .slightlyOversold: bullishPoints += 1
        case .overbought: bearishPoints += 3
        case .slightlyOverbought: bearishPoints += 1
        case .neutral: break
        }

        // MACD
        if macd.histogram > 0 { bullishPoints += 1 }
        else { bearishPoints += 1 }

        switch macd.crossover {
        case .bullish: bullishPoints += 3
        case .bearish: bearishPoints += 3
        case .none: break
        }

        // Trend
        switch trend {
        case .strongUptrend: bullishPoints += 4
        case .uptrend: bullishPoints += 2
        case .strongDowntrend: bearishPoints += 4
        case .downtrend: bearishPoints += 2
        case .sideways: break
        }

        // Bollinger Bands
        switch bollingerBands.position {
        case .belowLower: bullishPoints += 2  // Potential reversal
        case .aboveUpper: bearishPoints += 2  // Potential reversal
        default: break
        }

        let netScore = bullishPoints - bearishPoints
        let confidence = min(100, Double(abs(netScore)) * 10)

        if netScore >= 6 {
            return TechnicalSignal(direction: .strongBuy, confidence: confidence)
        } else if netScore >= 3 {
            return TechnicalSignal(direction: .buy, confidence: confidence)
        } else if netScore <= -6 {
            return TechnicalSignal(direction: .strongSell, confidence: confidence)
        } else if netScore <= -3 {
            return TechnicalSignal(direction: .sell, confidence: confidence)
        } else {
            return TechnicalSignal(direction: .neutral, confidence: confidence)
        }
    }

    // MARK: - Generate Insights

    private func generateInsights(
        rsi: RSIResult,
        macd: MACDResult,
        trend: TrendDirection,
        bollingerBands: BollingerBands,
        currentPrice: Double,
        sma20: Double?,
        sma50: Double?,
        volumeAnalysis: VolumeAnalysis
    ) -> [TechnicalInsight] {
        var insights: [TechnicalInsight] = []

        // Trend insight
        let trendInsight: TechnicalInsight
        switch trend {
        case .strongUptrend:
            trendInsight = TechnicalInsight(
                type: .trend,
                title: "Strong Uptrend",
                description: "Price is above all major moving averages with bullish alignment",
                signal: .bullish,
                importance: .high
            )
        case .uptrend:
            trendInsight = TechnicalInsight(
                type: .trend,
                title: "Uptrend",
                description: "Price showing bullish momentum above key moving averages",
                signal: .bullish,
                importance: .medium
            )
        case .strongDowntrend:
            trendInsight = TechnicalInsight(
                type: .trend,
                title: "Strong Downtrend",
                description: "Price is below all major moving averages with bearish alignment",
                signal: .bearish,
                importance: .high
            )
        case .downtrend:
            trendInsight = TechnicalInsight(
                type: .trend,
                title: "Downtrend",
                description: "Price showing bearish momentum below key moving averages",
                signal: .bearish,
                importance: .medium
            )
        case .sideways:
            trendInsight = TechnicalInsight(
                type: .trend,
                title: "Sideways/Consolidation",
                description: "No clear trend direction, price moving in a range",
                signal: .neutral,
                importance: .low
            )
        }
        insights.append(trendInsight)

        // RSI insight
        switch rsi.condition {
        case .overbought:
            insights.append(TechnicalInsight(
                type: .rsi,
                title: "RSI Overbought (\(String(format: "%.0f", rsi.value)))",
                description: "Market may be overextended, watch for potential pullback",
                signal: .bearish,
                importance: .high
            ))
        case .oversold:
            insights.append(TechnicalInsight(
                type: .rsi,
                title: "RSI Oversold (\(String(format: "%.0f", rsi.value)))",
                description: "Market may be oversold, watch for potential bounce",
                signal: .bullish,
                importance: .high
            ))
        case .slightlyOverbought:
            insights.append(TechnicalInsight(
                type: .rsi,
                title: "RSI Near Overbought (\(String(format: "%.0f", rsi.value)))",
                description: "Approaching overbought territory",
                signal: .bearish,
                importance: .medium
            ))
        case .slightlyOversold:
            insights.append(TechnicalInsight(
                type: .rsi,
                title: "RSI Near Oversold (\(String(format: "%.0f", rsi.value)))",
                description: "Approaching oversold territory",
                signal: .bullish,
                importance: .medium
            ))
        case .neutral:
            break
        }

        // MACD insight
        switch macd.crossover {
        case .bullish:
            insights.append(TechnicalInsight(
                type: .macd,
                title: "MACD Bullish Crossover",
                description: "MACD line crossed above signal line - bullish momentum",
                signal: .bullish,
                importance: .high
            ))
        case .bearish:
            insights.append(TechnicalInsight(
                type: .macd,
                title: "MACD Bearish Crossover",
                description: "MACD line crossed below signal line - bearish momentum",
                signal: .bearish,
                importance: .high
            ))
        case .none:
            if macd.histogram > 0 {
                insights.append(TechnicalInsight(
                    type: .macd,
                    title: "MACD Positive",
                    description: "Histogram positive indicating bullish momentum",
                    signal: .bullish,
                    importance: .low
                ))
            } else if macd.histogram < 0 {
                insights.append(TechnicalInsight(
                    type: .macd,
                    title: "MACD Negative",
                    description: "Histogram negative indicating bearish momentum",
                    signal: .bearish,
                    importance: .low
                ))
            }
        }

        // Bollinger Band insight
        switch bollingerBands.position {
        case .aboveUpper:
            insights.append(TechnicalInsight(
                type: .bollinger,
                title: "Above Bollinger Upper Band",
                description: "Price stretched above upper band, may revert to mean",
                signal: .bearish,
                importance: .medium
            ))
        case .belowLower:
            insights.append(TechnicalInsight(
                type: .bollinger,
                title: "Below Bollinger Lower Band",
                description: "Price stretched below lower band, may bounce",
                signal: .bullish,
                importance: .medium
            ))
        default:
            break
        }

        // Volume insight
        switch volumeAnalysis.trend {
        case .veryHigh:
            insights.append(TechnicalInsight(
                type: .volume,
                title: "Very High Volume",
                description: "Volume \(String(format: "%.1fx", volumeAnalysis.relativeVolume)) above average - strong conviction",
                signal: .neutral,
                importance: .high
            ))
        case .high:
            insights.append(TechnicalInsight(
                type: .volume,
                title: "Above Average Volume",
                description: "Volume \(String(format: "%.1fx", volumeAnalysis.relativeVolume)) above average",
                signal: .neutral,
                importance: .medium
            ))
        case .low:
            insights.append(TechnicalInsight(
                type: .volume,
                title: "Low Volume",
                description: "Below average volume - weak conviction in current move",
                signal: .neutral,
                importance: .low
            ))
        case .normal:
            break
        }

        // Moving Average crossover
        if let sma20 = sma20, let sma50 = sma50 {
            if sma20 > sma50 && abs(sma20 - sma50) / sma50 < 0.005 {
                insights.append(TechnicalInsight(
                    type: .movingAverage,
                    title: "Golden Cross Forming",
                    description: "20 SMA crossing above 50 SMA - bullish signal",
                    signal: .bullish,
                    importance: .high
                ))
            } else if sma20 < sma50 && abs(sma20 - sma50) / sma50 < 0.005 {
                insights.append(TechnicalInsight(
                    type: .movingAverage,
                    title: "Death Cross Forming",
                    description: "20 SMA crossing below 50 SMA - bearish signal",
                    signal: .bearish,
                    importance: .high
                ))
            }
        }

        return insights.sorted { $0.importance.rawValue > $1.importance.rawValue }
    }
}

// MARK: - Technical Analysis Result

struct TechnicalAnalysisResult {
    let trend: TrendDirection
    let signal: TechnicalSignal
    let rsi: RSIResult
    let macd: MACDResult
    let sma20: Double?
    let sma50: Double?
    let sma200: Double?
    let ema9: Double?
    let ema21: Double?
    let atr: Double
    let bollingerBands: BollingerBands
    let vwap: Double
    let supportLevels: [Double]
    let resistanceLevels: [Double]
    let volumeAnalysis: VolumeAnalysis
    let insights: [TechnicalInsight]
    let candlestickPatterns: [CandlestickPattern]

    static var empty: TechnicalAnalysisResult {
        TechnicalAnalysisResult(
            trend: .sideways,
            signal: TechnicalSignal(direction: .neutral, confidence: 0),
            rsi: RSIResult(value: 50, condition: .neutral),
            macd: MACDResult(macdLine: 0, signalLine: 0, histogram: 0, crossover: .none),
            sma20: nil,
            sma50: nil,
            sma200: nil,
            ema9: nil,
            ema21: nil,
            atr: 0,
            bollingerBands: BollingerBands(upper: 0, middle: 0, lower: 0, bandwidth: 0, position: .middle),
            vwap: 0,
            supportLevels: [],
            resistanceLevels: [],
            volumeAnalysis: VolumeAnalysis(trend: .normal, relativeVolume: 1, averageVolume: 0),
            insights: [],
            candlestickPatterns: []
        )
    }
}

// MARK: - Supporting Types

enum TrendDirection: String {
    case strongUptrend = "Strong Uptrend"
    case uptrend = "Uptrend"
    case sideways = "Sideways"
    case downtrend = "Downtrend"
    case strongDowntrend = "Strong Downtrend"

    var icon: String {
        switch self {
        case .strongUptrend: return "arrow.up.circle.fill"
        case .uptrend: return "arrow.up.right.circle.fill"
        case .sideways: return "arrow.left.arrow.right.circle.fill"
        case .downtrend: return "arrow.down.right.circle.fill"
        case .strongDowntrend: return "arrow.down.circle.fill"
        }
    }

    var color: Color {
        switch self {
        case .strongUptrend, .uptrend: return Theme.profit
        case .sideways: return Theme.accentOrange
        case .downtrend, .strongDowntrend: return Theme.loss
        }
    }
}

struct TechnicalSignal {
    let direction: SignalDirection
    let confidence: Double

    enum SignalDirection: String {
        case strongBuy = "Strong Buy"
        case buy = "Buy"
        case neutral = "Neutral"
        case sell = "Sell"
        case strongSell = "Strong Sell"

        var color: Color {
            switch self {
            case .strongBuy, .buy: return Theme.profit
            case .neutral: return Theme.accentOrange
            case .sell, .strongSell: return Theme.loss
            }
        }
    }
}

struct RSIResult {
    let value: Double
    let condition: RSICondition
}

enum RSICondition: String {
    case overbought = "Overbought"
    case slightlyOverbought = "Slightly Overbought"
    case neutral = "Neutral"
    case slightlyOversold = "Slightly Oversold"
    case oversold = "Oversold"

    var color: Color {
        switch self {
        case .overbought, .slightlyOverbought: return Theme.loss
        case .neutral: return Theme.textSecondary
        case .oversold, .slightlyOversold: return Theme.profit
        }
    }
}

struct MACDResult {
    let macdLine: Double
    let signalLine: Double
    let histogram: Double
    let crossover: MACDCrossover
}

enum MACDCrossover {
    case bullish, bearish, none
}

struct BollingerBands {
    let upper: Double
    let middle: Double
    let lower: Double
    let bandwidth: Double
    let position: BollingerPosition
}

enum BollingerPosition {
    case aboveUpper, upperHalf, middle, lowerHalf, belowLower
}

struct VolumeAnalysis {
    let trend: VolumeTrend
    let relativeVolume: Double
    let averageVolume: Double
}

enum VolumeTrend {
    case veryHigh, high, normal, low
}

struct TechnicalInsight: Identifiable {
    let id = UUID()
    let type: InsightType
    let title: String
    let description: String
    let signal: InsightSignal
    let importance: Importance

    enum InsightType {
        case trend, rsi, macd, bollinger, volume, movingAverage, support, resistance

        var icon: String {
            switch self {
            case .trend: return "chart.line.uptrend.xyaxis"
            case .rsi: return "gauge.with.needle"
            case .macd: return "waveform.path.ecg"
            case .bollinger: return "chart.bar.xaxis"
            case .volume: return "chart.bar.fill"
            case .movingAverage: return "point.topleft.down.curvedto.point.bottomright.up"
            case .support: return "arrow.down.to.line"
            case .resistance: return "arrow.up.to.line"
            }
        }
    }

    enum InsightSignal {
        case bullish, bearish, neutral

        var color: Color {
            switch self {
            case .bullish: return Theme.profit
            case .bearish: return Theme.loss
            case .neutral: return Theme.accentOrange
            }
        }
    }

    enum Importance: Int {
        case high = 3
        case medium = 2
        case low = 1
    }
}
