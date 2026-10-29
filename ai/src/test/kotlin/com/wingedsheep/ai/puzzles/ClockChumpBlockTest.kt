package com.wingedsheep.ai.puzzles

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Chump blocks priced by the opponent's clock ([AiProfile.chumpForTheClock]).
 *
 * The shape comes from production game `5ae3888c` (2026-10-14, Reality Fracture draft), turn 14:
 * the AI at 12 life, a 10/9 attacking, a 6/4 menace on the other side of the table that would join
 * the next attack, and an untapped 2/1 that could have chumped. It took ten, went to 2, and needed
 * three bodies a turn from then on — the 10/9's chump and both halves of the menace pair — where
 * one chump now would have left it needing one. Rebuilt here at a smaller scale with vanilla
 * creatures: a 6/4 attacking at 8 life, a 6/5 menace held back.
 *
 * Seat 1 attacks, seat 2 is the AI declaring blockers.
 */
class ClockChumpBlockTest : ScenarioTestBase() {

    private companion object {
        /** `ScenarioBuilder` seeds itself from the clock otherwise; a regression test cannot. */
        const val SCENARIO_SEED = 20261014L

        /** Everything live players face, minus this change: the AI that took the ten. */
        val WITHOUT_CLOCK = AiProfile.LIVE.copy(id = "live-without-clock", chumpForTheClock = false)
    }

    private data class Piece(
        val name: String,
        val seat: Int,
        val tapped: Boolean = false,
        val summoningSick: Boolean = false,
    )

    private fun blocksFor(
        profile: AiProfile,
        board: List<Piece>,
        attackers: List<String>,
        defenderLife: Int,
        attackerLife: Int = 20,
    ): Map<String, List<String>> {
        var builder = scenario().withRngSeed(SCENARIO_SEED).withPlayers()
        board.forEach {
            builder = builder.withCardOnBattlefield(it.seat, it.name, tapped = it.tapped, summoningSickness = it.summoningSick)
        }
        val game = builder.withLifeTotal(2, defenderLife).withLifeTotal(1, attackerLife).build()
            .advanceToDeclaration(1, Step.DECLARE_ATTACKERS)

        val attackerIds = attackers.map { name ->
            game.findPermanents(name).first { game.state.projectedState.getController(it) == game.seatId(1) }
        }
        game.execute(DeclareAttackers(game.seatId(1), attackerIds.associateWith { game.seatId(2) }))
        game.advanceToDeclaration(2, Step.DECLARE_BLOCKERS)

        val action = AIPlayer.create(cardRegistry, game.seatId(2), profile).chooseAction(game.state)
        val blocks = (action as? DeclareBlockers)?.blockers.orEmpty()
        val name = { id: EntityId -> game.state.getEntity(id)!!.get<CardComponent>()!!.name }
        return blocks.filterValues { it.isNotEmpty() }
            .entries.associate { (blocker, blocked) -> name(blocker) to blocked.map(name) }
    }

    /**
     * The production board, scaled: our two other creatures attacked last turn (tapped, they untap
     * before the next attack), the menace creature came down this turn (it attacks next turn).
     */
    private fun productionShape(theirSickMenace: Boolean = true) = listOf(
        Piece("Craw Wurm", 1),
        Piece("Ripscale Predator", 1, summoningSick = theirSickMenace),
        Piece("Grizzly Bears", 2),
        Piece("Hill Giant", 2, tapped = true),
        Piece("Grizzly Bears", 2, tapped = true),
    )

