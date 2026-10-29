package com.wingedsheep.engine.view

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.FACE_DOWN_DISPLAY_NAME
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/**
 * Renders a [PendingDecision] for one viewer, hiding the identity of every face-down object that
 * viewer may not look under.
 *
 * Decisions are built once, from the engine's full state, and some of them reach more than one
 * player: the combat-damage board goes to both choosers, and a selection can offer an opponent's
 * face-down creatures (Stand or Fall's piles). The engine therefore records real names and ids,
 * and every consumer that shows a decision to a player — the game server, a replay, an
 * observation — renders it through here. Each answer comes from [Visibility.cardNameFor], so a
 * reveal, a "look at face-down creatures" static, or CR 708.5's controller access is honoured
 * exactly as the client state honours it.
 *
 * Masked fields: [com.wingedsheep.engine.core.DecisionContext]`.sourceName` on any decision, the
 * combat board's nodes and prompt, and the `cardInfo` maps. Other prompts are not rewritten: they
 * are built for their chooser, who controls the source they name. A new decision that names an
 * object to a player who may not see it must mask that text here too.
 */
class DecisionMasker(private val visibility: Visibility) {

    fun maskFor(
        decision: PendingDecision,
        state: GameState,
        viewerId: EntityId,
        isSpectator: Boolean = false,
    ): PendingDecision {
        fun hidden(entityId: EntityId) =
            visibility.isCardIdentityHiddenFrom(state, entityId, viewerId, isSpectator)

        fun label(entityId: EntityId) =
            visibility.cardNameFor(state, entityId, viewerId, isSpectator) ?: FACE_DOWN_DISPLAY_NAME

        fun maskInfo(cardInfo: Map<EntityId, SearchCardInfo>?) = cardInfo?.mapValues { (entityId, info) ->
            if (hidden(entityId)) maskedInfo(label(entityId)) else info
        }

        val sourceId = decision.context.sourceId
        val context = if (sourceId != null && decision.context.sourceName != null && hidden(sourceId)) {
            decision.context.copy(sourceName = label(sourceId))
        } else {
            decision.context
        }

        return when (decision) {
            is CombatResolutionDecision -> {
                // The single-attacker prompt and source name embed that attacker's name.
                val single = decision.attackers.singleOrNull()
                val singleHidden = single != null && hidden(single.id)
                decision.copy(
                    attackers = decision.attackers.map { if (hidden(it.id)) it.copy(name = label(it.id)) else it },
                    blockers = decision.blockers.map { if (hidden(it.id)) it.copy(name = label(it.id)) else it },
                    prompt = if (singleHidden) decision.prompt.replace(single!!.name, label(single.id)) else decision.prompt,
                    context = if (singleHidden && context.sourceName == single!!.name) {
                        context.copy(sourceName = label(single.id))
                    } else {
                        context
                    },
                )
            }
            is SelectCardsDecision -> decision.copy(context = context, cardInfo = maskInfo(decision.cardInfo))
            is OrderObjectsDecision -> decision.copy(context = context, cardInfo = maskInfo(decision.cardInfo))
            is SplitPilesDecision -> decision.copy(context = context, cardInfo = maskInfo(decision.cardInfo))
            // Listed one by one, with no `else`, so a new decision type does not compile until
            // someone decides what it shows.
            is ChooseTargetsDecision -> decision.copy(context = context)
            is YesNoDecision -> decision.copy(context = context)
            is BatchYesNoDecision -> decision.copy(context = context)
            is ChooseModeDecision -> decision.copy(context = context)
            is ChooseColorDecision -> decision.copy(context = context)
            is ChooseNumberDecision -> decision.copy(context = context)
            is DistributeDecision -> decision.copy(context = context)
            is ChooseOptionDecision -> decision.copy(context = context)
            is ChooseReplacementDecision -> decision.copy(context = context)
            is AssignDamageDecision -> decision.copy(context = context)
            is SearchLibraryDecision -> decision.copy(context = context)
            is ReorderLibraryDecision -> decision.copy(context = context)
            is SelectManaSourcesDecision -> decision.copy(context = context)
            is BudgetModalDecision -> decision.copy(context = context)
            is PlayCardDecision -> decision.copy(context = context)
        }
    }

    /** What a face-down object shows: its label and nothing of the card underneath (CR 708.2a). */
    private fun maskedInfo(label: String) = SearchCardInfo(
        name = label,
        manaCost = "",
        typeLine = if (label == FACE_DOWN_DISPLAY_NAME) "Creature" else "",
        power = if (label == FACE_DOWN_DISPLAY_NAME) 2 else null,
    )
}
