import Foundation

// MARK: - Localized Strings Protocol

protocol LocalizedStrings {

    // MARK: - Tab Bar
    var tabOptionChain: String { get }
    var tabCalculator: String { get }
    var tabAIInsights: String { get }
    var tabCharts: String { get }
    var tabQuiz: String { get }
    var tabSettings: String { get }

    // MARK: - Splash Screen
    var splashTitle: String { get }
    var splashSubtitle: String { get }

    // MARK: - Onboarding
    var onboardingSkip: String { get }
    var onboardingNext: String { get }
    var onboardingGetStarted: String { get }

    var onboarding1Title: String { get }
    var onboarding1Subtitle: String { get }
    var onboarding1Feature1: String { get }
    var onboarding1Feature2: String { get }
    var onboarding1Feature3: String { get }
    var onboarding1Feature4: String { get }

    var onboarding2Title: String { get }
    var onboarding2Subtitle: String { get }
    var onboarding2Feature1: String { get }
    var onboarding2Feature2: String { get }
    var onboarding2Feature3: String { get }
    var onboarding2Feature4: String { get }

    var onboarding3Title: String { get }
    var onboarding3Subtitle: String { get }
    var onboarding3Feature1: String { get }
    var onboarding3Feature2: String { get }
    var onboarding3Feature3: String { get }
    var onboarding3Feature4: String { get }

    var onboarding4Title: String { get }
    var onboarding4Subtitle: String { get }
    var onboarding4Feature1: String { get }
    var onboarding4Feature2: String { get }
    var onboarding4Feature3: String { get }
    var onboarding4Feature4: String { get }

    // MARK: - Calculator
    var calculatorTitle: String { get }
    var calculatorParameters: String { get }
    var calculatorSpotPrice: String { get }
    var calculatorStrikePrice: String { get }
    var calculatorDaysToExpiry: String { get }
    var calculatorIVPercent: String { get }
    var calculatorRiskFreeRate: String { get }
    var calculatorOptionType: String { get }
    var calculatorCall: String { get }
    var calculatorPut: String { get }
    var calculatorCalculate: String { get }
    var calculatorReset: String { get }
    var calculatorResults: String { get }
    var calculatorTheoreticalPrice: String { get }
    var calculatorIntrinsicValue: String { get }
    var calculatorTimeValue: String { get }
    var calculatorBreakeven: String { get }
    var calculatorMoneyness: String { get }
    var calculatorTheGreeks: String { get }
    var calculatorQuickReference: String { get }
    var calculatorRiskDisclaimer: String { get }
    var calculatorRiskDisclaimerText: String { get }
    var calculatorIVEducationTitle: String { get }
    var calculatorIVEducationText: String { get }
    var calculatorLearnAboutIV: String { get }
    var calculatorWhatIsIV: String { get }
    var calculatorWhatIsIVText: String { get }
    var calculatorIVRanges: String { get }
    var calculatorLowIV: String { get }
    var calculatorLowIVDesc: String { get }
    var calculatorModerateIV: String { get }
    var calculatorModerateIVDesc: String { get }
    var calculatorHighIV: String { get }
    var calculatorHighIVDesc: String { get }
    var calculatorVeryHighIV: String { get }
    var calculatorVeryHighIVDesc: String { get }
    var calculatorIVImpact: String { get }
    var calculatorIVImpactText: String { get }
    var calculatorGotIt: String { get }
    var calculatorEnterSpotPrice: String { get }
    var calculatorEnterStrikePrice: String { get }
    var calculatorEnterDays: String { get }
    var calculatorEnterIV: String { get }
    var calculatorEnterRate: String { get }

