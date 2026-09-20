package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class MoldervineReclamationScenarioTest : ScenarioTestBase() {
    init {
        test("a creature you control dying gains one life and draws one card") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Moldervine Reclamation")
                .withCardOnBattlefield(1, "Llanowar Elves")
                .withCardInHand(1, "Lightning Bolt")
                .withCardInLibrary(1, "Forest")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            game.castSpell(1, "Lightning Bolt", game.findPermanent("Llanowar Elves")!!).error shouldBe null
            game.resolveStack()

            game.getLifeTotal(1) shouldBe 21
            game.handSize(1) shouldBe handBefore
        }

        test("an opponent's creature dying does not trigger Moldervine Reclamation") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Moldervine Reclamation")
                .withCardOnBattlefield(2, "Llanowar Elves")
                .withCardInHand(1, "Lightning Bolt")
                .withCardInLibrary(1, "Forest")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            game.castSpell(1, "Lightning Bolt", game.findPermanent("Llanowar Elves")!!).error shouldBe null
            game.resolveStack()

            game.getLifeTotal(1) shouldBe 20
            game.handSize(1) shouldBe handBefore - 1
        }
    }
}
