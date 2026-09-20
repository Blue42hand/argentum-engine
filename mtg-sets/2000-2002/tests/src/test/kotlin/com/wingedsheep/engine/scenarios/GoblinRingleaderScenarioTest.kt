package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class GoblinRingleaderScenarioTest : ScenarioTestBase() {
    init {
        test("ETB puts every Goblin in the top four into hand and bottoms the rest") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Goblin Ringleader")
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInLibrary(1, "Goblin Guide")
                .withCardInLibrary(1, "Krenko, Mob Boss")
                .withCardInLibrary(1, "Goblin Cratermaker")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Goblin Ringleader").error shouldBe null
            game.resolveStack()

            withClue("all three Goblin cards move to hand") {
                game.isInHand(1, "Goblin Guide") shouldBe true
                game.isInHand(1, "Krenko, Mob Boss") shouldBe true
                game.isInHand(1, "Goblin Cratermaker") shouldBe true
            }
            withClue("the non-Goblin is the only card left in the library") {
                game.librarySize(1) shouldBe 1
                game.isInHand(1, "Forest") shouldBe false
            }
        }
    }
}
