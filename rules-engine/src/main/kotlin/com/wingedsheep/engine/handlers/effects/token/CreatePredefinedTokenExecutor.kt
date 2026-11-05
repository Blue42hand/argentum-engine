package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.registry.TokenArtRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.DoubleFacedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CREATED_TOKENS
import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect
import kotlin.reflect.KClass

/**
 * Unified executor for all predefined token types (Treasure, Food, Lander, etc.).
 *
 * Looks up the token's CardDefinition from the CardRegistry to read type line, imageUri,
 * and abilities. The CardDefinition name must match [CreatePredefinedTokenEffect.tokenType].
 *
 * To add a new predefined token type, define it in `PredefinedTokens.kt` and add a facade
 * method to `Effects.kt` — no new executor needed.
 */
class CreatePredefinedTokenExecutor(
    private val cardRegistry: CardRegistry,
    private val staticAbilityHandler: StaticAbilityHandler? = null,
    private val amountEvaluator: DynamicAmountEvaluator,
    private val tokenArtRegistry: TokenArtRegistry? = null
) : EffectExecutor<CreatePredefinedTokenEffect> {

    override val effectType: KClass<CreatePredefinedTokenEffect> = CreatePredefinedTokenEffect::class

    /** Builds the substitute tokens of a `ReplaceTokenCreationWithToken` (Draconic Visitor). */
    private val substituteExecutor by lazy {
        CreateTokenExecutor(amountEvaluator, staticAbilityHandler, cardRegistry, tokenArtRegistry)
    }

    override fun execute(
        state: GameState,
        effect: CreatePredefinedTokenEffect,
        context: EffectContext
    ): EffectResult = execute(state, effect, context, rootEffect = effect)

    /**
     * [rootEffect] is the instruction a replacement question re-runs from the top once answered —
     * [effect] itself, or the instruction that delegated here (investigate), so the re-run redoes
     * that instruction's own bookkeeping too.
     */
    internal fun execute(
        state: GameState,
        effect: CreatePredefinedTokenEffect,
        originalContext: EffectContext,
        rootEffect: com.wingedsheep.sdk.scripting.effects.Effect
    ): EffectResult {
        val creator = effect.controller?.let {
            originalContext.resolvePlayerTarget(it, state) ?: return EffectResult.success(state)
        } ?: originalContext.controllerId
        val context = TokenCreationReplacementHelper.withCreator(originalContext, creator)
        val cardDef = cardRegistry.getCard(effect.tokenType)
            ?: return EffectResult.error(state, "No CardDefinition registered for predefined token '${effect.tokenType}'")

        // A named controller that resolves to nobody ("its controller" after an "up to one
        // target" chose none) creates nothing — it never falls back to the source's controller.
        val controller = effect.controller
        val wouldBeControllerId = if (controller != null) {
            context.resolvePlayerTarget(controller, state)
                ?: return EffectResult.success(state)
        } else {
            context.controllerId
        }
        // "Created under your control instead" (Crafty Cutpurse) before any other token
        // replacement; a batch already redirected its whole event.
        val tokenControllerId = if (TokenCreationBatch.active(context)) wouldBeControllerId
        else when (val redirect = TokenCreationReplacementHelper.redirectController(
            state, wouldBeControllerId, context, rootEffect, amountEvaluator.predicates
        )) {
            is TokenControllerRedirect.Resolved -> redirect.controllerId
            is TokenControllerRedirect.Ask -> return redirect.result
        }

        // Evaluate dynamic count if set (e.g. Lobelia's "X = the exiled card's power"),
        // otherwise use the fixed count. Coerced to >= 0 — a negative count would be a
        // bug elsewhere, but clamping defends against odd dynamic-amount edge cases.
        val baseTokenCount = effect.dynamicCount?.let { dyn ->
            amountEvaluator.evaluate(state, dyn, context).coerceAtLeast(0)
        } ?: effect.count

        // Token-count replacements (Doubling Season, Mondrak, Glory Dominus) apply to predefined
        // tokens exactly as to CreateTokenEffect tokens — before any substitution gets a look,
        // the same order CreateTokenExecutor uses.
        val tokenCount = com.wingedsheep.engine.core.GameLimits.cappedTokenCount(
            if (TokenCreationBatch.active(context)) baseTokenCount else TokenCreationReplacementHelper.applyCountReplacements(
                state, tokenControllerId, baseTokenCount,
                predicateEvaluator = amountEvaluator.predicates, handled = context.tokenCreationReplacementsHandled
            ),
            "predefined tokens"
        )

        // Check for token creation replacement effects (e.g., Mirrormind Crown)
        val replacementResult = if (TokenCreationBatch.active(context)) null else TokenCreationReplacementHelper.checkReplacement(
            state, effect, context, tokenCount, tokenControllerId, cardRegistry, staticAbilityHandler,
            predicateEvaluator = amountEvaluator.predicates
        )
        if (replacementResult != null) return replacementResult

        // "If one or more artifact tokens would be created under your control, that many 5/5 red
        // Dragon creature tokens with flying are created instead" (Draconic Visitor). Treasure,
        // Clue, Food, Map and the rest are artifact tokens, so this is the path it bites hardest.
        val prospective = prospectiveToken(cardDef, effect, tokenControllerId)
        TokenCreationReplacementHelper.findTokenSubstitution(
            state, tokenControllerId, prospective,
            predicateEvaluator = amountEvaluator.predicates,
            eligibleSources = TokenCreationBatch.sources(context),
            choices = context.pipeline.chosenValues,
            handled = context.tokenCreationReplacementsHandled,
        )?.let { lookup ->
            return when (lookup) {
                is TokenSubstitution -> substituteExecutor.createSubstituteTokens(
                    state, lookup, context, tokenCount, tokenControllerId,
                    riders = TokenRiders(tapped = effect.tapped), rootEffect = rootEffect
                )
                is TokenSubstitutionChoice ->
                    TokenCreationReplacementHelper.askForSubstitution(state, lookup, rootEffect, context, tokenCount)
            }
        }

        // Art: an explicit per-card override wins, then the art printed by the set the creating
        // card came from (so a reprint mints its own set's Treasure), then the one canonical
        // printing shared engine-wide by PredefinedTokens.
        //
        // A list, because a set may have printed the same token with several illustrations: the
        // batch is dealt out of it in order and wraps. Indexing by position in the batch keeps it
        // deterministic, so a replay re-simulates the same board. See CreateTokenExecutor.
        val sourceCard = context.sourceId
            ?.let { state.getEntity(it) }
            ?.get<CardComponent>()
        val resolvedImageUris = effect.imageUri?.let(::listOf)
            ?: tokenArtRegistry?.resolveAll(
                sourceCardDefinitionId = sourceCard?.cardDefinitionId,
                tokenName = effect.tokenType,
                sourcePrintingSetCode = sourceCard?.printingSetCode,
            )?.takeIf { it.isNotEmpty() }
            ?: listOf(cardDef.metadata.imageUri)

        return createTokens(state, effect, context, tokenControllerId, tokenCount, emptyList(),
            TokenEntryReplacements.beforeEvent(state, context).copy(continuationStack = emptyList()), resolvedImageUris)
    }

    /** Resume the already-replaced event; neither its counts nor its substitutions run twice. */
    internal fun createTokens(
        state: GameState,
        effect: CreatePredefinedTokenEffect,
        context: EffectContext,
        tokenControllerId: EntityId,
        tokenCount: Int,
        previousTokenIds: List<EntityId>,
        entrySourceState: GameState,
        resolvedImageUris: List<String?>
    ): EffectResult {
        val cardDef = cardRegistry.requireCard(effect.tokenType)
        var newState = state
        val createdTokenIds = previousTokenIds.toMutableList()
        val events = mutableListOf<com.wingedsheep.engine.core.GameEvent>()

        repeat(tokenCount) { indexInBatch ->
            val resolvedImageUri = resolvedImageUris[(previousTokenIds.size + indexInBatch) % resolvedImageUris.size]
            val (tokenId, stateWithId) = newState.newEntity()
            newState = stateWithId
            createdTokenIds.add(tokenId)

            val tokenComponent = CardComponent(
                cardDefinitionId = effect.tokenType,
                name = effect.tokenType,
                manaCost = cardDef.manaCost,
                typeLine = cardDef.typeLine,
                baseStats = cardDef.creatureStats,
                baseKeywords = cardDef.keywords,
                // A token has no mana cost unless its creator defines one (CR 202.1b) — the
                // Spellgorger Weird is a {2}{R} token — so the definition's cost is carried as
                // printed (ManaCost.ZERO when it has none). A colored token without a cost has
                // its color in its color indicator (CR 204), stored as colorIndicator;
                // otherwise its colors derive from that mana cost.
                colors = cardDef.colors,
                ownerId = tokenControllerId,
                imageUri = resolvedImageUri
            )

            var container = ComponentContainer.of(
                tokenComponent,
                TokenComponent,
                ControllerComponent(tokenControllerId),
                SummoningSicknessComponent,
                EnteredThisTurnComponent
            )

            if (effect.tapped) {
                container = container.with(TappedComponent)
            }

            // Printed keyword abilities that live on their own component rather than in
            // `baseKeywords` — toxic N above all (the Phyrexian Mite's toxic 1) — are attached the
            // same way a card's are, so a predefined token never silently loses one.
            container = com.wingedsheep.engine.core.CardEntityFactory
                .applyDefinitionDecorations(container, cardDef)

            // Transforming double-faced tokens (CR 701.51b — Incubator). The token
            // enters with its front face up; the back face's CardDefinition is
            // already auto-registered in the CardRegistry by registry.register(...).
            cardDef.backFace?.let { backFace ->
                container = container.with(
                    DoubleFacedComponent(
                        frontCardDefinitionId = cardDef.name,
                        backCardDefinitionId = backFace.name,
                        currentFace = DoubleFacedComponent.Face.FRONT
                    )
                )
            }

            // Wire up static abilities (e.g., equip bonuses, lord effects) from the
            // predefined token's CardDefinition into a ContinuousEffectSourceComponent
            // so the StateProjector applies them.
            if (staticAbilityHandler != null) {
                container = staticAbilityHandler.addContinuousEffectComponent(container, cardDef)
                container = staticAbilityHandler.addReplacementEffectComponent(container, cardDef)
            }

            newState = newState.withEntity(tokenId, container)

            newState = com.wingedsheep.engine.handlers.effects.copy.GrantedEntryCopy.applyBeforeEntry(newState, tokenId, cardRegistry, amountEvaluator.predicates)
            newState = TokenEntryReplacements.applyInheritedBundleRiders(newState, tokenId, state,
                tokenControllerId, context, amountEvaluator.predicates)
            newState = com.wingedsheep.engine.handlers.effects.BattlefieldEntry
                .place(newState, tokenControllerId, tokenId, tokenCreatorId = TokenCreationReplacementHelper.creatorId(context, tokenControllerId))

            // Predefined Map/Treasure/Clue/etc. tokens honor global "[filter] enter tapped"
            // replacements (Dauntless Dismantler taps an opponent's artifact token).
            newState = TokenEntryReplacements
                .applyEntryTap(
                    newState, tokenId, tokenControllerId, cardRegistry, definedTapped = effect.tapped,
                    predicateEvaluator = amountEvaluator.predicates, replacementSourceState = entrySourceState
                )

            val (withCounters, counterEvents) = com.wingedsheep.engine.handlers.effects.EntersWithReplacements.applyOnEntry(
                newState, tokenId, tokenControllerId, cardRegistry,
                predicateEvaluator = amountEvaluator.predicates, replacementSourceState = entrySourceState)
            newState = withCounters
            events += counterEvents

            // Use the copied identity, and defer its entry event until every as-enters choice finishes.
            val enteringCard = requireNotNull(newState.getEntity(tokenId)?.get<CardComponent>())
            val choicePlan = TokenEntryReplacements.firstEntersWithChoice(
                newState, tokenId, cardRegistry, predicateEvaluator = amountEvaluator.predicates,
                replacementSourceState = entrySourceState)
            if (choicePlan != null) {
                val pending = newState.pushContinuation(com.wingedsheep.engine.core.PredefinedTokenMaterializationContinuation(
                    effect, context, tokenControllerId, tokenCount - indexInBatch - 1,
                    createdTokenIds.toList(), entrySourceState, resolvedImageUris))
                val paused = com.wingedsheep.engine.handlers.effects.PermanentEntryReplacements.pauseForEntersWithChoice(
                    state = pending, entityId = tokenId, controllerId = tokenControllerId,
                    cardComponent = enteringCard, choice = choicePlan.choice, fromZone = null,
                    carryEvents = events, syntheticRiot = choicePlan.syntheticRiot,
                    syntheticRiotRemaining = choicePlan.syntheticRiotRemaining,
                    pendingEntryChoices = choicePlan.pendingEntryChoices,
                    replacementSourceState = entrySourceState)
                if (paused != null) return EffectResult.from(paused)
            }

            val (withSaga, sagaEvents) = com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
                .applySagaEntryIfNeeded(newState, tokenId, amountEvaluator.predicates, replacementSourceState = entrySourceState)
            newState = withSaga
            events += sagaEvents
            val (withIntrinsic, intrinsicEvents) = com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
                .applyIntrinsicEntryCountersIfNeeded(newState, tokenId, tokenControllerId, cardRegistry,
                    predicateEvaluator = amountEvaluator.predicates, replacementSourceState = entrySourceState)
            newState = withIntrinsic
            events += intrinsicEvents
            events += ZoneChangeEvent(
                entityId = tokenId, entityName = enteringCard.name, fromZone = null, toZone = Zone.BATTLEFIELD,
                ownerId = tokenControllerId, oldObject = null, newObject = newState.objectRef(tokenId))
        }

        // Apply "create those tokens plus an additional X token" replacements (Worldwalker
        // Helm) once for this batch. Only the original tokens are matched against the filter,
        // so the added token can't recursively re-trigger.
        val (afterAdditional, additionalEvents) = if (TokenCreationBatch.active(context)) newState to emptyList() else TokenCreationReplacementHelper
            .applyAdditionalTokenReplacements(
                newState, tokenControllerId, createdTokenIds, effect.tapped,
                cardRegistry, staticAbilityHandler, amountEvaluator.predicates, tokenCreatorId = TokenCreationReplacementHelper.creatorId(context, tokenControllerId),
                eligibleSources = TokenCreationBatch.sources(context)
                    ?: com.wingedsheep.engine.replacement.ActiveReplacements.all(entrySourceState).map { it.sourceId }.toSet(),
                handled = context.tokenCreationReplacementsHandled
            )
        newState = afterAdditional
        events.addAll(additionalEvents)
        if (!TokenCreationBatch.active(context) && newState.continuationStack.none {
                it is com.wingedsheep.engine.core.TokenBundleMaterializationContinuation
            }) newState = com.wingedsheep.engine.replacement.ActiveReplacements.purgeSpent(newState)

        // Publish the freshly-created token entity IDs to the pipeline so
        // sibling effects in a CompositeEffect can address them via
        // EffectTarget.PipelineTarget(CREATED_TOKENS, index). This is how
        // Effects.Incubate(N) puts +1/+1 counters on the token it just
        // produced without threading an entity reference through a custom executor.
        return EffectResult(
            state = newState,
            events = events,
            updatedCollections = mapOf(CREATED_TOKENS to createdTokenIds)
        )
    }

    /** The would-be predefined token, for the replacements that test a token before it exists. */
    internal fun prospectiveToken(
        cardDef: com.wingedsheep.sdk.model.CardDefinition,
        effect: CreatePredefinedTokenEffect,
        tokenControllerId: EntityId
    ) = CardComponent(
        cardDefinitionId = effect.tokenType,
        name = effect.tokenType,
        manaCost = cardDef.manaCost,
        typeLine = cardDef.typeLine,
        baseStats = cardDef.creatureStats,
        baseKeywords = cardDef.keywords,
        colors = cardDef.colors,
        ownerId = tokenControllerId
    )
}
