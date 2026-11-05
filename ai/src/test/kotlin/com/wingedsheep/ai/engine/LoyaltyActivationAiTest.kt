package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.insight.AiActionOption
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.AbilityCost
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * [AiProfile.fuelLoyaltyIsNotBoardValue] and [AiProfile.spendFuelWalkers]. The empower-Jace token ("−1: Surveil 1. −3: Draw a
 * card.") was offered every main phase from turn 5 to 24 of a 2026-10-10 `live` AI-vs-AI log (FRA
 * vs BLB, game 2) and never activated: the evaluator charges each spent loyalty counter as board
 * value, and neither ability buys anything it can see.
 *
 * [AiProfile.spendFuelWalkers] closes what that refund left open, off the 2026-10-15 production
 * games (148 main phases passed with "−1: Surveil 1" affordable, 15 with "−3: Draw a card"): the
 * last counters of a minus-only walker, a surveil that scored as a tie with passing, and minus
 * abilities granted by the FRA "Way of the X" enchantments.
 */
class LoyaltyActivationAiTest : ScenarioTestBase() {

    /** A walker with a + ability: its loyalty is a real stock, so a minus is still charged. */
    private val plusWalker = card("Test Plus Walker") {
        manaCost = "{3}"
        typeLine = "Planeswalker — Test"
        startingLoyalty = 0
        loyaltyAbility(1) { effect = Effects.GainLife(1) }
        loyaltyAbility(-1) { effect = Patterns.Library.surveil(1) }
    }

    init {
        cardRegistry.register(listOf(plusWalker))
    }

    private fun TestGame.withLoyalty(cardName: String, loyalty: Int) {
        val id = findPermanent(cardName)!!
        state = state.updateEntity(id) { c ->
            c.with((c.get<CountersComponent>() ?: CountersComponent()).withAdded(CounterType.LOYALTY, loyalty))
        }
    }

    private fun walkerBoard(
        walker: String,
        loyalty: Int,
        ours: List<String> = listOf("Grizzly Bears"),
        theirs: List<String> = listOf("Grizzly Bears"),
        life: Int = 20,
        library: Int = 8,
    ): TestGame {
        val game = scenario().withPlayers()
            .withRngSeed(RNG_SEED)
            .withCardOnBattlefield(1, walker, isToken = walker == "Jace")
            .apply { ours.forEach { withCardOnBattlefield(1, it) } }
            .apply { theirs.forEach { withCardOnBattlefield(2, it) } }
            .withLifeTotal(1, life)
            .withLandsOnBattlefield(1, "Plains", 3)
            .apply { repeat(library) { withCardInLibrary(1, if (it % 2 == 0) "Plains" else "Grizzly Bears") } }
            .apply { repeat(8) { withCardInLibrary(2, "Plains") } }
            .build()
        game.withLoyalty(walker, loyalty)
        return game.advanceToPriority(1, Step.PRECOMBAT_MAIN)
    }

    /** The action [profile] takes, and every option it scored, for the failure message. */
    private fun choose(game: TestGame, profile: AiProfile): Pair<GameAction, List<AiActionOption>> {
        var options = emptyList<AiActionOption>()
        val sink = AiInsightSink { _, insight -> if (insight.options.size > 1) options = insight.options }
        val action = AIPlayer.create(cardRegistry, game.player1Id, profile, insightSink = sink)
            .chooseAction(game.state)
        return action to options
    }

    private fun TestGame.activates(action: GameAction, walker: String) =
        action is ActivateAbility && action.sourceId == findPermanent(walker)

    /** The loyalty change of the Jace token's own ability [action] activates; null for anything else. */
    private fun TestGame.jaceCost(action: GameAction): Int? {
        if (action !is ActivateAbility || action.sourceId != findPermanent("Jace")) return null
        val ability = cardRegistry.getCard("Jace")!!.script.activatedAbilities.find { it.id == action.abilityId }
        return (ability?.cost as? AbilityCost.Loyalty)?.change
    }

    init {
        test("the Jace token's loyalty is only fuel; a walker with a + ability's is not") {
            val intents = IntentCatalog.of(cardRegistry)
            intents.loyaltyIsOnlyFuel("Jace") shouldBe true
            intents.loyaltyIsOnlyFuel("Test Plus Walker") shouldBe false
            intents.loyaltyIsOnlyFuel("Grizzly Bears") shouldBe false
        }

        for (loyalty in listOf(3, 5)) {
            test("the Jace token at $loyalty loyalty: passed before the flag, activated with it") {
                val game = walkerBoard("Jace", loyalty)

                val (before, beforeOptions) = choose(game, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
                withClue("reproduction: chose $before\n${beforeOptions.joinToString("\n")}") {
                    game.activates(before, "Jace") shouldBe false
                }

                for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_FUELLOYALTY, AiProfile.LIVE)) {
                    val (after, options) = choose(game, profile)
                    withClue("${profile.id} chose $after\n${options.joinToString("\n")}") {
                        game.activates(after, "Jace") shouldBe true
                    }
                }
            }
        }

