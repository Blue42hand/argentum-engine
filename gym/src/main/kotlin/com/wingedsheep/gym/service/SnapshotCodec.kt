package com.wingedsheep.gym.service

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Opaque handle pointing at a saved game state.
 *
 * For now only the in-process variant is implemented; the sealed design
 * leaves room for a cross-process byte-blob variant once we need it for
 * distributed MCTS.
 */
@Serializable
sealed interface SnapshotHandle {
    /** An in-process slot managed by [SnapshotCodec]. */
    @Serializable
    data class Slot(val slotId: Long) : SnapshotHandle
}

/**
 * Stores [GameState] snapshots and their player-ID roster in-process. Since
 * `GameState` is fully immutable, saving is free — we just hold a reference
 * — and restoring is also free: the restored env's state field is set back
 * to the referenced object, no deep copy required.
 *
 * Slots are keyed by a monotonically-increasing `Long`. Explicit [dispose] is
 * recommended for long-lived training sessions. Persistent hosts may also call
 * [disposeIdle] to reclaim snapshots abandoned by clients that disappeared
 * before cleanup; successful [load] calls renew that snapshot's idle activity.
 */
class SnapshotCodec {
    data class Entry(
        val state: GameState,
        val playerIds: List<EntityId>,
        val stepCount: Int
    )

    private data class StoredEntry(
        val entry: Entry,
        val lastAccess: Instant,
    )

    private val slots = ConcurrentHashMap<Long, StoredEntry>()
    private val nextId = AtomicLong(1)
    private val publicationScopes = ConcurrentHashMap<Long, Set<Long>>()
    private val nextPublicationScope = AtomicLong(0)

    internal var now: () -> Instant = { Instant.now() }

    fun save(state: GameState, playerIds: List<EntityId>, stepCount: Int): SnapshotHandle.Slot {
        val id = nextId.getAndIncrement()
        slots[id] = StoredEntry(
            entry = Entry(state, playerIds, stepCount),
            lastAccess = now(),
        )
        return SnapshotHandle.Slot(id)
    }

    fun load(handle: SnapshotHandle): Entry = when (handle) {
        is SnapshotHandle.Slot -> {
            val at = now()
            slots.computeIfPresent(handle.slotId) { _, stored ->
                stored.copy(lastAccess = at)
            }?.entry ?: throw NoSuchElementException("Snapshot slot ${handle.slotId} not found")
        }
    }

    fun dispose(handle: SnapshotHandle) {
        if (handle is SnapshotHandle.Slot) slots.remove(handle.slotId)
    }

    /**
     * Protect snapshots created by [block] until their handles have been published to a caller.
     *
     * Idle cleanup can otherwise reclaim a newly-saved slot before an outer transport boundary has
     * returned its opaque handle. Slots absent from this scope's admission baseline are therefore
     * ineligible for [disposeIdle] until the scope closes. On successful completion they receive a
     * fresh activity timestamp before the protection is removed. Pre-existing idle snapshots remain
     * eligible for normal cleanup throughout the scope.
     */
    fun <T> withPublication(block: () -> T): T {
        val before = slots.keys.toSet()
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
                    renew(slots.keys - before, now())
                }
            } finally {
                publicationScopes.remove(scopeId)
            }
        }
    }

    /**
     * Release snapshots idle for at least [ttlMs]. `ttlMs=0` disables automatic disposal.
     *
     * Each slot is checked with [ConcurrentHashMap.computeIfPresent], the same per-key atomic
     * boundary used by [load], so a load that renews a slot before its cleanup check cannot be
     * removed based on a stale last-access value.
     *
     * @return number of slots removed
     */
    fun disposeIdle(ttlMs: Long): Int {
        require(ttlMs >= 0) { "snapshot TTL must be >= 0" }
        if (ttlMs == 0L) return 0
        return disposeIdle(ttlMs, now())
    }

    /** Same cleanup operation with an explicit scan timestamp for deterministic schedulers/tests. */
    fun disposeIdle(ttlMs: Long, at: Instant): Int {
        require(ttlMs > 0) { "snapshot TTL must be > 0 when cleanup is enabled" }
        val disposed = AtomicInteger(0)
        slots.keys.forEach { slotId ->
            slots.computeIfPresent(slotId) { _, stored ->
                val expired = !stored.lastAccess.plusMillis(ttlMs).isAfter(at)
                if (expired && !isAwaitingPublication(slotId)) {
                    disposed.incrementAndGet()
                    null
                } else {
                    stored
                }
            }
        }
        return disposed.get()
    }

    private fun renew(slotIds: Collection<Long>, at: Instant) {
        slotIds.forEach { slotId ->
            slots.computeIfPresent(slotId) { _, stored -> stored.copy(lastAccess = at) }
        }
    }

    private fun isAwaitingPublication(slotId: Long): Boolean =
        publicationScopes.values.any { baseline -> slotId !in baseline }

    fun size(): Int = slots.size
}
