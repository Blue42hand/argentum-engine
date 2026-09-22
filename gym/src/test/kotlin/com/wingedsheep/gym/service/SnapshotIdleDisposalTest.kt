package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class SnapshotIdleDisposalTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    fun deck() = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))

    fun config() = EnvConfig(
        players = listOf(
            PlayerSpec("Alice", deck()),
            PlayerSpec("Bob", deck()),
        ),
        skipMulligans = true,
        startingPlayerIndex = 0,
        seed = 20260922L,
    )

    test("idle snapshot slots can be reclaimed") {
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        val codec = SnapshotCodec().also { it.now = { instant } }
        val service = MultiEnvService(registry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        val handle = service.snapshot(envId)

        codec.size() shouldBe 1
        instant = instant.plusMillis(1_000)
        codec.disposeIdle(1_000) shouldBe 1
        codec.size() shouldBe 0

        shouldThrow<NoSuchElementException> {
            service.restore(envId, handle)
        }
    }

    test("loading a snapshot renews activity before a stale cleanup check") {
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        val codec = SnapshotCodec().also { it.now = { instant } }
        val service = MultiEnvService(registry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        val handle = service.snapshot(envId)

        instant = instant.plusMillis(1_001)
        val staleSweepAt = instant
        instant = instant.plusMillis(1)
        service.restore(envId, handle)

        codec.disposeIdle(1_000, staleSweepAt) shouldBe 0
        codec.size() shouldBe 1

        instant = instant.plusMillis(999)
        codec.disposeIdle(1_000) shouldBe 0
        instant = instant.plusMillis(1)
        codec.disposeIdle(1_000) shouldBe 1
    }

    test("snapshot lifetime is independent from its source env and ttl zero stays disabled") {
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        val codec = SnapshotCodec().also { it.now = { instant } }
        val service = MultiEnvService(registry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        service.snapshot(envId)

        service.dispose(listOf(envId))
        service.listEnvs().size shouldBe 0
        codec.size() shouldBe 1

        instant = instant.plusSeconds(86_400)
        codec.disposeIdle(0) shouldBe 0
        codec.size() shouldBe 1
    }

    test("new snapshots survive publication while unrelated idle slots still expire") {
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        val codec = SnapshotCodec().also { it.now = { instant } }
        val service = MultiEnvService(registry(), snapshotCodec = codec)
        val envId = service.create(config()).envId
        service.snapshot(envId)

        instant = instant.plusMillis(1_001)
        codec.withPublication {
            service.snapshot(envId)
            instant = instant.plusMillis(1_001)

            codec.disposeIdle(1_000) shouldBe 1
            codec.size() shouldBe 1
        }

        instant = instant.plusMillis(999)
        codec.disposeIdle(1_000) shouldBe 0
        codec.size() shouldBe 1

        instant = instant.plusMillis(1)
        codec.disposeIdle(1_000) shouldBe 1
        codec.size() shouldBe 0
    }
})
