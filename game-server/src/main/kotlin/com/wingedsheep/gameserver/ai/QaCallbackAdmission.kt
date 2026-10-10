package com.wingedsheep.gameserver.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** QA-only pre-evidence admission; its counters are observations, never recording receipts. */
@Component
class QaCallbackAdmission(
    @Value("\${native.qa.callback-gate-enabled:false}") val enabled: Boolean = false,
) {
    private val lock = Any()
    private var paused = false
    private var active = 0
    private var waiting = 0
    private var failures = 0
    private var generation = 0L

    fun pause() = synchronized(lock) {
        check(enabled)
        paused = true
        generation++
    }

    fun resume(expectedGeneration: Long) = synchronized(lock) {
        check(enabled && paused && generation == expectedGeneration && active == 0 && failures == 0)
        paused = false
    }

    fun snapshot(): Map<String, Any> = synchronized(lock) {
        mapOf("qaCallbackGateEnabled" to enabled, "qaCallbackPaused" to paused,
            "qaCallbackActiveHandlers" to active, "qaCallbackWaitingHandlers" to waiting,
            "qaCallbackAdmissionFailures" to failures, "qaCallbackGeneration" to generation,
            "qaCallbackAdmissionDrained" to (enabled && paused && active == 0 && failures == 0))
    }

    suspend fun <T> admitted(block: suspend () -> T): T {
        if (!enabled) return block()
        synchronized(lock) {
            if (waiting + active >= 64) {
                failures++
                error("QA callback admission bound")
            }
            waiting++
        }
        var entered = false
        try {
            withTimeout(900_000L) {
                while (!entered) {
                    entered = synchronized(lock) {
                        if (paused) false else { waiting--; active++; true }
                    }
                    if (!entered) delay(10)
                }
            }
            return block()
        } catch (failure: TimeoutCancellationException) {
            synchronized(lock) { failures++ }
            throw failure
        } finally {
            synchronized(lock) {
                if (entered) active-- else waiting--
            }
        }
    }
}
