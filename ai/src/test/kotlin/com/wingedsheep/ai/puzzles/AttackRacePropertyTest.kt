package com.wingedsheep.ai.puzzles

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * Two properties of the live attack, swept over randomly generated boards where **every opposing
 * creature is tapped** — nothing blocks this combat, so the attack's damage is exactly its power
 * and the only thing an attacker gives up is its block on the swing back:
 *
 * 1. **No avoidable death.** When keeping everything home survives the crack-back, and the attack
 *    does not kill, the attack the AI declares survives it too.
 * 2. **Vigilance is free.** When attacking with everything costs nothing on the swing back — our
 *    vigilant creatures alone hold every one of theirs — no vigilant creature is left home: it
 *    deals its damage and is still a blocker afterwards, so leaving it home is dominated outright.
 *    (Only on those boards: elsewhere the search may restart from an empty attack and run out of
 *    iterations before it adds the 1-power creature back, which is a budget question, not this one.)
 *
 * Vanilla creatures (plus one vigilant 1/4), which is what keeps the oracle exact: with no evasion
 * on either side every creature we keep home can block any one attacker of theirs and soaks its
 * whole hit, so the least the crack-back can deal is `their total power − their N biggest powers`
 * for N creatures at home. The positions stay as cheap to check as
 * [BlockSurvivalPropertyTest]'s, and as nobody's choice.
 *
 * Seeds are pinned: a CI gate cannot be seeded from the clock.
 */
class AttackRacePropertyTest : ScenarioTestBase() {

    private companion object {
        const val SCENARIO_SEED = 20261016L
        const val VIGILANT = "Yotian Soldier"
    }

    init {
        val ourPool = listOf("Grizzly Bears", "Hill Giant", "Craw Wurm", "Pensive Minotaur", VIGILANT)
        val theirPool = listOf("Grizzly Bears", "Hill Giant", "Craw Wurm", "Pensive Minotaur")
        val seeds = listOf(20261016L, 9001L)
        val positionsPerSeed = 80

        test("the live AI never attacks into an avoidable lethal crack-back, and swings a vigilant creature when it is free") {
            val failures = mutableListOf<String>()
            var checked = 0

            for (seed in seeds) {
                val rng = kotlin.random.Random(seed)
                repeat(positionsPerSeed) { index ->
                    val ourNames = List(rng.nextInt(1, 5)) { ourPool.random(rng) }
                    val theirNames = List(rng.nextInt(1, 5)) { theirPool.random(rng) }
                    val ourLife = rng.nextInt(2, 15)
                    val theirLife = rng.nextInt(4, 21)

                    var builder = scenario().withRngSeed(SCENARIO_SEED).withPlayers()
                        .withLifeTotal(1, ourLife)
                        .withLifeTotal(2, theirLife)
                    ourNames.forEach { builder = builder.withCardOnBattlefield(1, it) }
                    theirNames.forEach { builder = builder.withCardOnBattlefield(2, it, tapped = true) }
                    val game = builder.build().advanceToDeclaration(1, Step.DECLARE_ATTACKERS)

                    val state = game.state
                    val aiId = game.seatId(1)
                    // A mis-built position scores as a pass otherwise. Skip rather than measure it.
                    if (state.pendingDecision != null || state.priorityPlayerId != aiId) return@repeat
                    val projected = state.projectedState
                    val ours = projected.getBattlefieldControlledBy(aiId).filter { projected.isCreature(it) }
                    val theirPowers = projected.getBattlefieldControlledBy(game.seatId(2))
                        .filter { projected.isCreature(it) }
                        .map { projected.getPower(it) ?: 0 }
                        .sortedDescending()
                    val crackBack = { home: Int -> theirPowers.drop(home).sum() }
                    val name = { id: EntityId -> state.getEntity(id)?.get<CardComponent>()?.name ?: id.value }

                    val action = AIPlayer.create(cardRegistry, aiId, AiProfile.LIVE).chooseAction(state)
                    val attackers = (action as? DeclareAttackers)?.attackers?.keys.orEmpty()
                    val dealt = attackers.sumOf { projected.getPower(it) ?: 0 }
                    val home = ours.count { it !in attackers || name(it) == VIGILANT }
                    checked++

                    val where = "seed=$seed[$index] at $ourLife life vs $theirLife — ours ${ourNames}, " +
                        "theirs (tapped) $theirNames; attacked with ${attackers.map(name)}"
                    if (dealt < theirLife && crackBack(ours.size) < ourLife && crackBack(home) >= ourLife) {
                        failures += "$where: the swing back deals ${crackBack(home)}, holding back survives"
                    }
                    val vigilant = ours.filter { name(it) == VIGILANT }
                    val idleVigilant = vigilant.filter { it !in attackers }
                    if (crackBack(vigilant.size) == 0 && idleVigilant.isNotEmpty()) {
                        failures += "$where: left ${idleVigilant.size} vigilant creature(s) home"
                    }
                }
            }

            println("attack-race sweep: $checked positions, ${failures.size} failures")
            failures.shouldBeEmpty()
        }
    }
}
