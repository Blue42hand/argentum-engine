package com.wingedsheep.engine.core

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.AlternativePaymentChoice
import kotlinx.serialization.Serializable

/**
 * The choices an action needs that its ID alone can't carry.
 *
 * An action ID resolves to the [com.wingedsheep.engine.legalactions.LegalAction] the enumerator
 * produced, and for several action types that `GameAction` is a *template*, not a complete move:
 * `DeclareAttackers` is enumerated with an empty attacker map (the enumerator advertises the
 * candidates in `validAttackers` / `validAttackTargets` and leaves the choice to the player), and
 * the same is true of `DeclareBlockers`, of a spell's targets, and of X. Stepping such an action
 * with nothing attached is a legal move — it just means "attack with nobody", "block with nobody",
 * "no targets" — which is why the gap was silent: combat was unreachable over the HTTP API while
 * every request returned 200.
 *
 * These params are how a caller completes the template. They are validated by the engine like any
 * other action: an illegal attacker assignment is rejected rather than quietly dropped (see
 * [com.wingedsheep.gym.GameGymEnv.step]).
 *
 * Not expressible here, deliberately — each has its own channel:
 * - Complex decisions (target-selection pauses, damage assignment, ordering, …) → `POST
 *   /envs/{id}/decision` with a typed `DecisionResponse`.
 * - Attacking bands (CR 702.22), other alternative/additional cost payments, and
 *   convoke/improvise selections. A step carrying params for an action that can't use them
 *   is rejected with a message naming the action, never ignored.
 *
 * @property attackers attacker entity id → the player, planeswalker or battle it attacks.
 * @property blockers blocker entity id → the attackers it blocks, in order.
 * @property targets Targets for a cast/activation, in target-requirement order. Ids are resolved
 *   against the current state: a player id becomes a player target, an object on the stack a spell
 *   target, a battlefield permanent a permanent target, and a card in any other zone a card target.
 * @property xValue The value chosen for X.
 * @property tappedPermanents Permanents chosen to tap as a spell or ability cost.
 * @property sacrificedPermanents Permanents chosen to sacrifice as a cost.
 * @property discardedCards Cards chosen to discard as a cost.
 * @property exiledCards Cards chosen to exile as a cost.
 * @property delvedCards Graveyard cards chosen to exile to pay generic mana with delve.
 */
