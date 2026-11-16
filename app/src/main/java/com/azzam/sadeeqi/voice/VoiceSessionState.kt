package com.azzam.sadeeqi.voice

/** Explicit lifecycle states for the voice conversation coordinator. */
enum class VoiceSessionState {
    STANDBY,
    WAKE_DETECTED,
    GREETING,
    ACTIVE_LISTENING,
    PROCESSING,
    SPEAKING,
    IDLE_TIMEOUT,
    STANDBY_RESUME
}

class VoiceSessionStateMachine {
    var state: VoiceSessionState = VoiceSessionState.STANDBY
        private set

    fun transition(next: VoiceSessionState) {
        state = next
    }

    fun isConversationActive(): Boolean = state in setOf(
        VoiceSessionState.WAKE_DETECTED,
        VoiceSessionState.GREETING,
        VoiceSessionState.ACTIVE_LISTENING,
        VoiceSessionState.PROCESSING,
        VoiceSessionState.SPEAKING,
        VoiceSessionState.IDLE_TIMEOUT
    )
}
