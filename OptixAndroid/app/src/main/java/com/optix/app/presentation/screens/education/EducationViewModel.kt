package com.optix.app.presentation.screens.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the Education screen
 */
data class EducationUiState(
    val selectedCategory: EducationCategory = EducationCategory.BASICS,
    val lessons: List<Lesson> = emptyList(),
    val greeks: List<GreekInfo> = emptyList(),
    val strategies: List<OptionStrategy> = emptyList(),
    val progress: EducationProgress = EducationProgress(),
    val selectedLesson: Lesson? = null,
    val selectedGreek: GreekInfo? = null,
    val selectedStrategy: OptionStrategy? = null,
    val isQuizActive: Boolean = false,
    val currentQuizQuestions: List<QuizQuestion> = emptyList(),
    val currentQuizIndex: Int = 0,
    val quizAnswers: Map<Int, Int> = emptyMap(),
    val showQuizResults: Boolean = false,
    val isLoading: Boolean = false
) {
    val completedLessonsCount: Int
        get() = progress.completedLessons.size

    val totalLessonsCount: Int
        get() = lessons.size

    val progressPercentage: Float
        get() = if (totalLessonsCount > 0) (completedLessonsCount.toFloat() / totalLessonsCount) * 100 else 0f

    val filteredLessons: List<Lesson>
        get() = lessons.filter { it.category == selectedCategory }

    val quizScore: Int
        get() {
            var score = 0
            quizAnswers.forEach { (index, answer) ->
                if (index < currentQuizQuestions.size && currentQuizQuestions[index].correctIndex == answer) {
                    score++
                }
            }
            return score
        }
}

