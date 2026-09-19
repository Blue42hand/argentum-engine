package com.wingedsheep.gym.contract

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class ObservationSemanticProvenanceTest : FunSpec({
    fun createRegistry(): CardRegistry {
        val registry = CardRegistry()
        registry.register(PortalSet.cards)
        registry.register(PortalSet.basicLands)
        return registry
    }

    fun simpleDeck() = Deck.of("Mountain" to 17, "Raging Goblin" to 3)

    fun newEnv(): GameEnvironment {
        val env = GameEnvironment.create(createRegistry())
        env.reset(
            GameConfig(
                players = listOf(
                    PlayerConfig("Alice", simpleDeck()),
                    PlayerConfig("Bob", simpleDeck())
                ),
                skipMulligans = true,
                startingPlayerIndex = 0
            )
        )
        return env
    }

    test("game observations expose Argentum semantic identity for every legal action") {
        val env = newEnv()
        val legalActions = env.legalActions()
        legalActions.shouldNotBeEmpty()

        val observation = ObservationBuilder(env.cardRegistry)
            .build(env.state, env.playerIds[0], legalActions)
            .observation as TrainingObservation

        observation.legalActions.size shouldBe legalActions.size
        observation.legalActions.zip(legalActions).forEach { (view, legalAction) ->
            view.semanticId shouldBe SemanticIdentity.forLegalAction(legalAction)
            view.semanticId!! shouldStartWith SemanticIdentity.ACTION_VERSION
        }
    }
})
