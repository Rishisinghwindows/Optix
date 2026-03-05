import Foundation

// MARK: - Localization Proxy

enum L {
    private static var s: LocalizedStrings { LocalizationManager.shared.strings }

    // MARK: - Tab Bar
    static var tabOptionChain: String { s.tabOptionChain }
    static var tabCalculator: String { s.tabCalculator }
    static var tabAIInsights: String { s.tabAIInsights }
    static var tabCharts: String { s.tabCharts }
    static var tabQuiz: String { s.tabQuiz }
    static var tabSettings: String { s.tabSettings }

    // MARK: - Splash
    static var splashTitle: String { s.splashTitle }
    static var splashSubtitle: String { s.splashSubtitle }

    // MARK: - Onboarding
    static var onboardingSkip: String { s.onboardingSkip }
    static var onboardingNext: String { s.onboardingNext }
    static var onboardingGetStarted: String { s.onboardingGetStarted }
    static var onboarding1Title: String { s.onboarding1Title }
    static var onboarding1Subtitle: String { s.onboarding1Subtitle }
    static var onboarding1Feature1: String { s.onboarding1Feature1 }
    static var onboarding1Feature2: String { s.onboarding1Feature2 }
    static var onboarding1Feature3: String { s.onboarding1Feature3 }
    static var onboarding1Feature4: String { s.onboarding1Feature4 }
    static var onboarding2Title: String { s.onboarding2Title }
    static var onboarding2Subtitle: String { s.onboarding2Subtitle }
    static var onboarding2Feature1: String { s.onboarding2Feature1 }
    static var onboarding2Feature2: String { s.onboarding2Feature2 }
    static var onboarding2Feature3: String { s.onboarding2Feature3 }
    static var onboarding2Feature4: String { s.onboarding2Feature4 }
    static var onboarding3Title: String { s.onboarding3Title }
    static var onboarding3Subtitle: String { s.onboarding3Subtitle }
    static var onboarding3Feature1: String { s.onboarding3Feature1 }
    static var onboarding3Feature2: String { s.onboarding3Feature2 }
    static var onboarding3Feature3: String { s.onboarding3Feature3 }
    static var onboarding3Feature4: String { s.onboarding3Feature4 }
    static var onboarding4Title: String { s.onboarding4Title }
    static var onboarding4Subtitle: String { s.onboarding4Subtitle }
    static var onboarding4Feature1: String { s.onboarding4Feature1 }
    static var onboarding4Feature2: String { s.onboarding4Feature2 }
    static var onboarding4Feature3: String { s.onboarding4Feature3 }
    static var onboarding4Feature4: String { s.onboarding4Feature4 }

    // MARK: - Calculator
    static var calculatorTitle: String { s.calculatorTitle }
    static var calculatorParameters: String { s.calculatorParameters }
    static var calculatorSpotPrice: String { s.calculatorSpotPrice }
    static var calculatorStrikePrice: String { s.calculatorStrikePrice }
    static var calculatorDaysToExpiry: String { s.calculatorDaysToExpiry }
    static var calculatorIVPercent: String { s.calculatorIVPercent }
    static var calculatorRiskFreeRate: String { s.calculatorRiskFreeRate }
    static var calculatorOptionType: String { s.calculatorOptionType }
    static var calculatorCall: String { s.calculatorCall }
    static var calculatorPut: String { s.calculatorPut }
    static var calculatorCalculate: String { s.calculatorCalculate }
    static var calculatorReset: String { s.calculatorReset }
    static var calculatorResults: String { s.calculatorResults }
    static var calculatorTheoreticalPrice: String { s.calculatorTheoreticalPrice }
    static var calculatorIntrinsicValue: String { s.calculatorIntrinsicValue }
    static var calculatorTimeValue: String { s.calculatorTimeValue }
    static var calculatorBreakeven: String { s.calculatorBreakeven }
    static var calculatorMoneyness: String { s.calculatorMoneyness }
    static var calculatorTheGreeks: String { s.calculatorTheGreeks }
    static var calculatorQuickReference: String { s.calculatorQuickReference }
    static var calculatorRiskDisclaimer: String { s.calculatorRiskDisclaimer }
    static var calculatorRiskDisclaimerText: String { s.calculatorRiskDisclaimerText }
    static var calculatorIVEducationTitle: String { s.calculatorIVEducationTitle }
    static var calculatorIVEducationText: String { s.calculatorIVEducationText }
    static var calculatorLearnAboutIV: String { s.calculatorLearnAboutIV }
    static var calculatorWhatIsIV: String { s.calculatorWhatIsIV }
    static var calculatorWhatIsIVText: String { s.calculatorWhatIsIVText }
    static var calculatorIVRanges: String { s.calculatorIVRanges }
    static var calculatorLowIV: String { s.calculatorLowIV }
    static var calculatorLowIVDesc: String { s.calculatorLowIVDesc }
    static var calculatorModerateIV: String { s.calculatorModerateIV }
    static var calculatorModerateIVDesc: String { s.calculatorModerateIVDesc }
    static var calculatorHighIV: String { s.calculatorHighIV }
    static var calculatorHighIVDesc: String { s.calculatorHighIVDesc }
    static var calculatorVeryHighIV: String { s.calculatorVeryHighIV }
    static var calculatorVeryHighIVDesc: String { s.calculatorVeryHighIVDesc }
    static var calculatorIVImpact: String { s.calculatorIVImpact }
    static var calculatorIVImpactText: String { s.calculatorIVImpactText }
    static var calculatorGotIt: String { s.calculatorGotIt }
    static var calculatorEnterSpotPrice: String { s.calculatorEnterSpotPrice }
    static var calculatorEnterStrikePrice: String { s.calculatorEnterStrikePrice }
    static var calculatorEnterDays: String { s.calculatorEnterDays }
    static var calculatorEnterIV: String { s.calculatorEnterIV }
    static var calculatorEnterRate: String { s.calculatorEnterRate }

