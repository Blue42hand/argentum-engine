package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Peer into the Abyss — both rounded halves use the targeted player's state. */
class PeerIntoTheAbyssScenarioTest : ScenarioTestBase() {
    init {
        fun game(targetLibrarySize: Int, targetLife: Int, targetPlayer: Int): TestGame {
            var builder = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Peer into the Abyss")
                .withLandsOnBattlefield(1, "Swamp", 7)
                .withLifeTotal(targetPlayer, targetLife)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(targetLibrarySize) { builder = builder.withCardInLibrary(targetPlayer, "Forest") }
            return builder.build()
        }

        fun TestGame.castAt(targetPlayer: Int) {
            val card = findCardsInHand(1, "Peer into the Abyss").single()
            val target = if (targetPlayer == 1) player1Id else player2Id
            execute(CastSpell(player1Id, card, listOf(ChosenTarget.Player(target)))).error shouldBe null
            resolveStack()
        }

        test("opponent with odd library and life draws and loses rounded-up halves") {
            val game = game(targetLibrarySize = 5, targetLife = 7, targetPlayer = 2)
            game.castAt(2)

            game.handSize(2) shouldBe 3
            game.librarySize(2) shouldBe 2
            game.getLifeTotal(2) shouldBe 3
            game.getLifeTotal(1) shouldBe 20
        }

        test("caster may target themselves and even halves are exact") {
            val game = game(targetLibrarySize = 4, targetLife = 8, targetPlayer = 1)
            game.castAt(1)

            game.handSize(1) shouldBe 2
            game.librarySize(1) shouldBe 2
            game.getLifeTotal(1) shouldBe 4
            game.getLifeTotal(2) shouldBe 20
        }
    }
}

/**
 * Peer into the Abyss (M21 #117) — {4}{B}{B}{B} Sorcery
 *
 *   Target player draws cards equal to half the number of cards in their library and loses half
 *   their life. Round up each time.
 *
 * Both amounts are read off the *targeted* player and rounded up (ruling 2020-06-23: 7 life → lose 4).
 */
class PeerIntoTheAbyssUpstreamScenarioTest : ScenarioTestBase() {

    init {
        context("Peer into the Abyss") {

            test("the targeted opponent draws half their library and loses half their life, rounded up") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Peer into the Abyss")
                    .withLandsOnBattlefield(1, "Swamp", 7)
                    .apply { repeat(4) { withCardInLibrary(1, "Forest") } }
                    .apply { repeat(5) { withCardInLibrary(2, "Island") } }
                    .withLifeTotal(1, 20)
                    .withLifeTotal(2, 7)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val spell = game.findCardsInHand(1, "Peer into the Abyss").single()
                val result = game.execute(
                    CastSpell(game.player1Id, spell, listOf(ChosenTarget.Player(game.player2Id)))
                )
                withClue("cast should succeed: ${result.error}") { result.error shouldBe null }
                game.resolveStack()

                withClue("five cards in library → draws three") {
                    game.handSize(2) shouldBe 3
                    game.librarySize(2) shouldBe 2
                }
                withClue("7 life → loses 4") { game.getLifeTotal(2) shouldBe 3 }
                withClue("the caster is untouched") {
                    game.getLifeTotal(1) shouldBe 20
                    game.librarySize(1) shouldBe 4
                }
            }
        }
    }
}
