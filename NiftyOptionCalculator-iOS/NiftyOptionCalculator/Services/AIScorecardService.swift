import Foundation

class AIScorecardService {
    static let shared = AIScorecardService()

    private let storageKey = "ai_scorecard_picks"
    private let maxPicks = 100

    private init() {
        // One-time reset for v1.1 scoring fix
        let resetKey = "scorecard_reset_v1_1"
        if !UserDefaults.standard.bool(forKey: resetKey) {
            UserDefaults.standard.removeObject(forKey: storageKey)
            UserDefaults.standard.set(true, forKey: resetKey)
        }
    }

    // MARK: - Storage

    func loadPicks() -> [TrackedPick] {
        guard let data = UserDefaults.standard.data(forKey: storageKey) else { return [] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return (try? decoder.decode([TrackedPick].self, from: data)) ?? []
    }

    func savePicks(_ picks: [TrackedPick]) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let trimmed = Array(picks.suffix(maxPicks))
        if let data = try? encoder.encode(trimmed) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }

    // MARK: - Track New Suggestions

    func trackSuggestions(calls: [AITradeSuggestion], puts: [AITradeSuggestion], indexName: String) {
        var picks = loadPicks()
        let existingIds = Set(picks.map { $0.id })

        let allSuggestions = calls + puts
        for suggestion in allSuggestions {
            let pick = TrackedPick.from(suggestion: suggestion, indexName: indexName)
            if !existingIds.contains(pick.id) {
                picks.append(pick)
            }
        }

        savePicks(picks)
    }

    // MARK: - Resolve from Option Chain

    /// Build price map from option chain and resolve outcomes
    func resolveFromOptionChain(_ optionChain: [OptionChainRow], indexName: String) {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd-MMM-yyyy"
        var priceMap: [String: Double] = [:]
        for row in optionChain {
            let strike = row.strikePrice
            if let call = row.callOption, call.lastTradedPrice > 0 {
                let expiryStr = formatter.string(from: call.expiryDate)
                let key = TrackedPick.compositeKey(indexName: indexName, strike: strike, optionType: "CE", expiry: expiryStr)
                priceMap[key] = call.lastTradedPrice
            }
            if let put = row.putOption, put.lastTradedPrice > 0 {
                let expiryStr = formatter.string(from: put.expiryDate)
                let key = TrackedPick.compositeKey(indexName: indexName, strike: strike, optionType: "PE", expiry: expiryStr)
                priceMap[key] = put.lastTradedPrice
            }
        }
        if !priceMap.isEmpty {
            resolveOutcomes(currentPrices: priceMap)
        }
    }

    // MARK: - Resolve Outcomes

    func resolveOutcomes(currentPrices: [String: Double]) {
        var picks = loadPicks()
        var changed = false

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd-MMM-yyyy"
        let now = Date()

        for i in picks.indices {
            guard picks[i].outcome == .active else { continue }

            let key = picks[i].id
            guard let currentPrice = currentPrices[key] else {
                // Check expiry
                if let expiryDate = dateFormatter.date(from: picks[i].expiryDate), expiryDate < now {
                    picks[i].outcome = .expired
                    picks[i].exitPrice = picks[i].entryPrice * 0.5
                    picks[i].returnPct = -50
                    picks[i].resolvedAt = now
                    changed = true
                }
                continue
            }

            // Update high water mark
            if currentPrice > (picks[i].highWaterMark ?? 0) {
                picks[i].highWaterMark = currentPrice
                changed = true
            }

            // Check target hit
            if currentPrice >= picks[i].targetPrice {
                picks[i].outcome = .win
                picks[i].exitPrice = picks[i].targetPrice
                picks[i].returnPct = ((picks[i].targetPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100
                picks[i].resolvedAt = now
                changed = true
            }
            // Check stop loss hit
            else if currentPrice <= picks[i].stopLossPrice {
                picks[i].outcome = .loss
                picks[i].exitPrice = picks[i].stopLossPrice
                picks[i].returnPct = ((picks[i].stopLossPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100
                picks[i].resolvedAt = now
                changed = true
            }
            // Check expiry
            else if let expiryDate = dateFormatter.date(from: picks[i].expiryDate), expiryDate < now {
                picks[i].outcome = .expired
                picks[i].exitPrice = currentPrice
                picks[i].returnPct = ((currentPrice - picks[i].entryPrice) / picks[i].entryPrice) * 100
                picks[i].resolvedAt = now
                changed = true
            }
        }

        if changed {
            savePicks(picks)
        }
    }

    // MARK: - Stats

    func getStats() -> ScorecardStats {
        let picks = loadPicks()
        let resolved = picks.filter { $0.outcome != .active }
        let wins = resolved.filter { $0.outcome == .win }
        let losses = resolved.filter { $0.outcome == .loss }
        let expired = resolved.filter { $0.outcome == .expired }
        let active = picks.filter { $0.outcome == .active }

        let winRate = resolved.isEmpty ? 0.0 : (Double(wins.count) / Double(resolved.count)) * 100
        let avgReturn = resolved.isEmpty ? 0.0 : resolved.compactMap { $0.returnPct }.reduce(0, +) / Double(resolved.count)

        // Top picks stats
        let topPickResolved = resolved.filter { $0.tier == "topPick" }
        let topPickWins = topPickResolved.filter { $0.outcome == .win }
        let topPickWinRate = topPickResolved.isEmpty ? 0.0 : (Double(topPickWins.count) / Double(topPickResolved.count)) * 100

        return ScorecardStats(
            totalPicks: picks.count,
            activePicks: active.count,
            wins: wins.count,
            losses: losses.count,
            expired: expired.count,
            winRate: winRate,
            avgReturn: avgReturn,
            topPickWinRate: topPickWinRate,
            recentPicks: Array(picks.sorted { $0.createdAt > $1.createdAt }.prefix(10))
        )
    }
}

struct ScorecardStats {
    let totalPicks: Int
    let activePicks: Int
    let wins: Int
    let losses: Int
    let expired: Int
    let winRate: Double
    let avgReturn: Double
    let topPickWinRate: Double
    let recentPicks: [TrackedPick]
}
