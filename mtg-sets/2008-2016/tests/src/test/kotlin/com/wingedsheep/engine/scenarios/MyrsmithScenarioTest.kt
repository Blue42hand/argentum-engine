package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MyrsmithScenarioTest : ScenarioTestBase() {
    init {
        test("paying once after an artifact cast creates one colorless 1/1 Myr artifact") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Myrsmith")
                .withCardInHand(1, "Memnite")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Memnite").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.submitManaSourcesAutoPay().error shouldBe null
            game.resolveStack()

            val myr = game.state.getZone(game.player1Id, Zone.BATTLEFIELD)
                .filter { game.state.projectedState.hasSubtype(it, "Myr") }
            myr.size shouldBe 1
            val projected = game.state.projectedState
            projected.hasType(myr.single(), "ARTIFACT") shouldBe true
            projected.getPower(myr.single()) shouldBe 1
            projected.getToughness(myr.single()) shouldBe 1
            projected.getColors(myr.single()).isEmpty() shouldBe true
        }

        test("declining the payment creates no Myr") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Myrsmith")
                .withCardInHand(1, "Memnite")
                .withLandsOnBattlefield(1, "Plains", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Memnite").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(false).error shouldBe null
            game.resolveStack()

            game.state.getZone(game.player1Id, Zone.BATTLEFIELD)
                .count { game.state.projectedState.hasSubtype(it, "Myr") } shouldBe 0
        }

        test("casting a nonartifact spell offers no payment") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Myrsmith")
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.resolveStack()
            (game.getPendingDecision() is YesNoDecision) shouldBe false
            game.state.getZone(game.player1Id, Zone.BATTLEFIELD)
                .count { game.state.projectedState.hasSubtype(it, "Myr") } shouldBe 0
        }
    }
}
