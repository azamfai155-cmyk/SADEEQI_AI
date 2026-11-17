package com.azzam.sadeeqi.core

import com.azzam.sadeeqi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

class OpenAiGateway(
    private val baseUrl: String = BuildConfig.SADEEQI_API_BASE_URL,
    private val accessTokenProvider: () -> String,
    private val fallbackBaseUrls: List<String> = BuildConfig.SADEEQI_API_FALLBACK_URLS
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
) {
    private val activeCalls = ConcurrentHashMap.newKeySet<okhttp3.Call>()
    private val cancellationGeneration = AtomicLong(0)

    fun cancelCurrentRequest() {
        // Increment first so a call being prepared concurrently cannot escape cancellation.
        cancellationGeneration.incrementAndGet()
        activeCalls.forEach { it.cancel() }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Turn(val role: String, val content: String)

    private fun endpoints(): List<String> {
        val candidates = (listOf(baseUrl) + fallbackBaseUrls)
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("https://") }
        return candidates.distinct().also {
            require(it.isNotEmpty()) { "اضبط عنوان HTTPS واحداً على الأقل لخوادم صديقي." }
        }
    }

    fun reply(history: List<Turn>, imageDataUrl: String? = null, enableWebSearch: Boolean = false): String {
        val requestGeneration = cancellationGeneration.get()
        val accessToken = accessTokenProvider()
        require(accessToken.isNotBlank()) {
            "انتهت جلسة تسجيل الدخول. سجّل الدخول مجدداً."
        }

        val input = JSONArray()
        val recentHistory = history.takeLast(24)

        recentHistory.forEachIndexed { index, turn ->
            val content = if (
                index == recentHistory.lastIndex &&
                turn.role == "user" &&
                imageDataUrl != null
            ) {
                JSONArray()
                    .put(JSONObject().put("type", "input_text").put("text", turn.content))
                    .put(JSONObject().put("type", "input_image").put("image_url", imageDataUrl))
            } else {
                turn.content
            }

            input.put(JSONObject().put("role", turn.role).put("content", content))
        }

        val payload = JSONObject()
            .put("input", input)
            .put("enable_web_search", enableWebSearch)
        val body = payload.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )

        var lastFailure: Exception? = null
        val candidates = endpoints()

        for ((index, endpoint) in candidates.withIndex()) {
            if (requestGeneration != cancellationGeneration.get()) {
                throw IOException("تم إلغاء الطلب.")
            }

            val request = Request.Builder()
                .url("$endpoint/v1/chat")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            val call = http.newCall(request)
            activeCalls.add(call)
            if (requestGeneration != cancellationGeneration.get()) call.cancel()

            try {
                call.execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            JSONObject(responseBody).optString("error")
                        }.getOrDefault("خطأ اتصال")
                        val failure = IOException("الخادم رفض الطلب (${response.code}): $message")
                        // Do not retry authentication, validation, or other client errors.
                        if (response.code in 500..599 || response.code == 429) {
                            lastFailure = failure
                            if (index < candidates.lastIndex) continue
                        }
                        throw failure
                    }

                    val output = runCatching {
                        JSONObject(responseBody).optString("output_text")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    if (output != null) return output

                    lastFailure = IOException("وصل رد فارغ أو غير صالح من الخادم.")
                    if (index < candidates.lastIndex) continue
                    throw lastFailure!!
                }
            } catch (failure: IOException) {
                if (requestGeneration != cancellationGeneration.get()) throw failure
                lastFailure = failure
                if (index == candidates.lastIndex) throw failure
                // A network failure or server-side failure may be recovered by the next host.
            } finally {
                activeCalls.remove(call)
            }
        }

        throw lastFailure ?: IOException("تعذر الاتصال بجميع خوادم صديقي.")
    }
}
