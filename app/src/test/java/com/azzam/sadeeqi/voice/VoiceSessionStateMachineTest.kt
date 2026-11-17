package com.azzam.sadeeqi.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionStateMachineTest {
    @Test fun modelsRequestedConversationLifecycle() {
        val machine = VoiceSessionStateMachine()
        assertEquals(VoiceSessionState.STANDBY, machine.state)
        machine.transition(VoiceSessionState.WAKE_DETECTED)
        machine.transition(VoiceSessionState.GREETING)
        machine.transition(VoiceSessionState.ACTIVE_LISTENING)
        machine.transition(VoiceSessionState.PROCESSING)
        machine.transition(VoiceSessionState.SPEAKING)
        machine.transition(VoiceSessionState.IDLE_TIMEOUT)
        assertTrue(machine.isConversationActive())
        machine.transition(VoiceSessionState.STANDBY_RESUME)
        machine.transition(VoiceSessionState.STANDBY)
        assertFalse(machine.isConversationActive())
    }
    @Test fun speakingPreservesExplicitStandbyResumeIntent() {
        val machine = VoiceSessionStateMachine()
        machine.transition(VoiceSessionState.STANDBY_RESUME)

        machine.beginSpeaking()

        assertEquals(VoiceSessionState.STANDBY_RESUME, machine.state)
        assertFalse(machine.isConversationActive())
    }

    @Test fun speakingMovesOrdinaryConversationIntoSpeakingState() {
        val machine = VoiceSessionStateMachine()
        machine.transition(VoiceSessionState.ACTIVE_LISTENING)

        machine.beginSpeaking()

        assertEquals(VoiceSessionState.SPEAKING, machine.state)
        assertTrue(machine.isConversationActive())
    }

}
