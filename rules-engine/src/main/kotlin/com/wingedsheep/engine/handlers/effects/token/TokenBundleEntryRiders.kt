package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.TokenBundleMaterializationContinuation
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils
import com.wingedsheep.engine.mechanics.combat.AttackedPermanents
import com.wingedsheep.engine.mechanics.combat.CombatDefenders
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.scripting.effects.*

/** Inherited attacking instructions apply to the actual entrant, including predefined substitutes. */
internal object TokenBundleEntryRiders {
    fun finishEntry(before: GameState, trial: EffectResult, effect: Effect, context: EffectContext): EffectResult {
        if (!context.tokenCreationReplacementsHandled || trial.error != null) return trial
        val frame = before.continuationStack.filterIsInstance<TokenBundleMaterializationContinuation>().lastOrNull()
            ?: return trial
        val original = frame.ridersSource ?: return trial
        val attacking = when (original) {
            is CreateTokenEffect -> original.attacking
            is CreateTokenCopyOfTargetEffect -> original.attacking
            else -> false
        }
        if (!attacking) return trial
        // Granted-entry-copy replay settles the actual identity before its defender is chosen.
        // The registry discards this entire trial and asks about the copy first.
        if (trial.state.entryCopyOrders?.unasked != null) return trial
        val tokens = trial.state.getBattlefield().filter { id ->
            id !in before.entities && trial.state.getEntity(id)?.has<TokenComponent>() == true &&
                trial.state.projectedState.isCreature(id)
        }
        if (tokens.isEmpty()) return trial
        check(tokens.size == 1) { "A bundle native entry must materialize exactly one token" }
        val controller = frame.bundle.controllerId
        val mode = when (original) {
            is CreateTokenEffect -> original.attackDefender
            is CreateTokenCopyOfTargetEffect -> original.attackDefender
            else -> EnteringAttackDefender.JOIN_SOURCE
        }
        val named = when (original) {
            is CreateTokenEffect -> original.attackedPlayer
            is CreateTokenCopyOfTargetEffect -> original.attackedPlayer
            else -> null
        }
        val namedPlayer = named?.let { TargetResolutionUtils.resolvePlayerTarget(it, context, before) }
            ?.takeIf { it in before.getOpponents(controller) }
        val defender = if (named != null && namedPlayer == null) null
        else if (mode == EnteringAttackDefender.CONTROLLER_CHOOSES) {
            val defenders = EnteringAttackDefenderChooser.defenders(before, context, controller, 1, namedPlayer)
                ?: return EnteringAttackDefenderChooser.pause(
                    before, effect, context, controller, 1,
                    trial.state.getEntity(tokens.single())?.get<CardComponent>()?.name ?: "Token", namedPlayer)
            defenders.single()
        } else namedPlayer ?: TargetResolutionUtils.defenderForEnteringAttacker(context, before, controller)
        if (defender == null) return trial
        var result = trial.state.updateEntity(tokens.single()) {
            it.with(AttackingComponent(defender, defendingPlayerId = CombatDefenders.defendingPlayerOf(before, defender)))
        }
        result = AttackedPermanents.markAttacked(result, defender)
        return trial.copy(state = result)
    }
}
