package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class TheGreatMoundScenarioTest : ScenarioTestBase() {
    private val abilities = cardRegistry.getCard("The Great Mound")!!.activatedAbilities

    init {
        test("the mana ability adds one colorless and taps The Great Mound") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "The Great Mound")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val mound = game.findPermanent("The Great Mound")!!

            game.execute(ActivateAbility(game.player1Id, mound, abilities[0].id)).error shouldBe null

            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 1
            game.state.getEntity(mound)?.has<TappedComponent>() shouldBe true
        }

        test("the three-mana ability creates one tapped Vibranium token") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "The Great Mound")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val mound = game.findPermanent("The Great Mound")!!

            game.execute(ActivateAbility(game.player1Id, mound, abilities[1].id)).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val tokens = game.findPermanents("Vibranium")
            tokens.size shouldBe 1
            game.state.getEntity(tokens.single())?.has<TokenComponent>() shouldBe true
            game.state.getEntity(tokens.single())?.has<TappedComponent>() shouldBe true
            game.state.getEntity(mound)?.has<TappedComponent>() shouldBe true
        }

        test("the six-mana ability draws exactly one card") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "The Great Mound")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val mound = game.findPermanent("The Great Mound")!!
            val handBefore = game.state.getHand(game.player1Id).size

            game.execute(ActivateAbility(game.player1Id, mound, abilities[2].id)).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            game.state.getHand(game.player1Id).size shouldBe handBefore + 1
            game.state.getEntity(mound)?.has<TappedComponent>() shouldBe true
        }
    }
}
