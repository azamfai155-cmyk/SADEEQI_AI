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
    fun speechRecognitionUsesSelectedLocaleAndFallsBackSafely() {
        assertEquals("ar-YE", VoiceSettings(languageTag = "ar-YE").speechRecognitionLanguageTag())
        assertEquals("ar", VoiceSettings(languageTag = "invalid locale").speechRecognitionLanguageTag())
    }
}

class VoiceTurnGateTest {
    @org.junit.Test
    fun beginningANewTurnInvalidatesThePreviousTurn() {
        val gate = VoiceTurnGate()
        val first = gate.begin()
        org.junit.Assert.assertTrue(gate.isCurrent(first))
        val second = gate.begin()
        org.junit.Assert.assertFalse(gate.isCurrent(first))
        org.junit.Assert.assertTrue(gate.isCurrent(second))
    }

    @org.junit.Test
    fun explicitInterruptionInvalidatesTheActiveTurn() {
        val gate = VoiceTurnGate()
        val active = gate.begin()
        gate.invalidate()
        org.junit.Assert.assertFalse(gate.isCurrent(active))
    }
}
