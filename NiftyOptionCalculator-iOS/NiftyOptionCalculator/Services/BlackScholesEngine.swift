import Foundation

/// Black-Scholes Option Pricing Engine
/// Calculates option prices and Greeks for European-style options
final class BlackScholesEngine {

    // MARK: - Constants

    /// Default risk-free rate for India (RBI repo rate approximation)
    static let defaultRiskFreeRate: Double = 0.07

    // MARK: - Core Calculations

    /// Calculate d1 component of Black-Scholes formula
    /// - Parameters:
    ///   - spotPrice: Current underlying price (S)
    ///   - strikePrice: Option strike price (K)
    ///   - timeToExpiry: Time to expiration in years (T)
    ///   - riskFreeRate: Risk-free interest rate (r)
    ///   - volatility: Implied volatility (σ)
    /// - Returns: d1 value
    private static func calculateD1(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double
    ) -> Double {
        guard timeToExpiry > 0, volatility > 0, strikePrice > 0, spotPrice > 0 else {
            return 0
        }

        let sqrtT = sqrt(timeToExpiry)
        let numerator = log(spotPrice / strikePrice) + (riskFreeRate + (volatility * volatility) / 2) * timeToExpiry
        let denominator = volatility * sqrtT

        return numerator / denominator
    }

    /// Calculate d2 component of Black-Scholes formula
    /// d2 = d1 - σ√T
    private static func calculateD2(d1: Double, volatility: Double, timeToExpiry: Double) -> Double {
        return d1 - volatility * sqrt(timeToExpiry)
    }

    /// Standard normal cumulative distribution function (CDF)
    /// Uses approximation for N(x)
    private static func normalCDF(_ x: Double) -> Double {
        return 0.5 * erfc(-x / sqrt(2))
    }

    /// Standard normal probability density function (PDF)
    /// N'(x) = (1/√2π) * e^(-x²/2)
    private static func normalPDF(_ x: Double) -> Double {
        return exp(-0.5 * x * x) / sqrt(2 * .pi)
    }

    // MARK: - Option Pricing

    /// Calculate Call option price using Black-Scholes formula
    /// Call = S·N(d1) - K·e^(-rT)·N(d2)
    static func calculateCallPrice(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = defaultRiskFreeRate,
        volatility: Double
    ) -> Double {
        guard timeToExpiry > 0 else {
            // At expiry, call value is max(S - K, 0)
            return max(spotPrice - strikePrice, 0)
        }

        let d1 = calculateD1(spotPrice: spotPrice, strikePrice: strikePrice,
                            timeToExpiry: timeToExpiry, riskFreeRate: riskFreeRate,
                            volatility: volatility)
        let d2 = calculateD2(d1: d1, volatility: volatility, timeToExpiry: timeToExpiry)

        let discountFactor = exp(-riskFreeRate * timeToExpiry)
        let callPrice = spotPrice * normalCDF(d1) - strikePrice * discountFactor * normalCDF(d2)

        return max(callPrice, 0)
    }

    /// Calculate Put option price using Black-Scholes formula
    /// Put = K·e^(-rT)·N(-d2) - S·N(-d1)
    static func calculatePutPrice(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = defaultRiskFreeRate,
        volatility: Double
    ) -> Double {
        guard timeToExpiry > 0 else {
            // At expiry, put value is max(K - S, 0)
            return max(strikePrice - spotPrice, 0)
        }

        let d1 = calculateD1(spotPrice: spotPrice, strikePrice: strikePrice,
                            timeToExpiry: timeToExpiry, riskFreeRate: riskFreeRate,
                            volatility: volatility)
        let d2 = calculateD2(d1: d1, volatility: volatility, timeToExpiry: timeToExpiry)

        let discountFactor = exp(-riskFreeRate * timeToExpiry)
        let putPrice = strikePrice * discountFactor * normalCDF(-d2) - spotPrice * normalCDF(-d1)

        return max(putPrice, 0)
    }

    // MARK: - Greeks Calculations

    /// Calculate all Greeks for a Call option
    static func calculateCallGreeks(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = defaultRiskFreeRate,
        volatility: Double
    ) -> GreeksResult {
        guard timeToExpiry > 0, volatility > 0 else {
            return GreeksResult(delta: spotPrice > strikePrice ? 1 : 0,
                               gamma: 0, theta: 0, vega: 0, rho: 0)
        }

        let d1 = calculateD1(spotPrice: spotPrice, strikePrice: strikePrice,
                            timeToExpiry: timeToExpiry, riskFreeRate: riskFreeRate,
                            volatility: volatility)
        let d2 = calculateD2(d1: d1, volatility: volatility, timeToExpiry: timeToExpiry)
        let sqrtT = sqrt(timeToExpiry)
        let discountFactor = exp(-riskFreeRate * timeToExpiry)

        // Delta: N(d1)
        let delta = normalCDF(d1)

        // Gamma: N'(d1) / (S·σ·√T)
        let gamma = normalPDF(d1) / (spotPrice * volatility * sqrtT)

        // Theta: -[S·N'(d1)·σ/(2√T)] - r·K·e^(-rT)·N(d2)
        // Expressed per day (divide by 365)
        let thetaAnnual = -(spotPrice * normalPDF(d1) * volatility / (2 * sqrtT)) -
                          riskFreeRate * strikePrice * discountFactor * normalCDF(d2)
        let theta = thetaAnnual / 365.0

        // Vega: S·√T·N'(d1) / 100 (per 1% change in volatility)
        let vega = spotPrice * sqrtT * normalPDF(d1) / 100.0

        // Rho: K·T·e^(-rT)·N(d2) / 100 (per 1% change in rate)
        let rho = strikePrice * timeToExpiry * discountFactor * normalCDF(d2) / 100.0

        return GreeksResult(delta: delta, gamma: gamma, theta: theta, vega: vega, rho: rho)
    }

