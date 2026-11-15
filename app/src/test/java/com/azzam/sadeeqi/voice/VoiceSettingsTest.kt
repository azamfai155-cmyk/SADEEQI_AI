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
}
