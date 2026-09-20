package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class AlelaArtfulProvocateurScenarioTest : ScenarioTestBase() {
    init {
        test("Alela pumps other flyers and creates one pumped Faerie for an artifact spell") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Alela, Artful Provocateur")
                .withCardOnBattlefield(1, "Serra Angel")
                .withCardInHand(1, "Talisman of Curiosity")
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val alela = game.findPermanent("Alela, Artful Provocateur")!!
            val angel = game.findPermanent("Serra Angel")!!
            withClue("Alela excludes herself from her flying lord effect") {
                game.state.projectedState.getPower(alela) shouldBe 2
            }
            withClue("another flyer gets +1/+0") {
                game.state.projectedState.getPower(angel) shouldBe 5
            }

            game.castSpell(1, "Talisman of Curiosity").error shouldBe null
            game.resolveStack()

            val faeries = game.findPermanents("Faerie Token")
            withClue("one artifact cast creates exactly one Faerie") {
                faeries.size shouldBe 1
            }
            withClue("the flying Faerie immediately receives Alela's +1/+0") {
                game.state.projectedState.getPower(faeries.single()) shouldBe 2
                game.state.projectedState.getToughness(faeries.single()) shouldBe 1
            }
        }
    }
}
