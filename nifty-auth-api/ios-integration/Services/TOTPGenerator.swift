import Foundation
import CryptoKit

/// TOTP (Time-based One-Time Password) Generator
/// Used for generating 2FA codes for Shoonya login
class TOTPGenerator {

    /// Generate a TOTP code from a secret key
    /// - Parameters:
    ///   - secret: Base32 encoded secret key from Shoonya TOTP settings
    ///   - time: Time to generate code for (defaults to current time)
    ///   - digits: Number of digits (default 6)
    ///   - period: Time period in seconds (default 30)
    /// - Returns: TOTP code string
    static func generate(
        secret: String,
        time: Date = Date(),
        digits: Int = 6,
        period: Int = 30
    ) -> String? {
        // Decode base32 secret
        guard let secretData = base32Decode(secret) else {
            return nil
        }

        // Calculate time counter
        let counter = UInt64(time.timeIntervalSince1970) / UInt64(period)

        // Generate HMAC-SHA1
        var counterBytes = counter.bigEndian
        let counterData = Data(bytes: &counterBytes, count: MemoryLayout<UInt64>.size)

        let key = SymmetricKey(data: secretData)
        let hmac = HMAC<Insecure.SHA1>.authenticationCode(for: counterData, using: key)
        let hmacData = Data(hmac)

        // Dynamic truncation
        let offset = Int(hmacData[hmacData.count - 1] & 0x0f)
        let truncatedHash = hmacData.subdata(in: offset..<(offset + 4))

        var number = truncatedHash.withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
        number &= 0x7fffffff

        // Get the required number of digits
        let otp = number % UInt32(pow(10, Double(digits)))

        return String(format: "%0\(digits)d", otp)
    }

    /// Decode Base32 string to Data
    private static func base32Decode(_ string: String) -> Data? {
        let alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        let cleanedString = string.uppercased().replacingOccurrences(of: " ", with: "")

        var bits = ""
        for char in cleanedString {
            guard let index = alphabet.firstIndex(of: char) else {
                continue
            }
            let value = alphabet.distance(from: alphabet.startIndex, to: index)
            bits += String(value, radix: 2).padLeft(toLength: 5, withPad: "0")
        }

        var bytes: [UInt8] = []
        var index = bits.startIndex
        while index < bits.endIndex {
            let endIndex = bits.index(index, offsetBy: 8, limitedBy: bits.endIndex) ?? bits.endIndex
            let byteString = String(bits[index..<endIndex])
            if byteString.count == 8, let byte = UInt8(byteString, radix: 2) {
                bytes.append(byte)
            }
            index = endIndex
        }

        return Data(bytes)
    }

    /// Get remaining seconds until next TOTP code
    static func remainingSeconds(period: Int = 30) -> Int {
        let currentTime = Int(Date().timeIntervalSince1970)
        return period - (currentTime % period)
    }

    /// Check if a TOTP code is still valid
    /// - Parameters:
    ///   - code: The TOTP code to validate
    ///   - secret: Base32 encoded secret key
    ///   - window: Number of periods to check before/after current (default 1)
    /// - Returns: True if code is valid
    static func validate(
        code: String,
        secret: String,
        window: Int = 1
    ) -> Bool {
        let currentTime = Date()

        for i in -window...window {
            let checkTime = currentTime.addingTimeInterval(Double(i * 30))
            if let generatedCode = generate(secret: secret, time: checkTime),
               generatedCode == code {
                return true
            }
        }

        return false
    }
}

// MARK: - String Extension for padding

private extension String {
    func padLeft(toLength length: Int, withPad character: Character) -> String {
        let paddingCount = length - self.count
        if paddingCount > 0 {
            return String(repeating: character, count: paddingCount) + self
        }
        return self
    }
}

// MARK: - Shoonya TOTP Helper

extension ShoonyaManager {
    /// Generate TOTP code from stored secret
    /// - Parameter secret: Base32 TOTP secret from Shoonya
    /// - Returns: 6-digit TOTP code
    func generateTOTP(secret: String) -> String? {
        return TOTPGenerator.generate(secret: secret)
    }

    /// Auto-login with stored TOTP secret
    /// - Parameters:
    ///   - userId: User ID
    ///   - password: Password
    ///   - totpSecret: Base32 TOTP secret
    func autoLogin(
        userId: String,
        password: String,
        totpSecret: String
    ) async throws {
        guard let totp = TOTPGenerator.generate(secret: totpSecret) else {
            throw ShoonyaError.invalidCredentials
        }

        try await login(userId: userId, password: password, totp: totp)
    }
}
