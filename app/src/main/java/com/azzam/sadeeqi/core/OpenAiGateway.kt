package com.azzam.sadeeqi.core

import com.azzam.sadeeqi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiGateway(private val baseUrl: String = BuildConfig.SADEEQI_API_BASE_URL) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).build()

    data class Turn(val role: String, val content: String)
    data class Health(val configured: Boolean, val version: String, val runtime: String)

    fun health(): Health {
        require(baseUrl.startsWith("https://")) { "اضبط عنوان HTTPS لخادم صديقي أولاً." }
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/health").get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("فشل فحص الخادم (${response.code}).")
            val data = JSONObject(body)
            return Health(data.optBoolean("configured", false), data.optString("version", "غير معروف"),
                data.optString("runtime", "غير معروف"))
        }
    }

    private fun createSession(): String {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/v1/session")
            .header("Content-Type", "application/json")
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(
                "تعذر إنشاء جلسة آمنة (${response.code}): " +
                    runCatching { JSONObject(body).optString("error") }.getOrDefault("خطأ اتصال"))
            return JSONObject(body).optString("token").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("لم يُصدر الخادم رمز جلسة صالحاً.")
        }
    }

    fun reply(history: List<Turn>, imageDataUrl: String? = null): String {
        require(baseUrl.startsWith("https://")) { "اضبط عنوان HTTPS لخادم صديقي أولاً." }
        val input = JSONArray()
        val recentHistory = history.takeLast(24)
        recentHistory.forEachIndexed { index, turn ->
            val content = if (index == recentHistory.lastIndex && turn.role == "user" && imageDataUrl != null) {
                JSONArray().put(JSONObject().put("type", "input_text").put("text", turn.content))
                    .put(JSONObject().put("type", "input_image").put("image_url", imageDataUrl))
            } else turn.content
            input.put(JSONObject().put("role", turn.role).put("content", content))
        }
        val payload = JSONObject().put("input", input).put("enable_web_search", true)
        val sessionToken = createSession()
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/v1/chat")
            .header("Authorization", "Bearer $sessionToken").header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(
                "الخادم رفض الطلب (${response.code}): " +
                    runCatching { JSONObject(body).optString("error") }.getOrDefault("خطأ اتصال"))
            return JSONObject(body).optString("output_text").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("وصل رد فارغ من محرك الذكاء الاصطناعي.")
        }
    }
}
