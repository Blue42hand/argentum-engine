package com.wingedsheep.gym

import com.wingedsheep.engine.core.ChooseDoorContinuation
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.NumberChosenResponse
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs

class StaleDecisionPreconditionTest : FunSpec({
    fun registry(): CardRegistry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }

    test("stale structured decision is rejected before authoritative decision validation") {
        val environment = GameEnvironment.create(registry())
        environment.reset(
            GameConfig(
                players = listOf(
                    PlayerConfig("Alice", Deck.of("Mountain" to 20)),
                    PlayerConfig("Bob", Deck.of("Mountain" to 20))
                ),
                skipMulligans = true,
                startingPlayerIndex = 0,
                seed = 20260922L
            )
        )

        val alice = environment.playerIds[0]
        val suspended = environment.state.suspendForDecision(
            question = { id ->
                YesNoDecision(
                    id = id,
                    playerId = alice,
                    prompt = "Choose yes or no",
                    context = DecisionContext(sourceId = EntityId("decision-source"))
                )
            },
            answer = ChooseDoorContinuation(
                controllerId = alice,
                roomId = EntityId("unused-room"),
                candidateFaceIds = emptyList(),
                lock = true
            )
        )
        val decision = suspended.pendingDecision.shouldNotBeNull()
        environment.restore(suspended.state, environment.playerIds)

        val gymEnv = GameGymEnv(
            environment = environment,
            perspectivePlayerIndex = 0,
            defaultRevealAll = false
        )
        val currentDigest = gymEnv.observe().observation.stateDigest
        val preSubmissionState = environment.state
        environment.lastRejection shouldBe null

        val error = shouldThrow<IllegalStateException> {
            gymEnv.submitDecision(
                NumberChosenResponse(
                    decisionId = decision.id,
                    number = 1
                ),
                expectedStateDigest = "stale-$currentDigest"
            )
        }

        error.message.shouldNotBeNull() shouldContain "Stale decision"
        environment.lastRejection shouldBe null
        environment.state shouldBeSameInstanceAs preSubmissionState
        environment.state.pendingDecision shouldBe decision
    }
})
