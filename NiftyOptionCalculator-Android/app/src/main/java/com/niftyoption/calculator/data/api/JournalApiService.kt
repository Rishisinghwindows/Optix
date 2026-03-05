package com.niftyoption.calculator.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.niftyoption.calculator.data.models.JournalCreateRequest
import com.niftyoption.calculator.data.models.JournalEntry
import com.niftyoption.calculator.data.models.JournalListResponse
import com.niftyoption.calculator.data.models.JournalStats
import com.niftyoption.calculator.data.models.JournalUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class JournalApiService {

    companion object {
        private const val BASE_URL = "https://api.optix.d23ai.in"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    var authToken: String? = null

    // MARK: - Journal Entry CRUD

    suspend fun createEntry(request: JournalCreateRequest): JournalEntry = withContext(Dispatchers.IO) {
        val json = gson.toJson(request)
        val body = json.toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url("$BASE_URL/api/v1/journal/entries")
            .post(body)
            .addAuthHeader()
            .build()

        executeRequest<JournalEntry>(httpRequest)
    }

    suspend fun getEntries(
        outcome: String? = null,
        symbol: String? = null,
        tag: String? = null,
        skip: Int = 0,
        limit: Int = 50
    ): JournalListResponse = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("$BASE_URL/api/v1/journal/entries?skip=$skip&limit=$limit")
        outcome?.let { urlBuilder.append("&outcome=$it") }
        symbol?.let { urlBuilder.append("&symbol=$it") }
        tag?.let { urlBuilder.append("&tag=$it") }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .get()
            .addAuthHeader()
            .build()

        executeRequest<JournalListResponse>(request)
    }

    suspend fun getEntry(id: String): JournalEntry = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/journal/entries/$id")
            .get()
            .addAuthHeader()
            .build()

        executeRequest<JournalEntry>(request)
    }

    suspend fun updateEntry(id: String, update: JournalUpdateRequest): JournalEntry = withContext(Dispatchers.IO) {
        val json = gson.toJson(update)
        val body = json.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/journal/entries/$id")
            .put(body)
            .addAuthHeader()
            .build()

        executeRequest<JournalEntry>(request)
    }

    suspend fun deleteEntry(id: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/journal/entries/$id")
            .delete()
            .addAuthHeader()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException(response.code, response.body?.string() ?: "Delete failed")
        }
    }

    // MARK: - Journal Stats

    suspend fun getStats(): JournalStats = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/journal/stats")
            .get()
            .addAuthHeader()
            .build()

        executeRequest<JournalStats>(request)
    }

    // MARK: - Push Notification Device Registration

    suspend fun registerDevice(
        token: String,
        platform: String = "android",
        deviceName: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        val payload = mutableMapOf<String, String>(
            "token" to token,
            "platform" to platform
        )
        deviceName?.let { payload["device_name"] = it }

        val json = gson.toJson(payload)
        val body = json.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/notifications/devices")
            .post(body)
            .addAuthHeader()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException(response.code, response.body?.string() ?: "Device registration failed")
        }
    }

    suspend fun unregisterDevice(token: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/notifications/devices/$token")
            .delete()
            .addAuthHeader()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException(response.code, response.body?.string() ?: "Device unregistration failed")
        }
    }

    // MARK: - Helpers

    private fun Request.Builder.addAuthHeader(): Request.Builder {
        authToken?.let { token ->
            addHeader("Authorization", "Bearer $token")
        }
        return this
    }

    private inline fun <reified T> executeRequest(request: Request): T {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            throw ApiException(response.code, responseBody ?: "Request failed")
        }

        if (responseBody.isNullOrBlank()) {
            throw ApiException(0, "Empty response body")
        }

        return try {
            gson.fromJson(responseBody, object : TypeToken<T>() {}.type)
        } catch (e: Exception) {
            throw ApiException(0, "Failed to parse response: ${e.message}")
        }
    }
}

class ApiException(val code: Int, override val message: String) : IOException(message)
