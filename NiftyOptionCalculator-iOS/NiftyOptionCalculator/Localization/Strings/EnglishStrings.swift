import Foundation

struct EnglishStrings: LocalizedStrings {

    // MARK: - Tab Bar
    var tabOptionChain: String { "Option Chain" }
    var tabCalculator: String { "Calculator" }
    var tabAIInsights: String { "AI Insights" }
    var tabCharts: String { "Charts" }
    var tabQuiz: String { "Quiz" }
    var tabSettings: String { "Settings" }

    // MARK: - Splash
    var splashTitle: String { "OPTIONS TRADING" }
    var splashSubtitle: String { "Smart trading starts here" }

    // MARK: - Onboarding
    var onboardingSkip: String { "Skip" }
    var onboardingNext: String { "Continue" }
    var onboardingGetStarted: String { "Get Started" }

    var onboarding1Title: String { "Welcome to\nOptix" }
    var onboarding1Subtitle: String { "Your premium companion for smarter options trading" }
    var onboarding1Feature1: String { "Real-time NIFTY & BANKNIFTY tracking" }
    var onboarding1Feature2: String { "Lightning-fast option calculations" }
    var onboarding1Feature3: String { "Built for Indian markets" }
    var onboarding1Feature4: String { "" }

    var onboarding2Title: String { "Live Option Chain" }
    var onboarding2Subtitle: String { "Real-time data from top Indian brokers" }
    var onboarding2Feature1: String { "Live prices from Upstox, Zerodha & more" }
    var onboarding2Feature2: String { "NIFTY, BANKNIFTY, FINNIFTY, SENSEX" }
    var onboarding2Feature3: String { "Switch expiries with one tap" }
    var onboarding2Feature4: String { "" }

    var onboarding3Title: String { "Smart Calculator\n& Greeks" }
    var onboarding3Subtitle: String { "Black-Scholes pricing made simple" }
    var onboarding3Feature1: String { "Theoretical option pricing" }
    var onboarding3Feature2: String { "Delta, Gamma, Theta & Vega" }
    var onboarding3Feature3: String { "Implied volatility calculator" }
    var onboarding3Feature4: String { "" }

    var onboarding4Title: String { "AI-Powered\nInsights" }
    var onboarding4Subtitle: String { "Machine learning meets options trading" }
    var onboarding4Feature1: String { "Smart strike selection" }
    var onboarding4Feature2: String { "AI-driven market analysis" }
    var onboarding4Feature3: String { "Predicted targets & stop-loss" }
    var onboarding4Feature4: String { "" }

    // MARK: - Calculator
    var calculatorTitle: String { "Option Calculator" }
    var calculatorParameters: String { "Parameters" }
    var calculatorSpotPrice: String { "Spot Price" }
    var calculatorStrikePrice: String { "Strike Price" }
    var calculatorDaysToExpiry: String { "Days to Expiry" }
    var calculatorIVPercent: String { "IV (%)" }
    var calculatorRiskFreeRate: String { "Risk Free Rate" }
    var calculatorOptionType: String { "Option Type" }
    var calculatorCall: String { "Call" }
    var calculatorPut: String { "Put" }
    var calculatorCalculate: String { "Calculate" }
    var calculatorReset: String { "Reset" }
    var calculatorResults: String { "Results" }
    var calculatorTheoreticalPrice: String { "Theoretical Price" }
    var calculatorIntrinsicValue: String { "Intrinsic" }
    var calculatorTimeValue: String { "Time Value" }
    var calculatorBreakeven: String { "Breakeven" }
    var calculatorMoneyness: String { "Moneyness" }
    var calculatorTheGreeks: String { "Option Greeks" }
    var calculatorQuickReference: String { "Quick Reference" }
    var calculatorRiskDisclaimer: String { "Important Notice" }
    var calculatorRiskDisclaimerText: String { "Theoretical prices. Actual market prices may differ due to IV changes, liquidity, and market gaps. Not investment advice." }
    var calculatorIVEducationTitle: String { "Implied Volatility (IV)" }
    var calculatorIVEducationText: String { "The most powerful variable" }
    var calculatorLearnAboutIV: String { "Learn About IV" }
    var calculatorWhatIsIV: String { "What is Implied Volatility?" }
    var calculatorWhatIsIVText: String { "IV measures the market's expectation of future price movement. Higher IV means the market expects bigger moves." }
    var calculatorIVRanges: String { "IV Ranges" }
    var calculatorLowIV: String { "Low IV" }
    var calculatorLowIVDesc: String { "When IV is low, options are relatively cheap. An increase in IV can boost your option's value." }
    var calculatorModerateIV: String { "Moderate IV" }
    var calculatorModerateIVDesc: String { "Normal market conditions with balanced pricing." }
    var calculatorHighIV: String { "High IV" }
    var calculatorHighIVDesc: String { "When IV is high (before events), options are expensive. If IV drops after the event, your option loses value even if the market moves in your direction." }
    var calculatorVeryHighIV: String { "Very High IV" }
    var calculatorVeryHighIVDesc: String { "Extreme volatility, usually around major events like budget or elections." }
    var calculatorIVImpact: String { "IV Impact" }
    var calculatorIVImpactText: String { "IV crush after events (budget, RBI policy) can cause losses even when direction is correct." }
    var calculatorGotIt: String { "Got It" }
    var calculatorEnterSpotPrice: String { "Enter spot price" }
    var calculatorEnterStrikePrice: String { "Enter strike price" }
    var calculatorEnterDays: String { "Enter days" }
    var calculatorEnterIV: String { "Enter IV" }
    var calculatorEnterRate: String { "Enter rate" }

