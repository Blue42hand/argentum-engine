package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
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