    // MARK: - Greeks
    static var greeksDelta: String { s.greeksDelta }
    static var greeksGamma: String { s.greeksGamma }
    static var greeksTheta: String { s.greeksTheta }
    static var greeksVega: String { s.greeksVega }
    static var greeksRho: String { s.greeksRho }
    static var greeksDeltaDesc: String { s.greeksDeltaDesc }
    static var greeksGammaDesc: String { s.greeksGammaDesc }
    static var greeksThetaDesc: String { s.greeksThetaDesc }
    static var greeksVegaDesc: String { s.greeksVegaDesc }
    static var greeksRhoDesc: String { s.greeksRhoDesc }
    static var greeksDeltaInterpDeepITM: String { s.greeksDeltaInterpDeepITM }
    static var greeksDeltaInterpATM: String { s.greeksDeltaInterpATM }
    static var greeksDeltaInterpDeepOTM: String { s.greeksDeltaInterpDeepOTM }
    static var greeksDeltaInterpModerate: String { s.greeksDeltaInterpModerate }
    static var greeksThetaInterpHigh: String { s.greeksThetaInterpHigh }
    static var greeksThetaInterpModerate: String { s.greeksThetaInterpModerate }
    static var greeksThetaInterpLow: String { s.greeksThetaInterpLow }
    static var greeksVegaInterpHigh: String { s.greeksVegaInterpHigh }
    static var greeksVegaInterpModerate: String { s.greeksVegaInterpModerate }
    static var greeksVegaInterpLow: String { s.greeksVegaInterpLow }
    static var greeksQuickRefDeltaCall: String { s.greeksQuickRefDeltaCall }
    static var greeksQuickRefDeltaPut: String { s.greeksQuickRefDeltaPut }
    static var greeksQuickRefGamma: String { s.greeksQuickRefGamma }
    static var greeksQuickRefTheta: String { s.greeksQuickRefTheta }
    static var greeksQuickRefVega: String { s.greeksQuickRefVega }
    static var greeksNearExpiryWarning: String { s.greeksNearExpiryWarning }
    static var greeksNearExpiryText: String { s.greeksNearExpiryText }
    static var greeksInterpretation: String { s.greeksInterpretation }
    static var greeksPerDay: String { s.greeksPerDay }
    static var greeksPer1PercentIV: String { s.greeksPer1PercentIV }
    static var greeksPer1PercentRate: String { s.greeksPer1PercentRate }

