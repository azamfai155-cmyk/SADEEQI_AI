package com.azzam.sadeeqi.memory

import com.azzam.sadeeqi.core.OpenAiGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRecallTest {
    @Test
    fun selectsRelevantArabicMemoryAndExcludesCurrentConversation() {
        val records = listOf(
            "chat:old:100:1" to "أنت\nأستخدم هاتف سامسونج وأعمل على مشروع صديقي.",
            "chat:old:101:2" to "صديقي\nيمكننا متابعة مشروع التطبيق لاحقاً.",
            "chat:current:200:1" to "أنت\nمشروع صديقي الحالي قيد التطوير.",
            "chat:other:300:1" to "أنت\nأحب الموسيقى والرياضة."
        )

        val result = MemoryRecall.select(
            records = records,
            query = "أين وصلنا في مشروع صديقي؟",
            excludedIdPrefix = "chat:current:"
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.none { it.first.startsWith("chat:current:") })
        assertTrue(result.first().second.contains("مشروع"))
    }

    @Test
    fun ignoresQueriesWithoutUsefulTerms() {
        val result = MemoryRecall.select(
            records = listOf("chat:old:1:1" to "أنت\nأستخدم هاتف سامسونج"),
            query = "هل هذا هو؟"
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun addsMemoryToLatestUserTurnWithoutChangingRoles() {
        val turns = listOf(
            OpenAiGateway.Turn("user", "السؤال السابق"),
            OpenAiGateway.Turn("assistant", "الرد السابق"),
            OpenAiGateway.Turn("user", "ما هاتفي؟")
        )

        val result = MemoryRecall.attachToLatestUserTurn(
            turns,
            listOf("memory:1" to "أستخدم هاتف سامسونج.")
        )

        assertEquals(turns.map { it.role }, result.map { it.role })
        assertEquals("السؤال السابق", result[0].content)
        assertTrue(result[2].content.contains("أستخدم هاتف سامسونج"))
        assertTrue(result[2].content.endsWith("ما هاتفي؟"))
    }

    @Test
    fun respectsResultAndCharacterLimits() {
        val records = listOf(
            "a" to "سامسونج هاتف أندرويد",
            "b" to "سامسونج هاتف قديم"
        )

        val result = MemoryRecall.select(
            records = records,
            query = "سامسونج هاتف",
            limit = 1,
            maxCharacters = 8
        )

        assertEquals(1, result.size)
        assertEquals(8, result.single().second.length)
    }
}
