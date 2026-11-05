package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.EntersWithReplacements
import com.wingedsheep.engine.handlers.effects.ReplacementEffectUtils
import com.wingedsheep.engine.mechanics.ReadAheadSynthesis
import com.wingedsheep.engine.mechanics.RiotSynthesis
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.ReceivedCountersThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EntersWithChoice

/**
 * The "as-enters" replacement pipeline shared by every path that puts a **token** onto the
 * battlefield — [TokenFromDefinition.mint] (a token minted from a bare card definition) and the four
 * token-copy executors ([CreateTokenCopyOfTargetExecutor], [CreateTokenCopyOfSourceExecutor],
 * [CreateTokenCopyOfChosenPermanentExecutor], [CreateTokenCopyOfEquippedCreatureExecutor]).
 *
 * Those executors place their tokens via the ad-hoc [com.wingedsheep.engine.handlers.effects.BattlefieldEntry.place]
 * path, which deliberately skips all enters-with replacement setup. Per CR 707.2 a copy has the
 * copied card's abilities, so a token copy must still run the copied card's "enters with counters"
 * (CR 614.1c) and "as this enters, choose …" (CR 614.12) replacements, plus any **granted** riot
 * (CR 702.136 / 702.136b — each granted instance is a separate choice) from a lord such as
 * Spider-Punk.
 *
 * Counters are applied inline by the caller through
 * [com.wingedsheep.engine.handlers.effects.EntersWithReplacements.applyOnEntry] (non-pausing). The
 * choice half is what needs a player decision mid-loop, so this object centralizes computing **which**
 * choice a just-placed token owes; the caller then pauses via
 * [com.wingedsheep.engine.handlers.effects.PermanentEntryReplacements.pauseForEntersWithChoice],
 * whose existing [com.wingedsheep.engine.core.EntersWithChoiceOnBattlefieldContinuation] resumer
 * records the value, applies the granted-riot branch, loops per riot instance, chains any further
 * printed choice, places a Saga's entry lore counters (a read-ahead Saga's count is the chosen
 * number, CR 702.155b — so a paused token skips its own Saga-entry step), and fires the token's ETB
 * triggers.
 */
object TokenEntryReplacements {
    private fun bundleFrame(state: GameState, context: com.wingedsheep.engine.handlers.EffectContext) =
        if (!context.tokenCreationReplacementsHandled) null else state.continuationStack
            .filterIsInstance<com.wingedsheep.engine.core.TokenBundleMaterializationContinuation>().lastOrNull()

    internal fun beforeEvent(state: GameState, context: com.wingedsheep.engine.handlers.EffectContext): GameState =
        bundleFrame(state, context)?.bundle?.entrySourceState ?: state

    internal fun applyInheritedBundleRiders(
        state: GameState, tokenId: EntityId, preCreationState: GameState, controllerId: EntityId,
        context: com.wingedsheep.engine.handlers.EffectContext, predicateEvaluator: PredicateEvaluator
    ): GameState {
        val frame = bundleFrame(state, context) ?: return state
        var result = applyInitialCounters(state, tokenId,
            frame.bundle.entrySourceState ?: preCreationState, controllerId,
            frame.inheritedCounters, predicateEvaluator)
        val stamp = when (val original = frame.ridersSource) {
            is com.wingedsheep.sdk.scripting.effects.CreateTokenEffect -> original.stampCreator
            is com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect -> original.stampCreator
            else -> false
        }
        val creator = frame.bundle.context.sourceId
        if (stamp && creator != null) result = result.updateEntity(tokenId) {
            it.with(com.wingedsheep.engine.state.components.identity.CreatedByComponent(creator))
        }
        return result
    }



    internal fun applyEntryTap(
        state: GameState, tokenId: EntityId, controllerId: EntityId, cardRegistry: CardRegistry?,
        predicateEvaluator: PredicateEvaluator, replacementSourceState: GameState,
        definedTapped: Boolean = false, attacking: Boolean = false
    ): GameState {
        val definition = state.getEntity(tokenId)?.get<CardComponent>()
            ?.let { cardRegistry?.getCard(it.cardDefinitionId) }
        val views = com.wingedsheep.engine.handlers.effects.entryEvaluationViews(
            state, tokenId, controllerId, replacementSourceState)
        val ownTapped = definition?.script?.replacementEffects
            ?.filterIsInstance<com.wingedsheep.sdk.scripting.EntersTapped>()
            ?.filter { it.payLifeCost == null }
            ?.any { replacement -> replacement.unlessCondition?.let { condition ->
                !predicateEvaluator.conditions.evaluate(state, condition,
                    com.wingedsheep.engine.handlers.EffectContext(sourceId = tokenId, controllerId = controllerId),
                    entryViews = views)
            } ?: true } == true
        val tapped = definedTapped || ownTapped
        return com.wingedsheep.engine.handlers.effects.EnterTappedReplacements.applyCreatedTokenEntryTap(
            state, tokenId, controllerId, definedTapped = tapped,
            attacking = attacking && state.projectedState.isCreature(tokenId),
            predicateEvaluator = predicateEvaluator, replacementSourceState = replacementSourceState)
    }

