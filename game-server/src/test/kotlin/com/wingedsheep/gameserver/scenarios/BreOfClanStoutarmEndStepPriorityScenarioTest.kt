package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.scripting.effects.GainLifeEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession

/**
 * Server-level regression for Bre of Clan Stoutarm's end-step "you may cast" decision.
 *
 * The card's engine behaviour is covered by `BreOfClanStoutarmTest` in rules-engine, but those
 * tests answer the yes/no directly. Players reported that in real games they "can't cast" the
 * exiled card — the suspicion being the Arena-style auto-pass logic speeds through the player's
 * own end step (`shouldAutoPassOnMyTurn(Step.END) == true`) and never surfaces the prompt.
 *
 * This drives the full priority loop the way `GamePlayHandler` does: the active player ends their
 * turn, the trigger goes on the stack at the beginning of the end step, the opponent passes, and
 * the trigger resolves into the inline "you may cast" choice. The auto-pass loop MUST stop and
 * present that decision (never auto-resolve past it), and answering "yes" must actually cast the
 * card.
 */
class BreOfClanStoutarmEndStepPriorityScenarioTest : ScenarioTestBase() {

    init {
        // Gains the controller 3 life so Bre's "if you gained life this turn" trigger condition holds.
        cardRegistry.register(
            CardDefinition.instant(
                name = "Healing Salve",
                manaCost = ManaCost.parse("{W}"),
                oracleText = "You gain 3 life.",
                script = CardScript.spell(effect = GainLifeEffect(3, EffectTarget.Controller))
            )
        )
        // A cheap nonland (MV 2 ≤ 3 life gained) the player may cast for free.
        cardRegistry.register(
            CardDefinition.creature(
                name = "Cheap Ogre",
                manaCost = ManaCost.parse("{1}{R}"),
                subtypes = setOf(Subtype("Ogre")),
                power = 2,
                toughness = 2
            )
        )

        test("the end-step auto-pass loop stops on the 'you may cast' prompt and the cast resolves") {
            // Start at postcombat main (combat already behind us) so the only thing left in the turn
            // is passing into the end step — keeps this focused on the end-step trigger flow.
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Bre of Clan Stoutarm")
                .withCardInHand(1, "Healing Salve")
                .withCardInLibrary(1, "Forest")        // top — exiled along the way
                .withCardInLibrary(1, "Cheap Ogre")    // the nonland the walk stops on
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                .build()

            // Gain 3 life this turn so the end-step ability will trigger.
            game.castSpell(1, "Healing Salve").error shouldBe null
            game.resolveStack()

            val p1 = game.player1Id
            val p2 = game.player2Id
            val session = newSession(game)

            // Player 1 ends their turn. From their own main phase the auto-pass loop won't move on
            // its own, so we submit the manual pass a real player would and drive the loop — exactly
            // the GamePlayHandler flow. This advances into the end step, where Bre's trigger fires.
            session.executeAction(p1, PassPriority(p1)) // leave postcombat main → into the end step
            driveAutoPass(session)

            // We should now be parked on player 2's priority with Bre's trigger on the stack
            // (an opponent's triggered ability — Arena-style auto-pass correctly stops here).
            val beforeOpponentPass = session.getStateForTesting()!!
            withClue("Bre's end-step trigger should be on the stack waiting for the opponent to pass") {
                beforeOpponentPass.stack.isNotEmpty() shouldBe true
                beforeOpponentPass.priorityPlayerId shouldBe p2
            }

            // Opponent passes; the trigger resolves into the inline "you may cast" decision.
            session.executeAction(p2, PassPriority(p2))
            driveAutoPass(session)

            val atDecision = session.getStateForTesting()!!
            val decision = atDecision.pendingDecision
            withClue("The auto-pass loop must STOP and present the 'you may cast' decision, not skip it") {
                decision.shouldBeInstanceOf<YesNoDecision>()
            }
            decision as YesNoDecision
            withClue("The decision must belong to Bre's controller") {
                decision.playerId shouldBe p1
            }
            withClue("It must be the free-cast prompt") {
                decision.prompt.lowercase().contains("cast") shouldBe true
            }

            // Accept the free cast. Cheap Ogre has no targets, so it goes straight on the stack.
            session.executeAction(p1, SubmitDecision(p1, YesNoResponse(decision.id, true)))
            driveAutoPass(session)

            val finalState = session.getStateForTesting()!!
            val ogreOnBattlefield = finalState.getBattlefield().any { id ->
                finalState.getEntity(id)
                    ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                    ?.name == "Cheap Ogre"
            }
            withClue("Accepting the prompt must actually cast Cheap Ogre onto the battlefield") {
                ogreOnBattlefield shouldBe true
            }
        }

        test("full turn: ending the turn from the main phase still surfaces the cast prompt") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Bre of Clan Stoutarm")
                .withCardInHand(1, "Healing Salve")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Cheap Ogre")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Healing Salve").error shouldBe null
            game.resolveStack()

