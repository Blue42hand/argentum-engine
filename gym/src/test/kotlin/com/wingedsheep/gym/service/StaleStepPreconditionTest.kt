package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class StaleStepPreconditionTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    fun config() = EnvConfig(
        players = listOf(
            PlayerSpec("Alice", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3))),
            PlayerSpec("Bob", DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3)))
        ),
        skipMulligans = true,
        startingPlayerIndex = 0
    )

    test("step accepts the current digest and rejects the same action handle after state advances") {
        val service = MultiEnvService(registry())
        val created = service.create(config())
        val opening = created.observation.observation
        val actionId = opening.legalActions.first().actionId

        service.step(
            StepRequest(
                envId = created.envId,
                actionId = actionId,
                expectedStateDigest = opening.stateDigest
            )
        )

        val advancedDigest = service.observe(created.envId).observation.stateDigest
        val error = shouldThrow<IllegalStateException> {
            service.step(
                StepRequest(
                    envId = created.envId,
                    actionId = actionId,
                    expectedStateDigest = opening.stateDigest
                )
            )
        }

        error.message.orEmpty() shouldContain "Stale step"
        error.message.orEmpty() shouldContain created.envId.value
        service.observe(created.envId).observation.stateDigest shouldBe advancedDigest
    }
})
