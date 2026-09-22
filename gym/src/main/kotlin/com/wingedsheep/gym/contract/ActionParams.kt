package com.wingedsheep.gym.contract

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId

/**
 * Backward-compatible Gym API name for the rules-engine action-template parameters.
 *
 * The authoritative parameter vocabulary and implementation now live in rules-engine
 * because game-server AI controllers and Gym callers complete the same native
 * [GameAction] templates.
 */
typealias ActionParams = com.wingedsheep.engine.core.ActionParams

/**
 * Backward-compatible Gym facade over the rules-engine parameterizer.
 *
 * No parameterization logic lives here; this preserves existing Gym source/API imports
 * while giving other Argentum hosts one generic engine implementation to call.
 */
object ActionParameterizer {
    fun apply(action: GameAction, params: ActionParams, state: GameState): GameAction =
        com.wingedsheep.engine.core.ActionParameterizer.apply(action, params, state)

    fun resolveTarget(id: EntityId, state: GameState): ChosenTarget =
        com.wingedsheep.engine.core.ActionParameterizer.resolveTarget(id, state)
}
