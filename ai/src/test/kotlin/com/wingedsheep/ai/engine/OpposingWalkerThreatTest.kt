package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiActionOption
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.puzzles.advanceToDeclaration
import com.wingedsheep.ai.puzzles.advanceToPriority
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.NumberChosenResponse
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * [AiProfile.walkersPricedAsEngines]: an opposing planeswalker is priced by what it keeps doing —
 * its + abilities (granted ones included) and how close it is to its ultimate — and a minus-only
 * Jace token by the few activations its loyalty buys, so the AI pressures the walker that is
 * actually winning the game.
 *
 * Off the 2026-10-15 production games: `52f175b0` turns 15–21, The Theorist, Jace Beleren climbed
 * to its −6 without being attacked; `4b240f7a` turn 14, No Admittance hit a Jace token instead of
 * The Theorist; `322ed69b` turn 19, Wrath of the Bloodmane hit a Jace token instead of the
 * opponent's creature; `c034dbb9` turn 14, Mabel's "remove up to three counters" removed none
 * from The Theorist at 1 loyalty. The ultimate and burn cases reproduce on the profile without the
 * flag; the others are guards the new pricing must keep.
 */
class OpposingWalkerThreatTest : ScenarioTestBase() {

    private val before = AiProfile.LIVE.copy(id = "live-without-walker-engines", walkersPricedAsEngines = false)
    private val after = AiProfile.LIVE

    private fun TestGame.withLoyalty(cardName: String, loyalty: Int) {
        val id = findPermanent(cardName)!!
        state = state.updateEntity(id) { c ->
            c.with((c.get<CountersComponent>() ?: CountersComponent()).withCounters(CounterType.LOYALTY, loyalty))
        }
    }

    private fun TestGame.nameOf(id: EntityId?): String =
        if (id == player2Id) "player" else id?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "nothing"

    private var lastOptions: List<AiActionOption> = emptyList()

    /** The AI (seat 1) attacking with [attackers] into an empty opposing board holding [walkers]. */
    private fun attackPosition(attackers: List<String>, walkers: List<Pair<String, Int>>, life: Int = 20): TestGame {
        val game = scenario().withPlayers()
            .withRngSeed(RNG_SEED)
            .withLifeTotal(2, life)
            .apply { attackers.forEach { withCardOnBattlefield(1, it) } }
            .withLandsOnBattlefield(1, "Island", 5)
            .apply { walkers.forEach { (name, _) -> withCardOnBattlefield(2, name, isToken = name == "Jace") } }
            .withLandsOnBattlefield(2, "Island", 5)
            .apply { repeat(6) { withCardInLibrary(1, "Island") } }
            .apply { repeat(6) { withCardInLibrary(2, "Island") } }
            .build()
        walkers.forEach { (name, loyalty) -> game.withLoyalty(name, loyalty) }
        return game.advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
    }

    private fun declared(game: TestGame, profile: AiProfile): Map<EntityId, EntityId> {
        val sink = AiInsightSink { _, insight -> lastOptions = insight.options }
        return AIPlayer.create(cardRegistry, game.player1Id, profile, insightSink = sink).chooseAction(game.state)
            .shouldBeInstanceOf<DeclareAttackers>().attackers
    }

    /** The AI (seat 1) in its main phase holding [spell], the opponent with [theirs] and [walkers]. */
    private fun removalPosition(
        spell: String,
        land: String,
        walkers: List<Pair<String, Int>>,
        theirs: List<String> = emptyList(),
    ): TestGame {
        val game = scenario().withPlayers()
            .withRngSeed(RNG_SEED)
            .withCardInHand(1, spell)
            .withLandsOnBattlefield(1, land, 4)
            .apply { walkers.forEach { (name, _) -> withCardOnBattlefield(2, name, isToken = name == "Jace") } }
            .apply { theirs.forEach { withCardOnBattlefield(2, it) } }
            .withLandsOnBattlefield(2, "Island", 5)
            .apply { repeat(6) { withCardInLibrary(1, land) } }
            .apply { repeat(6) { withCardInLibrary(2, "Island") } }
            .build()
        walkers.forEach { (name, loyalty) -> game.withLoyalty(name, loyalty) }
        return game.advanceToPriority(1, Step.PRECOMBAT_MAIN)
    }

    /** The permanent [profile] aims [spell] at, given casting it as its only choice. */
    private fun removalTarget(game: TestGame, spell: String, profile: AiProfile): EntityId? {
        val cast = LegalActionEnumerator.create(cardRegistry).enumerate(game.state, game.player1Id).first {
            (it.action as? CastSpell)?.let { c -> game.state.getEntity(c.cardId)?.get<CardComponent>()?.name == spell } == true
        }
        val chosen = AIPlayer.create(cardRegistry, game.player1Id, profile).chooseFrom(game.state, listOf(cast))
            .action.shouldBeInstanceOf<CastSpell>()
        return (chosen.targets.singleOrNull() as? ChosenTarget.Permanent)?.entityId
    }

