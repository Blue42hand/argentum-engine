package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.FaceDownModeComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.frf.cards.RealityShift
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.FaceDownMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class RealityShiftScenarioTest : FunSpec({

    test("exiles a creature and its controller manifests their top card") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + RealityShift)
            initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val victim = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val topCard = driver.putCardOnTopOfLibrary(opponent, "Hill Giant")
        val spell = driver.putCardInHand(player, "Reality Shift")

        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 1)
        driver.castSpell(player, spell, listOf(victim)).error shouldBe null
        while (!driver.isPaused && driver.state.stack.isNotEmpty()) driver.bothPass()

        driver.state.getZone(com.wingedsheep.engine.state.ZoneKey(opponent, Zone.EXILE)) shouldContain victim
        driver.getPermanents(opponent) shouldContain topCard
        driver.state.getEntity(topCard)?.get<FaceDownComponent>() shouldBe FaceDownComponent
        driver.state.getEntity(topCard)?.get<FaceDownModeComponent>()?.mode shouldBe FaceDownMode.MANIFEST
        driver.state.projectedState.getPower(topCard) shouldBe 2
        driver.state.projectedState.getToughness(topCard) shouldBe 2
    }
})
