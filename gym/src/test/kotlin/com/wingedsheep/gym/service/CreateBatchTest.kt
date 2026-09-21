package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class CreateBatchTest : FunSpec({
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

    test("createBatch creates independent envs in request order") {
        val svc = MultiEnvService(registry(), workerPool = EnvWorkerPool(parallelism = 2))

        val created = svc.createBatch(listOf(config(11), config(22)))

        created shouldHaveSize 2
        created.map { it.envId }.toSet().size shouldBe 2
        svc.listEnvs() shouldBe created.map { it.envId }.toSet()
        created.forEach { result ->
            svc.observe(result.envId).observation.stateDigest shouldBe
                result.observation.observation.stateDigest
        }
    }

    test("createBatch disposes successful siblings when one create fails") {
        val svc = MultiEnvService(registry(), workerPool = EnvWorkerPool(parallelism = 2))
        val bad = config(2).copy(
            players = config(2).players.mapIndexed { index, player ->
                if (index == 0) player.copy(deck = DeckSpec.RandomSealed(setCode = "POR")) else player
            }
        )

        val error = shouldThrow<IllegalArgumentException> {
            svc.createBatch(listOf(config(1), bad, config(3)))
        }

        error.message shouldContain "create batch item index=1 failed"
        error.message shouldContain "RandomSealed requires a BoosterGenerator"
        svc.listEnvs() shouldBe emptySet()
    }

    test("createBatch accepts an empty request") {
        val svc = MultiEnvService(registry())

        svc.createBatch(emptyList()) shouldBe emptyList()
        svc.listEnvs() shouldBe emptySet()
    }
})
