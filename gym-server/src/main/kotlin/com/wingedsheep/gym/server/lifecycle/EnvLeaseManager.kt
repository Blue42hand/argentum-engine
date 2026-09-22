package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.service.EnvId
import com.wingedsheep.gym.service.MultiEnvService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional server-side leases for long-lived Gym environments.
 *
 * A disabled lease manager preserves the historical explicit-dispose-only behavior. When enabled,
 * live environments receive a full-TTL grace period when first observed by the reaper. Requests
 * carrying a lease for an environment mark it active before controller execution and refresh its
 * last-activity timestamp after execution. The reaper never disposes an environment while a leased
 * request for that environment is active.
 *
 * This is transport lifecycle only: [MultiEnvService] remains the authoritative environment API.
 */
@Component
class EnvLeaseManager(
    private val multiEnvService: MultiEnvService,
    @Value("\${GYM_SERVER_ENV_TTL_MS:0}") private val ttlMs: Long,
) {
    private data class Lease(
        val lastActivity: Instant,
        val activeRequests: Int = 0,
    )

    private val leases = ConcurrentHashMap<EnvId, Lease>()

    internal var now: () -> Instant = { Instant.now() }

    init {
        require(ttlMs >= 0) { "GYM_SERVER_ENV_TTL_MS must be >= 0" }
    }

    val enabled: Boolean
        get() = ttlMs > 0

    /** Mark the supplied environments active for the duration of one HTTP request. */
    fun begin(envIds: Collection<EnvId>) {
        if (!enabled) return
        val at = now()
        envIds.toSet().forEach { envId ->
            leases.compute(envId) { _, existing ->
                val lease = existing ?: Lease(at)
                lease.copy(lastActivity = at, activeRequests = lease.activeRequests + 1)
            }
        }
    }

    /** Finish an HTTP request and renew each participating environment's lease. */
    fun end(envIds: Collection<EnvId>) {
        if (!enabled) return
        val at = now()
        envIds.toSet().forEach { envId ->
            leases.computeIfPresent(envId) { _, lease ->
                lease.copy(
                    lastActivity = at,
                    activeRequests = (lease.activeRequests - 1).coerceAtLeast(0),
                )
            }
        }
    }

    /**
     * Dispose environments whose lease has expired and that have no leased request in flight.
     *
     * Environments unknown to this manager (for example those created before leases were enabled)
     * are initialized with a full grace period on the first scan. Explicitly disposed envs are
     * pruned from lease bookkeeping on the next scan.
     *
     * All state transitions for one environment are serialized by [ConcurrentHashMap.compute] /
     * `computeIfPresent`. No separate per-lease monitor is taken, so request admission and reaping
     * cannot acquire the map and lease locks in opposite orders.
     */
    @Scheduled(fixedDelayString = "\${GYM_SERVER_ENV_REAPER_INTERVAL_MS:30000}")
    fun reapIdle() {
        if (!enabled) return

        val at = now()
        val live = multiEnvService.listEnvs()

        live.forEach { envId -> leases.putIfAbsent(envId, Lease(at)) }
        leases.keys.removeIf { it !in live }

        live.forEach { envId ->
            leases.computeIfPresent(envId) { _, lease ->
                val expired = lease.activeRequests == 0 &&
                    !lease.lastActivity.plusMillis(ttlMs).isAfter(at)
                if (!expired) {
                    lease
                } else {
                    multiEnvService.dispose(listOf(envId))
                    logger.debug("Disposed idle Gym environment {} after {} ms TTL", envId.value, ttlMs)
                    null
                }
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(EnvLeaseManager::class.java)
    }
}
