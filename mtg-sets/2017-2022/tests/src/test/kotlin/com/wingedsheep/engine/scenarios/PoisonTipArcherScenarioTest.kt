package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
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
