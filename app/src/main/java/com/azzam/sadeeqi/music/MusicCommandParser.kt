package com.azzam.sadeeqi.music

sealed interface MusicCommand {
    data object Pause : MusicCommand
    data object Resume : MusicCommand
    data object Next : MusicCommand
    data object Previous : MusicCommand
    data object VolumeUp : MusicCommand
    data object VolumeDown : MusicCommand
    data object OpenQuran : MusicCommand
    data object PlayRuqyah : MusicCommand
    data class PlayQuran(val surahNumber: Int, val surahName: String) : MusicCommand
    data class Search(val query: String) : MusicCommand
}

object MusicCommandParser {
    private val surahs = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
        "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء",
        "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
        "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر",
        "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان",
        "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم",
        "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف",
        "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة",
        "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات",
        "النبأ", "النازعات", "عبس", "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج",
        "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى",
        "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة",
        "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون",
        "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
    )

    private fun normalize(value: String): String = value.trim().lowercase()
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace("ى", "ي").replace("ة", "ه")
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("[؟?!،,.؛:]+"), " ")
        .replace(Regex("\\s+"), " ")

    fun allSurahs(): List<Pair<Int, String>> = surahs.mapIndexed { index, name -> (index + 1) to name }

    fun parse(raw: String): MusicCommand? {
        val text = normalize(raw)
        if (text.isBlank()) return null
        if (listOf("رقية شرعية", "الرقية الشرعية", "شغل رقية", "شغل الرقية", "اريد رقية")
                .any { normalize(it) in text }) return MusicCommand.PlayRuqyah

        if (listOf("وقف", "توقف", "اطف", "اطفي", "اطفئ", "اقفل", "اسكت", "اوقف", "ايقاف", "انهاء")
                .any { text == it || text.startsWith("$it ") } ||
            listOf("الموسيقي", "الموسيقيه", "الاغنيه", "الاغنيه", "التلاوه", "القران")
                .any { text.contains(it) && listOf("وقف", "توقف", "اطف", "اطفي", "اطفئ", "اقفل", "اوقف", "ايقاف").any(text::contains) }
        ) return MusicCommand.Pause

        if (listOf("كمل", "استأنف", "تابع", "اكمل", "واصل", "استمر")
                .map(::normalize).any { text == it || text.startsWith("$it ") } ||
            text in listOf("شغل الموسيقي", "تشغيل الموسيقي", "استئناف الموسيقي")
        ) return MusicCommand.Resume

        if (text.contains("التالي") || text.contains("اللي بعده") || text.contains("الاغنيه الجايه")) return MusicCommand.Next
        if (text.contains("السابق") || text.contains("اللي قبلها") || text.contains("الاغنيه السابقه")) return MusicCommand.Previous
        if (text.contains("ارفع الصوت") || text.contains("علي الصوت") || text.contains("زود الصوت")) return MusicCommand.VolumeUp
        if (text.contains("خفض الصوت") || text.contains("وطي الصوت") || text.contains("قلل الصوت")) return MusicCommand.VolumeDown

        val moods = listOf(
            listOf("حماسي", "حماسيه", "ارفع الحماس", "نشطني") to "upbeat energetic music",
            listOf("روقان", "روق", "هادي", "هادئ", "استرخاء", "ريح اعصابي") to "calm relaxing instrumental music",
            listOf("رومانسي", "رومانسيه") to "romantic soft music",
            listOf("حزين", "حزينه") to "sad emotional music",
            listOf("سعيد", "مبسوط", "فرح", "غير مزاجي") to "happy uplifting music",
            listOf("تركيز", "للدراسه", "للمذاكره") to "focus concentration instrumental music",
            listOf("نوم", "للنوم") to "sleep peaceful ambient music"
        )
        val asksForMusicMood = listOf("شغل", "شغلي", "اسمع", "خلينا", "خلنا", "غير الجو", "اريد موسيقى", "اريد اغاني")
            .any { normalize(it) in text }
        if (asksForMusicMood) {
            moods.firstOrNull { (phrases, _) -> phrases.any { normalize(it) in text } }
                ?.let { return MusicCommand.Search(it.second) }
        }

        val surahIndex = surahs.indexOfFirst { normalize(it) in text }
        val asksForQuran = listOf("قران", "القران", "قرآن", "القرآن", "تلاوه", "تلاوة").any { normalize(it) in text }
        val asksToPlay = listOf("شغل", "شغلي", "اقرا", "استمع", "ابدأ", "ابدا").any { text.contains(normalize(it)) }
        if (surahIndex >= 0 && text.contains("سوره") && asksToPlay) {
            return MusicCommand.PlayQuran(surahIndex + 1, surahs[surahIndex])
        }
        if (asksForQuran) {
            if (surahIndex >= 0) return MusicCommand.PlayQuran(surahIndex + 1, surahs[surahIndex])
            if (asksToPlay) return MusicCommand.OpenQuran
        }

        val searchPrefixes = listOf("ابحث عن", "دور لي على", "دور على", "شغل لي", "شغل", "اسمع", "اريد اسمع", "اريد تشغيل")
        val prefix = searchPrefixes.firstOrNull { text.startsWith(normalize(it) + " ") || text == normalize(it) }
        if (prefix != null) {
            var query = raw.trim().drop(prefix.length).trim().trimStart(':', '،', ' ')
            query = query.replace(Regex("^(أغنية|الأغنية|اغنية|الاغنية|اغنيه|الاغنيه|مقطع)\\s+"), "")
                .replace(Regex("\\s+(لو سمحت|من فضلك)$"), "")
                .trim()
            if (query.isNotBlank()) return MusicCommand.Search(query)
        }
        return null
    }
}