    // MARK: - Greeks
    var greeksDelta: String { "Delta" }
    var greeksGamma: String { "Gamma" }
    var greeksTheta: String { "Theta" }
    var greeksVega: String { "Vega" }
    var greeksRho: String { "Rho" }
    var greeksDeltaDesc: String { "Price sensitivity" }
    var greeksGammaDesc: String { "Delta change rate" }
    var greeksThetaDesc: String { "Time decay/day" }
    var greeksVegaDesc: String { "IV sensitivity" }
    var greeksRhoDesc: String { "Interest rate sensitivity" }
    var greeksDeltaInterpDeepITM: String { "Deep ITM - moves almost 1:1 with underlying" }
    var greeksDeltaInterpATM: String { "ATM - 50% chance of expiring ITM" }
    var greeksDeltaInterpDeepOTM: String { "Deep OTM - low probability of profit" }
    var greeksDeltaInterpModerate: String { "Moderate sensitivity to underlying movement" }
    var greeksThetaInterpHigh: String { "High time decay - losing value rapidly" }
    var greeksThetaInterpModerate: String { "Moderate time decay" }
    var greeksThetaInterpLow: String { "Low time decay" }
    var greeksVegaInterpHigh: String { "High volatility sensitivity" }
    var greeksVegaInterpModerate: String { "Moderate volatility sensitivity" }
    var greeksVegaInterpLow: String { "Low volatility sensitivity" }
    var greeksQuickRefDeltaCall: String { "Price change per 1-point move in underlying" }
    var greeksQuickRefDeltaPut: String { "Price change per 1-point move (negative for puts)" }
    var greeksQuickRefGamma: String { "Change in Delta per 1-point move" }
    var greeksQuickRefTheta: String { "Daily time decay (negative = losing value)" }
    var greeksQuickRefVega: String { "Price change per 1% change in IV" }
    var greeksNearExpiryWarning: String { "Near Expiry Warning" }
    var greeksNearExpiryText: String { "Near expiry (< 3 days): Gamma explodes, Theta accelerates, Black-Scholes becomes less reliable intraday." }
    var greeksInterpretation: String { "Interpretation" }
    var greeksPerDay: String { "per day" }
    var greeksPer1PercentIV: String { "per 1% IV change" }
    var greeksPer1PercentRate: String { "per 1% rate change" }

    // MARK: - Option Chain
    var optionChainTitle: String { "Option Chain" }
    var optionChainExpiry: String { "Expiry" }
    var optionChainSpotPrice: String { "Spot Price" }
    var optionChainChange: String { "Change" }
    var optionChainCalls: String { "CALLS" }
    var optionChainPuts: String { "PUTS" }
    var optionChainStrike: String { "Strike" }
    var optionChainLTP: String { "LTP" }
    var optionChainOI: String { "OI" }
    var optionChainVolume: String { "Volume" }
    var optionChainIV: String { "IV" }
    var optionChainBidAsk: String { "Bid/Ask" }
    var optionChainDayHigh: String { "Day High" }
    var optionChainDayLow: String { "Day Low" }
    var optionChainATM: String { "ATM" }
    var optionChainITM: String { "ITM" }
    var optionChainOTM: String { "OTM" }
    var optionChainPCR: String { "PCR" }
    var optionChainMaxPain: String { "Max Pain" }
    var optionChainTotalCallOI: String { "Total Call OI" }
    var optionChainTotalPutOI: String { "Total Put OI" }
    var optionChainDemoDataNote: String { "Showing demo data. Connect to a broker for live data." }
    var optionChainNoData: String { "No data available" }
    var optionChainLoading: String { "Loading option chain..." }
    var optionChainRefresh: String { "Refresh" }
    var optionChainLastUpdated: String { "Last updated" }
    var optionChainStrikesNearATM: String { "Strikes near ATM" }
    var optionChainAllStrikes: String { "All Strikes" }
    var optionChainSelectExpiry: String { "Select Expiry" }

