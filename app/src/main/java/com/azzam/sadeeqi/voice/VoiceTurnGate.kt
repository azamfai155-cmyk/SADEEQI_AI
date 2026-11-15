package com.azzam.sadeeqi.voice

import java.util.concurrent.atomic.AtomicLong

/** Tracks which voice turn is allowed to publish a response. */
internal class VoiceTurnGate {
    private val generation = AtomicLong(0)

    fun begin(): Long = generation.incrementAndGet()

    fun invalidate(): Long = generation.incrementAndGet()

    fun isCurrent(turnId: Long): Boolean = generation.get() == turnId
}
