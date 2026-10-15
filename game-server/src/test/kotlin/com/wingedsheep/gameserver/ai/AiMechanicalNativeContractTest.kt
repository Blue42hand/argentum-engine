package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.combat.RandomizedBlockerPilesExecutor
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.LiveActionSubmission
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.effects.RandomizedBlockerPilesEffect
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.socket.WebSocketSession

/** Proves the optimization consumes native enumeration; no blocker legality is inferred here. */
class AiMechanicalNativeContractTest : ScenarioTestBase() {
    init {
        test("native empty-block certification executes normally and leaves the response window open") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Air Elemental")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withActivePlayer(1).withPriorityPlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            game.declareAttackers(mapOf("Air Elemental" to 2)).error.shouldBeNull()
            game.passPriority()
            game.passPriority()
            game.state.step shouldBe Step.DECLARE_BLOCKERS
            val ai = game.player2Id
            val controller = mockk<AiPlayerController>()
            val session = GameSession(cardRegistry = cardRegistry)
            val players = listOf(game.player1Id, ai).associateWith { player ->
                PlayerSession(mockk<WebSocketSession>(relaxed = true) { every { id } returns player.value }, player, player.value)
            }
            session.injectStateForTesting(game.state, players)
            session.setFullControl(game.player1Id, true)
            session.setFullControl(ai, true)
            val update = session.createStateUpdate(ai, emptyList(), useEngineDecisionIds = true)
                .shouldBeInstanceOf<ServerMessage.StateUpdate>()
            update.legalActions.single().validBlockers shouldBe emptyList()
            update.legalActions.single().validBlockTargets shouldBe emptyMap()
            var applied = 0
            val socket = AiWebSocketSession(ai, controller, thinkingDelayMs = 500,
                onActionReady = { seat, action, epoch ->
                    session.executeLiveAction(seat, LiveActionSubmission(action, "obsolete", aiReply = true)).shouldBeNull()
                    session.executeLiveAction(seat, LiveActionSubmission(action, epoch!!, aiReply = true))
                        .shouldBeInstanceOf<GameSession.ActionResult.Success>()
                    applied++
                }, onMulliganKeep = {}, onMulliganTake = {}, onBottomCards = { _, _ -> },
                allowActionsOnlyFallback = false)
            try {
                socket.handleServerMessage(update)
                applied shouldBe 1
                verify(exactly = 0) { controller.chooseAction(any(), any(), any(), any()) }
                val after = session.getStateForTesting().shouldNotBeNull()
                after.getEntity(ai)!!.has<BlockersDeclaredThisCombatComponent>() shouldBe true
                after.step shouldBe Step.DECLARE_BLOCKERS
                session.getLegalActions(ai).any { it.actionType == "PassPriority" } shouldBe true
            } finally { socket.close() }
        }

        test("native randomized blocker piles with empty blockers still reach the controller") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Llanowar Elves")
                .withActivePlayer(1).withPriorityPlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS).build()
            game.declareAttackers(mapOf("Grizzly Bears" to 2)).error.shouldBeNull()
            game.state = RandomizedBlockerPilesExecutor().execute(game.state, RandomizedBlockerPilesEffect(),
                EffectContext(sourceId = null, controllerId = game.player1Id)).state
            game.passPriority()
            game.passPriority()
            val ai = game.player2Id
            val session = GameSession(cardRegistry = cardRegistry)
            session.injectStateForTesting(game.state, listOf(game.player1Id, ai).associateWith { player ->
                PlayerSession(mockk<WebSocketSession>(relaxed = true) { every { id } returns player.value }, player, player.value)
            })
            val update = session.createStateUpdate(ai, emptyList(), useEngineDecisionIds = true)
                .shouldBeInstanceOf<ServerMessage.StateUpdate>()
            update.legalActions.single().validBlockers shouldBe emptyList()
            update.legalActions.single().validBlockTargets.shouldBeNull()
            val controller = mockk<AiPlayerController>()
            every { controller.chooseAction(any(), any(), any(), any()) } returns
                ActionResponse.SubmitAction(DeclareBlockers(ai, emptyMap()))
            val socket = AiWebSocketSession(ai, controller, thinkingDelayMs = 0,
                onActionReady = { _, _, _ -> }, onMulliganKeep = {}, onMulliganTake = {},
                onBottomCards = { _, _ -> }, allowActionsOnlyFallback = false)
            try {
                socket.handleServerMessage(update)
                verify(exactly = 1) { controller.chooseAction(any(), any(), any(), any()) }
            } finally { socket.close() }
        }
    }
}
