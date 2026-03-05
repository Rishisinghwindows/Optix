import Foundation

struct BengaliStrings: LocalizedStrings {

    // MARK: - Tab Bar
    var tabOptionChain: String { "অপশন চেইন" }
    var tabCalculator: String { "ক্যালকুলেটর" }
    var tabAIInsights: String { "AI ইনসাইটস" }
    var tabCharts: String { "চার্টস" }
    var tabQuiz: String { "কুইজ" }
    var tabSettings: String { "সেটিংস" }

    // MARK: - Splash
    var splashTitle: String { "OPTIONS TRADING" }
    var splashSubtitle: String { "স্মার্ট ট্রেডিং এখান থেকে শুরু" }

    // MARK: - Onboarding
    var onboardingSkip: String { "এড়িয়ে যান" }
    var onboardingNext: String { "পরবর্তী" }
    var onboardingGetStarted: String { "শুরু করুন" }

    var onboarding1Title: String { "স্বাগতম\nOptix-এ" }
    var onboarding1Subtitle: String { "স্মার্ট অপশন ট্রেডিংয়ের জন্য আপনার প্রিমিয়াম সঙ্গী" }
    var onboarding1Feature1: String { "রিয়েল-টাইম NIFTY ও BANKNIFTY ট্র্যাকিং" }
    var onboarding1Feature2: String { "বিদ্যুৎ গতির অপশন গণনা" }
    var onboarding1Feature3: String { "ভারতীয় বাজারের জন্য তৈরি" }
    var onboarding1Feature4: String { "" }

    var onboarding2Title: String { "লাইভ অপশন চেইন" }
    var onboarding2Subtitle: String { "শীর্ষ ভারতীয় ব্রোকারদের থেকে রিয়েল-টাইম ডেটা" }
    var onboarding2Feature1: String { "Upstox, Zerodha এবং আরও থেকে লাইভ মূল্য" }
    var onboarding2Feature2: String { "NIFTY, BANKNIFTY, FINNIFTY, SENSEX" }
    var onboarding2Feature3: String { "এক ট্যাপে মেয়াদ পরিবর্তন করুন" }
    var onboarding2Feature4: String { "" }

    var onboarding3Title: String { "স্মার্ট ক্যালকুলেটর\nও গ্রিকস" }
    var onboarding3Subtitle: String { "Black-Scholes মূল্য নির্ধারণ সহজ করা হয়েছে" }
    var onboarding3Feature1: String { "তাত্ত্বিক অপশন মূল্য নির্ধারণ" }
    var onboarding3Feature2: String { "Delta, Gamma, Theta ও Vega" }
    var onboarding3Feature3: String { "ইমপ্লায়েড ভোলাটিলিটি ক্যালকুলেটর" }
    var onboarding3Feature4: String { "" }

    var onboarding4Title: String { "AI-চালিত\nইনসাইটস" }
    var onboarding4Subtitle: String { "মেশিন লার্নিং এবং অপশন ট্রেডিংয়ের মিলন" }
    var onboarding4Feature1: String { "স্মার্ট স্ট্রাইক নির্বাচন" }
    var onboarding4Feature2: String { "AI-চালিত বাজার বিশ্লেষণ" }
    var onboarding4Feature3: String { "পূর্বাভাসিত লক্ষ্য ও স্টপ-লস" }
    var onboarding4Feature4: String { "" }

