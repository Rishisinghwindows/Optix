import Foundation

// MARK: - Trading Index Model

enum TradingIndex: String, CaseIterable, Identifiable {
    case nifty50 = "NIFTY"
    case bankNifty = "BANKNIFTY"
    case niftyFinService = "FINNIFTY"
    case midcapSelect = "MIDCPNIFTY"
    case sensex = "SENSEX"
    case bankex = "BANKEX"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .nifty50: return "NIFTY 50"
        case .bankNifty: return "BANK NIFTY"
        case .niftyFinService: return "FIN NIFTY"
        case .midcapSelect: return "MIDCAP SELECT"
        case .sensex: return "SENSEX"
        case .bankex: return "BANKEX"
        }
    }

    var shortName: String {
        switch self {
        case .nifty50: return "NIFTY"
        case .bankNifty: return "BANKNIFTY"
        case .niftyFinService: return "FINNIFTY"
        case .midcapSelect: return "MIDCAP"
        case .sensex: return "SENSEX"
        case .bankex: return "BANKEX"
        }
    }

    var exchange: String {
        switch self {
        case .sensex, .bankex: return "BSE"
        default: return "NSE"
        }
    }

    // Upstox instrument key format
    var instrumentKey: String {
        switch self {
        case .nifty50: return "NSE_INDEX|Nifty 50"
        case .bankNifty: return "NSE_INDEX|Nifty Bank"
        case .niftyFinService: return "NSE_INDEX|Nifty Fin Service"
        case .midcapSelect: return "NSE_INDEX|NIFTY MID SELECT"
        case .sensex: return "BSE_INDEX|SENSEX"
        case .bankex: return "BSE_INDEX|BANKEX"
        }
    }

    // Strike price interval
    var strikeInterval: Double {
        switch self {
        case .nifty50: return 50
        case .bankNifty: return 100
        case .niftyFinService: return 50
        case .midcapSelect: return 25
        case .sensex: return 100
        case .bankex: return 100
        }
    }

    // Lot size (revised Nov 2024)
    var lotSize: Int {
        switch self {
        case .nifty50: return 75
        case .bankNifty: return 30
        case .niftyFinService: return 25
        case .midcapSelect: return 50
        case .sensex: return 10
        case .bankex: return 15
        }
    }

    // Expiry day of week (0 = Sunday, 4 = Thursday, 5 = Friday)
    var expiryDayOfWeek: Int {
        switch self {
        case .nifty50: return 4 // Thursday
        case .bankNifty: return 3 // Wednesday
        case .niftyFinService: return 2 // Tuesday
        case .midcapSelect: return 1 // Monday
        case .sensex: return 5 // Friday
        case .bankex: return 1 // Monday
        }
    }

    // Color for UI
    var themeColor: String {
        switch self {
        case .nifty50: return "00C805"  // Green
        case .bankNifty: return "007AFF" // Blue
        case .niftyFinService: return "FF9500" // Orange
        case .midcapSelect: return "AF52DE" // Purple
        case .sensex: return "FF3B30" // Red
        case .bankex: return "5AC8FA" // Cyan
        }
    }

    // Icon for UI
    var icon: String {
        switch self {
        case .nifty50: return "chart.line.uptrend.xyaxis"
        case .bankNifty: return "building.columns.fill"
        case .niftyFinService: return "indianrupeesign.circle.fill"
        case .midcapSelect: return "chart.bar.fill"
        case .sensex: return "s.circle.fill"
        case .bankex: return "b.circle.fill"
        }
    }

    // Approximate base price for mock data generation (Jan 2025)
    var basePrice: Double {
        switch self {
        case .nifty50: return 23500
        case .bankNifty: return 49500
        case .niftyFinService: return 23200
        case .midcapSelect: return 12500
        case .sensex: return 77500
        case .bankex: return 54000
        }
    }
}

// MARK: - Option Type Enum

enum OptionType: String, Codable, CaseIterable {
    case call = "CE"
    case put = "PE"

    var displayName: String {
        switch self {
        case .call: return L.calculatorCall
        case .put: return L.calculatorPut
        }
    }

    var shortName: String {
        return rawValue
    }
}

// MARK: - Option Data Model

struct OptionData: Identifiable, Codable, Equatable {
    let id: UUID
    let strikePrice: Double
    let optionType: OptionType
    let expiryDate: Date
    let lastTradedPrice: Double
    let openInterest: Int
    let changeInOI: Int
    let impliedVolatility: Double
    let bidPrice: Double
    let askPrice: Double
    let bidQty: Int
    let askQty: Int
    let volume: Int
    let underlyingValue: Double

    // Greeks from API (optional - if not provided, calculate using Black-Scholes)
    let delta: Double?
    let gamma: Double?
    let theta: Double?
    let vega: Double?

    // Upstox instrument key for WebSocket subscription
    let instrumentKey: String?

