package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.rtr.cards.GolgariCharm
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class GolgariCharmScenarioTest : FunSpec({

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + GolgariCharm)
        initMirrorMatch(deck = Deck.of("Swamp" to 20, "Forest" to 20), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.castCharm(player: EntityId, mode: Int, targets: List<ChosenTarget> = emptyList()) {
        val charm = putCardInHand(player, "Golgari Charm")
        giveMana(player, Color.BLACK, 1)
        giveMana(player, Color.GREEN, 1)
        submitSuccess(
            CastSpell(
                playerId = player,
                cardId = charm,
                targets = targets,
                paymentStrategy = PaymentStrategy.FromPool,
                chosenModes = listOf(mode),
                modeTargetsOrdered = listOf(targets),
            )
        )
        bothPass()
    }

    test("first mode gives every creature -1/-1 until end of turn") {
        val driver = driver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val ours = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val theirs = driver.putCreatureOnBattlefield(opponent, "Hill Giant")

        driver.castCharm(player, 0)

        driver.state.projectedState.getPower(ours) shouldBe 1
        driver.state.projectedState.getToughness(ours) shouldBe 1
        driver.state.projectedState.getPower(theirs) shouldBe 2
        driver.state.projectedState.getToughness(theirs) shouldBe 2
    }

    test("second mode destroys the targeted enchantment") {
        val driver = driver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val enchantment = driver.putPermanentOnBattlefield(opponent, "Test Enchantment")

        driver.castCharm(player, 1, listOf(ChosenTarget.Permanent(enchantment)))

        driver.getGraveyard(opponent) shouldContain enchantment
    }

    test("third mode regenerates each creature its caster controls") {
        val driver = driver()
        val player = driver.activePlayer!!
        val bears = driver.putCreatureOnBattlefield(player, "Grizzly Bears")

        driver.castCharm(player, 2)
        val blade = driver.putCardInHand(player, "Doom Blade")
        driver.giveMana(player, Color.BLACK, 1)
        driver.giveColorlessMana(player, 1)
        driver.castSpell(player, blade, listOf(bears))
        driver.bothPass()

        driver.findPermanent(player, "Grizzly Bears") shouldBe bears
        driver.isTapped(bears) shouldBe true
    }
})
