package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.khm.cards.BindingTheOldGods
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class BindingTheOldGodsScenarioTest : FunSpec({

    fun GameTestDriver.resolve(target: EntityId? = null) {
        var guard = 0
        while ((state.stack.isNotEmpty() || pendingDecision != null) && guard++ < 80) {
            when (val decision = pendingDecision) {
                is ChooseTargetsDecision -> submitTargetSelection(decision.playerId, listOfNotNull(target))
                is SelectCardsDecision -> {
                    val forest = decision.options.first()
                    submitDecision(decision.playerId, CardsSelectedResponse(decision.id, listOf(forest)))
                }
                null -> bothPass()
                else -> autoResolveDecision()
            }
        }
    }

    fun GameTestDriver.advanceToOwnMain(nth: Int) {
        val targetTurn = nth * 2 - 1
        var guard = 0
        while (!(state.turnNumber == targetTurn && state.step == Step.PRECOMBAT_MAIN) && guard++ < 600) {
            if (pendingDecision != null) autoResolveDecision()
            else if (state.priorityPlayerId != null) {
                autoSubmitCombatDeclarationIfNeeded()
                passPriority(state.priorityPlayerId!!)
            }
        }
    }

    test("its three chapters destroy, fetch a tapped Forest, and grant deathtouch") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + BindingTheOldGods)
            initMirrorMatch(deck = Deck.of("Forest" to 20, "Swamp" to 20), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val victim = driver.putPermanentOnBattlefield(opponent, "Test Enchantment")
        val creature = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val saga = driver.putCardInHand(player, "Binding the Old Gods")

        driver.giveColorlessMana(player, 2)
        driver.giveMana(player, Color.BLACK, 1)
        driver.giveMana(player, Color.GREEN, 1)
        driver.castSpell(player, saga).error shouldBe null
        driver.resolve(victim)
        driver.getGraveyard(opponent) shouldContain victim

        val landsBefore = driver.getPermanents(player).count {
            driver.state.projectedState.hasType(it, "LAND")
        }
        driver.advanceToOwnMain(2)
        driver.resolve()
        val landsAfter = driver.getPermanents(player).filter {
            driver.state.projectedState.hasType(it, "LAND")
        }
        landsAfter.size shouldBe landsBefore + 1
        landsAfter.any { driver.isTapped(it) && driver.state.projectedState.hasSubtype(it, "Forest") } shouldBe true

        driver.advanceToOwnMain(3)
        driver.resolve()
        driver.state.projectedState.hasKeyword(creature, Keyword.DEATHTOUCH) shouldBe true
    }
})