    // MARK: - Option Chain
    static var optionChainTitle: String { s.optionChainTitle }
    static var optionChainExpiry: String { s.optionChainExpiry }
    static var optionChainSpotPrice: String { s.optionChainSpotPrice }
    static var optionChainChange: String { s.optionChainChange }
    static var optionChainCalls: String { s.optionChainCalls }
    static var optionChainPuts: String { s.optionChainPuts }
    static var optionChainStrike: String { s.optionChainStrike }
    static var optionChainLTP: String { s.optionChainLTP }
    static var optionChainOI: String { s.optionChainOI }
    static var optionChainVolume: String { s.optionChainVolume }
    static var optionChainIV: String { s.optionChainIV }
    static var optionChainBidAsk: String { s.optionChainBidAsk }
    static var optionChainDayHigh: String { s.optionChainDayHigh }
    static var optionChainDayLow: String { s.optionChainDayLow }
    static var optionChainATM: String { s.optionChainATM }
    static var optionChainITM: String { s.optionChainITM }
    static var optionChainOTM: String { s.optionChainOTM }
    static var optionChainPCR: String { s.optionChainPCR }
    static var optionChainMaxPain: String { s.optionChainMaxPain }
    static var optionChainTotalCallOI: String { s.optionChainTotalCallOI }
    static var optionChainTotalPutOI: String { s.optionChainTotalPutOI }
    static var optionChainDemoDataNote: String { s.optionChainDemoDataNote }
    static var optionChainNoData: String { s.optionChainNoData }
    static var optionChainLoading: String { s.optionChainLoading }
    static var optionChainRefresh: String { s.optionChainRefresh }
    static var optionChainLastUpdated: String { s.optionChainLastUpdated }
    static var optionChainStrikesNearATM: String { s.optionChainStrikesNearATM }
    static var optionChainAllStrikes: String { s.optionChainAllStrikes }
    static var optionChainSelectExpiry: String { s.optionChainSelectExpiry }
    static var dayMon: String { s.dayMon }
    static var dayTue: String { s.dayTue }
    static var dayWed: String { s.dayWed }
    static var dayThu: String { s.dayThu }
    static var dayFri: String { s.dayFri }
    static var daySat: String { s.daySat }
    static var daySun: String { s.daySun }

    // MARK: - Settings
    static var settingsTitle: String { s.settingsTitle }
    static var settingsLanguage: String { s.settingsLanguage }
    static var settingsLanguageSubtitle: String { s.settingsLanguageSubtitle }
    static var settingsTheme: String { s.settingsTheme }
    static var settingsThemeSubtitle: String { s.settingsThemeSubtitle }
    static var settingsDataSource: String { s.settingsDataSource }
    static var settingsDataSourceSubtitle: String { s.settingsDataSourceSubtitle }
    static var settingsAbout: String { s.settingsAbout }
    static var settingsVersion: String { s.settingsVersion }
    static var settingsRateApp: String { s.settingsRateApp }
    static var settingsShareApp: String { s.settingsShareApp }
    static var settingsPrivacyPolicy: String { s.settingsPrivacyPolicy }
    static var settingsDisclaimer: String { s.settingsDisclaimer }
    static var settingsDisclaimerText: String { s.settingsDisclaimerText }
    static var settingsDisplay: String { s.settingsDisplay }
    static var settingsGeneral: String { s.settingsGeneral }
    static var settingsDeveloper: String { s.settingsDeveloper }
    static var settingsDeveloperName: String { s.settingsDeveloperName }
    static var settingsAppDescription: String { s.settingsAppDescription }
    static var settingsAllRightsReserved: String { s.settingsAllRightsReserved }

