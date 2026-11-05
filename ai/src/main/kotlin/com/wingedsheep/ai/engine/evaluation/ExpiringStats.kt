package com.wingedsheep.ai.engine.evaluation

import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration

/**
 * The power and toughness a leaf score should read for a creature: what it has **now**, or what it
 * will have **after cleanup** — and when the first is the right answer.
 *
 * An "until end of turn" stat change is worth something only if something consumes it before it
 * expires. A one-ply leaf is scored on the board right after the line resolves, and that board is
 * before cleanup, so a term that reads projected P/T straight off it prices a pump as if it were
 * permanent. `BoardPresence` already took the modification back off its base stats, but its
 * power-scaled keyword bonuses and every term of `ThreatAssessment` — attack potential, blocking
 * toughness, lethal on board, evasive power, all of them statements about *the next combat* — read
 * it whole. Off a production game (2026-10-14, Reality Fracture draft) that was the whole value of
 * three activations that bought nothing: Undulating Witness's `{2}: +1/-1` twice in our own
 * declare-attackers step with nothing attacking (and the creature it crowded out of the mana stayed
 * in hand), Shatterwing Pegasus's `{4}{W}` team pump in the opponent's declare-attackers step before
 * the AI declared no blocks, and again in our precombat main at 2 life — the pumped board "blocked"
 * the opponent's lethal crack-back, a crack-back that comes after the pump has worn off.
 *
 * ## What counts as a consumer
 *
 * A creature **already in a fight that has not dealt its damage** — attacking or blocking, in the
 * declare-attackers, declare-blockers or first-strike damage step. There, the bonus changes the
 * combat about to happen, and the leaf reads it whole.
 *
 * Everything else reads the settled stats, and that includes a creature that *could* attack later
 * this turn. A pump on a creature that has not been declared is not yet bought for anything: an
 * instant-speed pump is still available once attackers (or blockers) are declared, with strictly
 * more information, and the attack plan is `CombatAdvisor`'s decision, not the leaf's. The leaf
 * that priced "could attack" as "will attack" is how Pegasus pumped the team precombat and then
 * sent nobody.
 *
 * What the other consumers need, the simulation already provides: a pump in response to burn
 * resolves before the burn in the same simulation, so the creature that lives *is* the leaf; once
 * blockers are declared `GameSimulator` carries the line through combat damage
 * (`AiProfile.resolveThroughCombatDamage`), so firebreathing for lethal, or a pump that wins a
 * block, shows up as life lost and creatures dead — permanent changes this does not touch.
 *
 * Only `ModifyPowerToughness` floating effects with [Duration.EndOfTurn] are taken back off — the
 * same reading `BoardPresence` has always used for its base stats. A temporary base-P/T *setting*
 * effect is not read.
 */
internal object ExpiringStats {

    /** Whether [entityId] is attacking or blocking in a combat whose damage is still to come. */
    fun inFightStillAhead(state: GameState, entityId: EntityId): Boolean {
        if (state.step !in FIGHT_STILL_AHEAD) return false
        val entity = state.getEntity(entityId) ?: return false
        return entity.has<AttackingComponent>() || entity.has<BlockingComponent>()
    }

    /**
     * (power, toughness) modifications on [entityId] that expire at cleanup — the sum of its
     * until-end-of-turn `ModifyPowerToughness` floating effects.
     */
    fun temporaryModification(state: GameState, entityId: EntityId): Pair<Int, Int> {
        var powerMod = 0
        var toughnessMod = 0
        for (effect in state.floatingEffects) {
            if (effect.duration != Duration.EndOfTurn) continue
            if (entityId !in effect.effect.affectedEntities) continue
            val mod = effect.effect.modification
            if (mod is SerializableModification.ModifyPowerToughness) {
                powerMod += mod.powerMod
                toughnessMod += mod.toughnessMod
            }
        }
        return powerMod to toughnessMod
    }

    /** [entityId]'s power with its end-of-turn modifications taken off, never below zero. */
    fun settledPower(state: GameState, projected: ProjectedState, entityId: EntityId): Int =
        ((projected.getPower(entityId) ?: 0) - temporaryModification(state, entityId).first).coerceAtLeast(0)

    /** [entityId]'s toughness with its end-of-turn modifications taken off, never below zero. */
    fun settledToughness(state: GameState, projected: ProjectedState, entityId: EntityId): Int =
        ((projected.getToughness(entityId) ?: 0) - temporaryModification(state, entityId).second).coerceAtLeast(0)

    /**
     * The power a leaf should read for [entityId]: projected while [needAFight] is off or the
     * creature is in a fight still ahead, settled otherwise.
     */
    fun power(state: GameState, projected: ProjectedState, entityId: EntityId, needAFight: Boolean): Int =
        if (!needAFight || inFightStillAhead(state, entityId)) (projected.getPower(entityId) ?: 0).coerceAtLeast(0)
        else settledPower(state, projected, entityId)

    /** [power]'s toughness twin. */
    fun toughness(state: GameState, projected: ProjectedState, entityId: EntityId, needAFight: Boolean): Int =
        if (!needAFight || inFightStillAhead(state, entityId)) (projected.getToughness(entityId) ?: 0).coerceAtLeast(0)
        else settledToughness(state, projected, entityId)

    /**
     * Attackers declared and combat damage still to deal. Players get priority in the combat damage
     * step only after the damage is dealt, so from there on no fight is ahead.
     */
    private val FIGHT_STILL_AHEAD = setOf(
        Step.DECLARE_ATTACKERS, Step.DECLARE_BLOCKERS, Step.FIRST_STRIKE_COMBAT_DAMAGE,
    )
}
