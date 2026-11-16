package com.azzam.sadeeqi.voice

import android.content.Context
import java.util.Locale

/** Per-persona speech tuning stored independently from the currently active voice. */
data class VoiceProfileSettings(
    val voiceName: String,
    val languageTag: String,
    val speechRate: Float,
    val pitch: Float
)

/** Non-sensitive voice preferences; secrets and conversation content are never stored here. */
data class VoiceSettings(
    val voiceName: String = "",
    val languageTag: String = "ar-YE",
    val speechRate: Float = 0.96f,
    val pitch: Float = 0.98f,
    val volume: Float = 1.0f,
    val autoRead: Boolean = false,
    val useOpenAiVoice: Boolean = false,
    val openAiVoiceName: String = "marin",
    val dialectProfileId: String = "ibbi"
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
        volume = (if (volume.isFinite()) volume else 1.0f).coerceIn(0.0f, 1.0f),
        openAiVoiceName = openAiVoiceName.takeIf { it in VoiceSettingsStore.OPENAI_VOICES } ?: "marin",
        useOpenAiVoice = false,
        dialectProfileId = dialectProfileId.takeIf { id -> VoiceDialectProfiles.all.any { it.id == id } }
            ?: VoiceDialectProfiles.default.id
    )
}

class VoiceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sadeeqi_voice_settings", Context.MODE_PRIVATE)

    fun load(): VoiceSettings {
        val profileId = prefs.getString(KEY_DIALECT_PROFILE, "ibbi").orEmpty()
        val safeProfileId = profileId.takeIf { id -> VoiceDialectProfiles.all.any { it.id == id } } ?: "ibbi"
        val defaults = VoiceDialectProfiles.find(safeProfileId)
        return VoiceSettings(
        voiceName = prefs.getString(profileVoiceKey(safeProfileId), if (safeProfileId == "ibbi") prefs.getString(KEY_VOICE, "") else "").orEmpty(),
        languageTag = prefs.getString(profileLanguageKey(safeProfileId), defaults.languageTag).orEmpty(),
        speechRate = prefs.getFloat(profileRateKey(safeProfileId), defaults.speechRate),
        pitch = prefs.getFloat(profilePitchKey(safeProfileId), defaults.pitch),
        volume = prefs.getFloat(KEY_VOLUME, 1.0f),
        autoRead = prefs.getBoolean(KEY_AUTO_READ, false),
        useOpenAiVoice = prefs.getBoolean(KEY_USE_OPENAI_VOICE, false),
        openAiVoiceName = prefs.getString(KEY_OPENAI_VOICE, "marin").orEmpty(),
        dialectProfileId = safeProfileId
    ).normalized()
    }

    fun profileSettingsFor(profileId: String): VoiceProfileSettings {
        val safeId = profileId.takeIf { id -> VoiceDialectProfiles.all.any { it.id == id } } ?: "ibbi"
        val defaults = VoiceDialectProfiles.find(safeId)
        return VoiceProfileSettings(
            voiceName = prefs.getString(profileVoiceKey(safeId), "").orEmpty(),
            languageTag = prefs.getString(profileLanguageKey(safeId), defaults.languageTag).orEmpty(),
            speechRate = prefs.getFloat(profileRateKey(safeId), defaults.speechRate).let {
                if (it.isFinite()) it.coerceIn(0.5f, 2.0f) else defaults.speechRate
            },
            pitch = prefs.getFloat(profilePitchKey(safeId), defaults.pitch).let {
                if (it.isFinite()) it.coerceIn(0.5f, 2.0f) else defaults.pitch
            }
        )
    }

    private fun profileVoiceKey(profileId: String) = "voice_name_profile_$profileId"
    private fun profileLanguageKey(profileId: String) = "language_profile_$profileId"
    private fun profileRateKey(profileId: String) = "rate_profile_$profileId"
    private fun profilePitchKey(profileId: String) = "pitch_profile_$profileId"

    fun save(settings: VoiceSettings) {
        val safe = settings.normalized()
        prefs.edit()
            .putString(KEY_VOICE, safe.voiceName)
            .putString(profileVoiceKey(safe.dialectProfileId), safe.voiceName)
            .putString(profileLanguageKey(safe.dialectProfileId), safe.languageTag)
            .putFloat(profileRateKey(safe.dialectProfileId), safe.speechRate)
            .putFloat(profilePitchKey(safe.dialectProfileId), safe.pitch)
            .putString(KEY_LANGUAGE, safe.languageTag)
            .putFloat(KEY_RATE, safe.speechRate)
            .putFloat(KEY_PITCH, safe.pitch)
            .putFloat(KEY_VOLUME, safe.volume)
            .putBoolean(KEY_AUTO_READ, safe.autoRead)
            .putBoolean(KEY_USE_OPENAI_VOICE, safe.useOpenAiVoice)
            .putString(KEY_OPENAI_VOICE, safe.openAiVoiceName)
            .putString(KEY_DIALECT_PROFILE, safe.dialectProfileId)
            .apply()
    }

    companion object {
        private const val KEY_VOICE = "voice_name"
        private const val KEY_LANGUAGE = "language_tag"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "speech_pitch"
        private const val KEY_VOLUME = "speech_volume"
        private const val KEY_AUTO_READ = "auto_read"
        private const val KEY_USE_OPENAI_VOICE = "use_openai_voice"
        private const val KEY_OPENAI_VOICE = "openai_voice_name"
        private const val KEY_DIALECT_PROFILE = "dialect_profile_id"
        val OPENAI_VOICES = setOf("alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse", "marin", "cedar")
    }
}