    /// Calculate all Greeks for a Put option
    static func calculatePutGreeks(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = defaultRiskFreeRate,
        volatility: Double
    ) -> GreeksResult {
        guard timeToExpiry > 0, volatility > 0 else {
            return GreeksResult(delta: spotPrice < strikePrice ? -1 : 0,
                               gamma: 0, theta: 0, vega: 0, rho: 0)
        }

        let d1 = calculateD1(spotPrice: spotPrice, strikePrice: strikePrice,
                            timeToExpiry: timeToExpiry, riskFreeRate: riskFreeRate,
                            volatility: volatility)
        let d2 = calculateD2(d1: d1, volatility: volatility, timeToExpiry: timeToExpiry)
        let sqrtT = sqrt(timeToExpiry)
        let discountFactor = exp(-riskFreeRate * timeToExpiry)

        // Delta: N(d1) - 1
        let delta = normalCDF(d1) - 1

        // Gamma: Same as call - N'(d1) / (S·σ·√T)
        let gamma = normalPDF(d1) / (spotPrice * volatility * sqrtT)

        // Theta: -[S·N'(d1)·σ/(2√T)] + r·K·e^(-rT)·N(-d2)
        // Expressed per day (divide by 365)
        let thetaAnnual = -(spotPrice * normalPDF(d1) * volatility / (2 * sqrtT)) +
                          riskFreeRate * strikePrice * discountFactor * normalCDF(-d2)
        let theta = thetaAnnual / 365.0

        // Vega: Same as call - S·√T·N'(d1) / 100
        let vega = spotPrice * sqrtT * normalPDF(d1) / 100.0

        // Rho: -K·T·e^(-rT)·N(-d2) / 100
        let rho = -strikePrice * timeToExpiry * discountFactor * normalCDF(-d2) / 100.0

        return GreeksResult(delta: delta, gamma: gamma, theta: theta, vega: vega, rho: rho)
    }

    // MARK: - Implied Volatility

    /// Calculate implied volatility using Newton-Raphson method
    /// - Parameters:
    ///   - optionPrice: Current market price of the option
    ///   - spotPrice: Current underlying price
    ///   - strikePrice: Option strike price
    ///   - timeToExpiry: Time to expiration in years
    ///   - riskFreeRate: Risk-free interest rate
    ///   - isCall: True for call option, false for put
    ///   - maxIterations: Maximum iterations for convergence
    ///   - tolerance: Convergence tolerance
    /// - Returns: Implied volatility or nil if not converged
    static func calculateImpliedVolatility(
        optionPrice: Double,
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = defaultRiskFreeRate,
        isCall: Bool,
        maxIterations: Int = 100,
        tolerance: Double = 0.0001
    ) -> Double? {
        guard optionPrice > 0, timeToExpiry > 0 else { return nil }

        // Initial guess using Brenner-Subrahmanyam approximation
        var sigma = sqrt(2 * .pi / timeToExpiry) * optionPrice / spotPrice
        sigma = max(0.01, min(sigma, 5.0)) // Bound between 1% and 500%

        for _ in 0..<maxIterations {
            let price = isCall ?
                calculateCallPrice(spotPrice: spotPrice, strikePrice: strikePrice,
                                  timeToExpiry: timeToExpiry, riskFreeRate: riskFreeRate,
                                  volatility: sigma) :
                calculatePutPrice(spotPrice: spotPrice, strikePrice: strikePrice,
                                 timeToExpiry: timeToExpiry, riskFreeRate: riskFreeRate,
                                 volatility: sigma)

            let diff = price - optionPrice

            if abs(diff) < tolerance {
                return sigma
            }

            // Calculate vega for Newton-Raphson
            let d1 = calculateD1(spotPrice: spotPrice, strikePrice: strikePrice,
                                timeToExpiry: timeToExpiry, riskFreeRate: riskFreeRate,
                                volatility: sigma)
            let vega = spotPrice * sqrt(timeToExpiry) * normalPDF(d1)

            if vega < 0.0001 {
                // Vega too small, adjust sigma manually
                sigma = diff > 0 ? sigma * 0.9 : sigma * 1.1
            } else {
                sigma = sigma - diff / vega
            }

            sigma = max(0.01, min(sigma, 5.0))
        }

        return sigma // Return last estimate even if not fully converged
    }

    // MARK: - Utility Methods

    /// Convert days to years for time to expiry calculation
    static func daysToYears(_ days: Int) -> Double {
        return Double(days) / 365.0
    }

    /// Calculate time to expiry from expiry date
    static func timeToExpiry(from expiryDate: Date, currentDate: Date = Date()) -> Double {
        let seconds = expiryDate.timeIntervalSince(currentDate)
        let days = seconds / (24 * 60 * 60)
        return max(days / 365.0, 0)
    }

    /// Precise time to expiry accounting for NSE market close (15:30 IST).
    /// Avoids integer-day truncation that misprices short-dated options.
    static func preciseTimeToExpiry(from expiryDate: Date) -> Double {
        var calendar = Calendar.current
        calendar.timeZone = TimeZone(identifier: "Asia/Kolkata")!
        var components = calendar.dateComponents(in: calendar.timeZone, from: expiryDate)
        components.hour = 15
        components.minute = 30
        components.second = 0
        guard let adjustedExpiry = calendar.date(from: components) else {
            return timeToExpiry(from: expiryDate)
        }
        let seconds = adjustedExpiry.timeIntervalSince(Date())
        return max(seconds / (365.25 * 24 * 3600), 0)
    }
}