    // MARK: - Calculator
    var calculatorTitle: String { "অপশন ক্যালকুলেটর" }
    var calculatorParameters: String { "প্যারামিটার" }
    var calculatorSpotPrice: String { "স্পট মূল্য" }
    var calculatorStrikePrice: String { "স্ট্রাইক মূল্য" }
    var calculatorDaysToExpiry: String { "মেয়াদ শেষের দিন" }
    var calculatorIVPercent: String { "IV (%)" }
    var calculatorRiskFreeRate: String { "ঝুঁকিমুক্ত সুদের হার" }
    var calculatorOptionType: String { "অপশনের ধরন" }
    var calculatorCall: String { "কল" }
    var calculatorPut: String { "পুট" }
    var calculatorCalculate: String { "গণনা করুন" }
    var calculatorReset: String { "রিসেট" }
    var calculatorResults: String { "ফলাফল" }
    var calculatorTheoreticalPrice: String { "তাত্ত্বিক মূল্য" }
    var calculatorIntrinsicValue: String { "অভ্যন্তরীণ মূল্য" }
    var calculatorTimeValue: String { "সময় মূল্য" }
    var calculatorBreakeven: String { "ব্রেকইভেন" }
    var calculatorMoneyness: String { "মানিনেস" }
    var calculatorTheGreeks: String { "অপশন গ্রিকস" }
    var calculatorQuickReference: String { "দ্রুত রেফারেন্স" }
    var calculatorRiskDisclaimer: String { "গুরুত্বপূর্ণ বিজ্ঞপ্তি" }
    var calculatorRiskDisclaimerText: String { "তাত্ত্বিক মূল্য। IV পরিবর্তন, তারল্য এবং বাজারের ব্যবধানের কারণে প্রকৃত বাজার মূল্য ভিন্ন হতে পারে। এটি বিনিয়োগ পরামর্শ নয়।" }
    var calculatorIVEducationTitle: String { "ইমপ্লায়েড ভোলাটিলিটি (IV)" }
    var calculatorIVEducationText: String { "সবচেয়ে শক্তিশালী ভ্যারিয়েবল" }
    var calculatorLearnAboutIV: String { "IV সম্পর্কে জানুন" }
    var calculatorWhatIsIV: String { "ইমপ্লায়েড ভোলাটিলিটি কী?" }
    var calculatorWhatIsIVText: String { "IV ভবিষ্যৎ মূল্য পরিবর্তনের বাজারের প্রত্যাশা পরিমাপ করে। বেশি IV মানে বাজার বড় পরিবর্তনের আশা করছে।" }
    var calculatorIVRanges: String { "IV পরিসীমা" }
    var calculatorLowIV: String { "কম IV" }
    var calculatorLowIVDesc: String { "IV কম হলে অপশন তুলনামূলকভাবে সস্তা। IV বাড়লে আপনার অপশনের মূল্য বাড়তে পারে।" }
    var calculatorModerateIV: String { "মাঝারি IV" }
    var calculatorModerateIVDesc: String { "সুষম মূল্য সহ স্বাভাবিক বাজার পরিস্থিতি।" }
    var calculatorHighIV: String { "উচ্চ IV" }
    var calculatorHighIVDesc: String { "IV বেশি হলে (ইভেন্টের আগে) অপশন ব্যয়বহুল। ইভেন্টের পরে IV কমলে, বাজার আপনার দিকে গেলেও আপনার অপশনের মূল্য কমে।" }
    var calculatorVeryHighIV: String { "অত্যন্ত উচ্চ IV" }
    var calculatorVeryHighIVDesc: String { "চরম অস্থিরতা, সাধারণত বাজেট বা নির্বাচনের মতো বড় ইভেন্টের সময়।" }
    var calculatorIVImpact: String { "IV প্রভাব" }
    var calculatorIVImpactText: String { "ইভেন্টের পরে (বাজেট, RBI নীতি) IV ক্র্যাশ সঠিক দিক থাকলেও ক্ষতি করতে পারে।" }
    var calculatorGotIt: String { "বুঝেছি" }
    var calculatorEnterSpotPrice: String { "স্পট মূল্য লিখুন" }
    var calculatorEnterStrikePrice: String { "স্ট্রাইক মূল্য লিখুন" }
    var calculatorEnterDays: String { "দিন লিখুন" }
    var calculatorEnterIV: String { "IV লিখুন" }
    var calculatorEnterRate: String { "হার লিখুন" }

