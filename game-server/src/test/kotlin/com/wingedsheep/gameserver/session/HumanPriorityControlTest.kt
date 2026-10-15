package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.core.PayLifeOrEnterTappedSpellContinuation
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession

/** Session controls and races; the complete native-menu proof lives in ForcedPriorityPassTest. */
class HumanPriorityControlTest : ScenarioTestBase() {
    private fun board(withMana: Boolean = false): TestGame {
        val builder = scenario().withPlayers("A", "B").withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        if (withMana) builder.withLandsOnBattlefield(1, "Forest", 1)
        return builder.build()
    }

    private fun player(id: EntityId) = PlayerSession(
        mockk<WebSocketSession>(relaxed = true) { every { this@mockk.id } returns "ws-$id" }, id, "$id")

    private fun session(game: TestGame) = GameSession(cardRegistry = cardRegistry).also {
        it.injectStateForTesting(game.state, mapOf(game.player1Id to player(game.player1Id), game.player2Id to player(game.player2Id)))
    }

    private fun snapshot(session: GameSession, id: EntityId): ServerMessage.StateUpdate {
        session.clearLastSentState(id)
        return session.createStateUpdate(id, emptyList()).shouldBeInstanceOf<ServerMessage.StateUpdate>()
    }

    private fun arm(session: GameSession, id: EntityId, mode: String): Boolean {
        val view = snapshot(session, id)
        return session.setPriorityYield(id, mode, view.interactionEpoch, view.stateVersion)
    }

    private fun replace(session: GameSession, state: GameState) = session.injectStateForTesting(
        state, session.getPlayers().associateBy { it.playerId })

