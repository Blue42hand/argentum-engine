package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SatyrWayfinderScenarioTest : ScenarioTestBase() {
    init {
        test("Satyr Wayfinder puts a chosen revealed land in hand and the rest in the graveyard") {
            var builder = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Satyr Wayfinder")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(2) { builder = builder.withCardInLibrary(1, "Forest") }
            repeat(2) { builder = builder.withCardInLibrary(1, "Grizzly Bears") }
            val game = builder.build()

            game.castSpell(1, "Satyr Wayfinder").error shouldBe null
            game.resolveStack()

            val decision = game.getPendingDecision()
            decision.shouldBeInstanceOf<SelectCardsDecision>()
            decision.options.size shouldBe 2
            game.selectCards(listOf(decision.options.first()))
            game.resolveStack()

            game.findCardsInGraveyard(1, "Forest").size shouldBe 1
            game.findCardsInGraveyard(1, "Grizzly Bears").size shouldBe 2
        }

        test("Satyr Wayfinder may decline a revealed land") {
            var builder = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Satyr Wayfinder")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(4) { builder = builder.withCardInLibrary(1, "Forest") }
            val game = builder.build()

            game.castSpell(1, "Satyr Wayfinder").error shouldBe null
            game.resolveStack()
            game.skipSelection()
            game.resolveStack()

            game.findCardsInGraveyard(1, "Forest").size shouldBe 4
        }
    }
}
