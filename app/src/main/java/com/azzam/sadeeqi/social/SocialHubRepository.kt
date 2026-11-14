package com.azzam.sadeeqi.social

import com.azzam.sadeeqi.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SocialHubRepository(private val accessTokenProvider: () -> String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun loadProviders(): SocialProviderResponse {
        require(BuildConfig.SADEEQI_API_BASE_URL.startsWith("https://")) {
            "عنوان الخادم غير آمن أو غير مضبوط."
        }
        val accessToken = accessTokenProvider()
        require(accessToken.isNotBlank()) {
            "انتهت جلسة تسجيل الدخول. سجّل الدخول مجدداً."
        }

        val request = Request.Builder()
            .url(BuildConfig.SADEEQI_API_BASE_URL.trimEnd('/') + "/v1/social/providers")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("error") }
                    .getOrDefault("HTTP ${response.code}")
                error("تعذر تحميل منصات التواصل (${response.code}): $message")
            }
            val root = JSONObject(body)
            val array = root.optJSONArray("providers") ?: error("استجابة المنصات غير مكتملة.")
            val providers = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                SocialProvider(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    family = item.optString("family"),
                    auth = item.optString("auth"),
                    note = item.optString("note"),
                    configured = item.optBoolean("configured"),
                    connected = item.optBoolean("connected")
                )
            }
            return SocialProviderResponse(
                providers = providers,
                connectedCount = root.optInt("connectedCount", 0),
                configuredCount = root.optInt("configuredCount", providers.count { it.configured }),
                notice = root.optString("notice", "حالة الربط محدثة من الخادم.")
            )
        }
    }
}
