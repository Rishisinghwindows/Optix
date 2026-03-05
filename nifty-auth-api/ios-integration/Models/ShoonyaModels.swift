import Foundation

// MARK: - Shoonya Configuration

struct ShoonyaConfig {
    static let baseURL = "https://api.shoonya.com/NorenWClientTP"
    static let websocketURL = "wss://api.shoonya.com/NorenWSTP"

    // These should be stored securely and fetched from your backend
    static var vendorCode: String = ""
    static var apiKey: String = ""
}

// MARK: - Login Request/Response

struct ShoonyaLoginRequest: Codable {
    let source: String
    let apkversion: String
    let uid: String
    let pwd: String
    let factor2: String
    let vc: String
    let appkey: String
    let imei: String

    init(userId: String, password: String, totp: String, vendorCode: String, apiKey: String) {
        self.source = "API"
        self.apkversion = "1.0.0"
        self.uid = userId
        self.pwd = password.sha256()
        self.factor2 = totp
        self.vc = vendorCode
        self.appkey = "\(userId)|\(apiKey)".sha256()
        self.imei = UUID().uuidString
    }
}

struct ShoonyaLoginResponse: Codable {
    let stat: String
    let susertoken: String?
    let laession: String?
    let request_time: String?
    let uname: String?
    let email: String?
    let actid: String?
    let exarr: [String]?
    let emsg: String?

    var isSuccess: Bool {
        stat == "Ok" && susertoken != nil
    }
}

// MARK: - User Details

struct ShoonyaUserDetails: Codable {
    let stat: String
    let exarr: [String]?
    let orarr: [String]?
    let email: String?
    let actid: String?
    let brkname: String?
    let brnchid: String?
    let uid: String?
    let emsg: String?
}

// MARK: - Quotes Request/Response

struct ShoonyaQuotesRequest: Codable {
    let uid: String
    let token: String
    let exch: String

    enum CodingKeys: String, CodingKey {
        case uid, token = "token", exch
    }
}

struct ShoonyaQuotesResponse: Codable {
    let stat: String
    let emsg: String?
    let exch: String?
    let tsym: String?
    let cname: String?
    let symname: String?
    let seg: String?
    let instname: String?
    let isin: String?
    let ti: String?
    let ls: String?
    let pp: String?
    let mult: String?
    let uc: String?
    let lc: String?
    let prcftr_d: String?
    let token: String?
    let lp: String?           // Last Price
    let o: String?            // Open
    let h: String?            // High
    let l: String?            // Low
    let c: String?            // Close / Previous Close
    let v: String?            // Volume
    let ltq: String?          // Last Traded Quantity
    let ltt: String?          // Last Trade Time
    let bp1: String?          // Best Buy Price 1
    let sp1: String?          // Best Sell Price 1
    let bq1: String?          // Best Buy Quantity 1
    let sq1: String?          // Best Sell Quantity 1
    let oi: String?           // Open Interest
    let poi: String?          // Previous Open Interest

    var lastPrice: Double? {
        guard let lp = lp else { return nil }
        return Double(lp)
    }

    var changePercent: Double? {
        guard let lastPrice = lastPrice,
              let closeStr = c,
              let close = Double(closeStr),
              close > 0 else { return nil }
        return ((lastPrice - close) / close) * 100
    }
}

// MARK: - Option Chain Request/Response

struct ShoonyaOptionChainRequest: Codable {
    let uid: String
    let tsym: String
    let exch: String
    let strprc: String
    let cnt: String
}

struct ShoonyaOptionChainResponse: Codable {
    let stat: String
    let emsg: String?
    let values: [OptionChainItem]?
}

struct OptionChainItem: Codable, Identifiable {
    var id: String { token ?? UUID().uuidString }

    let exch: String?
    let tsym: String?
    let token: String?
    let optt: String?         // Option Type: CE/PE
    let strprc: String?       // Strike Price
    let pp: String?
    let ti: String?
    let ls: String?

    var strikePrice: Double? {
        guard let strprc = strprc else { return nil }
        return Double(strprc)
    }

    var optionType: String {
        optt ?? ""
    }
}

// MARK: - Search Scrip

struct ShoonyaSearchRequest: Codable {
    let uid: String
    let stext: String
    let exch: String
}

struct ShoonyaSearchResponse: Codable {
    let stat: String
    let emsg: String?
    let values: [ScripInfo]?
}

struct ScripInfo: Codable, Identifiable {
    var id: String { token ?? UUID().uuidString }

    let exch: String?
    let tsym: String?
    let token: String?
    let pp: String?
    let ti: String?
    let ls: String?
    let instname: String?
}

// MARK: - Index List

struct ShoonyaIndexInfo: Codable, Identifiable {
    var id: String { idxname ?? UUID().uuidString }

    let exch: String?
    let idxname: String?
    let token: String?
}

// MARK: - Common Index Tokens

enum ShoonyaIndex: String, CaseIterable {
    case nifty50 = "26000"
    case bankNifty = "26009"
    case finNifty = "26037"
    case midcapNifty = "26074"

    var displayName: String {
        switch self {
        case .nifty50: return "NIFTY 50"
        case .bankNifty: return "BANK NIFTY"
        case .finNifty: return "FIN NIFTY"
        case .midcapNifty: return "MIDCAP NIFTY"
        }
    }

    var exchange: String { "NSE" }
}

// MARK: - Logout Response

struct ShoonyaLogoutResponse: Codable {
    let stat: String
    let emsg: String?
}

// MARK: - Error Types

enum ShoonyaError: LocalizedError {
    case invalidCredentials
    case sessionExpired
    case networkError
    case invalidResponse
    case apiError(String)
    case notLoggedIn

    var errorDescription: String? {
        switch self {
        case .invalidCredentials:
            return "Invalid user ID, password, or TOTP"
        case .sessionExpired:
            return "Session expired. Please login again."
        case .networkError:
            return "Network error. Please check your connection."
        case .invalidResponse:
            return "Invalid response from server"
        case .apiError(let message):
            return message
        case .notLoggedIn:
            return "Please login to continue"
        }
    }
}

// MARK: - SHA256 Extension

extension String {
    func sha256() -> String {
        guard let data = self.data(using: .utf8) else { return "" }

        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash)
        }

        return hash.map { String(format: "%02x", $0) }.joined()
    }
}

// Import for CC_SHA256
import CommonCrypto
