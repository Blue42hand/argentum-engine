package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe

class ShurisFabricatorScenarioTest : ScenarioTestBase() {
    init {
        test("entering creates exactly two tapped Vibranium tokens") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Shuri's Fabricator")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Shuri's Fabricator").error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val tokens = game.findPermanents("Vibranium")
            tokens.size shouldBe 2
            tokens.forEach {
                game.state.getEntity(it)?.has<TokenComponent>() shouldBe true
                game.state.getEntity(it)?.has<TappedComponent>() shouldBe true
            }
        }

        test("the sorcery-speed ability returns an artifact with a finality counter") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Shuri's Fabricator")
                .withCardInGraveyard(1, "Sol Ring")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val fabricator = game.findPermanent("Shuri's Fabricator")!!
            val ring = game.findCardsInGraveyard(1, "Sol Ring").single()
            val ability = cardRegistry.requireCard("Shuri's Fabricator").activatedAbilities.single().id

            game.execute(ActivateAbility(
                playerId = game.player1Id,
                sourceId = fabricator,
                abilityId = ability,
                targets = listOf(ChosenTarget.Card(ring, game.player1Id, Zone.GRAVEYARD)),
            )).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            val returned = game.findPermanent("Sol Ring")!!
            game.state.getEntity(returned)?.get<CountersComponent>()?.getCount(CounterType.FINALITY) shouldBe 1
            game.state.getEntity(fabricator)?.has<TappedComponent>() shouldBe true
        }
    }
}
