package com.azzam.sadeeqi.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryTest {
    @Test
    fun groupsNewConversationRecordsIndependently() {
        val records = listOf(
            "chat:alpha:100:1" to "أنت\nالسؤال الأول",
            "chat:alpha:100:2" to "صديقي\nالرد الأول",
            "chat:beta:200:1" to "أنت\nالسؤال الثاني"
        )

        val summaries = ConversationHistory.group(records)

        assertEquals(2, summaries.size)
        assertEquals("beta", summaries[0].id)
        assertEquals("السؤال الثاني", summaries[0].title)
        assertEquals("alpha", summaries[1].id)
        assertEquals(2, summaries[1].messages.size)
    }

    @Test
    fun keepsLegacyRecordsTogetherWithoutMixingNewThreads() {
        val records = listOf(
            "chat:100:1" to "أنت\nرسالة قديمة",
            "chat:101:2" to "صديقي\nرد قديم",
            "chat:new-id:200:1" to "أنت\nرسالة جديدة"
        )

        val summaries = ConversationHistory.group(records)

        assertEquals(2, summaries.size)
        val legacy = summaries.first { it.id == "legacy" }
        assertEquals(2, legacy.messages.size)
        assertTrue(summaries.any { it.id == "new-id" })
    }

    @Test
    fun ignoresMalformedAndUnrelatedRecords() {
        val records = listOf(
            "saved-reply:1" to "صديقي\nرد محفوظ",
            "chat:broken:1" to "بيانات غير معروفة"
        )
        assertTrue(ConversationHistory.group(records).isEmpty())
    }
}
