# Android App Development Plan - Optix

## Overview
Build an Android version of the Optix iOS app using **Clean Architecture** with **Kotlin**, **Jetpack Compose**, and modern Android development best practices.

---

## Phase 1: Project Setup & Architecture Foundation (Week 1)

### 1.1 Project Structure (Clean Architecture)

```
app/
├── build.gradle.kts
├── src/main/
│   ├── java/com/optix/app/
│   │   ├── OptixApplication.kt
│   │   │
│   │   ├── di/                          # Dependency Injection
│   │   │   ├── AppModule.kt
│   │   │   ├── NetworkModule.kt
│   │   │   ├── DatabaseModule.kt
│   │   │   ├── RepositoryModule.kt
│   │   │   └── UseCaseModule.kt
│   │   │
│   │   ├── domain/                      # Domain Layer (Business Logic)
│   │   │   ├── model/                   # Domain Models
│   │   │   │   ├── TradingIndex.kt
│   │   │   │   ├── OptionData.kt
│   │   │   │   ├── OptionChainRow.kt
│   │   │   │   ├── Strategy.kt
│   │   │   │   ├── StrategyLeg.kt
│   │   │   │   ├── PayoffData.kt
│   │   │   │   ├── Greeks.kt
│   │   │   │   ├── PaperTrade.kt
│   │   │   │   ├── PaperPosition.kt
│   │   │   │   ├── AITradeSuggestion.kt
│   │   │   │   ├── OIAnalysisResult.kt
│   │   │   │   ├── IPOItem.kt
│   │   │   │   ├── ChatMessage.kt
│   │   │   │   ├── User.kt
│   │   │   │   └── Alert.kt
│   │   │   │
│   │   │   ├── repository/              # Repository Interfaces
│   │   │   │   ├── OptionChainRepository.kt
│   │   │   │   ├── AuthRepository.kt
│   │   │   │   ├── PaperTradingRepository.kt
│   │   │   │   ├── AIAnalysisRepository.kt
│   │   │   │   ├── IPORepository.kt
│   │   │   │   ├── ChatRepository.kt
│   │   │   │   └── AlertRepository.kt
│   │   │   │
│   │   │   └── usecase/                 # Use Cases
│   │   │       ├── optionchain/
│   │   │       │   ├── GetOptionChainUseCase.kt
│   │   │       │   ├── GetExpiryDatesUseCase.kt
│   │   │       │   └── SubscribeLivePricesUseCase.kt
│   │   │       ├── auth/
│   │   │       │   ├── SendOTPUseCase.kt
│   │   │       │   ├── VerifyOTPUseCase.kt
│   │   │       │   ├── GoogleLoginUseCase.kt
│   │   │       │   └── LogoutUseCase.kt
│   │   │       ├── papertrading/
│   │   │       │   ├── ExecuteTradeUseCase.kt
│   │   │       │   ├── GetPositionsUseCase.kt
│   │   │       │   └── GetPerformanceUseCase.kt
│   │   │       ├── ai/
│   │   │       │   ├── GetAIInsightsUseCase.kt
│   │   │       │   └── AnalyzeOptionUseCase.kt
│   │   │       ├── strategy/
│   │   │       │   ├── CalculatePayoffUseCase.kt
│   │   │       │   └── GetStrategyGreeksUseCase.kt
│   │   │       ├── alerts/
│   │   │       │   ├── CreateAlertUseCase.kt
│   │   │       │   ├── GetAlertsUseCase.kt
│   │   │       │   └── DeleteAlertUseCase.kt
│   │   │       └── ipo/
│   │   │           └── GetIPOListUseCase.kt
│   │   │
│   │   ├── data/                        # Data Layer
│   │   │   ├── remote/                  # API Services
│   │   │   │   ├── api/
│   │   │   │   │   ├── OptixApiService.kt
│   │   │   │   │   ├── UpstoxApiService.kt
│   │   │   │   │   └── NSEApiService.kt
│   │   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   │   │   ├── OptionChainDto.kt
│   │   │   │   │   ├── UserDto.kt
│   │   │   │   │   ├── TradeDto.kt
│   │   │   │   │   └── AlertDto.kt
│   │   │   │   └── websocket/
│   │   │   │       ├── WebSocketManager.kt
│   │   │   │       └── PriceStreamHandler.kt
│   │   │   │
│   │   │   ├── local/                   # Local Storage
│   │   │   │   ├── datastore/
│   │   │   │   │   ├── UserPreferences.kt
│   │   │   │   │   └── AppSettings.kt
│   │   │   │   └── db/
│   │   │   │       ├── OptixDatabase.kt
│   │   │   │       ├── dao/
│   │   │   │       │   ├── AlertDao.kt
│   │   │   │       │   └── CacheDao.kt
│   │   │   │       └── entity/
│   │   │   │           ├── AlertEntity.kt
│   │   │   │           └── CachedOptionChainEntity.kt
│   │   │   │
│   │   │   ├── repository/              # Repository Implementations
│   │   │   │   ├── OptionChainRepositoryImpl.kt
│   │   │   │   ├── AuthRepositoryImpl.kt
│   │   │   │   ├── PaperTradingRepositoryImpl.kt
│   │   │   │   ├── AIAnalysisRepositoryImpl.kt
│   │   │   │   ├── IPORepositoryImpl.kt
│   │   │   │   ├── ChatRepositoryImpl.kt
│   │   │   │   └── AlertRepositoryImpl.kt
│   │   │   │
│   │   │   └── mapper/                  # DTO to Domain Mappers
│   │   │       ├── OptionChainMapper.kt
│   │   │       ├── UserMapper.kt
│   │   │       └── AlertMapper.kt
│   │   │
│   │   ├── presentation/                # Presentation Layer (UI)
│   │   │   ├── navigation/
│   │   │   │   ├── NavGraph.kt
│   │   │   │   ├── Screen.kt
│   │   │   │   └── BottomNavBar.kt
│   │   │   │
│   │   │   ├── theme/
│   │   │   │   ├── Theme.kt
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Typography.kt
│   │   │   │   └── Shape.kt
│   │   │   │
│   │   │   ├── components/              # Reusable UI Components
│   │   │   │   ├── OptionRow.kt
│   │   │   │   ├── GreeksDisplay.kt
│   │   │   │   ├── LoadingShimmer.kt
│   │   │   │   ├── ErrorDialog.kt
│   │   │   │   ├── PriceChangeIndicator.kt
│   │   │   │   └── FloatingChatButton.kt
│   │   │   │
│   │   │   └── screens/
│   │   │       ├── splash/
│   │   │       │   ├── SplashScreen.kt
│   │   │       │   └── SplashViewModel.kt
│   │   │       ├── onboarding/
│   │   │       │   ├── OnboardingScreen.kt
│   │   │       │   └── OnboardingViewModel.kt
│   │   │       ├── optionchain/
│   │   │       │   ├── OptionChainScreen.kt
│   │   │       │   ├── OptionChainViewModel.kt
│   │   │       │   └── components/
│   │   │       │       ├── IndexSelector.kt
│   │   │       │       ├── ExpiryPicker.kt
│   │   │       │       └── OptionChainTable.kt
│   │   │       ├── aiinsights/
│   │   │       │   ├── AIInsightsScreen.kt
│   │   │       │   ├── AIInsightsViewModel.kt
│   │   │       │   └── components/
│   │   │       │       ├── TradeSuggestionCard.kt
│   │   │       │       └── ScoreReasoningSheet.kt
│   │   │       ├── papertrading/
│   │   │       │   ├── PaperTradingScreen.kt
│   │   │       │   ├── PaperTradingViewModel.kt
│   │   │       │   └── components/
│   │   │       │       ├── PositionCard.kt
│   │   │       │       ├── TradeHistoryItem.kt
│   │   │       │       └── TradeExecutionSheet.kt
│   │   │       ├── alerts/
│   │   │       │   ├── AlertsScreen.kt
│   │   │       │   ├── AlertsViewModel.kt
│   │   │       │   └── components/
│   │   │       │       ├── AlertCard.kt
│   │   │       │       └── CreateAlertSheet.kt
│   │   │       ├── ipo/
│   │   │       │   ├── IPODashboardScreen.kt
│   │   │       │   ├── IPOViewModel.kt
│   │   │       │   └── components/
│   │   │       │       └── IPOCard.kt
│   │   │       ├── strategy/
│   │   │       │   ├── StrategyBuilderScreen.kt
│   │   │       │   ├── StrategyViewModel.kt
│   │   │       │   └── components/
│   │   │       │       ├── LegEditor.kt
│   │   │       │       └── PayoffChart.kt
│   │   │       ├── calculator/
│   │   │       │   ├── CalculatorScreen.kt
│   │   │       │   └── CalculatorViewModel.kt
│   │   │       ├── oianalysis/
│   │   │       │   ├── OIAnalysisScreen.kt
│   │   │       │   ├── OIAnalysisViewModel.kt
│   │   │       │   └── components/
│   │   │       │       ├── OIHeatmap.kt
│   │   │       │       └── PCRGauge.kt
│   │   │       ├── chat/
│   │   │       │   ├── ChatScreen.kt
│   │   │       │   ├── ChatViewModel.kt
│   │   │       │   └── components/
│   │   │       │       └── ChatBubble.kt
│   │   │       ├── education/
│   │   │       │   ├── EducationScreen.kt
│   │   │       │   └── EducationViewModel.kt
│   │   │       ├── settings/
│   │   │       │   ├── SettingsScreen.kt
│   │   │       │   └── SettingsViewModel.kt
│   │   │       └── auth/
│   │   │           ├── LoginScreen.kt
│   │   │           └── AuthViewModel.kt
│   │   │
│   │   └── core/                        # Core Utilities
│   │       ├── util/
│   │       │   ├── Resource.kt          # Sealed class for API states
│   │       │   ├── Extensions.kt
│   │       │   ├── DateUtils.kt
│   │       │   └── NumberFormatter.kt
│   │       ├── calculation/
│   │       │   ├── BlackScholesEngine.kt
│   │       │   ├── StrategyCalculator.kt
│   │       │   └── OIAnalysisEngine.kt
│   │       └── constants/
│   │           ├── ApiConstants.kt
│   │           └── AppConstants.kt
│   │
│   └── res/
│       ├── values/
│       │   ├── strings.xml              # English (default)
│       │   ├── colors.xml
│       │   └── themes.xml
│       ├── values-hi/strings.xml        # Hindi
│       ├── values-bn/strings.xml        # Bengali
│       ├── values-kn/strings.xml        # Kannada
│       ├── values-ml/strings.xml        # Malayalam
│       ├── values-or/strings.xml        # Odia
│       ├── values-pa/strings.xml        # Punjabi
│       ├── values-ta/strings.xml        # Tamil
│       └── values-te/strings.xml        # Telugu
```