    /**
     * Give a just-placed token the counters its creating effect says it is created / enters with —
     * "create a … token with a +1/+1 counter on it" ([com.wingedsheep.sdk.scripting.effects.CreateTokenEffect.initialCounters]),
     * "the token enters with half that many +1/+1 counters on it"
     * ([com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect.initialCounters]).
     *
     * CR 122.6 counts those as counters being *put on* it, and CR 122.6a makes its controller the
     * player putting them there. So the counter-placement replacements apply (Hardened Scales,
     * Doubling Season), read against the battlefield as it stood before the tokens were created
     * ([preCreationState]) and the token's own prospective characteristics (CR 614.12), like any
     * other entrant. Stamps the same per-permanent history marker the ordinary placement paths do,
     * so counter-history readings (Kid Loki's "you've put one or more +1/+1 counters on this turn")
     * see the token and a later placement on it reports firstThisTurn = false. No
     * CountersAddedEvent is emitted: the counters arrive as part of the token's creation, not as a
     * separate placement event. Non-positive amounts put nothing.
     */
    fun applyInitialCounters(
        state: GameState,
        tokenId: EntityId,
        preCreationState: GameState,
        controllerId: EntityId,
        initialCounters: Map<CounterType, Int>,
        predicateEvaluator: PredicateEvaluator,
    ): GameState {
        if (initialCounters.values.none { it > 0 }) return state
        val prospective = EntersWithReplacements.prospectiveEntryView(state, tokenId, preCreationState)
        var counters = state.getEntity(tokenId)?.get<CountersComponent>() ?: CountersComponent()
        var history = ReceivedCountersThisTurnComponent()
        for ((counterType, amount) in initialCounters) {
            if (amount <= 0) continue
            val placed = ReplacementEffectUtils.applyCounterPlacementModifiers(
                prospective, tokenId, counterType, amount, placerId = controllerId,
                predicateEvaluator = predicateEvaluator, replacementSourceState = preCreationState, byEffect = true
            )
            if (placed <= 0) continue
            counters = counters.withAdded(counterType, placed)
            history = history.with(counterType, byController = true)
        }
        return state.updateEntity(tokenId) { c ->
            val withCounters = c.with(counters)
            if (history.counterTypes.isNotEmpty()) withCounters.with(history) else withCounters
        }
    }

    /**
     * The [EntersWithChoice] a just-placed token owes as it enters, together with the granted-riot
     * bookkeeping the choice pause needs. `null` when the token owes no choice (the common case).
     *
     * @property choice the first choice to present (printed choices and a synthesized granted-riot
     *   choice are pooled and ordered by [com.wingedsheep.sdk.scripting.ChoiceType.ordinal], matching
     *   [TokenFromDefinition]).
     * @property syntheticRiot true when [choice] is the synthesized granted-riot choice (the resumer
     *   then applies the chosen +1/+1-counter / haste branch directly — a granted permanent has no
     *   printed static to fall back on).
     * @property syntheticRiotRemaining the number of *further* granted-riot choices still owed after
     *   [choice] (CR 702.136b), 0 unless [syntheticRiot].
     */
    data class EntersWithChoicePlan(
        val choice: EntersWithChoice,
        val syntheticRiot: Boolean,
        val syntheticRiotRemaining: Int,
        val pendingEntryChoices: List<EntersWithChoice>
    )

    /**
     * Compute the first "as-enters" choice a token already on the battlefield owes, pooling the copied
     * card's printed [EntersWithChoice]s (looked up by the token's `cardDefinitionId`) with a
     * synthesized granted-riot choice. Returns `null` when there is nothing to choose.
     *
     * Kept identical in ordering to [TokenFromDefinition] so a minted token and a token copy resolve
     * their as-enters choices the same way.
     */
    fun firstEntersWithChoice(
        state: GameState,
        tokenId: EntityId,
        cardRegistry: CardRegistry,
        predicateEvaluator: PredicateEvaluator,
        replacementSourceState: GameState? = null
    ): EntersWithChoicePlan? {
        val cardComponent = state.getEntity(tokenId)?.get<CardComponent>() ?: return null
        val cardDef = cardRegistry.getCard(cardComponent.cardDefinitionId)
        val printedChoices = cardDef?.script?.replacementEffects
            ?.filterIsInstance<EntersWithChoice>()
            ?: emptyList()
        val prospective = EntersWithReplacements.prospectiveEntryView(state, tokenId, replacementSourceState)
        val grantedRiotCount = RiotSynthesis.grantedRiotInstanceCount(
            prospective, tokenId, cardRegistry, predicateEvaluator
        )
        // Resolve every riot instance independently. Printed riot uses the same branch executor
        // as granted riot here, so its choice cannot hide a grant or overwrite another MODE answer.
        val printedRiot = printedChoices.filter { it == RiotSynthesis.RIOT_CHOICE }
        val riotCount = grantedRiotCount + printedRiot.size
        val readAheadChoice = ReadAheadSynthesis.grantedChoice(prospective, tokenId, cardDef, cardRegistry, predicateEvaluator)
        val otherChoices = (printedChoices.filterNot { it == RiotSynthesis.RIOT_CHOICE } + listOfNotNull(readAheadChoice))
            .sortedBy { it.choiceType.ordinal }
        val firstChoice = if (riotCount > 0) RiotSynthesis.RIOT_CHOICE else otherChoices.firstOrNull() ?: return null
        return EntersWithChoicePlan(
            choice = firstChoice,
            syntheticRiot = riotCount > 0,
            syntheticRiotRemaining = (riotCount - 1).coerceAtLeast(0),
            pendingEntryChoices = if (riotCount > 0) otherChoices else otherChoices.drop(1)
        )
    }
}
