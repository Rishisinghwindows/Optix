package com.optix.app.domain.model

import java.util.UUID

/**
 * Education module data models
 */

// Education Categories
enum class EducationCategory {
    BASICS,
    GREEKS,
    STRATEGIES,
    ADVANCED
}

// Lesson Difficulty
enum class LessonDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

// Strategy Types for Education
enum class EducationStrategyType {
    BULLISH,
    BEARISH,
    NEUTRAL,
    VOLATILE
}

// Market Outlook
enum class MarketOutlook {
    BULLISH,
    BEARISH,
    NEUTRAL,
    HIGH_VOLATILITY,
    LOW_VOLATILITY
}

// Risk Level for Education
enum class EducationRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

// Greek Type
enum class GreekType {
    DELTA,
    GAMMA,
    THETA,
    VEGA,
    RHO
}

/**
 * Lesson model for education content
 */
data class Lesson(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val description: String = "",
    val category: EducationCategory,
    val difficulty: LessonDifficulty,
    val duration: String,
    val icon: String = "book",
    val isCompleted: Boolean = false,
    val keyPoints: List<String> = emptyList(),
    val content: List<LessonSection> = emptyList(),
    val quiz: List<QuizQuestion>? = null
)

/**
 * Section within a lesson
 */
data class LessonSection(
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val content: String,
    val example: LessonExample? = null,
    val tip: String? = null
)

/**
 * Example within a lesson
 */
data class LessonExample(
    val title: String,
    val scenario: String,
    val calculation: String? = null,
    val result: String
)

/**
 * Quiz Question
 */
data class QuizQuestion(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

/**
 * Quiz Result
 */
data class QuizResult(
    val lessonId: String,
    val score: Int,
    val totalQuestions: Int,
    val completedAt: Long = System.currentTimeMillis()
) {
    val percentage: Float
        get() = if (totalQuestions > 0) (score.toFloat() / totalQuestions) * 100 else 0f

    val isPerfect: Boolean
        get() = score == totalQuestions
}

/**
 * Greek Information for education
 */
data class GreekInfo(
    val id: String = UUID.randomUUID().toString(),
    val type: GreekType,
    val name: String,
    val symbol: String,
    val shortDescription: String,
    val fullDescription: String,
    val impact: String,
    val range: String,
    val example: String,
    val tips: List<String>,
    val colorHex: Long
)

/**
 * Option Strategy for education
 */
data class OptionStrategy(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val type: EducationStrategyType,
    val outlook: MarketOutlook,
    val riskLevel: EducationRiskLevel,
    val legs: List<EducationStrategyLeg>,
    val maxProfit: String,
    val maxLoss: String,
    val breakeven: String,
    val whenToUse: List<String>,
    val pros: List<String>,
    val cons: List<String>,
    val example: StrategyExample? = null
)

/**
 * Strategy Leg
 */
data class EducationStrategyLeg(
    val id: String = UUID.randomUUID().toString(),
    val action: String, // "Buy" or "Sell"
    val quantity: Int,
    val optionType: String, // "Call" or "Put"
    val strike: String // e.g., "ATM", "ITM+100", "OTM-100"
)

/**
 * Strategy Example with scenarios
 */
data class StrategyExample(
    val spotPrice: Double,
    val netCost: Double,
    val scenarios: List<ScenarioResult>
)

/**
 * Scenario Result for strategy payoff
 */
data class ScenarioResult(
    val expiryPrice: Double,
    val description: String,
    val profit: Double
)

/**
 * User's Education Progress
 */
data class EducationProgress(
    val completedLessons: Set<String> = emptySet(),
    val quizResults: Map<String, QuizResult> = emptyMap(),
    val lastAccessedLessonId: String? = null,
    val totalTimeSpent: Long = 0L // in milliseconds
) {
    val completedCount: Int
        get() = completedLessons.size

    val averageQuizScore: Float
        get() {
            if (quizResults.isEmpty()) return 0f
            return quizResults.values.map { it.percentage }.average().toFloat()
        }
}
