package com.azzam.sadeeqi.music

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Searches Wikimedia Commons audio files and accepts only reuse-friendly declared licenses. */
class WikimediaCommonsAudioRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    fun search(query: String, limit: Int = 15): List<RemoteMusicTrack> {
        require(query.isNotBlank()) { "اكتب عبارة للبحث." }
        val url = Uri.parse(API).buildUpon()
            .appendQueryParameter("action", "query")
            .appendQueryParameter("generator", "search")
            .appendQueryParameter("gsrsearch", query.trim())
            .appendQueryParameter("gsrnamespace", "6")
            .appendQueryParameter("gsrlimit", limit.coerceIn(1, 30).toString())
            .appendQueryParameter("prop", "imageinfo")
            .appendQueryParameter("iiprop", "url|mediatype|extmetadata")
            .appendQueryParameter("iiextmetadatafilter", "LicenseShortName|LicenseUrl|UsageTerms|Artist|Credit")
            .appendQueryParameter("format", "json")
            .appendQueryParameter("formatversion", "2")
            .build().toString()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "SadeeqiAI/1.0 (Android audio catalogue; contact: support@sadeeqi.app)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Wikimedia Commons أعاد خطأ HTTP ${response.code}")
            val pages = JSONObject(response.body?.string().orEmpty()).optJSONObject("query")
                ?.optJSONArray("pages") ?: return emptyList()
            return buildList {
                for (index in 0 until pages.length()) {
                    val page = pages.optJSONObject(index) ?: continue
                    val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                    if (info.optString("mediatype") != "AUDIO") continue
                    val mediaUrl = info.optString("url").takeIf { it.startsWith("https://") } ?: continue
                    val metadata = info.optJSONObject("extmetadata") ?: continue
                    fun field(name: String): String? = metadata.optJSONObject(name)?.optString("value")
                        ?.replace(Regex("<[^>]*>"), " ")?.replace("&nbsp;", " ")?.trim()
                        ?.takeIf { it.isNotBlank() && it != "undefined" }
                    val license = field("LicenseShortName") ?: field("UsageTerms") ?: continue
                    val normalized = license.lowercase()
                    val hasAllowedLicense = normalized == "cc0" ||
                        normalized.contains("public domain") ||
                        (normalized.startsWith("cc by") &&
                            !normalized.contains("nc") && !normalized.contains("nd"))
                    if (!hasAllowedLicense) continue
                    val title = page.optString("title").removePrefix("File:")
                    val artist = field("Artist") ?: field("Credit") ?: "مساهم في ويكيميديا"
                    add(RemoteMusicTrack(
                        id = "commons-${page.optLong("pageid")}",
                        title = title.ifBlank { "ملف صوتي من ويكيميديا" },
                        artist = artist,
                        durationSeconds = 0L,
                        streamUrl = mediaUrl,
                        artworkUrl = null,
                        permalink = info.optString("descriptionurl").takeIf { it.startsWith("https://") },
                        sourceName = "Wikimedia Commons",
                        license = license,
                        licenseUrl = field("LicenseUrl"),
                        attribution = field("Credit") ?: field("Artist")
                    ))
                }
            }
        }
    }

    companion object {
        private const val API = "https://commons.wikimedia.org/w/api.php"
    }
}