package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for Leyline Weaver (OM1 #144).
 *
 * Leyline Weaver {1}{R/G} Creature — Spider Avatar 2/2
 * Reach
 * {T}: Add {R} or {G}.
 * Whenever you cast a spell with mana value 4 or greater, untap this creature.
 */
class LeylineWeaverScenarioTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("casting a spell with mana value 4 or greater untaps it") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val weaver = driver.putCreatureOnBattlefield(active, "Leyline Weaver")
        driver.tapPermanent(weaver)

        val giant = driver.putCardInHand(active, "Hill Giant")
        driver.giveMana(active, Color.RED, 1)
        driver.giveColorlessMana(active, 3)
        driver.castSpell(active, giant).outcome shouldBe Outcome.Done

        driver.state.stack.size shouldBe 2 // Hill Giant + the untap trigger above it
        driver.bothPass() // resolve the trigger
        driver.isTapped(weaver) shouldBe false
    }

    test("a spell with mana value less than 4 does not untap it") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val weaver = driver.putCreatureOnBattlefield(active, "Leyline Weaver")
        driver.tapPermanent(weaver)

        val bears = driver.putCardInHand(active, "Grizzly Bears")
        driver.giveMana(active, Color.GREEN, 1)
        driver.giveColorlessMana(active, 1)
        driver.castSpell(active, bears).outcome shouldBe Outcome.Done

        driver.state.stack.size shouldBe 1
        driver.bothPass()
        driver.isTapped(weaver) shouldBe true
    }

    test("an opponent's big spell does not untap it") {
        val driver = createDriver()
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)
        val weaver = driver.putCreatureOnBattlefield(opponent, "Leyline Weaver")
        driver.tapPermanent(weaver)

        val giant = driver.putCardInHand(active, "Hill Giant")
        driver.giveMana(active, Color.RED, 1)
        driver.giveColorlessMana(active, 3)
        driver.castSpell(active, giant).outcome shouldBe Outcome.Done

        driver.state.stack.size shouldBe 1
        driver.bothPass()
        driver.isTapped(weaver) shouldBe true
    }
})
