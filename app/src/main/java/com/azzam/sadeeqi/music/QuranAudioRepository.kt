package com.azzam.sadeeqi.music

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Retrieves verse-by-verse recitation from AlQuran Cloud's public audio edition.
 * The app streams provider URLs and does not download or redistribute Quran audio.
 */
class QuranAudioRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    fun loadSurah(surahNumber: Int, reciterEdition: String = DEFAULT_RECITER): List<RemoteMusicTrack> {
        require(surahNumber in 1..114) { "رقم السورة غير صحيح." }
        val url = "https://api.alquran.cloud/v1/surah/$surahNumber/$reciterEdition"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("تعذر تحميل التلاوة (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val data = root.optJSONObject("data") ?: error("استجابة مصدر القرآن غير مكتملة.")
            val surahName = data.optString("name", "السورة $surahNumber")
            val ayahs = data.optJSONArray("ayahs") ?: error("لم تصل آيات السورة.")
            return buildList {
                for (index in 0 until ayahs.length()) {
                    val ayah = ayahs.optJSONObject(index) ?: continue
                    val audio = ayah.optString("audio").takeIf { it.startsWith("https://") } ?: continue
                    val number = ayah.optInt("numberInSurah", index + 1)
                    add(
                        RemoteMusicTrack(
                            id = "quran-$surahNumber-$number",
                            title = "$surahName — الآية $number",
                            artist = RECITER_LABELS[reciterEdition] ?: reciterEdition,
                            durationSeconds = 0L,
                            streamUrl = audio,
                            artworkUrl = null,
                            permalink = null
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_RECITER = "ar.alafasy"
        private val RECITER_LABELS = mapOf(
            "ar.alafasy" to "مشاري العفاسي",
            "ar.abdurrahmaansudais" to "عبدالرحمن السديس",
            "ar.husary" to "محمود خليل الحصري",
            "ar.minshawi" to "محمد صديق المنشاوي"
        )
    }
}
