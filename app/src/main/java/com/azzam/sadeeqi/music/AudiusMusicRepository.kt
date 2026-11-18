package com.azzam.sadeeqi.music

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteMusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val streamUrl: String,
    val artworkUrl: String?,
    val permalink: String?,
    val sourceName: String = "غير محدد",
    val license: String? = null,
    val licenseUrl: String? = null,
    val attribution: String? = null
)

class AudiusMusicRepository {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()

    fun search(query: String, limit: Int = 8): List<RemoteMusicTrack> {
        require(query.isNotBlank()) { "اكتب اسم أغنية أو فنان للبحث." }
        val url = Uri.parse("$BASE_URL/tracks/search").buildUpon()
            .appendQueryParameter("query", query.trim())
            .appendQueryParameter("limit", limit.coerceIn(1, 20).toString())
            .appendQueryParameter("app_name", APP_NAME).build().toString()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("خدمة Audius أعادت خطأ HTTP ${response.code}")
            val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data")
                ?: throw IllegalStateException("استجابة Audius غير متوقعة.")
            return buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    if (!item.optBoolean("is_streamable", true)) continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val title = item.optString("title").ifBlank { "مقطع بدون عنوان" }
                    val artist = item.optJSONObject("user")?.optString("name")?.ifBlank { "فنان مستقل" } ?: "فنان مستقل"
                    val permalink = item.optString("permalink").takeIf { it.startsWith("/") }?.let { "https://audius.co$it" }
                    add(RemoteMusicTrack(id, title, artist, item.optLong("duration", 0L),
                        Uri.parse("$BASE_URL/tracks/$id/stream").buildUpon()
                            .appendQueryParameter("app_name", APP_NAME).build().toString(),
                        null, permalink, sourceName = "Audius"))
                }
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.audius.co/v1"
        private const val APP_NAME = "sadeeqi_ai"
    }
}