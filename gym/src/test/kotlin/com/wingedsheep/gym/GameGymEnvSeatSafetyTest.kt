package com.wingedsheep.gym

import com.wingedsheep.engine.core.ChooseDoorContinuation
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.MayAbilityContinuation
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.contract.ActionParams
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GameGymEnvSeatSafetyTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    test("another seat cannot observe or submit the acting seat's decision surface") {
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
        val baseState = environment.state

        fun suspendForBob(sourceId: String) = baseState.suspendForDecision(
            question = { id ->
                YesNoDecision(
                    id = id,
                    playerId = bob,
                    prompt = "Secret decision for Bob",
                    context = DecisionContext(sourceId = EntityId(sourceId))
                )
            },
            answer = ChooseDoorContinuation(
                controllerId = bob,
                roomId = EntityId("unused-room"),
                candidateFaceIds = emptyList(),
                lock = true
            )
        )

        val firstSuspended = suspendForBob("hidden-source-a")
        val firstHiddenDecisionId = requireNotNull(firstSuspended.pendingDecision).id
        environment.restore(firstSuspended.state, environment.playerIds)

        val aliceEnv = GameGymEnv(
            environment = environment,
            perspectivePlayerIndex = 0,
            defaultRevealAll = false
        )
        val firstAliceView = aliceEnv.observe().observation as TrainingObservation

        firstAliceView.perspectivePlayerId shouldBe alice
        firstAliceView.agentToAct shouldBe bob
        firstAliceView.pendingDecision shouldBe null
        firstAliceView.legalActions.shouldBeEmpty()
        shouldThrow<IllegalArgumentException> {
            aliceEnv.step(0, ActionParams())
        }

        val firstBobView = aliceEnv.observeForPlayer(bob).observation as TrainingObservation
        firstBobView.perspectivePlayerId shouldBe bob
        firstBobView.agentToAct shouldBe bob
        firstBobView.pendingDecision.shouldNotBeNull()
        firstBobView.legalActions.shouldNotBeEmpty()
        firstBobView.legalActions.all { it.semanticId != null } shouldBe true

        shouldThrow<IllegalArgumentException> {
            aliceEnv.observeForPlayer(EntityId("not-seated"))
        }

        val firstDebugView = aliceEnv.observe(revealAll = true).observation as TrainingObservation
        val firstDebugDecision = firstDebugView.pendingDecision
        firstDebugDecision shouldNotBe null
        firstDebugDecision!!.decisionId shouldBe firstHiddenDecisionId
        firstDebugDecision.semanticId shouldNotBe null
        firstDebugView.legalActions.shouldNotBeEmpty()
        firstDebugView.legalActions.all { it.semanticId != null } shouldBe true

        // Change only hidden decision semantics. The debug observation must notice the difference,
        // while Alice's seat-projected view remains identical so the digest cannot be used as an
        // equality oracle for another player's private decision or legal-action surface.
        val secondSuspended = suspendForBob("hidden-source-b")
        environment.restore(secondSuspended.state, environment.playerIds)

        val secondAliceView = aliceEnv.observe().observation as TrainingObservation
        val secondDebugView = aliceEnv.observe(revealAll = true).observation as TrainingObservation

        secondAliceView.pendingDecision shouldBe null
        secondAliceView.legalActions.shouldBeEmpty()
        secondAliceView.stateDigest shouldBe firstAliceView.stateDigest

        secondDebugView.pendingDecision shouldNotBe null
        secondDebugView.pendingDecision!!.semanticId shouldNotBe firstDebugDecision.semanticId
        secondDebugView.stateDigest shouldNotBe firstDebugView.stateDigest
    }

    test("raw structured decisions require the deciding seat's latest observation") {
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
            answer = MayAbilityContinuation(
                playerId = bob,
                sourceName = null,
                effectIfYes = null,
                effectIfNo = null,
                effectContext = EffectContext(sourceId = null, controllerId = bob)
            )
        )
        environment.restore(suspended.state, environment.playerIds)

        val gymEnv = GameGymEnv(
            environment = environment,
            perspectivePlayerIndex = 0,
            defaultRevealAll = false
        )
        val decisionId = requireNotNull(suspended.state.pendingDecision).id

        val aliceView = gymEnv.observe().observation as TrainingObservation
        aliceView.perspectivePlayerId shouldBe alice
        aliceView.pendingDecision shouldBe null

        val beforeRejectedDecision = environment.state
        shouldThrow<IllegalArgumentException> {
            gymEnv.submitDecision(YesNoResponse(decisionId, false))
        }
        environment.state shouldBe beforeRejectedDecision

        val bobView = gymEnv.observeForPlayer(bob).observation as TrainingObservation
        bobView.pendingDecision.shouldNotBeNull()

        // Looking through another non-acting seat revokes the raw decision authority again.
        gymEnv.observeForPlayer(alice)
        shouldThrow<IllegalArgumentException> {
            gymEnv.submitDecision(YesNoResponse(decisionId, false))
        }
        environment.state shouldBe beforeRejectedDecision

        // Re-observing the deciding seat authorizes this exact pending decision.
        gymEnv.observeForPlayer(bob)
        gymEnv.submitDecision(YesNoResponse(decisionId, false))
        environment.state.pendingDecision shouldBe null
    }
})
