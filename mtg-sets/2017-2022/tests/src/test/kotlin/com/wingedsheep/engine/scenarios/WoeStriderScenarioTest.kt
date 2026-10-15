package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import com.wingedsheep.mtg.sets.definitions.thb.cards.WoeStrider

class WoeStriderScenarioTest : ScenarioTestBase() {
    init {
        test("ordinary cast makes a Goat and enters without escape counters") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Woe Strider").withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Woe Strider").error shouldBe null; game.resolveStack()
            val body = game.findPermanent("Woe Strider")!!
            (game.state.getEntity(body)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            game.state.projectedState.getBattlefieldControlledBy(game.player1Id)
                .count { game.state.projectedState.hasSubtype(it, "Goat") } shouldBe 1
        }
        test("escape pays four other cards and enters with two counters") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInGraveyard(1, "Woe Strider").withCardInGraveyard(1, "Plains")
                .withCardInGraveyard(1, "Island").withCardInGraveyard(1, "Forest").withCardInGraveyard(1, "Mountain")
                .withLandsOnBattlefield(1, "Swamp", 5)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val body = game.state.getGraveyard(game.player1Id).first()
            val fodder = game.state.getGraveyard(game.player1Id).filter { it != body }
            game.execute(CastSpell(game.player1Id, body, useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.ESCAPE,
                additionalCostPayment = AdditionalCostPayment(exiledCards = fodder))).error shouldBe null
            game.resolveStack()
            game.state.getEntity(body)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            game.state.getExile(game.player1Id).containsAll(fodder) shouldBe true
            val goat = game.state.projectedState.getBattlefieldControlledBy(game.player1Id)
                .single { game.state.projectedState.hasSubtype(it, "Goat") }
            val ability = WoeStrider.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, body, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(body)))).error shouldNotBe null
            game.execute(ActivateAbility(game.player1Id, body, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(goat)))).error shouldBe null
            game.resolveStack()
            (game.getPendingDecision() is SelectCardsDecision) shouldBe true
            game.selectCards(emptyList()).error shouldBe null
            game.resolveStack()
            game.keepLibraryOrder().error shouldBe null
            game.getPendingDecision() shouldBe null
        }
    }
}