    init {
        test("humans default forced-only and native execution gives opponent their response window") {
            val game = board()
            val session = session(game)
            session.getPriorityMode(game.player1Id) shouldBe GameSession.PriorityMode.FORCED_ONLY
            val intent = session.getAutoPassIntent().shouldNotBeNull()
            session.executeAutoPassIfCurrent(intent).shouldBeInstanceOf<GameSession.ActionResult.Success>()
            session.getStateForTesting()!!.priorityPlayerId shouldBe game.player2Id
            session.getStateForTesting()!!.step shouldBe Step.PRECOMBAT_MAIN
            session.executeAutoPassIfCurrent(intent).shouldBeInstanceOf<GameSession.ActionResult.Failure>()
        }

        test("mana alternatives preserve the human window while explicit yield may spend it") {
            val game = board(withMana = true)
            val session = session(game)
            session.getAutoPassIntent() shouldBe null
            arm(session, game.player1Id, "endOfTurn") shouldBe true
            session.executeAutoPassIfCurrent(session.getAutoPassIntent().shouldNotBeNull())
                .shouldBeInstanceOf<GameSession.ActionResult.Success>()
            session.getStateForTesting()!!.priorityPlayerId shouldBe game.player2Id
        }

        test("full control stop overrides and cancellation invalidate acquired authorization") {
            val game = board()
            val session = session(game)
            val intent = session.getAutoPassIntent().shouldNotBeNull()
            session.setFullControl(game.player1Id, true)
            session.executeAutoPassIfCurrent(intent).shouldBeInstanceOf<GameSession.ActionResult.Failure>()
            session.getAutoPassIntent() shouldBe null
            arm(session, game.player1Id, "nextTurn") shouldBe false
            session.setFullControl(game.player1Id, false)
            arm(session, game.player1Id, "nextTurn") shouldBe true
            val yieldIntent = session.getAutoPassIntent().shouldNotBeNull()
            val view = snapshot(session, game.player1Id)
            session.setPriorityYield(game.player1Id, null, view.interactionEpoch, view.stateVersion) shouldBe true
            session.executeAutoPassIfCurrent(yieldIntent).shouldBeInstanceOf<GameSession.ActionResult.Failure>()
            session.getAutoPassIntent() shouldBe null
            arm(session, game.player1Id, "nextTurn") shouldBe true
            session.setStopOverrides(game.player1Id, setOf(Step.PRECOMBAT_MAIN), emptySet())
            session.getPriorityYield(game.player1Id) shouldBe null
            session.getAutoPassIntent() shouldBe null
        }

        test("arming rejects stale epoch stale snapshot and newer native state") {
            val game = board()
            val session = session(game)
            val view = snapshot(session, game.player1Id)
            session.setPriorityYield(game.player1Id, "nextTurn", "stale", view.stateVersion) shouldBe false
            session.setPriorityYield(game.player1Id, "nextTurn", view.interactionEpoch, view.stateVersion - 1) shouldBe false
            session.executeAction(game.player1Id, PassPriority(game.player1Id))
            session.setPriorityYield(game.player1Id, "nextTurn", view.interactionEpoch, view.stateVersion) shouldBe false
        }

        test("native turn serial ends current-turn yield even for consecutive extra turns") {
            val game = board()
            val session = session(game)
            arm(session, game.player1Id, "endOfTurn") shouldBe true
            replace(session, game.state.copy(turnNumber = game.state.turnNumber + 1))
            session.getPriorityYield(game.player1Id) shouldBe null
        }

        test("next-turn yield waits through opponents extra turns and skipped own turns then expires before own upkeep") {
            val game = board()
            val session = session(game)
            arm(session, game.player1Id, "nextTurn") shouldBe true
            for (turn in 1..3) {
                replace(session, game.state.copy(turnNumber = game.state.turnNumber + turn,
                    activePlayerId = game.player2Id, priorityPlayerId = game.player2Id))
                session.getPriorityYield(game.player1Id) shouldBe "nextTurn"
            }
            replace(session, game.state.copy(turnNumber = game.state.turnNumber + 4,
                phase = Phase.BEGINNING, step = Step.UPKEEP))
            session.getPriorityYield(game.player1Id) shouldBe null
            session.getAutoPassIntent() shouldBe null
        }

        test("next-turn yield expires before a consecutive own extra turn") {
            val game = board()
            val session = session(game)
            arm(session, game.player1Id, "nextTurn") shouldBe true
            replace(session, game.state.copy(turnNumber = game.state.turnNumber + 1, phase = Phase.BEGINNING, step = Step.UPKEEP))
            session.getPriorityYield(game.player1Id) shouldBe null
            session.getAutoPassIntent() shouldBe null
        }

        test("required decisions and combat declarations are never spent by voluntary yields") {
            val game = board()
            val session = session(game)
            arm(session, game.player1Id, "nextTurn") shouldBe true
            replace(session, game.state.suspendForDecision(
                { id -> YesNoDecision(id, game.player1Id, "Choose", DecisionContext()) },
                PayLifeOrEnterTappedSpellContinuation(game.player1Id, game.player1Id, game.player1Id, 2)).state)
            session.getPriorityYield(game.player1Id) shouldBe null
            session.getAutoPassIntent() shouldBe null
            replace(session, game.state)
            arm(session, game.player1Id, "nextTurn") shouldBe true
            replace(session, game.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_ATTACKERS))
            session.getAutoPassIntent() shouldBe null
            session.getPriorityYield(game.player1Id) shouldBe null
        }

        test("reset reconnect and terminal state clear voluntary intent") {
            val game = board()
            val session = session(game)
            arm(session, game.player1Id, "nextTurn") shouldBe true
            val origin = snapshot(session, game.player1Id)
            session.resetStateForDevScenario(game.state)
            session.getPriorityYield(game.player1Id) shouldBe null
            session.setPriorityYield(game.player1Id, "nextTurn", origin.interactionEpoch, origin.stateVersion) shouldBe false
            arm(session, game.player1Id, "nextTurn") shouldBe true
            session.associatePlayer(player(game.player1Id))
            session.getPriorityYield(game.player1Id) shouldBe null
            arm(session, game.player1Id, "nextTurn") shouldBe true
            replace(session, game.state.copy(gameOver = true))
            session.getPriorityYield(game.player1Id) shouldBe null
            session.getAutoPassIntent() shouldBe null
        }