    // Day names
    var dayMon: String { "Mon" }
    var dayTue: String { "Tue" }
    var dayWed: String { "Wed" }
    var dayThu: String { "Thu" }
    var dayFri: String { "Fri" }
    var daySat: String { "Sat" }
    var daySun: String { "Sun" }

    // MARK: - Settings
    var settingsTitle: String { "Settings" }
    var settingsLanguage: String { "LANGUAGE" }
    var settingsLanguageSubtitle: String { "Choose your preferred language" }
    var settingsTheme: String { "THEME" }
    var settingsThemeSubtitle: String { "Choose your preferred theme" }
    var settingsDataSource: String { "Data Source" }
    var settingsDataSourceSubtitle: String { "Choose your data provider" }
    var settingsAbout: String { "ABOUT" }
    var settingsVersion: String { "Version" }
    var settingsRateApp: String { "Rate App" }
    var settingsShareApp: String { "Share App" }
    var settingsPrivacyPolicy: String { "Privacy Policy" }
    var settingsDisclaimer: String { "DISCLAIMER" }
    var settingsDisclaimerText: String { "This app provides theoretical option prices using the Black-Scholes model for educational and analytical purposes only." }
    var settingsDisplay: String { "DISPLAY" }
    var settingsGeneral: String { "GENERAL" }
    var settingsDeveloper: String { "Developer" }
    var settingsDeveloperName: String { "Rishi" }
    var settingsAppDescription: String { "Optix" }
    var settingsAllRightsReserved: String { "All rights reserved" }

    // MARK: - AI Insights
    var aiInsightsTitle: String { "AI Insights" }
    var aiInsightsSelectIndex: String { "Select Index" }
    var aiInsightsAnalyze: String { "Analyze" }
    var aiInsightsAnalyzing: String { "Analyzing..." }
    var aiInsightsUpdated: String { "Updated" }
    var aiInsightsNotAnalyzed: String { "Not analyzed" }
    var aiInsightsNoData: String { "No option chain data available" }
    var aiInsightsCallsTab: String { "Calls" }
    var aiInsightsPutsTab: String { "Puts" }
    var aiInsightsMarketTab: String { "Market" }
    var aiInsightsConfidence: String { "Confidence" }
    var aiInsightsMarketBias: String { "Market Bias" }
    var aiInsightsPrediction: String { "Prediction" }
    var aiInsightsDirection: String { "Direction" }
    var aiInsightsEntry: String { "Entry" }
    var aiInsightsTarget: String { "Target" }
    var aiInsightsStopLoss: String { "Stop Loss" }
    var aiInsightsExpectedMove: String { "Expected Move" }
    var aiInsightsRiskReward: String { "Risk:Reward" }
    var aiInsightsRationale: String { "Rationale" }
    var aiInsightsKeyLevels: String { "Key Levels" }
    var aiInsightsSupport: String { "Support" }
    var aiInsightsResistance: String { "Resistance" }
    var aiInsightsAnalyzeStrikes: String { "Analyze Strikes" }
    var aiInsightsSelectStrike: String { "Select Strike" }
    var aiInsightsStrikeAnalysis: String { "Strike Analysis" }
    var aiInsightsOverallSentiment: String { "Overall Sentiment" }
    var aiInsightsOIAnalysis: String { "OI Analysis" }
    var aiInsightsIVAnalysis: String { "IV Analysis" }
    var aiInsightsRecommendation: String { "Recommendation" }
    var aiInsightsBullish: String { "Bullish" }
    var aiInsightsBearish: String { "Bearish" }
    var aiInsightsNeutral: String { "Neutral" }
    var aiInsightsStrongBuy: String { "Strong Buy" }
    var aiInsightsBuy: String { "Buy" }
    var aiInsightsHold: String { "Hold" }
    var aiInsightsSell: String { "Sell" }
    var aiInsightsStrongSell: String { "Strong Sell" }
    var aiInsightsHigh: String { "High" }
    var aiInsightsMedium: String { "Medium" }
    var aiInsightsLow: String { "Low" }
    var aiInsightsLong: String { "Long" }
    var aiInsightsShort: String { "Short" }
    var aiInsightsAvoid: String { "Avoid" }

