package com.wingedsheep.engine.handlers.actions.spell

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.ManaSymbol
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom

/**
 * A resource-derived upper bound on repeatable optional-cost payments. The current repeatable
 * costs consume mana, life, or player counters. Use Long arithmetic so a hostile cast count cannot
 * overflow a scaled life/counter cost or allocate billions of repeated mana symbols.
 *
 * A cost with no positive, finite resource payment has no enumerable upper bound; callers must
 * offer only the first payment until a numeric count-choice action can represent that case.
 */
internal fun repeatableOptionalCostLimit(
    state: GameState,
    playerId: EntityId,
    costs: List<KeywordAbility.OptionalAdditionalCost>,
    availableMana: Long,
): Int? {
    val limits = buildList<Long> {
        for (cost in costs) {
            // A monocolored hybrid {2/U} can be paid with one blue mana even though its mana
            // value is two. Count the cheapest payable units, not mana value.
            val manaPerPayment = cost.manaCost?.symbols?.sumOf { symbol ->
                when (symbol) {
                    is ManaSymbol.Generic -> symbol.amount.toLong()
                    ManaSymbol.X -> 0L
                    else -> 1L
                }
            } ?: 0L
            if (manaPerPayment > 0) {
                val lifeForPhyrexian = if (cost.manaCost?.phyrexianSymbols?.isNotEmpty() == true) {
                    state.lifeTotal(playerId).toLong().coerceAtLeast(0) / 2
                } else 0L
                add((availableMana.coerceAtLeast(0) + lifeForPhyrexian) / manaPerPayment)
            }
            when (val atom = (cost.additionalCost as? AdditionalCost.Atom)?.atom) {
                is CostAtom.PayLife -> if (atom.amount > 0) {
                    add(state.lifeTotal(playerId).toLong().coerceAtLeast(0) / atom.amount)
                }
                is CostAtom.PayPlayerCounters -> {
                    val amount = atom.amount as? DynamicAmount.Fixed
                    if (amount != null && amount.amount > 0) {
                        val held = state.getEntity(playerId)?.get<CountersComponent>()
                            ?.getCount(atom.counterType) ?: 0
                        add(held.toLong().coerceAtLeast(0) / amount.amount)
                    }
                }
                else -> Unit
            }
        }
    }
    return limits.minOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
}

/** Find the last payable count without materializing one legal action per payment. */
internal fun maxAffordableOptionalCostTimes(upperBound: Int, canPay: (Int) -> Boolean): Int {
    var low = 0L
    var high = upperBound.toLong()
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (canPay(middle.toInt())) low = middle else high = middle - 1
    }
    return low.toInt()
}