    // MARK: - AI Insights
    static var aiInsightsTitle: String { s.aiInsightsTitle }
    static var aiInsightsSelectIndex: String { s.aiInsightsSelectIndex }
    static var aiInsightsAnalyze: String { s.aiInsightsAnalyze }
    static var aiInsightsAnalyzing: String { s.aiInsightsAnalyzing }
    static var aiInsightsUpdated: String { s.aiInsightsUpdated }
    static var aiInsightsNotAnalyzed: String { s.aiInsightsNotAnalyzed }
    static var aiInsightsNoData: String { s.aiInsightsNoData }
    static var aiInsightsCallsTab: String { s.aiInsightsCallsTab }
    static var aiInsightsPutsTab: String { s.aiInsightsPutsTab }
    static var aiInsightsMarketTab: String { s.aiInsightsMarketTab }
    static var aiInsightsConfidence: String { s.aiInsightsConfidence }
    static var aiInsightsMarketBias: String { s.aiInsightsMarketBias }
    static var aiInsightsPrediction: String { s.aiInsightsPrediction }
    static var aiInsightsDirection: String { s.aiInsightsDirection }
    static var aiInsightsEntry: String { s.aiInsightsEntry }
    static var aiInsightsTarget: String { s.aiInsightsTarget }
    static var aiInsightsStopLoss: String { s.aiInsightsStopLoss }
    static var aiInsightsExpectedMove: String { s.aiInsightsExpectedMove }
    static var aiInsightsRiskReward: String { s.aiInsightsRiskReward }
    static var aiInsightsRationale: String { s.aiInsightsRationale }
    static var aiInsightsKeyLevels: String { s.aiInsightsKeyLevels }
    static var aiInsightsSupport: String { s.aiInsightsSupport }
    static var aiInsightsResistance: String { s.aiInsightsResistance }
    static var aiInsightsAnalyzeStrikes: String { s.aiInsightsAnalyzeStrikes }
    static var aiInsightsSelectStrike: String { s.aiInsightsSelectStrike }
    static var aiInsightsStrikeAnalysis: String { s.aiInsightsStrikeAnalysis }
    static var aiInsightsOverallSentiment: String { s.aiInsightsOverallSentiment }
    static var aiInsightsOIAnalysis: String { s.aiInsightsOIAnalysis }
    static var aiInsightsIVAnalysis: String { s.aiInsightsIVAnalysis }
    static var aiInsightsRecommendation: String { s.aiInsightsRecommendation }
    static var aiInsightsBullish: String { s.aiInsightsBullish }
    static var aiInsightsBearish: String { s.aiInsightsBearish }
    static var aiInsightsNeutral: String { s.aiInsightsNeutral }
    static var aiInsightsStrongBuy: String { s.aiInsightsStrongBuy }
    static var aiInsightsBuy: String { s.aiInsightsBuy }
    static var aiInsightsHold: String { s.aiInsightsHold }
    static var aiInsightsSell: String { s.aiInsightsSell }
    static var aiInsightsStrongSell: String { s.aiInsightsStrongSell }
    static var aiInsightsHigh: String { s.aiInsightsHigh }
    static var aiInsightsMedium: String { s.aiInsightsMedium }
    static var aiInsightsLow: String { s.aiInsightsLow }
    static var aiInsightsLong: String { s.aiInsightsLong }
    static var aiInsightsShort: String { s.aiInsightsShort }
    static var aiInsightsAvoid: String { s.aiInsightsAvoid }

    // MARK: - Charts
    static var chartsIndexChart: String { s.chartsIndexChart }
    static var chartsATMOptions: String { s.chartsATMOptions }
    static var chartsMarketOverview: String { s.chartsMarketOverview }
    static var chartsPutCallRatio: String { s.chartsPutCallRatio }
    static var chartsMaxPain: String { s.chartsMaxPain }
    static var chartsTotalCallOI: String { s.chartsTotalCallOI }
    static var chartsTotalPutOI: String { s.chartsTotalPutOI }
    static var chartsBullish: String { s.chartsBullish }
    static var chartsBearish: String { s.chartsBearish }
    static var chartsAboveSpot: String { s.chartsAboveSpot }
    static var chartsBelowSpot: String { s.chartsBelowSpot }
    static var chartsLoadingChart: String { s.chartsLoadingChart }
    static var chartsLoadingOption: String { s.chartsLoadingOption }
    static var chartsPrice: String { s.chartsPrice }
    static var chartsHigh: String { s.chartsHigh }
    static var chartsLow: String { s.chartsLow }
    static var chartsAvgOI: String { s.chartsAvgOI }
    static var chartsAvgIV: String { s.chartsAvgIV }

    // MARK: - Upstox
    static var upstoxConnectTitle: String { s.upstoxConnectTitle }
    static var upstoxConnectSubtitle: String { s.upstoxConnectSubtitle }
    static var upstoxLiveData: String { s.upstoxLiveData }
    static var upstoxLiveDataDesc: String { s.upstoxLiveDataDesc }
    static var upstoxAccurateGreeks: String { s.upstoxAccurateGreeks }
    static var upstoxAccurateGreeksDesc: String { s.upstoxAccurateGreeksDesc }
    static var upstoxAutoRefresh: String { s.upstoxAutoRefresh }
    static var upstoxAutoRefreshDesc: String { s.upstoxAutoRefreshDesc }
    static var upstoxConnecting: String { s.upstoxConnecting }
    static var upstoxLoginButton: String { s.upstoxLoginButton }
    static var upstoxContinueDemo: String { s.upstoxContinueDemo }
    static var upstoxConnected: String { s.upstoxConnected }
    static var upstoxNotConnected: String { s.upstoxNotConnected }
    static var upstoxDataProviders: String { s.upstoxDataProviders }
    static var upstoxRealTimeStream: String { s.upstoxRealTimeStream }
    static var upstoxRefreshData: String { s.upstoxRefreshData }
    static var upstoxDisconnect: String { s.upstoxDisconnect }
    static var upstoxUseDemoData: String { s.upstoxUseDemoData }
    static var upstoxDataSource: String { s.upstoxDataSource }
    static var upstoxDemoData: String { s.upstoxDemoData }
    static var upstoxLive: String { s.upstoxLive }