@HiltViewModel
class EducationViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(EducationUiState())
    val state: StateFlow<EducationUiState> = _state.asStateFlow()

    init {
        loadEducationContent()
    }

    fun selectCategory(category: EducationCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun selectLesson(lesson: Lesson?) {
        _state.update { it.copy(selectedLesson = lesson) }
    }

    fun selectGreek(greek: GreekInfo?) {
        _state.update { it.copy(selectedGreek = greek) }
    }

    fun selectStrategy(strategy: OptionStrategy?) {
        _state.update { it.copy(selectedStrategy = strategy) }
    }

    fun markLessonCompleted(lessonId: String) {
        _state.update { state ->
            val newCompletedLessons = state.progress.completedLessons + lessonId
            state.copy(
                progress = state.progress.copy(completedLessons = newCompletedLessons)
            )
        }
    }

    fun startQuiz(questions: List<QuizQuestion>) {
        _state.update {
            it.copy(
                isQuizActive = true,
                currentQuizQuestions = questions,
                currentQuizIndex = 0,
                quizAnswers = emptyMap(),
                showQuizResults = false
            )
        }
    }

    fun answerQuestion(answerIndex: Int) {
        _state.update { state ->
            val newAnswers = state.quizAnswers + (state.currentQuizIndex to answerIndex)
            state.copy(quizAnswers = newAnswers)
        }
    }

    fun nextQuestion() {
        _state.update { state ->
            if (state.currentQuizIndex < state.currentQuizQuestions.size - 1) {
                state.copy(currentQuizIndex = state.currentQuizIndex + 1)
            } else {
                state.copy(showQuizResults = true)
            }
        }
    }

    fun finishQuiz() {
        val currentState = _state.value
        val lessonId = currentState.selectedLesson?.id
        if (lessonId != null) {
            val result = QuizResult(
                lessonId = lessonId,
                score = currentState.quizScore,
                totalQuestions = currentState.currentQuizQuestions.size
            )
            _state.update { state ->
                val newQuizResults = state.progress.quizResults + (lessonId to result)
                state.copy(
                    isQuizActive = false,
                    showQuizResults = false,
                    currentQuizQuestions = emptyList(),
                    quizAnswers = emptyMap(),
                    progress = state.progress.copy(quizResults = newQuizResults)
                )
            }
        } else {
            _state.update {
                it.copy(
                    isQuizActive = false,
                    showQuizResults = false,
                    currentQuizQuestions = emptyList(),
                    quizAnswers = emptyMap()
                )
            }
        }
    }

    fun resetQuiz() {
        _state.update {
            it.copy(
                currentQuizIndex = 0,
                quizAnswers = emptyMap(),
                showQuizResults = false
            )
        }
    }

    private fun loadEducationContent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val lessons = createLessons()
            val greeks = createGreeksInfo()
            val strategies = createStrategies()

            _state.update {
                it.copy(
                    lessons = lessons,
                    greeks = greeks,
                    strategies = strategies,
                    isLoading = false
                )
            }
        }
    }

    private fun createLessons(): List<Lesson> = listOf(
        // BASICS
        Lesson(
            title = "What are Options?",
            subtitle = "Introduction to options trading",
            description = "Learn the fundamentals of options contracts",
            category = EducationCategory.BASICS,
            difficulty = LessonDifficulty.BEGINNER,
            duration = "8 min",
            icon = "book",
            keyPoints = listOf(
                "Options give the right, not obligation, to buy or sell",
                "Call options: Right to BUY at strike price",
                "Put options: Right to SELL at strike price",
                "Premium is the price paid for the option"
            ),
            content = listOf(
                LessonSection(
                    title = "What is an Option?",
                    content = "An option is a financial contract that gives the buyer the right, but not the obligation, to buy or sell an underlying asset at a predetermined price (strike price) within a specific time period (until expiry).",
                    example = LessonExample(
                        title = "Simple Analogy",
                        scenario = "Think of it like booking a hotel room. You pay a small fee to reserve it, but you can cancel if plans change.",
                        result = "Similarly, options let you 'reserve' a price, paying only the premium."
                    )
                ),
                LessonSection(
                    title = "Call vs Put Options",
                    content = "There are two types of options:\n\n" +
                            "CALL Option: Gives you the right to BUY the underlying asset at the strike price. You profit when the market goes UP.\n\n" +
                            "PUT Option: Gives you the right to SELL the underlying asset at the strike price. You profit when the market goes DOWN.",
                    example = LessonExample(
                        title = "NIFTY Example",
                        scenario = "NIFTY is at 22,000. You buy a 22,500 Call option.",
                        calculation = "Strike: 22,500 | Premium: Rs 150 | Lot Size: 50",
                        result = "If NIFTY rises to 23,000, your Call is worth Rs 500+, giving you a profit!"
                    )
                ),
                LessonSection(
                    title = "Why Trade Options?",
                    content = "Options offer several advantages:\n\n" +
                            "1. Leverage: Control large positions with less capital\n" +
                            "2. Limited Risk: For buyers, max loss is the premium paid\n" +
                            "3. Hedging: Protect existing positions\n" +
                            "4. Flexibility: Profit in any market direction",
                    tip = "Start with buying options to limit your risk while learning. Selling options requires more capital and experience."
                )
            ),
            quiz = listOf(
                QuizQuestion(
                    question = "What does a Call option give you the right to do?",
                    options = listOf("Sell the asset", "Buy the asset", "Hold the asset", "Ignore the asset"),
                    correctIndex = 1,
                    explanation = "A Call option gives you the right to BUY the underlying asset at the strike price."
                ),
                QuizQuestion(
                    question = "What is the maximum loss for an option buyer?",
                    options = listOf("Unlimited", "Strike price", "Premium paid", "Spot price"),
                    correctIndex = 2,
                    explanation = "For option buyers, the maximum loss is limited to the premium paid."
                ),
                QuizQuestion(
                    question = "When does a Put option profit?",
                    options = listOf("Market goes up", "Market goes down", "Market stays flat", "Never"),
                    correctIndex = 1,
                    explanation = "Put options profit when the market goes DOWN below the strike price."
                )
            )
        ),
        Lesson(
            title = "Strike Price & Premium",
            subtitle = "Core option pricing concepts",
            description = "Understanding the key components of option contracts",
            category = EducationCategory.BASICS,
            difficulty = LessonDifficulty.BEGINNER,
            duration = "6 min",
            icon = "price",
            keyPoints = listOf(
                "Strike price is the exercise price of the option",
                "Premium = Intrinsic Value + Time Value",
                "ITM options have intrinsic value",
                "OTM options have only time value"
            ),
            content = listOf(
                LessonSection(
                    title = "Strike Price Explained",
                    content = "The strike price (or exercise price) is the predetermined price at which the option can be exercised. It's the price at which you can buy (for Calls) or sell (for Puts) the underlying asset.\n\nFor NIFTY options, strikes are available at intervals of 50 points (e.g., 22,000, 22,050, 22,100)."
                ),
                LessonSection(
                    title = "Option Premium",
                    content = "The premium is the price you pay to buy an option. It consists of two components:\n\n" +
                            "1. Intrinsic Value: The amount by which an option is in-the-money (ITM)\n\n" +
                            "2. Time Value: The extra amount traders pay for time remaining until expiry",
                    example = LessonExample(
                        title = "Premium Breakdown",
                        scenario = "NIFTY at 22,500. You see 22,300 CE trading at Rs 250.",
                        calculation = "Intrinsic Value = 22,500 - 22,300 = Rs 200\nTime Value = 250 - 200 = Rs 50\nTotal Premium = Rs 250",
                        result = "Rs 200 of the premium is intrinsic (real) value, Rs 50 is time value."
                    )
                ),
                LessonSection(
                    title = "Lot Size & Contract Value",
                    content = "Options trade in lots. For NIFTY, 1 lot = 50 units.\n\nTo calculate total investment:\nTotal Cost = Premium x Lot Size",
                    example = LessonExample(
                        title = "Cost Calculation",
                        scenario = "Buying NIFTY 22,500 CE at Rs 150",
                        calculation = "Premium = Rs 150\nLot Size = 50\nTotal = 150 x 50 = Rs 7,500",
                        result = "You need Rs 7,500 to buy 1 lot of this option."
                    ),
                    tip = "Always check the lot size before trading. It varies by underlying and can change over time."
                )
            ),
            quiz = listOf(
                QuizQuestion(
                    question = "What is the strike price?",
                    options = listOf("Current market price", "Predetermined exercise price", "Premium paid", "Lot size"),
                    correctIndex = 1,
                    explanation = "Strike price is the predetermined price at which the option can be exercised."
                ),
                QuizQuestion(
                    question = "An option trading at Rs 200 with Rs 150 intrinsic value has time value of:",
                    options = listOf("Rs 200", "Rs 150", "Rs 50", "Rs 350"),
                    correctIndex = 2,
                    explanation = "Time Value = Premium - Intrinsic Value = 200 - 150 = Rs 50"
                )
            )
        ),
        Lesson(
            title = "ITM, ATM & OTM",
            subtitle = "Understanding option moneyness",
            description = "Learn how strike prices relate to market price",
            category = EducationCategory.BASICS,
            difficulty = LessonDifficulty.BEGINNER,
            duration = "5 min",
            icon = "money",
            keyPoints = listOf(
                "ITM: Option has intrinsic value",
                "ATM: Strike equals market price",
                "OTM: Option has no intrinsic value",
                "Moneyness affects premium and Greeks"
            ),
            content = listOf(
                LessonSection(
                    title = "Moneyness Explained",
                    content = "Moneyness describes the relationship between an option's strike price and the current market price of the underlying asset.\n\n" +
                            "In-The-Money (ITM): Option has immediate exercise value\n" +
                            "At-The-Money (ATM): Strike is near current price\n" +
                            "Out-of-The-Money (OTM): Option has no immediate value"
                ),
                LessonSection(
                    title = "For Call Options",
                    content = "ITM Call: Strike < Spot Price (profitable to exercise)\n" +
                            "ATM Call: Strike = Spot Price\n" +
                            "OTM Call: Strike > Spot Price (not profitable to exercise)",
                    example = LessonExample(
                        title = "NIFTY at 22,500",
                        scenario = "Analyzing different Call strikes",
                        calculation = "22,000 CE: ITM (Strike 22,000 < Spot 22,500)\n22,500 CE: ATM (Strike = Spot)\n23,000 CE: OTM (Strike 23,000 > Spot 22,500)",
                        result = "22,000 CE has Rs 500 intrinsic value, 22,500 CE and 23,000 CE have none."
                    )
                ),
                LessonSection(
                    title = "For Put Options",
                    content = "ITM Put: Strike > Spot Price\n" +
                            "ATM Put: Strike = Spot Price\n" +
                            "OTM Put: Strike < Spot Price",
                    example = LessonExample(
                        title = "NIFTY at 22,500",
                        scenario = "Analyzing different Put strikes",
                        calculation = "23,000 PE: ITM (Strike 23,000 > Spot 22,500)\n22,500 PE: ATM (Strike = Spot)\n22,000 PE: OTM (Strike 22,000 < Spot 22,500)",
                        result = "23,000 PE has Rs 500 intrinsic value."
                    ),
                    tip = "ATM options have the highest time value and respond most dramatically to price changes."
                )
            ),
            quiz = listOf(
                QuizQuestion(
                    question = "If NIFTY is at 22,500, which Call option is ITM?",
                    options = listOf("23,000 CE", "22,500 CE", "22,000 CE", "All of them"),
                    correctIndex = 2,
                    explanation = "22,000 CE is ITM because Strike (22,000) < Spot (22,500)"
                ),
                QuizQuestion(
                    question = "Which option type typically has the highest time value?",
                    options = listOf("Deep ITM", "ATM", "Deep OTM", "All same"),
                    correctIndex = 1,
                    explanation = "ATM options have the highest time value due to maximum uncertainty about expiry outcome."
                )
            )
        ),
        Lesson(
            title = "Option Expiry & Time Decay",
            subtitle = "How time affects options",
            description = "Understanding expiration and time value erosion",
            category = EducationCategory.BASICS,
            difficulty = LessonDifficulty.BEGINNER,
            duration = "7 min",
            icon = "clock",
            keyPoints = listOf(
                "Options have an expiration date",
                "Time value decreases as expiry approaches",
                "Theta measures daily time decay",
                "Weekly options decay faster than monthly"
            ),
            content = listOf(
                LessonSection(
                    title = "Option Expiration",
                    content = "Every option has an expiration date. In India:\n\n" +
                            "Weekly Expiry: Every Thursday (for Index options)\n" +
                            "Monthly Expiry: Last Thursday of the month\n\n" +
                            "After expiry, options become worthless if OTM, or are settled at intrinsic value if ITM."
                ),
                LessonSection(
                    title = "Time Decay (Theta)",
                    content = "As time passes, the time value of an option decreases. This is called time decay or theta decay.\n\n" +
                            "Key Points:\n" +
                            "- Time decay accelerates near expiry\n" +
                            "- ATM options lose time value fastest\n" +
                            "- Option buyers lose to time decay\n" +
                            "- Option sellers benefit from time decay",
                    example = LessonExample(
                        title = "Time Decay Example",
                        scenario = "NIFTY 22,500 CE with 10 days to expiry trading at Rs 150",
                        calculation = "Theta = -10 means option loses Rs 10 per day\nDay 1: Rs 150\nDay 2: Rs 140\nDay 3: Rs 130\n(assuming no price change)",
                        result = "After 5 days, option might be worth only Rs 100 due to time decay alone."
                    )
                ),
                LessonSection(
                    title = "Weekly vs Monthly Options",
                    content = "Weekly options:\n- Expire every Thursday\n- Higher gamma, faster theta decay\n- Better for short-term trades\n- More volatile\n\nMonthly options:\n- More time for thesis to play out\n- Slower time decay\n- Better for swing trades",
                    tip = "Avoid holding OTM options into the last few days before expiry - time decay becomes brutal!"
                )
            ),
            quiz = listOf(
                QuizQuestion(
                    question = "What happens to time value as expiry approaches?",
                    options = listOf("Increases", "Decreases", "Stays same", "Becomes negative"),
                    correctIndex = 1,
                    explanation = "Time value decreases (decays) as expiry approaches, eventually reaching zero."
                ),
                QuizQuestion(
                    question = "Who benefits from time decay?",
                    options = listOf("Option buyers", "Option sellers", "Both equally", "Neither"),
                    correctIndex = 1,
                    explanation = "Option sellers benefit from time decay as the options they sold lose value over time."
                )
            )
        ),
        // ADVANCED
        Lesson(
            title = "Implied Volatility (IV)",
            subtitle = "The hidden variable in option pricing",
            description = "Master the most important factor in option trading",
            category = EducationCategory.ADVANCED,
            difficulty = LessonDifficulty.ADVANCED,
            duration = "12 min",
            icon = "chart",
            keyPoints = listOf(
                "IV reflects expected future volatility",
                "High IV = expensive options",
                "IV Crush happens after events",
                "IV Percentile helps gauge if IV is high or low"
            ),
            content = listOf(
                LessonSection(
                    title = "What is Implied Volatility?",
                    content = "Implied Volatility (IV) is the market's expectation of how much the underlying asset will move in the future. It's 'implied' from option prices using pricing models.\n\n" +
                            "Unlike historical volatility (actual past movement), IV is forward-looking and reflects uncertainty."
                ),
                LessonSection(
                    title = "IV and Option Prices",
                    content = "Higher IV = Higher option premiums\nLower IV = Lower option premiums\n\nThis applies to both calls and puts. When volatility is expected to be high (like before earnings or budget), options become more expensive.",
                    example = LessonExample(
                        title = "Pre-Budget IV Example",
                        scenario = "NIFTY 22,500 CE normally trades at Rs 150",
                        calculation = "Normal IV: 15%, Premium: Rs 150\nPre-Budget IV: 25%, Premium: Rs 280\nPost-Budget IV: 12%, Premium: Rs 100",
                        result = "Same option costs almost double before the event due to IV spike!"
                    )
                ),
                LessonSection(
                    title = "IV Crush",
                    content = "IV Crush is the sharp drop in IV after an anticipated event (earnings, elections, budget). Even if you predicted direction correctly, you can lose money if IV crushes hard enough.",
                    tip = "Before events with high IV, consider selling options (credit strategies) to benefit from IV crush. For directional bets, use spreads to reduce IV exposure."
                ),
                LessonSection(
                    title = "IV Percentile & IV Rank",
                    content = "IV Percentile: What percentage of days in the past year had lower IV than today?\n\n" +
                            "IV Rank: Where is current IV relative to its 52-week range?\n\n" +
                            "IV Percentile > 80%: IV is high, consider selling strategies\n" +
                            "IV Percentile < 20%: IV is low, consider buying strategies",
                    example = LessonExample(
                        title = "Using IV Percentile",
                        scenario = "NIFTY IV Percentile at 85%",
                        calculation = "Current IV is higher than 85% of days in past year",
                        result = "Options are expensive. Good time for Iron Condors or Credit Spreads."
                    )
                )
            ),
            quiz = listOf(
                QuizQuestion(
                    question = "What happens to option prices when IV increases?",
                    options = listOf("Decrease", "Stay same", "Increase", "Become zero"),
                    correctIndex = 2,
                    explanation = "Higher IV leads to higher option prices for both calls and puts."
                ),
                QuizQuestion(
                    question = "What is IV Crush?",
                    options = listOf("IV gradually increasing", "Sharp IV drop after an event", "IV becoming negative", "IV staying constant"),
                    correctIndex = 1,
                    explanation = "IV Crush is the sharp drop in implied volatility after an anticipated event."
                ),
                QuizQuestion(
                    question = "With IV Percentile at 90%, which strategy is preferable?",
                    options = listOf("Buying straddles", "Selling credit spreads", "Buying naked calls", "All are equally good"),
                    correctIndex = 1,
                    explanation = "High IV percentile means options are expensive, making selling strategies favorable."
                )
            )
        ),
        Lesson(
            title = "Open Interest Analysis",
            subtitle = "Reading market positioning",
            description = "Using OI to identify support and resistance",
            category = EducationCategory.ADVANCED,
            difficulty = LessonDifficulty.INTERMEDIATE,
            duration = "10 min",
            icon = "analytics",
            keyPoints = listOf(
                "OI represents total outstanding contracts",
                "High Put OI = Support level",
                "High Call OI = Resistance level",
                "OI changes reveal trader positioning"
            ),
            content = listOf(
                LessonSection(
                    title = "What is Open Interest?",
                    content = "Open Interest (OI) is the total number of outstanding option contracts that haven't been closed or exercised.\n\n" +
                            "When a new trade is made:\n- Buyer opens, Seller opens = OI increases\n- Buyer closes, Seller closes = OI decreases\n- One opens, one closes = OI unchanged"
                ),
                LessonSection(
                    title = "OI as Support/Resistance",
                    content = "Put OI Concentration: Writers have sold puts at this strike. They'll defend it (buy underlying if it approaches) = SUPPORT\n\n" +
                            "Call OI Concentration: Writers have sold calls at this strike. They'll defend it (sell underlying if it approaches) = RESISTANCE",
                    example = LessonExample(
                        title = "Max OI Analysis",
                        scenario = "NIFTY at 22,400. Highest Put OI at 22,000. Highest Call OI at 23,000.",
                        calculation = "Support Zone: Around 22,000 (Put writers defending)\nResistance Zone: Around 23,000 (Call writers defending)",
                        result = "Expected trading range: 22,000 - 23,000"
                    )
                ),
                LessonSection(
                    title = "OI Change Interpretation",
                    content = "Price UP + OI UP = Long Buildup (Bullish)\n" +
                            "Price DOWN + OI UP = Short Buildup (Bearish)\n" +
                            "Price UP + OI DOWN = Short Covering (Weak Bullish)\n" +
                            "Price DOWN + OI DOWN = Long Unwinding (Weak Bearish)",
                    tip = "Use OI analysis alongside price action. OI tells you about positioning, but price action confirms direction."
                )
            ),
            quiz = listOf(
                QuizQuestion(
                    question = "High Put OI at a strike indicates:",
                    options = listOf("Resistance level", "Support level", "Breakout point", "Random level"),
                    correctIndex = 1,
                    explanation = "High Put OI indicates put writers are defending that level, making it a support zone."
                ),
                QuizQuestion(
                    question = "Price going up with OI increasing indicates:",
                    options = listOf("Short covering", "Long unwinding", "Long buildup", "Short buildup"),
                    correctIndex = 2,
                    explanation = "Price UP + OI UP = Long Buildup, which is bullish."
                )
            )
        ),
        Lesson(
            title = "Put-Call Ratio (PCR)",
            subtitle = "Sentiment indicator for markets",
            description = "Using PCR for contrarian signals",
            category = EducationCategory.ADVANCED,
            difficulty = LessonDifficulty.INTERMEDIATE,
            duration = "8 min",
            icon = "sentiment",
            keyPoints = listOf(
                "PCR = Put OI / Call OI",
                "PCR > 1 = More puts (contrarian bullish)",
                "PCR < 0.7 = More calls (contrarian bearish)",
                "Works best at extremes"
            ),
            content = listOf(
                LessonSection(
                    title = "Understanding PCR",
                    content = "Put-Call Ratio measures the relative trading of puts vs calls. It can be calculated using:\n\n" +
                            "PCR (OI) = Total Put OI / Total Call OI\n" +
                            "PCR (Volume) = Put Volume / Call Volume\n\n" +
                            "OI-based PCR is more reliable for positioning analysis."
                ),
                LessonSection(
                    title = "Interpreting PCR",
                    content = "PCR > 1.2: Excessive fear, too many puts being bought (Contrarian BULLISH)\n\n" +
                            "PCR 0.8 - 1.0: Balanced, neutral sentiment\n\n" +
                            "PCR < 0.7: Excessive greed, too many calls being bought (Contrarian BEARISH)",
                    example = LessonExample(
                        title = "PCR at Extremes",
                        scenario = "PCR reaches 1.4 after a sharp market fall",
                        calculation = "Put OI = 14 lakhs, Call OI = 10 lakhs\nPCR = 14/10 = 1.4",
                        result = "Extreme fear! Market often reverses up from such levels."
                    )
                ),
                LessonSection(
                    title = "PCR Limitations",
                    content = "PCR is a sentiment tool, not a timing tool. High PCR can go higher!\n\nUse PCR with:\n- Price action confirmation\n- Support/resistance levels\n- Volume analysis",
                    tip = "Don't trade PCR alone. Use it to confirm other analysis. Extreme PCR + key support level = high probability trade."
                )
            ),
            quiz = listOf(
                QuizQuestion(
                    question = "PCR of 1.3 is considered:",
                    options = listOf("Bearish", "Neutral", "Contrarian Bullish", "Meaningless"),
                    correctIndex = 2,
                    explanation = "PCR > 1.2 indicates excessive puts, which is contrarian bullish (too much fear)."
                )
            )
        )
    )

    private fun createGreeksInfo(): List<GreekInfo> = listOf(
        GreekInfo(
            type = GreekType.DELTA,
            name = "Delta",
            symbol = "\u0394",
            shortDescription = "Measures price sensitivity to underlying movement",
            fullDescription = "Delta measures how much an option's price will change for every Rs 1 move in the underlying asset. It's the most important Greek for directional traders.\n\n" +
                    "Call Delta: Ranges from 0 to +1\n" +
                    "Put Delta: Ranges from -1 to 0\n\n" +
                    "ATM options have delta around 0.50 for calls and -0.50 for puts.",
            impact = "A delta of 0.50 means if NIFTY moves Rs 100, the option moves approximately Rs 50. Higher delta = more directional exposure.",
            range = "Calls: 0 to +1 | Puts: -1 to 0\nDeep ITM: Near +/-1 | ATM: +/-0.50 | Deep OTM: Near 0",
            example = "NIFTY 22,500 CE with Delta 0.55:\nIf NIFTY moves from 22,500 to 22,600 (+Rs 100)\nOption gains: 100 x 0.55 = Rs 55 approximately",
            tips = listOf(
                "Delta also represents approximate probability of expiring ITM",
                "Use delta to calculate position equivalent in underlying",
                "ATM straddles have near-zero total delta (market neutral)"
            ),
            colorHex = 0xFF007AFF // Blue
        ),
        GreekInfo(
            type = GreekType.GAMMA,
            name = "Gamma",
            symbol = "\u0393",
            shortDescription = "Rate of change of Delta",
            fullDescription = "Gamma measures how fast Delta changes for every Rs 1 move in the underlying. It's the second derivative of option price.\n\n" +
                    "Gamma is always positive for long options and tells you how your delta exposure will change as price moves.",
            impact = "High gamma means your delta changes rapidly. This can work for or against you. Near expiry, ATM options have explosive gamma.",
            range = "Always positive for long options\nHighest for ATM options\nIncreases as expiry approaches",
            example = "Delta = 0.50, Gamma = 0.05\nIf NIFTY moves +Rs 100:\nNew Delta = 0.50 + (100 x 0.0005) = 0.55\n\nYour position becomes more bullish as price rises!",
            tips = listOf(
                "High gamma near expiry can cause extreme P&L swings",
                "Gamma risk is why sellers fear Thursday expiry",
                "Long options = Long gamma (benefits from movement)",
                "Short options = Short gamma (benefits from stability)"
            ),
            colorHex = 0xFFBF5AF2 // Purple
        ),
        GreekInfo(
            type = GreekType.THETA,
            name = "Theta",
            symbol = "\u0398",
            shortDescription = "Time decay - value lost per day",
            fullDescription = "Theta measures how much an option loses value for each day that passes, assuming all else remains constant. It's always negative for option buyers.\n\n" +
                    "Time decay accelerates exponentially as expiry approaches, especially for ATM options.",
            impact = "If theta is -10, the option loses Rs 10 per day. Near expiry, theta can exceed Rs 50-100 per day for ATM options.",
            range = "Always negative for long options\nMost negative for ATM options\nAccelerates near expiry",
            example = "Option premium: Rs 150, Theta: -12\nAssuming NIFTY stays flat:\nDay 1: Rs 150\nDay 2: Rs 138\nDay 3: Rs 126\n\nYou're losing Rs 12 daily!",
            tips = listOf(
                "Option sellers are 'positive theta' - they earn from time decay",
                "Avoid holding OTM options in last 2-3 days before expiry",
                "Theta is highest for ATM, near-expiry options",
                "Weekend = 3 days theta decay on Friday"
            ),
            colorHex = 0xFFFF9F0A // Orange
        ),
        GreekInfo(
            type = GreekType.VEGA,
            name = "Vega",
            symbol = "\u03BD",
            shortDescription = "Sensitivity to volatility changes",
            fullDescription = "Vega measures how much an option's price changes for every 1% change in implied volatility. It's crucial for understanding how events affect option prices.\n\n" +
                    "Higher IV = Higher option prices\nLower IV = Lower option prices",
            impact = "If vega is 8, a 1% IV increase adds Rs 8 to the premium. Before major events, IV can spike 5-10%, significantly impacting prices.",
            range = "Always positive for long options\nHighest for ATM, longer-dated options\nDecreases as expiry approaches",
            example = "Option at Rs 150, Vega: 10, Current IV: 15%\nIV increases to 20% (+5%):\nNew Premium = 150 + (10 x 5) = Rs 200\n\nEven without price move, option gains Rs 50!",
            tips = listOf(
                "Buy options when IV is low (IV Percentile < 30%)",
                "Sell options when IV is high (IV Percentile > 70%)",
                "IV Crush post-event can destroy option value",
                "Use spreads to reduce vega exposure"
            ),
            colorHex = 0xFF00C805 // Green
        ),
        GreekInfo(
            type = GreekType.RHO,
            name = "Rho",
            symbol = "\u03C1",
            shortDescription = "Sensitivity to interest rate changes",
            fullDescription = "Rho measures how much an option's price changes for a 1% change in interest rates. It's the least important Greek for short-term traders.\n\n" +
                    "Calls have positive rho (benefit from rate increases)\nPuts have negative rho (benefit from rate decreases)",
            impact = "For most short-term trades, rho is negligible. It matters more for LEAPS (long-dated options) and during significant rate change periods.",
            range = "Calls: Positive rho | Puts: Negative rho\nHigher for ITM options\nHigher for longer-dated options",
            example = "Long-dated Call with Rho: 0.15\nIf interest rates increase by 1%:\nOption gains approximately Rs 15\n\nFor weekly options, this impact is minimal.",
            tips = listOf(
                "Rho is mostly irrelevant for weekly options",
                "Consider rho for LEAPS in changing rate environments",
                "RBI policy days can marginally affect longer-dated options",
                "Focus on Delta, Theta, and Vega for daily trading"
            ),
            colorHex = 0xFF64D2FF // Cyan
        )
    )

    private fun createStrategies(): List<OptionStrategy> = listOf(
        OptionStrategy(
            name = "Bull Call Spread",
            description = "A moderately bullish strategy that profits from upward price movement with limited risk and reward.",
            type = EducationStrategyType.BULLISH,
            outlook = MarketOutlook.BULLISH,
            riskLevel = EducationRiskLevel.LOW,
            legs = listOf(
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Call", strike = "ATM"),
                EducationStrategyLeg(action = "Sell", quantity = 1, optionType = "Call", strike = "OTM+200")
            ),
            maxProfit = "Strike Difference - Net Premium",
            maxLoss = "Net Premium Paid",
            breakeven = "Lower Strike + Net Premium",
            whenToUse = listOf(
                "Moderately bullish outlook",
                "Want to reduce cost of buying a call",
                "Expecting move up but limited upside",
                "High IV environment (reduces cost)"
            ),
            pros = listOf(
                "Lower cost than naked call",
                "Defined risk",
                "Benefits from IV decrease",
                "Lower breakeven"
            ),
            cons = listOf(
                "Capped profit potential",
                "Requires directional move",
                "Time decay works against",
                "Two legs = higher commissions"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = 70.0,
                scenarios = listOf(
                    ScenarioResult(22300.0, "Below lower strike", -3500.0),
                    ScenarioResult(22500.0, "At lower strike", -3500.0),
                    ScenarioResult(22570.0, "At breakeven", 0.0),
                    ScenarioResult(22700.0, "At upper strike", 6500.0),
                    ScenarioResult(23000.0, "Above upper strike", 6500.0)
                )
            )
        ),
        OptionStrategy(
            name = "Bear Put Spread",
            description = "A moderately bearish strategy that profits from downward price movement with limited risk and reward.",
            type = EducationStrategyType.BEARISH,
            outlook = MarketOutlook.BEARISH,
            riskLevel = EducationRiskLevel.LOW,
            legs = listOf(
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Put", strike = "ATM"),
                EducationStrategyLeg(action = "Sell", quantity = 1, optionType = "Put", strike = "OTM-200")
            ),
            maxProfit = "Strike Difference - Net Premium",
            maxLoss = "Net Premium Paid",
            breakeven = "Upper Strike - Net Premium",
            whenToUse = listOf(
                "Moderately bearish outlook",
                "Want to reduce cost of buying a put",
                "Expecting limited downside move",
                "Protecting portfolio downside"
            ),
            pros = listOf(
                "Lower cost than naked put",
                "Defined risk",
                "Benefits from IV decrease",
                "Clear profit target"
            ),
            cons = listOf(
                "Capped profit potential",
                "Requires directional move",
                "Time decay works against",
                "Lower strike must be breached"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = 65.0,
                scenarios = listOf(
                    ScenarioResult(22000.0, "Below lower strike", 6750.0),
                    ScenarioResult(22300.0, "At lower strike", 6750.0),
                    ScenarioResult(22435.0, "At breakeven", 0.0),
                    ScenarioResult(22500.0, "At upper strike", -3250.0),
                    ScenarioResult(22800.0, "Above upper strike", -3250.0)
                )
            )
        ),
        OptionStrategy(
            name = "Iron Condor",
            description = "A neutral strategy that profits when the underlying stays within a defined range. Collects premium from both sides.",
            type = EducationStrategyType.NEUTRAL,
            outlook = MarketOutlook.NEUTRAL,
            riskLevel = EducationRiskLevel.MEDIUM,
            legs = listOf(
                EducationStrategyLeg(action = "Sell", quantity = 1, optionType = "Put", strike = "OTM-200"),
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Put", strike = "OTM-400"),
                EducationStrategyLeg(action = "Sell", quantity = 1, optionType = "Call", strike = "OTM+200"),
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Call", strike = "OTM+400")
            ),
            maxProfit = "Net Premium Collected",
            maxLoss = "Wing Width - Net Premium",
            breakeven = "Short strikes +/- Net Premium",
            whenToUse = listOf(
                "Expecting low volatility",
                "Range-bound market expected",
                "High IV environment (collect more premium)",
                "After a big move (expecting consolidation)"
            ),
            pros = listOf(
                "Profits from time decay",
                "Benefits from IV decrease",
                "Defined risk on both sides",
                "No directional bias needed"
            ),
            cons = listOf(
                "Limited profit potential",
                "Large move causes max loss",
                "Needs active management",
                "Higher margin requirement"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = -60.0,
                scenarios = listOf(
                    ScenarioResult(22000.0, "Below lower wing", -7000.0),
                    ScenarioResult(22300.0, "At short put", 3000.0),
                    ScenarioResult(22500.0, "At center (ATM)", 3000.0),
                    ScenarioResult(22700.0, "At short call", 3000.0),
                    ScenarioResult(23000.0, "Above upper wing", -7000.0)
                )
            )
        ),
        OptionStrategy(
            name = "Long Straddle",
            description = "A volatility strategy that profits from big moves in either direction. Used before events with expected large movements.",
            type = EducationStrategyType.VOLATILE,
            outlook = MarketOutlook.HIGH_VOLATILITY,
            riskLevel = EducationRiskLevel.MEDIUM,
            legs = listOf(
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Call", strike = "ATM"),
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Put", strike = "ATM")
            ),
            maxProfit = "Unlimited (either direction)",
            maxLoss = "Total Premium Paid",
            breakeven = "Strike +/- Total Premium",
            whenToUse = listOf(
                "Expecting big move, unsure of direction",
                "Before major events (earnings, elections)",
                "Low IV environment (options are cheap)",
                "Breakout expected from consolidation"
            ),
            pros = listOf(
                "Profits from any big move",
                "Unlimited profit potential",
                "No directional bias needed",
                "Benefits from IV increase"
            ),
            cons = listOf(
                "Expensive (buying two options)",
                "Heavy time decay",
                "Needs move > total premium",
                "IV crush post-event hurts"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = 290.0,
                scenarios = listOf(
                    ScenarioResult(22000.0, "Below lower BE", 10500.0),
                    ScenarioResult(22210.0, "At lower BE", 0.0),
                    ScenarioResult(22500.0, "At strike (no move)", -14500.0),
                    ScenarioResult(22790.0, "At upper BE", 0.0),
                    ScenarioResult(23000.0, "Above upper BE", 10500.0)
                )
            )
        ),
        OptionStrategy(
            name = "Long Strangle",
            description = "Similar to straddle but cheaper. Buys OTM options on both sides, requiring larger move for profit.",
            type = EducationStrategyType.VOLATILE,
            outlook = MarketOutlook.HIGH_VOLATILITY,
            riskLevel = EducationRiskLevel.MEDIUM,
            legs = listOf(
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Call", strike = "OTM+200"),
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Put", strike = "OTM-200")
            ),
            maxProfit = "Unlimited (either direction)",
            maxLoss = "Total Premium Paid",
            breakeven = "Strike prices +/- Total Premium",
            whenToUse = listOf(
                "Expecting very large move",
                "Want cheaper volatility play than straddle",
                "Before high-impact events",
                "When ATM options are too expensive"
            ),
            pros = listOf(
                "Cheaper than straddle",
                "Profits from big moves",
                "Unlimited profit potential",
                "Benefits from IV spike"
            ),
            cons = listOf(
                "Requires larger move than straddle",
                "Both options are OTM",
                "High time decay",
                "Often expires worthless"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = 160.0,
                scenarios = listOf(
                    ScenarioResult(22000.0, "Big down move", 14000.0),
                    ScenarioResult(22140.0, "At lower BE", 0.0),
                    ScenarioResult(22500.0, "At center (ATM)", -8000.0),
                    ScenarioResult(22860.0, "At upper BE", 0.0),
                    ScenarioResult(23200.0, "Big up move", 17000.0)
                )
            )
        ),
        OptionStrategy(
            name = "Covered Call",
            description = "Sell a call against stock/futures holding. Generates income but caps upside.",
            type = EducationStrategyType.NEUTRAL,
            outlook = MarketOutlook.NEUTRAL,
            riskLevel = EducationRiskLevel.LOW,
            legs = listOf(
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Futures", strike = "Spot"),
                EducationStrategyLeg(action = "Sell", quantity = 1, optionType = "Call", strike = "OTM+200")
            ),
            maxProfit = "Premium + (Strike - Entry)",
            maxLoss = "Entry Price (if underlying goes to 0)",
            breakeven = "Entry Price - Premium Received",
            whenToUse = listOf(
                "Holding stock/futures long-term",
                "Want to generate income",
                "Slightly bullish to neutral view",
                "Willing to sell at strike price"
            ),
            pros = listOf(
                "Generates consistent income",
                "Reduces cost basis",
                "Works in sideways markets",
                "Lower risk than naked long"
            ),
            cons = listOf(
                "Caps upside potential",
                "Still exposed to downside",
                "May be assigned early",
                "Opportunity cost if rally"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = -80.0,
                scenarios = listOf(
                    ScenarioResult(22000.0, "Down move", -21000.0),
                    ScenarioResult(22420.0, "At breakeven", 0.0),
                    ScenarioResult(22500.0, "Flat", 4000.0),
                    ScenarioResult(22700.0, "At short call", 14000.0),
                    ScenarioResult(23000.0, "Above call (capped)", 14000.0)
                )
            )
        ),
        OptionStrategy(
            name = "Protective Put",
            description = "Buy a put to protect long stock/futures position. Acts as insurance against downside.",
            type = EducationStrategyType.BULLISH,
            outlook = MarketOutlook.BULLISH,
            riskLevel = EducationRiskLevel.LOW,
            legs = listOf(
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Futures", strike = "Spot"),
                EducationStrategyLeg(action = "Buy", quantity = 1, optionType = "Put", strike = "OTM-200")
            ),
            maxProfit = "Unlimited upside",
            maxLoss = "Entry - Strike + Premium",
            breakeven = "Entry Price + Premium Paid",
            whenToUse = listOf(
                "Want downside protection",
                "Holding through uncertain events",
                "Bullish but want insurance",
                "Can't watch positions constantly"
            ),
            pros = listOf(
                "Limited downside risk",
                "Unlimited upside potential",
                "Peace of mind",
                "Can hold through volatility"
            ),
            cons = listOf(
                "Premium cost reduces returns",
                "Put expires worthless if no crash",
                "Needs move > premium to profit",
                "Ongoing cost if rolled"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = 120.0,
                scenarios = listOf(
                    ScenarioResult(22000.0, "Crash (protected)", -10000.0),
                    ScenarioResult(22300.0, "At put strike", -16000.0),
                    ScenarioResult(22620.0, "At breakeven", 0.0),
                    ScenarioResult(22800.0, "Up move", 9000.0),
                    ScenarioResult(23200.0, "Big rally", 29000.0)
                )
            )
        ),
        OptionStrategy(
            name = "Short Straddle",
            description = "Sell ATM call and put. Maximum profit if underlying stays at strike. High risk strategy.",
            type = EducationStrategyType.NEUTRAL,
            outlook = MarketOutlook.LOW_VOLATILITY,
            riskLevel = EducationRiskLevel.VERY_HIGH,
            legs = listOf(
                EducationStrategyLeg(action = "Sell", quantity = 1, optionType = "Call", strike = "ATM"),
                EducationStrategyLeg(action = "Sell", quantity = 1, optionType = "Put", strike = "ATM")
            ),
            maxProfit = "Total Premium Collected",
            maxLoss = "Unlimited (either direction)",
            breakeven = "Strike +/- Total Premium",
            whenToUse = listOf(
                "Expecting very low volatility",
                "Expecting price to stay near current level",
                "High IV (collecting more premium)",
                "Confident about tight range"
            ),
            pros = listOf(
                "Collects maximum premium",
                "Benefits from time decay",
                "Profits from IV crush",
                "Works in sideways markets"
            ),
            cons = listOf(
                "Unlimited risk on both sides",
                "Requires high margin",
                "Any big move is dangerous",
                "Needs constant monitoring"
            ),
            example = StrategyExample(
                spotPrice = 22500.0,
                netCost = -280.0,
                scenarios = listOf(
                    ScenarioResult(22000.0, "Down move", -11000.0),
                    ScenarioResult(22220.0, "At lower BE", 0.0),
                    ScenarioResult(22500.0, "At strike (max profit)", 14000.0),
                    ScenarioResult(22780.0, "At upper BE", 0.0),
                    ScenarioResult(23000.0, "Up move", -11000.0)
                )
            )
        )
    )
}
