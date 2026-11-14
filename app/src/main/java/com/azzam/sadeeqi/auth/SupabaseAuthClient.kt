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
}
