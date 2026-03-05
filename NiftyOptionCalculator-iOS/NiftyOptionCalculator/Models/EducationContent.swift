import Foundation
import SwiftUI

// MARK: - Education Module Types

enum EducationCategory: String, CaseIterable, Identifiable {
    case basics = "Basics"
    case greeks = "Greeks"
    case strategies = "Strategies"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .basics: return "book.fill"
        case .greeks: return "function"
        case .strategies: return "puzzlepiece.fill"
        }
    }

    var color: Color {
        switch self {
        case .basics: return .blue
        case .greeks: return .orange
        case .strategies: return .green
        }
    }

    var description: String {
        switch self {
        case .basics: return "Learn the fundamentals of options trading"
        case .greeks: return "Understand how option prices change"
        case .strategies: return "Master popular trading strategies"
        }
    }
}

// MARK: - Lesson Model

struct Lesson: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let icon: String
    let duration: String // "5 min read"
    let content: [LessonSection]
    let keyPoints: [String]
    let quiz: [QuizQuestion]?
}

struct LessonSection: Identifiable {
    let id = UUID()
    let title: String?
    let content: String
    let example: LessonExample?
    let tip: String?
}

struct LessonExample: Identifiable {
    let id = UUID()
    let title: String
    let scenario: String
    let calculation: String?
    let result: String
}

struct QuizQuestion: Identifiable {
    let id = UUID()
    let question: String
    let options: [String]
    let correctIndex: Int
    let explanation: String
}

// MARK: - Greek Model

struct GreekInfo: Identifiable {
    let id: String
    let name: String
    let symbol: String
    let color: Color
    let shortDescription: String
    let fullDescription: String
    let impact: String
    let range: String
    let example: String
    let tips: [String]
}

// MARK: - Strategy Model

struct OptionStrategy: Identifiable {
    let id: String
    let name: String
    let type: EducationStrategyType
    let outlook: EducationMarketOutlook
    let riskLevel: EducationRiskLevel
    let legs: [EducationStrategyLeg]
    let description: String
    let whenToUse: [String]
    let maxProfit: String
    let maxLoss: String
    let breakeven: String
    let pros: [String]
    let cons: [String]
    let example: EducationStrategyExample?
}

enum EducationStrategyType: String {
    case bullish = "Bullish"
    case bearish = "Bearish"
    case neutral = "Neutral"
    case volatile = "Volatile"
}

enum EducationMarketOutlook: String {
    case bullish = "Bullish"
    case bearish = "Bearish"
    case neutral = "Neutral"
    case highVolatility = "High Volatility"
    case lowVolatility = "Low Volatility"
}

enum EducationRiskLevel: String {
    case low = "Low"
    case medium = "Medium"
    case high = "High"

    var color: Color {
        switch self {
        case .low: return .green
        case .medium: return .orange
        case .high: return .red
        }
    }
}

struct EducationStrategyLeg: Identifiable {
    let id = UUID()
    let action: String // "Buy" or "Sell"
    let optionType: String // "Call" or "Put"
    let strike: String // "ATM", "OTM", "ITM", or specific like "+100"
    let quantity: Int
}

struct EducationStrategyExample: Identifiable {
    let id = UUID()
    let spotPrice: Double
    let strikes: [Double]
    let premiums: [Double]
    let netCost: Double
    let scenarios: [ScenarioResult]
}

struct ScenarioResult: Identifiable {
    let id = UUID()
    let expiryPrice: Double
    let profit: Double
    let description: String
}

// MARK: - Education Data Provider

final class EducationDataProvider {
    static let shared = EducationDataProvider()

    // MARK: - Basics Lessons

    var basicsLessons: [Lesson] {
        [
            whatAreOptionsLesson,
            callOptionsLesson,
            putOptionsLesson,
            strikePriceLesson,
            expiryLesson,
            premiumLesson,
            intrinsicExtrinsicLesson,
            itmOtmAtmLesson
        ]
    }

