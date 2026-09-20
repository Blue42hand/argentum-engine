package com.wingedsheep.mtg.sets.definitions.soc

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/** Secrets of Strixhaven Commander (SOC), released April 24, 2026. */
object SecretsOfStrixhavenCommanderSet : MtgSet {
    override val code = "SOC"
    override val displayName = "Secrets of Strixhaven Commander"
    override val releaseDate = "2026-04-24"
    override val sealedSupported = false
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.soc.cards"
}