@Serializable
data class ActionParams(
    val attackers: Map<EntityId, EntityId> = emptyMap(),
    val blockers: Map<EntityId, List<EntityId>> = emptyMap(),
    val targets: List<EntityId> = emptyList(),
    val xValue: Int? = null,
    val tappedPermanents: List<EntityId> = emptyList(),
    val sacrificedPermanents: List<EntityId> = emptyList(),
    val discardedCards: List<EntityId> = emptyList(),
    val exiledCards: List<EntityId> = emptyList(),
    val delvedCards: List<EntityId> = emptyList(),
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
            if (tappedPermanents.isNotEmpty()) add("tappedPermanents")
            if (sacrificedPermanents.isNotEmpty()) add("sacrificedPermanents")
            if (discardedCards.isNotEmpty()) add("discardedCards")
            if (exiledCards.isNotEmpty()) add("exiledCards")
            if (delvedCards.isNotEmpty()) add("delvedCards")
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
 * Pure — it builds the action the engine will then validate. The offered-action overload checks
 * that selected cost IDs were among the enumerator's candidates; payment validates them again
 * against the current state before mutating it.
 * Anything it cannot express is an [IllegalArgumentException] (→ HTTP 400) rather than a silently
 * dropped choice, which is the failure mode this whole type exists to remove.
 */
object ActionParameterizer {

    /** Narrow the cost-choice contract to the costs on this particular offered action. */
    fun spec(legalAction: LegalAction): ActionParameterSpec {
        val fields = spec(legalAction.action).allowedFields.toMutableMap()
        if (legalAction.action is CastSpell || legalAction.action is ActivateAbility) {
            val cost = legalAction.additionalCostInfo
            if (cost?.validTapTargets.isNullOrEmpty()) fields.remove("tappedPermanents")
            if (cost?.validSacrificeTargets.isNullOrEmpty()) fields.remove("sacrificedPermanents")
            if (cost?.validDiscardTargets.isNullOrEmpty()) fields.remove("discardedCards")
            if (cost?.validExileTargets.isNullOrEmpty()) fields.remove("exiledCards")
        }
        if (legalAction.action is CastSpell &&
            (!legalAction.hasDelve || legalAction.hasXCost || legalAction.delveCards.isNullOrEmpty())
        ) fields.remove("delvedCards")
        return ActionParameterSpec(fields)
    }

    fun apply(legalAction: LegalAction, params: ActionParams, state: GameState): GameAction {
        params.allowOnly(legalAction.action, spec(legalAction))
        params.requireBlockTargets(legalAction.validBlockTargets)
        legalAction.additionalCostInfo?.let { cost ->
            params.requireCostCandidates(cost.validTapTargets, cost.validSacrificeTargets,
                cost.validDiscardTargets, cost.validExileTargets)
        }
        params.requireDelveCandidates(legalAction.delveCards?.map { it.entityId }.orEmpty())
        return apply(legalAction.action, params, state)
    }

    /** Complete a server controller's enriched offered action with the same cost checks as Gym. */
    fun apply(info: LegalActionInfo, params: ActionParams, state: GameState): GameAction {
        params.allowOnly(info.action, info.parameterSpec)
        params.requireBlockTargets(info.validBlockTargets)
        info.additionalCostInfo?.let { cost ->
            params.requireCostCandidates(cost.validTapTargets, cost.validSacrificeTargets,
                cost.validDiscardTargets, cost.validExileTargets)
        }
        params.requireDelveCandidates(info.validDelveCards?.map { it.entityId }.orEmpty())
        return apply(info.action, params, state)
    }

    private fun ActionParams.requireCostCandidates(
        tap: List<EntityId>, sacrifice: List<EntityId>, discard: List<EntityId>, exile: List<EntityId>
    ) {
        fun check(field: String, selected: List<EntityId>, candidates: List<EntityId>) {
            require(selected.all { it in candidates }) { "$field contains an entity not offered for this action" }
        }
        check("tappedPermanents", tappedPermanents, tap)
        check("sacrificedPermanents", sacrificedPermanents, sacrifice)
        check("discardedCards", discardedCards, discard)
        check("exiledCards", exiledCards, exile)
    }

    private fun ActionParams.requireBlockTargets(targets: Map<EntityId, List<EntityId>>?) {
        if (blockers.isEmpty() || targets == null) return
        require(blockers.all { (blocker, attackers) ->
            blocker in targets && attackers.all { it in targets.getValue(blocker) }
        }) { "blockers contains a blocker-attacker pair not offered for this action" }
    }

    private fun ActionParams.requireDelveCandidates(candidates: List<EntityId>) {
        require(delvedCards.all { it in candidates }) {
            "delvedCards contains a card not offered for this action"
        }
    }

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
        is CastSpell -> ActionParameterSpec(
            mapOf(
                "targets" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "xValue" to ActionParameterFieldKind.INTEGER,
                "tappedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "sacrificedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "discardedCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "exiledCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "delvedCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
            )
        )
        is ActivateAbility -> ActionParameterSpec(
            mapOf(
                "targets" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "xValue" to ActionParameterFieldKind.INTEGER,
                "tappedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "sacrificedPermanents" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "discardedCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
                "exiledCards" to ActionParameterFieldKind.ENTITY_ID_ARRAY,
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
                    xValue = params.xValue ?: action.xValue,
                    additionalCostPayment = params.withCostChoices(action.additionalCostPayment),
                    alternativePayment = params.withDelveChoice(action.alternativePayment),
                )
            }

            is ActivateAbility -> {
                params.allowOnly(action, spec(action))
                action.copy(
                    targets = params.targets.map { resolveTarget(it, state) }
                        .ifEmpty { action.targets },
                    xValue = params.xValue ?: action.xValue,
                    costPayment = params.withCostChoices(action.costPayment)
                )
            }

            else -> throw IllegalArgumentException(
                "Action ${action::class.simpleName} takes no step params; got $params"
            )
        }
    }

    /** Keep template payment fields that this native step did not replace. */
    private fun ActionParams.withCostChoices(existing: AdditionalCostPayment?): AdditionalCostPayment? {
        if (tappedPermanents.isEmpty() && sacrificedPermanents.isEmpty() &&
            discardedCards.isEmpty() && exiledCards.isEmpty()
        ) return existing
        val base = existing ?: AdditionalCostPayment.NONE
        return base.copy(
            tappedPermanents = tappedPermanents.ifEmpty { base.tappedPermanents },
            sacrificedPermanents = sacrificedPermanents.ifEmpty { base.sacrificedPermanents },
            discardedCards = discardedCards.ifEmpty { base.discardedCards },
            exiledCards = exiledCards.ifEmpty { base.exiledCards },
        )
    }

    /** Keep any other alternative-payment choices already present on the action template. */
    private fun ActionParams.withDelveChoice(existing: AlternativePaymentChoice?): AlternativePaymentChoice? {
        if (delvedCards.isEmpty()) return existing
        return (existing ?: AlternativePaymentChoice.NONE).copy(delvedCards = delvedCards)
    }

    /**
     * Turn a bare entity id into the [ChosenTarget] variant its current zone implies. The caller
     * sends ids because that is all the observation exposes; which variant an id means is a fact
     * about the game state, not about the request.
     *
     * `internal` rather than private so the zone dispatch can be asserted directly — it is the only
     * part of this object that reads live state, and driving a game to each of the four zones just
     * to reach it would test the driver, not the dispatch.
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
