package com.azzam.sadeeqi.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSettingsTest {
    @Test
    fun normalizedClampsOutOfRangeValues() {
        val result = VoiceSettings(
            languageTag = "ar-YE",
            speechRate = 9.0f,
            pitch = -2.0f,
            volume = 4.0f
        ).normalized()

        assertEquals("ar-YE", result.languageTag)
        assertEquals(2.0f, result.speechRate)
        assertEquals(0.5f, result.pitch)
        assertEquals(1.0f, result.volume)
    }

    @Test
    fun normalizedReplacesNonFiniteValuesAndInvalidLanguage() {
        val result = VoiceSettings(
            languageTag = "not a locale tag !!!",
            speechRate = Float.NaN,
            pitch = Float.POSITIVE_INFINITY,
            volume = Float.NEGATIVE_INFINITY
        ).normalized()

        assertEquals("ar", result.languageTag)
        assertEquals(0.96f, result.speechRate)
        assertEquals(0.98f, result.pitch)
        assertEquals(1.0f, result.volume)
    }

    @Test
    fun normalizedBoundsVoiceNameLength() {
        val result = VoiceSettings(voiceName = "v".repeat(400)).normalized()
        assertEquals(256, result.voiceName.length)
    }

    @Test
    fun normalizedKeepsOnlySupportedOpenAiRealtimeVoices() {
        val supported = VoiceSettings(openAiVoiceName = "cedar", useOpenAiVoice = true).normalized()
        val invalid = VoiceSettings(openAiVoiceName = "unknown-voice", useOpenAiVoice = true).normalized()

        assertEquals("cedar", supported.openAiVoiceName)
        assertEquals(false, supported.useOpenAiVoice)
        assertEquals("marin", invalid.openAiVoiceName)
        assertEquals(false, invalid.useOpenAiVoice)
    }
    @Test
    fun voicePersonasHaveDistinctIdentityAndTuning() {
        val profiles = VoiceDialectProfiles.all
        assertEquals(setOf("ibbi", "hadi", "dhaki"), profiles.map { it.id }.toSet())
        assertEquals(3, profiles.map { it.displayName }.toSet().size)
        assertEquals(3, profiles.map { it.sampleText }.toSet().size)
        assertEquals(3, profiles.map { it.speechRate }.toSet().size)
        assertEquals(3, profiles.map { it.pitch }.toSet().size)
        profiles.forEach { assertEquals("ar", it.languageTag) }
    }

    @Test
    fun normalizedKeepsSupportedRecognitionLanguages() {
        assertEquals("ar", VoiceSettings(recognitionLanguageTag = "ar").normalized().recognitionLanguageTag)
        assertEquals("ar-YE", VoiceSettings(recognitionLanguageTag = "ar-YE").normalized().recognitionLanguageTag)
    }

    @Test
    fun normalizedFallsBackForUnsupportedRecognitionLanguage() {
        assertEquals(
            "ar",
            VoiceSettings(recognitionLanguageTag = "en-US").normalized().recognitionLanguageTag
        )
    }

    @Test
    fun normalizedKeepsOnlyKnownDialectProfiles() {
        val ibbi = VoiceSettings(dialectProfileId = "ibbi").normalized()
        val hadi = VoiceSettings(dialectProfileId = "hadi").normalized()
        val dhaki = VoiceSettings(dialectProfileId = "dhaki").normalized()
        val invalid = VoiceSettings(dialectProfileId = "unknown").normalized()

        assertEquals("ibbi", ibbi.dialectProfileId)
        assertEquals("hadi", hadi.dialectProfileId)
        assertEquals("dhaki", dhaki.dialectProfileId)
        assertEquals("ibbi", invalid.dialectProfileId)
    }

}