    // MARK: - Greeks
    var greeksDelta: String { "Delta" }
    var greeksGamma: String { "Gamma" }
    var greeksTheta: String { "Theta" }
    var greeksVega: String { "Vega" }
    var greeksRho: String { "Rho" }
    var greeksDeltaDesc: String { "মূল্য সংবেদনশীলতা" }
    var greeksGammaDesc: String { "Delta পরিবর্তনের হার" }
    var greeksThetaDesc: String { "সময় ক্ষয়/দিন" }
    var greeksVegaDesc: String { "IV সংবেদনশীলতা" }
    var greeksRhoDesc: String { "সুদের হারের সংবেদনশীলতা" }
    var greeksDeltaInterpDeepITM: String { "গভীর ITM - আন্ডারলাইংয়ের সাথে প্রায় ১:১ চলে" }
    var greeksDeltaInterpATM: String { "ATM - ITM-এ মেয়াদ শেষ হওয়ার ৫০% সম্ভাবনা" }
    var greeksDeltaInterpDeepOTM: String { "গভীর OTM - লাভের সম্ভাবনা কম" }
    var greeksDeltaInterpModerate: String { "আন্ডারলাইং মুভমেন্টে মাঝারি সংবেদনশীলতা" }
    var greeksThetaInterpHigh: String { "উচ্চ সময় ক্ষয় - দ্রুত মূল্য হারাচ্ছে" }
    var greeksThetaInterpModerate: String { "মাঝারি সময় ক্ষয়" }
    var greeksThetaInterpLow: String { "কম সময় ক্ষয়" }
    var greeksVegaInterpHigh: String { "উচ্চ অস্থিরতা সংবেদনশীলতা" }
    var greeksVegaInterpModerate: String { "মাঝারি অস্থিরতা সংবেদনশীলতা" }
    var greeksVegaInterpLow: String { "কম অস্থিরতা সংবেদনশীলতা" }
    var greeksQuickRefDeltaCall: String { "আন্ডারলাইংয়ে ১-পয়েন্ট মুভে মূল্য পরিবর্তন" }
    var greeksQuickRefDeltaPut: String { "১-পয়েন্ট মুভে মূল্য পরিবর্তন (পুটের জন্য ঋণাত্মক)" }
    var greeksQuickRefGamma: String { "১-পয়েন্ট মুভে Delta-র পরিবর্তন" }
    var greeksQuickRefTheta: String { "দৈনিক সময় ক্ষয় (ঋণাত্মক = মূল্য হারাচ্ছে)" }
    var greeksQuickRefVega: String { "IV-তে ১% পরিবর্তনে মূল্য পরিবর্তন" }
    var greeksNearExpiryWarning: String { "মেয়াদ শেষের সতর্কতা" }
    var greeksNearExpiryText: String { "মেয়াদ শেষের কাছাকাছি (< ৩ দিন): Gamma বিস্ফোরিত হয়, Theta ত্বরান্বিত হয়, Black-Scholes ইন্ট্রাডেতে কম নির্ভরযোগ্য হয়ে পড়ে।" }
    var greeksInterpretation: String { "ব্যাখ্যা" }
    var greeksPerDay: String { "প্রতিদিন" }
    var greeksPer1PercentIV: String { "প্রতি ১% IV পরিবর্তনে" }
    var greeksPer1PercentRate: String { "প্রতি ১% হার পরিবর্তনে" }

