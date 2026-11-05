package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
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

/**
 * Ingenious Smith (AFR #21) — {1}{W} Creature — Human Artificer 1/1.
 *
 *   When this creature enters, look at the top four cards of your library. You may reveal an
 *   artifact card from among them and put it into your hand. Put the rest on the bottom of your
 *   library in a random order.
 *   Whenever one or more artifacts you control enter, put a +1/+1 counter on this creature. This
 *   ability triggers only once each turn.
 */
class IngeniousSmithUpstreamScenarioTest : ScenarioTestBase() {

    init {
        test("ETB puts a revealed artifact into hand") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Ingenious Smith")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Ornithopter")
                .withCardInLibrary(1, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val ornithopter = game.findCardsInLibrary(1, "Ornithopter").single()

            game.castSpell(1, "Ingenious Smith").error shouldBe null
            game.resolveStack()
            game.selectCards(listOf(ornithopter)).error shouldBe null
            game.resolveStack()

            game.isInHand(1, "Ornithopter") shouldBe true
            game.findCardsInLibrary(1, "Grizzly Bears").size shouldBe 1
            game.findCardsInLibrary(1, "Hill Giant").size shouldBe 1
        }

        test("artifacts entering add only one +1/+1 counter per turn") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Ingenious Smith")
                .withCardsInHand(1, "Ornithopter", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val smith = game.findPermanent("Ingenious Smith")!!
            fun counters() = game.state.getEntity(smith)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

            game.castSpell(1, "Ornithopter").error shouldBe null
            game.resolveStack()
            withClue("the first artifact entering adds a counter") { counters() shouldBe 1 }

            game.castSpell(1, "Ornithopter").error shouldBe null
            game.resolveStack()
            withClue("the ability triggers only once each turn") { counters() shouldBe 1 }
        }
    }
}