    private var whatAreOptionsLesson: Lesson {
        Lesson(
            id: "what-are-options",
            title: L.quizTitleWhatAreOptions,
            subtitle: "Understanding the basics",
            icon: "questionmark.circle.fill",
            duration: "5 min",
            content: [
                LessonSection(
                    title: nil,
                    content: "An option is a contract that gives you the RIGHT (but not obligation) to buy or sell an underlying asset at a specific price before a certain date.",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "Key Concept",
                    content: "Think of an option like a reservation at a restaurant. You pay a small fee to reserve a table (premium), and you have the right to use that table (exercise) by a certain time (expiry). If you don't show up, you only lose the reservation fee.",
                    example: LessonExample(
                        title: "Real World Analogy",
                        scenario: "You pay ₹500 to reserve a popular iPhone for ₹80,000 for 1 week",
                        calculation: nil,
                        result: "If price rises to ₹90,000, you still get it at ₹80,000. If price drops, you skip the deal and lose only ₹500."
                    ),
                    tip: nil
                ),
                LessonSection(
                    title: "Two Types of Options",
                    content: "CALL Option: Right to BUY\nPUT Option: Right to SELL\n\nRemember: \"Call up, Put down\" - Calls profit when price goes UP, Puts profit when price goes DOWN.",
                    example: nil,
                    tip: "Most beginners start with buying options because the risk is limited to the premium paid."
                )
            ],
            keyPoints: [
                "Options give you rights, not obligations",
                "You pay a premium for this right",
                "Calls = Right to Buy, Puts = Right to Sell",
                "Limited risk when buying options"
            ],
            quiz: [
                QuizQuestion(
                    question: L.quizWhatAreOptionsQ,
                    options: [L.quizWhatAreOptionsO1, L.quizWhatAreOptionsO2, L.quizWhatAreOptionsO3, L.quizWhatAreOptionsO4],
                    correctIndex: 1,
                    explanation: L.quizWhatAreOptionsExp
                )
            ]
        )
    }

    private var callOptionsLesson: Lesson {
        Lesson(
            id: "call-options",
            title: L.quizTitleCallOptions,
            subtitle: "Profit when market goes UP",
            icon: "arrow.up.circle.fill",
            duration: "6 min",
            content: [
                LessonSection(
                    title: nil,
                    content: "A CALL option gives you the right to BUY the underlying asset at the strike price. You buy calls when you expect the market to go UP.",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "How Calls Work",
                    content: "When you buy a call:\n• You pay a premium upfront\n• If price goes UP above strike + premium, you profit\n• If price stays below strike, you lose the premium\n• Maximum loss = Premium paid\n• Maximum profit = Unlimited (theoretically)",
                    example: LessonExample(
                        title: "NIFTY Call Example",
                        scenario: "NIFTY is at 25,000. You buy 25,000 CE for ₹200 premium.",
                        calculation: "Breakeven = 25,000 + 200 = 25,200\nLot size = 75\nTotal premium = 200 × 75 = ₹15,000",
                        result: "If NIFTY expires at 25,500:\nProfit = (25,500 - 25,200) × 75 = ₹22,500"
                    ),
                    tip: "Buy calls when you're bullish but want limited risk"
                ),
                LessonSection(
                    title: "When to Buy Calls",
                    content: "• You expect the market to rise\n• Before positive news/events\n• Support level holding strong\n• Bullish chart patterns\n• Low IV environment (options are cheap)",
                    example: nil,
                    tip: nil
                )
            ],
            keyPoints: [
                "Call = Right to BUY",
                "Buy calls when BULLISH",
                "Max loss = Premium paid",
                "Max profit = Unlimited",
                "Breakeven = Strike + Premium"
            ],
            quiz: [
                QuizQuestion(
                    question: L.quizCallOptionsQ,
                    options: [L.quizCallOptionsO1, L.quizCallOptionsO2, L.quizCallOptionsO3, L.quizCallOptionsO4],
                    correctIndex: 1,
                    explanation: L.quizCallOptionsExp
                )
            ]
        )
    }

    private var putOptionsLesson: Lesson {
        Lesson(
            id: "put-options",
            title: L.quizTitlePutOptions,
            subtitle: "Profit when market goes DOWN",
            icon: "arrow.down.circle.fill",
            duration: "6 min",
            content: [
                LessonSection(
                    title: nil,
                    content: "A PUT option gives you the right to SELL the underlying asset at the strike price. You buy puts when you expect the market to go DOWN.",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "How Puts Work",
                    content: "When you buy a put:\n• You pay a premium upfront\n• If price goes DOWN below strike - premium, you profit\n• If price stays above strike, you lose the premium\n• Maximum loss = Premium paid\n• Maximum profit = Strike price (if asset goes to zero)",
                    example: LessonExample(
                        title: "NIFTY Put Example",
                        scenario: "NIFTY is at 25,000. You buy 25,000 PE for ₹200 premium.",
                        calculation: "Breakeven = 25,000 - 200 = 24,800\nLot size = 75\nTotal premium = 200 × 75 = ₹15,000",
                        result: "If NIFTY expires at 24,500:\nProfit = (24,800 - 24,500) × 75 = ₹22,500"
                    ),
                    tip: "Puts are like insurance - they protect against downside"
                ),
                LessonSection(
                    title: "When to Buy Puts",
                    content: "• You expect the market to fall\n• Before negative news/events\n• Resistance level rejection\n• Bearish chart patterns\n• To hedge existing long positions",
                    example: nil,
                    tip: nil
                )
            ],
            keyPoints: [
                "Put = Right to SELL",
                "Buy puts when BEARISH",
                "Max loss = Premium paid",
                "Puts can hedge your portfolio",
                "Breakeven = Strike - Premium"
            ],
            quiz: [
                QuizQuestion(
                    question: L.quizPutOptionsQ,
                    options: [L.quizPutOptionsO1, L.quizPutOptionsO2, L.quizPutOptionsO3, L.quizPutOptionsO4],
                    correctIndex: 2,
                    explanation: L.quizPutOptionsExp
                )
            ]
        )
    }