    // MARK: - Greeks
    var greeksDelta: String { get }
    var greeksGamma: String { get }
    var greeksTheta: String { get }
    var greeksVega: String { get }
    var greeksRho: String { get }
    var greeksDeltaDesc: String { get }
    var greeksGammaDesc: String { get }
    var greeksThetaDesc: String { get }
    var greeksVegaDesc: String { get }
    var greeksRhoDesc: String { get }
    var greeksDeltaInterpDeepITM: String { get }
    var greeksDeltaInterpATM: String { get }
    var greeksDeltaInterpDeepOTM: String { get }
    var greeksDeltaInterpModerate: String { get }
    var greeksThetaInterpHigh: String { get }
    var greeksThetaInterpModerate: String { get }
    var greeksThetaInterpLow: String { get }
    var greeksVegaInterpHigh: String { get }
    var greeksVegaInterpModerate: String { get }
    var greeksVegaInterpLow: String { get }
    var greeksQuickRefDeltaCall: String { get }
    var greeksQuickRefDeltaPut: String { get }
    var greeksQuickRefGamma: String { get }
    var greeksQuickRefTheta: String { get }
    var greeksQuickRefVega: String { get }
    var greeksNearExpiryWarning: String { get }
    var greeksNearExpiryText: String { get }
    var greeksInterpretation: String { get }
    var greeksPerDay: String { get }
    var greeksPer1PercentIV: String { get }
    var greeksPer1PercentRate: String { get }

    // MARK: - Option Chain
    var optionChainTitle: String { get }
    var optionChainExpiry: String { get }
    var optionChainSpotPrice: String { get }
    var optionChainChange: String { get }
    var optionChainCalls: String { get }
    var optionChainPuts: String { get }
    var optionChainStrike: String { get }
    var optionChainLTP: String { get }
    var optionChainOI: String { get }
    var optionChainVolume: String { get }
    var optionChainIV: String { get }
    var optionChainBidAsk: String { get }
    var optionChainDayHigh: String { get }
    var optionChainDayLow: String { get }
    var optionChainATM: String { get }
    var optionChainITM: String { get }
    var optionChainOTM: String { get }
    var optionChainPCR: String { get }
    var optionChainMaxPain: String { get }
    var optionChainTotalCallOI: String { get }
    var optionChainTotalPutOI: String { get }
    var optionChainDemoDataNote: String { get }
    var optionChainNoData: String { get }
    var optionChainLoading: String { get }
    var optionChainRefresh: String { get }
    var optionChainLastUpdated: String { get }
    var optionChainStrikesNearATM: String { get }
    var optionChainAllStrikes: String { get }
    var optionChainSelectExpiry: String { get }

    // Day names
    var dayMon: String { get }
    var dayTue: String { get }
    var dayWed: String { get }
    var dayThu: String { get }
    var dayFri: String { get }
    var daySat: String { get }
    var daySun: String { get }

    // MARK: - Settings
    var settingsTitle: String { get }
    var settingsLanguage: String { get }
    var settingsLanguageSubtitle: String { get }
    var settingsTheme: String { get }
    var settingsThemeSubtitle: String { get }
    var settingsDataSource: String { get }
    var settingsDataSourceSubtitle: String { get }
    var settingsAbout: String { get }
    var settingsVersion: String { get }
    var settingsRateApp: String { get }
    var settingsShareApp: String { get }
    var settingsPrivacyPolicy: String { get }
    var settingsDisclaimer: String { get }
    var settingsDisclaimerText: String { get }
    var settingsDisplay: String { get }
    var settingsGeneral: String { get }
    var settingsDeveloper: String { get }
    var settingsDeveloperName: String { get }
    var settingsAppDescription: String { get }
    var settingsAllRightsReserved: String { get }

