package com.wingedsheep.engine.handlers.effects.permanent.abilities

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.CantHaveKeywordsEffect
import kotlin.reflect.KClass

/** Applies a snapshot-scoped keyword prohibition; normal floating-effect cleanup controls its lifetime. */
class CantHaveKeywordsExecutor : EffectExecutor<CantHaveKeywordsEffect> {

    override val effectType: KClass<CantHaveKeywordsEffect> = CantHaveKeywordsEffect::class

    override fun execute(
        state: GameState,
        effect: CantHaveKeywordsEffect,
        context: EffectContext
    ): EffectResult {
        if (context.isUnavailableBattlefieldSource(effect.target, state)) return EffectResult.success(state)

        // The two-arg overload is required: the one-arg form can't resolve attachment-relative
        // targets (EffectTarget.AttachedTo and friends).
        val targetId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.error(state, "No valid target for keyword removal")

        val targetContainer = state.getEntity(targetId)
            ?: return EffectResult.error(state, "Target permanent no longer exists")
        targetContainer.get<CardComponent>()
            ?: return EffectResult.error(state, "Target is not a card")
        if (targetId !in state.getBattlefield()) {
            return EffectResult.error(state, "Target is no longer on the battlefield")
        }

        val newState = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.CantHaveKeywords(effect.keywords.mapTo(linkedSetOf()) { it.name }),
            affectedEntities = setOf(targetId),
            duration = effect.duration,
            context = context
        )

        return EffectResult.success(newState, listOf(com.wingedsheep.engine.core.KeywordsProhibitedEvent(targetId, effect.keywords.mapTo(linkedSetOf()) { it.name })))
    }
}
