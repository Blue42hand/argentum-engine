package com.wingedsheep.gym

import com.wingedsheep.engine.core.ChooseDoorContinuation
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.contract.ActionParams
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GameGymEnvSeatSafetyTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    test("another seat cannot observe pending decision semantic provenance") {
        val environment = GameEnvironment.create(registry())
        environment.reset(
            GameConfig(
                players = listOf(
                    PlayerConfig("Alice", Deck.of("Mountain" to 20)),
                    PlayerConfig("Bob", Deck.of("Mountain" to 20))
                ),
                skipMulligans = true,
                startingPlayerIndex = 0,
                seed = 20260919L
            )
        )

        val alice = environment.playerIds[0]
        val bob = environment.playerIds[1]
        val suspended = environment.state.suspendForDecision(
            question = { id ->
                YesNoDecision(
                    id = id,
                    playerId = bob,
                    prompt = "Secret decision for Bob",
                    context = DecisionContext(sourceId = EntityId("hidden-source"))
                )
            },
            answer = ChooseDoorContinuation(
                controllerId = bob,
                roomId = EntityId("unused-room"),
                candidateFaceIds = emptyList(),
                lock = true
            )
        )
        val hiddenDecisionId = requireNotNull(suspended.pendingDecision).id
        environment.restore(suspended.state, environment.playerIds)

        val aliceEnv = GameGymEnv(
            environment = environment,
            perspectivePlayerIndex = 0,
            defaultRevealAll = false
        )
        val aliceView = aliceEnv.observe().observation as TrainingObservation

        aliceView.perspectivePlayerId shouldBe alice
        aliceView.agentToAct shouldBe bob
        aliceView.pendingDecision shouldBe null
        aliceView.legalActions.shouldNotBeEmpty()
        aliceView.legalActions.all { it.semanticId == null } shouldBe true

        val debugView = aliceEnv.observe(revealAll = true).observation as TrainingObservation
        val debugDecision = debugView.pendingDecision
        debugDecision shouldNotBe null
        debugDecision!!.decisionId shouldBe hiddenDecisionId
        debugDecision.semanticId shouldNotBe null
        debugView.legalActions.shouldNotBeEmpty()
        debugView.legalActions.all { it.semanticId != null } shouldBe true

        // Removing cross-seat provenance must not break the existing shared-env stepping contract.
        aliceEnv.step(aliceView.legalActions.first().actionId, ActionParams())
    }
})