    // MARK: - Common
    static var commonDone: String { s.commonDone }
    static var commonCancel: String { s.commonCancel }
    static var commonClose: String { s.commonClose }
    static var commonError: String { s.commonError }
    static var commonRetry: String { s.commonRetry }
    static var commonOK: String { s.commonOK }
    static var commonYes: String { s.commonYes }
    static var commonNo: String { s.commonNo }
    static var commonSave: String { s.commonSave }
    static var commonDelete: String { s.commonDelete }
    static var commonEdit: String { s.commonEdit }
    static var commonShare: String { s.commonShare }
    static var commonCopy: String { s.commonCopy }
    static var commonSearch: String { s.commonSearch }

    // MARK: - Moneyness
    static var moneynessITM: String { s.moneynessITM }
    static var moneynessATM: String { s.moneynessATM }
    static var moneynessOTM: String { s.moneynessOTM }
    static var moneynessDeepITM: String { s.moneynessDeepITM }
    static var moneynessDeepOTM: String { s.moneynessDeepOTM }

    // MARK: - Theme Names
    static var themeClassicGreen: String { s.themeClassicGreen }
    static var themeOceanBlue: String { s.themeOceanBlue }
    static var themeMidnightPurple: String { s.themeMidnightPurple }
    static var themeSunsetOrange: String { s.themeSunsetOrange }
    static var themeCyberpunk: String { s.themeCyberpunk }
    static var themeClassicGreenDesc: String { s.themeClassicGreenDesc }
    static var themeOceanBlueDesc: String { s.themeOceanBlueDesc }
    static var themeMidnightPurpleDesc: String { s.themeMidnightPurpleDesc }
    static var themeSunsetOrangeDesc: String { s.themeSunsetOrangeDesc }
    static var themeCyberpunkDesc: String { s.themeCyberpunkDesc }

    // MARK: - Additional Settings
    static var settingsAccount: String { s.settingsAccount }
    static var settingsDataRefresh: String { s.settingsDataRefresh }
    static var settingsSpotPriceInterval: String { s.settingsSpotPriceInterval }
    static var settingsOptionChainRefresh: String { s.settingsOptionChainRefresh }
    static var settingsDefaultIndex: String { s.settingsDefaultIndex }
    static var settingsPreferences: String { s.settingsPreferences }
    static var settingsHapticFeedback: String { s.settingsHapticFeedback }
    static var settingsBuild: String { s.settingsBuild }
    static var settingsLogoutUpstox: String { s.settingsLogoutUpstox }
    static var settingsImportantNotice: String { s.settingsImportantNotice }
    static var settingsDisclaimerBullet1: String { s.settingsDisclaimerBullet1 }
    static var settingsDisclaimerBullet2: String { s.settingsDisclaimerBullet2 }
    static var settingsDisclaimerBullet3: String { s.settingsDisclaimerBullet3 }
    static var settingsDisclaimerBullet4: String { s.settingsDisclaimerBullet4 }

    // MARK: - Appearance (Light/Dark Mode)
    static var settingsAppearance: String { s.settingsAppearance }
    static var settingsAppearanceMode: String { s.settingsAppearanceMode }
    static var themeSystemDefault: String { s.themeSystemDefault }
    static var themeLight: String { s.themeLight }
    static var themeDark: String { s.themeDark }