    // MARK: - Option Chain
    var optionChainTitle: String { "অপশন চেইন" }
    var optionChainExpiry: String { "মেয়াদ" }
    var optionChainSpotPrice: String { "স্পট মূল্য" }
    var optionChainChange: String { "পরিবর্তন" }
    var optionChainCalls: String { "কলস" }
    var optionChainPuts: String { "পুটস" }
    var optionChainStrike: String { "স্ট্রাইক" }
    var optionChainLTP: String { "LTP" }
    var optionChainOI: String { "OI" }
    var optionChainVolume: String { "ভলিউম" }
    var optionChainIV: String { "IV" }
    var optionChainBidAsk: String { "বিড/আস্ক" }
    var optionChainDayHigh: String { "দিনের সর্বোচ্চ" }
    var optionChainDayLow: String { "দিনের সর্বনিম্ন" }
    var optionChainATM: String { "ATM" }
    var optionChainITM: String { "ITM" }
    var optionChainOTM: String { "OTM" }
    var optionChainPCR: String { "PCR" }
    var optionChainMaxPain: String { "ম্যাক্স পেইন" }
    var optionChainTotalCallOI: String { "মোট কল OI" }
    var optionChainTotalPutOI: String { "মোট পুট OI" }
    var optionChainDemoDataNote: String { "ডেমো ডেটা দেখানো হচ্ছে। লাইভ ডেটার জন্য একটি ব্রোকারের সাথে সংযোগ করুন।" }
    var optionChainNoData: String { "কোনো ডেটা উপলব্ধ নেই" }
    var optionChainLoading: String { "অপশন চেইন লোড হচ্ছে..." }
    var optionChainRefresh: String { "রিফ্রেশ" }
    var optionChainLastUpdated: String { "সর্বশেষ আপডেট" }
    var optionChainStrikesNearATM: String { "ATM-এর কাছাকাছি স্ট্রাইক" }
    var optionChainAllStrikes: String { "সমস্ত স্ট্রাইক" }
    var optionChainSelectExpiry: String { "মেয়াদ নির্বাচন করুন" }

    // Day names
    var dayMon: String { "সোম" }
    var dayTue: String { "মঙ্গল" }
    var dayWed: String { "বুধ" }
    var dayThu: String { "বৃহ" }
    var dayFri: String { "শুক্র" }
    var daySat: String { "শনি" }
    var daySun: String { "রবি" }

    // MARK: - Settings
    var settingsTitle: String { "সেটিংস" }
    var settingsLanguage: String { "ভাষা" }
    var settingsLanguageSubtitle: String { "আপনার পছন্দের ভাষা নির্বাচন করুন" }
    var settingsTheme: String { "থিম" }
    var settingsThemeSubtitle: String { "আপনার পছন্দের থিম নির্বাচন করুন" }
    var settingsDataSource: String { "ডেটা সোর্স" }
    var settingsDataSourceSubtitle: String { "আপনার ডেটা প্রদানকারী নির্বাচন করুন" }
    var settingsAbout: String { "সম্পর্কে" }
    var settingsVersion: String { "সংস্করণ" }
    var settingsRateApp: String { "অ্যাপ রেট করুন" }
    var settingsShareApp: String { "অ্যাপ শেয়ার করুন" }
    var settingsPrivacyPolicy: String { "গোপনীয়তা নীতি" }
    var settingsDisclaimer: String { "দাবিত্যাগ" }
    var settingsDisclaimerText: String { "এই অ্যাপটি শুধুমাত্র শিক্ষামূলক এবং বিশ্লেষণমূলক উদ্দেশ্যে Black-Scholes মডেল ব্যবহার করে তাত্ত্বিক অপশন মূল্য প্রদান করে।" }
    var settingsDisplay: String { "ডিসপ্লে" }
    var settingsGeneral: String { "সাধারণ" }
    var settingsDeveloper: String { "ডেভেলপার" }
    var settingsDeveloperName: String { "Rishi" }
    var settingsAppDescription: String { "Optix" }
    var settingsAllRightsReserved: String { "সর্বস্বত্ব সংরক্ষিত" }