    init {
        test("an attack hits the walker one step from its ultimate, not a Jace token it could kill") {
            // 52f175b0: The Theorist at 5 makes its −6 the turn after next; the token at 3 is one
            // card. Three damage at The Theorist sets the ultimate back four turns.
            val game = attackPosition(
                attackers = listOf("Hill Giant"),
                walkers = listOf("The Theorist, Jace Beleren" to 5, "Jace" to 3),
            )
            val giant = game.findPermanent("Hill Giant")!!
            val theorist = game.findPermanent("The Theorist, Jace Beleren")!!

            val old = declared(game, before)
            withClue("reproduction: ${game.nameOf(old[giant])}") { (old[giant] == theorist) shouldBe false }

            val plan = declared(game, after)
            withClue(game.nameOf(plan[giant]) + "\n" + lastOptions.joinToString("\n")) {
                plan[giant] shouldBe theorist
            }
        }

        test("an attack that can kill either walker kills the engine, not the bigger Jace token") {
            val game = attackPosition(
                attackers = listOf("Craw Wurm"),
                walkers = listOf("The Theorist, Jace Beleren" to 3, "Jace" to 5),
            )
            val wurm = game.findPermanent("Craw Wurm")!!
            val plan = declared(game, after)
            withClue(game.nameOf(plan[wurm]) + "\n" + lastOptions.joinToString("\n")) {
                plan[wurm] shouldBe game.findPermanent("The Theorist, Jace Beleren")
            }
        }

        test("burn goes at the walker climbing to its ultimate, not the Jace token it can kill") {
            // 4b240f7a turn 14: The Theorist cast at 3 and +1'd to 4, a Jace token beside it; No
            // Admittance (3 damage to any target) killed the token. Three damage at The Theorist
            // takes its −6 from two turns out to five.
            val game = removalPosition(
                spell = "No Admittance", land = "Mountain",
                walkers = listOf("The Theorist, Jace Beleren" to 4, "Jace" to 3),
            )
            val theorist = game.findPermanent("The Theorist, Jace Beleren")
            val old = removalTarget(game, "No Admittance", before)
            withClue("reproduction: ${game.nameOf(old)}") { old shouldBe game.findPermanent("Jace") }
            val new = removalTarget(game, "No Admittance", after)
            withClue("after: ${game.nameOf(new)}") { new shouldBe theorist }
        }

        test("removal goes at the creature, not a Jace token under a Way") {
            // 322ed69b turn 19: Wrath of the Bloodmane at a Jace token (Way of the Cryomancer's
            // granted −3 on it) over Divining Duelist. A guard: the granted minus must not make the
            // token read as an engine.
            val built = scenario().withPlayers()
                .withRngSeed(RNG_SEED)
                .withCardInHand(1, "Wrath of the Bloodmane")
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardOnBattlefield(2, "Jace", isToken = true)
                .withCardOnBattlefield(2, "Way of the Cryomancer")
                .withCardOnBattlefield(2, "Divining Duelist")
                .withLandsOnBattlefield(2, "Island", 5)
                .apply { repeat(6) { withCardInLibrary(1, "Mountain") } }
                .apply { repeat(6) { withCardInLibrary(2, "Island") } }
                .build()
            built.withLoyalty("Jace", 4)
            val game = built.advanceToPriority(1, Step.PRECOMBAT_MAIN)
            val new = removalTarget(game, "Wrath of the Bloodmane", after)
            withClue("after: ${game.nameOf(new)}") { new shouldBe game.findPermanent("Divining Duelist") }
        }

        test("Mabel removes the last loyalty counter from an opposing walker") {
            // c034dbb9 turn 14, rebuilt from the log: "Remove how many loyalty counters from The
            // Theorist? (0-1)" was answered 0. The rebuilt board answers 1 with and without the
            // flag (the production position's hidden zones are not in the log), so this pins it:
            // the kill must stay worth more than the 1-loyalty walker it leaves.
            val built = scenario().withPlayers()
                .withRngSeed(RNG_SEED)
                .withLifeTotal(1, 15)
                .withLifeTotal(2, 18)
                .withCardInHand(1, "Mabel, Bitter Recluse")
                .withCardOnBattlefield(1, "Blessed Ghoul")
                .withCardOnBattlefield(1, "Living Library")
                .withCardOnBattlefield(1, "Jace", isToken = true)
                .withCardOnBattlefield(1, "Void Extrapolator")
                .withCardOnBattlefield(1, "Hexhaven Invigorator")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardOnBattlefield(2, "Void Extrapolator")
                .withCardOnBattlefield(2, "Mabel, Bitter Recluse")
                .withCardOnBattlefield(2, "The Theorist, Jace Beleren")
                .withLandsOnBattlefield(2, "Island", 7)
                .apply { repeat(6) { withCardInLibrary(1, "Swamp") } }
                .apply { repeat(6) { withCardInLibrary(2, "Island") } }
                .apply { repeat(4) { withCardInHand(2, "Island") } }
                .build()
            built.withLoyalty("Jace", 2)
            built.withLoyalty("The Theorist, Jace Beleren", 1)
            val game = built.advanceToPriority(1, Step.PRECOMBAT_MAIN)
            for (profile in listOf(before, after)) {
                val g = game
                val saved = g.state
                val ai = AIPlayer.create(cardRegistry, g.player1Id, profile)
                g.castSpell(1, "Mabel, Bitter Recluse")
                g.resolveStack()
                var answered: Int? = null
                var guard = 0
                while (guard++ < 20) {
                    val decision = g.state.pendingDecision
                    if (decision != null) {
                        val response = ai.respondToDecision(g.state, decision)
                        if (decision is ChooseNumberDecision) answered = (response as NumberChosenResponse).number
                        g.execute(SubmitDecision(decision.playerId, response))
                        continue
                    }
                    if (g.state.stack.isEmpty()) break
                    g.resolveStack()
                }
                withClue("${profile.id}: chose $answered") {
                    if (profile == after) {
                        answered shouldBe 1
                        g.findPermanent("The Theorist, Jace Beleren") shouldBe null
                    }
                }
                g.state = saved
            }
        }
    }

    private companion object {
        const val RNG_SEED = 20261015L
    }
}
