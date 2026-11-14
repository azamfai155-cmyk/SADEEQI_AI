package com.azzam.sadeeqi.auth

import android.content.Context
import com.azzam.sadeeqi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseAuthClient(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean =
        BuildConfig.SADEEQI_SUPABASE_URL.startsWith("https://") &&
            BuildConfig.SADEEQI_SUPABASE_ANON_KEY.isNotBlank()

    fun signIn(email: String, password: String) = authenticate(
        "/auth/v1/token?grant_type=password", email, password
    )

    fun signUp(email: String, password: String) = authenticate(
        "/auth/v1/signup", email, password
    )

    fun accessToken(): String = synchronized(REFRESH_LOCK) {
        // Re-read only after acquiring the process-wide lock: another request may
        // already have rotated the refresh token and saved a newer session.
        val session = EncryptedSessionStore.read(context)
            ?: error("سجّل الدخول إلى حسابك أولاً.")
        val now = System.currentTimeMillis() / 1000L
        if (session.optLong("expires_at", 0L) > now + 60L) {
            return@synchronized session.optString("access_token").takeIf { it.isNotBlank() }
                ?: error("جلسة الدخول غير صالحة.")
        }
        val refreshToken = session.optString("refresh_token")
        require(refreshToken.isNotBlank()) { "انتهت الجلسة. سجّل الدخول مجدداً." }
        val body = JSONObject().put("refresh_token", refreshToken)
        val request = Request.Builder()
            .url(BuildConfig.SADEEQI_SUPABASE_URL.trimEnd('/') + "/auth/v1/token?grant_type=refresh_token")
            .header("apikey", BuildConfig.SADEEQI_SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || !result.has("access_token")) {
                // A transient server/rate-limit response must not erase a valid
                // refresh token. Clear only when Supabase rejects the token itself.
                if (response.code == 400 || response.code == 401) {
                    EncryptedSessionStore.clear(context)
                    error("انتهت الجلسة. سجّل الدخول مجدداً.")
                }
                if (response.code == 429 || response.code >= 500) {
                    error("تعذر تجديد الجلسة مؤقتاً. تحقق من الإنترنت وحاول مجدداً.")
                }
                error("تعذر تجديد الجلسة. حاول تسجيل الدخول مجدداً.")
            }
            val refreshedAt = System.currentTimeMillis() / 1000L
            result.put("expires_at", refreshedAt + result.optLong("expires_in", 3600L))
            EncryptedSessionStore.save(context, result)
            result.getString("access_token")
        }
    }

    private fun authenticate(path: String, email: String, password: String): JSONObject {
        require(isConfigured()) { "لم يتم إعداد Supabase بعد. أضف عنوان المشروع والمفتاح العام في إعدادات البناء." }
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val request = Request.Builder()
            .url(BuildConfig.SADEEQI_SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SADEEQI_SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val message = result.optString("msg").ifBlank {
                    result.optString("message").ifBlank { result.optString("error_description", "تعذر تسجيل الدخول.") }
                }
                error(message)
            }
            if (result.has("access_token")) {
                val expiresIn = result.optLong("expires_in", 3600L)
                result.put("expires_at", System.currentTimeMillis() / 1000L + expiresIn)
                EncryptedSessionStore.save(context, result)
            }
            return result
        }
    }
    companion object {
        private val REFRESH_LOCK = Any()
    }
}