### 1.2 Dependencies (build.gradle.kts)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Dependency Injection - Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")

    // Networking - Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Image Loading - Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Charts - Vico
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Firebase (for notifications)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

---

## Phase 2: Core Infrastructure (Week 2)

### 2.1 Domain Models
Create all domain models matching iOS:
- TradingIndex (NIFTY50, BANKNIFTY, FINNIFTY, MIDCPNIFTY, SENSEX, BANKEX)
- OptionData, OptionChainRow, ExpiryDate
- Greeks, GreeksResult
- Strategy, StrategyLeg, PayoffData
- PaperTrade, PaperPosition, PerformanceMetrics
- AITradeSuggestion, OptionScore, ConfidenceLevel
- OIAnalysisResult, OIZone, SmartMoneyActivity
- Alert, AlertNotification
- User, ChatMessage, IPOItem

### 2.2 API Services
```kotlin
// OptixApiService.kt
interface OptixApiService {
    // Auth
    @POST("api/v1/auth/otp/send")
    suspend fun sendOTP(@Body request: SendOTPRequest): Response<OTPResponse>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOTP(@Body request: VerifyOTPRequest): Response<AuthResponse>

    // Option Chain
    @GET("api/v1/market/option-chain/{symbol}")
    suspend fun getOptionChain(
        @Path("symbol") symbol: String,
        @Query("expiry") expiry: String?
    ): Response<OptionChainResponse>

    // Paper Trading
    @GET("api/v1/paper-trading/positions")
    suspend fun getPositions(): Response<PositionsResponse>

    @POST("api/v1/paper-trading/trade")
    suspend fun executeTrade(@Body trade: TradeRequest): Response<TradeResponse>

    // Alerts
    @GET("api/v1/alerts")
    suspend fun getAlerts(): Response<AlertsResponse>

    @POST("api/v1/alerts")
    suspend fun createAlert(@Body alert: CreateAlertRequest): Response<AlertResponse>

    // AI Analysis
    @POST("api/v1/options-ai/analyze")
    suspend fun analyzeOption(@Body request: AnalyzeRequest): Response<AIAnalysisResponse>

    // IPO
    @GET("api/v1/ipo/list")
    suspend fun getIPOList(): Response<IPOListResponse>

    // Chat
    @POST("api/v1/chatbot/send")
    suspend fun sendChatMessage(@Body request: ChatRequest): Response<ChatResponse>
}
```

