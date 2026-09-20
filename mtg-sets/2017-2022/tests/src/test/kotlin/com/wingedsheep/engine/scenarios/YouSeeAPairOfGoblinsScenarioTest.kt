package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class YouSeeAPairOfGoblinsScenarioTest : ScenarioTestBase() {
    init {
        test("Befriend Them creates two Goblins") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "You See a Pair of Goblins")
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "You See a Pair of Goblins").error shouldBe null
            game.resolveStack()
            val choice = game.getPendingDecision() as ChooseOptionDecision
            game.submitDecision(OptionChosenResponse(choice.id, 1))
            game.resolveStack()

            game.findPermanents("Goblin Token").size shouldBe 2
        }
    }
}
