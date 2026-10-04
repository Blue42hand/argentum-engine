package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Buried Alive: search for zero to three creature cards, put the chosen cards in your graveyard. */
class BuriedAliveScenarioTest : ScenarioTestBase() {
    init {
        test("finds only creatures and allows fewer than three") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Buried Alive")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Hill Giant")
                .withCardInLibrary(1, "Centaur Courser")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findCardsInLibrary(1, "Grizzly Bears").first()
            val giant = game.findCardsInLibrary(1, "Hill Giant").first()
            game.castSpell(1, "Buried Alive").error shouldBe null
            game.resolveStack()
            val decision = game.getPendingDecision() as SelectCardsDecision
            decision.options.size shouldBe 3
            decision.minSelections shouldBe 0
            decision.maxSelections shouldBe 3

            game.selectCards(listOf(bears, giant))
            game.resolveStack()

            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, "Hill Giant") shouldBe true
            game.findCardsInLibrary(1, "Centaur Courser").size shouldBe 1
            game.findCardsInLibrary(1, "Forest").size shouldBe 1
        }

        test("may find no creatures and still shuffles") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Buried Alive")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Buried Alive").error shouldBe null
            game.resolveStack()
            val selection = game.selectCards(emptyList())
            val followup = game.resolveStack()

            game.findCardsInLibrary(1, "Grizzly Bears").size shouldBe 1
            game.findCardsInLibrary(1, "Forest").size shouldBe 1
            game.isInGraveyard(1, "Grizzly Bears") shouldBe false
            (selection.events + followup.flatMap { it.events })
                .filterIsInstance<LibraryShuffledEvent>().size shouldBe 1
        }
    }
}
