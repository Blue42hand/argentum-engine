package com.wingedsheep.ai.engine

import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Who is the beatdown: how far one player's position dominates one opponent's, as a single
 * continuous number in `[-1, 1]` — positive when we are ahead, negative when behind, zero at
 * parity.
 *
 * "Who's the beatdown" is the oldest piece of Magic strategy theory: the player whose position wins
 * the long game can afford to wait, and the player who is ahead *now* should convert before the other
 * side's draw steps find an answer. The static evaluator cannot see that — it scores one leaf, and an
 * attack leaf and a pass leaf sit at the same point in time, so the opponent's next draw is free in
 * both. This is the reading [CombatAdvisor]'s pressure term ([AiProfile.pressWhenAhead]) scales by.
 *
 * Two halves, averaged, each already a share in `[-1, 1]`:
 *
 * - **Resources** — `(ours − theirs) / (ours + theirs)` over creature combat value
 *   ([CombatMath.combatTradeValue], intrinsic so tapped attackers are not undercounted) plus a flat
 *   [HAND_CARD_VALUE] per card in hand, so an opponent holding a grip of cards is not read as far
 *   behind as their board says. A share rather than a difference on purpose: an even trade leaves
 *   the difference where it was but *grows* the leader's share (4 vs 2 → 3 vs 1), which is exactly
 *   why trading favours the larger army.
 * - **Race** — our urgency minus theirs, urgency being the share of the other side's life a full
 *   attack pushes through blockers that block only when the block is worth making
 *   ([CombatMath.calculateDamageThroughOptimalBlocking] with `chumpBlocks = false`), capped at one:
 *   the same `power / life` reading
 *   [com.wingedsheep.ai.engine.evaluation.ThreatAssessment] scores the race in. Life totals and evasion
 *   enter here: an opponent at 4 facing a flier is further behind than one at 20.
 *
 * Read off a board **after** a candidate combat, it answers "does the board this attack leaves
 * still dominate?" — so an attack that trades too much away loses its own licence to be aggressive.
 */
internal object BoardRole {

    /**
     * What a card in hand counts for on the resources side, in [CombatMath.combatTradeValue] units.
     * A vanilla 2/2 is 2.8 there; a card in hand is a creature not yet paid for, so it counts for a
     * little less than a deployed one.
     */
    const val HAND_CARD_VALUE = 2.0

    fun dominance(state: GameState, projected: ProjectedState, playerId: EntityId, opponentId: EntityId): Double {
        val resources = share(resources(state, projected, playerId), resources(state, projected, opponentId))
        val race = urgency(state, projected, playerId, opponentId) - urgency(state, projected, opponentId, playerId)
        return ((resources + race) / 2.0).coerceIn(-1.0, 1.0)
    }

    private fun share(ours: Double, theirs: Double): Double {
        val total = ours + theirs
        return if (total <= 0.0) 0.0 else (ours - theirs) / total
    }

    private fun resources(state: GameState, projected: ProjectedState, playerId: EntityId): Double =
        creatures(projected, playerId).sumOf { CombatMath.combatTradeValue(projected, it).coerceAtLeast(0.0) } +
            HAND_CARD_VALUE * state.getZone(playerId, Zone.HAND).size

    /**
     * The share of [defender]'s life [attacker]'s whole team pushes through per turn, in `[0, 1]`.
     * Tapped and summoning-sick creatures count on both sides: this is the standing race, not this
     * turn's combat — the crack-back is priced separately.
     */
    private fun urgency(state: GameState, projected: ProjectedState, attacker: EntityId, defender: EntityId): Double {
        val life = state.lifeTotal(defender)
        if (life <= 0) return 1.0
        val attackers = creatures(projected, attacker).filter {
            !projected.cantAttack(it) && Keyword.DEFENDER.name !in projected.getKeywords(it)
        }
        if (attackers.isEmpty()) return 0.0
        val blockers = creatures(projected, defender).filterNot { projected.cantBlock(it) }
        val through = CombatMath.calculateDamageThroughOptimalBlocking(
            state, projected, attackers, blockers, chumpBlocks = false,
        )
        return (through.toDouble() / life).coerceIn(0.0, 1.0)
    }

    private fun creatures(projected: ProjectedState, playerId: EntityId): List<EntityId> =
        projected.getBattlefieldControlledBy(playerId).filter { projected.isCreature(it) }
}
