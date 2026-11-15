package com.azzam.sadeeqi.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTurnGateTest {
    @Test
    fun beginningANewTurnInvalidatesThePreviousTurn() {
        val gate = VoiceTurnGate()
        val first = gate.begin()

        assertTrue(gate.isCurrent(first))

        val second = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun explicitInterruptionInvalidatesTheActiveTurn() {
        val gate = VoiceTurnGate()
        val active = gate.begin()

        gate.invalidate()

        assertFalse(gate.isCurrent(active))
    }
}