    // MARK: - Additional Calculator
    static var calculatorAdvancedAnalysis: String { s.calculatorAdvancedAnalysis }
    static var calculatorGreeksAndRisk: String { s.calculatorGreeksAndRisk }
    static var calculatorHide: String { s.calculatorHide }
    static var calculatorShow: String { s.calculatorShow }
    static var calculatorPriceCalculator: String { s.calculatorPriceCalculator }
    static var calculatorSimple: String { s.calculatorSimple }
    static var calculatorTargetDescription: String { s.calculatorTargetDescription }
    static var calculatorIfNiftyGoesTo: String { s.calculatorIfNiftyGoesTo }
    static var calculatorIfNiftyFallsTo: String { s.calculatorIfNiftyFallsTo }
    static var calculatorYourTarget: String { s.calculatorYourTarget }
    static var calculatorYourStopLoss: String { s.calculatorYourStopLoss }
    static var calculatorYourOptionWillBe: String { s.calculatorYourOptionWillBe }
    static var calculatorOptionPremium: String { s.calculatorOptionPremium }
    static var calculatorMaxProfit: String { s.calculatorMaxProfit }
    static var calculatorMaxLoss: String { s.calculatorMaxLoss }
    static var calculatorEnterTargetSL: String { s.calculatorEnterTargetSL }
    static var calculatorDaysLeft: String { s.calculatorDaysLeft }
    static var calculatorTheoreticalNote: String { s.calculatorTheoreticalNote }
    static var calculatorProbabilityRisk: String { s.calculatorProbabilityRisk }
    static var calculatorPriceDistribution: String { s.calculatorPriceDistribution }
    static var calculatorRiskAssessment: String { s.calculatorRiskAssessment }
    static var calculatorThetaRisk: String { s.calculatorThetaRisk }
    static var calculatorVegaRisk: String { s.calculatorVegaRisk }
    static var calculatorPositionImpact: String { s.calculatorPositionImpact }
    static var calculatorNiftyMove100: String { s.calculatorNiftyMove100 }
    static var calculatorDailyThetaDecay: String { s.calculatorDailyThetaDecay }
    static var calculatorIV1PercentIncrease: String { s.calculatorIV1PercentIncrease }
    static var calculatorBasedOnDelta: String { s.calculatorBasedOnDelta }
    static var calculatorTimeValueErosion: String { s.calculatorTimeValueErosion }
    static var calculatorVolatilitySensitivity: String { s.calculatorVolatilitySensitivity }

    // MARK: - Additional Greeks
    static var greeksInsight: String { s.greeksInsight }
    static var greeksHighDelta: String { s.greeksHighDelta }
    static var greeksLowDelta: String { s.greeksLowDelta }
    static var greeksModerateDelta: String { s.greeksModerateDelta }
    static var greeksHighGamma: String { s.greeksHighGamma }
    static var greeksLowGamma: String { s.greeksLowGamma }
    static var greeksHighTheta: String { s.greeksHighTheta }
    static var greeksLowTheta: String { s.greeksLowTheta }
    static var greeksHighVega: String { s.greeksHighVega }
    static var greeksLowVega: String { s.greeksLowVega }

    // MARK: - Additional Option Chain
    static var optionChainToday: String { s.optionChainToday }
    static var optionChainViewChart: String { s.optionChainViewChart }
    static var optionChainSelectIndex: String { s.optionChainSelectIndex }
    static var optionChainIndexInfo: String { s.optionChainIndexInfo }
    static var optionChainLotSize: String { s.optionChainLotSize }
    static var optionChainStrikeInterval: String { s.optionChainStrikeInterval }
    static var optionChainExpiryDay: String { s.optionChainExpiryDay }
    static var optionChainExchange: String { s.optionChainExchange }
    static var optionChainNearATM: String { s.optionChainNearATM }
    static var optionChainNSEIndices: String { s.optionChainNSEIndices }
    static var optionChainBSEIndices: String { s.optionChainBSEIndices }
    static var optionChainDemoConnect: String { s.optionChainDemoConnect }

    // MARK: - Onboarding Page 5
    static var onboarding5Title: String { s.onboarding5Title }
    static var onboarding5Subtitle: String { s.onboarding5Subtitle }
    static var onboarding5Feature1: String { s.onboarding5Feature1 }
    static var onboarding5Feature2: String { s.onboarding5Feature2 }
    static var onboarding5Feature3: String { s.onboarding5Feature3 }