    init(
        id: UUID = UUID(),
        strikePrice: Double,
        optionType: OptionType,
        expiryDate: Date,
        lastTradedPrice: Double,
        openInterest: Int = 0,
        changeInOI: Int = 0,
        impliedVolatility: Double,
        bidPrice: Double = 0,
        askPrice: Double = 0,
        bidQty: Int = 0,
        askQty: Int = 0,
        volume: Int = 0,
        underlyingValue: Double,
        delta: Double? = nil,
        gamma: Double? = nil,
        theta: Double? = nil,
        vega: Double? = nil,
        instrumentKey: String? = nil
    ) {
        self.id = id
        self.strikePrice = strikePrice
        self.optionType = optionType
        self.expiryDate = expiryDate
        self.lastTradedPrice = lastTradedPrice
        self.openInterest = openInterest
        self.changeInOI = changeInOI
        self.impliedVolatility = impliedVolatility
        self.bidPrice = bidPrice
        self.askPrice = askPrice
        self.bidQty = bidQty
        self.askQty = askQty
        self.volume = volume
        self.underlyingValue = underlyingValue
        self.delta = delta
        self.gamma = gamma
        self.theta = theta
        self.vega = vega
        self.instrumentKey = instrumentKey
    }

    var isITM: Bool {
        switch optionType {
        case .call:
            return underlyingValue > strikePrice
        case .put:
            return underlyingValue < strikePrice
        }
    }

    var isATM: Bool {
        let diff = abs(underlyingValue - strikePrice)
        return diff <= 50 // Within 50 points considered ATM
    }

    var moneyness: String {
        if isATM { return "ATM" }
        return isITM ? "ITM" : "OTM"
    }

    var daysToExpiry: Int {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.day], from: Date(), to: expiryDate)
        return max(components.day ?? 0, 0)
    }

    var timeToExpiryYears: Double {
        return Double(daysToExpiry) / 365.0
    }

    /// Precise time to expiry in years using seconds-based calculation.
    /// Accounts for NSE market close at 15:30 IST on expiry day,
    /// avoiding integer-day truncation that significantly misprices
    /// short-dated options.
    var preciseTimeToExpiryYears: Double {
        var calendar = Calendar.current
        calendar.timeZone = TimeZone(identifier: "Asia/Kolkata")!
        var components = calendar.dateComponents(in: calendar.timeZone, from: expiryDate)
        components.hour = 15
        components.minute = 30
        components.second = 0
        guard let adjustedExpiry = calendar.date(from: components) else {
            return timeToExpiryYears
        }
        let seconds = adjustedExpiry.timeIntervalSince(Date())
        return max(seconds / (365.25 * 24 * 3600), 0)
    }

    var displayStrike: String {
        return String(format: "%.0f", strikePrice)
    }

    var displayLTP: String {
        // Ensure we don't display negative prices (can happen with mock data)
        let safeLTP = max(lastTradedPrice, 0.05)
        return String(format: "%.2f", safeLTP)
    }

    var displayIV: String {
        return String(format: "%.2f%%", impliedVolatility * 100)
    }
}

// MARK: - Option Chain Strike Row

struct OptionChainRow: Identifiable {
    var id: Double { strikePrice }
    let strikePrice: Double
    let callOption: OptionData?
    let putOption: OptionData?

    init(strikePrice: Double, callOption: OptionData?, putOption: OptionData?) {
        self.strikePrice = strikePrice
        self.callOption = callOption
        self.putOption = putOption
    }

    var displayStrike: String {
        return String(format: "%.0f", strikePrice)
    }
}

// MARK: - Option Chain Response Model (NSE API)

struct NSEOptionChainResponse: Codable {
    let records: Records

    struct Records: Codable {
        let expiryDates: [String]
        let data: [DataItem]
        let underlyingValue: Double
        let strikePrices: [Double]
        let timestamp: String

        enum CodingKeys: String, CodingKey {
            case expiryDates
            case data
            case underlyingValue
            case strikePrices
            case timestamp
        }
    }

    struct DataItem: Codable {
        let strikePrice: Double
        let expiryDate: String
        let ce: OptionDetail?
        let pe: OptionDetail?

        enum CodingKeys: String, CodingKey {
            case strikePrice
            case expiryDate
            case ce = "CE"
            case pe = "PE"
        }
    }

    struct OptionDetail: Codable {
        let strikePrice: Double
        let expiryDate: String
        let underlying: String
        let identifier: String
        let openInterest: Int
        let changeinOpenInterest: Int
        let pchangeinOpenInterest: Double
        let totalTradedVolume: Int
        let impliedVolatility: Double
        let lastPrice: Double
        let change: Double
        let pChange: Double
        let totalBuyQuantity: Int
        let totalSellQuantity: Int
        let bidQty: Int
        let bidprice: Double
        let askQty: Int
        let askPrice: Double
        let underlyingValue: Double

        enum CodingKeys: String, CodingKey {
            case strikePrice
            case expiryDate
            case underlying
            case identifier
            case openInterest
            case changeinOpenInterest
            case pchangeinOpenInterest
            case totalTradedVolume
            case impliedVolatility
            case lastPrice
            case change
            case pChange
            case totalBuyQuantity
            case totalSellQuantity
            case bidQty
            case bidprice
            case askQty
            case askPrice
            case underlyingValue
        }
    }
}

// MARK: - Expiry Date Helper

struct ExpiryDate: Identifiable, Hashable {
    let id: String
    let date: Date
    let displayString: String

    init(dateString: String) {
        self.id = dateString

        let formatter = DateFormatter()
        formatter.dateFormat = "dd-MMM-yyyy"
        formatter.locale = Locale(identifier: "en_IN")

        if let date = formatter.date(from: dateString) {
            self.date = date
            let displayFormatter = DateFormatter()
            displayFormatter.dateFormat = "dd MMM"
            self.displayString = displayFormatter.string(from: date)
        } else {
            self.date = Date()
            self.displayString = dateString
        }
    }
}
