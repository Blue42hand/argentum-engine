package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class ConclaveMentorScenarioTest : ScenarioTestBase() {

    init {
        test("adds one extra +1/+1 counter to a creature you control") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Conclave Mentor")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Stony Strength")
                .withCardOnBattlefield(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Stony Strength", targetId = bears).error shouldBe null
            game.resolveStack()

            game.state.getEntity(bears)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
        }

        test("death trigger uses last-known power") {
            val game = scenario()
                .withPlayers()
                .withLifeTotal(1, 10)
                .withCardOnBattlefield(1, "Conclave Mentor")
                .withCardInHand(1, "Stony Strength")
                .withCardInHand(1, "Murder")
                .withCardOnBattlefield(1, "Forest")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val mentor = game.findPermanent("Conclave Mentor")!!
            game.castSpell(1, "Stony Strength", targetId = mentor).error shouldBe null
            game.resolveStack()
            game.state.projectedState.getPower(mentor) shouldBe 4

            game.castSpell(1, "Murder", targetId = mentor).error shouldBe null
            game.resolveStack()

            withClue("the 2/2 Mentor had two +1/+1 counters when it died") {
                game.getLifeTotal(1) shouldBe 14
            }
        }
    }
}