    // MARK: - AI Insights
    var aiInsightsTitle: String { get }
    var aiInsightsSelectIndex: String { get }
    var aiInsightsAnalyze: String { get }
    var aiInsightsAnalyzing: String { get }
    var aiInsightsUpdated: String { get }
    var aiInsightsNotAnalyzed: String { get }
    var aiInsightsNoData: String { get }
    var aiInsightsCallsTab: String { get }
    var aiInsightsPutsTab: String { get }
    var aiInsightsMarketTab: String { get }
    var aiInsightsConfidence: String { get }
    var aiInsightsMarketBias: String { get }
    var aiInsightsPrediction: String { get }
    var aiInsightsDirection: String { get }
    var aiInsightsEntry: String { get }
    var aiInsightsTarget: String { get }
    var aiInsightsStopLoss: String { get }
    var aiInsightsExpectedMove: String { get }
    var aiInsightsRiskReward: String { get }
    var aiInsightsRationale: String { get }
    var aiInsightsKeyLevels: String { get }
    var aiInsightsSupport: String { get }
    var aiInsightsResistance: String { get }
    var aiInsightsAnalyzeStrikes: String { get }
    var aiInsightsSelectStrike: String { get }
    var aiInsightsStrikeAnalysis: String { get }
    var aiInsightsOverallSentiment: String { get }
    var aiInsightsOIAnalysis: String { get }
    var aiInsightsIVAnalysis: String { get }
    var aiInsightsRecommendation: String { get }
    var aiInsightsBullish: String { get }
    var aiInsightsBearish: String { get }
    var aiInsightsNeutral: String { get }
    var aiInsightsStrongBuy: String { get }
    var aiInsightsBuy: String { get }
    var aiInsightsHold: String { get }
    var aiInsightsSell: String { get }
    var aiInsightsStrongSell: String { get }
    var aiInsightsHigh: String { get }
    var aiInsightsMedium: String { get }
    var aiInsightsLow: String { get }
    var aiInsightsLong: String { get }
    var aiInsightsShort: String { get }
    var aiInsightsAvoid: String { get }

    // MARK: - Charts
    var chartsIndexChart: String { get }
    var chartsATMOptions: String { get }
    var chartsMarketOverview: String { get }
    var chartsPutCallRatio: String { get }
    var chartsMaxPain: String { get }
    var chartsTotalCallOI: String { get }
    var chartsTotalPutOI: String { get }
    var chartsBullish: String { get }
    var chartsBearish: String { get }
    var chartsAboveSpot: String { get }
    var chartsBelowSpot: String { get }
    var chartsLoadingChart: String { get }
    var chartsLoadingOption: String { get }
    var chartsPrice: String { get }
    var chartsHigh: String { get }
    var chartsLow: String { get }
    var chartsAvgOI: String { get }
    var chartsAvgIV: String { get }

    // MARK: - Upstox Login
    var upstoxConnectTitle: String { get }
    var upstoxConnectSubtitle: String { get }
    var upstoxLiveData: String { get }
    var upstoxLiveDataDesc: String { get }
    var upstoxAccurateGreeks: String { get }
    var upstoxAccurateGreeksDesc: String { get }
    var upstoxAutoRefresh: String { get }
    var upstoxAutoRefreshDesc: String { get }
    var upstoxConnecting: String { get }
    var upstoxLoginButton: String { get }
    var upstoxContinueDemo: String { get }
    var upstoxConnected: String { get }
    var upstoxNotConnected: String { get }
    var upstoxDataProviders: String { get }
    var upstoxRealTimeStream: String { get }
    var upstoxRefreshData: String { get }
    var upstoxDisconnect: String { get }
    var upstoxUseDemoData: String { get }
    var upstoxDataSource: String { get }
    var upstoxDemoData: String { get }
    var upstoxLive: String { get }

    // MARK: - Common
    var commonDone: String { get }
    var commonCancel: String { get }
    var commonClose: String { get }
    var commonError: String { get }
    var commonRetry: String { get }
    var commonOK: String { get }
    var commonYes: String { get }
    var commonNo: String { get }
    var commonSave: String { get }
    var commonDelete: String { get }
    var commonEdit: String { get }
    var commonShare: String { get }
    var commonCopy: String { get }
    var commonSearch: String { get }

    // MARK: - Moneyness
    var moneynessITM: String { get }
    var moneynessATM: String { get }
    var moneynessOTM: String { get }
    var moneynessDeepITM: String { get }
    var moneynessDeepOTM: String { get }

