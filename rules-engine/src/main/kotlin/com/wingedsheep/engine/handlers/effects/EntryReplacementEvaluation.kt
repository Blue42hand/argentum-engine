package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.handlers.EntryEvaluationViews
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Read-only CR 614.12 views; never commit either synthesized view to the game. */
internal fun entryEvaluationViews(
    prepared: GameState,
    entrantId: EntityId,
    controllerId: EntityId,
    before: GameState,
    priorZone: ZoneKey? = null,
): EntryEvaluationViews {
    // Legacy single-entry callers may supply the already-placed state. Restore membership
    // for their pre-entry zone; batch callers supply the actual frozen pre-event snapshot,
    // whose card characteristics and zone ordering must remain untouched.
    val world = if (before.logicalZone(entrantId)?.zoneType == Zone.BATTLEFIELD) {
        val zones = before.zones.mapValues { (_, ids) -> ids - entrantId }
        val identity = before.objectIdentities[entrantId]
        before.copy(
            zones = if (priorZone == null) zones else
                zones + (priorZone to (zones[priorZone].orEmpty() + entrantId)),
            objectIdentities = if (priorZone == null || identity == null)
                before.objectIdentities - entrantId else
                before.objectIdentities + (entrantId to identity.copy(logicalZone = priorZone)),
        )
    } else before

    val entrant = prepared.getEntity(entrantId)
        ?: return EntryEvaluationViews(entrantId, world, world)
    val battlefield = prepared.logicalZone(entrantId)?.takeIf { it.zoneType == Zone.BATTLEFIELD }
        ?: ZoneKey(controllerId, Zone.BATTLEFIELD)
    val zones = world.zones.mapValues { (_, ids) -> ids - entrantId }
    val priorEffects = world.floatingEffects.mapTo(mutableSetOf()) { it.id }
    // Only this prepared entrant is added to the frozen world. A simultaneously arriving
    // type-granting permanent must not influence its replacement predicates. Preserve
    // original floating effects and new riders that affect only this entrant.
    val asWouldExist = world.copy(
        entities = world.entities + (entrantId to entrant.with(ControllerComponent(controllerId))),
        zones = zones + (battlefield to (zones[battlefield].orEmpty() + entrantId)),
        objectIdentities = world.objectIdentities + prepared.objectIdentities.filterKeys { it == entrantId }
            .mapValues { (_, identity) -> identity.copy(logicalZone = battlefield) },
        floatingEffects = world.floatingEffects + prepared.floatingEffects.filter {
            it.id !in priorEffects && it.effect.affectedEntities == setOf(entrantId)
        },
    )
    return EntryEvaluationViews(entrantId, world, asWouldExist)
}