    private var strikePriceLesson: Lesson {
        Lesson(
            id: "strike-price",
            title: "Strike Price",
            subtitle: "The price that matters most",
            icon: "target",
            duration: "5 min",
            content: [
                LessonSection(
                    title: nil,
                    content: "The strike price is the predetermined price at which you can buy (call) or sell (put) the underlying asset. It's the price you're \"betting\" the market will cross.",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "Strike Price Selection",
                    content: "Choosing the right strike is crucial:\n\n• Lower strike calls = More expensive, higher probability\n• Higher strike calls = Cheaper, lower probability\n• Higher strike puts = More expensive, higher probability\n• Lower strike puts = Cheaper, lower probability",
                    example: LessonExample(
                        title: "Strike Comparison",
                        scenario: "NIFTY at 25,000. Compare call options:",
                        calculation: "24,800 CE = ₹350 (ITM, high premium)\n25,000 CE = ₹200 (ATM, medium premium)\n25,200 CE = ₹100 (OTM, low premium)",
                        result: "Lower strikes cost more but have higher chance of profit"
                    ),
                    tip: nil
                ),
                LessonSection(
                    title: "Strike Intervals",
                    content: "Different indices have different strike intervals:\n• NIFTY: Every 50 points (25000, 25050, 25100...)\n• BANK NIFTY: Every 100 points (52000, 52100, 52200...)\n• FIN NIFTY: Every 50 points",
                    example: nil,
                    tip: "Start with ATM options - they offer the best balance of cost and probability"
                )
            ],
            keyPoints: [
                "Strike price = Your target price",
                "Lower strikes = Higher premium, higher probability",
                "Higher strikes = Lower premium, lower probability",
                "ATM strikes are often the sweet spot"
            ],
            quiz: nil
        )
    }

    private var expiryLesson: Lesson {
        Lesson(
            id: "expiry",
            title: "Expiry Date",
            subtitle: "Time is money in options",
            icon: "calendar.badge.clock",
            duration: "5 min",
            content: [
                LessonSection(
                    title: nil,
                    content: "Every option has an expiry date - the last day you can exercise your right. After expiry, the option becomes worthless if not in profit.",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "Expiry Schedule in India",
                    content: "• NIFTY: Every Thursday\n• BANK NIFTY: Every Wednesday\n• FIN NIFTY: Every Tuesday\n• SENSEX: Every Friday\n\nMonthly expiry = Last Thursday of month\nWeekly expiry = Every week",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "Time Decay (Theta)",
                    content: "As expiry approaches, option value decreases due to time decay. This happens because there's less time for the option to become profitable.\n\nTime decay accelerates in the last week before expiry!",
                    example: LessonExample(
                        title: "Time Decay Example",
                        scenario: "A 25,000 CE with 30 days to expiry costs ₹300",
                        calculation: "After 15 days (no price change): ~₹210\nAfter 25 days: ~₹100\nOn expiry day: Only intrinsic value",
                        result: "The option loses ~₹200 just due to time passing"
                    ),
                    tip: "Avoid buying options with less than 1 week to expiry unless you're experienced"
                )
            ],
            keyPoints: [
                "Options expire on specific days",
                "Time decay eats into option value daily",
                "Longer expiry = More expensive but safer",
                "Weekly options decay faster"
            ],
            quiz: nil
        )
    }

    private var premiumLesson: Lesson {
        Lesson(
            id: "premium",
            title: "Option Premium",
            subtitle: "The price you pay for options",
            icon: "indianrupeesign.circle.fill",
            duration: "6 min",
            content: [
                LessonSection(
                    title: nil,
                    content: "Premium is the price you pay to buy an option. It's determined by multiple factors and represents the maximum you can lose when buying options.",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "What Affects Premium?",
                    content: "1. Intrinsic Value: How much the option is in-the-money\n2. Time Value: More time = Higher premium\n3. Implied Volatility (IV): Higher IV = Higher premium\n4. Interest Rates: Minor effect\n5. Distance from Strike: Closer to ATM = Higher premium",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "Calculating Total Cost",
                    content: "Total cost = Premium × Lot Size\n\nIn NIFTY (lot size 75):\nIf premium is ₹200, total cost = ₹200 × 75 = ₹15,000",
                    example: LessonExample(
                        title: "Premium Calculation",
                        scenario: "You want to buy 2 lots of NIFTY 25,000 CE at ₹180",
                        calculation: "Premium per unit = ₹180\nLot size = 75\nNumber of lots = 2\nTotal = 180 × 75 × 2 = ₹27,000",
                        result: "Your maximum loss is ₹27,000, maximum profit is unlimited"
                    ),
                    tip: "Always calculate your total exposure before trading"
                )
            ],
            keyPoints: [
                "Premium = Cost of buying an option",
                "Total cost = Premium × Lot size × Lots",
                "Premium = Intrinsic value + Time value",
                "High IV = Expensive options"
            ],
            quiz: nil
        )
    }

