package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.combat.CombatTaxes
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import io.kotest.matchers.shouldBe

class SummonYojimboScenarioTest : ScenarioTestBase() {
    private val advance = card("Yojimbo Test Advance Chapter") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val saga = target(TargetFilter(GameObjectFilter.Enchantment.youControl()))
            effect = Effects.AddCounters(CounterType.LORE, 1, saga)
        }
    }
    private fun board(large: Boolean = false) = scenario().withPlayers("Player", "Opponent")
        .withCardInHand(1, "Summon: Yojimbo")
        .withCardInHand(1, advance.name).withCardInHand(1, advance.name).withCardInHand(1, advance.name)
        .withLandsOnBattlefield(1, "Plains", 4)
        .withCardOnBattlefield(1, "Serra Angel")
        .withCardOnBattlefield(2, if (large) "Air Elemental" else "Grizzly Bears")
        .withCardOnBattlefield(2, if (large) "Craw Wurm" else "Hill Giant")
        .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()

    private fun cast(game: TestGame) {
        game.castSpell(1, "Summon: Yojimbo").error shouldBe null
        game.resolveStack() // No legal chapter-I targets on these boards.
    }
    private fun chapter(game: TestGame) {
        game.castSpell(1, advance.name, game.findPermanent("Summon: Yojimbo")!!).error shouldBe null
        game.resolveStack()
    }
    private fun price(game: TestGame, count: Int = 1): Int {
        val attackers = game.state.projectedState.getBattlefieldControlledBy(game.player2Id)
            .filter { game.state.projectedState.isCreature(it) }.take(count)
        return CombatTaxes.attackTax(game.state, cardRegistry,
            attackers.associateWith { game.player1Id }, game.state.projectedState,
            PredicateEvaluator(cardRegistry))
    }

    init {
        cardRegistry.register(advance)
        test("chapter II taxes each attacker and the protection survives Yojimbo leaving") {
            val game = board()
            cast(game); chapter(game)
            price(game, 2) shouldBe 4
            game.state = game.zones.moveToZone(game.state, game.findPermanent("Summon: Yojimbo")!!, Zone.GRAVEYARD).state
            price(game) shouldBe 2
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            price(game) shouldBe 2 // Opponent's next turn is still protected.
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            price(game) shouldBe 0 // Caster's next turn ends the duration.
        }
        test("two chapter resolutions in the same turn stack their taxes") {
            val game = board()
            cast(game); chapter(game); chapter(game)
            price(game) shouldBe 4
        }
        test("chapter IV counts qualifying opponents, not their creatures, and ignores your large creature") {
            val game = board(large = true)
            cast(game); repeat(3) { chapter(game) }
            game.findPermanents("Treasure").size shouldBe 1
            game.isInGraveyard(1, "Summon: Yojimbo") shouldBe true
        }
        test("chapter IV creates no Treasure when only you control a large creature") {
            val game = board()
            cast(game); repeat(3) { chapter(game) }
            game.findPermanents("Treasure").size shouldBe 0
        }
    }
}
