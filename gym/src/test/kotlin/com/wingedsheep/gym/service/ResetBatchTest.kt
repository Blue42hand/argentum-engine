package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ResetBatchTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    fun config(seed: Long) = EnvConfig(
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
        perspectivePlayerIndex = 0,
        seed = seed
    )

    test("resetBatch resets distinct envs in request order and preserves env ids") {
        val svc = MultiEnvService(registry())
        val first = svc.create(config(1)).envId
        val second = svc.create(config(2)).envId

        listOf(first, second).forEach { envId ->
            val actionId = svc.observe(envId).observation.legalActions.first().actionId
            svc.step(StepRequest(envId, actionId))
        }

        val firstConfig = config(101)
        val secondConfig = config(202)
        val expectedFirst = svc.create(firstConfig).observation.observation.stateDigest
        val expectedSecond = svc.create(secondConfig).observation.observation.stateDigest

        val results = svc.resetBatch(
            listOf(
                ResetRequest(first, firstConfig),
                ResetRequest(second, secondConfig)
            )
        )

        results shouldHaveSize 2
        results.map { it.first } shouldBe listOf(first, second)
        (results[0].second.observation as TrainingObservation).turnNumber.shouldBeExactly(1)
        (results[1].second.observation as TrainingObservation).turnNumber.shouldBeExactly(1)
        results[0].second.observation.stateDigest shouldBe expectedFirst
        results[1].second.observation.stateDigest shouldBe expectedSecond
    }

    test("resetBatch rejects duplicate env ids before scheduling work") {
        val svc = MultiEnvService(registry())
        val envId = svc.create(config(1)).envId

        val error = shouldThrow<IllegalArgumentException> {
            svc.resetBatch(
                listOf(
                    ResetRequest(envId, config(2)),
                    ResetRequest(envId, config(3))
                )
            )
        }

        error.message.orEmpty() shouldContain "reset batch contains duplicate envId"
    }

    test("resetBatch identifies an unknown failing env") {
        val svc = MultiEnvService(registry())
        val missing = EnvId("missing-reset-env")

        val error = shouldThrow<NoSuchElementException> {
            svc.resetBatch(listOf(ResetRequest(missing, config(1))))
        }

        error.message.orEmpty() shouldContain "reset batch item envId=$missing failed"
    }

    test("empty resetBatch returns empty results") {
        val svc = MultiEnvService(registry())
        svc.resetBatch(emptyList()) shouldBe emptyList()
    }
})