package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.sdk.model.EntityId

/** A complete legal unit allocation in offered order, or null when capacity is insufficient. */
object CounterAllocation {
    fun fullPayment(decision: SelectCardsDecision): List<EntityId>? {
        val limits = decision.selectionLimits ?: return null
        var remaining = decision.maxSelections
        val selected = mutableListOf<EntityId>()
        for (id in decision.options.distinct()) {
            val count = minOf(remaining, (limits[id] ?: 0).coerceAtLeast(0))
            repeat(count) { selected.add(id) }
            remaining -= count
            if (remaining == 0) return selected
        }
        return if (remaining == 0) selected else null
    }
}