            val p1 = game.player1Id
            val p2 = game.player2Id
            val session = newSession(game)

            // End the turn the way a real player does: pass out of precombat main, declare no
            // attackers when the loop stops at combat, pass out of postcombat main, then let the
            // opponent pass on the end-step trigger.
            session.executeAction(p1, PassPriority(p1))           // leave precombat main
            driveAutoPass(session)
            withClue("Auto-pass should park at the active player's declare-attackers (Bre can attack)") {
                val s = session.getStateForTesting()!!
                s.step shouldBe Step.DECLARE_ATTACKERS
                s.priorityPlayerId shouldBe p1
            }
            session.executeAction(p1, DeclareAttackers(p1, emptyMap())) // skip combat
            driveAutoPass(session)
            withClue("Auto-pass should park at the active player's postcombat main") {
                val s = session.getStateForTesting()!!
                s.step shouldBe Step.POSTCOMBAT_MAIN
                s.priorityPlayerId shouldBe p1
            }
            session.executeAction(p1, PassPriority(p1))           // leave postcombat main → end step
            driveAutoPass(session)

            // Opponent passes on Bre's end-step trigger; it resolves into the cast prompt.
            session.executeAction(p2, PassPriority(p2))
            driveAutoPass(session)

            val decision = session.getStateForTesting()!!.pendingDecision
            withClue("The cast prompt must be presented even when reached via a normal end-of-turn pass") {
                decision.shouldBeInstanceOf<YesNoDecision>()
            }
        }
    }

    /** Drive the auto-pass loop the way GamePlayHandler.processAutoPassLoop does. */
    private fun driveAutoPass(session: GameSession) {
        var loopCount = 0
        val maxLoops = 100
        while (loopCount < maxLoops) {
            val autoPassPlayer = session.getAutoPassPlayer() ?: break
            session.executeAutoPass(autoPassPlayer)
            loopCount++
        }
    }

    private fun newSession(game: TestGame): GameSession {
        val session = GameSession(cardRegistry = cardRegistry)
        val ws1 = mockk<WebSocketSession>(relaxed = true) { every { id } returns "ws1" }
        val ws2 = mockk<WebSocketSession>(relaxed = true) { every { id } returns "ws2" }
        session.injectStateForTesting(
            game.state,
            mapOf(
                game.player1Id to PlayerSession(ws1, game.player1Id, "Player1"),
                game.player2Id to PlayerSession(ws2, game.player2Id, "Player2")
            )
        )
        // This regression exercises the explicit legacy Auto heuristics. Human sessions now
        // default to Forced Only, which correctly preserves the fixture's mana abilities.
        session.setPriorityMode(game.player1Id, GameSession.PriorityMode.AUTO)
        session.setPriorityMode(game.player2Id, GameSession.PriorityMode.AUTO)
        return session
    }
}
