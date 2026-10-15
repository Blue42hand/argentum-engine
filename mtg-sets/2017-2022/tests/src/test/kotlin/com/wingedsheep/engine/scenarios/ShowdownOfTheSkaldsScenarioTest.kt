package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.mtg.sets.definitions.khm.cards.ShowdownOfTheSkalds
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ShowdownOfTheSkaldsScenarioTest : FunSpec({
    fun GameTestDriver.resolve(target: EntityId) {
        var guard = 0
        while ((state.stack.isNotEmpty() || pendingDecision != null) && guard++ < 80) {
            when (val decision = pendingDecision) {
                is ChooseTargetsDecision -> submitTargetSelection(decision.playerId, listOf(target))
                null -> bothPass()
                else -> autoResolveDecision()
            }
        }
        state.stack.size shouldBe 0
        pendingDecision shouldBe null
    }
    fun GameTestDriver.advanceToOwnMain(turn: Int) {
        var guard = 0
        while (!(state.turnNumber == turn && state.step == Step.PRECOMBAT_MAIN) && guard++ < 600) {
            if (pendingDecision != null) autoResolveDecision()
            else if (state.priorityPlayerId != null) {
                autoSubmitCombatDeclarationIfNeeded()
                passPriority(state.priorityPlayerId!!)
            }
        }
        state.turnNumber shouldBe turn
    }
    test("chapter III grants repeated triggers that survive sacrificing the Saga") {
        val d = GameTestDriver().apply {
            registerCards(TestCards.all + ShowdownOfTheSkalds)
            initMirrorMatch(Deck.of("Forest" to 40), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = d.activePlayer!!
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val saga = d.putCardInHand(player, "Showdown of the Skalds")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED, 1)
        d.giveMana(player, Color.WHITE, 1)
        d.castSpell(player, saga).error shouldBe null
        d.resolve(creature)
        d.state.getExile(player).size shouldBe 4

        d.advanceToOwnMain(3)
        d.resolve(creature)
        d.advanceToOwnMain(5)
        d.resolve(creature)
        d.getGraveyard(player).contains(saga) shouldBe true
        for (expected in 1..2) {
            val spell = d.putCardInHand(player, "Grizzly Bears")
            d.giveColorlessMana(player, 1)
            d.giveMana(player, Color.GREEN, 1)
            d.castSpell(player, spell).error shouldBe null
            d.resolve(creature)
            d.state.getEntity(creature)!!.get<CountersComponent>()!!
                .getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe expected
        }
    }
})
