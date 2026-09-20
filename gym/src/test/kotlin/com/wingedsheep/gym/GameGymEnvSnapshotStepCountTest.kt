package com.wingedsheep.gym

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.SnapshotCodec
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GameGymEnvSnapshotStepCountTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    test("snapshot restore preserves the environment step count") {
        val environment = GameEnvironment.create(registry())
        environment.reset(
            GameConfig(
                players = listOf(
                    PlayerConfig("Alice", Deck.of("Mountain" to 20)),
                    PlayerConfig("Bob", Deck.of("Mountain" to 20))
                ),
                skipMulligans = true,
                startingPlayerIndex = 0,
                seed = 20260920L
            )
        )
        val gymEnv = GameGymEnv(
            environment = environment,
            perspectivePlayerIndex = 0,
            defaultRevealAll = false
        )
        val codec = SnapshotCodec()

        environment.step(environment.legalActions().first().action)
        val snapshottedStepCount = environment.stepCount
        snapshottedStepCount shouldBe 1
        val handle = gymEnv.snapshot(codec)

        environment.step(environment.legalActions().first().action)
        environment.stepCount shouldBe 2

        gymEnv.restore(codec, handle)
        environment.stepCount shouldBe snapshottedStepCount
    }
})
