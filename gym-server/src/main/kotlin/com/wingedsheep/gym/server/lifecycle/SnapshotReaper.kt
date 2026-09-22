package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.service.MultiEnvService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Optional persistent-host cleanup for snapshot handles abandoned by disconnected trainers.
 *
 * Snapshot lifetime is deliberately independent from environment lifetime. The codec owns idle
 * activity and race-safe disposal; this server component only schedules the optional policy.
 */
@Component
class SnapshotReaper(
    private val multiEnvService: MultiEnvService,
    @Value("\${GYM_SERVER_SNAPSHOT_TTL_MS:0}") private val ttlMs: Long,
) {
    init {
        require(ttlMs >= 0) { "GYM_SERVER_SNAPSHOT_TTL_MS must be >= 0" }
    }

    val enabled: Boolean
        get() = ttlMs > 0

    @Scheduled(fixedDelayString = "\${GYM_SERVER_SNAPSHOT_REAPER_INTERVAL_MS:30000}")
    fun reapIdle() {
        if (!enabled) return

        val disposed = multiEnvService.snapshotCodec.disposeIdle(ttlMs)
        if (disposed > 0) {
            logger.debug("Disposed {} idle Gym snapshot(s) after {} ms TTL", disposed, ttlMs)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SnapshotReaper::class.java)
    }
}
