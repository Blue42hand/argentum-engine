package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class PoisonTipArcherScenarioTest : ScenarioTestBase() {
    init {
        test("another creature dying makes the opponent lose one life") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Poison-Tip Archer")
                .withCardOnBattlefield(2, "Llanowar Elves")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLifeTotal(1, 20)
                .withLifeTotal(2, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Lightning Bolt", game.findPermanent("Llanowar Elves")!!).error shouldBe null
            game.resolveStack()

            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 19
        }

        test("Poison-Tip Archer does not trigger for its own death alone") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Poison-Tip Archer")
                .withCardInHand(1, "Hero's Downfall")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withLifeTotal(2, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Hero's Downfall", game.findPermanent("Poison-Tip Archer")!!).error shouldBe null
            game.resolveStack()

            game.getLifeTotal(2) shouldBe 20
        }
    }
}

/**
 * Poison-Tip Archer (M19 #220) — Reach, deathtouch. "Whenever another creature dies, each opponent
 * loses 1 life." Fires for any player's creature, but not for the Archer itself.
 */
class PoisonTipArcherUpstreamScenarioTest : ScenarioTestBase() {

    init {
        context("Poison-Tip Archer") {

            test("an opponent's creature dying makes each opponent lose 1 life") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Poison-Tip Archer")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInHand(1, "Murder")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val oppBefore = game.getLifeTotal(2)
                val selfBefore = game.getLifeTotal(1)
                game.castSpell(1, "Murder", game.findPermanent("Grizzly Bears")!!).error shouldBe null
                game.resolveStack()

                withClue("Grizzly Bears died") { game.isInGraveyard(2, "Grizzly Bears") shouldBe true }
                withClue("the opponent loses 1 life") { game.getLifeTotal(2) shouldBe oppBefore - 1 }
                withClue("you lose nothing") { game.getLifeTotal(1) shouldBe selfBefore }
            }

            test("the Archer dying itself does not trigger") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Poison-Tip Archer")
                    .withCardInHand(1, "Murder")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val oppBefore = game.getLifeTotal(2)
                game.castSpell(1, "Murder", game.findPermanent("Poison-Tip Archer")!!).error shouldBe null
                game.resolveStack()

                withClue("the Archer died") { game.isInGraveyard(1, "Poison-Tip Archer") shouldBe true }
                game.getLifeTotal(2) shouldBe oppBefore
            }
        }
    }
}
