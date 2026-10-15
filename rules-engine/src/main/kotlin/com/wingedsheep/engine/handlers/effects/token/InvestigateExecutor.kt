package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.InvestigatedThisTurnComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlin.reflect.KClass

/** Each investigation is a separate token creation event, completed after replacement decisions. */
class InvestigateExecutor(
    private val createPredefinedToken: CreatePredefinedTokenExecutor,
    private val amountEvaluator: DynamicAmountEvaluator,
    private val executeEffect: ((GameState, Effect, EffectContext) -> EffectResult)? = null
) : EffectExecutor<InvestigateEffect> {
    override val effectType: KClass<InvestigateEffect> = InvestigateEffect::class

    override fun execute(state: GameState, effect: InvestigateEffect, context: EffectContext): EffectResult {
        val investigator = effect.controller?.let { context.resolvePlayerTarget(it, state) } ?: context.controllerId
        val execute = executeEffect ?: { s: GameState, e: Effect, c: EffectContext ->
            createPredefinedToken.execute(s, e as CreatePredefinedTokenEffect, c)
        }
        return continueInvestigating(execute, state, investigator,
            amountEvaluator.evaluate(state, effect.count, context).coerceAtLeast(0), context,
            context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name },
            state.entities.filterValues { it.has<TokenComponent>() }.keys)
    }

    companion object {
        fun markInvestigation(state: GameState, investigator: EntityId, sourceName: String?): EffectResult {
            val already = state.getEntity(investigator)?.has<InvestigatedThisTurnComponent>() == true
            val marked = if (already) state else state.updateEntity(investigator) { it.with(InvestigatedThisTurnComponent) }
            return EffectResult(marked, listOf(InvestigatedEvent(investigator, !already, sourceName)))
        }

        fun continueInvestigating(execute: (GameState, Effect, EffectContext) -> EffectResult,
            state: GameState, investigator: EntityId, times: Int, context: EffectContext,
            sourceName: String?, initialTokenIds: Set<EntityId>): EffectResult {
            var current = state
            val events = mutableListOf<GameEvent>()
            for (index in 0 until times) {
                val prepared = current.pushContinuation(CompleteInvestigationContinuation(
                    investigator, times - index - 1, context, sourceName, initialTokenIds))
                val created = execute(prepared, CreatePredefinedTokenEffect("Clue",
                    controller = EffectTarget.SpecificEntity(investigator)), context)
                events += created.events
                if (created.outcome !is Outcome.Done) return created.copy(events = events)
                val marked = markInvestigation(created.state.popContinuation().second, investigator, sourceName)
                current = marked.state
                events += marked.events
            }
            val tokens = current.entities.filter { (id, entity) -> id !in initialTokenIds && entity.has<TokenComponent>() }.keys.toList()
            return EffectResult(current, events, updatedCollections = mapOf(CREATED_TOKENS to tokens))
        }
    }
}
