package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ExecutorModule
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.Effect

/**
 * Module providing all player-related effect executors.
 *
 * PayOrSufferExecutor runs arbitrary suffer effects through the parent registry's execute
 * function, which the registry hands in at construction.
 */
class PlayerExecutors(
    /** The registry's re-entrant entry point, for the executors that run sub-effects. */
    private val effectExecutor: (GameState, Effect, EffectContext) -> EffectResult,
    private val zones: ZoneTransitionService,
    private val decisionHandler: DecisionHandler = DecisionHandler(),
    private val cardRegistry: CardRegistry
) : ExecutorModule {
    private val payOrSufferExecutor = PayOrSufferExecutor(zones, cardRegistry = cardRegistry, executeEffect = effectExecutor)

    private val openLifeBidExecutor = OpenLifeBidExecutor(executeEffect = effectExecutor)

    override fun executors(): List<EffectExecutor<*>> = listOf(
        AmassExecutor(effectExecutor),
        CollectEvidenceExecutor(zones, decisionHandler),
        CollectEvidenceChosenAmountExecutor(),
        AddAdditionalUpkeepStepsExecutor(),
        AddAdditionalEndStepsExecutor(),
        AddCombatPhaseExecutor(),
        AddMainPhaseExecutor(),
        AnyPlayerMayPayExecutor(executeEffect = effectExecutor),
        CantActivateLoyaltyAbilitiesExecutor(),
        CantCastSpellsExecutor(),
        CantSearchLibrariesExecutor(),
        CantCastSpellsFromNonHandZonesExecutor(),
        CantPlayCardsFromHandExecutor(),
        ChooseNumberForSourceExecutor(decisionHandler),
        ChooseOpponentForSourceExecutor(),
        ChooseCardTypeForSourceExecutor(),
        CreateGlobalTriggeredAbilityExecutor(),
        CreatePermanentEmblemExecutor(),
        EachPlayerChoosesCreatureTypeExecutor(),
        EndTheTurnExecutor(),
        GainCitysBlessingExecutor(),
        ChangeSpeedExecutor(),
        RemoveMaximumHandSizeExecutor(),
        ReduceMaximumHandSizeExecutor(),
        GiftGivenExecutor(),
        ForagedExecutor(),
        GrantCastCreaturesFromGraveyardWithForageExecutor(),
        GrantFlashToSpellsExecutor(),
        GrantSpellKeywordExecutor(),
        GrantSpellsCantBeCounteredExecutor(),
        GrantDamageBonusExecutor(),
        GrantEvasionKeywordExecutor(),
        GrantPlayerProtectionExecutor(),
        HijackNextTurnExecutor(),
        ControlCombatDeclarationsExecutor(),
        LockLifeGainExecutor(),
        openLifeBidExecutor,
        LoseGameExecutor(),
        WinGameExecutor(),
        payOrSufferExecutor,
        PlayAdditionalLandsExecutor(),
        PreventLandPlaysThisTurnExecutor(),
        SecretBidExecutor(decisionHandler),
        SetDayNightExecutor(cardRegistry),
        SkipCombatPhasesExecutor(),
        SkipNextDrawStepExecutor(),
        SkipStepOrPhaseThisTurnExecutor(),
        PayAnyAmountOfLifeAsEntersExecutor(),
        SkipNextTurnExecutor(),
        SkipUntapExecutor(),
        TakeExtraTurnExecutor(),
        TheRingTemptsYouExecutor()
    )
}
