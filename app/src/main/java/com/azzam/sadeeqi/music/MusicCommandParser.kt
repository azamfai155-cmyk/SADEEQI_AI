package com.azzam.sadeeqi.music

sealed interface MusicCommand {
    data object Pause : MusicCommand
    data object Resume : MusicCommand
    data object Next : MusicCommand
    data object Previous : MusicCommand
    data object VolumeUp : MusicCommand
    data object VolumeDown : MusicCommand
    data class Search(val query: String) : MusicCommand
}

object MusicCommandParser {
    fun parse(raw: String): MusicCommand? {
        val text = raw.trim().lowercase()
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ى", "ي")
        if (text in listOf("وقف", "توقف", "ايقاف", "اوقف الموسيقي", "اوقف الموسيقى", "وقف الموسيقى", "اوقف الاغنيه", "اوقف الاغنية")) return MusicCommand.Pause
        if (text in listOf("كمل", "استأنف", "استأنف الموسيقى", "تابع الموسيقى", "شغل الموسيقى", "تشغيل الموسيقى")) return MusicCommand.Resume
        if (text.contains("الاغنيه التاليه") || text.contains("الاغنية التالية") || text == "التالي" || text == "اللي بعده") return MusicCommand.Next
        if (text.contains("الاغنيه السابقه") || text.contains("الاغنية السابقة") || text == "السابق" || text == "اللي قبلها") return MusicCommand.Previous
        if (text.contains("ارفع الصوت") || text.contains("علي الصوت") || text.contains("زود الصوت")) return MusicCommand.VolumeUp
        if (text.contains("خفض الصوت") || text.contains("وطي الصوت") || text.contains("قلل الصوت")) return MusicCommand.VolumeDown
        val searchPhrases = listOf("ابحث عن موسيقى", "ابحث عن اغنيه", "ابحث عن أغنية", "شغل موسيقى", "شغل اغنيه", "شغل أغنية", "ابحث عن")
        val phrase = searchPhrases.firstOrNull { text.startsWith(it) }
        if (phrase != null) {
            val query = raw.trim().drop(phrase.length).trim().trimStart(':', '،', ' ')
            return MusicCommand.Search(query.ifBlank { "موسيقى" })
        }
        return null
    }
}
