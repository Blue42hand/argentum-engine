package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class SwanSongScenarioTest : ScenarioTestBase() {
    init {
        test("counters an instant and gives its controller one flying Bird") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Swan Song")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val lifeBefore = game.getLifeTotal(1)

            game.castSpellTargetingPlayer(2, "Lightning Bolt", 1).error shouldBe null
            game.passPriority()
            game.castSpellTargetingStackSpell(1, "Swan Song", "Lightning Bolt").error shouldBe null
            game.resolveStack()

            withClue("Lightning Bolt is countered before dealing damage") {
                game.isInGraveyard(2, "Lightning Bolt") shouldBe true
                game.getLifeTotal(1) shouldBe lifeBefore
            }
            val birds = game.findAllPermanents("Bird Token")
            withClue("the countered spell's controller gets exactly one Bird") {
                birds.size shouldBe 1
                game.state.getEntity(birds.single())?.get<ControllerComponent>()?.playerId shouldBe game.player2Id
            }
        }
    }
}
