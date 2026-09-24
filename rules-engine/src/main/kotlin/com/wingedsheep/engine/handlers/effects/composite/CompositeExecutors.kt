package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ExecutorModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.Effect

/**
 * Module providing composite effect executors.
 *
 * These executors run sub-effects through the parent registry's execute function, which the
 * registry hands in at construction.
 */
class CompositeExecutors(
    /** The registry's re-entrant entry point, for the executors that run sub-effects. */
    private val effectExecutor: (GameState, Effect, EffectContext) -> EffectResult,
    private val cardRegistry: com.wingedsheep.engine.registry.CardRegistry,
    private val targetFinder: TargetFinder = TargetFinder(),
    private val decisionHandler: DecisionHandler = DecisionHandler()
) : ExecutorModule {
    private val compositeEffectExecutor = CompositeEffectExecutor(effectExecutor)
    private val createDelayedTriggerExecutor = CreateDelayedTriggerExecutor()
    private val forEachExecutor = ForEachExecutor(effectExecutor)
    private val forEachCapturedControllerExecutor = ForEachCapturedControllerExecutor(effectExecutor)
    private val mayRevealCardFromHandEffectExecutor = MayRevealCardFromHandEffectExecutor(effectExecutor)
    private val beholdEffectExecutor = BeholdEffectExecutor(effectExecutor)
    private val budgetModalEffectExecutor = BudgetModalEffectExecutor(effectExecutor)
    private val modalEffectExecutor = ModalEffectExecutor(effectExecutor)
    private val gatedEffectExecutor = GatedEffectExecutor(cardRegistry, effectExecutor)
    private val payManaCostExecutor = PayManaCostExecutor(cardRegistry)
    private val payDynamicManaCostExecutor = PayDynamicManaCostExecutor(cardRegistry)
    private val payManaCostRepeatedlyExecutor = PayManaCostRepeatedlyExecutor(cardRegistry, decisionHandler)
    private val reflexiveTriggerEffectExecutor = ReflexiveTriggerEffectExecutor(effectExecutor, targetFinder, decisionHandler, cardRegistry)
    private val flipCoinExecutor = FlipCoinExecutor(cardRegistry, effectExecutor, decisionHandler)
    private val repeatWhileExecutor = RepeatWhileExecutor(effectExecutor)
    private val conditionalOnCollectionExecutor = ConditionalOnCollectionExecutor(effectExecutor)
    private val flipTwoCoinsExecutor = FlipTwoCoinsExecutor(cardRegistry, effectExecutor, decisionHandler)
    private val flipCoinsExecutor = FlipCoinsExecutor(cardRegistry, decisionHandler)
    private val flipCoinsUntilLossExecutor = FlipCoinsUntilLossExecutor(cardRegistry, decisionHandler)
    private val chooseActionEffectExecutor = ChooseActionEffectExecutor(effectExecutor)
    private val repeatDynamicTimesExecutor = RepeatDynamicTimesExecutor(effectExecutor)
    private val chooseNumberThenExecutor = ChooseNumberThenExecutor(decisionHandler)

    override fun executors(): List<EffectExecutor<*>> = listOf(
        budgetModalEffectExecutor,
        chooseActionEffectExecutor,
        compositeEffectExecutor,
        createDelayedTriggerExecutor,
        forEachExecutor,
        forEachCapturedControllerExecutor,
        mayRevealCardFromHandEffectExecutor,
        beholdEffectExecutor,
        modalEffectExecutor,
        gatedEffectExecutor,
        payManaCostExecutor,
        payDynamicManaCostExecutor,
        payManaCostRepeatedlyExecutor,
        reflexiveTriggerEffectExecutor,
        flipCoinExecutor,
        flipTwoCoinsExecutor,
        flipCoinsExecutor,
        flipCoinsUntilLossExecutor,
        repeatWhileExecutor,
        repeatDynamicTimesExecutor,
        conditionalOnCollectionExecutor,
        chooseNumberThenExecutor
    )
}
