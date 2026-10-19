package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Goblin War Party (MH1 #131) has two cast-time modes. Paying entwine {2}{R}
 * selects both; the first mode creates Goblins, then the second buffs creatures
 * already under the caster's control, including those newly created Goblins.
 *
 * The single-mode and entwined paths exercise the existing ModalEffect cost,
 * printed-order resolution, and snapshot-bounded buff vocabulary together.
 * See Scryfall's 2019-06-14 rulings for the ordering and snapshot boundaries.
 */
class GoblinWarPartyScenarioTest : ScenarioTestBase() {

    init {
        fun board(mountains: Int, withBear: Boolean = false): TestGame {
            var builder = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Goblin War Party")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Mountain")
                .withLandsOnBattlefield(1, "Mountain", mountains)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

            if (withBear) {
                builder = builder.withCardOnBattlefield(1, "Grizzly Bears")
            }
            return builder.build()
        }

        fun cast(game: TestGame, modes: List<Int>) = game.execute(
            CastSpell(
                playerId = game.player1Id,
                cardId = game.findCardsInHand(1, "Goblin War Party").first(),
                chosenModes = modes,
                modeTargetsOrdered = modes.map { emptyList() },
            )
        )

        context("Goblin War Party — independent modes") {
            test("the first mode alone creates three unbuffed Goblins") {
                val game = board(mountains = 4)

                cast(game, listOf(0)).error shouldBe null
                game.resolveStack()

                val tokens = game.findPermanents("Goblin Token")
                tokens.size shouldBe 3
                tokens.forEach { token ->
                    game.state.projectedState.getPower(token) shouldBe 1
                    game.state.projectedState.getToughness(token) shouldBe 1
                    game.state.projectedState.hasKeyword(token, Keyword.HASTE) shouldBe false
                }
            }

            test("the second mode alone buffs a creature already controlled") {
                val game = board(mountains = 4, withBear = true)
                val bear = game.findPermanent("Grizzly Bears")!!

                cast(game, listOf(1)).error shouldBe null
                game.resolveStack()

                withClue("The pre-existing 2/2 is 3/3 with haste") {
                    game.state.projectedState.getPower(bear) shouldBe 3
                    game.state.projectedState.getToughness(bear) shouldBe 3
                    game.state.projectedState.hasKeyword(bear, Keyword.HASTE) shouldBe true
                }
                game.findPermanents("Goblin Token").size shouldBe 0
            }
        }

        context("Goblin War Party — effect boundaries") {
            test("buff and haste expire during cleanup") {
                val game = board(mountains = 4, withBear = true)
                val bear = game.findPermanent("Grizzly Bears")!!
                cast(game, listOf(1)).error shouldBe null
                game.resolveStack()
                game.state.projectedState.getPower(bear) shouldBe 3

                game.passUntilPhase(Phase.ENDING, Step.CLEANUP)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                game.state.projectedState.getPower(bear) shouldBe 2
                game.state.projectedState.getToughness(bear) shouldBe 2
                game.state.projectedState.hasKeyword(bear, Keyword.HASTE) shouldBe false
            }

            test("creatures entering later in the turn do not receive the buff") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Goblin War Party")
                    .withCardInHand(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                cast(game, listOf(1)).error shouldBe null
                game.resolveStack()
                game.castSpell(1, "Grizzly Bears").error shouldBe null
                game.resolveStack()

                game.findPermanents("Grizzly Bears").size shouldBe 2
                game.findPermanents("Grizzly Bears").forEach { bear ->
                    game.state.projectedState.getPower(bear) shouldBe 2
                    game.state.projectedState.getToughness(bear) shouldBe 2
                    game.state.projectedState.hasKeyword(bear, Keyword.HASTE) shouldBe false
                }
            }
        }

        context("Goblin War Party — entwine") {
            test("seven mana selects both and buffs the Goblins created by mode one") {
                val game = board(mountains = 7, withBear = true)
                val bear = game.findPermanent("Grizzly Bears")!!

                cast(game, listOf(0, 1)).error shouldBe null
                game.resolveStack()

                val tokens = game.findPermanents("Goblin Token")
                withClue("The first mode created exactly three Goblins") {
                    tokens.size shouldBe 3
                }
                withClue("Printed mode order lets the second mode see those Goblins") {
                    tokens.forEach { token ->
                        game.state.projectedState.getPower(token) shouldBe 2
                        game.state.projectedState.getToughness(token) shouldBe 2
                        game.state.projectedState.hasKeyword(token, Keyword.HASTE) shouldBe true
                    }
                }
                game.state.projectedState.getPower(bear) shouldBe 3
                game.state.projectedState.hasKeyword(bear, Keyword.HASTE) shouldBe true
            }

            test("four mana cannot select both modes without paying the entwine surcharge") {
                val game = board(mountains = 4)

                cast(game, listOf(0, 1)).error shouldNotBe null

                game.findPermanents("Goblin Token").size shouldBe 0
                game.findCardsInHand(1, "Goblin War Party").size shouldBe 1
            }
        }
    }
}
