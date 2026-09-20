package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.isd.cards.KessigWolfRun
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KessigWolfRunTest : FunSpec({
    val pumpAbility = KessigWolfRun.activatedAbilities[1]

    test("uses the activation's X value and grants trample until end of turn") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + KessigWolfRun)
        driver.initMirrorMatch(Deck.of("Forest" to 20, "Mountain" to 20), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val me = driver.activePlayer!!
        val wolfRun = driver.putPermanentOnBattlefield(me, "Kessig Wolf Run")
        val bears = driver.putCreatureOnBattlefield(me, "Grizzly Bears")

        driver.giveColorlessMana(me, 3)
        driver.giveMana(me, Color.RED, 1)
        driver.giveMana(me, Color.GREEN, 1)
        driver.submit(
            ActivateAbility(
                playerId = me,
                sourceId = wolfRun,
                abilityId = pumpAbility.id,
                targets = listOf(ChosenTarget.Permanent(bears)),
                xValue = 3,
            )
        ).isSuccess shouldBe true
        driver.bothPass()

        driver.state.projectedState.getPower(bears) shouldBe 5
        driver.state.projectedState.hasKeyword(bears, Keyword.TRAMPLE) shouldBe true

        driver.passPriorityUntil(Step.UPKEEP)
        driver.state.projectedState.getPower(bears) shouldBe 2
        driver.state.projectedState.hasKeyword(bears, Keyword.TRAMPLE) shouldBe false
    }
})
