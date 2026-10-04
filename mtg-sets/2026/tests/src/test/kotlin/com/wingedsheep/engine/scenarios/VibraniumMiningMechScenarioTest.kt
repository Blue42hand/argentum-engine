package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class VibraniumMiningMechScenarioTest : ScenarioTestBase() {
    init {
        test("entering creates one tapped Vibranium token") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Vibranium Mining Mech")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Vibranium Mining Mech").error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val tokens = game.findPermanents("Vibranium")
            tokens.size shouldBe 1
            game.state.getEntity(tokens.single())?.has<TokenComponent>() shouldBe true
            game.state.getEntity(tokens.single())?.has<TappedComponent>() shouldBe true
            game.state.projectedState.isCreature(game.findPermanent("Vibranium Mining Mech")!!) shouldBe false
        }

        test("crewing and attacking creates one tapped Vibranium token") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Vibranium Mining Mech", summoningSickness = false)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val mech = game.findPermanent("Vibranium Mining Mech")!!
            val bears = game.findPermanent("Grizzly Bears")!!

            game.execute(CrewVehicle(game.player1Id, mech, listOf(bears))).error shouldBe null
            game.resolveStack()
            game.state.projectedState.isCreature(mech) shouldBe true

            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Vibranium Mining Mech" to 2)).error shouldBe null
            game.resolveStack()

            val tokens = game.findPermanents("Vibranium")
            tokens.size shouldBe 1
            game.state.getEntity(tokens.single())?.has<TappedComponent>() shouldBe true
        }

        test("pumping an uncrewed Vehicle persists after it is crewed") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Vibranium Mining Mech")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val mech = game.findPermanent("Vibranium Mining Mech")!!
            val pump = cardRegistry.requireCard("Vibranium Mining Mech").activatedAbilities.single().id

            game.execute(ActivateAbility(game.player1Id, mech, pump)).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.execute(CrewVehicle(game.player1Id, mech, listOf(bears))).error shouldBe null
            game.resolveStack()

            game.state.projectedState.isCreature(mech) shouldBe true
            game.state.projectedState.getPower(mech) shouldBe 7
            game.state.projectedState.getToughness(mech) shouldBe 6
        }
    }
}
