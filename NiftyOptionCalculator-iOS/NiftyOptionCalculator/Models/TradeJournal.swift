import Foundation

// MARK: - Journal Tag

enum JournalTag: String, CaseIterable, Codable {
    case scalp
    case swing
    case hedging
    case spread
    case directional
    case expiry_day

    var displayName: String {
        rawValue.replacingOccurrences(of: "_", with: " ").capitalized
    }
}

// MARK: - Trade Mood

enum TradeMood: String, CaseIterable, Codable {
    case confident
    case nervous
    case neutral
    case greedy
    case fearful
    case disciplined

    var emoji: String {
        switch self {
        case .confident: return "\u{1F4AA}"   // flexed biceps
        case .nervous: return "\u{1F630}"     // anxious face
        case .neutral: return "\u{1F610}"     // neutral face
        case .greedy: return "\u{1F911}"      // money-mouth face
        case .fearful: return "\u{1F628}"     // fearful face
        case .disciplined: return "\u{1F9D8}" // person in lotus position
        }
    }
}

// MARK: - Market Condition

enum MarketCondition: String, CaseIterable, Codable {
    case trending_up
    case trending_down
    case range_bound
    case volatile
    case low_vol

    var displayName: String {
        rawValue.replacingOccurrences(of: "_", with: " ").capitalized
    }
}

// MARK: - Trade Outcome

enum TradeOutcome: String, CaseIterable, Codable {
    case profit
    case loss
    case breakeven
}

// MARK: - Journal Entry

struct JournalEntry: Identifiable, Codable {
    let id: String
    let user_id: String
    let symbol: String
    let strike_price: Double
    let option_type: String   // CE or PE
    let direction: String     // buy or sell
    let entry_price: Double
    let exit_price: Double?
    let quantity: Int
    let lot_size: Int
    let entry_date: String
    let exit_date: String?
    let expiry_date: String?
    let realized_pnl: Double?
    let realized_pnl_percent: Double?
    let title: String?
    let notes: String?
    let tags: String?
    let market_condition: String?
    let mood: String?
    let outcome: String?
    let paper_position_id: String?
    let created_at: String
    let updated_at: String
}

// MARK: - Journal List Response

struct JournalListResponse: Codable {
    let entries: [JournalEntry]
    let total: Int
}

// MARK: - Journal Stats

struct JournalStats: Codable {
    let total_trades: Int
    let winning_trades: Int
    let losing_trades: Int
    let breakeven_trades: Int
    let win_rate: Double
    let total_pnl: Double
    let average_pnl: Double
    let best_trade: Double?
    let worst_trade: Double?
    let average_holding_days: Double?
    let most_traded_symbol: String?
    let most_used_tags: [String]
    let trades_by_mood: [String: Int]
    let trades_by_condition: [String: Int]
}

// MARK: - Create Request

struct JournalCreateRequest: Codable {
    let symbol: String
    let strike_price: Double
    let option_type: String
    let direction: String
    let entry_price: Double
    let exit_price: Double?
    let quantity: Int
    let lot_size: Int
    let entry_date: String
    let exit_date: String?
    let expiry_date: String?
    let title: String?
    let notes: String?
    let tags: String?
    let market_condition: String?
    let mood: String?
    let outcome: String?
    let paper_position_id: String?
}

// MARK: - Update Request

struct JournalUpdateRequest: Codable {
    var exit_price: Double?
    var exit_date: String?
    var title: String?
    var notes: String?
    var tags: String?
    var market_condition: String?
    var mood: String?
    var outcome: String?
}
