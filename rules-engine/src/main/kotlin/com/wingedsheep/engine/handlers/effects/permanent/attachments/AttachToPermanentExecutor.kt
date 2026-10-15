package com.wingedsheep.engine.handlers.effects.permanent.attachments

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.AttachToPermanentEffect
import kotlin.reflect.KClass

class AttachToPermanentExecutor(
    private val predicates: PredicateEvaluator,
    private val cards: CardRegistry,
) : EffectExecutor<AttachToPermanentEffect> {
    override val effectType: KClass<AttachToPermanentEffect> = AttachToPermanentEffect::class
    override fun execute(state: GameState, effect: AttachToPermanentEffect, context: EffectContext): EffectResult {
        if (context.isUnavailableBattlefieldSource(effect.attachment, state)) return EffectResult.success(state)
        val attachment = context.resolveTarget(effect.attachment, state) ?: return EffectResult.success(state)
        val host = context.resolveTarget(effect.host, state) ?: return EffectResult.success(state)
        if (!AttachmentMover.canAttach(state, predicates, cards, attachment, host)) return EffectResult.success(state)
        val (next, events) = AttachmentMover.attach(state, attachment, host, context.controllerId)
        return EffectResult.success(next, events)
    }
}
