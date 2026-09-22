package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.DeckSpec
import com.wingedsheep.gym.service.EnvConfig
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.PlayerSpec
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import java.time.Instant

class EnvLeaseManagerTest : FunSpec({
    fun registry() = CardRegistry().apply {
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
    )

    test("idle environments are reaped after the configured TTL") {
        val service = MultiEnvService(registry())
        val envId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }

        manager.reapIdle()
        service.listEnvs() shouldContain envId

        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldNotContain envId
    }

    test("an active leased request cannot be reaped and renews activity when it ends") {
        val service = MultiEnvService(registry())
        val envId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }

        manager.reapIdle()
        instant = instant.plusMillis(1_001)
        manager.begin(listOf(envId))
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        manager.end(listOf(envId))
        instant = instant.plusMillis(999)
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        instant = instant.plusMillis(2)
        manager.reapIdle()
        service.listEnvs() shouldNotContain envId
    }

    test("overlapping leased requests keep the environment active until all requests end") {
        val service = MultiEnvService(registry())
        val envId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }

        manager.reapIdle()
        manager.begin(listOf(envId))
        manager.begin(listOf(envId))

        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        manager.end(listOf(envId))
        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        manager.end(listOf(envId))
        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldNotContain envId
    }

    test("disabled leases preserve explicit-dispose-only behavior") {
        val service = MultiEnvService(registry())
        val envId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 0)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }

        manager.reapIdle()
        instant = instant.plusSeconds(86_400)
        manager.reapIdle()

        service.listEnvs() shouldContain envId
    }
})
