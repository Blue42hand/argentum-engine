package com.wingedsheep.gym.contract

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class StateDigestSemanticProvenanceTest : FunSpec({
    fun createRegistry(): CardRegistry {
        val registry = CardRegistry()
        registry.register(PortalSet.cards)
        registry.register(PortalSet.basicLands)
        return registry
    }

    fun simpleDeck() = Deck.of("Mountain" to 17, "Raging Goblin" to 3)

    fun baseObservation(): TrainingObservation {
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
        return ObservationBuilder(env.cardRegistry)
            .build(env.state, env.playerIds[0], env.legalActions())
            .observation as TrainingObservation
    }

    fun pending(base: TrainingObservation, routingId: String, semanticId: String) = PendingDecisionView(
        decisionId = routingId,
        semanticId = semanticId,
        kind = PendingDecisionKind.YES_NO,
        playerId = base.agentToAct ?: base.perspectivePlayerId,
        prompt = "Choose whether to continue"
    )

    test("state digest ignores the pending decision routing nonce") {
        val base = baseObservation()
        val semanticId = "argentum-decision-v1:same-semantics"

        val first = StateDigest.compute(
            base.copy(pendingDecision = pending(base, "routing-a", semanticId))
        )
        val rerouted = StateDigest.compute(
            base.copy(pendingDecision = pending(base, "routing-b", semanticId))
        )

        first shouldBe rerouted
    }

    test("state digest changes when pending decision semantics change") {
        val base = baseObservation()

        val first = StateDigest.compute(
            base.copy(
                pendingDecision = pending(
                    base,
                    routingId = "routing-a",
                    semanticId = "argentum-decision-v1:semantic-a"
                )
            )
        )
        val changed = StateDigest.compute(
            base.copy(
                pendingDecision = pending(
                    base,
                    routingId = "routing-a",
                    semanticId = "argentum-decision-v1:semantic-b"
                )
            )
        )

        first shouldNotBe changed
    }
})
