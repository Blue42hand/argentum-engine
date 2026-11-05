package com.wingedsheep.ai.engine.knowledge

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.effects.CantHaveKeywordsEffect
import com.wingedsheep.sdk.scripting.effects.Effect

/** Keyword denial that opens legal targets without waiting for combat. */
internal object TargetingDenial {
    private val TARGETING_KEYWORDS = setOf(Keyword.HEXPROOF, Keyword.SHROUD)

    fun reads(effect: Effect): Boolean = effect is CantHaveKeywordsEffect &&
        effect.keywords.isNotEmpty() && effect.keywords.all { it in TARGETING_KEYWORDS }
}
