package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.ActionParameterizer
import com.wingedsheep.engine.core.ActionParams
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.engine.state.components.player.CantLoseLifeComponent
import com.wingedsheep.mtg.sets.definitions.c19.cards.KrrikSonOfYawgmoth
import com.wingedsheep.mtg.sets.definitions.som.cards.Exsanguinate
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Scenario tests for Exsanguinate (SOM).
 *
 * Oracle: "Each opponent loses X life. You gain life equal to the life lost this way."
 *
 * Exercises the new [com.wingedsheep.sdk.dsl.Effects.DrainLife] primitive: the gain equals
 * the life *actually* lost, as a single life-gain event after all losses.
 */
class ExsanguinateScenarioTest : ScenarioTestBase() {

    private fun game(x: Int) = scenario()
        .withPlayers("Player1", "Player2")
        .withCardInHand(1, "Exsanguinate")
        .withLandsOnBattlefield(1, "Swamp", 2 + x)
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    init {
        fun lifePaymentGame(life: Int, black: Int = 0): Pair<GameTestDriver, EntityId> {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all + listOf(Exsanguinate, KrrikSonOfYawgmoth))
            driver.initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val me = driver.activePlayer!!
            driver.putCreatureOnBattlefield(me, "K'rrik, Son of Yawgmoth")
            driver.setLifeTotal(me, life)
            driver.giveColorlessMana(me, 4 - black)
            if (black > 0) driver.giveMana(me, Color.BLACK, black)
            return driver to me
        }

        test("K'rrik permits X four with four floating mana and four life, but not X five") {
            val (driver, me) = lifePaymentGame(20)
            val spell = driver.putCardInHand(me, "Exsanguinate")
            val offer = driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == spell }
            offer.maxAffordableX shouldBe 4
            val presented = LegalActionEnricher(driver.services.manaSolver, driver.cardRegistry)
                .enrich(listOf(offer), driver.state, me).single()
            presented.maxAffordableX shouldBe 4
            (ActionParameterizer.apply(presented, ActionParams(xValue = 4), driver.state) as CastSpell)
                .xValue shouldBe 4
            val before = driver.state
            val rejected = driver.submit(CastSpell(me, spell, xValue = 5, paymentStrategy = PaymentStrategy.Explicit(
                emptyList(), List(2) { Color.BLACK }
            )))
            rejected.error.shouldNotBeNull()
            rejected.newState shouldBe before
            rejected.events shouldBe emptyList()
            driver.getLifeTotal(me) shouldBe 20
            driver.submitSuccess(CastSpell(me, spell, xValue = 4, paymentStrategy = PaymentStrategy.Explicit(
                emptyList(), List(2) { Color.BLACK }
            )))
            driver.getLifeTotal(me) shouldBe 16
            driver.bothPass() // K'rrik's cast trigger
            driver.bothPass() // Exsanguinate
            driver.getLifeTotal(me) shouldBe 20
            driver.getLifeTotal(driver.getOpponent(me)) shouldBe 16
        }

        test("X ceiling respects mixed black mana and a shared life budget") {
            val (driver, me) = lifePaymentGame(3, black = 1)
            val spell = driver.putCardInHand(me, "Exsanguinate")
            driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == spell }
                .maxAffordableX shouldBe 3
            driver.submit(CastSpell(me, spell, xValue = 4, paymentStrategy = PaymentStrategy.Explicit(
                emptyList(), List(2) { Color.BLACK }
            ))).error.shouldNotBeNull()
            driver.getLifeTotal(me) shouldBe 3
            driver.submitSuccess(CastSpell(me, spell, xValue = 3, paymentStrategy = PaymentStrategy.Explicit(
                emptyList(), listOf(Color.BLACK)
            )))
            driver.getLifeTotal(me) shouldBe 1
        }

        test("with too little life BB must use mana and the X ceiling stays two") {
            val (driver, me) = lifePaymentGame(1, black = 2)
            val spell = driver.putCardInHand(me, "Exsanguinate")
            driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == spell }
                .maxAffordableX shouldBe 2
            driver.submitSuccess(CastSpell(me, spell, xValue = 2))
            driver.getLifeTotal(me) shouldBe 1
        }

        test("exactly four life permits both black pips, while a life-loss lock forbids them") {
            val (driver, me) = lifePaymentGame(4, black = 2)
            val spell = driver.putCardInHand(me, "Exsanguinate")
            driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == spell }
                .maxAffordableX shouldBe 4
            driver.replaceState(driver.state.updateEntity(me) { it.with(CantLoseLifeComponent()) })
            driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == spell }
                .maxAffordableX shouldBe 2
            driver.submit(CastSpell(me, spell, xValue = 4, paymentStrategy = PaymentStrategy.Explicit(
                emptyList(), List(2) { Color.BLACK }
            ))).error.shouldNotBeNull()
            driver.getLifeTotal(me) shouldBe 4
        }

        test("X zero accepts life payment but life cannot replace generic X") {
            val (driver, me) = lifePaymentGame(20)
            driver.replaceState(driver.state.updateEntity(me) {
                it.with(com.wingedsheep.engine.state.components.player.ManaPoolComponent())
            })
            val spell = driver.putCardInHand(me, "Exsanguinate")
            driver.legalActions(me).single { (it.action as? CastSpell)?.cardId == spell }
                .maxAffordableX shouldBe 0
            driver.submit(CastSpell(me, spell, xValue = 1)).error.shouldNotBeNull()
            driver.submitSuccess(CastSpell(me, spell, xValue = 0, paymentStrategy = PaymentStrategy.Explicit(
                emptyList(), List(2) { Color.BLACK }
            )))
            driver.getLifeTotal(me) shouldBe 16
        }

        context("Exsanguinate") {
            test("each opponent loses X life and the caster gains that much") {
                val game = game(3)
                val myLife = game.getLifeTotal(1)
                val theirLife = game.getLifeTotal(2)

                game.castXSpell(1, "Exsanguinate", 3).error shouldBe null
                game.resolveStack()

                withClue("opponent loses X = 3") {
                    game.getLifeTotal(2) shouldBe theirLife - 3
                }
                withClue("caster gains the life lost") {
                    game.getLifeTotal(1) shouldBe myLife + 3
                }
            }

            test("X = 0 changes no life totals") {
                val game = game(0)
                val myLife = game.getLifeTotal(1)
                val theirLife = game.getLifeTotal(2)

                game.castXSpell(1, "Exsanguinate", 0).error shouldBe null
                game.resolveStack()

                game.getLifeTotal(1) shouldBe myLife
                game.getLifeTotal(2) shouldBe theirLife
            }

            test("the full X is lost (and gained) even when it takes the opponent below 0") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Exsanguinate")
                    .withLandsOnBattlefield(1, "Swamp", 7)
                    .withLifeTotal(2, 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val myLife = game.getLifeTotal(1)

                game.castXSpell(1, "Exsanguinate", 5).error shouldBe null
                game.resolveStack()

                withClue("life loss is not capped at the opponent's remaining life (CR 119.6)") {
                    game.getLifeTotal(1) shouldBe myLife + 5
                }
            }
        }
    }
}
