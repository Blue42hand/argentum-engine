package com.wingedsheep.gym.server.config

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.model.CardDefinition

/**
 * Builds the complete card registry used by the Gym server.
 *
 * Keep non-Spring tooling on this factory rather than recreating a partial registry: compatibility
 * probes, batch runners, and future Commander Gym adapters need to answer the same "is this card
 * actually loadable?" question as the live Gym environment.
 */
internal fun createGymCardRegistry(): CardRegistry = CardRegistry().apply {
    // Predefined tokens are runtime dependencies of cards that create them.
    register(PredefinedTokens.allTokens)

    for (set in MtgSetCatalog.all) {
        register(set.cards.stampSetCode(set.code))
        // Basic-land variants are needed for exact deck identities such as "Swamp#BLB-270".
        register(set.basicLands)
        set.basicLandsFallback?.let { register(it.basicLands) }
    }
}

/** Stamp a set code onto any card that doesn't already carry one. */
private fun List<CardDefinition>.stampSetCode(setCode: String): List<CardDefinition> =
    map { if (it.setCode == null) it.copy(setCode = setCode) else it }