### 2.3 WebSocket Manager
```kotlin
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val _priceUpdates = MutableSharedFlow<PriceUpdate>()
    val priceUpdates: SharedFlow<PriceUpdate> = _priceUpdates.asSharedFlow()

    fun connect(url: String) { ... }
    fun subscribe(instrumentKeys: List<String>) { ... }
    fun disconnect() { ... }
}
```

### 2.4 Resource Wrapper
```kotlin
sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}
```

---

## Phase 3: Authentication & User Management (Week 3)

### 3.1 Features
- OTP-based login (phone number)
- Google Sign-In integration
- Token management with DataStore
- Session persistence
- Auto-refresh tokens

### 3.2 Implementation
```kotlin
// AuthViewModel.kt
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sendOTPUseCase: SendOTPUseCase,
    private val verifyOTPUseCase: VerifyOTPUseCase,
    private val googleLoginUseCase: GoogleLoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun sendOTP(phone: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            sendOTPUseCase(phone).collect { result ->
                when (result) {
                    is Resource.Success -> _uiState.update {
                        it.copy(isLoading = false, otpSent = true)
                    }
                    is Resource.Error -> _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                    is Resource.Loading -> { }
                }
            }
        }
    }
}
```

---

## Phase 4: Option Chain Screen (Week 4)

