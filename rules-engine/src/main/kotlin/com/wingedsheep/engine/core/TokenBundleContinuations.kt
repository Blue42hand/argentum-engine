package com.wingedsheep.engine.core

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.replacement.ReplacementEffectIdentity
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ReplacementEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import kotlinx.serialization.Serializable

/** A homogeneous part of a single, still-pending token creation event. */
@Serializable
data class TokenCreationBatch(
    val creation: Effect?,
    val definitionName: String? = null,
    val prospectiveCard: CardComponent,
    val count: Int,
    val ridersSource: Effect? = null
)

/** Replacement bookkeeping belongs to the whole event, including every substituted token. */
@Serializable
data class TokenCreationBundle(
    val controllerId: EntityId,
    val context: EffectContext,
    val batches: List<TokenCreationBatch>,
    val applied: Set<ReplacementEffectIdentity> = emptySet(),
    val following: List<TokenCreationBundle> = emptyList(),
    val initialTokenIds: Set<EntityId> = emptySet()
)

@Serializable
data class TokenBundleReplacement(
    val identity: ReplacementEffectIdentity,
    val sourceId: EntityId,
    val controllerId: EntityId,
    val effect: ReplacementEffect,
    val description: String
)

/** Choose the next replacement, or answer the optional replacement selected by that choice. */
@Serializable
data class TokenBundleReplacementContinuation(
    val bundle: TokenCreationBundle,
    val options: List<TokenBundleReplacement> = emptyList(),
    val optional: TokenBundleReplacement? = null
) : AnswerContinuation

/** Native token-entry decisions finish before the next part of the same bundle is created. */
@Serializable
data class TokenBundleMaterializationContinuation(
    val bundle: TokenCreationBundle,
    val nextBatch: Int,
    val ridersSource: Effect? = null,
    val priorTokenIds: Set<EntityId> = emptySet()
) : AutomaticContinuation


/** Investigation finishes after its token creation, including replacement decisions. */
@Serializable
data class CompleteInvestigationContinuation(
    val investigatorId: EntityId,
    val remaining: Int,
    val context: EffectContext,
    val sourceName: String?,
    val initialTokenIds: Set<EntityId> = emptySet()
) : AutomaticContinuation
