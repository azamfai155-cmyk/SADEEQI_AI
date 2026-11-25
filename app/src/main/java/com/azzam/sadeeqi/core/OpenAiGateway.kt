
package com.azzam.sadeeqi.core

import com.azzam.sadeeqi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class OpenAiGateway(
    private val baseUrl: String = BuildConfig.SADEEQI_API_BASE_URL,
    private val accessTokenProvider: () -> String
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
    data class Source(val title: String, val url: String)
    data class ChatReply(val text: String, val sources: List<Source>)

    internal fun parseSources(body: String): List<Source> {
        val sourceArray = runCatching { JSONObject(body).optJSONArray("sources") }.getOrNull()
            ?: return emptyList()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Source>()
        for (index in 0 until minOf(sourceArray.length(), 48)) {
            val item = sourceArray.optJSONObject(index) ?: continue
            val rawUrl = item.optString("url").trim()
            val uri = runCatching { URI(rawUrl) }.getOrNull() ?: continue
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                uri.host.isNullOrBlank() || uri.userInfo != null) continue
            val normalized = runCatching { uri.toASCIIString() }.getOrNull() ?: continue
            if (!seen.add(normalized)) continue
            val title = item.optString("title").trim()
                .takeIf { it.isNotBlank() }?.take(240) ?: uri.host
            result += Source(title, normalized)
            if (result.size == 12) break
        }
        return result
    }

    fun reply(history: List<Turn>, imageDataUrl: String? = null): ChatReply {
        require(baseUrl.startsWith("https://")) {
            "اضبط عنوان HTTPS لخادم صديقي أولاً."
        }

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

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/chat")
            .header("Authorization", "Bearer $accessToken")
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

                val answer = JSONObject(body).optString("output_text")
                    .takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(
                        "وصل رد فارغ من محرك الذكاء الاصطناعي."
                    )
                return ChatReply(answer, parseSources(body))
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }
}
