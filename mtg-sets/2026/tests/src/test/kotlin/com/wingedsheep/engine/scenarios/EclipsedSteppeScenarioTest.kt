package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class EclipsedSteppeScenarioTest : ScenarioTestBase() {

    init {
        test("enters tapped with fewer than two basic lands") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Eclipsed Steppe")
                .withCardOnBattlefield(1, "Plains")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val steppe = game.findCardsInHand(1, "Eclipsed Steppe").single()
            game.execute(PlayLand(game.player1Id, steppe)).error shouldBe null

            game.state.getEntity(steppe)?.has<TappedComponent>() shouldBe true
        }

        test("enters untapped when two basic lands are controlled") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Eclipsed Steppe")
                .withCardOnBattlefield(1, "Plains")
                .withCardOnBattlefield(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val steppe = game.findCardsInHand(1, "Eclipsed Steppe").single()
            game.execute(PlayLand(game.player1Id, steppe)).error shouldBe null

            withClue("the threshold counts basic lands, not all lands") {
                game.state.getEntity(steppe)?.has<TappedComponent>() shouldBe false
            }
        }
    }
}
