package com.azzam.sadeeqi.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderValidatorTest {
    @Test
    fun trimsTitleAndRejectsBlankOrOverlongValues() {
        assertEquals("موعد الطبيب", ReminderValidator.normalizeTitle("  موعد الطبيب  "))
        assertNull(ReminderValidator.normalizeTitle("   "))
        assertNull(ReminderValidator.normalizeTitle("x".repeat(121)))
    }

    @Test
    fun acceptsOnlyFutureDueTimes() {
        assertTrue(ReminderValidator.isFuture(1_001L, 1_000L))
        assertFalse(ReminderValidator.isFuture(1_000L, 1_000L))
        assertFalse(ReminderValidator.isFuture(999L, 1_000L))
    }
}
