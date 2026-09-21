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

class SnapshotBatchTest : FunSpec({
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

    test("snapshotBatch captures distinct envs in request order and restores their branch points") {
        val svc = MultiEnvService(registry())
        val first = svc.create(config(1))
        val second = svc.create(config(2))

        val results = svc.snapshotBatch(listOf(first.envId, second.envId))

        results shouldHaveSize 2
        results.map { it.first } shouldBe listOf(first.envId, second.envId)
        results[0].second shouldNotBe results[1].second
        svc.snapshotCodec.size() shouldBe 2

        advance(svc, first.envId)
        advance(svc, second.envId)
        svc.restore(first.envId, results[0].second).observation.stateDigest shouldBe
            first.observation.observation.stateDigest
        svc.restore(second.envId, results[1].second).observation.stateDigest shouldBe
            second.observation.observation.stateDigest
    }

    test("snapshotBatch rejects duplicate env ids before retaining any snapshots") {
        val svc = MultiEnvService(registry())
        val created = svc.create(config(1))

        val error = shouldThrow<IllegalArgumentException> {
            svc.snapshotBatch(listOf(created.envId, created.envId))
        }

        error.message.orEmpty() shouldContain "snapshot batch contains duplicate envId"
        svc.snapshotCodec.size() shouldBe 0
    }

    test("snapshotBatch identifies an unknown failing env") {
        val svc = MultiEnvService(registry())
        val missing = EnvId("missing-snapshot-env")

        val error = shouldThrow<NoSuchElementException> {
            svc.snapshotBatch(listOf(missing))
        }

        error.message.orEmpty() shouldContain "snapshot batch item envId=$missing failed"
        svc.snapshotCodec.size() shouldBe 0
    }

    test("empty snapshotBatch returns empty results") {
        val svc = MultiEnvService(registry())
        svc.snapshotBatch(emptyList()) shouldBe emptyList()
        svc.snapshotCodec.size() shouldBe 0
    }
})
