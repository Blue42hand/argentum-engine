package com.wingedsheep.engine.core

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * The choices an action needs that its ID alone can't carry.
 *
 * An action ID resolves to the [com.wingedsheep.engine.legalactions.LegalAction] the enumerator
 * produced, and for several action types that `GameAction` is a *template*, not a complete move:
 * `DeclareAttackers` is enumerated with an empty attacker map (the enumerator advertises the
 * candidates in `validAttackers` / `validAttackTargets` and leaves the choice to the player), and
 * the same is true of `DeclareBlockers`, of a spell's targets, and of X. Submitting such an action
 * with nothing attached is a legal move — it just means "attack with nobody", "block with nobody",
 * "no targets" — so a host that forgets to complete the template fails silently.
 *
 * These params are how any host that consumes legal-action templates (the Gym, an AI controller)
 * completes one. The completed action is then validated by the engine like any other: an illegal
 * attacker assignment is rejected rather than quietly dropped.
 *
 * Not expressible here, deliberately — each has its own channel:
 * - Complex decisions (target-selection pauses, damage assignment, ordering, …) → a
 *   [SubmitDecision] carrying a typed [DecisionResponse].
 * - Attacking bands (CR 702.22), alternative/additional cost payments, convoke/delve/improvise
 *   selections. A step carrying params for an action that can't use them is rejected with a
 *   message naming the action, never ignored.
 *
 * @property attackers attacker entity id → the player, planeswalker or battle it attacks.
 * @property blockers blocker entity id → the attackers it blocks, in order.
 * @property targets Targets for a cast/activation, in target-requirement order. Ids are resolved
 *   against the current state: a player id becomes a player target, an object on the stack a spell
 *   target, a battlefield permanent a permanent target, and a card in any other zone a card target.
 * @property xValue The value chosen for X.
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

    /** The names of the fields actually carrying a choice — the vocabulary of the error messages. */
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
 * Machine-readable parameter vocabulary for one native [GameAction] template.
 *
 * External action hosts should consume this instead of reconstructing which [ActionParams] fields
 * apply to which action type. This describes only the wire shape accepted by [ActionParameterizer];
 * the legal-action projection and normal engine validation remain authoritative for which entity ids
 * and values are legal in the current state.
 */
@Serializable
data class ActionParameterSpec(
    val allowedFields: Map<String, ActionParameterFieldKind> = emptyMap(),
) {
    val acceptsParameters: Boolean
        get() = allowedFields.isNotEmpty()

    companion object {
        val EMPTY = ActionParameterSpec()
    }
}

/** Wire-level kinds used by [ActionParameterSpec]. */
@Serializable
enum class ActionParameterFieldKind {
    ENTITY_ID_MAP,
    ENTITY_ID_ARRAY_MAP,
    ENTITY_ID_ARRAY,
    INTEGER,
}

/**
 * Folds [ActionParams] into the template `GameAction` an action ID resolved to.
 *
 * Pure — it builds the action the engine will then validate; it does not check legality itself.
 * Anything it cannot express is an [IllegalArgumentException] rather than a silently
 * dropped choice, which is the failure mode this whole type exists to remove.
 */
object ActionParameterizer {

    /**
     * Return the native parameter contract for [action].
     *
     * The same contract drives [apply]'s field whitelist, keeping external metadata and native
     * acceptance semantics from drifting apart. An empty spec means the action takes no
     * [ActionParams].
     */
    fun spec(action: GameAction): ActionParameterSpec = when (action) {
        is DeclareAttackers -> ActionParameterSpec(
            mapOf("attackers" to ActionParameterFieldKind.ENTITY_ID_MAP)
        )
        is DeclareBlockers -> ActionParameterSpec(
            mapOf("blockers" to ActionParameterFieldKind.ENTITY_ID_ARRAY_MAP)
        )
        is CastSpell, is ActivateAbility -> ActionParameterSpec(
            mapOf(
                "targets" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "xValue" to ActionParameterFieldKind.INTEGER,
            )
        )
        else -> ActionParameterSpec.EMPTY
    }

    fun apply(action: GameAction, params: ActionParams, state: GameState): GameAction {
        if (params.isEmpty) return action

        return when (action) {
            is DeclareAttackers -> {
                params.allowOnly(action, spec(action))
                action.copy(attackers = params.attackers)
            }

            is DeclareBlockers -> {
                params.allowOnly(action, spec(action))
                action.copy(blockers = params.blockers)
            }

            is CastSpell -> {
                params.allowOnly(action, spec(action))
                action.copy(
                    targets = params.targets.map { resolveTarget(it, state) }
                        .ifEmpty { action.targets },
                    xValue = params.xValue ?: action.xValue
                )
            }

            is ActivateAbility -> {
                params.allowOnly(action, spec(action))
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

    /**
     * Turn a bare entity id into the [ChosenTarget] variant its current zone implies. The caller
     * sends ids because that is all the observation exposes; which variant an id means is a fact
     * about the game state, not about the request.
     *
     * Public so the zone dispatch can be asserted directly — it is the only part of this object that
     * reads live state, and driving a game to each of the four zones just to reach it would test the
     * driver, not the dispatch.
     */
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

    /** Reject fields outside the engine-authored [ActionParameterSpec] for [action]. */
    private fun ActionParams.allowOnly(action: GameAction, spec: ActionParameterSpec) {
        val unusable = populatedFields - spec.allowedFields.keys
        require(unusable.isEmpty()) {
            "Step param(s) ${unusable.joinToString(", ") { "'$it'" }} " +
                "are not applicable to ${action::class.simpleName}"
        }
    }
}
