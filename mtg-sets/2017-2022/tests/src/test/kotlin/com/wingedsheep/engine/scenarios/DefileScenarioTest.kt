package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class DefileScenarioTest : ScenarioTestBase() {
    init {
        test("only your Swamps reduce a targeted creature's stats") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Defile")
                .withCardOnBattlefield(2, "Hill Giant")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(2, "Swamp", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val giant = game.findPermanent("Hill Giant")!!
            game.castSpell(1, "Defile", giant).error shouldBe null
            game.resolveStack()

            game.state.projectedState.getPower(giant) shouldBe 1
            game.state.projectedState.getToughness(giant) shouldBe 1
        }

        test("a nonbasic land with the Swamp subtype counts") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Defile")
                .withCardOnBattlefield(2, "Hill Giant")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Watery Grave")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val giant = game.findPermanent("Hill Giant")!!
            game.castSpell(1, "Defile", giant).error shouldBe null
            game.resolveStack()

            game.state.projectedState.getPower(giant) shouldBe 1
            game.state.projectedState.getToughness(giant) shouldBe 1
        }
    }
}