    // MARK: - Theme Names
    var themeClassicGreen: String { get }
    var themeOceanBlue: String { get }
    var themeMidnightPurple: String { get }
    var themeSunsetOrange: String { get }
    var themeCyberpunk: String { get }
    var themeClassicGreenDesc: String { get }
    var themeOceanBlueDesc: String { get }
    var themeMidnightPurpleDesc: String { get }
    var themeSunsetOrangeDesc: String { get }
    var themeCyberpunkDesc: String { get }

    // MARK: - Quiz Questions (Protocol Requirements)
    var quizTitleWhatAreOptions: String { get }
    var quizTitleCallOptions: String { get }
    var quizTitlePutOptions: String { get }
    var quizTitleItmOtmAtm: String { get }
    var quizWhatAreOptionsQ: String { get }
    var quizWhatAreOptionsO1: String { get }
    var quizWhatAreOptionsO2: String { get }
    var quizWhatAreOptionsO3: String { get }
    var quizWhatAreOptionsO4: String { get }
    var quizWhatAreOptionsExp: String { get }
    var quizCallOptionsQ: String { get }
    var quizCallOptionsO1: String { get }
    var quizCallOptionsO2: String { get }
    var quizCallOptionsO3: String { get }
    var quizCallOptionsO4: String { get }
    var quizCallOptionsExp: String { get }
    var quizPutOptionsQ: String { get }
    var quizPutOptionsO1: String { get }
    var quizPutOptionsO2: String { get }
    var quizPutOptionsO3: String { get }
    var quizPutOptionsO4: String { get }
    var quizPutOptionsExp: String { get }
    var quizItmOtmAtmQ: String { get }
    var quizItmOtmAtmO1: String { get }
    var quizItmOtmAtmO2: String { get }
    var quizItmOtmAtmO3: String { get }
    var quizItmOtmAtmO4: String { get }
    var quizItmOtmAtmExp: String { get }

    // MARK: - Education Quiz UI
    var educationQuiz: String { get }
    var educationTestKnowledge: String { get }
    var educationHasQuiz: String { get }
    var educationAvailableQuizzes: String { get }
    var educationCompleted: String { get }
    var educationAvailable: String { get }
    var educationAvgScore: String { get }
    var educationCorrect: String { get }
}

// MARK: - Protocol Extension Defaults (additional strings, English fallback)
// Translation files can override these for full localization.

extension LocalizedStrings {

    // Tab Bar (new)
    var tabQuiz: String { "Quiz" }

    // Settings — additional section headers & labels
    var settingsAccount: String { "ACCOUNT" }
    var settingsDataRefresh: String { "DATA REFRESH" }
    var settingsSpotPriceInterval: String { "Spot Price Interval" }
    var settingsOptionChainRefresh: String { "Option Chain Refresh" }
    var settingsDefaultIndex: String { "DEFAULT INDEX" }
    var settingsPreferences: String { "PREFERENCES" }
    var settingsHapticFeedback: String { "Haptic Feedback" }
    var settingsBuild: String { "Build" }
    var settingsLogoutUpstox: String { "Logout from Upstox" }
    var settingsImportantNotice: String { "Important Notice" }
    var settingsDisclaimerBullet1: String { "This app provides theoretical option prices using the Black-Scholes model for educational and analytical purposes only." }
    var settingsDisclaimerBullet2: String { "It does not constitute financial or investment advice." }
    var settingsDisclaimerBullet3: String { "Actual market prices may differ significantly due to volatility changes, liquidity, bid-ask spreads, and market gaps." }
    var settingsDisclaimerBullet4: String { "Always consult a SEBI-registered advisor before trading. Trade at your own risk." }

