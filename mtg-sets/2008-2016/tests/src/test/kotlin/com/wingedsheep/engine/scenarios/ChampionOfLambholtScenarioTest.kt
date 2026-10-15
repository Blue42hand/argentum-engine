package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.avr.cards.ChampionOfLambholt
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ChampionOfLambholtScenarioTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + ChampionOfLambholt)
        it.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    for ((championPower, attackerName, blocked) in listOf(
        Triple(4, "Grizzly Bears", false),
        Triple(1, "Hill Giant", true),
        Triple(2, "Hill Giant", true),
        Triple(3, "Grizzly Bears", false)
    )) {
        test("power-$championPower Champion determines whether a 2-power blocker can block $attackerName") {
            val d = driver()
            val attacker = d.activePlayer!!
            val defender = d.getOpponent(attacker)
            val champion = d.putCreatureOnBattlefield(attacker, "Champion of Lambholt")
            val ally = d.putCreatureOnBattlefield(attacker, attackerName)
            d.addComponent(champion, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to championPower - 1)))
            d.removeSummoningSickness(ally)
            val blocker = d.putCreatureOnBattlefield(defender, "Grizzly Bears")
            d.passPriorityUntil(Step.DECLARE_ATTACKERS)
            d.declareAttackers(attacker, listOf(ally), defender).error shouldBe null
            d.passPriorityUntil(Step.DECLARE_BLOCKERS)
            val result = d.declareBlockers(defender, mapOf(blocker to listOf(ally)))
            if (blocked) result.error shouldBe null else result.error shouldNotBe null
        }
    }

    test("an opposing Champion does not protect the attacking player's creatures") {
        val d = driver()
        val attacker = d.activePlayer!!
        val defender = d.getOpponent(attacker)
        val champion = d.putCreatureOnBattlefield(defender, "Champion of Lambholt")
        d.addComponent(champion, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 5)))
        val ally = d.putCreatureOnBattlefield(attacker, "Hill Giant")
        d.removeSummoningSickness(ally)
        val blocker = d.putCreatureOnBattlefield(defender, "Grizzly Bears")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(attacker, listOf(ally), defender).error shouldBe null
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        d.declareBlockers(defender, mapOf(blocker to listOf(ally))).error shouldBe null
    }
})
