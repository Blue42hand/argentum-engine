package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.view.DecisionMasker
import com.wingedsheep.engine.view.Visibility
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.model.EntityId

class DecisionEnricher(private val cardRegistry: CardRegistry) {
    private val visibility = Visibility(cardRegistry, conditionEvaluator = PredicateEvaluator(cardRegistry = null).conditions)

    /** Hides every face-down identity the viewer may not see; the one per-viewer masking point. */
    private val masker = DecisionMasker(visibility)

    /**
     * The art to display for [entityId], or null when [viewerId] may not see what it is.
     *
     * Reads the entity's own [CardComponent.imageUri], which `CardEntityFactory` stamps from the
     * printing the player actually put in their deck. Re-deriving it from the canonical
     * [com.wingedsheep.sdk.model.CardDefinition] metadata instead (as this used to) shows the
     * *original* printing's art for every reprint, so a card in a search/reveal prompt didn't match
     * the same card in hand or on the battlefield — both of which read the component. The definition
     * lookup remains only as a fallback for entities with no image stamped.
     */
    private fun imageUriFor(state: GameState, entityId: EntityId, viewerId: EntityId): String? {
        if (visibility.isCardIdentityHiddenFrom(state, entityId, viewerId)) return null
        val cardComponent = state.getEntity(entityId)?.get<CardComponent>() ?: return null
        return cardComponent.imageUri
            ?: cardRegistry.getCard(cardComponent.cardDefinitionId)?.metadata?.imageUri
    }

    private fun Map<EntityId, SearchCardInfo>.withImages(state: GameState, viewerId: EntityId) =
        mapValues { (entityId, cardInfo) -> cardInfo.copy(imageUri = imageUriFor(state, entityId, viewerId)) }

    fun enrich(decision: PendingDecision, state: GameState, viewerId: EntityId): PendingDecision {
        return when (val masked = masker.maskFor(decision, state, viewerId)) {
            is SearchLibraryDecision -> masked.copy(cards = masked.cards.withImages(state, viewerId))
            is ReorderLibraryDecision -> masked.copy(cardInfo = masked.cardInfo.withImages(state, viewerId))
            is SelectCardsDecision -> masked.copy(cardInfo = masked.cardInfo?.withImages(state, viewerId))
            is OrderObjectsDecision -> masked.copy(cardInfo = masked.cardInfo?.withImages(state, viewerId))
            is SplitPilesDecision -> masked.copy(cardInfo = masked.cardInfo?.withImages(state, viewerId))
            // Other decision types don't have card info to enrich
            else -> masked
        }
    }

    fun createOpponentDecisionStatus(
        decision: PendingDecision,
        state: GameState,
        viewerId: EntityId,
    ): ServerMessage.OpponentDecisionStatus {
        val displayText = when (decision) {
            is SelectCardsDecision -> "Selecting cards"
            is com.wingedsheep.engine.core.PlayCardDecision -> "Playing a card"
            is ChooseTargetsDecision -> "Choosing targets"
            is YesNoDecision -> "Making a choice"
            is BatchYesNoDecision -> "Making a choice"
            is ChooseModeDecision -> "Choosing mode"
            is ChooseColorDecision -> "Choosing a color"
            is ChooseNumberDecision -> "Choosing a number"
            is DistributeDecision -> "Distributing"
            is OrderObjectsDecision -> decision.orderingTitle ?: "Ordering blockers"
            is SplitPilesDecision -> "Splitting piles"
            is SearchLibraryDecision -> "Searching library"
            is ReorderLibraryDecision -> "Reordering cards"
            is AssignDamageDecision -> "Assigning damage"
            is CombatResolutionDecision -> "Assigning combat damage"
            is ChooseOptionDecision -> "Making a choice"
            is ChooseReplacementDecision -> "Changing text"
            is BudgetModalDecision -> "Choosing modes"
            is SelectManaSourcesDecision -> "Selecting mana sources"
        }
        return ServerMessage.OpponentDecisionStatus(
            playerId = decision.playerId.value,
            decisionType = decision::class.simpleName ?: "Unknown",
            displayText = displayText,
            sourceName = masker.maskFor(decision, state, viewerId).context.sourceName,
            sourceId = decision.context.sourceId?.value
        )
    }
}
