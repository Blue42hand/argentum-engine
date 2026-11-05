package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiActionOption
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.puzzles.advanceToDeclaration
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * [AiProfile.attackPlaneswalkers]: each attacker picks its defender, and an opposing planeswalker
 * is priced as the ongoing engine it is ([PlaneswalkerThreat]) against the life the damage would
 * otherwise take.
 *
 * The reproduction is production game `8522de5e` (2026-10-14, Reality Fracture draft) turn 16,
 * rebuilt as a minimal position: Traxos was the AI's only attacker, the opponent had no creature
 * (Unsummon had just bounced it), 22 life, Garruk, Veiled Butcher at 4 loyalty and a Jace token at
 * 2. The AI sent Traxos at the player.
 */
class AttackPlaneswalkersTest : ScenarioTestBase() {

    private fun TestGame.withLoyalty(cardName: String, loyalty: Int) {
        val id = findPermanent(cardName)!!
        state = state.updateEntity(id) { c ->
            c.with((c.get<CountersComponent>() ?: CountersComponent()).withCounters(CounterType.LOYALTY, loyalty))
        }
    }

    /** The plans the last [declared] call scored, for failure messages. */
    private var lastOptions: List<AiActionOption> = emptyList()

    /** The attack [profile] declares from [game], attacker → defender. */
    private fun declared(game: TestGame, profile: AiProfile): Map<EntityId, EntityId> {
        val sink = AiInsightSink { _, insight -> lastOptions = insight.options }
        return AIPlayer.create(cardRegistry, game.player1Id, profile, insightSink = sink).chooseAction(game.state)
            .shouldBeInstanceOf<DeclareAttackers>().attackers
    }

    private fun TestGame.describe(plan: Map<EntityId, EntityId>): String =
        plan.entries.joinToString { (a, d) -> "${nameOf(a)} -> ${if (d == player2Id) "player" else nameOf(d)}" }

    private fun TestGame.nameOf(id: EntityId): String =
        state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name ?: id.toString()

    /**
     * The opponent at [life] with [walkers] (name to loyalty) and no creature unless [blockers]
     * names some; the AI attacks with [attackers]. Opponent library and hand are padded so nothing
     * decks out on the way to combat.
     */
    private fun position(
        attackers: List<String>,
        walkers: List<Pair<String, Int>>,
        life: Int,
        blockers: List<String> = emptyList(),
    ): TestGame {
        val game = scenario().withPlayers()
            .withRngSeed(RNG_SEED)
            .withLifeTotal(1, 14)
            .withLifeTotal(2, life)
            .apply { attackers.forEach { withCardOnBattlefield(1, it) } }
            .withLandsOnBattlefield(1, "Island", 7)
            .apply { walkers.forEach { (name, _) -> withCardOnBattlefield(2, name, isToken = name == "Jace") } }
            .apply { blockers.forEach { withCardOnBattlefield(2, it) } }
            .withLandsOnBattlefield(2, "Swamp", 6)
            .apply { repeat(6) { withCardInLibrary(1, "Island") } }
            .apply { repeat(6) { withCardInLibrary(2, "Swamp") } }
            .build()
        walkers.forEach { (name, loyalty) -> game.withLoyalty(name, loyalty) }
        return game.advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
    }

    init {
        test("production 8522de5e turn 16: Traxos goes at a planeswalker, not the player on 22") {
            val game = position(
                attackers = listOf("Traxos, Academy Guardian"),
                walkers = listOf("Garruk, Veiled Butcher" to 4, "Jace" to 2),
                life = 22,
            )
            val traxos = game.findPermanent("Traxos, Academy Guardian")!!
            val walkers = listOf(game.findPermanent("Garruk, Veiled Butcher")!!, game.findPermanent("Jace")!!)

            val before = declared(game, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
            withClue("reproduction: ${game.describe(before)}") {
                before[traxos] shouldBe game.player2Id
            }

            for (profile in listOf(AiProfile.PRODUCTION_CANDIDATE_WALKERATTACKS, AiProfile.LIVE)) {
                val after = declared(game, profile)
                withClue("${profile.id}: ${game.describe(after)}") {
                    after[traxos].shouldBeIn(walkers)
                }
            }
        }

        test("kills a low-loyalty planeswalker rather than hitting a healthy player") {
            val game = position(
                attackers = listOf("Wind Drake"),
                walkers = listOf("Garruk, Veiled Butcher" to 2),
                life = 20,
            )
            val plan = declared(game, AiProfile.LIVE)
            withClue(game.describe(plan) + "\n" + lastOptions.joinToString("\n")) {
                plan[game.findPermanent("Wind Drake")!!] shouldBe game.findPermanent("Garruk, Veiled Butcher")
            }
        }

        test("goes face when the attack is lethal") {
            val game = position(
                attackers = listOf("Wind Drake"),
                walkers = listOf("Garruk, Veiled Butcher" to 2),
                life = 2,
            )
            val plan = declared(game, AiProfile.LIVE)
            withClue(game.describe(plan) + "\n" + lastOptions.joinToString("\n")) {
                plan[game.findPermanent("Wind Drake")!!] shouldBe game.player2Id
            }
        }

        test("keeps the damage on the player when the race is nearly won") {
            // Two damage takes the opponent from 4 to 2 — worth more, by the life curve, than
            // ending a Jace token that can only surveil twice.
            val game = position(
                attackers = listOf("Wind Drake"),
                walkers = listOf("Jace" to 2),
                life = 4,
            )
            val plan = declared(game, AiProfile.LIVE)
            withClue(game.describe(plan) + "\n" + lastOptions.joinToString("\n")) {
                plan[game.findPermanent("Wind Drake")!!] shouldBe game.player2Id
            }
        }

        test("splits the attack: the attacker that exactly finishes the walker goes at it, the big one face") {
            val game = position(
                attackers = listOf("Serra Angel", "Wind Drake"),
                walkers = listOf("Garruk, Veiled Butcher" to 2),
                life = 20,
            )
            val plan = declared(game, AiProfile.LIVE)
            withClue(game.describe(plan) + "\n" + lastOptions.joinToString("\n")) {
                plan[game.findPermanent("Wind Drake")!!] shouldBe game.findPermanent("Garruk, Veiled Butcher")
                plan[game.findPermanent("Serra Angel")!!] shouldBe game.player2Id
            }
        }
    }

    private companion object {
        const val RNG_SEED = 20261014L
    }
}
