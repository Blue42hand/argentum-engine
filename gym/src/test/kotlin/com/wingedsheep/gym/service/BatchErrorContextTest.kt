package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BatchErrorContextTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    fun simpleDeck() = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))

    fun config() = EnvConfig(
        players = listOf(
            PlayerSpec(name = "Alice", deck = simpleDeck()),
            PlayerSpec(name = "Bob", deck = simpleDeck())
        ),
        skipMulligans = true,
        startingPlayerIndex = 0
    )

    test("step batch identifies the failing env and preserves bad-request category") {
        val service = MultiEnvService(registry())
        val healthy = service.create(config()).envId
        val failing = service.create(config()).envId
        val healthyAction = service.observe(healthy).observation.legalActions.first().actionId

        val error = shouldThrow<IllegalArgumentException> {
            service.stepBatch(
                listOf(
                    StepRequest(healthy, healthyAction),
                    StepRequest(failing, 99_999)
                )
            )
        }

        error.message?.contains("step batch item envId=$failing failed") shouldBe true
        (error.cause is IllegalArgumentException) shouldBe true
    }
})
