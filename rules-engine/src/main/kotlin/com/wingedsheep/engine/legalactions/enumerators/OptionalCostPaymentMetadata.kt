package com.wingedsheep.engine.legalactions.enumerators

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.ConvokeCreatureData
import com.wingedsheep.engine.legalactions.DelveCardData
import com.wingedsheep.engine.legalactions.EnumerationContext
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId

/** Optional additional costs do not remove a spell's delve/convoke payment methods. Keep their
 * native candidates even when the first offered payment costs zero generic: a later repeat
 * count can need them. This attaches metadata only, with no affordability search or cost change. */
internal fun applyOptionalCostPaymentMetadata(
    context: EnumerationContext, actions: List<LegalAction>,
): List<LegalAction> {
    if (context.mode != EnumerationMode.FULL) return actions
    if (actions.none { offer ->
            (offer.action as? CastSpell)?.declaredCostSlot != null && (!offer.hasConvoke || !offer.hasDelve)
        }) return actions
    val convokeByPlayer = mutableMapOf<EntityId, List<ConvokeCreatureData>>()
    val delveByPlayer = mutableMapOf<EntityId, List<DelveCardData>>()
    return actions.map { offer ->
        val cast = offer.action as? CastSpell ?: return@map offer
        if (cast.declaredCostSlot == null || (offer.hasConvoke && offer.hasDelve)) return@map offer
        val card = context.state.getEntity(cast.cardId)?.get<CardComponent>() ?: return@map offer
        val printed = context.cardRegistry.getCard(card.cardDefinitionId) ?: return@map offer
        val definition = context.costCalculator.optionalPaymentDefinition(context.state, cast, printed)
        val convoke = offer.hasConvoke || context.grantedKeywordResolver.hasKeyword(
            context.state, cast.playerId, definition, Keyword.CONVOKE, cast.cardId,
        )
        val delve = offer.hasDelve || context.grantedKeywordResolver.hasKeyword(
            context.state, cast.playerId, definition, Keyword.DELVE, cast.cardId,
        )
        if (!convoke && !delve) return@map offer
        offer.copy(
            hasConvoke = convoke,
            convokeCreatures = if (!convoke) offer.convokeCreatures else offer.convokeCreatures ?: convokeByPlayer.getOrPut(cast.playerId) {
                context.costUtils.findConvokeCreatures(context.state, cast.playerId)
            },
            hasDelve = delve,
            delveCards = if (!delve) offer.delveCards else offer.delveCards ?: delveByPlayer.getOrPut(cast.playerId) {
                context.costUtils.findDelveCards(context.state, cast.playerId)
            },
        )
    }
}
