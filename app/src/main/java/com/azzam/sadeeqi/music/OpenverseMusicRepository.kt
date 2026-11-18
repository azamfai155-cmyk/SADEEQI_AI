package com.azzam.sadeeqi.music

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Openverse indexes openly licensed audio; keep original source and license metadata. */
class OpenverseMusicRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    fun search(query: String, limit: Int = 20): List<RemoteMusicTrack> {
        require(query.isNotBlank()) { "اكتب عبارة للبحث." }
        val url = Uri.parse(BASE_URL).buildUpon().appendQueryParameter("q", query.trim())
            .appendQueryParameter("page_size", limit.coerceIn(1, 30).toString())
            .appendQueryParameter("mature", "false").build().toString()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Openverse أعاد خطأ HTTP ${response.code}")
            val results = JSONObject(response.body?.string().orEmpty()).optJSONArray("results") ?: return emptyList()
            return buildList {
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val license = item.optString("license").lowercase()
                    if (license !in ALLOWED_LICENSES) continue
                    val stream = item.optString("url").takeIf { it.startsWith("https://") } ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(RemoteMusicTrack(
                        id = "openverse-$id",
                        title = item.optString("title").ifBlank { "تسجيل صوتي بدون عنوان" },
                        artist = item.optString("creator").ifBlank { "صاحب العمل غير محدد" },
                        durationSeconds = item.optLong("duration", 0L).coerceAtLeast(0L),
                        streamUrl = stream,
                        artworkUrl = item.optString("thumbnail").takeIf { it.startsWith("https://") },
                        permalink = item.optString("foreign_landing_url").takeIf { it.startsWith("https://") },
                        sourceName = "Openverse · ${item.optString("source").ifBlank { "مكتبة مفتوحة" }}",
                        license = license,
                        licenseUrl = item.optString("license_url").takeIf { it.startsWith("https://") },
                        attribution = item.optString("attribution").takeIf { it.isNotBlank() }
                    ))
                }
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.openverse.org/v1/audio/"
        private val ALLOWED_LICENSES = setOf("cc0", "by", "by-sa", "pdm")
    }
}