        test("AI defaults remain legacy Auto and voluntary state is echoed in full and delta updates") {
            val game = board()
            val session = session(game)
            session.setPlayerPersistenceInfo(game.player2Id, "AI", "test-token", isAi = true)
            session.getPriorityMode(game.player2Id) shouldBe GameSession.PriorityMode.AUTO
            arm(session, game.player1Id, "nextTurn") shouldBe true
            snapshot(session, game.player1Id).priorityYield shouldBe "nextTurn"
            session.createStateUpdate(game.player1Id, emptyList())
                .shouldBeInstanceOf<ServerMessage.StateDeltaUpdate>().priorityYield shouldBe "nextTurn"
        }

        test("undo invalidates the voluntary intent and old timeline authorization") {
            val game = board()
            val session = session(game)
            session.executeAction(game.player1Id, PassPriority(game.player1Id))
                .shouldBeInstanceOf<GameSession.ActionResult.Success>()
            arm(session, game.player1Id, "nextTurn") shouldBe true
            val old = snapshot(session, game.player1Id)
            session.executeUndo(game.player1Id).shouldBeInstanceOf<GameSession.ActionResult.Success>()
            session.getPriorityYield(game.player1Id) shouldBe null
            session.setPriorityYield(game.player1Id, "nextTurn", old.interactionEpoch, old.stateVersion) shouldBe false
        }

        test("non-defender certified pass is never rewritten into an empty blocker declaration") {
            val game = board()
            val session = session(game)
            replace(session, game.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_BLOCKERS,
                priorityPlayerId = game.player2Id))
            val intent = session.getAutoPassIntent().shouldNotBeNull()
            session.executeAutoPassIfCurrent(intent).shouldBeInstanceOf<GameSession.ActionResult.Success>()
            session.getStateForTesting()!!.getEntity(game.player2Id)!!
                .get<com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent>() shouldBe null
            session.getStateForTesting()!!.priorityPlayerId shouldBe game.player1Id
        }

        test("native skipped-turn transition expires next-turn yield at actual own upkeep") {
            val game = scenario().withPlayers("A", "B").withActivePlayer(1)
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Forest")
                .inPhase(Phase.ENDING, Step.END).build()
            val session = session(game)
            replace(session, game.state.updateEntity(game.player2Id) {
                it.with(com.wingedsheep.engine.state.components.player.SkipNextTurnComponent())
            })
            arm(session, game.player1Id, "nextTurn") shouldBe true
            session.executeAutoPassIfCurrent(session.getAutoPassIntent().shouldNotBeNull())
                .shouldBeInstanceOf<GameSession.ActionResult.Success>()
            session.executeAutoPassIfCurrent(session.getAutoPassIntent().shouldNotBeNull())
                .shouldBeInstanceOf<GameSession.ActionResult.Success>()
            session.getStateForTesting()!!.activePlayerId shouldBe game.player1Id
            session.getStateForTesting()!!.turnNumber shouldBe game.state.turnNumber + 1
            session.getStateForTesting()!!.step shouldBe Step.UPKEEP
            session.getPriorityYield(game.player1Id) shouldBe null
            session.getAutoPassIntent() shouldBe null
        }

        test("another seat's explicit full control is not consumed by a voluntary yield") {
            val game = board()
            val session = session(game)
            session.setFullControl(game.player2Id, true)
            arm(session, game.player1Id, "nextTurn") shouldBe true
            session.executeAutoPassIfCurrent(session.getAutoPassIntent().shouldNotBeNull())
                .shouldBeInstanceOf<GameSession.ActionResult.Success>()
            session.getAutoPassIntent() shouldBe null
            session.getStateForTesting()!!.priorityPlayerId shouldBe game.player2Id
        }
    }
}