    private var intrinsicExtrinsicLesson: Lesson {
        Lesson(
            id: "intrinsic-extrinsic",
            title: "Intrinsic & Extrinsic Value",
            subtitle: "Two parts of every premium",
            icon: "square.split.2x1.fill",
            duration: "5 min",
            content: [
                LessonSection(
                    title: "Intrinsic Value",
                    content: "The real, tangible value if the option were exercised right now.\n\nFor Calls: Max(0, Spot - Strike)\nFor Puts: Max(0, Strike - Spot)\n\nOnly ITM options have intrinsic value.",
                    example: LessonExample(
                        title: "Intrinsic Value Example",
                        scenario: "NIFTY at 25,100",
                        calculation: "25,000 CE intrinsic = 25,100 - 25,000 = ₹100\n25,200 CE intrinsic = Max(0, 25,100 - 25,200) = ₹0\n25,000 PE intrinsic = Max(0, 25,000 - 25,100) = ₹0",
                        result: "Only the 25,000 CE has intrinsic value"
                    ),
                    tip: nil
                ),
                LessonSection(
                    title: "Extrinsic (Time) Value",
                    content: "The extra value above intrinsic, representing:\n• Time remaining until expiry\n• Implied volatility expectations\n• Probability of becoming profitable\n\nExtrinsic Value = Premium - Intrinsic Value",
                    example: LessonExample(
                        title: "Time Value Example",
                        scenario: "NIFTY at 25,100, 25,000 CE trading at ₹250",
                        calculation: "Intrinsic value = 25,100 - 25,000 = ₹100\nTotal premium = ₹250\nExtrinsic value = 250 - 100 = ₹150",
                        result: "₹150 is \"time value\" that will decay to zero by expiry"
                    ),
                    tip: "At expiry, only intrinsic value remains. All time value becomes zero."
                )
            ],
            keyPoints: [
                "Intrinsic = Real value now",
                "Extrinsic = Time + volatility value",
                "OTM options have only extrinsic value",
                "Extrinsic value decays to zero at expiry"
            ],
            quiz: nil
        )
    }

    private var itmOtmAtmLesson: Lesson {
        Lesson(
            id: "itm-otm-atm",
            title: L.quizTitleItmOtmAtm,
            subtitle: "Moneyness of options",
            icon: "slider.horizontal.3",
            duration: "5 min",
            content: [
                LessonSection(
                    title: "In-The-Money (ITM)",
                    content: "Option has intrinsic value.\n\n• Call is ITM when: Spot > Strike\n• Put is ITM when: Spot < Strike\n\nITM options are more expensive but have higher probability of profit.",
                    example: nil,
                    tip: nil
                ),
                LessonSection(
                    title: "At-The-Money (ATM)",
                    content: "Strike price ≈ Current spot price.\n\nATM options have:\n• Highest time value\n• Delta around 0.5\n• Best balance of cost vs probability",
                    example: nil,
                    tip: "ATM options are most liquid and popular"
                ),
                LessonSection(
                    title: "Out-of-The-Money (OTM)",
                    content: "Option has no intrinsic value.\n\n• Call is OTM when: Spot < Strike\n• Put is OTM when: Spot > Strike\n\nOTM options are cheap but have lower probability of profit.",
                    example: LessonExample(
                        title: "Moneyness Example",
                        scenario: "NIFTY at 25,000",
                        calculation: "24,800 CE = ITM (100 points in-the-money)\n25,000 CE = ATM (at the money)\n25,200 CE = OTM (200 points out-of-money)",
                        result: "ITM costs most, OTM costs least"
                    ),
                    tip: nil
                )
            ],
            keyPoints: [
                "ITM = Has intrinsic value (expensive)",
                "ATM = Strike ≈ Spot (balanced)",
                "OTM = No intrinsic value (cheap)",
                "For Calls: Lower strike = More ITM",
                "For Puts: Higher strike = More ITM"
            ],
            quiz: [
                QuizQuestion(
                    question: L.quizItmOtmAtmQ,
                    options: [L.quizItmOtmAtmO1, L.quizItmOtmAtmO2, L.quizItmOtmAtmO3, L.quizItmOtmAtmO4],
                    correctIndex: 2,
                    explanation: L.quizItmOtmAtmExp
                )
            ]
        )
    }

    // MARK: - Greeks Data

