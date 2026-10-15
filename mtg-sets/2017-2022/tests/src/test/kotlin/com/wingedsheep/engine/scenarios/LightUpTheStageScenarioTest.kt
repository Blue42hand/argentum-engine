package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class LightUpTheStageScenarioTest : ScenarioTestBase() {
    init {
        test("spectacle is rejected before an opponent loses life") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Light Up the Stage")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val card = game.state.getHand(game.player1Id).single()
            game.execute(CastSpell(game.player1Id, card, useAlternativeCost = true)).error shouldNotBe null
        }
        test("opponent life loss enables spectacle and both exiled cards receive permission") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Light Up the Stage").withCardInHand(1, "Shock")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Hill Giant")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingPlayer(1, "Shock", 2).error shouldBe null
            game.resolveStack()
            val card = game.state.getHand(game.player1Id).single()
            game.execute(CastSpell(game.player1Id, card, useAlternativeCost = true)).error shouldBe null
            game.resolveStack()
            val exiled = game.state.getExile(game.player1Id)
            exiled.size shouldBe 2
            game.state.mayPlayPermissions.any { it.cardIds.containsAll(exiled) } shouldBe true
        }
    }
}
