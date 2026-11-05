package com.wingedsheep.ai.engine

import com.wingedsheep.ai.puzzles.advanceToDeclaration
import com.wingedsheep.ai.puzzles.seatId
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan

/**
 * When the blocks still let lethal through, the survival pass stacks chump blockers on a trampler
 * ([AiProfile.stackChumpsOnTramplers]).
 *
 * Production game `359c26af` (2026-10-15) turn 19: at 9 life against a 7/6, a 6/8 flier and an
 * 8/8 trampler, holding ten untapped creatures, the AI put one body in front of the trampler and
 * died to 7 trample + 6 in the air. Rebuilt with vanilla creatures: a 7/7 trampler (Duskdale
 * Wurm), a 6/4 (Craw Wurm) and a 2/2 flier (Wind Drake) attacking into four Grizzly Bears at 6
 * life. One Bear on each Wurm lets 5 + 2 through; a second Bear behind the trampler leaves 3 + 2.
 *
 * Seat 1 attacks, seat 2 is the AI declaring blockers.
 */
class StackChumpsOnTramplersTest : ScenarioTestBase() {

    private companion object {
        const val SCENARIO_SEED = 20261015L
        val WITHOUT = AiProfile.LIVE.copy(id = "live-without-stacking", stackChumpsOnTramplers = false)
        val ATTACKERS = listOf("Duskdale Wurm", "Craw Wurm", "Wind Drake")
        val POWER = mapOf("Duskdale Wurm" to 7, "Craw Wurm" to 6, "Wind Drake" to 2)
    }

    /** The declared blocks as blocker id → attacker name, with the blocker toughness on the side. */
    private fun blocksFor(profile: AiProfile, life: Int, blockers: Int): List<Pair<Int, String>> {
        var builder = scenario().withRngSeed(SCENARIO_SEED).withPlayers()
        ATTACKERS.forEach { builder = builder.withCardOnBattlefield(1, it) }
        repeat(blockers) { builder = builder.withCardOnBattlefield(2, "Grizzly Bears") }
        val game = builder.withLifeTotal(2, life).build().advanceToDeclaration(1, Step.DECLARE_ATTACKERS)

        val attackerIds = ATTACKERS.map { name -> game.findPermanents(name).single() }
        game.execute(DeclareAttackers(game.seatId(1), attackerIds.associateWith { game.seatId(2) }))
        game.advanceToDeclaration(2, Step.DECLARE_BLOCKERS)

        val action = AIPlayer.create(cardRegistry, game.seatId(2), profile).chooseAction(game.state)
        val name = { id: EntityId -> game.state.getEntity(id)!!.get<CardComponent>()!!.name }
        return (action as? DeclareBlockers)?.blockers.orEmpty()
            .filterValues { it.isNotEmpty() }
            .map { (blocker, blocked) -> (game.state.projectedState.getToughness(blocker) ?: 0) to name(blocked.single()) }
    }

    /** Damage the vanilla attackers deal through [blocks]: unblocked power, plus trample spill. */
    private fun damageThrough(blocks: List<Pair<Int, String>>): Int = ATTACKERS.sumOf { attacker ->
        val on = blocks.filter { it.second == attacker }
        when {
            on.isEmpty() -> POWER.getValue(attacker)
            attacker == "Duskdale Wurm" -> (POWER.getValue(attacker) - on.sumOf { it.first }).coerceAtLeast(0)
            else -> 0
        }
    }

    init {
        test("game 359c26af turn 19: two bodies behind the trampler, and the AI survives") {
            val blocks = blocksFor(AiProfile.LIVE, life = 6, blockers = 4)
            withClue("blocks were $blocks") {
                damageThrough(blocks) shouldBeLessThan 6
                blocks.count { it.second == "Duskdale Wurm" } shouldBeGreaterThanOrEqual 2
            }
        }

        test("without stacking the same board dies — the production misplay") {
            val blocks = blocksFor(WITHOUT, life = 6, blockers = 4)
            withClue("blocks were $blocks") { damageThrough(blocks) shouldBeGreaterThanOrEqual 6 }
        }
    }
}
