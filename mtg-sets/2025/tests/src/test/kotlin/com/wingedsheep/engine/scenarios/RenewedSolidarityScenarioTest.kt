package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe

class RenewedSolidarityScenarioTest : ScenarioTestBase() {
    init {
        test("copies this turn's Faerie tokens at your end step and gives the chosen type its power bonus") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Alela, Artful Provocateur")
                .withCardInHand(1, "Renewed Solidarity")
                .withLandsOnBattlefield(1, "Plains", 3)
                .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Renewed Solidarity").error shouldBe null
            game.resolveStack()
            val decision = game.state.pendingDecision as ChooseOptionDecision
            val option = decision.options.indexOf("Faerie")
            game.submitDecision(OptionChosenResponse(decision.id, option)).error shouldBe null
            game.resolveStack()
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.resolveStack()
            val faeries = game.state.getBattlefield().filter {
                game.state.getEntity(it)?.has<com.wingedsheep.engine.state.components.identity.TokenComponent>() == true &&
                    "Faerie" in game.state.projectedState.getSubtypes(it)
            }
            faeries.size shouldBe 2
            game.state.projectedState.getPower(faeries.first()) shouldBe 3
        }
    }
}
