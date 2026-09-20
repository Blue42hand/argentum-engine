package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.eld.cards.DwarvenMine
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DwarvenMineTest : FunSpec({
    fun createDriver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + DwarvenMine)
        it.initMirrorMatch(Deck.of("Plains" to 40), startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("enters untapped and creates a Dwarf when you already control three Mountains") {
        val driver = createDriver()
        val me = driver.activePlayer!!
        repeat(3) { driver.putPermanentOnBattlefield(me, "Mountain") }
        val mine = driver.putCardInHand(me, "Dwarven Mine")

        driver.playLand(me, mine).isSuccess shouldBe true
        driver.isTapped(mine) shouldBe false
        driver.bothPass()

        driver.getCreatures(me).count { driver.getCardName(it) == "Dwarf Token" } shouldBe 1
    }

    test("enters tapped and creates no Dwarf with fewer than three other Mountains") {
        val driver = createDriver()
        val me = driver.activePlayer!!
        repeat(2) { driver.putPermanentOnBattlefield(me, "Mountain") }
        val mine = driver.putCardInHand(me, "Dwarven Mine")

        driver.playLand(me, mine).isSuccess shouldBe true
        driver.isTapped(mine) shouldBe true

        driver.getCreatures(me).count { driver.getCardName(it) == "Dwarf Token" } shouldBe 0
    }
})
