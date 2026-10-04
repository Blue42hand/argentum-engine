package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.snc.cards.BrokersHideout
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BrokersHideoutScenarioTest : FunSpec({
    fun driver(deck: Deck) = GameTestDriver().also {
        it.registerCards(TestCards.all + BrokersHideout)
        it.initMirrorMatch(deck, startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("sacrificing the land searches for an allowed basic tapped and gains one life") {
        val game = driver(Deck.of("Island" to 40))
        val you = game.player1
        val card = game.putCardInHand(you, "Brokers Hideout")
        game.playLand(you, card).error shouldBe null
        game.bothPass() // Sacrifice and create the reflexive trigger.
        game.bothPass() // Resolve the reflexive trigger through the search.

        val decision = game.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        game.submitCardSelection(you, listOf(decision.options.first())).error shouldBe null
        while (game.state.stack.isNotEmpty() && !game.isPaused) game.bothPass()

        game.findPermanent(you, "Brokers Hideout") shouldBe null
        val basic = game.findPermanent(you, "Island")
        basic shouldNotBe null
        game.state.getEntity(basic!!)?.has<TappedComponent>() shouldBe true
        game.getLifeTotal(you) shouldBe 21
    }

    test("a library with only a disallowed basic offers no search choice but still gains life") {
        val game = driver(Deck.of("Swamp" to 40))
        val you = game.player1
        val card = game.putCardInHand(you, "Brokers Hideout")
        game.playLand(you, card).error shouldBe null
        game.bothPass()
        game.bothPass()

        game.pendingDecision shouldBe null
        game.findPermanent(you, "Swamp") shouldBe null
        game.getLifeTotal(you) shouldBe 21
    }

    test("removing the land before its entry trigger resolves prevents the reflexive search and life gain") {
        val game = driver(Deck.of("Island" to 40))
        val you = game.player1
        val card = game.putCardInHand(you, "Brokers Hideout")
        game.playLand(you, card).error shouldBe null
        val permanent = game.findPermanent(you, "Brokers Hideout")!!
        val boomerang = game.putCardInHand(you, "Boomerang")
        game.giveMana(you, Color.BLUE, 2)
        game.castSpell(you, boomerang, listOf(permanent)).error shouldBe null
        game.bothPass() // Boomerang returns the land before its ETB action.
        game.bothPass() // Failed sacrifice must not create a reflexive trigger.

        game.state.stack.isEmpty() shouldBe true
        game.pendingDecision shouldBe null
        game.getLifeTotal(you) shouldBe 20
        game.findPermanent(you, "Island") shouldBe null
    }
})