    // Calculator — additional labels
    var calculatorAdvancedAnalysis: String { "Advanced Analysis" }
    var calculatorGreeksAndRisk: String { "Greeks & Risk" }
    var calculatorHide: String { "Hide" }
    var calculatorShow: String { "Show" }
    var calculatorPriceCalculator: String { "Price Calculator" }
    var calculatorSimple: String { "Simple" }
    var calculatorTargetDescription: String { "Enter where you think Nifty will go, and we'll calculate your option price" }
    var calculatorIfNiftyGoesTo: String { "If Nifty goes to" }
    var calculatorIfNiftyFallsTo: String { "If Nifty falls to" }
    var calculatorYourTarget: String { "Your target price" }
    var calculatorYourStopLoss: String { "Your stop-loss price" }
    var calculatorYourOptionWillBe: String { "YOUR OPTION WILL BE" }
    var calculatorOptionPremium: String { "Option Premium" }
    var calculatorMaxProfit: String { "Max Profit" }
    var calculatorMaxLoss: String { "Max Loss" }
    var calculatorEnterTargetSL: String { "Enter target and stop-loss above" }
    var calculatorDaysLeft: String { "Days Left" }
    var calculatorTheoreticalNote: String { "Theoretical price assumes constant IV and no market gaps" }
    var calculatorProbabilityRisk: String { "Probability & Risk" }
    var calculatorPriceDistribution: String { "Price Distribution" }
    var calculatorRiskAssessment: String { "Risk Assessment" }
    var calculatorThetaRisk: String { "Theta Risk" }
    var calculatorVegaRisk: String { "Vega Risk" }
    var calculatorPositionImpact: String { "Position Impact" }
    var calculatorNiftyMove100: String { "100 pt Nifty move" }
    var calculatorDailyThetaDecay: String { "Daily Theta decay" }
    var calculatorIV1PercentIncrease: String { "1% IV increase" }
    var calculatorBasedOnDelta: String { "Based on current Delta" }
    var calculatorTimeValueErosion: String { "Time value erosion" }
    var calculatorVolatilitySensitivity: String { "Volatility sensitivity" }

    // Greeks — additional labels for insight cards
    var greeksInsight: String { "Insight" }
    var greeksHighDelta: String { "High delta - behaves almost like futures" }
    var greeksLowDelta: String { "Low delta - cheap but risky" }
    var greeksModerateDelta: String { "Moderate delta - balanced risk/reward" }
    var greeksHighGamma: String { "High gamma - delta changes rapidly near ATM" }
    var greeksLowGamma: String { "Low gamma - stable delta" }
    var greeksHighTheta: String { "High theta - significant daily decay" }
    var greeksLowTheta: String { "Low theta - time decay is manageable" }
    var greeksHighVega: String { "High vega - very sensitive to IV changes" }
    var greeksLowVega: String { "Low vega - IV changes have less impact" }

    // Option Chain — additional labels
    var optionChainToday: String { "Today" }
    var optionChainViewChart: String { "View Chart" }
    var optionChainSelectIndex: String { "Select Index" }
    var optionChainIndexInfo: String { "Index Information" }
    var optionChainLotSize: String { "Lot Size" }
    var optionChainStrikeInterval: String { "Strike Interval" }
    var optionChainExpiryDay: String { "Expiry Day" }
    var optionChainExchange: String { "Exchange" }
    var optionChainNearATM: String { "Near ATM" }
    var optionChainNSEIndices: String { "NSE INDICES" }
    var optionChainBSEIndices: String { "BSE INDICES" }
    var optionChainDemoConnect: String { "Demo data — connect Upstox for live prices" }

    // Onboarding — 5th page
    var onboarding5Title: String { "You're Ready!" }
    var onboarding5Subtitle: String { "Start your smart options trading journey" }
    var onboarding5Feature1: String { "All features unlocked" }
    var onboarding5Feature2: String { "Connect your broker anytime" }
    var onboarding5Feature3: String { "Happy trading!" }

