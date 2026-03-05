import Foundation
import SwiftUI

// MARK: - Candlestick Pattern Service

final class CandlestickPatternService {
    static let shared = CandlestickPatternService()

    private init() {}

    // MARK: - Main Detection Entry Point

    /// Analyze candles and detect all patterns
    func detectPatterns(candles: [OHLCData]) -> [CandlestickPattern] {
        guard candles.count >= 5 else { return [] }

        var patterns: [CandlestickPattern] = []

        // Single candle patterns (check last 3 candles)
        for i in max(0, candles.count - 3)..<candles.count {
            let candle = candles[i]
            let prevCandles = i > 0 ? Array(candles[max(0, i-5)..<i]) : []

            if let pattern = detectSingleCandlePattern(candle: candle, previousCandles: prevCandles, index: i) {
                patterns.append(pattern)
            }
        }

        // Two candle patterns (check last 2 pairs)
        for i in max(1, candles.count - 2)..<candles.count {
            let current = candles[i]
            let previous = candles[i - 1]
            let trend = detectTrend(candles: Array(candles[max(0, i-10)..<i]))

            if let pattern = detectTwoCandlePattern(current: current, previous: previous, trend: trend, index: i) {
                patterns.append(pattern)
            }
        }

        // Three candle patterns (check last pattern)
        if candles.count >= 3 {
            let lastThree = Array(candles.suffix(3))
            let trend = detectTrend(candles: Array(candles.dropLast(3).suffix(10)))

            if let pattern = detectThreeCandlePattern(candles: lastThree, trend: trend, index: candles.count - 1) {
                patterns.append(pattern)
            }
        }

        // Sort by recency and importance
        return patterns
            .sorted { $0.importance.rawValue > $1.importance.rawValue }
            .prefix(5)
            .map { $0 }
    }

    // MARK: - Single Candle Patterns

    private func detectSingleCandlePattern(candle: OHLCData, previousCandles: [OHLCData], index: Int) -> CandlestickPattern? {
        let bodySize = abs(candle.close - candle.open)
        let totalRange = candle.high - candle.low
        let upperWick = candle.high - max(candle.open, candle.close)
        let lowerWick = min(candle.open, candle.close) - candle.low

        guard totalRange > 0 else { return nil }

        let bodyRatio = bodySize / totalRange
        let upperWickRatio = upperWick / totalRange
        let lowerWickRatio = lowerWick / totalRange

        let trend = detectTrend(candles: previousCandles)

        // DOJI - Very small body (< 10% of range)
        if bodyRatio < 0.1 {
            // Dragonfly Doji - Long lower wick, no upper wick
            if lowerWickRatio > 0.6 && upperWickRatio < 0.1 {
                return CandlestickPattern(
                    type: .dragonflyDoji,
                    signal: trend == .down ? .bullish : .neutral,
                    importance: .high,
                    description: "Dragonfly Doji - Potential bullish reversal",
                    candleIndex: index
                )
            }

            // Gravestone Doji - Long upper wick, no lower wick
            if upperWickRatio > 0.6 && lowerWickRatio < 0.1 {
                return CandlestickPattern(
                    type: .gravestoneDoji,
                    signal: trend == .up ? .bearish : .neutral,
                    importance: .high,
                    description: "Gravestone Doji - Potential bearish reversal",
                    candleIndex: index
                )
            }

            // Regular Doji
            return CandlestickPattern(
                type: .doji,
                signal: .neutral,
                importance: .medium,
                description: "Doji - Market indecision, potential reversal",
                candleIndex: index
            )
        }

        // HAMMER - Small body at top, long lower wick (in downtrend)
        if trend == .down && bodyRatio < 0.35 && lowerWickRatio > 0.5 && upperWickRatio < 0.15 {
            return CandlestickPattern(
                type: .hammer,
                signal: .bullish,
                importance: .high,
                description: "Hammer - Strong bullish reversal signal after downtrend",
                candleIndex: index
            )
        }

        // INVERTED HAMMER - Small body at bottom, long upper wick (in downtrend)
        if trend == .down && bodyRatio < 0.35 && upperWickRatio > 0.5 && lowerWickRatio < 0.15 {
            return CandlestickPattern(
                type: .invertedHammer,
                signal: .bullish,
                importance: .medium,
                description: "Inverted Hammer - Potential bullish reversal",
                candleIndex: index
            )
        }

        // SHOOTING STAR - Small body at bottom, long upper wick (in uptrend)
        if trend == .up && bodyRatio < 0.35 && upperWickRatio > 0.5 && lowerWickRatio < 0.15 {
            return CandlestickPattern(
                type: .shootingStar,
                signal: .bearish,
                importance: .high,
                description: "Shooting Star - Strong bearish reversal signal after uptrend",
                candleIndex: index
            )
        }

        // HANGING MAN - Small body at top, long lower wick (in uptrend)
        if trend == .up && bodyRatio < 0.35 && lowerWickRatio > 0.5 && upperWickRatio < 0.15 {
            return CandlestickPattern(
                type: .hangingMan,
                signal: .bearish,
                importance: .high,
                description: "Hanging Man - Bearish reversal warning after uptrend",
                candleIndex: index
            )
        }

        // MARUBOZU - Large body with no/tiny wicks
        if bodyRatio > 0.85 {
            if candle.close > candle.open {
                return CandlestickPattern(
                    type: .bullishMarubozu,
                    signal: .bullish,
                    importance: .medium,
                    description: "Bullish Marubozu - Strong buying pressure",
                    candleIndex: index
                )
            } else {
                return CandlestickPattern(
                    type: .bearishMarubozu,
                    signal: .bearish,
                    importance: .medium,
                    description: "Bearish Marubozu - Strong selling pressure",
                    candleIndex: index
                )
            }
        }

        // SPINNING TOP - Small body with equal wicks
        if bodyRatio < 0.3 && abs(upperWickRatio - lowerWickRatio) < 0.15 {
            return CandlestickPattern(
                type: .spinningTop,
                signal: .neutral,
                importance: .low,
                description: "Spinning Top - Market indecision",
                candleIndex: index
            )
        }

        return nil
    }