    init {
        test("game 5ae3888c turn 14: chumps the big attacker rather than drop into three-chumps-a-turn range") {
            val blocks = blocksFor(AiProfile.LIVE, productionShape(), attackers = listOf("Craw Wurm"), defenderLife = 8)
            withClue("blocks were $blocks") {
                blocks.values.flatten() shouldBe listOf("Craw Wurm")
                blocks.size shouldBe 1
            }
        }

        test("the same board without the clock pass takes the hit — the production misplay") {
            val blocks = blocksFor(WITHOUT_CLOCK, productionShape(), attackers = listOf("Craw Wurm"), defenderLife = 8)
            withClue("blocks were $blocks") { blocks.shouldBeEmpty() }
        }

        test("far from the clock the hit is taken: no chump at 20 life") {
            val blocks = blocksFor(AiProfile.LIVE, productionShape(), attackers = listOf("Craw Wurm"), defenderLife = 20)
            withClue("blocks were $blocks") { blocks.shouldBeEmpty() }
        }

        test("a race it wins first keeps the bodies: the opponent is dead to our next attack") {
            // At 4 life with only the menace creature home to block, our three creatures swing for
            // lethal next turn — so take the six and keep the Bears to attack with.
            val blocks = blocksFor(
                AiProfile.LIVE, productionShape(theirSickMenace = false),
                attackers = listOf("Craw Wurm"), defenderLife = 8, attackerLife = 4,
            )
            withClue("blocks were $blocks") { blocks.shouldBeEmpty() }
        }

        test("no menace and enough blockers: the next attack is covered either way, so no chump") {
            // Taking the six leaves 6 life and one chump needed against the 6/4 next turn; chumping
            // now leaves none needed. Same body either way, so the clock does not pay for it now.
            val board = listOf(
                Piece("Craw Wurm", 1),
                Piece("Grizzly Bears", 2),
                Piece("Hill Giant", 2, tapped = true),
            )
            val blocks = blocksFor(AiProfile.LIVE, board, attackers = listOf("Craw Wurm"), defenderLife = 12)
            withClue("blocks were $blocks") { blocks.shouldBeEmpty() }
        }

        // ── Property: far from the clock, no chump ───────────────────────────────────────────

        test("when even an unblocked next attack cannot kill us after this hit, the AI never chumps") {
            val power = mapOf("Grizzly Bears" to 2, "Hill Giant" to 3, "Craw Wurm" to 6, "Ordinary Bear" to 4)
            val pool = power.keys.toList()
            val chumps = mutableListOf<String>()
            var checked = 0
            for (seed in listOf(20261014L, 99L)) {
                val rng = kotlin.random.Random(seed)
                repeat(60) { index ->
                    val attackerNames = List(rng.nextInt(1, 4)) { pool.random(rng) }
                    val homeNames = List(rng.nextInt(0, 3)) { pool.random(rng) }
                    val blockerNames = List(rng.nextInt(1, 4)) { pool.random(rng) }

                    // Far from the clock: after taking every point now, their whole board unblocked
                    // next turn still leaves us alive.
                    val theirPower = (attackerNames + homeNames).sumOf { power.getValue(it) }
                    val life = attackerNames.sumOf { power.getValue(it) } + theirPower + 1 + rng.nextInt(0, 4)

                    var builder = scenario().withRngSeed(SCENARIO_SEED).withPlayers()
                    attackerNames.forEach { builder = builder.withCardOnBattlefield(1, it) }
                    homeNames.forEach { builder = builder.withCardOnBattlefield(1, it, summoningSickness = true) }
                    blockerNames.forEach { builder = builder.withCardOnBattlefield(2, it) }
                    val game = builder.withLifeTotal(2, life).build()
                        .advanceToDeclaration(1, Step.DECLARE_ATTACKERS)
                    val attackerIds = game.state.getBattlefield().filter {
                        game.state.projectedState.getController(it) == game.seatId(1) &&
                            game.state.projectedState.isCreature(it) &&
                            !game.state.getEntity(it)!!.has<com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent>()
                    }
                    game.execute(DeclareAttackers(game.seatId(1), attackerIds.associateWith { game.seatId(2) }))
                    game.advanceToDeclaration(2, Step.DECLARE_BLOCKERS)
                    val state = game.state
                    val aiId = game.seatId(2)
                    if (state.pendingDecision != null || state.priorityPlayerId != aiId) return@repeat
                    checked++

                    val projected = state.projectedState
                    val blocks = (AIPlayer.create(cardRegistry, aiId, AiProfile.LIVE).chooseAction(state) as? DeclareBlockers)
                        ?.blockers.orEmpty()
                    val name = { id: EntityId -> state.getEntity(id)?.get<CardComponent>()?.name ?: id.value }
                    for ((blocker, blocked) in blocks) {
                        val attacker = blocked.singleOrNull() ?: continue
                        if (state.getEntity(attacker)?.has<AttackingComponent>() != true) continue
                        val gang = blocks.filterValues { attacker in it }.keys
                        val blockerDies = (projected.getPower(attacker) ?: 0) >= (projected.getToughness(blocker) ?: 0) && gang.size == 1
                        val attackerDies = gang.sumOf { projected.getPower(it) ?: 0 } >= (projected.getToughness(attacker) ?: 0)
                        if (blockerDies && !attackerDies) {
                            chumps += "seed=$seed[$index] at $life life: ${name(blocker)} chumped ${name(attacker)} " +
                                "(attackers $attackerNames, home $homeNames, blockers $blockerNames)"
                        }
                    }
                }
            }
            println("clock-chump sweep: $checked far-from-clock positions, ${chumps.size} chumps")
            chumps.shouldBeEmpty()
        }
    }
}
