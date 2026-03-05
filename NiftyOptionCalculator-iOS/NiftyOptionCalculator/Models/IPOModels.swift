import Foundation
import SwiftUI

// MARK: - IPO Status

enum IPOStatus: String, Codable, CaseIterable {
    case upcoming = "upcoming"
    case open = "open"
    case closed = "closed"
    case listed = "listed"
    case allotment = "allotment"

    var displayName: String {
        switch self {
        case .upcoming: return "Upcoming"
        case .open: return "Open"
        case .closed: return "Closed"
        case .listed: return "Listed"
        case .allotment: return "Allotment"
        }
    }

    var color: Color {
        switch self {
        case .open: return Theme.profit
        case .upcoming: return Theme.accentOrange
        case .closed: return Theme.loss
        case .listed: return Color(hex: "3B82F6")
        case .allotment: return Color(hex: "8B5CF6")
        }
    }

    var icon: String {
        switch self {
        case .open: return "checkmark.circle.fill"
        case .upcoming: return "clock.fill"
        case .closed: return "xmark.circle.fill"
        case .listed: return "chart.line.uptrend.xyaxis"
        case .allotment: return "person.2.fill"
        }
    }
}

// MARK: - IPO Type

enum IPOType: String, Codable, CaseIterable {
    case mainboard = "mainboard"
    case sme = "sme"

    var displayName: String {
        switch self {
        case .mainboard: return "Mainboard"
        case .sme: return "SME"
        }
    }

    var color: Color {
        switch self {
        case .mainboard: return Color(hex: "3B82F6")
        case .sme: return Color(hex: "8B5CF6")
        }
    }
}

// MARK: - AI Verdict

enum IPOVerdict: String, Codable {
    case subscribe = "Subscribe"
    case avoid = "Avoid"
    case neutral = "Neutral"

    var color: Color {
        switch self {
        case .subscribe: return Theme.profit
        case .avoid: return Theme.loss
        case .neutral: return Theme.accentOrange
        }
    }

    var icon: String {
        switch self {
        case .subscribe: return "hand.thumbsup.fill"
        case .avoid: return "hand.thumbsdown.fill"
        case .neutral: return "hand.raised.fill"
        }
    }
}

// MARK: - GMP Data

struct GMPData: Codable, Equatable {
    let gmpValue: Double?
    let estimatedListingPrice: Double?
    let listingGainPct: Double?

    enum CodingKeys: String, CodingKey {
        case gmpValue = "gmp_value"
        case estimatedListingPrice = "estimated_listing_price"
        case listingGainPct = "listing_gain_pct"
    }

    var isPositive: Bool {
        (gmpValue ?? 0) >= 0
    }
}

// MARK: - IPO Item

struct IPOItem: Codable, Identifiable, Equatable {
    let companyName: String
    let slug: String
    let status: IPOStatus
    let ipoType: IPOType
    let openDate: String?
    let closeDate: String?
    let listingDate: String?
    let priceBandLow: Double?
    let priceBandHigh: Double?
    let lotSize: Int?
    let issueSizeCr: String?
    let minInvestment: Double?
    let exchange: String?
    let gmp: GMPData?
    let aiVerdict: String?
    let aiAnalysis: String?

    var id: String { slug }

    enum CodingKeys: String, CodingKey {
        case companyName = "company_name"
        case slug
        case status
        case ipoType = "ipo_type"
        case openDate = "open_date"
        case closeDate = "close_date"
        case listingDate = "listing_date"
        case priceBandLow = "price_band_low"
        case priceBandHigh = "price_band_high"
        case lotSize = "lot_size"
        case issueSizeCr = "issue_size_cr"
        case minInvestment = "min_investment"
        case exchange
        case gmp
        case aiVerdict = "ai_verdict"
        case aiAnalysis = "ai_analysis"
    }

    var priceBandDisplay: String {
        if let low = priceBandLow, let high = priceBandHigh {
            if low == high {
                return "₹\(Int(low))"
            }
            return "₹\(Int(low)) - ₹\(Int(high))"
        }
        return "-"
    }

    var minInvestmentDisplay: String {
        if let min = minInvestment {
            return "₹\(formatNumber(min))"
        }
        return "-"
    }

    var dateRangeDisplay: String {
        if let open = openDate, let close = closeDate {
            return "\(open) - \(close)"
        }
        return openDate ?? closeDate ?? "-"
    }

    var gmpDisplay: String {
        if let gmpValue = gmp?.gmpValue {
            let sign = gmpValue >= 0 ? "+" : ""
            return "\(sign)₹\(Int(gmpValue))"
        }
        return "-"
    }

    var listingGainDisplay: String {
        if let gain = gmp?.listingGainPct {
            let sign = gain >= 0 ? "+" : ""
            return "\(sign)\(String(format: "%.1f", gain))%"
        }
        return "-"
    }

    private func formatNumber(_ num: Double) -> String {
        if num >= 10000000 {
            return String(format: "%.2f Cr", num / 10000000)
        } else if num >= 100000 {
            return String(format: "%.2f L", num / 100000)
        } else if num >= 1000 {
            return String(format: "%.1fK", num / 1000)
        }
        return String(format: "%.0f", num)
    }
}

// MARK: - IPO List Response

struct IPOListResponse: Codable {
    let ipos: [IPOItem]
    let total: Int
    let lastUpdated: String?
    let source: String?

    enum CodingKeys: String, CodingKey {
        case ipos
        case total
        case lastUpdated = "last_updated"
        case source
    }
}

// MARK: - IPO Analysis Response

struct IPOAnalysisResponse: Codable {
    let verdict: String
    let confidence: Int
    let analysis: String
    let keyPositives: [String]
    let keyRisks: [String]
    let expectedListingGain: String?
    let recommendationSummary: String
    let disclaimer: String

    enum CodingKeys: String, CodingKey {
        case verdict
        case confidence
        case analysis
        case keyPositives = "key_positives"
        case keyRisks = "key_risks"
        case expectedListingGain = "expected_listing_gain"
        case recommendationSummary = "recommendation_summary"
        case disclaimer
    }

    var verdictEnum: IPOVerdict {
        IPOVerdict(rawValue: verdict) ?? .neutral
    }
}

// MARK: - IPO Analysis Request

struct IPOAnalysisRequest: Codable {
    let companyName: String

    enum CodingKeys: String, CodingKey {
        case companyName = "company_name"
    }
}