    // MARK: - Charts
    var chartsIndexChart: String { "Chart" }
    var chartsATMOptions: String { "ATM Options" }
    var chartsMarketOverview: String { "Market Overview" }
    var chartsPutCallRatio: String { "Put-Call Ratio" }
    var chartsMaxPain: String { "Max Pain" }
    var chartsTotalCallOI: String { "Total Call OI" }
    var chartsTotalPutOI: String { "Total Put OI" }
    var chartsBullish: String { "Bullish" }
    var chartsBearish: String { "Bearish" }
    var chartsAboveSpot: String { "Above Spot" }
    var chartsBelowSpot: String { "Below Spot" }
    var chartsLoadingChart: String { "Loading chart data..." }
    var chartsLoadingOption: String { "Loading option data..." }
    var chartsPrice: String { "Price" }
    var chartsHigh: String { "High" }
    var chartsLow: String { "Low" }
    var chartsAvgOI: String { "Avg OI" }
    var chartsAvgIV: String { "Avg IV" }

    // MARK: - Upstox Login
    var upstoxConnectTitle: String { "Connect to Upstox" }
    var upstoxConnectSubtitle: String { "Login with your Upstox account to get live option chain data" }
    var upstoxLiveData: String { "Live Data" }
    var upstoxLiveDataDesc: String { "Real-time option chain from NSE" }
    var upstoxAccurateGreeks: String { "Accurate Greeks" }
    var upstoxAccurateGreeksDesc: String { "Delta, Gamma, Theta, Vega from exchange" }
    var upstoxAutoRefresh: String { "Auto Refresh" }
    var upstoxAutoRefreshDesc: String { "Data updates every few seconds" }
    var upstoxConnecting: String { "Connecting..." }
    var upstoxLoginButton: String { "Login with Upstox" }
    var upstoxContinueDemo: String { "Continue with Demo Data" }
    var upstoxConnected: String { "Connected" }
    var upstoxNotConnected: String { "Not Connected" }
    var upstoxDataProviders: String { "DATA PROVIDERS" }
    var upstoxRealTimeStream: String { "Real-time Stream" }
    var upstoxRefreshData: String { "Refresh Data" }
    var upstoxDisconnect: String { "Disconnect" }
    var upstoxUseDemoData: String { "Use Demo Data" }
    var upstoxDataSource: String { "Data Source" }
    var upstoxDemoData: String { "Demo Data" }
    var upstoxLive: String { "Upstox Live" }

    // MARK: - Common
    var commonDone: String { "Done" }
    var commonCancel: String { "Cancel" }
    var commonClose: String { "Close" }
    var commonError: String { "Error" }
    var commonRetry: String { "Retry" }
    var commonOK: String { "OK" }
    var commonYes: String { "Yes" }
    var commonNo: String { "No" }
    var commonSave: String { "Save" }
    var commonDelete: String { "Delete" }
    var commonEdit: String { "Edit" }
    var commonShare: String { "Share" }
    var commonCopy: String { "Copy" }
    var commonSearch: String { "Search" }

    // MARK: - Moneyness
    var moneynessITM: String { "In The Money" }
    var moneynessATM: String { "At The Money" }
    var moneynessOTM: String { "Out of The Money" }
    var moneynessDeepITM: String { "Deep ITM" }
    var moneynessDeepOTM: String { "Deep OTM" }

    // MARK: - Theme Names
    var themeClassicGreen: String { "Classic Green" }
    var themeOceanBlue: String { "Ocean Blue" }
    var themeMidnightPurple: String { "Midnight Purple" }
    var themeSunsetOrange: String { "Sunset Orange" }
    var themeCyberpunk: String { "Cyberpunk" }
    var themeClassicGreenDesc: String { "Traditional trading terminal" }
    var themeOceanBlueDesc: String { "Cool ocean vibes" }
    var themeMidnightPurpleDesc: String { "Deep purple aesthetics" }
    var themeSunsetOrangeDesc: String { "Warm sunset tones" }
    var themeCyberpunkDesc: String { "Futuristic neon style" }

    // MARK: - Paper Trading
    var tabPaperTrade: String { "Paper" }
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
    var paperTradeStopLossTarget: String { "SL/Target" }
    var paperTradeLoginRequired: String { "Login Required" }
    var paperTradeLoginDescription: String { "Sign in to access paper trading and track your portfolio" }
    var paperTradeFeature1: String { "Practice trading with virtual ₹10L" }
    var paperTradeFeature2: String { "Track your positions & P&L" }
    var paperTradeFeature3: String { "View performance analytics" }

    // MARK: - Education Quiz UI
    var educationQuiz: String { "Quiz" }
    var educationTestKnowledge: String { "Test your knowledge" }
    var educationHasQuiz: String { "Quiz Included" }
    var educationAvailableQuizzes: String { "AVAILABLE QUIZZES" }
    var educationCompleted: String { "Completed" }
    var educationAvailable: String { "Available" }
    var educationAvgScore: String { "Avg Score" }
    var educationCorrect: String { "correct" }

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