    // MARK: - AI Insights
    var aiInsightsTitle: String { "AI ইনসাইটস" }
    var aiInsightsSelectIndex: String { "সূচক নির্বাচন করুন" }
    var aiInsightsAnalyze: String { "বিশ্লেষণ করুন" }
    var aiInsightsAnalyzing: String { "বিশ্লেষণ হচ্ছে..." }
    var aiInsightsUpdated: String { "আপডেট হয়েছে" }
    var aiInsightsNotAnalyzed: String { "বিশ্লেষণ হয়নি" }
    var aiInsightsNoData: String { "কোনো অপশন চেইন ডেটা উপলব্ধ নেই" }
    var aiInsightsCallsTab: String { "কলস" }
    var aiInsightsPutsTab: String { "পুটস" }
    var aiInsightsMarketTab: String { "বাজার" }
    var aiInsightsConfidence: String { "আস্থা" }
    var aiInsightsMarketBias: String { "বাজার প্রবণতা" }
    var aiInsightsPrediction: String { "পূর্বাভাস" }
    var aiInsightsDirection: String { "দিক" }
    var aiInsightsEntry: String { "এন্ট্রি" }
    var aiInsightsTarget: String { "লক্ষ্য" }
    var aiInsightsStopLoss: String { "স্টপ লস" }
    var aiInsightsExpectedMove: String { "প্রত্যাশিত মুভ" }
    var aiInsightsRiskReward: String { "ঝুঁকি:পুরস্কার" }
    var aiInsightsRationale: String { "যুক্তি" }
    var aiInsightsKeyLevels: String { "মূল স্তর" }
    var aiInsightsSupport: String { "সাপোর্ট" }
    var aiInsightsResistance: String { "রেজিস্ট্যান্স" }
    var aiInsightsAnalyzeStrikes: String { "স্ট্রাইক বিশ্লেষণ করুন" }
    var aiInsightsSelectStrike: String { "স্ট্রাইক নির্বাচন করুন" }
    var aiInsightsStrikeAnalysis: String { "স্ট্রাইক বিশ্লেষণ" }
    var aiInsightsOverallSentiment: String { "সামগ্রিক মনোভাব" }
    var aiInsightsOIAnalysis: String { "OI বিশ্লেষণ" }
    var aiInsightsIVAnalysis: String { "IV বিশ্লেষণ" }
    var aiInsightsRecommendation: String { "সুপারিশ" }
    var aiInsightsBullish: String { "বুলিশ" }
    var aiInsightsBearish: String { "বিয়ারিশ" }
    var aiInsightsNeutral: String { "নিরপেক্ষ" }
    var aiInsightsStrongBuy: String { "দৃঢ় কেনা" }
    var aiInsightsBuy: String { "কেনা" }
    var aiInsightsHold: String { "ধরে রাখুন" }
    var aiInsightsSell: String { "বিক্রি" }
    var aiInsightsStrongSell: String { "দৃঢ় বিক্রি" }
    var aiInsightsHigh: String { "উচ্চ" }
    var aiInsightsMedium: String { "মাঝারি" }
    var aiInsightsLow: String { "নিম্ন" }
    var aiInsightsLong: String { "লং" }
    var aiInsightsShort: String { "শর্ট" }
    var aiInsightsAvoid: String { "এড়িয়ে চলুন" }

    // MARK: - Charts
    var chartsIndexChart: String { "চার্ট" }
    var chartsATMOptions: String { "ATM অপশন" }
    var chartsMarketOverview: String { "বাজার সারসংক্ষেপ" }
    var chartsPutCallRatio: String { "পুট-কল অনুপাত" }
    var chartsMaxPain: String { "ম্যাক্স পেইন" }
    var chartsTotalCallOI: String { "মোট কল OI" }
    var chartsTotalPutOI: String { "মোট পুট OI" }
    var chartsBullish: String { "বুলিশ" }
    var chartsBearish: String { "বিয়ারিশ" }
    var chartsAboveSpot: String { "স্পটের উপরে" }
    var chartsBelowSpot: String { "স্পটের নিচে" }
    var chartsLoadingChart: String { "চার্ট ডেটা লোড হচ্ছে..." }
    var chartsLoadingOption: String { "অপশন ডেটা লোড হচ্ছে..." }
    var chartsPrice: String { "মূল্য" }
    var chartsHigh: String { "সর্বোচ্চ" }
    var chartsLow: String { "সর্বনিম্ন" }
    var chartsAvgOI: String { "গড় OI" }
    var chartsAvgIV: String { "গড় IV" }

