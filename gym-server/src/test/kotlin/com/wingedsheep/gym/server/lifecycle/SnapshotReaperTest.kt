package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.server.config.createGymCardRegistry
import com.wingedsheep.gym.service.DeckSpec
import com.wingedsheep.gym.service.EnvConfig
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.PlayerSpec
import com.wingedsheep.gym.service.SnapshotCodec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class SnapshotReaperTest : FunSpec({
    fun config() = EnvConfig(
        players = listOf(
            PlayerSpec("Alice", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))),
            PlayerSpec("Bob", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))),
        ),
        skipMulligans = true,
        startingPlayerIndex = 0,
        seed = 20260922L,
    )

    test("enabled snapshot reaper releases idle slots") {
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        val codec = SnapshotCodec().also { it.now = { instant } }
        val service = MultiEnvService(createGymCardRegistry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        service.snapshot(envId)
        val reaper = SnapshotReaper(service, ttlMs = 1_000)

        codec.size() shouldBe 1
        instant = instant.plusMillis(1_000)
        reaper.reapIdle()
        codec.size() shouldBe 0
    }

    test("disabled snapshot reaper preserves explicit cleanup") {
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        val codec = SnapshotCodec().also { it.now = { instant } }
        val service = MultiEnvService(createGymCardRegistry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        service.snapshot(envId)
        val reaper = SnapshotReaper(service, ttlMs = 0)

        instant = instant.plusSeconds(86_400)
        reaper.reapIdle()
        codec.size() shouldBe 1
    }
})
