package com.wingedsheep.engine.core

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Choices needed to complete an engine-authored [GameAction] template.
 *
 * Several legal actions intentionally expose a template plus a set of candidates rather than one
 * fully materialized move. Attackers, blockers, spell/ability targets, and X values are examples.
 * This type lets any Argentum host complete those templates before submitting the resulting native
 * action through the normal authoritative rules path.
 *
 * Complex continuation decisions remain [DecisionResponse]s and are not represented here.
 */
@Serializable
data class ActionParams(
    val attackers: Map<EntityId, EntityId> = emptyMap(),
    val blockers: Map<EntityId, List<EntityId>> = emptyMap(),
    val targets: List<EntityId> = emptyList(),
    val xValue: Int? = null
) {
    val isEmpty: Boolean
        get() = populatedFields.isEmpty()

    internal val populatedFields: List<String>
        get() = buildList {
            if (attackers.isNotEmpty()) add("attackers")
            if (blockers.isNotEmpty()) add("blockers")
            if (targets.isNotEmpty()) add("targets")
            if (xValue != null) add("xValue")
        }

    companion object {
        val EMPTY = ActionParams()
    }
}

/**
 * Purely materializes an action template from caller-supplied [ActionParams].
 *
 * This does not replace engine legality checks. The returned native [GameAction] must still travel
 * through the ordinary authoritative submission path, where normal rules validation applies.
 * Unsupported or inapplicable parameter fields fail closed rather than being silently ignored.
 */
object ActionParameterizer {

    fun apply(action: GameAction, params: ActionParams, state: GameState): GameAction {
        if (params.isEmpty) return action

        return when (action) {
            is DeclareAttackers -> {
                params.allowOnly(action, "attackers")
                action.copy(attackers = params.attackers)
            }

            is DeclareBlockers -> {
                params.allowOnly(action, "blockers")
                action.copy(blockers = params.blockers)
            }

            is CastSpell -> {
                params.allowOnly(action, "targets", "xValue")
                action.copy(
                    targets = params.targets.map { resolveTarget(it, state) }
                        .ifEmpty { action.targets },
                    xValue = params.xValue ?: action.xValue
                )
            }

            is ActivateAbility -> {
                params.allowOnly(action, "targets", "xValue")
                action.copy(
                    targets = params.targets.map { resolveTarget(it, state) }
                        .ifEmpty { action.targets },
                    xValue = params.xValue ?: action.xValue
                )
            }

            else -> throw IllegalArgumentException(
                "Action ${action::class.simpleName} takes no step params; got $params"
            )
        }
    }

    /** Resolve an exposed entity id to the target variant implied by authoritative state. */
    fun resolveTarget(id: EntityId, state: GameState): ChosenTarget = when {
        id in state.turnOrder -> ChosenTarget.Player(id)
        id in state.stack -> ChosenTarget.Spell(id)
        id in state.getBattlefield() -> ChosenTarget.Permanent(id)
        else -> {
            val key = state.zones.entries.firstOrNull { (_, ids) -> id in ids }?.key
                ?: throw IllegalArgumentException("Target $id is in no zone of the current state")
            ChosenTarget.Card(cardId = id, ownerId = key.ownerId, zone = key.zoneType)
        }
    }

    private fun ActionParams.allowOnly(action: GameAction, vararg allowed: String) {
        val unusable = populatedFields - allowed.toSet()
        require(unusable.isEmpty()) {
            "Step param(s) ${unusable.joinToString(", ") { "'$it'" }} " +
                "are not applicable to ${action::class.simpleName}"
        }
    }
}
