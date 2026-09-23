package com.wingedsheep.engine.mechanics

import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AbilityActivatedThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.ActivationRestriction

/**
 * The one evaluation of an [ActivationRestriction] ("Activate only during your turn", "only once
 * each turn", "only if …", …).
 *
 * Three activation-legality paths used to carry their own copy of this switch and had to be kept in
 * step by hand: `ActivateAbilityHandler` (the authoritative re-check, which needs a player-facing
 * reason), `CastPermissionUtils.checkActivationRestriction` (what the legal-action enumerators
 * offer) and `ManaSolver`'s auto-tap filter. All three now ask this kernel, so an ability can't be
 * offered by one path and rejected by another over a restriction.
 *
 * Lives in `mechanics` rather than `legalactions` so `ManaSolver` can use it without depending on the
 * legal-actions module — the same reason [OnceOnlyActivationAllowance] lives here.
 *
 * Stateless apart from its two collaborators; cheap to construct wherever it is needed.
 */
class ActivationRestrictionKernel(
    private val cardRegistry: CardRegistry,
    private val conditionEvaluator: ConditionEvaluator,
) {

    /**
     * Whether [restriction] currently permits [playerId] to activate [ability] of [sourceId].
     */
    fun isSatisfied(
        state: GameState,
        playerId: EntityId,
        restriction: ActivationRestriction,
        sourceId: EntityId?,
        ability: ActivatedAbility,
    ): Boolean = violation(state, playerId, restriction, sourceId, ability) == null

    /**
     * The reason [restriction] forbids [playerId] from activating [ability] of [sourceId] right now,
     * or `null` when it doesn't. For an [ActivationRestriction.All] the first failing member's
     * reason is returned; members are checked in order and the check stops at the first failure.
     *
     * @param sourceId the permanent whose ability is being activated. `null` is accepted for callers
     *   that ask before a source exists: the source-scoped restrictions (the per-turn and once-only
     *   trackers, "controlled since your most recent turn") then pass, and an
     *   [ActivationRestriction.OnlyIfCondition] is evaluated with no source.
     * @param ability the ability being checked — the single source of both the per-ability identity
     *   the turn/lifetime trackers key on ([ActivatedAbility.id], read by
     *   [ActivationRestriction.OncePerTurn] / [ActivationRestriction.MaxPerTurn]) and the
     *   `isExhaust`/`isPowerUp` flags [ActivationRestriction.Once] reads. Deliberately required, with
     *   no default: a defaulted `ability` would let a forgetful call site silently disable the
     *   ExtraOnceOnlyActivations permission on its path while the others kept honouring it.
     */
    fun violation(
        state: GameState,
        playerId: EntityId,
        restriction: ActivationRestriction,
        sourceId: EntityId?,
        ability: ActivatedAbility,
    ): String? = when (restriction) {
        // Not a restriction: it widens *who* may activate, which the controller check reads through
        // [anyPlayerMay].
        is ActivationRestriction.AnyPlayerMay -> null
        is ActivationRestriction.OnlyDuringYourTurn -> {
            // CR 805.5a — "your turn" is the active team's turn in Two-Headed Giant.
            if (!state.isActiveTurnFor(playerId)) "This ability can only be activated during your turn"
            else null
        }
        is ActivationRestriction.BeforeStep -> {
            if (state.step.ordinal >= restriction.step.ordinal)
                "This ability can only be activated before ${restriction.step.displayName}"
            else null
        }
        is ActivationRestriction.DuringPhase -> {
            if (state.phase != restriction.phase)
                "This ability can only be activated during ${restriction.phase.displayName}"
            else null
        }
        is ActivationRestriction.DuringStep -> {
            if (state.step != restriction.step)
                "This ability can only be activated during ${restriction.step.displayName}"
            else null
        }
        is ActivationRestriction.OnlyIfCondition -> {
            val context = EffectContext(
                sourceId = sourceId,
                controllerId = playerId,
                targets = emptyList(),
                xValue = 0
            )
            if (!conditionEvaluator.evaluate(state, restriction.condition, context))
                "Activation condition not met"
            else null
        }
        is ActivationRestriction.OncePerTurn -> {
            val tracker = sourceId?.let { state.getEntity(it)?.get<AbilityActivatedThisTurnComponent>() }
            if (tracker != null && tracker.hasActivated(ability.id)) {
                "This ability can only be activated once each turn"
            } else null
        }
        is ActivationRestriction.MaxPerTurn -> {
            if (sourceId == null) null
            else {
                val tracker = state.getEntity(sourceId)?.get<AbilityActivatedThisTurnComponent>()
                if ((tracker?.activationCount(ability.id) ?: 0) >= restriction.count) {
                    "This ability can't be activated more than ${restriction.count} times each turn"
                } else null
            }
        }
        is ActivationRestriction.Once -> {
            // An exhaust or power-up ability's once-only memory can be raised or waived by an
            // ExtraOnceOnlyActivations permission (Elvish Refueler, Wonder Man); a plain Once
            // restriction on an ordinary ability never is.
            if (sourceId == null) null
            else if (!OnceOnlyActivationAllowance.mayActivate(
                    state, playerId, sourceId, ability, cardRegistry, conditionEvaluator
                )
            ) "This ability can only be activated once"
            else null
        }
        is ActivationRestriction.ControlledSinceYourMostRecentTurn -> {
            // "Controlled continuously since the beginning of your most recent turn" — the
            // summoning-sickness condition (CR 302.6) generalized to any permanent. The engine
            // re-stamps SummoningSicknessComponent on entry and on every control change and
            // clears it at the controller's untap, so its absence is exactly this predicate.
            // Haste does not lift it (CR 702.10c covers only the tap/untap symbols), so this reads
            // the marker directly rather than going through SummoningSicknessRules.
            if (sourceId != null && state.getEntity(sourceId)?.has<SummoningSicknessComponent>() == true)
                "You must have controlled this permanent continuously since your most recent turn began"
            else null
        }
        is ActivationRestriction.All -> restriction.restrictions.firstNotNullOfOrNull {
            violation(state, playerId, it, sourceId, ability)
        }
    }

    companion object {
        /**
         * Whether [restriction] opens the ability to players other than the source's controller
         * (Lethal Vapors). Recursive through [ActivationRestriction.All], because the permission is
         * routinely *narrowed* by a companion restriction rather than standing alone — Merseine's
         * "only the controller of the enchanted creature may activate this ability" is
         * AnyPlayerMay + a condition.
         */
        fun anyPlayerMay(restriction: ActivationRestriction): Boolean = when (restriction) {
            is ActivationRestriction.AnyPlayerMay -> true
            is ActivationRestriction.All -> restriction.restrictions.any { anyPlayerMay(it) }
            else -> false
        }
    }
}
