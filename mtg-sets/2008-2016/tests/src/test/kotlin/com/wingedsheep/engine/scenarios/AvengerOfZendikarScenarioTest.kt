package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class AvengerOfZendikarScenarioTest : ScenarioTestBase() {
    init {
        test("ETB counts lands and accepting landfall puts a counter on every Plant") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Avenger of Zendikar")
                .withCardInHand(1, "Forest")
                .withLandsOnBattlefield(1, "Forest", 7)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Avenger of Zendikar").error shouldBe null
            game.resolveStack()

            withClue("seven controlled lands create seven Plants") {
                game.findPermanents("Plant Token").size shouldBe 7
            }

            val land = game.state.getHand(game.player1Id).first { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Forest"
            }
            game.execute(PlayLand(game.player1Id, land)).error shouldBe null
            game.resolveStack()
            game.answerYesNo(true)
            game.resolveStack()

            withClue("the optional landfall effect counters every Plant present at resolution") {
                game.findPermanents("Plant Token").forEach { plant ->
                    game.state.getEntity(plant)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }
            }
        }
    }
}