    var greeksInfo: [GreekInfo] {
        [
            GreekInfo(
                id: "delta",
                name: "Delta",
                symbol: "Δ",
                color: .blue,
                shortDescription: "How much option price changes with spot",
                fullDescription: "Delta measures the rate of change of option price with respect to changes in the underlying asset's price. It represents the expected change in option premium for every ₹1 move in the underlying.",
                impact: "A delta of 0.5 means if NIFTY moves ₹100, the option price moves approximately ₹50.",
                range: "Calls: 0 to 1\nPuts: -1 to 0",
                example: "NIFTY 25,000 CE with delta 0.50:\nIf NIFTY rises from 25,000 to 25,100 (+₹100)\nOption premium increases by ~₹50",
                tips: [
                    "ATM options have delta ~0.5",
                    "Deep ITM options have delta ~1",
                    "Deep OTM options have delta ~0",
                    "Use delta to estimate position exposure"
                ]
            ),
            GreekInfo(
                id: "gamma",
                name: "Gamma",
                symbol: "Γ",
                color: .orange,
                shortDescription: "Rate of change of Delta",
                fullDescription: "Gamma measures how fast Delta changes when the underlying price moves. It's the acceleration of option price change. High gamma means Delta will change quickly.",
                impact: "High gamma = Delta changes rapidly = Position becomes more sensitive to price moves.",
                range: "Always positive for long options\nHighest for ATM options near expiry",
                example: "If Delta is 0.50 and Gamma is 0.05:\nAfter NIFTY moves ₹100 up, new Delta ≈ 0.55",
                tips: [
                    "Gamma is highest for ATM options",
                    "Gamma increases near expiry",
                    "High gamma = High risk and reward",
                    "Option sellers fear gamma near expiry"
                ]
            ),
            GreekInfo(
                id: "theta",
                name: "Theta",
                symbol: "Θ",
                color: .red,
                shortDescription: "Time decay - daily loss in value",
                fullDescription: "Theta measures how much value an option loses each day due to the passage of time, assuming everything else remains constant. It's always negative for option buyers.",
                impact: "A theta of -5 means the option loses ₹5 per day (per lot) just from time passing.",
                range: "Always negative for long options\nAccelerates near expiry",
                example: "If Theta is -10 and lot size is 75:\nDaily loss = ₹10 × 75 = ₹750 per lot",
                tips: [
                    "Time decay accelerates in last week",
                    "ATM options have highest theta",
                    "Option sellers benefit from theta",
                    "Weekends don't pause theta"
                ]
            ),
            GreekInfo(
                id: "vega",
                name: "Vega",
                symbol: "ν",
                color: .purple,
                shortDescription: "Sensitivity to volatility changes",
                fullDescription: "Vega measures how much the option price changes for every 1% change in implied volatility. High vega means the option is very sensitive to IV changes.",
                impact: "A vega of 50 means if IV increases by 1%, option premium increases by ₹50.",
                range: "Always positive for long options\nHighest for ATM, long-dated options",
                example: "If Vega is 40 and IV rises from 15% to 17% (+2%):\nPremium increase ≈ 40 × 2 = ₹80",
                tips: [
                    "Buy options when IV is low",
                    "Sell options when IV is high",
                    "IV usually spikes during market falls",
                    "Events can cause IV crush"
                ]
            )
        ]
    }

    // MARK: - Strategies Data

    var strategies: [OptionStrategy] {
        [
            longCallStrategy,
            longPutStrategy,
            coveredCallStrategy,
            protectivePutStrategy,
            bullCallSpreadStrategy,
            bearPutSpreadStrategy,
            longStraddleStrategy,
            longStrangleStrategy,
            ironCondorStrategy,
            butterflySpreadStrategy
        ]
    }

    private var longCallStrategy: OptionStrategy {
        OptionStrategy(
            id: "long-call",
            name: "Long Call",
            type: .bullish,
            outlook: .bullish,
            riskLevel: .medium,
            legs: [
                EducationStrategyLeg(action: "Buy", optionType: "Call", strike: "ATM/OTM", quantity: 1)
            ],
            description: "The simplest bullish strategy. Buy a call option when you expect the market to rise significantly.",
            whenToUse: [
                "Strong bullish view on the market",
                "Expecting a big upward move",
                "Want limited risk with unlimited profit potential",
                "Before positive news/events"
            ],
            maxProfit: "Unlimited",
            maxLoss: "Premium paid",
            breakeven: "Strike + Premium",
            pros: [
                "Limited and known maximum loss",
                "Unlimited profit potential",
                "Lower capital requirement than buying stocks",
                "Leveraged exposure to upside"
            ],
            cons: [
                "Time decay works against you",
                "Need significant move to be profitable",
                "Can lose 100% of premium",
                "IV drop hurts position"
            ],
            example: EducationStrategyExample(
                spotPrice: 25000,
                strikes: [25000],
                premiums: [200],
                netCost: 200,
                scenarios: [
                    ScenarioResult(expiryPrice: 24500, profit: -200, description: "Lose full premium"),
                    ScenarioResult(expiryPrice: 25000, profit: -200, description: "Lose full premium"),
                    ScenarioResult(expiryPrice: 25200, profit: 0, description: "Breakeven"),
                    ScenarioResult(expiryPrice: 25500, profit: 300, description: "Profit ₹300")
                ]
            )
        )
    }

