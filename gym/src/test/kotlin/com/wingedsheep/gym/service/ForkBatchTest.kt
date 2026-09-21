package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class ForkBatchTest : FunSpec({
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

    test("forkBatch expands distinct source envs in request order with isolated children") {
        val svc = MultiEnvService(registry())
        val first = svc.create(config(1))
        val second = svc.create(config(2))
        val firstDigest = svc.observe(first.envId).observation.stateDigest
        val secondDigest = svc.observe(second.envId).observation.stateDigest

        val results = svc.forkBatch(
            listOf(
                ForkRequest(first.envId, count = 2),
                ForkRequest(second.envId, count = 1)
            )
        )

        results.map { it.first } shouldBe listOf(first.envId, second.envId)
        results[0].second shouldHaveSize 2
        results[1].second shouldHaveSize 1
        results.flatMap { it.second }.toSet().size shouldBe 3
        results.flatMap { it.second }.forEach { child ->
            child shouldNotBe first.envId
            child shouldNotBe second.envId
        }
        results[0].second.forEach { child ->
            svc.observe(child).observation.stateDigest shouldBe firstDigest
        }
        results[1].second.forEach { child ->
            svc.observe(child).observation.stateDigest shouldBe secondDigest
        }

        val steppedChild = results[0].second.first()
        val sibling = results[0].second.last()
        val actionId = svc.observe(steppedChild).observation.legalActions.first().actionId
        svc.step(StepRequest(steppedChild, actionId))

        svc.observe(steppedChild).observation.stateDigest shouldNotBe firstDigest
        svc.observe(sibling).observation.stateDigest shouldBe firstDigest
        svc.observe(first.envId).observation.stateDigest shouldBe firstDigest
    }

    test("forkBatch rejects duplicate source env ids before allocating children") {
        val svc = MultiEnvService(registry())
        val created = svc.create(config(1))
        val before = svc.listEnvs()

        val error = shouldThrow<IllegalArgumentException> {
            svc.forkBatch(
                listOf(
                    ForkRequest(created.envId, count = 1),
                    ForkRequest(created.envId, count = 2)
                )
            )
        }

        error.message.orEmpty() shouldContain "fork batch contains duplicate envId"
        svc.listEnvs() shouldBe before
    }

    test("forkBatch identifies invalid count and unknown source without allocating children") {
        val svc = MultiEnvService(registry())
        val created = svc.create(config(1))
        val before = svc.listEnvs()

        val badCount = shouldThrow<IllegalArgumentException> {
            svc.forkBatch(listOf(ForkRequest(created.envId, count = 0)))
        }
        badCount.message.orEmpty() shouldContain "fork batch item envId=${created.envId} failed"
        badCount.message.orEmpty() shouldContain "fork count must be positive"
        svc.listEnvs() shouldBe before

        val missing = EnvId("missing-fork-source")
        val unknown = shouldThrow<NoSuchElementException> {
            svc.forkBatch(listOf(ForkRequest(missing, count = 1)))
        }
        unknown.message.orEmpty() shouldContain "fork batch item envId=$missing failed"
        svc.listEnvs() shouldBe before
    }

    test("empty forkBatch returns empty results") {
        val svc = MultiEnvService(registry())
        svc.forkBatch(emptyList()) shouldBe emptyList()
    }
})
