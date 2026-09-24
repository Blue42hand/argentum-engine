package com.wingedsheep.engine.handlers.predicates

import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.targets.TargetOther
import com.wingedsheep.sdk.scripting.targets.TargetRequirement

/**
 * The one reading of an Aura's printed "Enchant …" restriction (CR 303.4a) against a would-be host.
 *
 * Only the requirement's *filter* is evaluated — never targeting legality — because both callers
 * ask about an Aura that isn't being cast: the enchant state-based action (CR 704.5m, an attached
 * Aura isn't re-targeted, so hexproof/shroud don't dislodge it) and `CardPredicate.CouldEnchant`
 * ("an Aura card that could enchant it" — an Aura put onto the battlefield doesn't target,
 * CR 303.4f).
 */
object EnchantRestriction {

    /**
     * The battlefield filter behind an Aura's `auraTarget`, or null when the requirement isn't one
     * that can be checked against a permanent host (an "enchant player" requirement, say).
     */
    fun filterOf(requirement: TargetRequirement): TargetFilter? = when (requirement) {
        is TargetObject -> requirement.filter
        // "Enchant another …" — the distinctness rule is targeting-only; the filter is the base's.
        is TargetOther -> filterOf(requirement.baseRequirement)
        else -> null
    }

    /**
     * Whether [hostId] satisfies [requirement], with "you" in the restriction meaning
     * [controllerId] (the Aura's controller, or the player who would put it onto the battlefield).
     * Null when the requirement can't be judged against a permanent at all — callers decide
     * whether that fails open (the SBA) or closed (a search filter).
     */
    fun hostSatisfies(
        state: GameState,
        projected: ProjectedState,
        predicateEvaluator: PredicateEvaluator,
        requirement: TargetRequirement,
        hostId: EntityId,
        controllerId: EntityId,
        auraId: EntityId?
    ): Boolean? {
        val filter = filterOf(requirement) ?: return null
        // A cross-zone union requirement is satisfied by any one clause; only battlefield clauses
        // can describe a permanent host.
        val battlefieldClauses = filter.clauses().filter { it.zone == Zone.BATTLEFIELD }
        if (battlefieldClauses.isEmpty()) return null
        val context = PredicateContext(controllerId = controllerId, sourceId = auraId)
        return battlefieldClauses.any {
            predicateEvaluator.matches(state, projected, hostId, it.baseFilter, context)
        }
    }
}
