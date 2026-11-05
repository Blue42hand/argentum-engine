package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiActionOption
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.puzzles.advanceToDeclaration
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityCost
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * [AiProfile.defendPlaneswalkers]: our own planeswalkers priced by what the next attack leaves
 * of them ([PlaneswalkerThreat.boardValue]), spent before they die, and blocked for when they are
 * worth more than the body.
 *
 * The production shape is game `2e28f192` (2026-10-14, Reality Fracture draft) turn 8: the AI's
 * Jace token at 3 loyalty, no creature of its own, the opponent's Unflinching Hortimancer (2/1)
 * untapped and a Blessed Ghoul (1/1 lifelink) that would be able to attack next turn. The AI used
 * −1 (surveil), leaving Jace at 2, and the next attack killed it with two counters unspent.
 */
class DefendPlaneswalkersTest : ScenarioTestBase() {

    private companion object {
        const val RNG_SEED = 20261014L

        /**
         * Everything live players face, minus this change — and minus [AiProfile.spendFuelWalkers],
         * which prices a minus-only walker's activations on its own terms and would hide it.
         */
        val WITHOUT = AiProfile.LIVE.copy(
            id = "live-without-walkerdefence", defendPlaneswalkers = false, spendFuelWalkers = false,
        )
    }

    private fun TestGame.withLoyalty(cardName: String, loyalty: Int) {
        val id = findPermanent(cardName)!!
        state = state.updateEntity(id) { c ->
            c.with((c.get<CountersComponent>() ?: CountersComponent()).withCounters(CounterType.LOYALTY, loyalty))
        }
    }

    private fun TestGame.nameOf(id: EntityId): String =
        state.getEntity(id)?.get<CardComponent>()?.name ?: id.toString()

    // ── Loyalty use ──────────────────────────────────────────────────────────────────────────

    /** The AI (seat 1) in its precombat main with a Jace token at [loyalty]; [theirs] on the other side. */
    private fun jaceBoard(loyalty: Int, theirs: List<Pair<String, Boolean>>, ours: List<String> = emptyList()): TestGame {
        val game = scenario().withPlayers()
            .withRngSeed(RNG_SEED)
            .withCardOnBattlefield(1, "Jace", isToken = true)
            .apply { ours.forEach { withCardOnBattlefield(1, it) } }
            .withLandsOnBattlefield(1, "Island", 4)
            .apply { theirs.forEach { (name, sick) -> withCardOnBattlefield(2, name, summoningSickness = sick) } }
            .withLandsOnBattlefield(2, "Plains", 4)
            .apply { repeat(4) { withCardInHand(1, "Craw Wurm") } }
            .apply { repeat(8) { withCardInLibrary(1, if (it % 2 == 0) "Island" else "Grizzly Bears") } }
            .apply { repeat(8) { withCardInLibrary(2, "Plains") } }
            .build()
        game.withLoyalty("Jace", loyalty)
        return game.advanceToPriority(1, Step.PRECOMBAT_MAIN)
    }

    private fun choose(game: TestGame, profile: AiProfile): Pair<GameAction, List<AiActionOption>> {
        var options = emptyList<AiActionOption>()
        val sink = AiInsightSink { _, insight -> if (insight.options.size > 1) options = insight.options }
        val action = AIPlayer.create(cardRegistry, game.player1Id, profile, insightSink = sink).chooseAction(game.state)
        return action to options
    }

    /** The loyalty change of the Jace ability [action] activates, or null when it activates none. */
    private fun TestGame.jaceCost(action: GameAction): Int? {
        if (action !is ActivateAbility || action.sourceId != findPermanent("Jace")) return null
        val ability = cardRegistry.getCard("Jace")!!.script.activatedAbilities.first { it.id == action.abilityId }
        return (ability.cost as AbilityCost.Loyalty).change
    }

    // ── Blocking ─────────────────────────────────────────────────────────────────────────────

