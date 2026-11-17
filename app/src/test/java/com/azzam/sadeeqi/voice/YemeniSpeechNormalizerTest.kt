package com.azzam.sadeeqi.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class YemeniSpeechNormalizerTest {
    @Test fun normalizesCommonYemeniWords() {
        assertEquals("أريد", YemeniSpeechNormalizer.normalizeForUnderstanding("اشتي"))
        assertEquals("تريد", YemeniSpeechNormalizer.normalizeForUnderstanding("تشتي"))
        assertEquals("الآن", YemeniSpeechNormalizer.normalizeForUnderstanding("دحين"))
    }

    @Test fun keepsContextSensitiveWordUnchanged() {
        assertEquals("عاد", YemeniSpeechNormalizer.normalizeForUnderstanding("عاد"))
    }

    @Test fun normalizesPhraseWithoutChangingOtherWords() {
        assertEquals(
            "أريد أتكلم معك الآن",
            YemeniSpeechNormalizer.normalizeForUnderstanding("اشتي أتكلم معك دحين")
        )
    }

    @Test fun preservesOriginalTranscriptInAssistantContext() {
        assertEquals(
            "النص كما سمعته: اشتي أتكلم\nتوضيح لبعض المفردات اليمنية دون تغيير بقية المعنى: أريد أتكلم",
            YemeniSpeechNormalizer.assistantContext("اشتي أتكلم")
        )
    }

    @Test fun normalizesWhitespaceOnlyWhenNoDialectWordExists() {
        assertEquals("مرحبا بك", YemeniSpeechNormalizer.normalizeForUnderstanding("  مرحبا   بك  "))
    }
}
