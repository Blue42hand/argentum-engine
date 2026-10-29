package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.EntersWithChoiceOnBattlefieldContinuation
import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.sdk.core.Zone

/** Durable instruction scope; carried by the existing pipeline context across entry questions. */
internal object TokenEntryGroups {
    private const val KEY = "__simultaneousTokenEntryGroup"
    fun group(context: EffectContext): String? = context.pipeline.chosenValues[KEY]
    fun withGroup(context: EffectContext, group: String): EffectContext = context.copy(
        pipeline = context.pipeline.copy(chosenValues = context.pipeline.chosenValues + (KEY to group)))

    fun stamp(result: EffectResult, group: String?): EffectResult {
        if (group == null) return result
        val frames = result.state.continuationStack.map { frame ->
            val suspension = frame as? Suspension
            val answer = suspension?.answer as? EntersWithChoiceOnBattlefieldContinuation
            if (answer == null || answer.simultaneousEntryGroup != null ||
                suspension.question.id != result.pendingDecision?.id) frame
            else suspension.copy(answer = answer.copy(simultaneousEntryGroup = group))
        }
        return result.copy(state = result.state.copy(continuationStack = frames), events = result.events.map {
            if (it is ZoneChangeEvent && it.toZone == Zone.BATTLEFIELD) it.copy(simultaneousEntryGroup = group)
            else it
        })
    }
}
