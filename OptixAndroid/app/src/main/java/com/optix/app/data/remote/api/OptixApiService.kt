package com.optix.app.data.remote.api

import com.optix.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Main API service interface for Optix backend
 */
interface OptixApiService {

    // ============== Auth ==============

    @POST("api/v1/auth/otp/send")
    suspend fun sendOtp(@Body request: OtpRequestDto): Response<OTPResponse>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body request: VerifyOtpRequestDto): Response<AuthResponse>

    @POST("api/v1/auth/social/google")
    suspend fun googleSignIn(@Body request: GoogleSignInRequestDto): Response<AuthResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: Map<String, String>): Response<RefreshResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    // ============== User ==============

    @GET("api/v1/user/me")
    suspend fun getCurrentUser(): Response<UserDto>

    // ============== Market Data ==============

    @GET("api/v1/market/option-chain/{index}")
    suspend fun getOptionChain(
        @Path("index") index: String,
        @Query("expiry") expiry: String
    ): Response<OptionChainResponse>

    @GET("api/v1/market/expiries/{index}")
    suspend fun getExpiries(
        @Path("index") index: String
    ): Response<ExpiryDatesResponse>

    @GET("api/v1/market/spot-price/{symbol}")
    suspend fun getSpotPrice(
        @Path("symbol") symbol: String
    ): Response<SpotPriceResponse>

    @GET("api/v1/market/india-vix")
    suspend fun getIndiaVix(): Response<IndiaVixResponse>

    @GET("api/v1/market/overview")
    suspend fun getMarketOverview(): Response<MarketOverviewResponse>

    // ============== Paper Trading ==============

    @GET("api/v1/paper-trading/positions")
    suspend fun getPositions(): Response<PositionsResponse>

    @POST("api/v1/paper-trading/trade")
    suspend fun executeTrade(@Body request: TradeRequest): Response<TradeResponse>

    @POST("api/v1/paper-trading/close/{positionId}")
    suspend fun closePosition(
        @Path("positionId") positionId: String,
        @Body request: ClosePositionRequest
    ): Response<TradeResponse>

    @GET("api/v1/paper-trading/history")
    suspend fun getTradeHistory(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<TradeHistoryResponse>

    @GET("api/v1/paper-trading/performance")
    suspend fun getPerformance(): Response<PerformanceResponse>

    // ============== Alerts ==============

    @GET("api/v1/alerts")
    suspend fun getAlerts(): Response<AlertsResponse>

    @GET("api/v1/alerts/{alertId}")
    suspend fun getAlert(@Path("alertId") alertId: String): Response<AlertDto>

    @POST("api/v1/alerts")
    suspend fun createAlert(@Body request: CreateAlertRequest): Response<AlertDto>

    @PUT("api/v1/alerts/{alertId}")
    suspend fun updateAlert(
        @Path("alertId") alertId: String,
        @Body request: UpdateAlertRequest
    ): Response<AlertDto>

    @DELETE("api/v1/alerts/{alertId}")
    suspend fun deleteAlert(@Path("alertId") alertId: String): Response<Unit>

    @POST("api/v1/alerts/{alertId}/toggle")
    suspend fun toggleAlert(@Path("alertId") alertId: String): Response<AlertDto>

    @GET("api/v1/alerts/stats")
    suspend fun getAlertStats(): Response<AlertStatsDto>

    @GET("api/v1/alerts/notifications/list")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("unread_only") unreadOnly: Boolean = false
    ): Response<NotificationsResponse>

    @POST("api/v1/alerts/notifications/mark-read")
    suspend fun markNotificationsRead(@Body request: MarkReadRequest): Response<MarkReadResponse>

    @POST("api/v1/alerts/notifications/mark-all-read")
    suspend fun markAllNotificationsRead(): Response<MarkReadResponse>

    // ============== AI Analysis ==============

    @POST("api/v1/options-ai/analyze")
    suspend fun analyzeOption(@Body request: AnalyzeOptionRequest): Response<AIAnalysisResponse>

    @POST("api/v1/options/analyze")
    suspend fun analyzeTradeWithAI(@Body request: AnalyzeTradeRequest): Response<AnalyzeTradeResponse>

    @GET("api/v1/options-ai/insights/{symbol}")
    suspend fun getAIInsights(
        @Path("symbol") symbol: String,
        @Query("expiry") expiry: String? = null
    ): Response<AIInsightsResponse>

    // ============== IPO ==============

    @GET("api/v1/ipo/list")
    suspend fun getIPOList(
        @Query("status") status: String? = null
    ): Response<IPOListResponse>

    @GET("api/v1/ipo/{id}")
    suspend fun getIPODetail(@Path("id") id: String): Response<IPODetailResponse>

    // ============== Trade Journal ==============

    @GET("api/v1/journal")
    suspend fun getJournalEntries(
        @Query("outcome") outcome: String? = null,
        @Query("symbol") symbol: String? = null,
        @Query("tag") tag: String? = null,
        @Query("mood") mood: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<JournalListResponse>

    @GET("api/v1/journal/stats")
    suspend fun getJournalStats(): Response<JournalStatsDto>

    @POST("api/v1/journal")
    suspend fun createJournalEntry(@Body request: JournalCreateRequest): Response<JournalEntryDto>

    @PUT("api/v1/journal/{entryId}")
    suspend fun updateJournalEntry(
        @Path("entryId") entryId: String,
        @Body request: JournalUpdateRequest
    ): Response<JournalEntryDto>

    @DELETE("api/v1/journal/{entryId}")
    suspend fun deleteJournalEntry(@Path("entryId") entryId: String): Response<JournalDeleteResponse>

    // ============== Chat ==============

    @POST("api/v1/chatbot/quick")
    suspend fun quickChat(@Body request: QuickChatRequest): Response<QuickChatResponse>

    @POST("api/v1/chatbot/send")
    suspend fun sendChatMessage(@Body request: ChatRequest): Response<ChatResponse>

    @GET("api/v1/chatbot/sessions")
    suspend fun getChatSessions(): Response<ChatSessionsResponse>

    @DELETE("api/v1/chatbot/sessions/{sessionId}")
    suspend fun deleteChatSession(@Path("sessionId") sessionId: String): Response<Unit>
}
