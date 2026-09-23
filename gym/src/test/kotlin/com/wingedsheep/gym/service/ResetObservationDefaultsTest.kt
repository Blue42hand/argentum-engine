package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ResetObservationDefaultsTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    fun config(perspective: Int, revealAll: Boolean) = EnvConfig(
        players = listOf(
            PlayerSpec(
                name = "Alice",
                deck = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3)),
                playerId = EntityId("alice")
            ),
            PlayerSpec(
                name = "Bob",
                deck = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3)),
                playerId = EntityId("bob")
            )
        ),
        skipMulligans = true,
        startingPlayerIndex = 0,
        perspectivePlayerIndex = perspective,
        revealAll = revealAll,
        seed = 20260923L
    )

    test("reset replaces default observation perspective and reveal policy") {
        val svc = MultiEnvService(registry())
        val envId = svc.create(config(perspective = 0, revealAll = false)).envId

        val reset = svc.reset(envId, config(perspective = 1, revealAll = true))
            .observation as TrainingObservation
        reset.perspectivePlayerId shouldBe EntityId("bob")
        val aliceHandAfterReset = reset.zones.first {
            it.ownerId == EntityId("alice") && it.zoneType == Zone.HAND
        }
        aliceHandAfterReset.hidden shouldBe false
        aliceHandAfterReset.cards.size shouldBe aliceHandAfterReset.size

        val observed = svc.observe(envId).observation as TrainingObservation
        observed.perspectivePlayerId shouldBe EntityId("bob")
        val aliceHandOnNextObserve = observed.zones.first {
            it.ownerId == EntityId("alice") && it.zoneType == Zone.HAND
        }
        aliceHandOnNextObserve.hidden shouldBe false
        aliceHandOnNextObserve.cards.size shouldBe aliceHandOnNextObserve.size
    }
})
