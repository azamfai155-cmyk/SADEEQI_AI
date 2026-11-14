
package com.azzam.sadeeqi.core

import com.azzam.sadeeqi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiGateway(
    private val baseUrl: String = BuildConfig.SADEEQI_API_BASE_URL,
    private val appToken: String = BuildConfig.SADEEQI_APP_TOKEN
) {
    @Volatile private var activeCall: okhttp3.Call? = null

    fun cancelCurrentRequest() {
        activeCall?.cancel()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Turn(val role: String, val content: String)

    fun reply(history: List<Turn>, imageDataUrl: String? = null): String {
        require(baseUrl.startsWith("https://")) {
            "اضبط عنوان HTTPS لخادم صديقي أولاً."
        }

        require(appToken.isNotBlank()) {
            "لم يتم ضبط رمز التطبيق للخادم."
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
                    .put(
                        JSONObject()
                            .put("type", "input_text")
                            .put("text", turn.content)
                    )
                    .put(
                        JSONObject()
                            .put("type", "input_image")
                            .put("image_url", imageDataUrl)
                    )
            } else {
                turn.content
            }

            input.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", content)
            )
        }

        val payload = JSONObject()
            .put("input", input)
            .put("enable_web_search", true)

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/chat")
            .header("Authorization", "Bearer $appToken")
            .header("Content-Type", "application/json")
            .post(
                payload.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        val call = http.newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "الخادم رفض الطلب (${response.code}): " +
                            runCatching {
                                JSONObject(body).optString("error")
                            }.getOrDefault("خطأ اتصال")
                    )
                }

                return JSONObject(body)
                    .optString("output_text")
                    .takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(
                        "وصل رد فارغ من محرك الذكاء الاصطناعي."
                    )
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }
}
