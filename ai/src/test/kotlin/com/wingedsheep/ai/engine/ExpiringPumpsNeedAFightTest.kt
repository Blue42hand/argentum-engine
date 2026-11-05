package com.wingedsheep.ai.engine

import com.wingedsheep.ai.puzzles.advanceToDeclaration
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.ai.puzzles.advanceToStackResponse
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * An "until end of turn" pump is worth something only if a fight spends it before cleanup —
 * [com.wingedsheep.ai.engine.evaluation.CreatureValuation.expiringStatsNeedAFight] and
 * [AiProfile.activatedPumpsNeedAPlannedAttack], as [AiProfile.LIVE] ships them.
 *
 * The misplays are from production game `5ae3888c` (2026-10-14, Reality Fracture draft), each
 * rebuilt as a minimal position; the controls pin the uses a pump is actually for.
 */
class ExpiringPumpsNeedAFightTest : ScenarioTestBase() {

    private fun TestGame.liveChoice(): GameAction =
        AIPlayer.create(cardRegistry, player1Id, AiProfile.LIVE).chooseAction(state)

    private fun TestGame.activatedSource(action: GameAction): String? =
        (action as? ActivateAbility)?.let { state.getEntity(it.sourceId)?.get<CardComponent>()?.name }

    /**
     * Let the AI play out [step] — its choices and decisions, with the opponent passing — until the
     * game leaves the step or [limit] actions have been taken.
     */
    private fun TestGame.letAiPlayOut(step: Step, limit: Int = 30) {
        val ai = AIPlayer.create(cardRegistry, player1Id, AiProfile.LIVE)
        repeat(limit) {
            if (state.step != step) return
            val decision = state.pendingDecision
            if (decision != null) {
                check(decision.playerId == player1Id) { "Unexpected opponent decision ${decision::class.simpleName}" }
                execute(SubmitDecision(player1Id, ai.respondToDecision(state, decision))).error shouldBe null
                return@repeat
            }
            val priority = state.priorityPlayerId ?: return
            val action = if (priority == player1Id) ai.chooseAction(state) else PassPriority(priority)
            execute(action).error shouldBe null
        }
    }

    init {

        test("T11: no +1/-1 on Undulating Witness in our declare-attackers step with nothing attacking") {
            // The reach blocker keeps both fliers home; the Witness's pump then has no fight to
            // be spent in, and the four mana it took were the two creatures in hand.
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Undulating Witness")
                .withCardOnBattlefield(1, "Shatterwing Pegasus")
                .withLandsOnBattlefield(1, "Island", 3)
                .withLandsOnBattlefield(1, "Plains", 3)
                .withCardInHand(1, "Unflinching Hortimancer")
                .withCardInHand(1, "Proctor of Potential")
                .withCardOnBattlefield(2, "Titanbones, Towering Heart")
                .withLandsOnBattlefield(2, "Forest", 6)
                .build()
                .advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
                .also { it.declareAttackers(emptyMap()) }
                .advanceToPriority(1, Step.DECLARE_ATTACKERS)

            game.liveChoice().shouldBeInstanceOf<PassPriority>()

            // With the mana still there, the main phase after combat deploys both creatures.
            game.advanceToPriority(1, Step.POSTCOMBAT_MAIN)
            game.letAiPlayOut(Step.POSTCOMBAT_MAIN)
            game.isOnBattlefield("Unflinching Hortimancer") shouldBe true
            game.isOnBattlefield("Proctor of Potential") shouldBe true
        }

        test("T14: no team pump in the opponent's declare-attackers step before a block it will not make") {
            // Only the 2/1 is untapped; a 3/2 still cannot block a 10/9 profitably, and the
            // pump is gone before our creatures attack again.
            val game = scenario().withPlayers()
                .withActivePlayer(2)
                .withLifeTotal(1, 12)
                .withCardOnBattlefield(1, "Shatterwing Pegasus", tapped = true)
                .withCardOnBattlefield(1, "Proctor of Potential", tapped = true)
                .withCardOnBattlefield(1, "Unflinching Hortimancer")
                .withLandsOnBattlefield(1, "Plains", 5)
                .withCardOnBattlefield(2, "Craw Wurm")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()
                .advanceToDeclaration(2, Step.DECLARE_ATTACKERS)
                .also { it.declareAttackers(mapOf("Craw Wurm" to 1)) }
                .advanceToPriority(1, Step.DECLARE_ATTACKERS)

            game.activatedSource(game.liveChoice()) shouldBe null
        }

        test("T15: no precombat team pump at 2 life for an attack the plan does not make") {
            val game = scenario().withPlayers()
                .withLifeTotal(1, 2)
                .withLifeTotal(2, 19)
                .withCardOnBattlefield(1, "Shatterwing Pegasus")
                .withCardOnBattlefield(1, "Proctor of Potential")
                .withCardOnBattlefield(1, "Unflinching Hortimancer")
                .withLandsOnBattlefield(1, "Plains", 7)
                .withCardOnBattlefield(2, "Titanbones, Towering Heart", tapped = true)
                .withCardOnBattlefield(2, "Apex Witchstalker")
                .withCardOnBattlefield(2, "Craw Wurm")
                .build()
                .advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.activatedSource(game.liveChoice()) shouldBe null
        }

        // ── Controls: the fights a pump is for ──

        test("still pumps the unblocked attacker to exactly lethal after blocks") {
            val game = scenario().withPlayers()
                .withLifeTotal(2, 3)
                .withCardOnBattlefield(1, "Shatterwing Pegasus")
                .withLandsOnBattlefield(1, "Plains", 5)
                .withCardOnBattlefield(2, "Hill Giant")
                .build()
                .advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
                .also { it.declareAttackers(mapOf("Shatterwing Pegasus" to 2)) }
                .advanceToPriority(1, Step.DECLARE_BLOCKERS)

            game.activatedSource(game.liveChoice()) shouldBe "Shatterwing Pegasus"
        }

        test("still pumps a blocker so it wins the block") {
            // Pegasus 2/3 blocking a 3/3: +1/+1 makes it a 3/4 that kills the Giant and lives.
            val game = scenario().withPlayers()
                .withActivePlayer(2)
                .withCardOnBattlefield(1, "Shatterwing Pegasus")
                .withLandsOnBattlefield(1, "Plains", 5)
                .withCardOnBattlefield(2, "Hill Giant")
                .build()
                .advanceToDeclaration(2, Step.DECLARE_ATTACKERS)
                .also { it.declareAttackers(mapOf("Hill Giant" to 1)) }
                .advanceToDeclaration(1, Step.DECLARE_BLOCKERS)
                .also { it.declareBlockers(mapOf("Shatterwing Pegasus" to listOf("Hill Giant"))) }
                .advanceToPriority(1, Step.DECLARE_BLOCKERS)

            game.activatedSource(game.liveChoice()) shouldBe "Shatterwing Pegasus"
        }

        test("still pumps in response to burn that the pump survives") {
            // Shock at the 2/2; the team pump makes it a 3/3.
            val game = scenario().withPlayers()
                .withActivePlayer(2)
                .withCardOnBattlefield(1, "Shatterwing Pegasus")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Plains", 5)
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withCardInHand(2, "Shock")
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.advanceToPriority(2, Step.PRECOMBAT_MAIN)
            game.castSpell(2, "Shock", bears).error shouldBe null
            game.advanceToStackResponse(1)

            game.activatedSource(game.liveChoice()) shouldBe "Shatterwing Pegasus"
        }
    }
}
