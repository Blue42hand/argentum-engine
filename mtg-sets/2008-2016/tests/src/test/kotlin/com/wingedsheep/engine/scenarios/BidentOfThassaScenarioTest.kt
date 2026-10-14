package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.ths.cards.BidentOfThassa
import com.wingedsheep.sdk.core.*
import com.wingedsheep.engine.core.ActivateAbility
import io.kotest.matchers.shouldBe

class BidentOfThassaScenarioTest : ScenarioTestBase() {
    init {
        test("Bident's rule includes later arrivals, survives its source, and expires at cleanup") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Bident of Thassa")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInHand(2, "Raging Goblin")
                .withCardInLibrary(1, "Island").withCardInLibrary(2, "Mountain")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bident = game.findPermanent("Bident of Thassa")!!
            game.execute(ActivateAbility(game.player1Id, bident, BidentOfThassa.activatedAbilities.first().id)).error shouldBe null
            game.resolveStack()
            val giant = game.findPermanent("Hill Giant")!!
            val bear = game.findPermanent("Grizzly Bears")!!
            game.state.projectedState.mustAttack(giant) shouldBe true
            game.state.projectedState.mustAttack(bear) shouldBe false
            val goblin = game.state.getHand(game.player2Id).first()
            game.state = game.zones.moveToZone(game.state, goblin, Zone.BATTLEFIELD).state
            game.state.projectedState.mustAttack(goblin) shouldBe true
            game.state = game.zones.moveToZone(game.state, bident, Zone.GRAVEYARD).state
            game.state.projectedState.mustAttack(goblin) shouldBe true
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.state.projectedState.mustAttack(giant) shouldBe false
            game.state.projectedState.mustAttack(goblin) shouldBe false
        }
    }
}
