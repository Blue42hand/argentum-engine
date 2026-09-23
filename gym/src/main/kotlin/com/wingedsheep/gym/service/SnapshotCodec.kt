package com.wingedsheep.gym.service

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    /**
     * An in-process slot managed by one [SnapshotCodec] instance.
     *
     * [codecId] is nullable only so older serialized handles remain decodable. A legacy handle
     * deliberately fails closed on load because the in-process snapshot it named cannot survive the
     * codec/process that created it.
     */
    @Serializable
    data class Slot(
        val slotId: Long,
        val codecId: String? = null,
    ) : SnapshotHandle
}

/**
 * Stores [GameState] snapshots and their player-ID roster in-process. Since
 * `GameState` is fully immutable, saving is free — we just hold a reference
 * — and restoring is also free: the restored env's state field is set back
 * to the referenced object, no deep copy required.
 *
 * Slots are keyed by a monotonically-increasing `Long` within this codec and handles also carry an
 * opaque per-codec namespace. The namespace makes stale handles fail closed across process restarts
 * or independent service instances even when both local counters allocate the same slot number.
 * `dispose` is optional but recommended for long-lived training sessions so the JVM can collect old
 * snapshots.
 */
class SnapshotCodec {
    private val codecId = UUID.randomUUID().toString()
    private val slots = ConcurrentHashMap<Long, Entry>()
    private val nextId = AtomicLong(1)

    data class Entry(
        val state: GameState,
        val playerIds: List<EntityId>,
        val stepCount: Int
    )

    fun save(state: GameState, playerIds: List<EntityId>, stepCount: Int): SnapshotHandle.Slot {
        val id = nextId.getAndIncrement()
        slots[id] = Entry(state, playerIds, stepCount)
        return SnapshotHandle.Slot(id, codecId)
    }

    fun load(handle: SnapshotHandle): Entry = when (handle) {
        is SnapshotHandle.Slot -> {
            requireOwned(handle)
            slots[handle.slotId]
                ?: throw NoSuchElementException("Snapshot slot ${handle.slotId} not found")
        }
    }

    fun dispose(handle: SnapshotHandle) {
        // Disposal stays idempotent, but a stale/foreign handle must never delete a colliding slot.
        if (handle is SnapshotHandle.Slot && handle.codecId == codecId) {
            slots.remove(handle.slotId)
        }
    }

    private fun requireOwned(handle: SnapshotHandle.Slot) {
        if (handle.codecId != codecId) {
            throw NoSuchElementException(
                "Snapshot slot ${handle.slotId} does not belong to this snapshot codec"
            )
        }
    }

    fun size(): Int = slots.size
}
