package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class PantherRobotScenarioTest : ScenarioTestBase() {
    init {
        val scrap = card("Panther Scrap") {
            manaCost = "{1}"
            colorIdentity = ""
            typeLine = "Artifact"
            oracleText = ""
        }
        cardRegistry.register(scrap)

        test("eight artifacts reduce its ten-mana cost to two and it retains reach and trample") {
            var builder = scenario()
                .withPlayers("P1", "P2")
                .withCardInHand(1, "Panther Robot")
                .withLandsOnBattlefield(1, "Plains", 2)
            repeat(8) { builder = builder.withCardOnBattlefield(1, "Panther Scrap") }
            val game = builder.withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val result = game.castSpell(1, "Panther Robot")
            withClue("affinity reduces {10} to {2}: ${result.error}") {
                result.error shouldBe null
            }
            game.resolveStack()

            val panther = game.findPermanent("Panther Robot")!!
            game.state.projectedState.getPower(panther) shouldBe 8
            game.state.projectedState.getToughness(panther) shouldBe 8
            game.state.projectedState.hasKeyword(panther, Keyword.REACH) shouldBe true
            game.state.projectedState.hasKeyword(panther, Keyword.TRAMPLE) shouldBe true
        }
    }
}