    // MARK: - Upstox Login
    var upstoxConnectTitle: String { "Upstox-এ সংযোগ করুন" }
    var upstoxConnectSubtitle: String { "লাইভ অপশন চেইন ডেটা পেতে আপনার Upstox অ্যাকাউন্ট দিয়ে লগইন করুন" }
    var upstoxLiveData: String { "লাইভ ডেটা" }
    var upstoxLiveDataDesc: String { "NSE থেকে রিয়েল-টাইম অপশন চেইন" }
    var upstoxAccurateGreeks: String { "সঠিক গ্রিকস" }
    var upstoxAccurateGreeksDesc: String { "এক্সচেঞ্জ থেকে Delta, Gamma, Theta, Vega" }
    var upstoxAutoRefresh: String { "অটো রিফ্রেশ" }
    var upstoxAutoRefreshDesc: String { "প্রতি কয়েক সেকেন্ডে ডেটা আপডেট হয়" }
    var upstoxConnecting: String { "সংযোগ হচ্ছে..." }
    var upstoxLoginButton: String { "Upstox দিয়ে লগইন করুন" }
    var upstoxContinueDemo: String { "ডেমো ডেটা দিয়ে চালিয়ে যান" }
    var upstoxConnected: String { "সংযুক্ত" }
    var upstoxNotConnected: String { "সংযুক্ত নয়" }
    var upstoxDataProviders: String { "ডেটা প্রদানকারী" }
    var upstoxRealTimeStream: String { "রিয়েল-টাইম স্ট্রিম" }
    var upstoxRefreshData: String { "ডেটা রিফ্রেশ করুন" }
    var upstoxDisconnect: String { "সংযোগ বিচ্ছিন্ন করুন" }
    var upstoxUseDemoData: String { "ডেমো ডেটা ব্যবহার করুন" }
    var upstoxDataSource: String { "ডেটা সোর্স" }
    var upstoxDemoData: String { "ডেমো ডেটা" }
    var upstoxLive: String { "Upstox লাইভ" }

    // MARK: - Common
    var commonDone: String { "সম্পন্ন" }
    var commonCancel: String { "বাতিল" }
    var commonClose: String { "বন্ধ করুন" }
    var commonError: String { "ত্রুটি" }
    var commonRetry: String { "পুনরায় চেষ্টা করুন" }
    var commonOK: String { "ঠিক আছে" }
    var commonYes: String { "হ্যাঁ" }
    var commonNo: String { "না" }
    var commonSave: String { "সংরক্ষণ করুন" }
    var commonDelete: String { "মুছুন" }
    var commonEdit: String { "সম্পাদনা" }
    var commonShare: String { "শেয়ার করুন" }
    var commonCopy: String { "কপি করুন" }
    var commonSearch: String { "অনুসন্ধান" }

    // MARK: - Moneyness
    var moneynessITM: String { "ইন দ্য মানি" }
    var moneynessATM: String { "অ্যাট দ্য মানি" }
    var moneynessOTM: String { "আউট অফ দ্য মানি" }
    var moneynessDeepITM: String { "গভীর ITM" }
    var moneynessDeepOTM: String { "গভীর OTM" }

    // MARK: - Theme Names
    var themeClassicGreen: String { "ক্লাসিক সবুজ" }
    var themeOceanBlue: String { "সমুদ্র নীল" }
    var themeMidnightPurple: String { "মধ্যরাত বেগুনি" }
    var themeSunsetOrange: String { "সূর্যাস্ত কমলা" }
    var themeCyberpunk: String { "সাইবারপাঙ্ক" }
    var themeClassicGreenDesc: String { "ঐতিহ্যবাহী ট্রেডিং টার্মিনাল" }
    var themeOceanBlueDesc: String { "শীতল সমুদ্রের আবহ" }
    var themeMidnightPurpleDesc: String { "গভীর বেগুনি নন্দনতত্ত্ব" }
    var themeSunsetOrangeDesc: String { "উষ্ণ সূর্যাস্তের রং" }
    var themeCyberpunkDesc: String { "ভবিষ্যৎমুখী নিয়ন স্টাইল" }

