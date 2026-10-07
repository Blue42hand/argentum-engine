package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.eld.cards.ChitteringWitch
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.model.Deck
import io.kotest.matchers.shouldBe

class ChitteringWitchScenarioTest : ScenarioTestBase() {
    init {
        test("entering creates one Rat per opponent in a three-seat game") {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all + ChitteringWitch)
            val players = driver.initMultiplayer(decks = List(3) { Deck.of("Swamp" to 40) })
            val caster = players.first()
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val witch = driver.putCardInHand(caster, "Chittering Witch")
            driver.giveMana(caster, Color.BLACK, 4)
            driver.castSpell(caster, witch).error shouldBe null
            var passes = 0
            while (driver.stackSize > 0 && passes++ < 12) driver.passPriority(driver.priorityPlayer!!)

            driver.getPermanents(caster).count { driver.getCardName(it) == "Rat Token" } shouldBe 2
            players.drop(1).forEach { opponent ->
                driver.getPermanents(opponent).count { driver.getCardName(it) == "Rat Token" } shouldBe 0
            }
        }

        test("entering creates a Rat for the opponent in a two-seat game") {
            val game = scenario()
                .withPlayers("Witch", "Opponent")
                .withCardInHand(1, "Chittering Witch")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Chittering Witch").error shouldBe null
            game.resolveStack()
            game.findPermanents("Rat Token").size shouldBe 1
        }

        test("sacrificing a Rat pays for the activated -2/-2 and expires at cleanup") {
            val game = scenario()
                .withPlayers("Witch", "Opponent")
                .withCardInHand(1, "Chittering Witch")
                .withCardOnBattlefield(2, "Force of Nature")
                .withLandsOnBattlefield(1, "Swamp", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Chittering Witch").error shouldBe null
            game.resolveStack()

            val witch = game.findPermanent("Chittering Witch")!!
            val rat = game.findPermanent("Rat Token")!!
            val giant = game.findPermanent("Force of Nature")!!
            val abilityId = cardRegistry.getCard("Chittering Witch")!!.activatedAbilities.single().id
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = witch,
                    abilityId = abilityId,
                    targets = listOf(ChosenTarget.Permanent(giant)),
                    costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(rat)),
                )
            ).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Rat Token") shouldBe false
            game.state.projectedState.getPower(giant) shouldBe 3
            game.state.projectedState.getToughness(giant) shouldBe 3
            game.passUntilPhase(Phase.ENDING, Step.CLEANUP)
            game.state.projectedState.getPower(giant) shouldBe 5
            game.state.projectedState.getToughness(giant) shouldBe 5
        }
    }
}
