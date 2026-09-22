package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.server.config.createGymCardRegistry
import com.wingedsheep.gym.server.dto.ObserveBatchItem
import com.wingedsheep.gym.server.lifecycle.EnvLeaseManager
import com.wingedsheep.gym.service.DeckSpec
import com.wingedsheep.gym.service.EnvConfig
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.PlayerSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.time.Instant

class ObserveBatchControllerTest : FunSpec({
    test("empty observe batch maps to an empty response") {
        val service = MultiEnvService(CardRegistry())
        val controller = ObserveBatchController(service, EnvLeaseManager(service, ttlMs = 0))

        controller.observeBatch(emptyList()) shouldBe emptyList()
    }

    test("observe batch renews the body-derived lease without a header") {
        val service = MultiEnvService(createGymCardRegistry())
        val envId = service.create(
            EnvConfig(
                players = listOf(
                    PlayerSpec("Alice", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))),
                    PlayerSpec("Bob", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))),
                ),
                skipMulligans = true,
                startingPlayerIndex = 0,
            )
        ).envId
        val manager = EnvLeaseManager(service, ttlMs = 1_000)
        var instant = Instant.parse("2026-09-22T00:00:00Z")
        manager.now = { instant }
        manager.reapIdle()

        instant = instant.plusMillis(900)
        ObserveBatchController(service, manager).observeBatch(listOf(ObserveBatchItem(envId)))

        instant = instant.plusMillis(200) // beyond the original lease, inside the renewed lease
        manager.reapIdle()
        service.listEnvs() shouldContain envId

        instant = instant.plusMillis(801)
        manager.reapIdle()
        service.listEnvs() shouldNotContain envId
    }
})
