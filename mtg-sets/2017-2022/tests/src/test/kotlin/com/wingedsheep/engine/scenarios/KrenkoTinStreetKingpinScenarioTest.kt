package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class KrenkoTinStreetKingpinScenarioTest : ScenarioTestBase() {
    init {
        test("attack counter is applied before token count reads Krenko's power") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Krenko, Tin Street Kingpin", summoningSickness = false)
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val krenko = game.findPermanent("Krenko, Tin Street Kingpin")!!

            game.declareAttackers(mapOf("Krenko, Tin Street Kingpin" to 2)).error shouldBe null
            game.resolveStack()

            game.state.getEntity(krenko)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            game.findPermanents("Goblin Token").size shouldBe 2
        }
    }
}
