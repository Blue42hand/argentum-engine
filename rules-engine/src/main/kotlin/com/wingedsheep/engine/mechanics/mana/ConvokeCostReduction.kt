package com.wingedsheep.engine.mechanics.mana

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.ManaSymbol

/** The same one-colored-pip credit for native offer checks and actual payment. */
internal fun reduceConvokeColorCost(cost: ManaCost, color: Color): ManaCost {
    val mutableSymbols = cost.symbols.toMutableList()
    val coloredSymbol = ManaSymbol.Colored(color)

    val exactIndex = mutableSymbols.indexOfFirst { it == coloredSymbol }
    if (exactIndex >= 0) {
        mutableSymbols.removeAt(exactIndex)
        return ManaCost(mutableSymbols)
    }

    val hybridIndex = mutableSymbols.indexOfFirst { symbol ->
        (symbol is ManaSymbol.HybridPair && (symbol.color1 == color || symbol.color2 == color)) ||
            (symbol is ManaSymbol.MonocolorHybrid && symbol.color == color)
    }
    if (hybridIndex >= 0) {
        mutableSymbols.removeAt(hybridIndex)
    }

    return ManaCost(mutableSymbols)

}
