package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.player.SkipNextTurnComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dst.cards.EaterOfDays
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EaterOfDaysScenarioTest : FunSpec({
    test("entering makes its controller skip the next two turns") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + EaterOfDays)
            initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val card = driver.putCardInHand(player, "Eater of Days")
        driver.giveColorlessMana(player, 4)
        driver.castSpell(player, card).error shouldBe null
        driver.bothPass()
        driver.bothPass()

        driver.state.getEntity(player)?.get<SkipNextTurnComponent>()?.turns shouldBe 2
    }
})
