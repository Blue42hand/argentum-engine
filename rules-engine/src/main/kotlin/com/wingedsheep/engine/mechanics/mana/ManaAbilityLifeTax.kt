package com.wingedsheep.engine.mechanics.mana

import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.tapForMana
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.handlers.effects.life.LifePaymentService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom

/**
 * "Mana abilities of this land cost an additional N life to activate" (Thran Portal,
 * [com.wingedsheep.sdk.scripting.ManaAbilitiesCostAdditionalLife]).
 *
 * The tax is a projected Layer 6 value ([com.wingedsheep.engine.mechanics.layers.ProjectedState.getManaAbilityLifeTax]),
 * so it disappears with the permanent's abilities and needs no card registry to read. Every place
 * a mana ability is activated consults it here:
 *  - the manual pipeline (legal-action enumeration and the activation lookup) folds it into the
 *    ability's cost via [withTax], so the ordinary cost payer charges the life and refuses the
 *    activation when it can't be paid (CR 119.4);
 *  - the auto-pay solver prices the source as a pain source and drops it when its controller
 *    can't pay ([ManaSolver.findAvailableManaSources]);
 *  - every auto-pay path that taps a solver-chosen source charges it with [pay] (or
 *    [tapForManaPayingTax], the tap-and-charge pair).
 */
object ManaAbilityLifeTax {

    /** The additional life each mana ability of [entityId] costs right now (0 for nearly everything). */
    fun amount(state: GameState, entityId: EntityId): Int =
        state.projectedState.getManaAbilityLifeTax(entityId)

    /** [abilities] of [entityId] with the tax folded into each mana ability's cost. */
    fun withTax(state: GameState, entityId: EntityId, abilities: List<ActivatedAbility>): List<ActivatedAbility> {
        val tax = amount(state, entityId)
        if (tax <= 0) return abilities
        return abilities.map { withTax(it, tax) }
    }

    /** [ability] with [tax] additional life in its cost, when it is a mana ability. */
    fun withTax(ability: ActivatedAbility, tax: Int): ActivatedAbility {
        if (tax <= 0 || !ability.isManaAbility) return ability
        val life = AbilityCost.Atom(CostAtom.PayLife(tax))
        val cost = when (val c = ability.cost) {
            is AbilityCost.Composite -> AbilityCost.Composite(c.costs + life)
            else -> AbilityCost.Composite(listOf(c, life))
        }
        return ability.copy(cost = cost)
    }

    /**
     * Charge [payerId] the tax for activating a mana ability of [sourceId]. A no-op (no events)
     * when the source isn't taxed.
     */
    fun pay(
        zones: ZoneTransitionService,
        state: GameState,
        sourceId: EntityId,
        payerId: EntityId,
    ): Pair<GameState, List<GameEvent>> {
        val tax = amount(state, sourceId)
        if (tax <= 0) return state to emptyList()
        return LifePaymentService.pay(zones, state, payerId, tax) ?: (state to emptyList())
    }

    /**
     * [tapForMana] plus the tax — for the auto-pay paths that tap a chosen source directly rather
     * than through [ManaAbilitySideEffectExecutor.tapSourcesWithSideEffects]. Nothing is charged
     * when the source was already tapped (no activation happened).
     */
    fun tapForManaPayingTax(
        zones: ZoneTransitionService,
        state: GameState,
        sourceId: EntityId,
        tapperId: EntityId,
    ): Pair<GameState, List<GameEvent>> {
        val (tapped, tapEvents) = tapForMana(state, sourceId, tapperId)
        if (tapEvents.isEmpty()) return tapped to tapEvents
        val (paid, lifeEvents) = pay(zones, tapped, sourceId, tapperId)
        return paid to (tapEvents + lifeEvents)
    }
}