    // MARK: - Two Candle Patterns

    private func detectTwoCandlePattern(current: OHLCData, previous: OHLCData, trend: TrendType, index: Int) -> CandlestickPattern? {
        let prevBodySize = abs(previous.close - previous.open)
        let currBodySize = abs(current.close - current.open)
        let prevIsGreen = previous.close > previous.open
        let currIsGreen = current.close > current.open

        // BULLISH ENGULFING - Green candle completely engulfs previous red candle
        if trend == .down && !prevIsGreen && currIsGreen {
            if current.open <= previous.close && current.close >= previous.open {
                if currBodySize > prevBodySize * 1.1 {
                    return CandlestickPattern(
                        type: .bullishEngulfing,
                        signal: .bullish,
                        importance: .high,
                        description: "Bullish Engulfing - Strong reversal signal, buyers taking control",
                        candleIndex: index
                    )
                }
            }
        }

        // BEARISH ENGULFING - Red candle completely engulfs previous green candle
        if trend == .up && prevIsGreen && !currIsGreen {
            if current.open >= previous.close && current.close <= previous.open {
                if currBodySize > prevBodySize * 1.1 {
                    return CandlestickPattern(
                        type: .bearishEngulfing,
                        signal: .bearish,
                        importance: .high,
                        description: "Bearish Engulfing - Strong reversal signal, sellers taking control",
                        candleIndex: index
                    )
                }
            }
        }

        // PIERCING LINE - Green candle opens below prev low, closes above prev midpoint
        if trend == .down && !prevIsGreen && currIsGreen {
            let prevMid = (previous.open + previous.close) / 2
            if current.open < previous.low && current.close > prevMid && current.close < previous.open {
                return CandlestickPattern(
                    type: .piercingLine,
                    signal: .bullish,
                    importance: .medium,
                    description: "Piercing Line - Bullish reversal, buyers pushing back",
                    candleIndex: index
                )
            }
        }

        // DARK CLOUD COVER - Red candle opens above prev high, closes below prev midpoint
        if trend == .up && prevIsGreen && !currIsGreen {
            let prevMid = (previous.open + previous.close) / 2
            if current.open > previous.high && current.close < prevMid && current.close > previous.open {
                return CandlestickPattern(
                    type: .darkCloudCover,
                    signal: .bearish,
                    importance: .medium,
                    description: "Dark Cloud Cover - Bearish reversal, sellers pushing back",
                    candleIndex: index
                )
            }
        }

        // TWEEZER BOTTOM - Two candles with same low in downtrend
        if trend == .down {
            let lowDiff = abs(previous.low - current.low) / previous.low
            if lowDiff < 0.002 && !prevIsGreen && currIsGreen {
                return CandlestickPattern(
                    type: .tweezerBottom,
                    signal: .bullish,
                    importance: .medium,
                    description: "Tweezer Bottom - Support found, potential reversal",
                    candleIndex: index
                )
            }
        }

        // TWEEZER TOP - Two candles with same high in uptrend
        if trend == .up {
            let highDiff = abs(previous.high - current.high) / previous.high
            if highDiff < 0.002 && prevIsGreen && !currIsGreen {
                return CandlestickPattern(
                    type: .tweezerTop,
                    signal: .bearish,
                    importance: .medium,
                    description: "Tweezer Top - Resistance found, potential reversal",
                    candleIndex: index
                )
            }
        }

        // HARAMI (Bullish) - Small green candle inside previous large red candle
        if trend == .down && !prevIsGreen && currIsGreen {
            if current.open > previous.close && current.close < previous.open {
                if currBodySize < prevBodySize * 0.5 {
                    return CandlestickPattern(
                        type: .bullishHarami,
                        signal: .bullish,
                        importance: .medium,
                        description: "Bullish Harami - Selling pressure weakening",
                        candleIndex: index
                    )
                }
            }
        }

        // HARAMI (Bearish) - Small red candle inside previous large green candle
        if trend == .up && prevIsGreen && !currIsGreen {
            if current.open < previous.close && current.close > previous.open {
                if currBodySize < prevBodySize * 0.5 {
                    return CandlestickPattern(
                        type: .bearishHarami,
                        signal: .bearish,
                        importance: .medium,
                        description: "Bearish Harami - Buying pressure weakening",
                        candleIndex: index
                    )
                }
            }
        }

        return nil
    }

