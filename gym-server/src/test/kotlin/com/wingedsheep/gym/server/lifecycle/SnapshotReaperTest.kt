package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.SnapshotCodec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class SnapshotReaperTest : FunSpec({
    test("enabled snapshot reaper releases idle slots") {
        val codec = SnapshotCodec()
        val service = MultiEnvService(CardRegistry(), snapshotCodec = codec)
        codec.save(GameState(), emptyList(), 0)
        val reaper = SnapshotReaper(service, ttlMs = 1_000).also {
            it.now = { Instant.parse("2100-01-01T00:00:00Z") }
        }

        codec.size() shouldBe 1
        reaper.reapIdle()
        codec.size() shouldBe 0
    }

    test("disabled snapshot reaper preserves explicit cleanup contract") {
        val codec = SnapshotCodec()
        val service = MultiEnvService(CardRegistry(), snapshotCodec = codec)
        codec.save(GameState(), emptyList(), 0)
        val reaper = SnapshotReaper(service, ttlMs = 0).also {
            it.now = { Instant.parse("2100-01-01T00:00:00Z") }
        }

        reaper.reapIdle()
        codec.size() shouldBe 1
    }
})
