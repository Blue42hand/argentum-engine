package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c19.cards.KrrikSonOfYawgmoth
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * K'rrik, Son of Yawgmoth — {4}{B/P}{B/P}{B/P}, 2/2 Lifelink
 *
 * For each {B} in a cost, you may pay 2 life rather than pay that mana.
 * Whenever you cast a black spell, put a +1/+1 counter on K'rrik.
 */
class KrrikSonOfYawgmothScenarioTest : FunSpec({

    fun setup(): Pair<GameTestDriver, EntityId> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(KrrikSonOfYawgmoth))
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver to driver.activePlayer!!
    }

    fun GameTestDriver.plusOnes(entityId: EntityId): Int =
        state.getEntity(entityId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    test("a black spell's {B} is paid with 2 life, and casting it grows K'rrik") {
        val (driver, me) = setup()
        val krrik = driver.putCreatureOnBattlefield(me, "K'rrik, Son of Yawgmoth")
        val island = driver.putLandOnBattlefield(me, "Island")
        val creature = driver.putCardInHand(me, "Black Creature")

        driver.submitSuccess(CastSpell(playerId = me, cardId = creature))
        withClue("{1} from the Island, {B} from 2 life") {
            driver.isTapped(island) shouldBe true
            driver.getLifeTotal(me) shouldBe 18
        }
        driver.bothPass()
        driver.plusOnes(krrik) shouldBe 1
        driver.bothPass()
        driver.findPermanent(me, "Black Creature") shouldBe creature
    }

    test("generic mana still needs mana, and a nonblack spell doesn't grow K'rrik") {
        val (driver, me) = setup()
        val krrik = driver.putCreatureOnBattlefield(me, "K'rrik, Son of Yawgmoth")
        val black = driver.putCardInHand(me, "Black Creature")
        withClue("the {1} can't be paid with life") {
            (driver.legalActions(me).firstOrNull { (it.action as? CastSpell)?.cardId == black }?.affordable == true) shouldBe false
        }

        driver.putLandOnBattlefield(me, "Forest")
        driver.putLandOnBattlefield(me, "Forest")
        val bears = driver.putCardInHand(me, "Grizzly Bears")
        driver.submitSuccess(CastSpell(playerId = me, cardId = bears))
        driver.bothPass()
        driver.plusOnes(krrik) shouldBe 0
        driver.getLifeTotal(me) shouldBe 20
    }

    test("K'rrik itself can be cast with 6 life and four mana; its mana value stays 7") {
        val (driver, me) = setup()
        repeat(4) { driver.putLandOnBattlefield(me, "Island") }
        val krrik = driver.putCardInHand(me, "K'rrik, Son of Yawgmoth")

        driver.submitSuccess(CastSpell(playerId = me, cardId = krrik))
        driver.getLifeTotal(me) shouldBe 14
        driver.bothPass()
        driver.findPermanent(me, "K'rrik, Son of Yawgmoth") shouldBe krrik
        KrrikSonOfYawgmoth.manaCost.cmc shouldBe 7
    }

    test("command-zone life payment exposes lands even without a mana-only auto-tap preview") {
        val (driver, me) = setup()
        val lands = List(4) { driver.putLandOnBattlefield(me, "Island") }
        val krrik = driver.putCardInCommandZone(me, "K'rrik, Son of Yawgmoth")
        driver.replaceState(driver.state.updateEntity(krrik) { it.with(CommanderComponent(me)) })
        val offer = driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == krrik }
        offer.autoTapPreview shouldBe null
        val presented = LegalActionEnricher(driver.services.manaSolver, driver.cardRegistry)
            .enrich(listOf(offer), driver.state, me).single()
        presented.availableManaSources.shouldNotBeNull().map { it.entityId }.toSet() shouldBe lands.toSet()
        driver.submitSuccess(CastSpell(me, krrik, paymentStrategy = PaymentStrategy.Explicit(
            lands, List(3) { Color.BLACK }
        )))
        driver.getLifeTotal(me) shouldBe 14
        lands.forEach { driver.isTapped(it) shouldBe true }
    }

    test("mixed floating mana and selected sources pay generic after the life choices") {
        val (driver, me) = setup()
        driver.giveColorlessMana(me, 2)
        val islands = List(2) { driver.putLandOnBattlefield(me, "Island") }
        val swamp = driver.putLandOnBattlefield(me, "Swamp")
        val krrik = driver.putCardInHand(me, "K'rrik, Son of Yawgmoth")
        driver.submitSuccess(CastSpell(me, krrik, paymentStrategy = PaymentStrategy.Explicit(
            islands + swamp, List(2) { Color.BLACK }
        )))
        driver.getLifeTotal(me) shouldBe 16
        (islands + swamp).forEach { driver.isTapped(it) shouldBe true }
        driver.state.getEntity(me)!!.get<ManaPoolComponent>()!!.total shouldBe 0
    }

    test("insufficient life rejects manual payment without spending mana or tapping sources") {
        val (driver, me) = setup()
        driver.setLifeTotal(me, 5)
        val lands = List(4) { driver.putLandOnBattlefield(me, "Island") }
        val krrik = driver.putCardInHand(me, "K'rrik, Son of Yawgmoth")
        val before = driver.state
        val rejected = driver.submit(CastSpell(me, krrik, paymentStrategy = PaymentStrategy.Explicit(
            lands, List(3) { Color.BLACK }
        )))
        rejected.error.shouldNotBeNull()
        rejected.newState shouldBe before
        rejected.events shouldBe emptyList()
        driver.getLifeTotal(me) shouldBe 5
        lands.forEach { driver.isTapped(it) shouldBe false }
    }

    test("commander tax remains generic mana when the Phyrexian pips use life") {
        val (driver, me) = setup()
        val krrik = driver.putCardInCommandZone(me, "K'rrik, Son of Yawgmoth")
        driver.replaceState(driver.state.updateEntity(krrik) {
            it.with(CommanderComponent(me, castsFromCommandZone = 1))
        })
        val lands = MutableList(4) { driver.putLandOnBattlefield(me, "Island") }
        val payment = List(3) { Color.BLACK }
        val before = driver.state
        val rejected = driver.submit(CastSpell(me, krrik, paymentStrategy = PaymentStrategy.Explicit(lands, payment)))
        rejected.error.shouldNotBeNull()
        rejected.newState shouldBe before
        rejected.events shouldBe emptyList()
        driver.getLifeTotal(me) shouldBe 20
        lands.forEach { driver.isTapped(it) shouldBe false }
        repeat(2) { lands += driver.putLandOnBattlefield(me, "Island") }
        driver.submitSuccess(CastSpell(me, krrik, paymentStrategy = PaymentStrategy.Explicit(lands, payment)))
        driver.getLifeTotal(me) shouldBe 14
        lands.forEach { driver.isTapped(it) shouldBe true }
    }
})
