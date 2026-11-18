package com.azzam.sadeeqi.voice

/** Arabic wake-phrase matcher for the explicit, user-started foreground listening mode. */
object WakeWordMatcher {
    fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun matches(recognizedText: String): Boolean =
        normalize(recognizedText).split(' ').any { it == "صديقي" }
}
