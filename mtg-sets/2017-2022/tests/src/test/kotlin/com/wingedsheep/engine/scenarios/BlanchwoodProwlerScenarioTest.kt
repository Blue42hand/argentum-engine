package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BlanchwoodProwlerScenarioTest : ScenarioTestBase() {
    init {
        test("Blanchwood Prowler returns a milled land without getting a counter") {
            var builder = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Blanchwood Prowler")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(3) { builder = builder.withCardInLibrary(1, "Forest") }
            val game = builder.build()

            game.castSpell(1, "Blanchwood Prowler").error shouldBe null
            game.resolveStack()
            val decision = game.getPendingDecision()
            decision.shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(listOf(decision.options.first()))
            game.resolveStack()

            game.findCardsInGraveyard(1, "Forest").size shouldBe 2
            val prowler = game.findPermanent("Blanchwood Prowler")!!
            val counters = game.state.getEntity(prowler)?.get<CountersComponent>()
            (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
        }

        test("Blanchwood Prowler gets a counter when no milled land is taken") {
            var builder = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Blanchwood Prowler")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(3) { builder = builder.withCardInLibrary(1, "Grizzly Bears") }
            val game = builder.build()

            game.castSpell(1, "Blanchwood Prowler").error shouldBe null
            game.resolveStack()
            if (game.hasPendingDecision()) game.skipSelection()
            game.resolveStack()

            val prowler = game.findPermanent("Blanchwood Prowler")!!
            val counters = game.state.getEntity(prowler)?.get<CountersComponent>()
            (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
        }
    }
}