    // IV Education — additional detail strings
    var calculatorHighIVExpensive: String { "High IV = Expensive Options" }
    var calculatorHighIVExpensiveDesc: String { "When IV is high (before events), options are expensive. If IV drops after the event, your option loses value even if the market moves in your direction." }
    var calculatorLowIVCheap: String { "Low IV = Cheaper Options" }
    var calculatorLowIVCheapDesc: String { "When IV is low, options are relatively cheap. An increase in IV can boost your option's value." }
    var calculatorEventDrivenIV: String { "Event-Driven IV Changes" }
    var calculatorEventDrivenIVDesc: String { "Budget day, RBI policy, election results, and quarterly earnings cause IV spikes before the event and IV crush after." }

    // Market bias labels
    var marketBiasStrongBullish: String { "Strong Bullish" }
    var marketBiasStrongBearish: String { "Strong Bearish" }

    // Actual theme names (override protocol's placeholder names)
    var themeProfessionalBlue: String { "Professional Blue" }
    var themeProfessionalBlueDesc: String { "Clean blue theme used by top brokers" }
    var themeDhanPremium: String { "Dhan Premium" }
    var themeDhanPremiumDesc: String { "Premium navy with glass-morphism" }
    var themeClassicGreenActualDesc: String { "Robinhood-inspired neon green theme" }

    // Appearance (Light/Dark Mode)
    var settingsAppearance: String { "APPEARANCE" }
    var settingsAppearanceMode: String { "Appearance Mode" }
    var themeSystemDefault: String { "System" }
    var themeLight: String { "Light" }
    var themeDark: String { "Dark" }

    // MARK: - Paper Trading
    var tabPaperTrade: String { "Paper Trade" }
    var paperTradeDashboard: String { "Dashboard" }
    var paperTradePositions: String { "Positions" }
    var paperTradeHistory: String { "History" }
    var paperTradePerformance: String { "Performance" }
    var paperTradePortfolioValue: String { "Portfolio Value" }
    var paperTradeAvailableMargin: String { "Available" }
    var paperTradeTotalPnL: String { "Total P&L" }
    var paperTradeUnrealizedPnL: String { "Unrealized P&L" }
    var paperTradeRealizedPnL: String { "Realized P&L" }
    var paperTradeWinRate: String { "Win Rate" }
    var paperTradeMaxDrawdown: String { "Max Drawdown" }
    var paperTradeBestTrade: String { "Best Trade" }
    var paperTradeWorstTrade: String { "Worst Trade" }
    var paperTradeProfitFactor: String { "Profit Factor" }
    var paperTradeAvgHoldingDays: String { "Avg Hold" }
    var paperTradeSquareOff: String { "Square Off" }
    var paperTradeExecute: String { "Execute" }
    var paperTradeBuy: String { "BUY" }
    var paperTradeSell: String { "SELL" }
    var paperTradeQuantity: String { "Quantity (Lots)" }
    var paperTradeResetPortfolio: String { "Reset Portfolio" }
    var paperTradeResetConfirmation: String { "Reset Portfolio?" }
    var paperTradeNoPositions: String { "No Open Positions" }
    var paperTradeNoHistory: String { "No Trade History" }
    var paperTradeStartTrading: String { "Start paper trading from the Option Chain" }
    var paperTradeStopLoss: String { "Stop Loss" }
    var paperTradeTarget: String { "Target" }
    var paperTradeStopLossTarget: String { "Stop Loss & Target" }
    var paperTradeLoginRequired: String { "Login Required" }
    var paperTradeLoginDescription: String { "Sign in to access paper trading and track your portfolio" }
    var paperTradeFeature1: String { "Practice trading with virtual ₹10L" }
    var paperTradeFeature2: String { "Track your positions & P&L" }
    var paperTradeFeature3: String { "View performance analytics" }

