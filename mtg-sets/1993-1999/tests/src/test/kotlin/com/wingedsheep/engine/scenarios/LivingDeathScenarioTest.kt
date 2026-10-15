package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class LivingDeathScenarioTest : ScenarioTestBase() {
    init {
        test("all three instructions are global batches; only the original graveyard cards return") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Living Death").withLandsOnBattlefield(1, "Swamp", 5)
                .withCardOnBattlefield(1, "Zulaport Cutthroat").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInGraveyard(1, "Serra Angel").withCardInGraveyard(2, "Air Elemental")
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Living Death").error shouldBe null; game.resolveStack()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, "Zulaport Cutthroat") shouldBe true
            game.isInGraveyard(2, "Hill Giant") shouldBe true
            game.state.projectedState.getController(game.findPermanent("Serra Angel")!!) shouldBe game.player1Id
            game.state.projectedState.getController(game.findPermanent("Air Elemental")!!) shouldBe game.player2Id
            game.getLifeTotal(1) shouldBe 22; game.getLifeTotal(2) shouldBe 18
        }
        test("a sacrificed creature redirected into exile is not returned with the original exiled cards") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Living Death").withLandsOnBattlefield(1, "Swamp", 5)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Leyline of the Void")
                .withCardInGraveyard(1, "Serra Angel")
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bear = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Living Death").error shouldBe null; game.resolveStack()
            game.state.getExile(game.player1Id).contains(bear) shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe false
            game.isOnBattlefield("Serra Angel") shouldBe true
        }
    }
}
