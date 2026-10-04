package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActionParameterizer
import com.wingedsheep.engine.core.ActionParams
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.mtg.sets.definitions.ons.cards.GoblinPiledriver
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Per-pair block offers share the engine's protection and evasion validation. */
class NativeBlockTargetsTest : FunSpec({
    val blueDrake = card("Test Blue Drake") {
        manaCost = "{1}{U}"
        typeLine = "Creature — Drake"
        power = 2
        toughness = 2
    }

    test("protection removes only the blue block pair from native offers") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(GoblinPiledriver, blueDrake))
        driver.initMirrorMatch(Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)
        val piledriver = driver.putCreatureOnBattlefield(attacker, "Goblin Piledriver")
        val bear = driver.putCreatureOnBattlefield(attacker, "Grizzly Bears")
        val drake = driver.putCreatureOnBattlefield(defender, "Test Blue Drake")
        val lion = driver.putCreatureOnBattlefield(defender, "Savannah Lions")
        driver.removeSummoningSickness(piledriver)
        driver.removeSummoningSickness(bear)
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(piledriver, bear), defender).error shouldBe null
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)

        val offered = driver.legalActions(defender).single { it.action is DeclareBlockers }
        offered.validBlockers!!.toSet() shouldBe setOf(drake, lion)
        offered.validBlockTargets!![drake] shouldBe listOf(bear)
        offered.validBlockTargets[lion]!!.toSet() shouldBe setOf(piledriver, bear)
        val info = LegalActionEnricher(ManaSolver(driver.cardRegistry), driver.cardRegistry)
            .enrich(listOf(offered), driver.state, defender).single()
        info.validBlockTargets shouldBe offered.validBlockTargets
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(offered, ActionParams(blockers = mapOf(drake to listOf(piledriver))), driver.state)
        }
        shouldThrow<IllegalArgumentException> {
            ActionParameterizer.apply(info, ActionParams(blockers = mapOf(drake to listOf(piledriver))), driver.state)
        }
        val legal = ActionParameterizer.apply(info, ActionParams(blockers = mapOf(drake to listOf(bear))), driver.state)
        driver.submit(legal).error shouldBe null
    }
})
