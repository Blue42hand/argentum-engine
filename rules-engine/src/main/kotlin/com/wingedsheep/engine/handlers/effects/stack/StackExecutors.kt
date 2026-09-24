package com.wingedsheep.engine.handlers.effects.stack

import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ExecutorModule
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.mechanics.stack.SpellCounterer
import com.wingedsheep.engine.registry.CardRegistry

/**
 * Module providing all stack-related effect executors.
 */
class StackExecutors(
    private val zones: ZoneTransitionService,
    private val amountEvaluator: DynamicAmountEvaluator = DynamicAmountEvaluator(),
    private val cardRegistry: CardRegistry,
    private val counterer: SpellCounterer
) : ExecutorModule {
    override fun executors(): List<EffectExecutor<*>> = listOf(
        CounterEffectExecutor(amountEvaluator, cardRegistry, counterer),
        ExileTargetSpellExecutor(counterer),
        ExileSpellsOnStackExecutor(counterer),
        CounterAllOnStackExecutor(counterer),
        WardCounterEffectExecutor(zones, cardRegistry, counterer),
        ChangeSpellTargetExecutor(),
        ChangeTargetExecutor(),
        StormCopyEffectExecutor(),
        CopyTargetSpellExecutor(),
        CopyEachTargetSpellExecutor(),
        CopySpellForEachOtherPossibleTargetExecutor(),
        CopyTargetTriggeredAbilityExecutor(),
        CopyTargetSpellOrAbilityExecutor(),
        CopyNextSpellCastExecutor(),
        CopyEachSpellCastExecutor(),
        MakeNextSpellUncounterableExecutor(),
        GrantNextSpellAffinityExecutor(),
        GrantNextSpellFreeCastExecutor(),
        ReduceSpellCostsThisTurnExecutor(amountEvaluator),
        ReselectTargetRandomlyExecutor(),
        ChangeTriggeringObjectTargetsExecutor(),
        GrantKeywordToSpellExecutor(),
        MarkSpellExileWithCountersExecutor(),
        MarkSpellPlotOnResolveExecutor(),
        ReturnSpellToOwnersHandExecutor(),
        ReturnSpellOrPermanentToOwnersHandExecutor(zones, cardRegistry),
        DestroySourceOfTargetedAbilityExecutor(zones),
        RemoveAbilitiesFromSourceOfTargetedAbilityExecutor()
    )
}
