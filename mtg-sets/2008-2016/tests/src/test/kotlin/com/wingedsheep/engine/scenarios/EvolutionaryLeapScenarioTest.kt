package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ori.cards.EvolutionaryLeap
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvolutionaryLeapScenarioTest : FunSpec({
    test("sacrificing a creature reveals to the next creature and puts it into hand") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + EvolutionaryLeap)
            initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val leap = driver.putPermanentOnBattlefield(player, "Evolutionary Leap")
        val fodder = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.putCardOnTopOfLibrary(player, "Bear Cub")
        driver.putCardOnTopOfLibrary(player, "Forest")
        driver.giveMana(player, Color.GREEN, 1)

        driver.submit(
            ActivateAbility(
                playerId = player,
                sourceId = leap,
                abilityId = EvolutionaryLeap.activatedAbilities.single().id,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder)),
            )
        ).error shouldBe null
        driver.bothPass()

        driver.getGraveyardCardNames(player).contains("Grizzly Bears") shouldBe true
        driver.findCardsInHand(player, "Bear Cub").size shouldBe 1
    }
})