    // MARK: - Three Candle Patterns

    private func detectThreeCandlePattern(candles: [OHLCData], trend: TrendType, index: Int) -> CandlestickPattern? {
        guard candles.count == 3 else { return nil }

        let first = candles[0]
        let second = candles[1]
        let third = candles[2]

        let firstIsGreen = first.close > first.open
        let secondIsGreen = second.close > second.open
        let thirdIsGreen = third.close > third.open

        let firstBody = abs(first.close - first.open)
        let secondBody = abs(second.close - second.open)
        let thirdBody = abs(third.close - third.open)

        // MORNING STAR - Bullish reversal (Red, Small/Doji, Green)
        if trend == .down {
            let secondIsDoji = secondBody < firstBody * 0.3
            if !firstIsGreen && secondIsDoji && thirdIsGreen {
                if second.high < first.close && third.close > (first.open + first.close) / 2 {
                    return CandlestickPattern(
                        type: .morningStar,
                        signal: .bullish,
                        importance: .high,
                        description: "Morning Star - Strong bullish reversal pattern",
                        candleIndex: index
                    )
                }
            }
        }

        // EVENING STAR - Bearish reversal (Green, Small/Doji, Red)
        if trend == .up {
            let secondIsDoji = secondBody < firstBody * 0.3
            if firstIsGreen && secondIsDoji && !thirdIsGreen {
                if second.low > first.close && third.close < (first.open + first.close) / 2 {
                    return CandlestickPattern(
                        type: .eveningStar,
                        signal: .bearish,
                        importance: .high,
                        description: "Evening Star - Strong bearish reversal pattern",
                        candleIndex: index
                    )
                }
            }
        }

        // THREE WHITE SOLDIERS - Three consecutive green candles with higher closes
        if firstIsGreen && secondIsGreen && thirdIsGreen {
            if second.close > first.close && third.close > second.close {
                if secondBody > firstBody * 0.7 && thirdBody > secondBody * 0.7 {
                    return CandlestickPattern(
                        type: .threeWhiteSoldiers,
                        signal: .bullish,
                        importance: .high,
                        description: "Three White Soldiers - Strong bullish continuation",
                        candleIndex: index
                    )
                }
            }
        }

        // THREE BLACK CROWS - Three consecutive red candles with lower closes
        if !firstIsGreen && !secondIsGreen && !thirdIsGreen {
            if second.close < first.close && third.close < second.close {
                if secondBody > firstBody * 0.7 && thirdBody > secondBody * 0.7 {
                    return CandlestickPattern(
                        type: .threeBlackCrows,
                        signal: .bearish,
                        importance: .high,
                        description: "Three Black Crows - Strong bearish continuation",
                        candleIndex: index
                    )
                }
            }
        }

        // THREE INSIDE UP - Bullish Harami followed by confirmation
        if trend == .down && !firstIsGreen && secondIsGreen && thirdIsGreen {
            // Second candle inside first
            if second.open > first.close && second.close < first.open {
                // Third candle closes above first's open
                if third.close > first.open {
                    return CandlestickPattern(
                        type: .threeInsideUp,
                        signal: .bullish,
                        importance: .high,
                        description: "Three Inside Up - Confirmed bullish reversal",
                        candleIndex: index
                    )
                }
            }
        }

        // THREE INSIDE DOWN - Bearish Harami followed by confirmation
        if trend == .up && firstIsGreen && !secondIsGreen && !thirdIsGreen {
            // Second candle inside first
            if second.open < first.close && second.close > first.open {
                // Third candle closes below first's open
                if third.close < first.open {
                    return CandlestickPattern(
                        type: .threeInsideDown,
                        signal: .bearish,
                        importance: .high,
                        description: "Three Inside Down - Confirmed bearish reversal",
                        candleIndex: index
                    )
                }
            }
        }

        return nil
    }