    // MARK: - IV Education Detail
    static var calculatorHighIVExpensive: String { s.calculatorHighIVExpensive }
    static var calculatorHighIVExpensiveDesc: String { s.calculatorHighIVExpensiveDesc }
    static var calculatorLowIVCheap: String { s.calculatorLowIVCheap }
    static var calculatorLowIVCheapDesc: String { s.calculatorLowIVCheapDesc }
    static var calculatorEventDrivenIV: String { s.calculatorEventDrivenIV }
    static var calculatorEventDrivenIVDesc: String { s.calculatorEventDrivenIVDesc }

    // MARK: - Market Bias
    static var marketBiasStrongBullish: String { s.marketBiasStrongBullish }
    static var marketBiasStrongBearish: String { s.marketBiasStrongBearish }

    // MARK: - Actual Theme Names
    static var themeProfessionalBlue: String { s.themeProfessionalBlue }
    static var themeProfessionalBlueDesc: String { s.themeProfessionalBlueDesc }
    static var themeDhanPremium: String { s.themeDhanPremium }
    static var themeDhanPremiumDesc: String { s.themeDhanPremiumDesc }
    static var themeClassicGreenActualDesc: String { s.themeClassicGreenActualDesc }

    // MARK: - Paper Trading
    static var tabPaperTrade: String { s.tabPaperTrade }
    static var paperTradeDashboard: String { s.paperTradeDashboard }
    static var paperTradePositions: String { s.paperTradePositions }
    static var paperTradeHistory: String { s.paperTradeHistory }
    static var paperTradePerformance: String { s.paperTradePerformance }
    static var paperTradePortfolioValue: String { s.paperTradePortfolioValue }
    static var paperTradeAvailableMargin: String { s.paperTradeAvailableMargin }
    static var paperTradeTotalPnL: String { s.paperTradeTotalPnL }
    static var paperTradeUnrealizedPnL: String { s.paperTradeUnrealizedPnL }
    static var paperTradeRealizedPnL: String { s.paperTradeRealizedPnL }
    static var paperTradeWinRate: String { s.paperTradeWinRate }
    static var paperTradeMaxDrawdown: String { s.paperTradeMaxDrawdown }
    static var paperTradeBestTrade: String { s.paperTradeBestTrade }
    static var paperTradeWorstTrade: String { s.paperTradeWorstTrade }
    static var paperTradeProfitFactor: String { s.paperTradeProfitFactor }
    static var paperTradeAvgHoldingDays: String { s.paperTradeAvgHoldingDays }
    static var paperTradeSquareOff: String { s.paperTradeSquareOff }
    static var paperTradeExecute: String { s.paperTradeExecute }
    static var paperTradeBuy: String { s.paperTradeBuy }
    static var paperTradeSell: String { s.paperTradeSell }
    static var paperTradeQuantity: String { s.paperTradeQuantity }
    static var paperTradeResetPortfolio: String { s.paperTradeResetPortfolio }
    static var paperTradeResetConfirmation: String { s.paperTradeResetConfirmation }
    static var paperTradeNoPositions: String { s.paperTradeNoPositions }
    static var paperTradeNoHistory: String { s.paperTradeNoHistory }
    static var paperTradeStartTrading: String { s.paperTradeStartTrading }
    static var paperTradeStopLoss: String { s.paperTradeStopLoss }
    static var paperTradeTarget: String { s.paperTradeTarget }
    static var paperTradeStopLossTarget: String { s.paperTradeStopLossTarget }
    static var paperTradeLoginRequired: String { s.paperTradeLoginRequired }
    static var paperTradeLoginDescription: String { s.paperTradeLoginDescription }
    static var paperTradeFeature1: String { s.paperTradeFeature1 }
    static var paperTradeFeature2: String { s.paperTradeFeature2 }
    static var paperTradeFeature3: String { s.paperTradeFeature3 }