    /**
     * Seat 1 attacks seat 2's planeswalker [walker] (at [loyalty]) with [attackers]; seat 2, the AI,
     * has [blockers] and declares blocks. Returns blocker name → blocked attacker names.
     */
    private fun blocksFor(
        profile: AiProfile,
        walker: String,
        loyalty: Int,
        attackers: List<String>,
        blockers: List<String>,
        defenderLife: Int = 20,
    ): Map<String, List<String>> {
        var builder = scenario().withRngSeed(RNG_SEED).withPlayers()
            .withCardOnBattlefield(2, walker, isToken = walker == "Jace")
        attackers.forEach { builder = builder.withCardOnBattlefield(1, it) }
        blockers.forEach { builder = builder.withCardOnBattlefield(2, it) }
        val game = builder.withLifeTotal(2, defenderLife)
            .apply { repeat(6) { withCardInLibrary(1, "Island") } }
            .apply { repeat(6) { withCardInLibrary(2, "Island") } }
            .build()
        game.withLoyalty(walker, loyalty)
        game.advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
        val walkerId = game.findPermanent(walker)!!
        val attackerIds = attackers.map { name ->
            game.findPermanents(name).first { game.state.projectedState.getController(it) == game.player1Id }
        }
        game.execute(DeclareAttackers(game.player1Id, attackerIds.associateWith { walkerId }))
        game.advanceToDeclaration(2, Step.DECLARE_BLOCKERS)

        val action = AIPlayer.create(cardRegistry, game.player2Id, profile).chooseAction(game.state)
        val blocks = (action as? DeclareBlockers)?.blockers.orEmpty()
        return blocks.filterValues { it.isNotEmpty() }
            .entries.associate { (blocker, blocked) -> game.nameOf(blocker) to blocked.map { game.nameOf(it) } }
    }

    init {
        test("production 2e28f192 turn 8: a Jace the next attack kills cashes in −3 rather than surveil") {
            val game = jaceBoard(3, theirs = listOf("Unflinching Hortimancer" to false, "Blessed Ghoul" to true))

            val (before, beforeOptions) = choose(game, WITHOUT)
            withClue("reproduction chose $before\n${beforeOptions.joinToString("\n")}") {
                game.jaceCost(before) shouldBe -1
            }

            for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_WALKERDEFENCE, AiProfile.LIVE)) {
                val (after, options) = choose(game, profile)
                withClue("${profile.id} chose $after\n${options.joinToString("\n")}") {
                    game.jaceCost(after) shouldBe -3
                }
            }
        }

        test("game 5ae3888c: a Jace at 1 loyalty the next attack kills is no longer charged for spending it") {
            // Surveil itself moves no evaluated number here, so what matters is the charge: before,
            // the −1 cost the whole walker; now the walker is about to be lost either way.
            val game = jaceBoard(1, theirs = listOf("Unflinching Hortimancer" to false))
            fun surveil(options: List<AiActionOption>) = options.first { it.label.startsWith("-1") }

            val (_, beforeOptions) = choose(game, WITHOUT)
            withClue("reproduction\n${beforeOptions.joinToString("\n")}") {
                (surveil(beforeOptions).advantage!! < 0.0) shouldBe true
            }
            val (_, options) = choose(game, AiProfile.LIVE)
            withClue(options.joinToString("\n")) {
                (surveil(options).advantage!! >= 0.0) shouldBe true
            }
        }

        test("a Jace a blocker keeps safe is not cashed in") {
            // The Bears stand in front of the Hortimancer: nothing reaches Jace, so the attack gives
            // no reason to −3. (LIVE's `spendFuelWalkers` draws with it anyway — a minus-only
            // walker's loyalty has nothing else to be saved for — so this pins the walker pricing
            // without it; `LoyaltyActivationAiTest` pins that.)
            val game = jaceBoard(3, theirs = listOf("Unflinching Hortimancer" to false), ours = listOf("Grizzly Bears"))
            val (action, options) = choose(game, AiProfile.LIVE.copy(spendFuelWalkers = false))
            withClue("chose $action\n${options.joinToString("\n")}") {
                game.jaceCost(action) shouldNotBe -3
            }
        }

        test("chumps to save a planeswalker worth more than the body") {
            // Garruk at 4 is an engine with its −3 in reach; a Savannah Lions is a 2/1.
            val board = { profile: AiProfile ->
                blocksFor(profile, "Garruk, Veiled Butcher", 4, attackers = listOf("Hill Giant"), blockers = listOf("Savannah Lions"))
            }
            withClue("reproduction") { board(WITHOUT).shouldBeEmpty() }
            val blocks = board(AiProfile.LIVE)
            withClue("blocks were $blocks") { blocks["Savannah Lions"] shouldBe listOf("Hill Giant") }
        }

        test("trades to save a planeswalker") {
            val board = { profile: AiProfile ->
                blocksFor(profile, "Garruk, Veiled Butcher", 3, attackers = listOf("Grizzly Bears"), blockers = listOf("Grizzly Bears"))
            }
            val blocks = board(AiProfile.LIVE)
            withClue("blocks were $blocks") { blocks.values.flatten() shouldContain "Grizzly Bears" }
        }

        test("does not throw a good creature away for a near-dead Jace token") {
            // Jace at 1 can surveil once more; the Hill Giant would die to the Craw Wurm for it.
            val blocks = blocksFor(
                AiProfile.LIVE, "Jace", 1,
                attackers = listOf("Craw Wurm"), blockers = listOf("Hill Giant"),
            )
            withClue("blocks were $blocks") { blocks.shouldBeEmpty() }
        }
    }
}