    // MARK: - Education Quiz UI
    var educationQuiz: String { "কুইজ" }
    var educationTestKnowledge: String { "আপনার জ্ঞান পরীক্ষা করুন" }
    var educationHasQuiz: String { "কুইজ আছে" }
    var educationAvailableQuizzes: String { "উপলব্ধ কুইজ" }
    var educationCompleted: String { "সম্পন্ন" }
    var educationAvailable: String { "উপলব্ধ" }
    var educationAvgScore: String { "গড় স্কোর" }
    var educationCorrect: String { "সঠিক" }

    // MARK: - Quiz Questions

    // Quiz Titles
    var quizTitleWhatAreOptions: String { "অপশন কী?" }
    var quizTitleCallOptions: String { "কল অপশন (CE)" }
    var quizTitlePutOptions: String { "পুট অপশন (PE)" }
    var quizTitleItmOtmAtm: String { "ITM, OTM এবং ATM" }

    // Quiz 1: What are Options?
    var quizWhatAreOptionsQ: String { "অপশন আপনাকে কী দেয়?" }
    var quizWhatAreOptionsO1: String { "কেনা/বেচার বাধ্যবাধকতা" }
    var quizWhatAreOptionsO2: String { "কেনা/বেচার অধিকার" }
    var quizWhatAreOptionsO3: String { "গ্যারান্টিযুক্ত লাভ" }
    var quizWhatAreOptionsO4: String { "বিনামূল্যে শেয়ার" }
    var quizWhatAreOptionsExp: String { "অপশন আপনাকে একটি পূর্বনির্ধারিত মূল্যে সম্পদ কেনা বা বিক্রি করার অধিকার (বাধ্যবাধকতা নয়) দেয়।" }

    // Quiz 2: Call Options (CE)
    var quizCallOptionsQ: String { "আপনার কল অপশন কখন কেনা উচিত?" }
    var quizCallOptionsO1: String { "যখন আপনি দাম কমার আশা করেন" }
    var quizCallOptionsO2: String { "যখন আপনি দাম বাড়ার আশা করেন" }
    var quizCallOptionsO3: String { "যখন আপনি শেয়ার বিক্রি করতে চান" }
    var quizCallOptionsO4: String { "যখন IV খুব বেশি থাকে" }
    var quizCallOptionsExp: String { "আপনি যখন অন্তর্নিহিত মূল্য বাড়বে বলে আশা করেন তখন কল অপশন কেনেন। কলগুলি ঊর্ধ্বমুখী মূল্য গতিবিধি থেকে লাভ করে।" }

    // Quiz 3: Put Options (PE)
    var quizPutOptionsQ: String { "পুট কেনার সময় সর্বাধিক ক্ষতি কত?" }
    var quizPutOptionsO1: String { "সীমাহীন" }
    var quizPutOptionsO2: String { "স্ট্রাইক প্রাইস" }
    var quizPutOptionsO3: String { "প্রদত্ত প্রিমিয়াম" }
    var quizPutOptionsO4: String { "শূন্য" }
    var quizPutOptionsExp: String { "যেকোনো অপশন (কল বা পুট) কেনার সময়, আপনার সর্বাধিক ক্ষতি আপনি অগ্রিম প্রদত্ত প্রিমিয়ামে সীমাবদ্ধ।" }

    // Quiz 4: ITM, OTM & ATM
    var quizItmOtmAtmQ: String { "যদি NIFTY 25,000-এ থাকে, কোন কল অপশন ITM?" }
    var quizItmOtmAtmO1: String { "25,200 CE" }
    var quizItmOtmAtmO2: String { "25,000 CE" }
    var quizItmOtmAtmO3: String { "24,800 CE" }
    var quizItmOtmAtmO4: String { "25,500 CE" }
    var quizItmOtmAtmExp: String { "Spot > Strike হলে কল ITM হয়। যেহেতু NIFTY 25,000-এ আছে, 24,800 CE হল ITM কারণ 25,000 > 24,800।" }
}