### 4.1 Features
- Index selector (6 indices)
- Expiry date picker
- Live option chain table
- Real-time price updates via WebSocket
- Search/filter by strike
- Greeks display
- Pull-to-refresh

### 4.2 UI Components
```kotlin
@Composable
fun OptionChainScreen(
    viewModel: OptionChainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column {
        IndexSelector(
            selectedIndex = uiState.selectedIndex,
            onIndexSelected = viewModel::selectIndex
        )

        ExpiryPicker(
            expiries = uiState.expiries,
            selected = uiState.selectedExpiry,
            onExpirySelected = viewModel::selectExpiry
        )

        if (uiState.isLoading) {
            ShimmerOptionChain()
        } else {
            LazyColumn {
                items(uiState.optionChain) { row ->
                    OptionChainRow(
                        row = row,
                        spotPrice = uiState.spotPrice,
                        onCallClick = { viewModel.onOptionSelected(row.call) },
                        onPutClick = { viewModel.onOptionSelected(row.put) }
                    )
                }
            }
        }
    }
}
```

---

## Phase 5: AI Insights Screen (Week 5)

### 5.1 Features
- AI-powered trade suggestions
- Confidence levels (High/Medium/Low)
- Score reasoning breakdown
- Market sentiment display
- Option analysis deep-dive

### 5.2 Implementation
- Integrate with backend AI analysis API
- Display suggestion cards with entry/target/stoploss
- Show reasoning modal with score breakdown
- Animate confidence indicators

---

## Phase 6: Paper Trading (Week 6)

### 6.1 Features
- Virtual portfolio management
- Execute buy/sell trades
- Real-time P&L tracking
- Position management
- Trade history
- Performance metrics (win rate, Sharpe ratio)

### 6.2 Sub-screens
- Positions tab
- Trade History tab
- Performance tab
- Trade execution bottom sheet

---

## Phase 7: Price Alerts (Week 7)

### 7.1 Features
- Create spot price alerts
- Create option premium alerts
- Create PCR alerts
- Push notifications when triggered
- Alert management (edit/delete/toggle)
- Notification history

### 7.2 Push Notifications
- Firebase Cloud Messaging integration
- Background alert checking service
- Rich notifications with action buttons

---

## Phase 8: Strategy Builder (Week 8)

### 8.1 Features
- 14+ predefined strategies
- Custom multi-leg builder
- Payoff diagram visualization
- Max profit/loss calculation
- Breakeven points
- Greeks aggregation
- Risk/reward ratio

### 8.2 Charts
- Use Vico library for payoff charts
- Interactive zoom/pan
- Strike price markers

---

## Phase 9: OI Analysis Dashboard (Week 9)

### 9.1 Features
- OI Heatmap visualization
- Support/Resistance zones
- PCR calculation and gauge
- Max Pain calculation
- Smart Money activity detection
- IV Surface (optional)

### 9.2 Visualizations
- Custom Canvas composables for heatmap
- Gauge component for PCR
- Zone markers on chart

---

