package com.wingedsheep.gym.service

import com.wingedsheep.engine.state.GameState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SnapshotHandleOwnershipTest : FunSpec({
    test("snapshot handles cannot alias a colliding slot in another codec") {
        val first = SnapshotCodec()
        val second = SnapshotCodec()

        val firstHandle = first.save(GameState(), emptyList(), stepCount = 11)
        val secondHandle = second.save(GameState(), emptyList(), stepCount = 22)

        // Independent codecs intentionally use the same cheap local counter. Ownership, not the
        // counter value, is what makes an opaque handle safe across restart/service boundaries.
        firstHandle.slotId shouldBe secondHandle.slotId
        firstHandle.codecId shouldNotBe secondHandle.codecId

        shouldThrow<NoSuchElementException> {
            second.load(firstHandle)
        }

        // Cleanup with a stale/foreign handle stays idempotent and cannot delete the local slot.
        second.dispose(firstHandle)
        second.size() shouldBe 1
        second.load(secondHandle).stepCount shouldBe 22
    }

    test("legacy bare slot handles fail closed instead of aliasing a current slot") {
        val codec = SnapshotCodec()
        val currentHandle = codec.save(GameState(), emptyList(), stepCount = 7)
        val legacyHandle = SnapshotHandle.Slot(currentHandle.slotId)

        shouldThrow<NoSuchElementException> {
            codec.load(legacyHandle)
        }

        codec.dispose(legacyHandle)
        codec.size() shouldBe 1
        codec.load(currentHandle).stepCount shouldBe 7
    }
})