    // MARK: - Education
    var educationTitle: String { "Learn Options" }
    var educationSubtitle: String { "Basics, Greeks & Strategies" }
    var educationBasics: String { "Basics" }
    var educationGreeks: String { "Greeks" }
    var educationStrategies: String { "Strategies" }
    var educationHasQuiz: String { "Has Quiz" }
    var educationKeyPoints: String { "Key Points" }
    var educationQuizIncluded: String { "Quiz Included" }
    var educationTakeQuiz: String { "Take Quiz" }
    var educationRetakeQuiz: String { "Retake Quiz" }
    var educationQuizComplete: String { "Quiz Complete!" }
    var educationScore: String { "Score" }
    var educationQuiz: String { "Quiz" }
    var educationQuestion: String { "Question" }
    var educationNextQuestion: String { "Next Question" }
    var educationFinish: String { "Finish" }
    var educationCheckAnswer: String { "Check Answer" }
    var educationCorrect: String { "Correct!" }
    var educationIncorrect: String { "Incorrect" }
    var educationClose: String { "Close" }
    var educationWhatItMeasures: String { "What It Measures" }
    var educationImpact: String { "Impact" }
    var educationRange: String { "Range" }
    var educationExample: String { "Example" }
    var educationProTips: String { "Pro Tips" }
    var educationStrategyLegs: String { "Strategy Legs" }
    var educationRiskReward: String { "Risk & Reward" }
    var educationMaxProfit: String { "Max Profit" }
    var educationMaxLoss: String { "Max Loss" }
    var educationBreakeven: String { "Breakeven" }
    var educationWhenToUse: String { "When To Use" }
    var educationPros: String { "Pros" }
    var educationCons: String { "Cons" }
    var educationPayoffExample: String { "Payoff Example" }
    var educationSpotPrice: String { "Spot Price" }
    var educationNetCost: String { "Net Cost" }
    var educationExpiryAt: String { "Expiry at" }

    // MARK: - Quiz Questions

    // Quiz Titles
    var quizTitleWhatAreOptions: String { "What are Options?" }
    var quizTitleCallOptions: String { "Call Options (CE)" }
    var quizTitlePutOptions: String { "Put Options (PE)" }
    var quizTitleItmOtmAtm: String { "ITM, OTM & ATM" }

    // Quiz 1: What are Options?
    var quizWhatAreOptionsQ: String { "What does an option give you?" }
    var quizWhatAreOptionsO1: String { "Obligation to buy/sell" }
    var quizWhatAreOptionsO2: String { "Right to buy/sell" }
    var quizWhatAreOptionsO3: String { "Guaranteed profit" }
    var quizWhatAreOptionsO4: String { "Free shares" }
    var quizWhatAreOptionsExp: String { "An option gives you the RIGHT (not obligation) to buy or sell an asset at a predetermined price." }

    // Quiz 2: Call Options (CE)
    var quizCallOptionsQ: String { "When should you buy a Call option?" }
    var quizCallOptionsO1: String { "When you expect price to fall" }
    var quizCallOptionsO2: String { "When you expect price to rise" }
    var quizCallOptionsO3: String { "When you want to sell shares" }
    var quizCallOptionsO4: String { "When IV is very high" }
    var quizCallOptionsExp: String { "You buy Call options when you expect the underlying price to rise. Calls profit from upward price movement." }

    // Quiz 3: Put Options (PE)
    var quizPutOptionsQ: String { "What is the maximum loss when buying a Put?" }
    var quizPutOptionsO1: String { "Unlimited" }
    var quizPutOptionsO2: String { "Strike price" }
    var quizPutOptionsO3: String { "Premium paid" }
    var quizPutOptionsO4: String { "Zero" }
    var quizPutOptionsExp: String { "When buying any option (call or put), your maximum loss is limited to the premium you paid upfront." }

    // Quiz 4: ITM, OTM & ATM
    var quizItmOtmAtmQ: String { "If NIFTY is at 25,000, which call option is ITM?" }
    var quizItmOtmAtmO1: String { "25,200 CE" }
    var quizItmOtmAtmO2: String { "25,000 CE" }
    var quizItmOtmAtmO3: String { "24,800 CE" }
    var quizItmOtmAtmO4: String { "25,500 CE" }
    var quizItmOtmAtmExp: String { "A call is ITM when Spot > Strike. Since NIFTY is at 25,000, the 24,800 CE is ITM because 25,000 > 24,800." }
}
