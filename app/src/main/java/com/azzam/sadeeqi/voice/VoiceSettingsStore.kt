package com.azzam.sadeeqi.voice

import android.content.Context

/** Non-sensitive voice preferences; secrets and conversation content are never stored here. */
data class VoiceSettings(
    val voiceName: String = "",
    val languageTag: String = "ar",
    val speechRate: Float = 0.96f,
    val pitch: Float = 0.98f,
    val volume: Float = 1.0f,
    val autoRead: Boolean = false
)

class VoiceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sadeeqi_voice_settings", Context.MODE_PRIVATE)

    fun load(): VoiceSettings = VoiceSettings(
        voiceName = prefs.getString(KEY_VOICE, "").orEmpty(),
        languageTag = prefs.getString(KEY_LANGUAGE, "ar").orEmpty().ifBlank { "ar" },
        speechRate = prefs.getFloat(KEY_RATE, 0.96f).coerceIn(0.5f, 2.0f),
        pitch = prefs.getFloat(KEY_PITCH, 0.98f).coerceIn(0.5f, 2.0f),
        volume = prefs.getFloat(KEY_VOLUME, 1.0f).coerceIn(0.0f, 1.0f),
        autoRead = prefs.getBoolean(KEY_AUTO_READ, false)
    )

    fun save(settings: VoiceSettings) {
        prefs.edit()
            .putString(KEY_VOICE, settings.voiceName)
            .putString(KEY_LANGUAGE, settings.languageTag)
            .putFloat(KEY_RATE, settings.speechRate.coerceIn(0.5f, 2.0f))
            .putFloat(KEY_PITCH, settings.pitch.coerceIn(0.5f, 2.0f))
            .putFloat(KEY_VOLUME, settings.volume.coerceIn(0.0f, 1.0f))
            .putBoolean(KEY_AUTO_READ, settings.autoRead)
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
