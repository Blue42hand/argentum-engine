package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class GlitteringMassifScenarioTest : ScenarioTestBase() {
    init {
        test("the Mountain Plains land enters tapped") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardInHand(1, "Glittering Massif")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val massif = game.findCardsInHand(1, "Glittering Massif").single()
            game.execute(PlayLand(game.player1Id, massif)).error shouldBe null
            game.isOnBattlefield("Glittering Massif") shouldBe true
            game.state.getEntity(massif)!!.has<TappedComponent>() shouldBe true
        }

        test("cycling for two discards the land and draws a card") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardInHand(1, "Glittering Massif")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withCardInLibrary(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.cycleCard(1, "Glittering Massif").error shouldBe null
            game.resolveStack()
            game.isInGraveyard(1, "Glittering Massif") shouldBe true
            game.isInHand(1, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Glittering Massif") shouldBe false
        }
    }
}
