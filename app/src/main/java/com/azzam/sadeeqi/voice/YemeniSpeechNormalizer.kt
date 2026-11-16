package com.azzam.sadeeqi.voice

/**
 * Conservative text normalization for common Yemeni conversational words.
 *
 * The original transcript is never mutated. Callers can send both the original
 * and this normalized variant to an assistant so dialect hints do not erase
 * what Android's recognizer actually heard.
 */
object YemeniSpeechNormalizer {
    fun normalizeForUnderstanding(input: String): String {
        if (input.isBlank()) return input
        return input
            .replace(Regex("[\\t\\r\\n]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .replace(Regex("(?<![\\p{L}])اشتي(?![\\p{L}])"), "أريد")
            .replace(Regex("(?<![\\p{L}])تشتي(?![\\p{L}])"), "تريد")
            .replace(Regex("(?<![\\p{L}])دحين(?![\\p{L}])"), "الآن")
    }

    /** Keeps context-sensitive words unchanged; "عاد" is intentionally not mapped. */
    fun assistantContext(original: String): String {
        val normalized = normalizeForUnderstanding(original)
        return if (normalized == original) original else
            "النص كما سمعته: $original\nتوضيح لبعض المفردات اليمنية دون تغيير بقية المعنى: $normalized"
    }
}
