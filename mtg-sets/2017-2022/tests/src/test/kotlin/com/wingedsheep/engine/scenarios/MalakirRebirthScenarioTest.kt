package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class MalakirRebirthScenarioTest : ScenarioTestBase() {
    init {
        cardRegistry.register(com.wingedsheep.mtg.sets.definitions.m10.cards.DoomBlade)
        test("returns the targeted creature tapped exactly once") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Malakir Rebirth").withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(2, "Doom Blade").withCardInHand(2, "Doom Blade").withLandsOnBattlefield(2, "Swamp", 4)
                .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bear = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Malakir Rebirth", bear).error shouldBe null; game.resolveStack()
            game.getLifeTotal(1) shouldBe 18
            game.getLifeTotal(2) shouldBe 20
            game.passPriority()
            game.castSpell(2, "Doom Blade", bear).error shouldBe null; game.resolveStack()
            game.state.getBattlefield().contains(bear) shouldBe true
            game.state.getEntity(bear)!!.has<TappedComponent>() shouldBe true
            if (game.state.priorityPlayerId != game.player2Id) game.passPriority()
            game.castSpell(2, "Doom Blade", bear).error shouldBe null; game.resolveStack()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
        }
        test("the land back enters tapped and has a black mana ability") {
            val game = scenario().withPlayers("Player", "Opponent").withCardInHand(1, "Malakir Rebirth")
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.execute(PlayLand(game.player1Id, game.state.getHand(game.player1Id).single(), asBackFace = true)).error shouldBe null
            val land = game.findPermanent("Malakir Mire")!!
            game.state.getEntity(land)!!.has<TappedComponent>() shouldBe true
            game.state.projectedState.hasType(land, "LAND") shouldBe true
        }
        test("an illegal target on resolution prevents the life loss") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Malakir Rebirth").withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(2, "Doom Blade").withLandsOnBattlefield(2, "Swamp", 2)
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bear = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Malakir Rebirth", bear).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Doom Blade", bear).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
        }
    }
}
