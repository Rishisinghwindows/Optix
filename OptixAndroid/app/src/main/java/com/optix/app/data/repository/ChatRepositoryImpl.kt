package com.optix.app.data.repository

import com.optix.app.core.util.Resource
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.data.remote.dto.ChatContextDto
import com.optix.app.data.remote.dto.ChatRequest
import com.optix.app.data.remote.dto.QuickChatRequest
import com.optix.app.domain.model.ChatContext
import com.optix.app.domain.model.ChatSession
import com.optix.app.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val api: OptixApiService
) : ChatRepository {

    override suspend fun quickChat(
        message: String,
        context: ChatContext?
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = QuickChatRequest(
                        message = message,
                        context = context?.toDto()
                    )
                    val response = api.quickChat(request)
                    if (response.isSuccessful && response.body() != null) {
                        Resource.Success(response.body()!!.text)
                    } else {
                        // API failed, use demo fallback
                        Resource.Success(generateDemoResponse(message))
                    }
                } catch (e: Exception) {
                    // API unavailable, use demo fallback
                    Resource.Success(generateDemoResponse(message))
                }
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun sendMessage(
        message: String,
        sessionId: String?,
        context: ChatContext?
    ): Flow<Resource<Pair<String, String>>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = ChatRequest(
                        message = message,
                        sessionId = sessionId,
                        context = context?.toDto()
                    )
                    val response = api.sendChatMessage(request)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        Resource.Success(Pair(body.reply, body.sessionId))
                    } else {
                        // API failed, use demo fallback with fake session
                        val demoSessionId = sessionId ?: "demo-session-${System.currentTimeMillis()}"
                        Resource.Success(Pair(generateDemoResponse(message), demoSessionId))
                    }
                } catch (e: Exception) {
                    // API unavailable, use demo fallback
                    val demoSessionId = sessionId ?: "demo-session-${System.currentTimeMillis()}"
                    Resource.Success(Pair(generateDemoResponse(message), demoSessionId))
                }
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun getSessions(): Flow<Resource<List<ChatSession>>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    val response = api.getChatSessions()
                    if (response.isSuccessful && response.body() != null) {
                        val sessions = response.body()!!.sessions.map { dto ->
                            ChatSession(
                                id = dto.id,
                                userId = "",
                                title = dto.title,
                                createdAt = parseDateTime(dto.createdAt),
                                updatedAt = parseDateTime(dto.updatedAt)
                            )
                        }
                        Resource.Success(sessions)
                    } else {
                        Resource.Success(emptyList())
                    }
                } catch (e: Exception) {
                    Resource.Success(emptyList())
                }
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun deleteSession(sessionId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    val response = api.deleteChatSession(sessionId)
                    if (response.isSuccessful) {
                        Resource.Success(Unit)
                    } else {
                        Resource.Error("Failed to delete session")
                    }
                } catch (e: Exception) {
                    Resource.Error(e.message ?: "Failed to delete session")
                }
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    private fun ChatContext.toDto(): ChatContextDto {
        return ChatContextDto(
            spotPrice = spotPrice,
            pcr = pcr,
            selectedExpiry = selectedExpiry,
            selectedStrike = selectedStrike,
            ivPercentile = ivPercentile,
            marketTrend = marketTrend,
            symbol = symbol
        )
    }

    private fun parseDateTime(dateString: String): LocalDateTime {
        return try {
            LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }

    /**
     * Generate demo response when API is unavailable
     * Matches the existing ChatViewModel fallback responses
     */
    private fun generateDemoResponse(question: String): String {
        val lowerQuestion = question.lowercase()

        return when {
            lowerQuestion.contains("pcr") || lowerQuestion.contains("put call ratio") -> {
                "**Put-Call Ratio (PCR)** is a sentiment indicator:\n\n" +
                "- **PCR > 1**: More puts traded than calls - typically bullish (contrarian view)\n" +
                "- **PCR < 1**: More calls traded than puts - typically bearish\n" +
                "- **PCR = 1**: Balanced sentiment\n\n" +
                "**How to use it:**\n" +
                "1. High PCR (>1.2) suggests oversold conditions\n" +
                "2. Low PCR (<0.7) suggests overbought conditions\n" +
                "3. Use with other indicators for confirmation\n\n" +
                "Current NIFTY PCR is shown in the Option Chain header."
            }
            lowerQuestion.contains("greek") || lowerQuestion.contains("delta") || lowerQuestion.contains("theta") -> {
                "**Options Greeks** measure risk sensitivities:\n\n" +
                "**Delta (\u0394)**: Price sensitivity\n" +
                "- Call delta: 0 to 1\n" +
                "- Put delta: -1 to 0\n" +
                "- ATM options have ~0.5 delta\n\n" +
                "**Gamma (\u0393)**: Rate of delta change\n" +
                "- Highest for ATM options\n" +
                "- Increases near expiry\n\n" +
                "**Theta (\u0398)**: Time decay\n" +
                "- Always negative for buyers\n" +
                "- Accelerates near expiry\n\n" +
                "**Vega (V)**: IV sensitivity\n" +
                "- Higher for ATM, longer expiry\n\n" +
                "Use the Calculator to see Greeks for any option!"
            }
            lowerQuestion.contains("straddle") || lowerQuestion.contains("strangle") -> {
                "**Straddle vs Strangle:**\n\n" +
                "**Long Straddle:**\n" +
                "- Buy ATM Call + ATM Put\n" +
                "- Profit from big move either direction\n" +
                "- Max loss = total premium paid\n" +
                "- Use when expecting high volatility\n\n" +
                "**Long Strangle:**\n" +
                "- Buy OTM Call + OTM Put\n" +
                "- Cheaper than straddle\n" +
                "- Needs bigger move to profit\n\n" +
                "**Short versions:**\n" +
                "- Profit from time decay\n" +
                "- Risk if market moves big\n\n" +
                "Try the Strategy Builder to visualize payoffs!"
            }
            lowerQuestion.contains("iron condor") -> {
                "**Iron Condor Strategy:**\n\n" +
                "A neutral strategy for range-bound markets.\n\n" +
                "**Structure:**\n" +
                "1. Sell OTM Put (lower strike)\n" +
                "2. Buy further OTM Put (protection)\n" +
                "3. Sell OTM Call (higher strike)\n" +
                "4. Buy further OTM Call (protection)\n\n" +
                "**Characteristics:**\n" +
                "- Limited profit (net credit)\n" +
                "- Limited loss (width - credit)\n" +
                "- Profit if price stays in range\n" +
                "- Benefits from time decay\n\n" +
                "**Best used when:**\n" +
                "- Low IV environment\n" +
                "- Expecting sideways movement\n" +
                "- Want defined risk"
            }
            lowerQuestion.contains("max pain") -> {
                "**Max Pain Theory:**\n\n" +
                "The strike price where option buyers lose the most money at expiry.\n\n" +
                "**How it works:**\n" +
                "- Option writers (sellers) have interest in price staying at max pain\n" +
                "- Price tends to gravitate toward max pain near expiry\n\n" +
                "**How to use:**\n" +
                "1. Check max pain in OI Analysis\n" +
                "2. Use as potential support/resistance\n" +
                "3. More relevant closer to expiry\n\n" +
                "**Limitations:**\n" +
                "- Not always accurate\n" +
                "- Strong trends can override\n" +
                "- Best used with other indicators"
            }
            lowerQuestion.contains("support") || lowerQuestion.contains("resistance") -> {
                "**Identifying Support/Resistance from OI:**\n\n" +
                "**Support (Put OI):**\n" +
                "- High Put OI = potential support\n" +
                "- Put writers defend these levels\n" +
                "- Look for strikes below current price\n\n" +
                "**Resistance (Call OI):**\n" +
                "- High Call OI = potential resistance\n" +
                "- Call writers defend these levels\n" +
                "- Look for strikes above current price\n\n" +
                "**Tips:**\n" +
                "1. Check OI Analysis > Zones tab\n" +
                "2. Monitor OI changes for shifts\n" +
                "3. Combine with technical levels\n" +
                "4. Higher OI = stronger zone"
            }
            lowerQuestion.contains("sideways") || lowerQuestion.contains("range") -> {
                "**Strategies for Sideways Market:**\n\n" +
                "**1. Short Straddle/Strangle:**\n" +
                "- Sell ATM/OTM options\n" +
                "- Profit from time decay\n" +
                "- Risk: unlimited if breakout\n\n" +
                "**2. Iron Condor:**\n" +
                "- Defined risk version\n" +
                "- Sell spread on both sides\n" +
                "- Limited profit & loss\n\n" +
                "**3. Iron Butterfly:**\n" +
                "- Sell ATM straddle + buy wings\n" +
                "- Max profit if price at ATM\n\n" +
                "**4. Calendar Spread:**\n" +
                "- Sell near expiry, buy far\n" +
                "- Profit from IV & time\n\n" +
                "Use Strategy Builder to compare payoffs!"
            }
            lowerQuestion.contains("bullish") || lowerQuestion.contains("buy call") -> {
                "**Bullish Strategies:**\n\n" +
                "**1. Long Call (Simple):**\n" +
                "- Buy ATM/slightly OTM call\n" +
                "- Unlimited profit potential\n" +
                "- Max loss = premium\n\n" +
                "**2. Bull Call Spread (Moderate):**\n" +
                "- Buy lower strike call\n" +
                "- Sell higher strike call\n" +
                "- Reduced cost, capped profit\n\n" +
                "**3. Bull Put Spread (Credit):**\n" +
                "- Sell higher strike put\n" +
                "- Buy lower strike put\n" +
                "- Collect premium upfront\n\n" +
                "**Choosing strategy:**\n" +
                "- Strong conviction \u2192 Long Call\n" +
                "- Moderate view \u2192 Spreads\n" +
                "- High IV \u2192 Sell puts"
            }
            lowerQuestion.contains("bearish") || lowerQuestion.contains("buy put") -> {
                "**Bearish Strategies:**\n\n" +
                "**1. Long Put (Simple):**\n" +
                "- Buy ATM/slightly OTM put\n" +
                "- Profit as market falls\n" +
                "- Max loss = premium\n\n" +
                "**2. Bear Put Spread:**\n" +
                "- Buy higher strike put\n" +
                "- Sell lower strike put\n" +
                "- Reduced cost, capped profit\n\n" +
                "**3. Bear Call Spread (Credit):**\n" +
                "- Sell lower strike call\n" +
                "- Buy higher strike call\n" +
                "- Collect premium upfront\n\n" +
                "Check AI Insights for bearish trade ideas!"
            }
            lowerQuestion.contains("iv") || lowerQuestion.contains("implied volatility") -> {
                "**Implied Volatility (IV)** explained:\n\n" +
                "**What is IV?**\n" +
                "- Market's expectation of future price movement\n" +
                "- Higher IV = higher option premiums\n" +
                "- Expressed as annualized percentage\n\n" +
                "**IV Percentile:**\n" +
                "- Compares current IV to historical range\n" +
                "- IV Percentile > 50 = relatively high IV\n" +
                "- IV Percentile < 50 = relatively low IV\n\n" +
                "**Trading implications:**\n" +
                "- High IV: Consider selling options\n" +
                "- Low IV: Consider buying options\n" +
                "- IV crush after events = premium drop"
            }
            lowerQuestion.contains("vega") -> {
                "**Vega** measures IV sensitivity:\n\n" +
                "**Key points:**\n" +
                "- Shows price change per 1% IV change\n" +
                "- Always positive for long options\n" +
                "- Higher for ATM options\n" +
                "- Higher for longer expiry\n\n" +
                "**Trading with Vega:**\n" +
                "- Long options benefit from rising IV\n" +
                "- Short options benefit from falling IV\n" +
                "- Before events: IV typically rises\n" +
                "- After events: IV typically falls (IV crush)"
            }
            lowerQuestion.contains("gamma") -> {
                "**Gamma** measures delta's rate of change:\n\n" +
                "**Key characteristics:**\n" +
                "- Highest for ATM options\n" +
                "- Increases dramatically near expiry\n" +
                "- Always positive for long options\n\n" +
                "**Why it matters:**\n" +
                "- High gamma = delta changes rapidly\n" +
                "- Near expiry ATM = \"gamma scalping\" opportunity\n" +
                "- Risk: Can work against you if wrong direction\n\n" +
                "**Gamma risk:**\n" +
                "- Short options have negative gamma\n" +
                "- Can lead to large losses on big moves"
            }
            else -> {
                "That's a great question! Here's what I can help you with:\n\n" +
                "**Trading Concepts:**\n" +
                "- PCR, Max Pain, IV\n" +
                "- Greeks (Delta, Gamma, Theta, Vega)\n" +
                "- OI Analysis\n\n" +
                "**Strategies:**\n" +
                "- Straddle, Strangle\n" +
                "- Iron Condor, Butterfly\n" +
                "- Bull/Bear Spreads\n\n" +
                "**Analysis:**\n" +
                "- Support/Resistance from OI\n" +
                "- Smart Money Activity\n" +
                "- Strategy selection\n\n" +
                "Try asking about any of these topics!"
            }
        }
    }
}
