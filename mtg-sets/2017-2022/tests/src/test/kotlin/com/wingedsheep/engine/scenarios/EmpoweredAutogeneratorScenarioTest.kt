package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class EmpoweredAutogeneratorScenarioTest : ScenarioTestBase() {
    init {
        fun board() = scenario().withPlayers()
            .withCardOnBattlefield(1, "Empowered Autogenerator")
            .withCardInLibrary(1, "Forest")
            .withCardInLibrary(2, "Forest")
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

        test("the artifact enters tapped") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Empowered Autogenerator")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Empowered Autogenerator").error shouldBe null
            game.resolveStack()
            val generator = game.findPermanent("Empowered Autogenerator")!!
            game.state.getEntity(generator)?.has<TappedComponent>() shouldBe true
        }

        test("each activation adds a counter before measuring its chosen-color mana") {
            val game = board().build()
            val generator = game.findPermanent("Empowered Autogenerator")!!
            game.state = game.state.updateEntity(generator) { entity ->
                entity.with(CountersComponent(mapOf(CounterType.CHARGE to 1)))
            }
            val ability = cardRegistry.requireCard("Empowered Autogenerator").activatedAbilities.single().id

            game.execute(ActivateAbility(game.player1Id, generator, ability,
                manaColorChoice = Color.BLUE)).error shouldBe null

            game.state.getEntity(generator)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) shouldBe 2
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.blue shouldBe 2
            game.state.stack.isEmpty() shouldBe true
        }

        test("the first activation produces one mana") {
            val game = board().build()
            val generator = game.findPermanent("Empowered Autogenerator")!!
            val ability = cardRegistry.requireCard("Empowered Autogenerator").activatedAbilities.single().id

            game.execute(ActivateAbility(game.player1Id, generator, ability,
                manaColorChoice = Color.RED)).error shouldBe null

            game.state.getEntity(generator)?.get<CountersComponent>()
                ?.getCount(CounterType.CHARGE) shouldBe 1
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.red shouldBe 1
        }
    }
}
