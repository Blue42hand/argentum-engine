package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.snc.cards.CabarettiCourtyard
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CabarettiCourtyardScenarioTest : FunSpec({
    fun driver(deck: Deck) = GameTestDriver().also {
        it.registerCards(TestCards.all + CabarettiCourtyard)
        it.initMirrorMatch(deck, startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("sacrificing the land searches for an allowed basic tapped and gains one life") {
        val game = driver(Deck.of("Forest" to 40))
        val you = game.player1
        val card = game.putCardInHand(you, "Cabaretti Courtyard")
        game.playLand(you, card).error shouldBe null
        game.bothPass() // Sacrifice and create the reflexive trigger.
        game.bothPass() // Resolve the reflexive trigger through the search.

        val decision = game.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        game.submitCardSelection(you, listOf(decision.options.first())).error shouldBe null
        while (game.state.stack.isNotEmpty() && !game.isPaused) game.bothPass()

        game.findPermanent(you, "Cabaretti Courtyard") shouldBe null
        val basic = game.findPermanent(you, "Forest")
        basic shouldNotBe null
        game.state.getEntity(basic!!)?.has<TappedComponent>() shouldBe true
        game.getLifeTotal(you) shouldBe 21
    }

    test("a library with only a disallowed basic offers no search choice but still gains life") {
        val game = driver(Deck.of("Island" to 40))
        val you = game.player1
        val card = game.putCardInHand(you, "Cabaretti Courtyard")
        game.playLand(you, card).error shouldBe null
        game.bothPass()
        game.bothPass()

        game.pendingDecision shouldBe null
        game.findPermanent(you, "Island") shouldBe null
        game.getLifeTotal(you) shouldBe 21
    }
})
