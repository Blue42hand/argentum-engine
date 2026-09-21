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

    fun advance(svc: MultiEnvService, envId: EnvId) {
        val actionId = svc.observe(envId).observation.legalActions.first().actionId
        svc.step(StepRequest(envId, actionId))
    }

    test("restoreBatch restores distinct envs in request order and preserves env ids") {
        val svc = MultiEnvService(registry())
        val first = svc.create(config(1))
        val second = svc.create(config(2))
        val firstHandle = svc.snapshot(first.envId)
        val secondHandle = svc.snapshot(second.envId)

        advance(svc, first.envId)
        advance(svc, second.envId)
        svc.observe(first.envId).observation.stateDigest shouldNotBe first.observation.observation.stateDigest
        svc.observe(second.envId).observation.stateDigest shouldNotBe second.observation.observation.stateDigest

        val results = svc.restoreBatch(
            listOf(
                RestoreRequest(first.envId, firstHandle),
                RestoreRequest(second.envId, secondHandle)
            )
        )

        results shouldHaveSize 2
        results.map { it.first } shouldBe listOf(first.envId, second.envId)
        results[0].second.observation.stateDigest shouldBe first.observation.observation.stateDigest
        results[1].second.observation.stateDigest shouldBe second.observation.observation.stateDigest
    }

    test("restoreBatch rejects duplicate env ids before scheduling work") {
        val svc = MultiEnvService(registry())
        val created = svc.create(config(1))
        val handle = svc.snapshot(created.envId)
        advance(svc, created.envId)
        val mutatedDigest = svc.observe(created.envId).observation.stateDigest

        val error = shouldThrow<IllegalArgumentException> {
            svc.restoreBatch(
                listOf(
                    RestoreRequest(created.envId, handle),
                    RestoreRequest(created.envId, handle)
                )
            )
        }

        error.message.orEmpty() shouldContain "restore batch contains duplicate envId"
        svc.observe(created.envId).observation.stateDigest shouldBe mutatedDigest
    }

    test("restoreBatch identifies an unknown failing env") {
        val svc = MultiEnvService(registry())
        val created = svc.create(config(1))
        val handle = svc.snapshot(created.envId)
        val missing = EnvId("missing-restore-env")

        val error = shouldThrow<NoSuchElementException> {
            svc.restoreBatch(listOf(RestoreRequest(missing, handle)))
        }

        error.message.orEmpty() shouldContain "restore batch item envId=$missing failed"
    }

    test("empty restoreBatch returns empty results") {
        val svc = MultiEnvService(registry())
        svc.restoreBatch(emptyList()) shouldBe emptyList()
    }
})