    // MARK: - Education
    static var educationTitle: String { s.educationTitle }
    static var educationSubtitle: String { s.educationSubtitle }
    static var educationBasics: String { s.educationBasics }
    static var educationGreeks: String { s.educationGreeks }
    static var educationStrategies: String { s.educationStrategies }
    static var educationHasQuiz: String { s.educationHasQuiz }
    static var educationAvailableQuizzes: String { s.educationAvailableQuizzes }
    static var educationCompleted: String { s.educationCompleted }
    static var educationAvailable: String { s.educationAvailable }
    static var educationAvgScore: String { s.educationAvgScore }
    static var educationKeyPoints: String { s.educationKeyPoints }
    static var educationQuizIncluded: String { s.educationQuizIncluded }
    static var educationTakeQuiz: String { s.educationTakeQuiz }
    static var educationRetakeQuiz: String { s.educationRetakeQuiz }
    static var educationQuizComplete: String { s.educationQuizComplete }
    static var educationScore: String { s.educationScore }
    static var educationQuiz: String { s.educationQuiz }
    static var educationTestKnowledge: String { s.educationTestKnowledge }
    static var educationQuestion: String { s.educationQuestion }
    static var educationNextQuestion: String { s.educationNextQuestion }
    static var educationFinish: String { s.educationFinish }
    static var educationCheckAnswer: String { s.educationCheckAnswer }
    static var educationCorrect: String { s.educationCorrect }
    static var educationIncorrect: String { s.educationIncorrect }
    static var educationClose: String { s.educationClose }
    static var educationWhatItMeasures: String { s.educationWhatItMeasures }
    static var educationImpact: String { s.educationImpact }
    static var educationRange: String { s.educationRange }
    static var educationExample: String { s.educationExample }
    static var educationProTips: String { s.educationProTips }
    static var educationStrategyLegs: String { s.educationStrategyLegs }
    static var educationRiskReward: String { s.educationRiskReward }
    static var educationMaxProfit: String { s.educationMaxProfit }
    static var educationMaxLoss: String { s.educationMaxLoss }
    static var educationBreakeven: String { s.educationBreakeven }
    static var educationWhenToUse: String { s.educationWhenToUse }
    static var educationPros: String { s.educationPros }
    static var educationCons: String { s.educationCons }
    static var educationPayoffExample: String { s.educationPayoffExample }
    static var educationSpotPrice: String { s.educationSpotPrice }
    static var educationNetCost: String { s.educationNetCost }
    static var educationExpiryAt: String { s.educationExpiryAt }

    // MARK: - Quiz Questions

    // Quiz Titles
    static var quizTitleWhatAreOptions: String { s.quizTitleWhatAreOptions }
    static var quizTitleCallOptions: String { s.quizTitleCallOptions }
    static var quizTitlePutOptions: String { s.quizTitlePutOptions }
    static var quizTitleItmOtmAtm: String { s.quizTitleItmOtmAtm }

    // Quiz 1: What are Options?
    static var quizWhatAreOptionsQ: String { s.quizWhatAreOptionsQ }
    static var quizWhatAreOptionsO1: String { s.quizWhatAreOptionsO1 }
    static var quizWhatAreOptionsO2: String { s.quizWhatAreOptionsO2 }
    static var quizWhatAreOptionsO3: String { s.quizWhatAreOptionsO3 }
    static var quizWhatAreOptionsO4: String { s.quizWhatAreOptionsO4 }
    static var quizWhatAreOptionsExp: String { s.quizWhatAreOptionsExp }

    // Quiz 2: Call Options (CE)
    static var quizCallOptionsQ: String { s.quizCallOptionsQ }
    static var quizCallOptionsO1: String { s.quizCallOptionsO1 }
    static var quizCallOptionsO2: String { s.quizCallOptionsO2 }
    static var quizCallOptionsO3: String { s.quizCallOptionsO3 }
    static var quizCallOptionsO4: String { s.quizCallOptionsO4 }
    static var quizCallOptionsExp: String { s.quizCallOptionsExp }

    // Quiz 3: Put Options (PE)
    static var quizPutOptionsQ: String { s.quizPutOptionsQ }
    static var quizPutOptionsO1: String { s.quizPutOptionsO1 }
    static var quizPutOptionsO2: String { s.quizPutOptionsO2 }
    static var quizPutOptionsO3: String { s.quizPutOptionsO3 }
    static var quizPutOptionsO4: String { s.quizPutOptionsO4 }
    static var quizPutOptionsExp: String { s.quizPutOptionsExp }

    // Quiz 4: ITM, OTM & ATM
    static var quizItmOtmAtmQ: String { s.quizItmOtmAtmQ }
    static var quizItmOtmAtmO1: String { s.quizItmOtmAtmO1 }
    static var quizItmOtmAtmO2: String { s.quizItmOtmAtmO2 }
    static var quizItmOtmAtmO3: String { s.quizItmOtmAtmO3 }
    static var quizItmOtmAtmO4: String { s.quizItmOtmAtmO4 }
    static var quizItmOtmAtmExp: String { s.quizItmOtmAtmExp }
}