## Phase 10: IPO Dashboard (Week 10)

### 10.1 Features
- IPO listings with status
- Gray Market Premium (GMP)
- AI verdict (Subscribe/Avoid/Neutral)
- Status filters
- IPO details view

---

## Phase 11: Calculator & Greeks (Week 11)

### 11.1 Features
- Black-Scholes option pricing
- Greeks calculation (Delta, Gamma, Theta, Vega)
- Interactive inputs
- Real-time calculation
- Educational tooltips

### 11.2 Implementation
```kotlin
object BlackScholesEngine {
    fun calculateOptionPrice(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        volatility: Double,
        riskFreeRate: Double = 0.07,
        optionType: OptionType
    ): OptionPriceResult { ... }

    fun calculateGreeks(...): GreeksResult { ... }
}
```

---

## Phase 12: Chat Assistant (Week 12)

### 12.1 Features
- AI-powered trading assistant
- Context-aware responses
- Streaming message support
- Session management
- Suggested questions
- Floating chat button

---

## Phase 13: Education Hub (Week 13)

### 13.1 Features
- Trading lessons
- Greeks education
- Strategy tutorials
- Interactive quizzes
- Progress tracking

---

## Phase 14: Settings & Localization (Week 14)

### 14.1 Features
- Theme selection (Light/Dark/System)
- Language selection (9 languages)
- Broker integration settings
- Notification preferences
- About & Support

### 14.2 Localization
- Hindi, Bengali, Kannada, Malayalam
- Odia, Punjabi, Tamil, Telugu
- 100+ strings per language

---

## Phase 15: Polish & Testing (Week 15-16)

### 15.1 UI Polish
- Animations and transitions
- Loading states (shimmer)
- Error handling UI
- Empty states
- Pull-to-refresh

### 15.2 Testing
- Unit tests for UseCases
- Unit tests for ViewModels
- Repository tests with mocks
- UI tests with Compose Testing
- Integration tests

### 15.3 Performance
- ProGuard/R8 optimization
- Image optimization
- Lazy loading
- Memory leak detection

---

## Tech Stack Summary

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| Networking | Retrofit + OkHttp |
| WebSocket | OkHttp WebSocket |
| Local DB | Room |
| Preferences | DataStore |
| Async | Coroutines + Flow |
| Navigation | Navigation Compose |
| Charts | Vico |
| Image Loading | Coil |
| Push Notifications | Firebase Cloud Messaging |
| Auth | Google Sign-In |
| Serialization | Kotlinx Serialization |

---

## Key Differences from iOS

| Aspect | iOS | Android |
|--------|-----|---------|
| UI Framework | SwiftUI | Jetpack Compose |
| State Management | @Published, Combine | StateFlow, SharedFlow |
| DI | Manual Singletons | Hilt |
| Navigation | NavigationStack | Navigation Compose |
| Local Storage | UserDefaults, Keychain | DataStore, EncryptedSharedPreferences |
| Networking | URLSession | Retrofit + OkHttp |
| WebSocket | URLSessionWebSocketTask | OkHttp WebSocket |

---

## Estimated Timeline

| Phase | Duration | Features |
|-------|----------|----------|
| 1 | Week 1 | Project Setup & Architecture |
| 2 | Week 2 | Core Infrastructure |
| 3 | Week 3 | Authentication |
| 4 | Week 4 | Option Chain |
| 5 | Week 5 | AI Insights |
| 6 | Week 6 | Paper Trading |
| 7 | Week 7 | Price Alerts |
| 8 | Week 8 | Strategy Builder |
| 9 | Week 9 | OI Analysis |
| 10 | Week 10 | IPO Dashboard |
| 11 | Week 11 | Calculator |
| 12 | Week 12 | Chat Assistant |
| 13 | Week 13 | Education |
| 14 | Week 14 | Settings & i18n |
| 15-16 | Week 15-16 | Testing & Polish |

**Total: ~16 weeks (4 months)**

---

## Getting Started

1. Create new Android project in Android Studio
2. Set up Hilt DI
3. Create package structure as outlined
4. Implement domain models
5. Set up Retrofit and WebSocket
6. Start with Auth flow
7. Build Option Chain screen
8. Continue with remaining features

---

## Resources

- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Clean Architecture Guide](https://developer.android.com/topic/architecture)
- [Hilt Documentation](https://dagger.dev/hilt/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Vico Charts](https://github.com/patrykandpatrick/vico)
