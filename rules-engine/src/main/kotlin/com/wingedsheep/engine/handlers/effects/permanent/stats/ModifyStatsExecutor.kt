package com.wingedsheep.engine.handlers.effects.permanent.stats

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.StatsModifiedEvent
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.Sublayer
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlin.reflect.KClass

/**
 * Executor for ModifyStatsEffect.
 * Applies stat changes to creatures, or to an untargeted source that may become a creature.
 *
 * Supports both fixed and dynamic amounts via [DynamicAmountEvaluator].
 */
class ModifyStatsExecutor(
    private val amountEvaluator: DynamicAmountEvaluator
) : EffectExecutor<ModifyStatsEffect> {

    override val effectType: KClass<ModifyStatsEffect> = ModifyStatsEffect::class

    override fun execute(
        state: GameState,
        effect: ModifyStatsEffect,
        context: EffectContext
    ): EffectResult {
        if (context.isUnavailableBattlefieldSource(effect.target, state)) return EffectResult.success(state)

        // Resolve the target creature
        val targetId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.error(state, "No valid target for stat modification")

        // Targeted creature effects still require a creature. A permanent's own untargeted stat
        // ability can resolve before it becomes a creature: CR 208.3a creates the modification
        // now, and layer 7c applies it if the permanent becomes a creature later (e.g. a Vehicle).
        val targetContainer = state.getEntity(targetId)
            ?: return EffectResult.error(state, "Target creature no longer exists")
        val cardComponent = targetContainer.get<CardComponent>()
            ?: return EffectResult.error(state, "Target is not a card")
        val projected = state.projectedState
        if (effect.target != EffectTarget.Self &&
            !projected.isCreature(targetId) && !targetContainer.has<FaceDownComponent>()) {
            return EffectResult.error(state, "Target is not a creature")
        }

        val powerMod = amountEvaluator.evaluate(state, effect.powerModifier, context)
        val toughnessMod = amountEvaluator.evaluate(state, effect.toughnessModifier, context)

        // Create a floating effect for the stat modification
        val newState = state.addFloatingEffect(
            layer = Layer.POWER_TOUGHNESS,
            modification = SerializableModification.ModifyPowerToughness(powerMod, toughnessMod),
            affectedEntities = setOf(targetId),
            duration = effect.duration,
            context = context,
            sublayer = Sublayer.MODIFICATIONS
        )

        // Emit event for visualization
        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "Unknown"
        val events = listOf(
            StatsModifiedEvent(
                targetId = targetId,
                targetName = cardComponent.name,
                powerChange = powerMod,
                toughnessChange = toughnessMod,
                sourceName = sourceName
            )
        )

        return EffectResult.success(newState, events)
    }
}
