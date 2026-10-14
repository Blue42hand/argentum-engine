package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe

class DetentionSphereScenarioTest : ScenarioTestBase() {
    init {
        test("exiles every matching permanent and returns them to their owners when it leaves") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Detention Sphere")
                .withLandsOnBattlefield(1, "Plains", 2).withLandsOnBattlefield(1, "Island", 1)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInHand(2, "Disenchant").withLandsOnBattlefield(2, "Plains", 2)
                .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bears = game.findPermanents("Grizzly Bears").toSet()
            game.castSpell(1, "Detention Sphere").error shouldBe null
            game.resolveStack()
            game.answerYesNo(true).error shouldBe null
            game.selectTargets(listOf(bears.first())).error shouldBe null
            game.resolveStack()
            game.findPermanents("Grizzly Bears").size shouldBe 0
            game.isOnBattlefield("Hill Giant") shouldBe true
            val sphere = game.findPermanent("Detention Sphere")!!
            game.passPriority()
            game.castSpell(2, "Disenchant", sphere).error shouldBe null
            game.resolveStack()
            game.findPermanents("Grizzly Bears").size shouldBe 2
            game.findPermanents("Grizzly Bears").count { game.state.projectedState.getController(it) == game.player1Id } shouldBe 1
            game.findPermanents("Grizzly Bears").count { game.state.projectedState.getController(it) == game.player2Id } shouldBe 1
        }

        test("may be declined without exiling anything") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Detention Sphere")
                .withLandsOnBattlefield(1, "Plains", 2).withLandsOnBattlefield(1, "Island", 1)
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Detention Sphere").error shouldBe null
            game.resolveStack()
            game.answerYesNo(false).error shouldBe null
            game.resolveStack()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
        }
    }
}
