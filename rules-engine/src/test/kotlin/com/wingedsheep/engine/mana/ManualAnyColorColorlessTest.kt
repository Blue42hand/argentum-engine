package com.wingedsheep.engine.mana

import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.atq.cards.AshnodsAltar
import com.wingedsheep.mtg.sets.definitions.lrw.cards.SpringleafDrum
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Manual color choice can pay generic costs, but cannot supply a colorless pip. */
class ManualAnyColorColorlessTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(SpringleafDrum, AshnodsAltar, PredefinedTokens.Treasure))
        it.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
    }
    fun solver(d: GameTestDriver) = ManaSolver(d.cardRegistry, predicateEvaluator = PredicateEvaluator(cardRegistry = null))

    test("manual any-color production cannot pay C but can pay each actual color") {
        val d = driver(); val p = d.activePlayer!!
        d.putPermanentOnBattlefield(p, "Springleaf Drum")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val s = solver(d)
        s.canPay(d.state, p, ManaCost.parse("{C}")) shouldBe false
        for (color in listOf("W", "U", "B", "R", "G")) {
            s.canPay(d.state, p, ManaCost.parse("{$color}")) shouldBe true
        }
    }
    test("two lands and a manual any-color unit pay generic three but not two plus C") {
        val d = driver(); val p = d.activePlayer!!
        d.putPermanentOnBattlefield(p, "Springleaf Drum")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        repeat(2) { d.putPermanentOnBattlefield(p, "Forest") }
        val s = solver(d)
        s.canPay(d.state, p, ManaCost.parse("{2}{C}")) shouldBe false
        s.canPay(d.state, p, ManaCost.parse("{3}")) shouldBe true
        s.canPay(d.state, p, ManaCost.parse("{G}{C}")) shouldBe false
    }
    test("sacrificing any-color production does not manufacture C either") {
        val d = driver(); val p = d.activePlayer!!
        d.putPermanentOnBattlefield(p, "Treasure")
        val s = solver(d)
        s.canPay(d.state, p, ManaCost.parse("{C}")) shouldBe false
        s.canPay(d.state, p, ManaCost.parse("{1}")) shouldBe true
    }
    test("genuine manual colorless production still pays C and generic costs") {
        val d = driver(); val p = d.activePlayer!!
        d.putPermanentOnBattlefield(p, "Ashnod's Altar")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putPermanentOnBattlefield(p, "Forest")
        val s = solver(d)
        s.canPay(d.state, p, ManaCost.parse("{2}{C}")) shouldBe true
        s.canPay(d.state, p, ManaCost.parse("{G}{C}")) shouldBe true
        s.canPay(d.state, p, ManaCost.parse("{4}")) shouldBe false
    }
})
