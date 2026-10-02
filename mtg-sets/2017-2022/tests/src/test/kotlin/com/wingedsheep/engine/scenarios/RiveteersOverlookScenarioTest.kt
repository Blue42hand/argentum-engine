package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.snc.cards.RiveteersOverlook
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RiveteersOverlookScenarioTest : FunSpec({
    fun driver(deck: Deck = Deck.of("Forest" to 40)) = GameTestDriver().also {
        it.registerCards(TestCards.all + RiveteersOverlook)
        it.initMirrorMatch(deck, startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("successful sacrifice creates the reflexive search and gains life") {
        val game = driver()
        val you = game.player1
        val overlook = game.putCardInHand(you, "Riveteers Overlook")
        game.playLand(you, overlook).error shouldBe null
        game.bothPass() // ETB: sacrifice, put the reflexive trigger on the stack
        game.bothPass() // reflexive trigger: pause for the library search

        val decision = game.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        game.submitCardSelection(you, listOf(decision.options.first())).error shouldBe null
        while (game.state.stack.isNotEmpty() && !game.isPaused) game.bothPass()

        game.findPermanent(you, "Riveteers Overlook") shouldBe null
        val forest = game.findPermanent(you, "Forest")
        forest shouldNotBe null
        game.state.getEntity(forest!!)?.has<TappedComponent>() shouldBe true
        game.getLifeTotal(you) shouldBe 21
    }

    test("declining to find a basic land still gains life") {
        val game = driver()
        val you = game.player1
        val overlook = game.putCardInHand(you, "Riveteers Overlook")
        game.playLand(you, overlook).error shouldBe null
        game.bothPass() // Sacrifice and create the reflexive trigger.
        game.bothPass() // Resolve the reflexive trigger through the library search.

        game.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        game.submitCardSelection(you, emptyList()).error shouldBe null
        while (game.state.stack.isNotEmpty() && !game.isPaused) game.bothPass()

        game.findPermanent(you, "Riveteers Overlook") shouldBe null
        game.findPermanent(you, "Forest") shouldBe null
        game.getLifeTotal(you) shouldBe 21
    }

    test("a library with no matching basic still gains life without putting a land onto the battlefield") {
        val game = driver(Deck.of("Plains" to 40))
        val you = game.player1
        val overlook = game.putCardInHand(you, "Riveteers Overlook")
        game.playLand(you, overlook).error shouldBe null
        game.bothPass() // Sacrifice and create the reflexive trigger.
        game.bothPass() // Search has no eligible cards, then gain life.

        game.pendingDecision shouldBe null
        game.findPermanent(you, "Riveteers Overlook") shouldBe null
        game.findPermanent(you, "Plains") shouldBe null
        game.getLifeTotal(you) shouldBe 21
    }

    test("if the land leaves before its ETB resolves, failed sacrifice creates no reflexive trigger") {
        val game = driver()
        val you = game.player1
        val overlookCard = game.putCardInHand(you, "Riveteers Overlook")
        game.playLand(you, overlookCard).error shouldBe null
        val overlook = game.findPermanent(you, "Riveteers Overlook")!!
        val boomerang = game.putCardInHand(you, "Boomerang")
        game.giveMana(you, Color.BLUE, 2)
        game.castSpell(you, boomerang, listOf(overlook)).error shouldBe null
        game.bothPass() // Boomerang resolves first
        game.bothPass() // ETB action cannot sacrifice the absent incarnation

        game.pendingDecision shouldBe null
        game.getLifeTotal(you) shouldBe 20
        game.findPermanent(you, "Forest") shouldBe null
    }
})
