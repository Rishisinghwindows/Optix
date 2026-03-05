package com.niftyoption.calculator.data.api

import com.niftyoption.calculator.data.models.ExpiryDate
import com.niftyoption.calculator.data.models.NSEOptionChainResponse
import com.niftyoption.calculator.data.models.OptionChainRow
import com.niftyoption.calculator.data.models.OptionData
import com.niftyoption.calculator.data.models.OptionType
import com.niftyoption.calculator.domain.BlackScholesEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

// MARK: - NSE API Interface

interface NSEApi {
    @GET("api/option-chain-indices")
    suspend fun getOptionChain(@Query("symbol") symbol: String = "NIFTY"): NSEOptionChainResponse
}

// MARK: - NSE API Error Types

sealed class NSEApiError : Exception() {
    data object InvalidURL : NSEApiError()
    data class NetworkError(override val cause: Throwable) : NSEApiError()
    data object InvalidResponse : NSEApiError()
    data class DecodingError(override val cause: Throwable) : NSEApiError()
    data object RateLimited : NSEApiError()
    data class ServerError(val code: Int) : NSEApiError()

    override val message: String
        get() = when (this) {
            InvalidURL -> "Invalid API URL"
            is NetworkError -> "Network error: ${cause.message}"
            InvalidResponse -> "Invalid response from server"
            is DecodingError -> "Data parsing error: ${cause.message}"
            RateLimited -> "Rate limited. Please wait before retrying."
            is ServerError -> "Server error: $code"
        }
}

// MARK: - NSE Repository

class NSERepository {

    private val baseUrl = "https://www.nseindia.com/"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // Cache
    private var cachedData: NSEOptionChainResponse? = null
    private var cacheTimestamp: Long = 0
    private val cacheValidityMs = 30_000L

    // Rate limiting
    private var lastRequestTime: Long = 0
    private val minRequestIntervalMs = 1000L
    private val mutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", baseUrl)
                .header("Cache-Control", "no-cache")
                .build()
            chain.proceed(request)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(NSEApi::class.java)

    // MARK: - Public Methods

    suspend fun fetchOptionChain(symbol: String = "NIFTY", forceRefresh: Boolean = false): Result<NSEOptionChainResponse> {
        return mutex.withLock {
            // Check cache
            if (!forceRefresh && cachedData != null) {
                if (System.currentTimeMillis() - cacheTimestamp < cacheValidityMs) {
                    return@withLock Result.success(cachedData!!)
                }
            }

            // Rate limiting
            val elapsed = System.currentTimeMillis() - lastRequestTime
            if (elapsed < minRequestIntervalMs) {
                delay(minRequestIntervalMs - elapsed)
            }

            try {
                // Warmup request
                warmupSession()

                // Fetch data
                val response = withContext(Dispatchers.IO) {
                    api.getOptionChain(symbol)
                }

                cachedData = response
                cacheTimestamp = System.currentTimeMillis()
                lastRequestTime = System.currentTimeMillis()

                Result.success(response)
            } catch (e: Exception) {
                Result.failure(NSEApiError.NetworkError(e))
            }
        }
    }

    suspend fun fetchOptionChainData(symbol: String = "NIFTY", expiry: String? = null): List<OptionChainRow> {
        return fetchOptionChain(symbol).fold(
            onSuccess = { parseOptionChainResponse(it, expiry) },
            onFailure = { generateMockData() }
        )
    }

    suspend fun fetchExpiryDates(symbol: String = "NIFTY"): List<ExpiryDate> {
        return fetchOptionChain(symbol).fold(
            onSuccess = { response ->
                response.records.expiryDates.map { ExpiryDate.fromString(it) }
            },
            onFailure = { generateMockExpiryDates() }
        )
    }

    suspend fun fetchUnderlyingValue(symbol: String = "NIFTY"): Double {
        return fetchOptionChain(symbol).fold(
            onSuccess = { it.records.underlyingValue },
            onFailure = { 25000.0 }
        )
    }

    // MARK: - Private Methods

