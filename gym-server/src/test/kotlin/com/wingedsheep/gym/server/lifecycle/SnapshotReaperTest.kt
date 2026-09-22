package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.DeckSpec
import com.wingedsheep.gym.service.EnvConfig
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.PlayerSpec
import com.wingedsheep.gym.service.SnapshotCodec
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class SnapshotReaperTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

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
        val codec = SnapshotCodec()
        val service = MultiEnvService(registry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        service.snapshot(envId)
        val reaper = SnapshotReaper(service, ttlMs = 1_000).also {
            it.now = { Instant.parse("2100-01-01T00:00:00Z") }
        }

        codec.size() shouldBe 1
        reaper.reapIdle()
        codec.size() shouldBe 0
    }

    test("disabled snapshot reaper preserves explicit cleanup") {
        val codec = SnapshotCodec()
        val service = MultiEnvService(registry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        service.snapshot(envId)
        val reaper = SnapshotReaper(service, ttlMs = 0).also {
            it.now = { Instant.parse("2100-01-01T00:00:00Z") }
        }

        reaper.reapIdle()
        codec.size() shouldBe 1
    }
})
