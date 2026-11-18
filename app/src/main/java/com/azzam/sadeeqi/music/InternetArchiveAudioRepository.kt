package com.azzam.sadeeqi.music

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Internet Archive audio items are included only when item metadata declares an approved reuse license. */
class InternetArchiveAudioRepository {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build()

    fun search(query: String, limit: Int = 8): List<RemoteMusicTrack> {
        require(query.isNotBlank()) { "اكتب عبارة للبحث." }
        val safeQuery = query.trim().split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}_-]"), "") }
            .filter { it.isNotBlank() }.joinToString(" ").take(100)
        if (safeQuery.isBlank()) return emptyList()
        val lucene = "mediatype:audio AND (licenseurl:http*by* OR licenseurl:http*publicdomain*) AND ($safeQuery)"
        val searchUrl = Uri.parse(SEARCH_URL).buildUpon()
            .appendQueryParameter("q", lucene)
            .appendQueryParameter("fl[]", "identifier")
            .appendQueryParameter("fl[]", "title")
            .appendQueryParameter("fl[]", "creator")
            .appendQueryParameter("fl[]", "licenseurl")
            .appendQueryParameter("rows", limit.coerceIn(1, 12).toString())
            .appendQueryParameter("output", "json")
            .build().toString()
        val request = Request.Builder().url(searchUrl).header("Accept", "application/json").build()
        val docs = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Internet Archive search HTTP ${response.code}")
            JSONObject(response.body?.string().orEmpty()).optJSONObject("response")?.optJSONArray("docs")
                ?: JSONArray()
        }
        return buildList {
            for (index in 0 until docs.length()) {
                val doc = docs.optJSONObject(index) ?: continue
                val identifier = doc.optString("identifier").takeIf { it.matches(Regex("[A-Za-z0-9_-]{3,100}")) } ?: continue
                runCatching { loadPlayableItem(identifier) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun loadPlayableItem(identifier: String): RemoteMusicTrack? {
        val request = Request.Builder().url("$METADATA_URL/$identifier").header("Accept", "application/json").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string().orEmpty())
            val metadata = root.optJSONObject("metadata") ?: return null
            val licenseUrl = metadata.optString("licenseurl").takeIf { it.startsWith("http") } ?: return null
            if (!isApprovedLicense(licenseUrl)) return null
            val files = root.optJSONArray("files") ?: return null
            var chosen: JSONObject? = null
            for (i in 0 until files.length()) {
                val file = files.optJSONObject(i) ?: continue
                val name = file.optString("name")
                val format = file.optString("format").lowercase()
                val audioExtension = name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
                val audioFormat = listOf("mp3", "mpeg", "ogg", "vorbis", "flac", "wave", "wav", "m4a", "aac")
                    .any { format.contains(it) }
                if (audioExtension && audioFormat && !name.endsWith("_sample.mp3")) {
                    chosen = file
                    if (format.contains("vbr mp3") || format == "mp3") break
                }
            }
            val file = chosen ?: return null
            val fileName = file.optString("name").takeIf { it.isNotBlank() } ?: return null
            val streamUrl = Uri.parse("$DOWNLOAD_URL/$identifier/").buildUpon()
                .appendPath(fileName).build().toString()
            val creator = metadata.optString("creator").ifBlank { "مساهم في Internet Archive" }
            val title = metadata.optString("title").ifBlank { identifier }
            val duration = file.optString("length").toDoubleOrNull()?.toLong()?.coerceAtLeast(0L) ?: 0L
            RemoteMusicTrack(
                id = "archive-$identifier-${fileName.hashCode()}",
                title = title,
                artist = creator,
                durationSeconds = duration,
                streamUrl = streamUrl,
                artworkUrl = null,
                permalink = "https://archive.org/details/$identifier",
                sourceName = "Internet Archive",
                license = licenseUrl.substringAfterLast('/').ifBlank { "Public license" },
                licenseUrl = licenseUrl,
                attribution = "$title — $creator"
            )
        }
    }

    private fun isApprovedLicense(url: String): Boolean {
        val value = url.lowercase()
        if (value.contains("by-nc") || value.contains("by-nd") || value.contains("/licenses/nc") || value.contains("/licenses/nd")) return false
        return value.contains("/licenses/by/") || value.contains("/licenses/by-sa/") ||
            value.contains("/publicdomain/zero/") || value.contains("/publicdomain/mark/")
    }

    companion object {
        private const val SEARCH_URL = "https://archive.org/advancedsearch.php"
        private const val METADATA_URL = "https://archive.org/metadata"
        private const val DOWNLOAD_URL = "https://archive.org/download"
        private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "oga", "flac", "wav", "wave", "m4a", "aac")
    }
}