    private suspend fun warmupSession() {
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(baseUrl)
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(request).execute().close()
            }
        } catch (e: Exception) {
            // Warmup failure is not critical
        }
    }

    private fun parseOptionChainResponse(
        response: NSEOptionChainResponse,
        expiry: String?
    ): List<OptionChainRow> {
        val dateFormatter = SimpleDateFormat("dd-MMM-yyyy", Locale("en", "IN"))

        // Filter by expiry
        val filteredData = if (expiry != null) {
            response.records.data.filter { it.expiryDate == expiry }
        } else {
            val nearestExpiry = response.records.expiryDates.firstOrNull() ?: ""
            response.records.data.filter { it.expiryDate == nearestExpiry }
        }

        // Group by strike price
        val strikeMap = mutableMapOf<Double, Pair<OptionData?, OptionData?>>()

        for (item in filteredData) {
            val strikePrice = item.strikePrice
            val expiryDate = try {
                dateFormatter.parse(item.expiryDate) ?: Date()
            } catch (e: Exception) {
                Date()
            }

            var entry = strikeMap[strikePrice] ?: (null to null)

            item.ce?.let { ce ->
                entry = entry.copy(
                    first = OptionData(
                        strikePrice = ce.strikePrice,
                        optionType = OptionType.CALL,
                        expiryDate = expiryDate,
                        lastTradedPrice = ce.lastPrice,
                        openInterest = ce.openInterest,
                        changeInOI = ce.changeinOpenInterest,
                        impliedVolatility = ce.impliedVolatility / 100.0,
                        bidPrice = ce.bidprice,
                        askPrice = ce.askPrice,
                        bidQty = ce.bidQty,
                        askQty = ce.askQty,
                        volume = ce.totalTradedVolume,
                        underlyingValue = ce.underlyingValue
                    )
                )
            }

            item.pe?.let { pe ->
                entry = entry.copy(
                    second = OptionData(
                        strikePrice = pe.strikePrice,
                        optionType = OptionType.PUT,
                        expiryDate = expiryDate,
                        lastTradedPrice = pe.lastPrice,
                        openInterest = pe.openInterest,
                        changeInOI = pe.changeinOpenInterest,
                        impliedVolatility = pe.impliedVolatility / 100.0,
                        bidPrice = pe.bidprice,
                        askPrice = pe.askPrice,
                        bidQty = pe.bidQty,
                        askQty = pe.askQty,
                        volume = pe.totalTradedVolume,
                        underlyingValue = pe.underlyingValue
                    )
                )
            }

            strikeMap[strikePrice] = entry
        }

        return strikeMap.map { (strike, options) ->
            OptionChainRow(
                strikePrice = strike,
                callOption = options.first,
                putOption = options.second
            )
        }.sortedBy { it.strikePrice }
    }

    // MARK: - Mock Data Generation

    fun generateMockData(spotPrice: Double = 25000.0, numStrikes: Int = 21): List<OptionChainRow> {
        val strikesInterval = 50.0
        val atmStrike = (spotPrice / strikesInterval).toLong() * strikesInterval
        val startStrike = atmStrike - (numStrikes / 2) * strikesInterval

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val expiryDate = calendar.time
        val timeToExpiry = BlackScholesEngine.timeToExpiry(expiryDate)
        val baseIV = 0.15

        return (0 until numStrikes).map { i ->
            val strike = startStrike + i * strikesInterval

            val callPrice = BlackScholesEngine.calculateCallPrice(
                spotPrice = spotPrice,
                strikePrice = strike,
                timeToExpiry = timeToExpiry,
                volatility = baseIV
            )

            val putPrice = BlackScholesEngine.calculatePutPrice(
                spotPrice = spotPrice,
                strikePrice = strike,
                timeToExpiry = timeToExpiry,
                volatility = baseIV
            )

            val distance = abs(strike - atmStrike)
            val oiFactor = maxOf(0.1, 1.0 - distance / 1000.0)
            val callOI = (Random.nextDouble(50000.0, 200000.0) * oiFactor).toInt()
            val putOI = (Random.nextDouble(50000.0, 200000.0) * oiFactor).toInt()

            val callOption = OptionData(
                strikePrice = strike,
                optionType = OptionType.CALL,
                expiryDate = expiryDate,
                lastTradedPrice = callPrice + Random.nextDouble(-2.0, 2.0),
                openInterest = callOI,
                changeInOI = Random.nextInt(-5000, 10000),
                impliedVolatility = baseIV + Random.nextDouble(-0.02, 0.02),
                bidPrice = callPrice - 1,
                askPrice = callPrice + 1,
                bidQty = Random.nextInt(100, 1000),
                askQty = Random.nextInt(100, 1000),
                volume = Random.nextInt(10000, 100000),
                underlyingValue = spotPrice
            )

            val putOption = OptionData(
                strikePrice = strike,
                optionType = OptionType.PUT,
                expiryDate = expiryDate,
                lastTradedPrice = putPrice + Random.nextDouble(-2.0, 2.0),
                openInterest = putOI,
                changeInOI = Random.nextInt(-5000, 10000),
                impliedVolatility = baseIV + Random.nextDouble(-0.02, 0.02),
                bidPrice = putPrice - 1,
                askPrice = putPrice + 1,
                bidQty = Random.nextInt(100, 1000),
                askQty = Random.nextInt(100, 1000),
                volume = Random.nextInt(10000, 100000),
                underlyingValue = spotPrice
            )

            OptionChainRow(
                strikePrice = strike,
                callOption = callOption,
                putOption = putOption
            )
        }
    }

    private fun generateMockExpiryDates(): List<ExpiryDate> {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())

        return (0 until 4).map { weekOffset ->
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, 7 * weekOffset)
            ExpiryDate.fromString(formatter.format(calendar.time))
        }
    }
}
