package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.stx.cards.NecroblossomSnarl
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class NecroblossomSnarlTest : FunSpec({
    fun createDriver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + NecroblossomSnarl)
        it.initMirrorMatch(Deck.of("Mountain" to 40), startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("revealing a Swamp lets the land enter untapped") {
        val driver = createDriver()
        val me = driver.activePlayer!!
        val swamp = driver.putCardInHand(me, "Swamp")
        val snarl = driver.putCardInHand(me, "Necroblossom Snarl")

        driver.playLand(me, snarl).isPaused shouldBe true
        driver.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>().options shouldBe listOf(swamp)
        driver.submitCardSelection(me, listOf(swamp)).isSuccess shouldBe true

        driver.isTapped(snarl) shouldBe false
    }

    test("declining the reveal makes the land enter tapped") {
        val driver = createDriver()
        val me = driver.activePlayer!!
        driver.putCardInHand(me, "Forest")
        val snarl = driver.putCardInHand(me, "Necroblossom Snarl")

        driver.playLand(me, snarl).isPaused shouldBe true
        driver.submitCardSelection(me, emptyList()).isSuccess shouldBe true

        driver.isTapped(snarl) shouldBe true
    }
})
