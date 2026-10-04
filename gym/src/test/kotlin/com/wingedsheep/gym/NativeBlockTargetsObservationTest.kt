package com.wingedsheep.gym

import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.ons.cards.GoblinPiledriver
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NativeBlockTargetsObservationTest : FunSpec({
    val blueDrake = card("Test Blue Drake") {
        manaCost = "{1}{U}"
        typeLine = "Creature — Drake"
        power = 2
        toughness = 2
    }

    test("Gym observation keeps the legal blocker-attacker pair map") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(GoblinPiledriver, blueDrake))
        driver.initMirrorMatch(Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)
        val piledriver = driver.putCreatureOnBattlefield(attacker, "Goblin Piledriver")
        val bear = driver.putCreatureOnBattlefield(attacker, "Grizzly Bears")
        val drake = driver.putCreatureOnBattlefield(defender, "Test Blue Drake")
        driver.removeSummoningSickness(piledriver)
        driver.removeSummoningSickness(bear)
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(piledriver, bear), defender).error shouldBe null
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
        val offered = driver.legalActions(defender)
        val view = (ObservationBuilder(driver.cardRegistry).build(driver.state, defender, offered)
            .observation as TrainingObservation).legalActions.first {
            offered[it.actionId].action is DeclareBlockers
        }
        view.validBlockTargets[drake] shouldBe listOf(bear)
        (piledriver in view.validBlockTargets[drake].orEmpty()) shouldBe false
    }
})
