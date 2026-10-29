package com.wingedsheep.ai.engine

import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * [AiProfile.precombatPumpsForThePlan], as [AiProfile.LIVE] ships it: a pump that changes who
 * attacks is cast before the attack is chosen, and every other precombat pump still waits.
 */
class PrecombatPumpsForThePlanTest : ScenarioTestBase() {

    private val before = AiProfile.LIVE.copy(id = "live-without-planpumps", precombatPumpsForThePlan = false)

    private fun TestGame.choice(profile: AiProfile = AiProfile.LIVE): GameAction =
        AIPlayer.create(cardRegistry, player1Id, profile).chooseAction(state)

    private fun TestGame.castName(action: GameAction): String? =
        (action as? CastSpell)?.let { state.getEntity(it.cardId)?.get<CardComponent>()?.name }

    init {

        test("a trick that turns a stay-home 2/2 into an attacker is cast before combat") {
            // A 2/2 does not attack into an untapped 3/3; a 5/5 does, and at 5 life the 3/3 has to
            // throw itself in front of it. Without the flag the trick is held out of the main phase
            // for a beginning-of-combat window the AI never stops in, and nothing attacks.
            val game = scenario().withPlayers()
                .withTurnNumber(5)
                .withLifeTotal(2, 5)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Giant Growth")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()
                .advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.castName(game.choice(before)) shouldBe null
            game.castName(game.choice()) shouldBe "Giant Growth"
        }

        test("a trick whose attack is worth less than the card is still held") {
            // The same board at 20 life: five damage does not pay for Giant Growth, and the planner
            // says so.
            val game = scenario().withPlayers()
                .withTurnNumber(5)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Giant Growth")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()
                .advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.castName(game.choice()) shouldBe null
        }

        test("a trick on a creature that attacks anyway still waits for blocks") {
            // Nothing can block the Bears, so the plan sends them with or without the pump.
            val game = scenario().withPlayers()
                .withTurnNumber(5)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Giant Growth")
                .withLandsOnBattlefield(2, "Mountain", 3)
                .build()
                .advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.castName(game.choice()) shouldBe null
        }

        test("no precombat pump for a creature that cannot attack this turn") {
            val game = scenario().withPlayers()
                .withTurnNumber(5)
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = true)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Giant Growth")
                .withCardOnBattlefield(2, "Hill Giant")
                .build()
                .advanceToPriority(1, Step.PRECOMBAT_MAIN)

            game.castName(game.choice()) shouldBe null
        }
    }
}
