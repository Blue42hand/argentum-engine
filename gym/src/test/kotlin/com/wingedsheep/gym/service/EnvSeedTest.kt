package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EnvSeedTest : FunSpec({
    test("explicit EnvConfig seed reproduces the opening observation") {
        val registry = CardRegistry().apply {
            register(PortalSet.cards)
            register(PortalSet.basicLands)
        }
        val deck = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))
        val config = EnvConfig(
            players = listOf(
                PlayerSpec("Alice", deck, playerId = EntityId("alice")),
                PlayerSpec("Bob", deck, playerId = EntityId("bob"))
            ),
            skipMulligans = true,
            startingPlayerIndex = 0,
            seed = 20260920L
        )
        val service = MultiEnvService(registry)

        val first = service.create(config).observation.observation
        val second = service.create(config).observation.observation

        second.stateDigest shouldBe first.stateDigest
    }
})
