package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.service.EnvId
import com.wingedsheep.gym.service.MultiEnvService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
    private val publicationScopes = ConcurrentHashMap<Long, Set<EnvId>>()
    private val nextPublicationScope = AtomicLong(0)

    internal var now: () -> Instant = { Instant.now() }

    init {
        require(ttlMs >= 0) { "GYM_SERVER_ENV_TTL_MS must be >= 0" }
    }

    val enabled: Boolean
        get() = ttlMs > 0

    /**
     * Hold leases for [envIds] while [block] performs one parsed HTTP operation.
     *
     * This is primarily used by batch controllers, whose environment IDs live in the request body
     * rather than the URL. It deliberately composes with [EnvLeaseFilter]: an optional header lease
     * may already be active before controller dispatch, and this nested lease keeps the body-derived
     * environments protected for the authoritative operation itself.
     */
    fun <T> withLeases(envIds: Collection<EnvId>, block: () -> T): T {
        begin(envIds)
        return try {
            block()
        } finally {
            end(envIds)
        }
    }

    /**
     * Protect newly registered environments until a resource-producing HTTP response is published.
     *
     * Callers cannot lease an environment ID before the server has returned it. A long-running create
     * or fork request can therefore outlive the configured TTL after a scheduled scan first discovers
     * one of its newly registered environments. Each publication scope records the live registry at
     * request admission; only environments absent from that baseline are protected from expiry. Old,
     * unrelated idle environments continue to reap normally while the request is in flight.
     *
     * On successful completion every environment that appeared since the scope began receives a fresh
     * full TTL before the scope is removed. Concurrent scopes may harmlessly protect or renew one
     * another's newly-created environments. [MultiEnvService] remains unaware of this HTTP lifecycle
     * concern.
     */
    fun <T> withEnvPublication(block: () -> T): T {
        if (!enabled) return block()

        val before = multiEnvService.listEnvs()
        val scopeId = nextPublicationScope.incrementAndGet()
        publicationScopes[scopeId] = before
        var completed = false
        return try {
            val result = block()
            completed = true
            result
        } finally {
            try {
                if (completed) {
                    renew(multiEnvService.listEnvs() - before, now())
                }
            } finally {
                publicationScopes.remove(scopeId)
            }
        }
    }

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

    private fun renew(envIds: Collection<EnvId>, at: Instant) {
        envIds.forEach { envId ->
            leases.compute(envId) { _, existing ->
                val lease = existing ?: Lease(at)
                lease.copy(lastActivity = at)
            }
        }
    }

    private fun isAwaitingPublication(envId: EnvId): Boolean =
        publicationScopes.values.any { baseline -> envId !in baseline }

    /**
     * Reconcile lease bookkeeping with one point-in-time live-environment snapshot.
     *
     * The snapshot may already be stale by the time reconciliation runs. In particular, an
     * environment can be created after [MultiEnvService.listEnvs] returns and then acquire a lease
     * before this method sees the old snapshot. Never delete an active lease merely because that
     * environment is absent from the snapshot; a later scan can reconcile it once the request ends.
     */
    internal fun reconcileLeases(live: Set<EnvId>, at: Instant) {
        live.forEach { envId -> leases.putIfAbsent(envId, Lease(at)) }
        leases.keys.forEach { envId ->
            if (envId !in live) {
                leases.computeIfPresent(envId) { _, lease ->
                    if (lease.activeRequests == 0) null else lease
                }
            }
        }
    }

    /**
     * Dispose environments whose lease has expired and that have no leased request in flight.
     *
     * Environments unknown to this manager (for example those created before leases were enabled)
     * are initialized with a full grace period on the first scan. Explicitly disposed envs are
     * pruned from lease bookkeeping on a later scan once no leased request remains in flight.
     *
     * A newly registered environment that is still awaiting publication by a resource-producing HTTP
     * request is also protected. Pre-existing idle environments remain eligible for normal cleanup.
     *
     * All state transitions for one environment are serialized by [ConcurrentHashMap.compute] /
     * `computeIfPresent`. No separate per-lease monitor is taken, so request admission and reaping
     * cannot acquire the map and lease locks in opposite orders.
     */
    @Scheduled(fixedDelayString = "\${GYM_SERVER_ENV_REAPER_INTERVAL_MS:30000}")
    fun reapIdle() {
        if (!enabled) return

        val at = now()
        val live = multiEnvService.listEnvs().toSet()

        reconcileLeases(live, at)

        live.forEach { envId ->
            leases.computeIfPresent(envId) { _, lease ->
                val expired = lease.activeRequests == 0 &&
                    !lease.lastActivity.plusMillis(ttlMs).isAfter(at)
                if (!expired || isAwaitingPublication(envId)) {
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
