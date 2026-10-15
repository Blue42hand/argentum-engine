package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.*
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
                    (it.granted || (!state.projectedState.hasLostAllAbilities(it.sourceId) &&
                        !state.projectedState.isFaceDown(it.sourceId)))
            }
    }

    private fun definitionCard(def: CardDefinition, owner: EntityId) = CardComponent(
        cardDefinitionId = def.name, name = def.name, manaCost = def.manaCost,
        typeLine = def.typeLine, baseStats = def.creatureStats, baseKeywords = def.keywords,
        colors = def.colors, ownerId = owner
    )

    fun intercept(state: GameState, effect: Effect, context: EffectContext): EffectResult? {
        if (effect !is CreateTokenEffect && effect !is CreatePredefinedTokenEffect &&
            effect !is CreateTokenCopyOfTargetEffect && effect !is CreateTokenCopyOfSourceEffect &&
            effect !is CreateTokenCopyOfEquippedCreatureEffect && effect !is CreateRoleTokenEffect) return null
        if (!enabled(state, context)) return null
        val controllerTarget = when (effect) {
            is CreateTokenEffect -> effect.controller
            is CreatePredefinedTokenEffect -> effect.controller
            is CreateTokenCopyOfTargetEffect -> effect.controller
            else -> null
        }
        val recipients = controllerTarget?.let { context.resolvePlayerTargets(it, state) }
            ?.takeIf { it.isNotEmpty() } ?: listOf(context.controllerId)
        val bundles = recipients.mapNotNull { owner ->
            prepare(state, effect, context, owner)?.let { batch ->
                TokenCreationBundle(owner, context, listOf(batch),
                    initialTokenIds = state.entities.filterValues { it.has<TokenComponent>() }.keys)
            }
        }
        if (bundles.isEmpty()) return null
        return resolve(state, bundles.first().copy(following = bundles.drop(1)))
    }

    /** Choice-based factories call this only after their choice has been made. */
    fun fromDefinition(state: GameState, name: String, context: EffectContext): EffectResult {
        val def = cards.requireCard(name)
        val batch = TokenCreationBatch(null, name, definitionCard(def, context.controllerId), 1)
        return resolve(state, TokenCreationBundle(context.controllerId, context, listOf(batch),
            initialTokenIds = state.entities.filterValues { it.has<TokenComponent>() }.keys))
    }

    private fun prepare(state: GameState, effect: Effect, ctx: EffectContext, owner: EntityId): TokenCreationBatch? {
        val recipient = EffectTarget.SpecificEntity(owner)
        return when (effect) {
            is CreatePredefinedTokenEffect -> {
                val def = cards.getCard(effect.tokenType) ?: return null
                TokenCreationBatch(effect.copy(count = 1, dynamicCount = null, controller = recipient),
                    prospectiveCard = definitionCard(def, owner),
                    count = effect.dynamicCount?.let { amounts.evaluate(state, it, ctx) } ?: effect.count)
            }
            is CreateTokenEffect -> {
                val bag = ctx.sourceId?.let { state.getEntity(it)?.get<CastChoicesComponent>() }
                val colors = effect.colorsFromChoice?.let {
                    (bag?.chosen?.get(it) as? ChoiceValue.ColorChoice)?.color?.let(::setOf) ?: emptySet()
                } ?: effect.colors
                val types = effect.creatureTypesFromChoice?.let {
                    (bag?.chosen?.get(it) as? ChoiceValue.TextChoice)?.text?.let(::setOf) ?: setOf("Creature")
                } ?: effect.creatureTypes
                val typeLine = buildString {
                    if (effect.legendary) append("Legendary ")
                    if (effect.artifactToken) append("Artifact ")
                    if (effect.enchantmentToken) append("Enchantment ")
                    append("Creature — "); append(types.joinToString(" "))
                }
                val power = effect.dynamicPower?.let { amounts.evaluate(state, it, ctx) } ?: effect.power
                val toughness = effect.dynamicToughness?.let { amounts.evaluate(state, it, ctx) } ?: effect.toughness
                TokenCreationBatch(effect.copy(count = DynamicAmount.Fixed(1), controller = recipient),
                    prospectiveCard = CardComponent(
                        cardDefinitionId = "token:${types.joinToString("-")}",
                        name = effect.name ?: "${types.joinToString(" ")} Token",
                        manaCost = ManaCost.ZERO, typeLine = TypeLine.parse(typeLine),
                        baseStats = CreatureStats(power, toughness), baseKeywords = effect.keywords,
                        colors = colors, ownerId = owner),
                    count = amounts.evaluate(state, effect.count, ctx))
            }
            is CreateTokenCopyOfTargetEffect -> {
                val source = CreateTokenCopyOfTargetExecutor(amounts, cardRegistry = cards, targetFinder = targetFinder)
                    .copySource(state, effect, ctx)?.copiableCardComponent() ?: return null
                TokenCreationBatch(effect.copy(count = DynamicAmount.Fixed(1), controller = recipient),
                    prospectiveCard = CopyExceptionApplier.apply(source, effect.copyExceptions).copy(ownerId = owner),
                    count = amounts.evaluate(state, effect.count, ctx))
            }
            is CreateTokenCopyOfSourceEffect -> {
                val source = ctx.sourceId?.let { state.getEntity(it)?.copiableCardComponent() } ?: return null
                TokenCreationBatch(effect.copy(count = 1),
                    prospectiveCard = CopyExceptionApplier.apply(source, effect.copyExceptions).copy(ownerId = owner),
                    count = effect.count)
            }
            is CreateTokenCopyOfEquippedCreatureEffect -> {
                val host = ctx.sourceId?.let { state.getEntity(it)?.get<AttachedToComponent>()?.targetId } ?: return null
                var card = state.getEntity(host)?.copiableCardComponent() ?: return null
                if (effect.removeLegendary) card = card.copy(typeLine = card.typeLine.withoutLegendary())
                if (effect.grantHaste) card = card.copy(baseKeywords = card.baseKeywords + Keyword.HASTE)
                TokenCreationBatch(effect, prospectiveCard = card.copy(ownerId = owner), count = 1)
            }
            is CreateRoleTokenEffect -> {
                if (ctx.resolveTarget(effect.target, state) == null) return null
                val def = cards.getCard(effect.roleName) ?: return null
                TokenCreationBatch(effect, prospectiveCard = definitionCard(def, owner), count = 1)
            }
            else -> null
        }
    }

    private fun matches(state: GameState, bundle: TokenCreationBundle, replacement: TokenBundleReplacement,
                        batch: TokenCreationBatch): Boolean {
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
            if (candidate.identity in bundle.applied) return@filter false
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

    private fun coalesce(batches: List<TokenCreationBatch>): List<TokenCreationBatch> =
        batches.filter { it.count > 0 }.groupBy { listOf(it.creation, it.definitionName, it.prospectiveCard, it.ridersSource) }
            .map { (_, group) -> group.first().copy(count = count(group.sumOf { it.count.toLong() })) }

    private fun predefined(name: String, owner: EntityId, n: Int, tapped: Boolean = false) =
        TokenCreationBatch(CreatePredefinedTokenEffect(name, controller = EffectTarget.SpecificEntity(owner), tapped = tapped),
            prospectiveCard = definitionCard(cards.requireCard(name), owner), count = n)

    fun apply(state: GameState, bundle: TokenCreationBundle, candidate: TokenBundleReplacement): TokenCreationBundle {
        val matching = bundle.batches.filter { matches(state, bundle, candidate, it) }
        val other = bundle.batches.filterNot { matches(state, bundle, candidate, it) }
        val re = candidate.effect
        val replaced = when (re) {
            is MultiplyTokenCreation -> matching.map { it.copy(count = count(it.count.toLong() * re.factor)) } + other
            is ModifyTokenCount -> matching.map { it.copy(count = count(it.count.toLong() + re.modifier)) } + other
            is CreateAdditionalToken -> bundle.batches + predefined(re.additionalTokenType, bundle.controllerId,
                re.additionalTokenCount).copy(ridersSource = matching.first().ridersSource ?: matching.first().creation)
            is ReplaceTokenCreationWithTokens -> matching.flatMap { batch ->
                re.tokenTypes.map { predefined(it, bundle.controllerId, batch.count).copy(ridersSource = batch.ridersSource ?: batch.creation) }
            } + other
            is ReplaceTokenCreationWithToken -> {
                val token = re.token as CreateTokenEffect
                matching.map { batch ->
                    requireNotNull(prepare(state, token.copy(tapped = false, attacking = false,
                        controller = EffectTarget.SpecificEntity(bundle.controllerId)), bundle.context, bundle.controllerId))
                        .copy(count = batch.count, ridersSource = batch.ridersSource ?: batch.creation)
                } + other
            }
            is ReplaceTokenCreationWithAttachedCopy -> {
                val target = state.getEntity(candidate.sourceId)!!.get<AttachedToComponent>()!!.targetId
                val effect = CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(target))
                matching.map { batch -> requireNotNull(prepare(state, effect, bundle.context, bundle.controllerId))
                    .copy(count = batch.count, ridersSource = batch.ridersSource ?: batch.creation) } + other
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
        if (options.isEmpty()) return materialize(state, bundle, 0)
        if (options.size == 1) return select(state, bundle, options.single())
        // Identical mandatory substitutions have no observable ordering choice.
        if (options.none { it.effect.optional } &&
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
        if (!candidate.effect.optional) return resolve(markOffered(state, candidate), apply(state, bundle, candidate))
        return EffectResult.from(markOffered(state, candidate).suspendForDecision(
            { id -> YesNoDecision(id = id, playerId = bundle.controllerId,
                prompt = "Use ${sourceLabel(state, candidate)}? ${candidate.effect.description}",
                context = DecisionContext(sourceId = candidate.sourceId, sourceName = candidate.description, phase = DecisionPhase.RESOLUTION)) },
            TokenBundleReplacementContinuation(bundle, optional = candidate), emptyList()))
    }

    private fun markOffered(state: GameState, candidate: TokenBundleReplacement): GameState =
        if (candidate.effect is ReplaceTokenCreationWithAttachedCopy &&
            candidate.effect.oncePerTurn && state.getEntity(candidate.sourceId) != null)
            state.updateEntity(candidate.sourceId) { it.with(TokenReplacementOfferedThisTurnComponent) }
        else state

    fun materialize(state: GameState, bundle: TokenCreationBundle, start: Int): EffectResult {
        var current = state
        val events = mutableListOf<GameEvent>()
        val context = bundle.context.copy(tokenCreationReplacementsHandled = true,
            resolutionDepth = bundle.context.resolutionDepth + 1)
        for (index in start until bundle.batches.size) {
            val batch = bundle.batches[index]
            val n = GameLimits.cappedTokenCount(batch.count, "replacement token bundle")
            val creation = batch.creation
            // Single-token factories have no count field; each copy is still part of this bundle.
            val repetitions = if (creation is CreateTokenCopyOfEquippedCreatureEffect || creation is CreateRoleTokenEffect ||
                creation == null) n else 1
            for (repeatIndex in 0 until repetitions) {
                val remainder = if (repetitions == 1 || repeatIndex == repetitions - 1) bundle
                    else bundle.copy(batches = bundle.batches.toMutableList().also {
                        it[index] = batch.copy(count = repetitions - repeatIndex - 1)
                    })
                val next = if (repeatIndex == repetitions - 1) index + 1 else index
                val prepared = current.pushContinuation(TokenBundleMaterializationContinuation(remainder, next, batch.ridersSource,
                    current.entities.filterValues { it.has<TokenComponent>() }.keys))
                val effect = when (creation) {
                    is CreatePredefinedTokenEffect -> creation.copy(count = n, dynamicCount = null, tapped = creation.tapped || tapped(batch.ridersSource))
                    is CreateTokenEffect -> creation.copy(count = DynamicAmount.Fixed(n),
                        tapped = creation.tapped || tapped(batch.ridersSource),
                        attacking = creation.attacking || attacking(batch.ridersSource))
                    is CreateTokenCopyOfTargetEffect -> creation.copy(count = DynamicAmount.Fixed(n),
                        tapped = creation.tapped || tapped(batch.ridersSource),
                        attacking = creation.attacking || attacking(batch.ridersSource))
                    is CreateTokenCopyOfSourceEffect -> creation.copy(count = n)
                    else -> creation
                }
                val result = if (effect != null) execute(prepared, effect, context)
                    else TokenFromDefinition.mint(prepared, cards.requireCard(requireNotNull(batch.definitionName)),
                        bundle.controllerId, cards, StaticAbilityHandler(cards), predicateEvaluator = amounts.predicates)
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
        val controller = frame.bundle.controllerId
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
        val attacking = when (original) {
            is CreateTokenEffect -> original.attacking
            is CreateTokenCopyOfTargetEffect -> original.attacking
            else -> false
        }
        var current = state
        val effects = mutableListOf<Effect>()
        for (id in ids) {
            if (attacking && current.getEntity(id)?.get<CardComponent>()?.typeLine?.isCreature == true) {
                val defender = com.wingedsheep.engine.handlers.effects.TargetResolutionUtils
                    .defenderForEnteringAttacker(context, current, controller)
                if (defender != null) current = current.updateEntity(id) {
                    it.with(com.wingedsheep.engine.state.components.combat.AttackingComponent(defender,
                        defendingPlayerId = com.wingedsheep.engine.mechanics.combat.CombatDefenders.defendingPlayerOf(current, defender)))
                }
            }
            if (original is CreateTokenEffect) {
                if (original.stampCreator && context.sourceId != null)
                    current = current.updateEntity(id) { it.with(CreatedByComponent(context.sourceId)) }
                for ((kind, n) in original.initialCounters)
                    effects += AddCountersEffect(kind, n, EffectTarget.SpecificEntity(id))
            }
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
        // A counter replacement can itself pause. The cleared frame resumes the remaining batches.
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
        return EffectResult(state, emptyList(), updatedCollections = mapOf(CREATED_TOKENS to created))
    }
}
