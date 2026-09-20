package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Rules-focused scenarios for Generous Gift (MH1 #11).
 *
 * In particular these lock the 2019-06-14 ruling: a legal target that cannot be destroyed still
 * gives its controller an Elephant, while an illegal target makes the whole spell fail to resolve.
 */
class GenerousGiftScenarioTest : ScenarioTestBase() {

    private fun countElephants(game: TestGame, playerNumber: Int): Int {
        val playerId = if (playerNumber == 1) game.player1Id else game.player2Id
        return game.state.getBattlefield(playerId).count { id ->
            game.state.getEntity(id)?.get<CardComponent>()?.name?.contains("Elephant") == true
        }
    }

    init {
        context("Generous Gift") {
            test("destroys a permanent and gives its controller a 3/3 green Elephant") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Generous Gift")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardOnBattlefield(2, "Centaur Courser")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Centaur Courser")!!
                val result = game.castSpell(1, "Generous Gift", targetId = target)
                withClue("Casting Generous Gift should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                game.findPermanent("Centaur Courser") shouldBe null
                countElephants(game, 2) shouldBe 1
                countElephants(game, 1) shouldBe 0
            }

            test("a legal indestructible target survives but its controller still gets the Elephant") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Generous Gift")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardOnBattlefield(2, "Darksteel Citadel")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Darksteel Citadel")!!
                val result = game.castSpell(1, "Generous Gift", targetId = target)
                withClue("Casting Generous Gift should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                withClue("Indestructible target should remain on the battlefield") {
                    game.findPermanent("Darksteel Citadel") shouldBe target
                }
                countElephants(game, 2) shouldBe 1
            }

            test("an illegal target at resolution makes the spell fizzle and creates no Elephant") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Generous Gift")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInHand(2, "Unsummon")
                    .withLandsOnBattlefield(2, "Island", 1)
                    .withCardOnBattlefield(2, "Centaur Courser")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Centaur Courser")!!
                val gift = game.castSpell(1, "Generous Gift", targetId = target)
                withClue("Casting Generous Gift should succeed: ${gift.error}") {
                    gift.error shouldBe null
                }

                val unsummon = game.castSpell(2, "Unsummon", targetId = target)
                withClue("Casting Unsummon in response should succeed: ${unsummon.error}") {
                    unsummon.error shouldBe null
                }
                game.resolveStack()

                game.findPermanent("Centaur Courser") shouldBe null
                countElephants(game, 2) shouldBe 0
                countElephants(game, 1) shouldBe 0
            }
        }
    }
}