        test("the Jace token at 1 loyalty: a −1 that kills it is still charged the walker") {
            val game = walkerBoard("Jace", 1)
            val (action, options) = choose(game, WITHOUT_SPEND)
            withClue("chose $action\n${options.joinToString("\n")}") {
                options.none { it.note?.contains("loyalty is only fuel") == true } shouldBe true
                game.activates(action, "Jace") shouldBe false
            }
        }

        for (loyalty in listOf(1, 2)) {
            test("the Jace token at $loyalty loyalty surveils instead of sitting on its loyalty") {
                val game = walkerBoard("Jace", loyalty)

                // At 1 the −1 is charged the whole walker. At 2 it is a tie with passing that the
                // playouts break either way (this seed surveils), so only 1 pins a reproduction.
                if (loyalty == 1) {
                    val (before, beforeOptions) = choose(game, WITHOUT_SPEND)
                    withClue("reproduction: chose $before\n${beforeOptions.joinToString("\n")}") {
                        game.activates(before, "Jace") shouldBe false
                    }
                }

                for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_FUELWALKERS, AiProfile.LIVE)) {
                    val (after, options) = choose(game, profile)
                    withClue("${profile.id} chose $after\n${options.joinToString("\n")}") {
                        game.jaceCost(after) shouldBe -1
                    }
                }
            }
        }

        for (loyalty in listOf(3, 4)) {
            test("the Jace token at $loyalty loyalty draws rather than surveils") {
                val game = walkerBoard("Jace", loyalty)
                val (action, options) = choose(game, AiProfile.LIVE)
                withClue("chose $action\n${options.joinToString("\n")}") {
                    game.jaceCost(action) shouldBe -3
                }
            }
        }

        test("the Jace token never draws from an empty library") {
            // "−3: Draw a card" with nothing left to draw loses the game (CR 704.5b), which the
            // drawn-card floor once priced as a card.
            val game = walkerBoard("Jace", 3, library = 0)
            val (action, options) = choose(game, AiProfile.LIVE)
            withClue("chose $action\n${options.joinToString("\n")}") {
                game.jaceCost(action) shouldNotBe -3
            }
        }

        test("the Jace token at 1 loyalty is held while the next attack would be lethal to us") {
            // At 2 life with nothing to block their Bears, the token is the only other thing the
            // attack can be pointed at; surveilling it away buys a look at one card.
            val game = walkerBoard("Jace", 1, ours = emptyList(), theirs = listOf("Grizzly Bears"), life = 2)
            val (action, options) = choose(game, AiProfile.LIVE)
            withClue("chose $action\n${options.joinToString("\n")}") {
                game.activates(action, "Jace") shouldBe false
            }
            // The same board at a safe life total spends it.
            val safe = walkerBoard("Jace", 1, ours = emptyList(), theirs = listOf("Grizzly Bears"), life = 20)
            val (spent, safeOptions) = choose(safe, AiProfile.LIVE)
            withClue("at 20 life chose $spent\n${safeOptions.joinToString("\n")}") {
                safe.jaceCost(spent) shouldBe -1
            }
        }

        test("a loyalty ability granted by Way of the Healer is spent when it is the best play") {
            // Jace at 2: "−1: Surveil 1" or the granted "−2: Create a 2/2 Cadet. Surveil 1."
            val game = walkerBoard("Jace", 2, ours = listOf("Way of the Healer"))
            val (action, options) = choose(game, AiProfile.LIVE)
            withClue("chose $action\n${options.joinToString("\n")}") {
                game.activates(action, "Jace") shouldBe true
                (action as ActivateAbility).abilityId.value.startsWith("Way of the Healer") shouldBe true
            }
        }

        test("a granted + ability makes the Jace token an engine, a granted minus does not") {
            val intents = IntentCatalog.of(cardRegistry)
            val healer = walkerBoard("Jace", 3, ours = listOf("Way of the Healer"))
            intents.loyaltyIsOnlyFuel(healer.state, healer.findPermanent("Jace")!!) shouldBe true
            intents.loyaltyAbilities(healer.state, healer.findPermanent("Jace")!!).size shouldBe 3

            val pyromancer = walkerBoard("Jace", 3, ours = listOf("Way of the Pyromancer"))
            intents.loyaltyIsOnlyFuel(pyromancer.state, pyromancer.findPermanent("Jace")!!) shouldBe false
        }

        test("a walker with a + ability gets no refund on its minus") {
            val game = walkerBoard("Test Plus Walker", 3)
            val (action, options) = choose(game, AiProfile.LIVE)
            withClue("chose $action\n${options.joinToString("\n")}") {
                options.none { it.note?.contains("loyalty is only fuel") == true } shouldBe true
            }
        }
    }

    private companion object {
        /**
         * The AI's lookahead runs on the game's RNG, which a scenario seeds from the clock. Passing
         * at 3 loyalty beats surveil by 0.3–1.1 depending on that seed, so an unpinned run can flip
         * the reproduction; pinning it keeps the before/after comparison about the flag alone.
         */
        const val RNG_SEED = 20261010L

        /** Everything live players face, minus [AiProfile.spendFuelWalkers]. */
        val WITHOUT_SPEND = AiProfile.LIVE.copy(id = "live-without-fuelwalkers", spendFuelWalkers = false)
    }
}
