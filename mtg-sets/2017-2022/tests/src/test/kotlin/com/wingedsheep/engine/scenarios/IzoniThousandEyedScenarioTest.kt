package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import com.wingedsheep.mtg.sets.definitions.grn.cards.IzoniThousandEyed

class IzoniThousandEyedScenarioTest : ScenarioTestBase() {
    init {
        test("undergrowth counts creature cards including artifact creatures, not other card types") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Izoni, Thousand-Eyed")
                .withLandsOnBattlefield(1, "Swamp", 3).withLandsOnBattlefield(1, "Forest", 3)
                .withCardInGraveyard(1, "Grizzly Bears").withCardInGraveyard(1, "Ornithopter")
                .withCardInGraveyard(1, "Swamp").withCardInGraveyard(2, "Hill Giant")
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Izoni, Thousand-Eyed").error shouldBe null; game.resolveStack()
            val insects = game.state.projectedState.getBattlefieldControlledBy(game.player1Id)
                .filter { game.state.projectedState.hasSubtype(it, "Insect") }
            insects.size shouldBe 2
            insects.forEach { game.state.projectedState.getPower(it) shouldBe 1 }
        }
        test("another creature pays the activation; Izoni cannot sacrifice itself") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Izoni, Thousand-Eyed").withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 1).withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val izoni = game.findPermanent("Izoni, Thousand-Eyed")!!; val bear = game.findPermanent("Grizzly Bears")!!
            val ability = IzoniThousandEyed.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, izoni, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(izoni)))).error shouldNotBe null
            game.execute(ActivateAbility(game.player1Id, izoni, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(bear)))).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 21; game.state.getHand(game.player1Id).size shouldBe 1
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
        }
    }
}
