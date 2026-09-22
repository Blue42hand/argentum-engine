package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.server.config.createGymCardRegistry
import com.wingedsheep.gym.service.DeckSpec
import com.wingedsheep.gym.service.EnvConfig
import com.wingedsheep.gym.service.EnvId
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.PlayerSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import java.time.Instant

class EnvLeaseManagerTest : FunSpec({
    fun config() = EnvConfig(
        players = listOf(
            PlayerSpec("Alice", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))),
            PlayerSpec("Bob", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))),
        ),
        skipMulligans = true,
        startingPlayerIndex = 0,
    )

    test("idle environments are reaped after the configured TTL") {
        val service = MultiEnvService(createGymCardRegistry())
        val envId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }

        manager.reapIdle() // discover the live env and grant its initial grace period
        service.listEnvs() shouldContain envId

        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldNotContain envId
    }

    test("an active leased request cannot be reaped and renews activity when it ends") {
        val service = MultiEnvService(createGymCardRegistry())
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

    test("a scoped body-derived lease protects batch work and renews on completion") {
        val service = MultiEnvService(createGymCardRegistry())
        val envId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }

        manager.reapIdle()
        instant = instant.plusMillis(1_001)

        manager.withLeases(listOf(envId)) {
            manager.reapIdle()
            service.listEnvs() shouldContain envId
            instant = instant.plusMillis(5_000)
            manager.reapIdle()
            service.listEnvs() shouldContain envId
        }

        instant = instant.plusMillis(999)
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        instant = instant.plusMillis(2)
        manager.reapIdle()
        service.listEnvs() shouldNotContain envId
    }

    test("new environments cannot expire before publication while unrelated idle envs still reap") {
        val service = MultiEnvService(createGymCardRegistry())
        val oldEnvId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }
        lateinit var newEnvId: EnvId

        manager.reapIdle() // discover the pre-existing env
        instant = instant.plusMillis(1_001)

        manager.withEnvPublication {
            newEnvId = service.create(config()).envId
            manager.reapIdle()
            service.listEnvs() shouldNotContain oldEnvId
            service.listEnvs() shouldContain newEnvId

            instant = instant.plusMillis(5_000)
            manager.reapIdle() // well beyond TTL, but the new env is still unpublished
            service.listEnvs() shouldContain newEnvId
        }

        instant = instant.plusMillis(999)
        manager.reapIdle()
        service.listEnvs() shouldContain newEnvId

        instant = instant.plusMillis(2)
        manager.reapIdle()
        service.listEnvs() shouldNotContain newEnvId
    }

    test("overlapping leased requests keep the environment active until all requests end") {
        val service = MultiEnvService(createGymCardRegistry())
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

    test("a stale live-environment scan cannot discard an active lease") {
        val service = MultiEnvService(createGymCardRegistry())
        val envId = service.create(config()).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }

        manager.begin(listOf(envId))
        manager.reconcileLeases(emptySet(), instant) // simulate a scan taken before this env existed

        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        manager.end(listOf(envId))
        instant = instant.plusMillis(1_001)
        manager.reapIdle()
        service.listEnvs() shouldNotContain envId
    }

    test("disabled leases preserve explicit-dispose-only behavior") {
        val service = MultiEnvService(createGymCardRegistry())
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
