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

class RestoreBatchTest : FunSpec({
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

    test("restoreBatch restores distinct envs in request order and preserves env ids") {
        val svc = MultiEnvService(registry())
        val first = svc.create(config(101))
        val second = svc.create(config(202))
        val firstHandle = svc.snapshot(first.envId)
        val secondHandle = svc.snapshot(second.envId)
        val firstDigest = first.observation.observation.stateDigest
        val secondDigest = second.observation.observation.stateDigest

        val firstAction = svc.observe(first.envId).observation.legalActions.first().actionId
        val secondAction = svc.observe(second.envId).observation.legalActions.first().actionId
        svc.step(StepRequest(first.envId, firstAction))
        svc.step(StepRequest(second.envId, secondAction))
        svc.observe(first.envId).observation.stateDigest shouldNotBe firstDigest
        svc.observe(second.envId).observation.stateDigest shouldNotBe secondDigest

        val results = svc.restoreBatch(
            listOf(
                RestoreRequest(first.envId, firstHandle),
                RestoreRequest(second.envId, secondHandle)
            )
        )

        results shouldHaveSize 2
        results.map { it.first } shouldBe listOf(first.envId, second.envId)
        results[0].second.observation.stateDigest shouldBe firstDigest
        results[1].second.observation.stateDigest shouldBe secondDigest
        svc.listEnvs().containsAll(listOf(first.envId, second.envId)) shouldBe true
    }

    test("restoreBatch rejects duplicate target env ids before scheduling work") {
        val svc = MultiEnvService(registry())
        val created = svc.create(config(1))
        val firstHandle = svc.snapshot(created.envId)
        val secondHandle = svc.snapshot(created.envId)

        val error = shouldThrow<IllegalArgumentException> {
            svc.restoreBatch(
                listOf(
                    RestoreRequest(created.envId, firstHandle),
                    RestoreRequest(created.envId, secondHandle)
                )
            )
        }

        error.message.orEmpty() shouldContain "restore batch contains duplicate envId"
    }

    test("restoreBatch identifies the env whose snapshot handle is invalid") {
        val svc = MultiEnvService(registry())
        val created = svc.create(config(1))
        val handle = svc.snapshot(created.envId)
        svc.disposeSnapshot(handle)

        val error = shouldThrow<NoSuchElementException> {
            svc.restoreBatch(listOf(RestoreRequest(created.envId, handle)))
        }

        error.message.orEmpty() shouldContain "restore batch item envId=${created.envId} failed"
        error.message.orEmpty() shouldContain "Snapshot slot"
    }

    test("empty restoreBatch returns empty results") {
        val svc = MultiEnvService(registry())
        svc.restoreBatch(emptyList()) shouldBe emptyList()
    }
})
