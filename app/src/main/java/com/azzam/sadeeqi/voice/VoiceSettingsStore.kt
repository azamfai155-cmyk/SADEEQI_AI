package com.azzam.sadeeqi.voice

import android.content.Context
import java.util.Locale

/** Non-sensitive voice preferences; secrets and conversation content are never stored here. */
data class VoiceSettings(
    val voiceName: String = "",
    val languageTag: String = "ar",
    val speechRate: Float = 0.96f,
    val pitch: Float = 0.98f,
    val volume: Float = 1.0f,
    val autoRead: Boolean = false
)

/** Sanitizes persisted/user-controlled values before they reach Android's TTS engine. */
fun VoiceSettings.normalized(): VoiceSettings {
    val candidateLanguage = languageTag.trim()
    val safeLanguage = candidateLanguage
        .takeIf { it.matches(Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")) }
        ?.let { Locale.forLanguageTag(it).toLanguageTag().takeUnless { tag -> tag == "und" } }
        ?: "ar"
    return copy(
        voiceName = voiceName.take(256),
        languageTag = safeLanguage,
        speechRate = (if (speechRate.isFinite()) speechRate else 0.96f).coerceIn(0.5f, 2.0f),
        pitch = (if (pitch.isFinite()) pitch else 0.98f).coerceIn(0.5f, 2.0f),
        volume = (if (volume.isFinite()) volume else 1.0f).coerceIn(0.0f, 1.0f)
    )
}

class VoiceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sadeeqi_voice_settings", Context.MODE_PRIVATE)

    fun load(): VoiceSettings = VoiceSettings(
        voiceName = prefs.getString(KEY_VOICE, "").orEmpty(),
        languageTag = prefs.getString(KEY_LANGUAGE, "ar").orEmpty(),
        speechRate = prefs.getFloat(KEY_RATE, 0.96f),
        pitch = prefs.getFloat(KEY_PITCH, 0.98f),
        volume = prefs.getFloat(KEY_VOLUME, 1.0f),
        autoRead = prefs.getBoolean(KEY_AUTO_READ, false)
    ).normalized()

    fun save(settings: VoiceSettings) {
        val safe = settings.normalized()
        prefs.edit()
            .putString(KEY_VOICE, safe.voiceName)
            .putString(KEY_LANGUAGE, safe.languageTag)
            .putFloat(KEY_RATE, safe.speechRate)
            .putFloat(KEY_PITCH, safe.pitch)
            .putFloat(KEY_VOLUME, safe.volume)
            .putBoolean(KEY_AUTO_READ, safe.autoRead)
            .apply()
    }

    companion object {
        private const val KEY_VOICE = "voice_name"
        private const val KEY_LANGUAGE = "language_tag"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_VOLUME = "volume"
        private const val KEY_AUTO_READ = "auto_read"
    }
}
