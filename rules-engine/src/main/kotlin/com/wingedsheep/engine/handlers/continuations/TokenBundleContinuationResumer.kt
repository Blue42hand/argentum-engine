package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.token.TokenBundleReplacements
import com.wingedsheep.engine.handlers.effects.token.InvestigateExecutor
import com.wingedsheep.engine.state.GameState

/** Resumes the pending event before any of its substituted tokens enter. */
class TokenBundleContinuationResumer(private val services: EngineServices) :
    ContinuationResumerModule, AutoResumerModule {
    private fun planner() = TokenBundleReplacements(services.cardRegistry,
        services.dynamicAmountEvaluator, services.targetFinder, services.effectExecutorRegistry::execute)

    private fun complete(result: EffectResult, events: List<GameEvent>, checkForMore: CheckForMore): ExecutionResult {
        val combined = result.copy(events = events + result.events)
        if (combined.outcome !is Outcome.Done) return combined.toExecutionResult()
        return checkForMore(exposeCollectionsToNextFrame(combined.state, combined.updatedCollections), combined.events)
    }

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(TokenBundleReplacementContinuation::class) { state, frame, response, checkForMore ->
            val planner = planner()
            val optional = frame.optional
            val result = if (frame.substitution != null) {
                if (response !is OptionChosenResponse) return@resumer ExecutionResult.error(state, "Expected token choice")
                planner.chooseSubstitution(state, frame.bundle, frame.substitution, response.optionIndex)
            } else if (optional != null) {
                if (response !is YesNoResponse) return@resumer ExecutionResult.error(state, "Expected yes/no")
                val bundle = if (response.choice) planner.apply(state, frame.bundle, optional)
                    else frame.bundle.copy(applied = frame.bundle.applied + optional.identity)
                planner.resolve(state, bundle)
            } else {
                if (response !is OptionChosenResponse) return@resumer ExecutionResult.error(state, "Expected replacement choice")
                val candidate = frame.options.getOrNull(response.optionIndex)
                    ?: return@resumer ExecutionResult.error(state, "Invalid replacement choice")
                planner.select(state, frame.bundle, candidate)
            }
            complete(result, emptyList(), checkForMore)
        }
    )

    override fun autoResumers(): List<AutoResumer<*>> = listOf(
        autoResumer(PredefinedTokenMaterializationContinuation::class) { state, frame, events, checkForMore ->
            val executor = com.wingedsheep.engine.handlers.effects.token.CreatePredefinedTokenExecutor(
                services.cardRegistry, com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler(services.cardRegistry),
                services.dynamicAmountEvaluator)
            val result = executor.createTokens(state, frame.effect, frame.context, frame.controllerId,
                frame.remaining, frame.createdTokenIds, frame.entrySourceState, frame.resolvedImageUris)
            complete(com.wingedsheep.engine.handlers.effects.token.TokenEntryGroups.stamp(result,
                com.wingedsheep.engine.handlers.effects.token.TokenEntryGroups.group(frame.context)), events, checkForMore)
        },
        autoResumer(TokenBundleMaterializationContinuation::class) { state, frame, events, checkForMore ->
            complete(planner().resumeMaterialization(state, frame), events, checkForMore)
        },
        autoResumer(CompleteInvestigationContinuation::class) { state, frame, events, checkForMore ->
            val marked = InvestigateExecutor.markInvestigation(state, frame.investigatorId, frame.sourceName)
            val result = InvestigateExecutor.continueInvestigating(services.effectExecutorRegistry::execute,
                marked.state, frame.investigatorId, frame.remaining, frame.context, frame.sourceName,
                frame.initialTokenIds)
            complete(result, events + marked.events, checkForMore)
        }
    )
}
