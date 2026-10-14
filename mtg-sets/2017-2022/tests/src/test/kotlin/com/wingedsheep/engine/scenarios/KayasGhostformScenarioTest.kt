package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe

class KayasGhostformScenarioTest : ScenarioTestBase() {
    init {
        for ((spell, land, count) in listOf(Triple("Doom Blade", "Swamp", 2), Triple("Swords to Plowshares", "Plains", 1))) {
            test("returns the enchanted creature after $spell but not the Aura") {
                val game = scenario().withPlayers("Owner", "Opponent")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardAttachedTo(1, "Kaya's Ghostform", "Grizzly Bears")
                    .withCardInHand(2, spell)
                    .withLandsOnBattlefield(2, land, count)
                    .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
                    .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val bear = game.findPermanent("Grizzly Bears")!!
                game.castSpell(2, spell, bear).error shouldBe null
                game.resolveStack()
                game.isOnBattlefield("Grizzly Bears") shouldBe true
                game.state.projectedState.getController(game.findPermanent("Grizzly Bears")!!) shouldBe game.player1Id
                game.isInGraveyard(1, "Kaya's Ghostform") shouldBe true
            }
        }
    }
}
