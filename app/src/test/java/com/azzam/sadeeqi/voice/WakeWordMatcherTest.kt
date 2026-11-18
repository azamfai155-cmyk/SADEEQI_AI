package com.azzam.sadeeqi.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordMatcherTest {
    @Test
    fun recognizesWakeWordAloneAndInsideNaturalSpeech() {
        assertTrue(WakeWordMatcher.matches("صديقي"))
        assertTrue(WakeWordMatcher.matches("يا صديقي"))
        assertTrue(WakeWordMatcher.matches("صديقي افتح الموسيقى"))
        assertTrue(WakeWordMatcher.matches("مرحبا يا صديقي"))
    }

    @Test
    fun normalizesArabicDiacriticsAndAlefForms() {
        assertTrue(WakeWordMatcher.matches("يَا صَدِيقِي"))
        assertTrue(WakeWordMatcher.matches("إزيك يا صديقي؟"))
    }

    @Test
    fun doesNotMatchPartialOrUnrelatedWords() {
        assertFalse(WakeWordMatcher.matches("صديق"))
        assertFalse(WakeWordMatcher.matches("صديقتي"))
        assertFalse(WakeWordMatcher.matches("مرحبا يا صديقيّات"))
        assertFalse(WakeWordMatcher.matches("افتح الموسيقى"))
    }
}
