package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
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
        startingPlayerIndex = 0,
        seed = 20260922L
    )

    test("step accepts the current digest and rejects the same observation after state advances") {
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
        service.observe(created.envId).observation.stateDigest shouldBe advancedDigest
    }

    test("digest from a non-default acting seat keeps that seat's action registry") {
        val alice = EntityId("alice")
        val bob = EntityId("bob")
        val service = MultiEnvService(registry())
        val created = service.create(
            EnvConfig(
                players = listOf(
                    PlayerSpec(
                        "Alice",
                        DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3)),
                        playerId = alice
                    ),
                    PlayerSpec(
                        "Bob",
                        DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3)),
                        playerId = bob
                    )
                ),
                skipMulligans = true,
                startingPlayerIndex = 1,
                perspectivePlayerIndex = 0,
                seed = 20260922L
            )
        )

        val defaultOpening = created.observation.observation
        defaultOpening.agentToAct shouldBe bob
        defaultOpening.legalActions.shouldBeEmpty()

        val bobOpening = service.observe(created.envId, perspectivePlayerId = bob).observation
        bobOpening.agentToAct shouldBe bob
        val actionId = bobOpening.legalActions.first().actionId

        service.step(
            StepRequest(
                envId = created.envId,
                actionId = actionId,
                expectedStateDigest = bobOpening.stateDigest
            )
        )
    }
})
