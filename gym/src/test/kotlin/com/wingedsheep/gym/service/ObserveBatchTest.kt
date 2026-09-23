package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ObserveBatchTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    val alice = EntityId("alice")
    val bob = EntityId("bob")

    fun config(seed: Long) = EnvConfig(
        players = listOf(
            PlayerSpec(
                name = "Alice",
                deck = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3)),
                playerId = alice
            ),
            PlayerSpec(
                name = "Bob",
                deck = DeckSpec.Explicit(mapOf("Mountain" to 17, "Raging Goblin" to 3)),
                playerId = bob
            )
        ),
        skipMulligans = true,
        startingPlayerIndex = 0,
        seed = seed
    )

    test("observeBatch preserves order and permits multiple seat views of one env") {
        val svc = MultiEnvService(registry())
        val first = svc.create(config(101)).envId
        val second = svc.create(config(202)).envId

        val results = svc.observeBatch(
            listOf(
                ObserveRequest(second, perspectivePlayerId = bob),
                ObserveRequest(first, perspectivePlayerId = alice),
                ObserveRequest(first, perspectivePlayerId = bob)
            )
        )

        results shouldHaveSize 3
        results.map { it.first } shouldBe listOf(second, first, first)

        val secondBob = results[0].second.observation as TrainingObservation
        val firstAlice = results[1].second.observation as TrainingObservation
        val firstBob = results[2].second.observation as TrainingObservation

        secondBob.perspectivePlayerId shouldBe bob
        firstAlice.perspectivePlayerId shouldBe alice
        firstBob.perspectivePlayerId shouldBe bob

        firstAlice.agentToAct shouldBe alice
        firstAlice.legalActions.shouldNotBeEmpty()
        firstBob.agentToAct shouldBe alice
        firstBob.legalActions.shouldBeEmpty()
    }

    test("duplicate seat views leave the acting seat authoritative for the next step") {
        val pool = EnvWorkerPool(parallelism = 1)
        val svc = MultiEnvService(registry(), workerPool = pool)

        try {
            val envId = svc.create(config(303)).envId
            val results = svc.observeBatch(
                listOf(
                    ObserveRequest(envId, perspectivePlayerId = alice),
                    ObserveRequest(envId, perspectivePlayerId = bob)
                )
            )

            val aliceView = results[0].second.observation as TrainingObservation
            val bobView = results[1].second.observation as TrainingObservation
            aliceView.agentToAct shouldBe alice
            aliceView.legalActions.shouldNotBeEmpty()
            bobView.legalActions.shouldBeEmpty()

            val pass = aliceView.legalActions.first { it.kind == "PassPriority" }
            val stepped = svc.step(
                StepRequest(
                    envId = envId,
                    actionId = pass.actionId,
                    expectedStateDigest = aliceView.stateDigest
                )
            ).observation as TrainingObservation

            stepped.agentToAct shouldBe bob
        } finally {
            pool.close()
        }
    }

    test("observeBatch identifies the failing env and preserves singular error category") {
        val svc = MultiEnvService(registry())
        val missing = EnvId("missing-observe-env")

        val error = shouldThrow<NoSuchElementException> {
            svc.observeBatch(listOf(ObserveRequest(missing)))
        }

        error.message.orEmpty() shouldContain "observe batch item envId=$missing failed"
    }

    test("empty observeBatch returns empty results") {
        val svc = MultiEnvService(registry())
        svc.observeBatch(emptyList()) shouldBe emptyList()
    }
})