    private var longPutStrategy: OptionStrategy {
        OptionStrategy(
            id: "long-put",
            name: "Long Put",
            type: .bearish,
            outlook: .bearish,
            riskLevel: .medium,
            legs: [
                EducationStrategyLeg(action: "Buy", optionType: "Put", strike: "ATM/OTM", quantity: 1)
            ],
            description: "The simplest bearish strategy. Buy a put option when you expect the market to fall significantly.",
            whenToUse: [
                "Strong bearish view on the market",
                "Expecting a big downward move",
                "Want to hedge existing long positions",
                "Before negative news/events"
            ],
            maxProfit: "Strike - Premium (if stock goes to 0)",
            maxLoss: "Premium paid",
            breakeven: "Strike - Premium",
            pros: [
                "Limited and known maximum loss",
                "Profit from market falls",
                "Great hedging tool",
                "Don't need to short sell"
            ],
            cons: [
                "Time decay works against you",
                "Need significant move to profit",
                "Markets have upward bias long-term",
                "IV drop hurts position"
            ],
            example: EducationStrategyExample(
                spotPrice: 25000,
                strikes: [25000],
                premiums: [200],
                netCost: 200,
                scenarios: [
                    ScenarioResult(expiryPrice: 25500, profit: -200, description: "Lose full premium"),
                    ScenarioResult(expiryPrice: 25000, profit: -200, description: "Lose full premium"),
                    ScenarioResult(expiryPrice: 24800, profit: 0, description: "Breakeven"),
                    ScenarioResult(expiryPrice: 24500, profit: 300, description: "Profit ₹300")
                ]
            )
        )
    }

    private var coveredCallStrategy: OptionStrategy {
        OptionStrategy(
            id: "covered-call",
            name: "Covered Call",
            type: .neutral,
            outlook: .neutral,
            riskLevel: .low,
            legs: [
                EducationStrategyLeg(action: "Hold", optionType: "Stock/Futures", strike: "-", quantity: 1),
                EducationStrategyLeg(action: "Sell", optionType: "Call", strike: "OTM", quantity: 1)
            ],
            description: "Sell a call against shares you own to generate income. Best in sideways to mildly bullish markets.",
            whenToUse: [
                "Own the underlying and expect sideways movement",
                "Want to generate income from holdings",
                "Willing to sell if price rises significantly",
                "Neutral to slightly bullish view"
            ],
            maxProfit: "Premium + (Strike - Purchase price)",
            maxLoss: "Purchase price - Premium (if stock goes to 0)",
            breakeven: "Purchase price - Premium received",
            pros: [
                "Generate income from existing holdings",
                "Reduces cost basis",
                "Time decay works for you",
                "Lower risk than naked calls"
            ],
            cons: [
                "Caps upside profit potential",
                "Still exposed to downside risk",
                "May have to sell at strike if ITM",
                "Requires holding underlying"
            ],
            example: nil
        )
    }

    private var protectivePutStrategy: OptionStrategy {
        OptionStrategy(
            id: "protective-put",
            name: "Protective Put",
            type: .bullish,
            outlook: .bullish,
            riskLevel: .low,
            legs: [
                EducationStrategyLeg(action: "Hold", optionType: "Stock/Futures", strike: "-", quantity: 1),
                EducationStrategyLeg(action: "Buy", optionType: "Put", strike: "OTM", quantity: 1)
            ],
            description: "Buy a put to protect your long position. Like insurance for your portfolio.",
            whenToUse: [
                "Want to protect gains in existing position",
                "Worried about short-term downside",
                "Before uncertain events",
                "Long-term bullish but short-term cautious"
            ],
            maxProfit: "Unlimited (minus put premium)",
            maxLoss: "Stock price - Strike + Premium",
            breakeven: "Purchase price + Premium",
            pros: [
                "Protects against significant losses",
                "Maintains unlimited upside",
                "Peace of mind during volatility",
                "Known maximum loss"
            ],
            cons: [
                "Premium cost reduces profits",
                "Time decay if not used",
                "Need to roll if held long-term",
                "Can be expensive in high IV"
            ],
            example: nil
        )
    }