    // MARK: - Trend Detection

    private enum TrendType {
        case up, down, sideways
    }

    private func detectTrend(candles: [OHLCData]) -> TrendType {
        guard candles.count >= 3 else { return .sideways }

        let closes = candles.map { $0.close }
        var upCount = 0
        var downCount = 0

        for i in 1..<closes.count {
            if closes[i] > closes[i-1] {
                upCount += 1
            } else if closes[i] < closes[i-1] {
                downCount += 1
            }
        }

        let total = upCount + downCount
        guard total > 0 else { return .sideways }

        let upRatio = Double(upCount) / Double(total)

        if upRatio > 0.6 {
            return .up
        } else if upRatio < 0.4 {
            return .down
        } else {
            return .sideways
        }
    }
}

// MARK: - Candlestick Pattern Model

struct CandlestickPattern: Identifiable {
    let id = UUID()
    let type: PatternType
    let signal: PatternSignal
    let importance: Importance
    let description: String
    let candleIndex: Int

    var displayName: String {
        type.displayName
    }

    var icon: String {
        switch signal {
        case .bullish: return "arrow.up.circle.fill"
        case .bearish: return "arrow.down.circle.fill"
        case .neutral: return "circle.fill"
        }
    }

    var color: Color {
        signal.color
    }

    enum PatternType: String {
        // Single Candle
        case doji = "Doji"
        case dragonflyDoji = "Dragonfly Doji"
        case gravestoneDoji = "Gravestone Doji"
        case hammer = "Hammer"
        case invertedHammer = "Inverted Hammer"
        case shootingStar = "Shooting Star"
        case hangingMan = "Hanging Man"
        case bullishMarubozu = "Bullish Marubozu"
        case bearishMarubozu = "Bearish Marubozu"
        case spinningTop = "Spinning Top"

        // Two Candle
        case bullishEngulfing = "Bullish Engulfing"
        case bearishEngulfing = "Bearish Engulfing"
        case piercingLine = "Piercing Line"
        case darkCloudCover = "Dark Cloud Cover"
        case tweezerTop = "Tweezer Top"
        case tweezerBottom = "Tweezer Bottom"
        case bullishHarami = "Bullish Harami"
        case bearishHarami = "Bearish Harami"

        // Three Candle
        case morningStar = "Morning Star"
        case eveningStar = "Evening Star"
        case threeWhiteSoldiers = "Three White Soldiers"
        case threeBlackCrows = "Three Black Crows"
        case threeInsideUp = "Three Inside Up"
        case threeInsideDown = "Three Inside Down"

        var displayName: String { rawValue }

        var emoji: String {
            switch self {
            case .doji, .dragonflyDoji, .gravestoneDoji, .spinningTop:
                return "➖"
            case .hammer, .invertedHammer, .bullishEngulfing, .piercingLine, .bullishHarami, .tweezerBottom, .morningStar, .threeWhiteSoldiers, .threeInsideUp, .bullishMarubozu:
                return "🟢"
            case .shootingStar, .hangingMan, .bearishEngulfing, .darkCloudCover, .bearishHarami, .tweezerTop, .eveningStar, .threeBlackCrows, .threeInsideDown, .bearishMarubozu:
                return "🔴"
            }
        }
    }

    enum PatternSignal {
        case bullish, bearish, neutral

        var color: Color {
            switch self {
            case .bullish: return Theme.profit
            case .bearish: return Theme.loss
            case .neutral: return Theme.accentOrange
            }
        }

        var label: String {
            switch self {
            case .bullish: return "Bullish"
            case .bearish: return "Bearish"
            case .neutral: return "Neutral"
            }
        }
    }

    enum Importance: Int {
        case high = 3
        case medium = 2
        case low = 1
    }
}
