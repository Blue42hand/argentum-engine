package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect
import io.kotest.matchers.shouldBe

/**
 * Academy Manufactor (MH2 #219) — "If you would create a Clue, Food, or Treasure token, instead
 * create one of each." Pins the three rulings: that many of each, three times that many with two
 * Manufactors, and other tokens untouched.
 */
class AcademyManufactorScenarioTest : ScenarioTestBase() {

    private val twoTreasures = card("Test Two Treasures") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateTreasure(2) }
    }

    private val oneClue = card("Test One Clue") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateClue(1) }
    }

    private val oneSoldier = card("Test One Soldier") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateToken(power = 1, toughness = 1, creatureTypes = setOf("Soldier")) }
    }

    init {
        listOf(twoTreasures, oneClue, oneSoldier).forEach { cardRegistry.register(it) }

        fun cast(spell: String, manufactors: Int = 1): TestGame {
            val builder = scenario().withPlayers("P1", "P2")
            repeat(manufactors) { builder.withCardOnBattlefield(1, "Academy Manufactor") }
            val game = builder
                .withCardInHand(1, spell)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, spell)
            game.resolveStack()
            return game
        }

        fun TestGame.counts() = Triple(
            findPermanents("Clue").size, findPermanents("Food").size, findPermanents("Treasure").size
        )

        test("a Clue becomes one of each") {
            cast("Test One Clue").counts() shouldBe Triple(1, 1, 1)
        }

        test("two Treasures become two of each") {
            cast("Test Two Treasures").counts() shouldBe Triple(2, 2, 2)
        }

        test("two Manufactors make three times that many of each") {
            cast("Test One Clue", manufactors = 2).counts() shouldBe Triple(3, 3, 3)
        }

        test("other tokens are untouched") {
            val game = cast("Test One Soldier")
            game.findPermanents("Soldier Token").size shouldBe 1
            game.counts() shouldBe Triple(0, 0, 0)
        }
        test("casts as a three mana Assembly-Worker and replaces a Treasure with one of each") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Academy Manufactor").withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Academy Manufactor").error shouldBe null
            game.resolveStack()
            game.findPermanents("Academy Manufactor").size shouldBe 1
            val result = services.effectExecutorRegistry.execute(game.state,
                CreatePredefinedTokenEffect("Treasure"), EffectContext(null, game.player1Id))
            result.error shouldBe null
            game.state = result.state
            listOf("Clue", "Food", "Treasure").map { game.findPermanents(it).size } shouldBe listOf(1, 1, 1)
        }
        listOf("Academy Manufactor" to listOf(2, 3, 2), "Peregrin Took" to listOf(3, 3, 3)).forEach { (first, expected) ->
            test("real Peregrin Took replacement can apply after choosing $first first") {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Academy Manufactor")
                    .withCardOnBattlefield(1, "Peregrin Took").build()
                val result = services.effectExecutorRegistry.execute(game.state,
                    CreatePredefinedTokenEffect("Treasure", count = 2), EffectContext(null, game.player1Id))
                result.error shouldBe null
                game.state = result.state
                val decision = game.state.pendingDecision as ChooseOptionDecision
                game.submitDecision(OptionChosenResponse(decision.id,
                    decision.options.indexOfFirst { it.startsWith(first) })).error shouldBe null
                listOf("Clue", "Food", "Treasure").map { game.findPermanents(it).size } shouldBe expected
            }
        }
    }
}
