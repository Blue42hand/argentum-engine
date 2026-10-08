package com.wingedsheep.engine.mana

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.AlternativePaymentChoice
import com.wingedsheep.sdk.scripting.ConvokePayment
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual

class PhyrexianXAffordabilityTest : FunSpec({
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("each X consumes mana separately after the life-paid fixed pips") {
        val driver = driver()
        val me = driver.activePlayer!!
        driver.giveColorlessMana(me, 5)
        val cost = ManaCost.parse("{X}{X}{B/P}{B/P}")
        driver.services.manaSolver.maxAffordableXWithLife(driver.state, me, cost) shouldBe 2
        driver.services.manaSolver.canPay(driver.state, me, cost, 2) shouldBe true
        driver.services.manaSolver.canPay(driver.state, me, cost, 3) shouldBe false
    }

    test("fixed generic increases must still be paid with mana") {
        val driver = driver()
        val me = driver.activePlayer!!
        driver.giveColorlessMana(me, 4)
        val cost = ManaCost.parse("{2}{X}{B/P}{B/P}")
        driver.services.manaSolver.maxAffordableXWithLife(driver.state, me, cost) shouldBe 2
        driver.services.manaSolver.canPay(driver.state, me, cost, 3) shouldBe false
    }

    test("a life-payable pip cannot hide an unpaid colored requirement") {
        val driver = driver()
        val me = driver.activePlayer!!
        driver.giveColorlessMana(me, 4)
        val cost = ManaCost.parse("{X}{U}{B/P}")
        driver.services.manaSolver.maxAffordableXWithLife(driver.state, me, cost) shouldBe 0
        driver.services.manaSolver.canPay(driver.state, me, cost, 0) shouldBe false
    }

    test("an X color restriction still applies when the fixed pips use life") {
        val driver = driver()
        val me = driver.activePlayer!!
        driver.giveColorlessMana(me, 3)
        driver.giveMana(me, Color.RED, 1)
        val cost = ManaCost.parse("{X}{B/P}")
        driver.services.manaSolver.maxAffordableXWithLife(driver.state, me, cost,
            xManaRestriction = setOf(Color.RED)) shouldBe 1
        driver.services.manaSolver.canPay(driver.state, me, cost, 2,
            xManaRestriction = setOf(Color.RED)) shouldBe false
    }

    test("eligible restricted floating mana contributes to the ceiling") {
        val driver = driver()
        val me = driver.activePlayer!!
        driver.giveRestrictedMana(me, null, 4, ManaRestriction.InstantOrSorceryOnly)
        val cost = ManaCost.parse("{X}{B/P}{B/P}")
        val context = SpellPaymentContext(isInstantOrSorcery = true)
        driver.services.manaSolver.maxAffordableXWithLife(driver.state, me, cost,
            spellContext = context) shouldBe 4
        driver.services.manaSolver.maxAffordableXWithLife(driver.state, me, cost) shouldBe 0
    }

    test("monocolored hybrid can use one colored mana rather than its larger mana value") {
        val driver = driver()
        val me = driver.activePlayer!!
        driver.giveMana(me, Color.BLUE, 4)
        val cost = ManaCost.parse("{X}{2/U}{B/P}")
        driver.services.manaSolver.maxAffordableXWithLife(driver.state, me, cost) shouldBe 3
        driver.services.manaSolver.canPay(driver.state, me, cost, 3) shouldBe true
        driver.services.manaSolver.canPay(driver.state, me, cost, 4) shouldBe false
    }

    test("colored convoke stays on its alternate-payment path even when its creature is a mana source") {
        val driver = driver()
        val me = driver.activePlayer!!
        val source = card("Blue Convoke Mana Source") {
            manaCost = "{U}"
            typeLine = "Creature — Wizard"
            power = 1
            toughness = 1
            activatedAbility { cost = Costs.Tap; effect = Effects.AddColorlessMana(1) }
        }
        val probe = card("Convoke Phyrexian X Probe") {
            manaCost = "{X}{U}{B/P}"
            typeLine = "Sorcery"
            keywords(Keyword.CONVOKE)
            spell { effect = Effects.DrawCards(1) }
        }
        driver.registerCards(listOf(source, probe))
        val creature = driver.putCreatureOnBattlefield(me, source.name)
        driver.removeSummoningSickness(creature)
        driver.giveColorlessMana(me, 4)
        val spell = driver.putCardInHand(me, probe.name)
        val offer = driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == spell }
        // The existing alternate-payment resource bound must not become an ordinary-mana
        // solver's zero ceiling: this creature can pay U despite producing only colorless mana.
        offer.maxAffordableX!!.shouldBeGreaterThanOrEqual(3)
        driver.submitSuccess(CastSpell(me, spell, xValue = 3,
            alternativePayment = AlternativePaymentChoice(
                convokedCreatures = mapOf(creature to ConvokePayment(Color.BLUE))),
            paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLACK))))
        driver.getLifeTotal(me) shouldBe 18
        driver.isTapped(creature) shouldBe true
    }
})