    private var bullCallSpreadStrategy: OptionStrategy {
        OptionStrategy(
            id: "bull-call-spread",
            name: "Bull Call Spread",
            type: .bullish,
            outlook: .bullish,
            riskLevel: .low,
            legs: [
                EducationStrategyLeg(action: "Buy", optionType: "Call", strike: "ATM/Slightly OTM", quantity: 1),
                EducationStrategyLeg(action: "Sell", optionType: "Call", strike: "Higher OTM", quantity: 1)
            ],
            description: "Buy a call and sell a higher strike call. Reduces cost but caps maximum profit.",
            whenToUse: [
                "Moderately bullish view",
                "Want to reduce cost of long call",
                "Expect price to rise but not dramatically",
                "High IV environment (selling reduces IV risk)"
            ],
            maxProfit: "Difference in strikes - Net premium",
            maxLoss: "Net premium paid",
            breakeven: "Lower strike + Net premium",
            pros: [
                "Lower cost than long call",
                "Reduced impact of IV drop",
                "Both risks and rewards are capped",
                "Less affected by time decay"
            ],
            cons: [
                "Limited profit potential",
                "Need directional move to profit",
                "Two-leg execution required",
                "May miss big moves"
            ],
            example: EducationStrategyExample(
                spotPrice: 25000,
                strikes: [25000, 25200],
                premiums: [200, 100],
                netCost: 100,
                scenarios: [
                    ScenarioResult(expiryPrice: 24800, profit: -100, description: "Max loss"),
                    ScenarioResult(expiryPrice: 25100, profit: 0, description: "Breakeven"),
                    ScenarioResult(expiryPrice: 25200, profit: 100, description: "Max profit"),
                    ScenarioResult(expiryPrice: 25500, profit: 100, description: "Max profit (capped)")
                ]
            )
        )
    }

    private var bearPutSpreadStrategy: OptionStrategy {
        OptionStrategy(
            id: "bear-put-spread",
            name: "Bear Put Spread",
            type: .bearish,
            outlook: .bearish,
            riskLevel: .low,
            legs: [
                EducationStrategyLeg(action: "Buy", optionType: "Put", strike: "ATM/Slightly OTM", quantity: 1),
                EducationStrategyLeg(action: "Sell", optionType: "Put", strike: "Lower OTM", quantity: 1)
            ],
            description: "Buy a put and sell a lower strike put. Reduces cost but caps maximum profit.",
            whenToUse: [
                "Moderately bearish view",
                "Want to reduce cost of long put",
                "Expect price to fall but not crash",
                "High IV environment"
            ],
            maxProfit: "Difference in strikes - Net premium",
            maxLoss: "Net premium paid",
            breakeven: "Higher strike - Net premium",
            pros: [
                "Lower cost than long put",
                "Reduced impact of IV drop",
                "Both risks and rewards are capped",
                "Less affected by time decay"
            ],
            cons: [
                "Limited profit potential",
                "Need directional move to profit",
                "Two-leg execution required",
                "May miss big crashes"
            ],
            example: nil
        )
    }

    private var longStraddleStrategy: OptionStrategy {
        OptionStrategy(
            id: "long-straddle",
            name: "Long Straddle",
            type: .volatile,
            outlook: .highVolatility,
            riskLevel: .high,
            legs: [
                EducationStrategyLeg(action: "Buy", optionType: "Call", strike: "ATM", quantity: 1),
                EducationStrategyLeg(action: "Buy", optionType: "Put", strike: "ATM", quantity: 1)
            ],
            description: "Buy both ATM call and put. Profit from big moves in either direction.",
            whenToUse: [
                "Expect big move but unsure of direction",
                "Before major events (earnings, budget, elections)",
                "IV is relatively low",
                "Expect volatility to increase"
            ],
            maxProfit: "Unlimited",
            maxLoss: "Total premium paid (both options)",
            breakeven: "Strike ± Total premium",
            pros: [
                "Profit from big moves either way",
                "No need to predict direction",
                "Unlimited profit potential",
                "Benefits from IV increase"
            ],
            cons: [
                "Expensive (paying for two options)",
                "Need very big move to profit",
                "Double time decay",
                "IV crush after events hurts"
            ],
            example: EducationStrategyExample(
                spotPrice: 25000,
                strikes: [25000, 25000],
                premiums: [200, 200],
                netCost: 400,
                scenarios: [
                    ScenarioResult(expiryPrice: 24400, profit: 200, description: "Profit from fall"),
                    ScenarioResult(expiryPrice: 24600, profit: 0, description: "Lower breakeven"),
                    ScenarioResult(expiryPrice: 25000, profit: -400, description: "Max loss"),
                    ScenarioResult(expiryPrice: 25400, profit: 0, description: "Upper breakeven"),
                    ScenarioResult(expiryPrice: 25600, profit: 200, description: "Profit from rise")
                ]
            )
        )
    }

