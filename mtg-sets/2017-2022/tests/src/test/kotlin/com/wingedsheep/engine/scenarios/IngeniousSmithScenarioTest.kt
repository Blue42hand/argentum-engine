package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IngeniousSmithScenarioTest : ScenarioTestBase() {
    private fun selectionGame() = scenario()
        .withPlayers("Smith", "Opponent")
        .withCardInHand(1, "Ingenious Smith")
        .withLandsOnBattlefield(1, "Plains", 2)
        .withCardInLibrary(1, "Sol Ring")
        .withCardInLibrary(1, "Forest")
        .withCardInLibrary(1, "Mountain")
        .withCardInLibrary(1, "Swamp")
        .withCardInLibrary(1, "Ornithopter")
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    init {
        test("entering offers only an artifact and puts the other three cards on the bottom") {
            val game = selectionGame()
            val ring = game.findCardsInLibrary(1, "Sol Ring").single()
            val untouched = game.findCardsInLibrary(1, "Ornithopter").single()
            val original = game.state.getLibrary(game.player1Id).toSet()
            game.castSpell(1, "Ingenious Smith").error shouldBe null
            game.resolveStack()
            val choice = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            choice.playerId shouldBe game.player1Id
            choice.options shouldBe listOf(ring)
            game.selectCards(listOf(ring)).error shouldBe null
            game.resolveStack()
            game.findCardsInHand(1, "Sol Ring") shouldBe listOf(ring)
            game.state.getLibrary(game.player1Id).toSet() shouldBe original - ring
            game.state.getLibrary(game.player1Id).first() shouldBe untouched
            game.getPendingDecision() shouldBe null
        }

        test("the optional reveal can be declined even when an artifact is available") {
            val game = selectionGame()
            val original = game.state.getLibrary(game.player1Id).toSet()
            val untouched = game.findCardsInLibrary(1, "Ornithopter").single()
            game.castSpell(1, "Ingenious Smith").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(emptyList()).error shouldBe null
            game.resolveStack()
            game.findCardsInHand(1, "Sol Ring").size shouldBe 0
            game.state.getLibrary(game.player1Id).toSet() shouldBe original
            game.state.getLibrary(game.player1Id).first() shouldBe untouched
            game.getPendingDecision() shouldBe null
        }

        test("a batch of artifacts gives one counter and later artifacts are capped until the next turn") {
            val game = scenario()
                .withPlayers("Smith", "Opponent")
                .withCardOnBattlefield(1, "Ingenious Smith")
                .withCardInHand(1, "Brass's Bounty")
                .withCardInHand(1, "Memnite")
                .withCardInHand(1, "Ornithopter")
                .withLandsOnBattlefield(1, "Mountain", 7)
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Swamp")
                .withCardInLibrary(2, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val smith = game.findPermanent("Ingenious Smith")!!
            game.castSpell(1, "Brass's Bounty").error shouldBe null
            game.resolveStack()
            game.findAllPermanents("Treasure").size shouldBe 7
            game.state.projectedState.getPower(smith) shouldBe 2
            game.castSpell(1, "Memnite").error shouldBe null
            game.resolveStack()
            game.state.projectedState.getPower(smith) shouldBe 2
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            game.castSpell(1, "Ornithopter").error shouldBe null
            game.resolveStack()
            game.state.projectedState.getPower(smith) shouldBe 3
        }

        test("an opponent's artifact does not trigger the Smith") {
            val game = scenario()
                .withPlayers("Smith", "Opponent")
                .withCardOnBattlefield(1, "Ingenious Smith")
                .withCardInHand(2, "Memnite")
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val smith = game.findPermanent("Ingenious Smith")!!
            game.castSpell(2, "Memnite").error shouldBe null
            game.resolveStack()
            game.state.projectedState.getPower(smith) shouldBe 1
        }
    }
}
