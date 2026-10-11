package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/** Transient evaluation inputs for one simultaneous entry event. Never stored in game state. */
data class EntryEvaluationViews(
    val entrantId: EntityId,
    val worldBeforeEvent: GameState,
    val entrantAsWouldExist: GameState,
)
