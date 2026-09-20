package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh1.cards.HallOfHeliodsGenerosity
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class HallOfHeliodsGenerosityTest : FunSpec({
    val recursionAbility = HallOfHeliodsGenerosity.activatedAbilities[1]

    fun createDriver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + HallOfHeliodsGenerosity)
        it.initMirrorMatch(Deck.of("Plains" to 20, "Forest" to 20), startingLife = 20)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("puts an enchantment card from your graveyard on top of your library") {
        val driver = createDriver()
        val me = driver.activePlayer!!
        val hall = driver.putPermanentOnBattlefield(me, "Hall of Heliod's Generosity")
        val enchantment = driver.putCardInGraveyard(me, "Glorious Anthem")

        driver.giveColorlessMana(me, 1)
        driver.giveMana(me, Color.WHITE, 1)
        driver.submit(
            ActivateAbility(
                playerId = me,
                sourceId = hall,
                abilityId = recursionAbility.id,
                targets = listOf(ChosenTarget.Card(enchantment, me, Zone.GRAVEYARD)),
            )
        ).isSuccess shouldBe true
        driver.bothPass()

        driver.state.getLibrary(me).first() shouldBe enchantment
    }

    test("cannot target a nonenchantment card") {
        val driver = createDriver()
        val me = driver.activePlayer!!
        val hall = driver.putPermanentOnBattlefield(me, "Hall of Heliod's Generosity")
        val creature = driver.putCardInGraveyard(me, "Grizzly Bears")

        driver.giveColorlessMana(me, 1)
        driver.giveMana(me, Color.WHITE, 1)
        driver.submit(
            ActivateAbility(
                playerId = me,
                sourceId = hall,
                abilityId = recursionAbility.id,
                targets = listOf(ChosenTarget.Card(creature, me, Zone.GRAVEYARD)),
            )
        ).isSuccess shouldBe false
        driver.getGraveyardCardNames(me) shouldContain "Grizzly Bears"
    }
})
