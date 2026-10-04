package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class KarnsBastionScenarioTest : ScenarioTestBase() {
    init {
        test("the mana ability taps for one colorless mana") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Karn's Bastion")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bastion = game.findPermanent("Karn's Bastion")!!
            val manaAbility = cardRegistry.requireCard("Karn's Bastion").activatedAbilities
                .first { it.isManaAbility }.id

            game.execute(ActivateAbility(game.player1Id, bastion, manaAbility)).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 1
        }

        test("the paid ability proliferates a chosen permanent but leaves an unchosen one alone") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Karn's Bastion")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Savannah Lions")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bastion = game.findPermanent("Karn's Bastion")!!
            val bears = game.findPermanent("Grizzly Bears")!!
            val lions = game.findPermanent("Savannah Lions")!!
            for (entity in listOf(bears, lions)) {
                game.state = game.state.updateEntity(entity) { container ->
                    container.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1))
                }
            }
            val ability = cardRegistry.requireCard("Karn's Bastion").activatedAbilities
                .first { !it.isManaAbility }.id

            game.execute(ActivateAbility(game.player1Id, bastion, ability)).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.hasPendingDecision() shouldBe true
            game.selectCards(listOf(bears)).error shouldBe null

            game.state.getEntity(bears)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            game.state.getEntity(lions)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
        }
    }
}
