package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect
import io.kotest.matchers.shouldBe

class AcademyManufactorScenarioTest : ScenarioTestBase() {
    init {
        test("casts as a three mana Assembly-Worker and replaces a Treasure with one of each") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Academy Manufactor").withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Academy Manufactor").error shouldBe null
            game.resolveStack()
            game.findPermanents("Academy Manufactor").size shouldBe 1
            val result = services.effectExecutorRegistry.execute(game.state,
                CreatePredefinedTokenEffect("Treasure"), EffectContext(null, game.player1Id))
            result.error shouldBe null
            game.state = result.state
            listOf("Clue", "Food", "Treasure").map { game.findPermanents(it).size } shouldBe listOf(1, 1, 1)
        }
        listOf("Academy Manufactor" to listOf(2, 3, 2), "Peregrin Took" to listOf(3, 3, 3)).forEach { (first, expected) ->
            test("real Peregrin Took replacement can apply after choosing $first first") {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Academy Manufactor")
                    .withCardOnBattlefield(1, "Peregrin Took").build()
                val result = services.effectExecutorRegistry.execute(game.state,
                    CreatePredefinedTokenEffect("Treasure", count = 2), EffectContext(null, game.player1Id))
                result.error shouldBe null
                game.state = result.state
                val decision = game.state.pendingDecision as ChooseOptionDecision
                game.submitDecision(OptionChosenResponse(decision.id,
                    decision.options.indexOfFirst { it.startsWith(first) })).error shouldBe null
                listOf("Clue", "Food", "Treasure").map { game.findPermanents(it).size } shouldBe expected
            }
        }
    }
}
