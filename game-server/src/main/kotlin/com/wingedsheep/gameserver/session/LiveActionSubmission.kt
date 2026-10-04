package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.GameAction

/** A canonical engine action paired with the live timeline on which the choice originated. */
data class LiveActionSubmission(
    val action: GameAction,
    val interactionEpoch: String,
    val messageId: String? = null,
    /** Guard an AI correction that may be a mana ability action rather than a decision reply. */
    val expectedDecisionId: String? = null,
    /** A payment correction must use the exact state from which its observation was made. */
    val expectedStateRevision: Long? = null,
    /** AI mana decisions are checked by the native processor before live submission. */
    val previewManaPayment: Boolean = false,
)
