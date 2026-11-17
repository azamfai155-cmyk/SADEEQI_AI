package com.azzam.sadeeqi.voice

/** Separate local voice personas. Dialect labels describe the intended style, not a verified TTS accent. */
data class VoiceDialectProfile(
    val id: String,
    val displayName: String,
    val dialectLabel: String,
    val description: String,
    val languageTag: String,
    val speechRate: Float,
    val pitch: Float,
    val sampleText: String
)

object VoiceDialectProfiles {
    val all = listOf(
        VoiceDialectProfile(
            id = "ibbi",
            displayName = "الصوت الإبّي",
            dialectLabel = "محافظة إب – اليمن",
            description = "الصوت اليمني الأساسي؛ نعمل على ضبطه ليكون إبّياً طبيعياً.",
            languageTag = "ar",
            speechRate = 0.94f,
            pitch = 1.00f,
            sampleText = "يا هلا، كيف حالك؟ عساك بخير؟ أنا صديقي، قل لي إيش تشتي وأنا معك."
        ),
        VoiceDialectProfile(
            id = "hadi",
            displayName = "هادي",
            dialectLabel = "إبّي – هادئ",
            description = "شخصية هادئة وودودة، مستقلة بإعداداتها.",
            languageTag = "ar",
            speechRate = 0.88f,
            pitch = 0.94f,
            sampleText = "يا مرحبا، خذ راحتك واحكي لي بهدوء، أنا أسمعك وموجود معك."
        ),
        VoiceDialectProfile(
            id = "dhaki",
            displayName = "ذكي",
            dialectLabel = "إبّي – نشيط",
            description = "شخصية نشيطة وواضحة، مستقلة عن هادي.",
            languageTag = "ar",
            speechRate = 1.02f,
            pitch = 1.06f,
            sampleText = "أيوه، تمام! قل لي إيش ننجز اليوم، وبنرتبها مع بعض خطوة خطوة."
        )
    )

    val default = all.first()
    fun find(id: String): VoiceDialectProfile = all.firstOrNull { it.id == id } ?: default
}
