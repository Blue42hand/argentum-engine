package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class ReturnOfTheWildspeakerScenarioTest : ScenarioTestBase() {
    init {
        test("draw mode uses only the greatest non-Human power you control") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Return of the Wildspeaker")
                .withCardOnBattlefield(1, "Myrsmith") // Human, power 2
                .withCardOnBattlefield(1, "Llanowar Elves") // non-Human, power 1
                .withCardOnBattlefield(1, "Craw Wurm") // non-Human, power 6; max is 6, sum is 7
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Mountain")
                .withLandsOnBattlefield(1, "Forest", 5)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpellWithMode(1, "Return of the Wildspeaker", modeIndex = 0).error shouldBe null
            game.resolveStack()

            game.state.getHand(game.player1Id).size shouldBe 6
        }

        test("draw mode draws zero with no non-Human creature") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Return of the Wildspeaker")
                .withCardOnBattlefield(1, "Myrsmith")
                .withCardInLibrary(1, "Forest")
                .withLandsOnBattlefield(1, "Forest", 5)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpellWithMode(1, "Return of the Wildspeaker", modeIndex = 0).error shouldBe null
            game.resolveStack()

            game.state.getHand(game.player1Id).size shouldBe 0
        }

        test("pump mode affects only your non-Human creatures") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Return of the Wildspeaker")
                .withCardOnBattlefield(1, "Myrsmith") // Human
                .withCardOnBattlefield(1, "Grizzly Bears") // non-Human
                .withCardOnBattlefield(2, "Hill Giant")
                .withLandsOnBattlefield(1, "Forest", 5)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val human = game.findPermanent("Myrsmith")!!
            val bears = game.findPermanent("Grizzly Bears")!!
            val opponent = game.findPermanent("Hill Giant")!!
            game.castSpellWithMode(1, "Return of the Wildspeaker", modeIndex = 1).error shouldBe null
            game.resolveStack()

            val projected = game.state.projectedState
            projected.getPower(human) shouldBe 2
            projected.getToughness(human) shouldBe 1
            projected.getPower(bears) shouldBe 5
            projected.getToughness(bears) shouldBe 5
            projected.getPower(opponent) shouldBe 3
            projected.getToughness(opponent) shouldBe 3

            game.passUntilPhase(Phase.ENDING, Step.CLEANUP)
            game.state.projectedState.getPower(bears) shouldBe 2
            game.state.projectedState.getToughness(bears) shouldBe 2
        }
    }
}
