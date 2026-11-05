package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.core.TokenCreationBatch as TokenBundleBatch
import com.wingedsheep.engine.event.DelayedTriggeredAbility
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.handlers.effects.copy.CopyExceptionApplier
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.replacement.ActiveReplacements
import com.wingedsheep.engine.replacement.ReplacementEffectIdentity
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * A creation event stays pending as a bundle until all chosen replacements have applied.
 * No tokens enter while replacement order is chosen. Equal batches are coalesced after
 * every substitution, so many replacement instances do not expand an exponential tree.
 *
 * The legacy single-kind path remains available when no multi-kind substitution is in
 * force. Materialization uses native executors with this event's replacements disabled;
 * independently executed child instructions start fresh replacement events.
 */
class TokenBundleReplacements(
    private val cards: CardRegistry,
    private val amounts: DynamicAmountEvaluator,
    private val targetFinder: TargetFinder,
    private val execute: (GameState, Effect, EffectContext) -> EffectResult
) {
    companion object {
        fun enabled(state: GameState, context: EffectContext): Boolean =
            !context.tokenCreationReplacementsHandled && ActiveReplacements.all(state).any {
                it.effect is ReplaceTokenCreationWithTokens &&
                    (TokenCreationBatch.sources(context)?.let { sources -> it.sourceId in sources } ?: true) &&
                    (it.granted || (!state.projectedState.hasLostAllAbilities(it.sourceId) &&
                        !state.projectedState.isFaceDown(it.sourceId)))
            }
    }

    private fun definitionCard(def: CardDefinition, owner: EntityId) = CardComponent(
        cardDefinitionId = def.name, name = def.name, manaCost = def.manaCost,
        typeLine = def.typeLine, baseStats = def.creatureStats, baseKeywords = def.keywords,
        colors = def.colors, ownerId = owner
    )

    private fun scoped(state: GameState, context: EffectContext): Pair<GameState, EffectContext> {
        val existing = TokenEntryGroups.group(context)
        val (group, next) = if (existing != null) existing to state else state.newRoutingId()
        return next to TokenEntryGroups.withGroup(context, group)
    }

    fun intercept(state: GameState, effect: Effect, context: EffectContext): EffectResult? {
        if (effect !is CreateTokenEffect && effect !is CreatePredefinedTokenEffect &&
            effect !is CreateTokenCopyOfTargetEffect && effect !is CreateTokenCopyOfSourceEffect &&
            effect !is CreateTokenCopyOfEquippedCreatureEffect && effect !is CreateAuraTokenEffect &&
            effect !is CreateTokenBatchEffect) return null
        if (effect is CreateTokenBatchEffect && effect.groups.isEmpty()) return null
        if (!enabled(state, context)) return null
        val controllerTarget = when (effect) {
            is CreateTokenEffect -> effect.controller
            is CreatePredefinedTokenEffect -> effect.controller
            is CreateTokenCopyOfTargetEffect -> effect.controller
            is CreateTokenBatchEffect -> effect.controller
            else -> null
        }
        val recipients = if (effect is CreateTokenBatchEffect && controllerTarget != null)
            listOfNotNull(context.resolvePlayerTarget(controllerTarget, state))
        else controllerTarget?.let { context.resolvePlayerTargets(it, state) }
            ?: listOf(context.controllerId)
        if (recipients.isEmpty()) return EffectResult.success(state)
        val (next, ctx) = scoped(state, context)
        val eligible = TokenCreationBatch.sources(context)
            ?: ActiveReplacements.all(state).map { it.sourceId }.toSet()
        val groups = if (effect is CreateTokenBatchEffect) effect.groups else listOf(effect)
        // Resolve every redirect before materializing any recipient, so replay cannot duplicate tokens.
        val redirected = recipients.map { wouldBe ->
            val owner = when (val redirect = TokenCreationReplacementHelper.redirectController(
                state, wouldBe, context, effect, amounts.predicates)) {
                is TokenControllerRedirect.Resolved -> redirect.controllerId
                is TokenControllerRedirect.Ask -> return redirect.result
            }
            wouldBe to owner
        }
        val bundles = redirected.mapNotNull { (wouldBe, owner) ->
            val batches = groups.mapNotNull { prepare(state, it, ctx, owner) }.filter { it.count > 0 }
            if (batches.isEmpty()) null else TokenCreationBundle(owner, ctx, batches, wouldBeControllerId = wouldBe,
                eligibleSources = eligible,
                initialTokenIds = state.entities.filterValues { it.has<TokenComponent>() }.keys,
                entrySourceState = state.copy(continuationStack = emptyList()))
        }
        if (bundles.isEmpty()) return EffectResult.success(state)
        return resolve(next, bundles.first().copy(following = bundles.drop(1)))
    }

    /** Choice-based factories call this only after their choice has been made. */
    fun fromDefinition(state: GameState, name: String, context: EffectContext, controllerId: EntityId = context.controllerId): EffectResult {
        val def = cards.requireCard(name)
        val batch = TokenBundleBatch(null, name, definitionCard(def, controllerId), 1)
        val (next, ctx) = scoped(state, context)
        return resolve(next, TokenCreationBundle(controllerId, ctx, listOf(batch), wouldBeControllerId = context.controllerId,
            eligibleSources = TokenCreationBatch.sources(context)
                ?: ActiveReplacements.all(state).map { it.sourceId }.toSet(),
            initialTokenIds = state.entities.filterValues { it.has<TokenComponent>() }.keys,
                entrySourceState = state.copy(continuationStack = emptyList())))
    }

    private fun prepare(state: GameState, effect: Effect, ctx: EffectContext, owner: EntityId): TokenBundleBatch? {
        val recipient = EffectTarget.SpecificEntity(owner)
        return when (effect) {
            is CreatePredefinedTokenEffect -> {
                val def = cards.getCard(effect.tokenType) ?: return null
                TokenBundleBatch(effect.copy(count = 1, dynamicCount = null, controller = recipient),
                    prospectiveCard = definitionCard(def, owner),
                    count = effect.dynamicCount?.let { amounts.evaluate(state, it, ctx) } ?: effect.count)
            }
            is CreateTokenEffect -> {
                val identity = CreateTokenExecutor(amounts, cardRegistry = cards).identity(state, effect, ctx)
                val frozen = effect.copy(count = DynamicAmount.Fixed(1), controller = recipient,
                    power = identity.power, toughness = identity.toughness,
                    dynamicPower = null, dynamicToughness = null,
                    colors = identity.colors, colorsFromChoice = null,
                    creatureTypes = identity.creatureTypes, creatureTypesFromChoice = null)
                TokenBundleBatch(frozen, prospectiveCard = identity.prospective(frozen, owner),
                    count = amounts.evaluate(state, effect.count, ctx), entryCounters = effect.initialCounters)
            }
            is CreateTokenCopyOfTargetEffect -> {
                val executor = CreateTokenCopyOfTargetExecutor(amounts, cardRegistry = cards, targetFinder = targetFinder)
                val source = executor.copySource(state, effect, ctx)?.copiableCardComponent() ?: return null
                val exceptions = executor.resolvedExceptions(state, effect, ctx)
                val frozen = effect.copy(count = DynamicAmount.Fixed(1), controller = recipient,
                    exceptions = exceptions, basePower = null, baseToughness = null,
                    initialCounters = effect.initialCounters.mapValues { (_, amount) -> DynamicAmount.Fixed(amounts.evaluate(state, amount, ctx)) })
                TokenBundleBatch(frozen,
                    prospectiveCard = executor.prospectiveToken(source, exceptions, frozen, ctx, owner),
                    count = amounts.evaluate(state, effect.count, ctx),
                    entryCounters = effect.initialCounters.mapValues { (_, amount) -> amounts.evaluate(state, amount, ctx) })
            }
            is CreateTokenCopyOfSourceEffect -> {
                val id = ctx.sourceId ?: return null
                val departed = ctx.lastKnownSourceSnapshot
                    ?.takeIf { ctx.sourceReferenceLost || id !in state.getBattlefield() }?.copiableContainer()
                val source = (departed ?: state.getEntity(id))?.copiableCardComponent() ?: return null
                TokenBundleBatch(effect.copy(count = 1),
                    prospectiveCard = CopyExceptionApplier.apply(source, effect.copyExceptions, ctx.resolvingTriggeredAbility)
                        .copy(ownerId = owner, isDoubleFaced = false),
                    count = effect.count)
            }
            is CreateTokenCopyOfEquippedCreatureEffect -> {
                val host = ctx.sourceId?.let { state.getEntity(it)?.get<AttachedToComponent>()?.targetId } ?: return null
                var card = state.getEntity(host)?.copiableCardComponent() ?: return null
                if (effect.removeLegendary) card = card.copy(typeLine = card.typeLine.withoutLegendary())
                TokenBundleBatch(effect, prospectiveCard = card.copy(ownerId = owner), count = 1)
            }
            is CreateAuraTokenEffect -> {
                val host = ctx.resolveTarget(effect.target, state)?.takeIf { it in state.getBattlefield() }
                    ?: return null
                val def = cards.getCard(effect.tokenName) ?: return null
                val card = definitionCard(def, owner)
                val (probeId, withId) = state.newEntity()
                val probe = withId.withEntity(probeId, ComponentContainer.of(card, ControllerComponent(owner)))
                if (!com.wingedsheep.engine.handlers.predicates.EnchantRestriction.couldAttach(
                    probe, probe.projectedState, amounts.predicates, cards, probeId, card, host, owner)) return null
                TokenBundleBatch(effect, prospectiveCard = card, count = 1)
            }
            else -> null
        }
    }

    private fun matches(state: GameState, bundle: TokenCreationBundle, replacement: TokenBundleReplacement,
                        batch: TokenBundleBatch): Boolean {
        if (batch.count <= 0) return false
        val event = replacement.effect.appliesTo as? EventPattern.TokenCreationEvent ?: return false
        if (!TokenCreationReplacementHelper.controllerMatches(event.controller, state, replacement.controllerId,
                bundle.controllerId, amounts.predicates)) return false
        val filter = event.tokenFilter ?: return true
        val (id, withId) = state.newEntity()
        val probe = withId.withEntity(id, ComponentContainer.of(batch.prospectiveCard,
            TokenComponent, ControllerComponent(bundle.controllerId)))
        return amounts.predicates.matches(probe, state.projectedState, id, filter,
            PredicateContext(controllerId = replacement.controllerId, sourceId = replacement.sourceId))
    }

    private fun candidates(state: GameState, bundle: TokenCreationBundle): List<TokenBundleReplacement> {
        val active = mutableListOf<TokenBundleReplacement>()
        for (id in state.getBattlefield()) {
            if (state.projectedState.hasLostAllAbilities(id) || state.projectedState.isFaceDown(id)) continue
            val entity = state.getEntity(id) ?: continue
            val replacements = entity.get<ReplacementEffectSourceComponent>()?.replacementEffects
                ?.takeIf { it.isNotEmpty() } ?: continue
            val owner = state.projectedState.getController(id) ?: continue
            val text = TextChanges.of(state, id)
            for ((index, printed) in replacements.withIndex()) {
                active += TokenBundleReplacement(ReplacementEffectIdentity.BattlefieldIdentity(id, index),
                    id, owner, if (text == null) printed else printed.applyTextReplacement(text),
                    entity.get<CardComponent>()?.name ?: "Replacement")
            }
        }
        for ((index, grant) in state.grantedReplacementEffects.withIndex()) {
            active += TokenBundleReplacement(ReplacementEffectIdentity.GrantedIdentity(index),
                grant.entityId, grant.controllerId, grant.replacement, "Granted replacement")
        }
        return active.filter { candidate ->
            val re = candidate.effect
            if (candidate.identity in bundle.applied ||
                bundle.eligibleSources?.let { candidate.sourceId !in it } == true) return@filter false
            if (re !is MultiplyTokenCreation && re !is ModifyTokenCount && re !is CreateAdditionalToken &&
                re !is ReplaceTokenCreationWithToken && re !is ReplaceTokenCreationWithTokens &&
                re !is ReplaceTokenCreationWithAttachedCopy) return@filter false
            if (re is ReplaceTokenCreationWithAttachedCopy) {
                val host = state.getEntity(candidate.sourceId) ?: return@filter false
                if (re.oncePerTurn && host.has<TokenReplacementOfferedThisTurnComponent>()) return@filter false
                if (host.get<AttachedToComponent>()?.targetId?.let { state.getEntity(it) } == null) return@filter false
            }
            val context = EffectContext(candidate.sourceId, candidate.controllerId)
            re.restrictions.all { amounts.conditions.evaluate(state, it, context) } &&
                bundle.batches.any { matches(state, bundle, candidate, it) }
        }
    }

    private fun count(value: Long) = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun coalesce(batches: List<TokenBundleBatch>): List<TokenBundleBatch> =
        batches.filter { it.count > 0 }.groupBy { listOf(it.creation, it.definitionName, it.prospectiveCard, it.ridersSource, it.entryCounters) }
            .map { (_, group) -> group.first().copy(count = count(group.sumOf { it.count.toLong() })) }

    private fun predefined(name: String, owner: EntityId, n: Int, tapped: Boolean = false) =
        TokenBundleBatch(CreatePredefinedTokenEffect(name, controller = EffectTarget.SpecificEntity(owner), tapped = tapped),
            prospectiveCard = definitionCard(cards.requireCard(name), owner), count = n)

    fun apply(state: GameState, bundle: TokenCreationBundle, candidate: TokenBundleReplacement): TokenCreationBundle {
        val matching = bundle.batches.filter { matches(state, bundle, candidate, it) }
        val other = bundle.batches.filterNot { matches(state, bundle, candidate, it) }
        val re = candidate.effect
        val replaced = when (re) {
            is MultiplyTokenCreation -> matching.map { it.copy(count = count(it.count.toLong() * re.factor)) } + other
            is ModifyTokenCount -> {
                var remaining = re.modifier.toLong()
                bundle.batches.map { batch ->
                    if (batch !in matching) batch else {
                        val adjusted = if (remaining >= 0) count(batch.count.toLong() + remaining).also { remaining = 0 }
                        else {
                            val removed = minOf(batch.count.toLong(), -remaining)
                            remaining += removed
                            batch.count - removed.toInt()
                        }
                        batch.copy(count = adjusted)
                    }
                }
            }
            is CreateAdditionalToken -> {
                fun addition(batch: TokenBundleBatch, n: Int, entersTapped: Boolean): TokenBundleBatch {
                    val original = batch.ridersSource ?: batch.creation
                    val riders = when (original) {
                        is CreateTokenEffect -> original.copy(tapped = entersTapped)
                        is CreatePredefinedTokenEffect -> original.copy(tapped = entersTapped)
                        is CreateTokenCopyOfTargetEffect -> original.copy(tapped = entersTapped)
                        else -> original
                    }
                    return predefined(re.additionalTokenType, bundle.controllerId, n, entersTapped)
                        .copy(ridersSource = riders, entryCounters = batch.entryCounters)
                }
                val added = when (re.scale) {
                    AdditionalTokenScale.PER_EVENT -> listOf(addition(matching.first(), re.additionalTokenCount,
                        re.inheritTapped && matching.all { tapped(it.ridersSource ?: it.creation) }))
                    AdditionalTokenScale.PER_MATCHING_TOKEN -> matching.map { batch -> addition(batch,
                        count(batch.count.toLong() * re.additionalTokenCount.toLong()),
                        re.inheritTapped && tapped(batch.ridersSource ?: batch.creation)) }
                }
                bundle.batches + added
            }
            is ReplaceTokenCreationWithTokens -> matching.flatMap { batch ->
                re.tokenTypes.map { predefined(it, bundle.controllerId, batch.count).copy(ridersSource = batch.ridersSource ?: batch.creation, entryCounters = batch.entryCounters) }
            } + other
            is ReplaceTokenCreationWithToken -> {
                val token = re.tokens.single() as CreateTokenEffect
                matching.map { batch ->
                    requireNotNull(prepare(state, token.copy(tapped = false, attacking = false, exileAtStep = null, sacrificeAtStep = null,
                        stampCreator = false, initialCounters = emptyMap(), controller = EffectTarget.SpecificEntity(bundle.controllerId)), bundle.context, bundle.controllerId))
                        .copy(count = batch.count, ridersSource = batch.ridersSource ?: batch.creation, entryCounters = batch.entryCounters)
                } + other
            }
            is ReplaceTokenCreationWithAttachedCopy -> {
                val target = state.getEntity(candidate.sourceId)!!.get<AttachedToComponent>()!!.targetId
                val effect = CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(target))
                matching.map { batch -> requireNotNull(prepare(state, effect, bundle.context, bundle.controllerId))
                    .copy(count = batch.count, ridersSource = batch.ridersSource ?: batch.creation, entryCounters = batch.entryCounters) } + other
            }
            else -> error("Unsupported token replacement")
        }
        return bundle.copy(batches = coalesce(replaced), applied = bundle.applied + candidate.identity)
    }

    private fun tapped(effect: Effect?) = when (effect) {
        is CreateTokenEffect -> effect.tapped
        is CreatePredefinedTokenEffect -> effect.tapped
        is CreateTokenCopyOfTargetEffect -> effect.tapped
        else -> false
    }

    private fun attacking(effect: Effect?) = when (effect) {
        is CreateTokenEffect -> effect.attacking
        is CreateTokenCopyOfTargetEffect -> effect.attacking
        else -> false
    }

    private fun sourceLabel(state: GameState, candidate: TokenBundleReplacement): String {
        val hostName = if (candidate.effect is ReplaceTokenCreationWithAttachedCopy)
            state.getEntity(candidate.sourceId)?.get<AttachedToComponent>()?.targetId
                ?.let { state.getEntity(it)?.get<CardComponent>()?.name }
            else null
        return if (hostName == null) candidate.description else "${candidate.description} (${hostName})"
    }

    fun resolve(state: GameState, bundle: TokenCreationBundle): EffectResult {
        if (bundle.batches.none { it.count > 0 }) return finish(state, bundle)
        val options = candidates(state, bundle)
        if (options.isEmpty()) {
            var capacity = GameLimits.cappedTokenCount(count(bundle.batches.sumOf { it.count.toLong() }), "replacement token bundle")
            val limited = bundle.batches.map { batch ->
                val n = minOf(batch.count, capacity)
                capacity -= n
                batch.copy(count = n)
            }
            return materialize(state, bundle.copy(batches = limited), 0)
        }
        if (options.size == 1) return select(state, bundle, options.single())
        // Identical mandatory substitutions have no observable ordering choice.
        if (options.none { it.effect.optional ||
                (it.effect is ReplaceTokenCreationWithToken && it.effect.asksForChoice) } &&
            options.map { apply(state, bundle, it).batches }.distinct().size == 1)
            return select(state, bundle, options.first())
        return EffectResult.from(state.suspendForDecision(
            { id -> ChooseOptionDecision(id = id, playerId = bundle.controllerId,
                prompt = "Choose the next token replacement to apply",
                options = options.map { "${sourceLabel(state, it)}: ${it.effect.description}" },
                context = DecisionContext(phase = DecisionPhase.RESOLUTION)) },
            TokenBundleReplacementContinuation(bundle, options), emptyList()))
    }

    fun select(state: GameState, bundle: TokenCreationBundle, candidate: TokenBundleReplacement): EffectResult {
        val substitution = candidate.effect as? ReplaceTokenCreationWithToken
        if (substitution?.asksForChoice == true) {
            val options = substitution.tokens.map { ReplaceTokenCreationWithToken.tokenNounPhrase(it as CreateTokenEffect) } +
                if (substitution.optional) listOf("Create the original tokens") else emptyList()
            return EffectResult.from(state.suspendForDecision(
                { id -> ChooseOptionDecision(id = id, playerId = candidate.controllerId,
                    prompt = "${sourceLabel(state, candidate)}: choose the tokens to create instead",
                    options = options, context = DecisionContext(sourceId = candidate.sourceId,
                        sourceName = candidate.description, phase = DecisionPhase.RESOLUTION)) },
                TokenBundleReplacementContinuation(bundle, substitution = candidate), emptyList()))
        }
        if (!candidate.effect.optional) return resolve(markOffered(state, candidate), apply(state, bundle, candidate))
        return EffectResult.from(markOffered(state, candidate).suspendForDecision(
            { id -> YesNoDecision(id = id, playerId = bundle.controllerId,
                prompt = "Use ${sourceLabel(state, candidate)}? ${candidate.effect.description}",
                context = DecisionContext(sourceId = candidate.sourceId, sourceName = candidate.description, phase = DecisionPhase.RESOLUTION)) },
            TokenBundleReplacementContinuation(bundle, optional = candidate), emptyList()))
    }

    fun chooseSubstitution(state: GameState, bundle: TokenCreationBundle,
                           candidate: TokenBundleReplacement, index: Int): EffectResult {
        val replacement = candidate.effect as? ReplaceTokenCreationWithToken
            ?: return EffectResult.error(state, "Expected token substitution")
        if (index == replacement.tokens.size && replacement.optional)
            return resolve(state, bundle.copy(applied = bundle.applied + candidate.identity))
        val token = replacement.tokens.getOrNull(index)
            ?: return EffectResult.error(state, "Invalid token substitution")
        val chosen = candidate.copy(effect = replacement.copy(tokens = listOf(token), optional = false))
        return resolve(state, apply(state, bundle, chosen))
    }

    private fun markOffered(state: GameState, candidate: TokenBundleReplacement): GameState =
        if (candidate.effect is ReplaceTokenCreationWithAttachedCopy &&
            candidate.effect.oncePerTurn && state.getEntity(candidate.sourceId) != null)
            state.updateEntity(candidate.sourceId) { it.with(TokenReplacementOfferedThisTurnComponent) }
        else state

    fun materialize(state: GameState, bundle: TokenCreationBundle, start: Int): EffectResult =
        TokenEntryGroups.stamp(materializeUngrouped(state, bundle, start), TokenEntryGroups.group(bundle.context))

    private fun materializeUngrouped(state: GameState, bundle: TokenCreationBundle, start: Int): EffectResult {
        var current = state
        val events = mutableListOf<GameEvent>()
        val context = bundle.context.copy(tokenCreationReplacementsHandled = true,
            pipeline = bundle.context.pipeline.copy(storedCollections = bundle.context.pipeline.storedCollections +
                (TokenCreationBatch.WOULD_BE_CONTROLLER to listOf(bundle.wouldBeControllerId))),
            resolutionDepth = bundle.context.resolutionDepth + 1)
        for (index in start until bundle.batches.size) {
            val batch = bundle.batches[index]
            val n = GameLimits.cappedTokenCount(batch.count, "replacement token bundle")
            if (n == 0) continue
            val creation = batch.creation
            // Each native call handles one entrant, so its entry-copy and defender replay is
            // complete before the next entrant. Every call still shares this pending event/group.
            val repetitions = n
            for (repeatIndex in 0 until repetitions) {
                val remainder = if (repetitions == 1 || repeatIndex == repetitions - 1) bundle
                    else bundle.copy(batches = bundle.batches.toMutableList().also {
                        it[index] = batch.copy(count = repetitions - repeatIndex - 1)
                    })
                val next = if (repeatIndex == repetitions - 1) index + 1 else index
                val prepared = current.pushContinuation(TokenBundleMaterializationContinuation(remainder, next, batch.ridersSource,
                    current.entities.filterValues { it.has<TokenComponent>() }.keys,
                    inheritedCounters = if (batch.ridersSource != null) batch.entryCounters else emptyMap()))
                val effect = when (creation) {
                    is CreatePredefinedTokenEffect -> creation.copy(count = 1, dynamicCount = null, tapped = creation.tapped || tapped(batch.ridersSource))
                    is CreateTokenEffect -> creation.copy(count = DynamicAmount.Fixed(1),
                        tapped = creation.tapped || tapped(batch.ridersSource),
                        attacking = creation.attacking || attacking(batch.ridersSource),
                        attackDefender = when (val original = batch.ridersSource) {
                            is CreateTokenEffect -> original.attackDefender
                            is CreateTokenCopyOfTargetEffect -> original.attackDefender
                            else -> creation.attackDefender
                        },
                        attackedPlayer = when (val original = batch.ridersSource) {
                            is CreateTokenEffect -> original.attackedPlayer
                            is CreateTokenCopyOfTargetEffect -> original.attackedPlayer
                            else -> creation.attackedPlayer
                        })
                    is CreateTokenCopyOfTargetEffect -> creation.copy(count = DynamicAmount.Fixed(1),
                        tapped = creation.tapped || tapped(batch.ridersSource),
                        attacking = creation.attacking || attacking(batch.ridersSource),
                        attackDefender = when (val original = batch.ridersSource) {
                            is CreateTokenEffect -> original.attackDefender
                            is CreateTokenCopyOfTargetEffect -> original.attackDefender
                            else -> creation.attackDefender
                        },
                        attackedPlayer = when (val original = batch.ridersSource) {
                            is CreateTokenEffect -> original.attackedPlayer
                            is CreateTokenCopyOfTargetEffect -> original.attackedPlayer
                            else -> creation.attackedPlayer
                        })
                    is CreateTokenCopyOfSourceEffect -> creation.copy(count = 1)
                    else -> creation
                }
                val result = if (effect != null) execute(prepared, effect, context)
                    else TokenFromDefinition.mint(prepared, cards.requireCard(requireNotNull(batch.definitionName)),
                        bundle.controllerId, cards, StaticAbilityHandler(cards), predicateEvaluator = amounts.predicates,
                        entrySourceState = bundle.entrySourceState, tokenCreatorId = bundle.wouldBeControllerId)
                events += result.events
                if (result.outcome is Outcome.Paused || result.error != null) return result.copy(events = events)
                check(result.state.peekContinuation() is TokenBundleMaterializationContinuation)
                val (frame, popped) = result.state.popContinuation()
                val completed = applyMaterializedRiders(popped, frame as TokenBundleMaterializationContinuation)
                events += completed.events
                if (completed.outcome !is Outcome.Done) return completed.copy(events = events)
                current = completed.state
            }
        }
        val finished = finish(current, bundle)
        return finished.copy(events = events + finished.events)
    }


    /** Entry instructions belong to the creating effect even when token characteristics change. */
    private fun applyMaterializedRiders(state: GameState, frame: TokenBundleMaterializationContinuation): EffectResult {
        val original = frame.ridersSource ?: return EffectResult.success(state)
        val ids = state.entities.filter { (id, entity) -> id !in frame.priorTokenIds && entity.has<TokenComponent>() }.keys
        val context = frame.bundle.context
        val controller = frame.bundle.wouldBeControllerId
        val source = context.sourceId ?: context.controllerId
        val name = state.getEntity(source)?.get<CardComponent>()?.name ?: "Unknown"
        val exile = when (original) {
            is CreateTokenEffect -> original.exileAtStep
            is CreateTokenCopyOfTargetEffect -> original.exileAtStep
            is CreateTokenCopyOfSourceEffect -> original.exileAtStep
            else -> null
        }
        val sacrifice = when (original) {
            is CreateTokenEffect -> original.sacrificeAtStep
            is CreateTokenCopyOfTargetEffect -> original.sacrificeAtStep
            else -> null
        }
        var current = state
        val effects = mutableListOf<Effect>()
        for (id in ids) {
            if (original is CreateTokenCopyOfEquippedCreatureEffect && original.grantHaste)
                effects += GrantKeywordEffect(Keyword.HASTE, EffectTarget.SpecificEntity(id), Duration.Permanent)
            for ((step, isSacrifice) in listOf(exile to false, sacrifice to true)) {
                if (step == null) continue
                var delayed: Effect = if (isSacrifice) SacrificeTargetEffect(EffectTarget.SpecificEntity(id))
                    else MoveToZoneEffect(EffectTarget.SpecificEntity(id), Zone.EXILE)
                if (!isSacrifice && original is CreateTokenCopyOfTargetEffect && original.exileUnlessSourceIsRingBearer)
                    delayed = GatedEffect(gate = Gate.WhenCondition(com.wingedsheep.sdk.scripting.conditions.SourceIsRingBearer),
                        then = CompositeEffect(emptyList()), otherwise = delayed)
                val (routing, withId) = current.newRoutingId()
                current = withId.addDelayedTrigger(DelayedTriggeredAbility(id = routing, effect = delayed,
                    fireAtStep = step, sourceId = source, sourceName = name, controllerId = controller,
                    objectReferences = context.objectReferences,
                    fireOnPlayerId = if (isSacrifice && original is CreateTokenCopyOfTargetEffect &&
                        original.sacrificeOnlyOnControllersTurn) controller else null))
            }
        }
        if (effects.isEmpty()) return EffectResult.success(current)
        // A remaining independent rider may pause; the cleared frame resumes the remaining batches.
        val prepared = current.pushContinuation(frame.copy(ridersSource = null, priorTokenIds = emptySet()))
        val result = execute(prepared, CompositeEffect(effects), context)
        return if (result.outcome !is Outcome.Done) result
            else result.copy(state = result.state.popContinuation().second)
    }

    fun resumeMaterialization(state: GameState, frame: TokenBundleMaterializationContinuation): EffectResult {
        val riders = applyMaterializedRiders(state, frame)
        if (riders.outcome !is Outcome.Done) return riders
        val remaining = materialize(riders.state, frame.bundle, frame.nextBatch)
        return remaining.copy(events = riders.events + remaining.events)
    }

    private fun finish(state: GameState, bundle: TokenCreationBundle): EffectResult {
        if (bundle.following.isNotEmpty()) {
            val next = bundle.following.first().copy(following = bundle.following.drop(1))
            return resolve(state, next)
        }
        val created = state.entities.filter { (id, entity) ->
            id !in bundle.initialTokenIds && entity.has<TokenComponent>()
        }.keys.toList()
        return EffectResult(ActiveReplacements.purgeSpent(state), emptyList(), updatedCollections = mapOf(CREATED_TOKENS to created))
    }
}
