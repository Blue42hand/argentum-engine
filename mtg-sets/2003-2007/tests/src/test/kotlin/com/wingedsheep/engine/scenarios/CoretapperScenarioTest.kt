package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CoretapperScenarioTest : ScenarioTestBase() {
    private fun chargeCounters(state: com.wingedsheep.engine.state.GameState, artifact: EntityId): Int =
        state.getEntity(artifact)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0

    init {
        fun board() = scenario().withPlayers()
            .withCardOnBattlefield(1, "Coretapper", summoningSickness = false)
            .withCardOnBattlefield(1, "Darksteel Ingot")
            .withCardOnBattlefield(2, "Darksteel Ingot")
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

        test("tapping adds one charge counter to an artifact") {
            val game = board().build()
            val myr = game.findPermanent("Coretapper")!!
            val ingot = game.findPermanents("Darksteel Ingot")
                .single { game.state.projectedState.getController(it) == game.player1Id }
            val tapAbility = cardRegistry.requireCard("Coretapper").activatedAbilities[0].id

            game.execute(ActivateAbility(game.player1Id, myr, tapAbility,
                targets = listOf(ChosenTarget.Permanent(ingot)))).error shouldBe null
            game.resolveStack()

            chargeCounters(game.state, ingot) shouldBe 1
        }

        test("sacrificing adds two charge counters to an opponent's artifact") {
            val game = board().build()
            val myr = game.findPermanent("Coretapper")!!
            val ingot = game.findPermanents("Darksteel Ingot")
                .single { game.state.projectedState.getController(it) == game.player2Id }
            val sacrificeAbility = cardRegistry.requireCard("Coretapper").activatedAbilities[1].id

            game.execute(ActivateAbility(game.player1Id, myr, sacrificeAbility,
                targets = listOf(ChosenTarget.Permanent(ingot)))).error shouldBe null
            game.isInGraveyard(1, "Coretapper") shouldBe true
            game.resolveStack()

            chargeCounters(game.state, ingot) shouldBe 2
        }

        test("a nonartifact permanent cannot be targeted") {
            val game = board().withCardOnBattlefield(1, "Forest").build()
            val myr = game.findPermanent("Coretapper")!!
            val forest = game.findPermanent("Forest")!!
            val tapAbility = cardRegistry.requireCard("Coretapper").activatedAbilities[0].id

            game.execute(ActivateAbility(game.player1Id, myr, tapAbility,
                targets = listOf(ChosenTarget.Permanent(forest)))).error shouldNotBe null
            chargeCounters(game.state, forest) shouldBe 0
        }
    }
}