    private var longStrangleStrategy: OptionStrategy {
        OptionStrategy(
            id: "long-strangle",
            name: "Long Strangle",
            type: .volatile,
            outlook: .highVolatility,
            riskLevel: .high,
            legs: [
                EducationStrategyLeg(action: "Buy", optionType: "Call", strike: "OTM", quantity: 1),
                EducationStrategyLeg(action: "Buy", optionType: "Put", strike: "OTM", quantity: 1)
            ],
            description: "Buy OTM call and OTM put. Cheaper than straddle but needs bigger move.",
            whenToUse: [
                "Expect very big move but unsure of direction",
                "Want cheaper alternative to straddle",
                "Before major market events",
                "IV is low"
            ],
            maxProfit: "Unlimited",
            maxLoss: "Total premium paid",
            breakeven: "Call strike + Premium OR Put strike - Premium",
            pros: [
                "Cheaper than straddle",
                "Profit from big moves either way",
                "Unlimited profit potential",
                "Lower premium at risk"
            ],
            cons: [
                "Need even bigger move than straddle",
                "Wider breakeven range",
                "Both options can expire worthless",
                "IV crush hurts badly"
            ],
            example: EducationStrategyExample(
                spotPrice: 25000,
                strikes: [25200, 24800],
                premiums: [100, 100],
                netCost: 200,
                scenarios: [
                    ScenarioResult(expiryPrice: 24400, profit: 200, description: "Profit from big fall"),
                    ScenarioResult(expiryPrice: 24600, profit: 0, description: "Lower breakeven"),
                    ScenarioResult(expiryPrice: 25000, profit: -200, description: "Max loss"),
                    ScenarioResult(expiryPrice: 25400, profit: 0, description: "Upper breakeven"),
                    ScenarioResult(expiryPrice: 25600, profit: 200, description: "Profit from big rise")
                ]
            )
        )
    }

    private var ironCondorStrategy: OptionStrategy {
        OptionStrategy(
            id: "iron-condor",
            name: "Iron Condor",
            type: .neutral,
            outlook: .lowVolatility,
            riskLevel: .medium,
            legs: [
                EducationStrategyLeg(action: "Sell", optionType: "Put", strike: "OTM", quantity: 1),
                EducationStrategyLeg(action: "Buy", optionType: "Put", strike: "Lower OTM", quantity: 1),
                EducationStrategyLeg(action: "Sell", optionType: "Call", strike: "OTM", quantity: 1),
                EducationStrategyLeg(action: "Buy", optionType: "Call", strike: "Higher OTM", quantity: 1)
            ],
            description: "Sell OTM put spread and call spread. Profit if price stays in a range.",
            whenToUse: [
                "Expect low volatility / sideways market",
                "IV is high (options are expensive)",
                "After major events (IV crush expected)",
                "Want to collect premium with defined risk"
            ],
            maxProfit: "Net premium received",
            maxLoss: "Width of spread - Premium received",
            breakeven: "Short strikes ± Premium received",
            pros: [
                "High probability of profit",
                "Defined and limited risk",
                "Benefits from time decay",
                "Benefits from IV drop"
            ],
            cons: [
                "Limited profit potential",
                "Can lose more than you make",
                "Requires range-bound market",
                "4 legs = higher transaction costs"
            ],
            example: EducationStrategyExample(
                spotPrice: 25000,
                strikes: [24600, 24800, 25200, 25400],
                premiums: [-50, 80, 80, -50],
                netCost: -60,
                scenarios: [
                    ScenarioResult(expiryPrice: 24500, profit: -140, description: "Max loss on downside"),
                    ScenarioResult(expiryPrice: 24740, profit: 0, description: "Lower breakeven"),
                    ScenarioResult(expiryPrice: 25000, profit: 60, description: "Max profit"),
                    ScenarioResult(expiryPrice: 25260, profit: 0, description: "Upper breakeven"),
                    ScenarioResult(expiryPrice: 25500, profit: -140, description: "Max loss on upside")
                ]
            )
        )
    }

    private var butterflySpreadStrategy: OptionStrategy {
        OptionStrategy(
            id: "butterfly-spread",
            name: "Butterfly Spread",
            type: .neutral,
            outlook: .lowVolatility,
            riskLevel: .low,
            legs: [
                EducationStrategyLeg(action: "Buy", optionType: "Call", strike: "Lower", quantity: 1),
                EducationStrategyLeg(action: "Sell", optionType: "Call", strike: "Middle (ATM)", quantity: 2),
                EducationStrategyLeg(action: "Buy", optionType: "Call", strike: "Higher", quantity: 1)
            ],
            description: "Buy 1 lower call, sell 2 ATM calls, buy 1 higher call. Maximum profit at middle strike.",
            whenToUse: [
                "Expect very low volatility",
                "Price expected to stay near current level",
                "Want cheap directional bet with high reward",
                "Near expiry for maximum effect"
            ],
            maxProfit: "Middle strike - Lower strike - Net premium",
            maxLoss: "Net premium paid",
            breakeven: "Lower strike + Premium OR Upper strike - Premium",
            pros: [
                "Very low cost to enter",
                "High reward to risk ratio",
                "Limited and defined risk",
                "Good for pinning near strike"
            ],
            cons: [
                "Very low probability of max profit",
                "Need price to stay in tight range",
                "3 legs = execution complexity",
                "Profits erode quickly outside range"
            ],
            example: nil
        )
    }
}
