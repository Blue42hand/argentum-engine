package com.wingedsheep.mtg.sets.definitions.om1

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Through the Omenpaths (2025)
 *
 * The in-universe counterpart of Marvel's Spider-Man, released for Magic Online and Arena.
 * Scaffolded to hold the canonical definitions of cards it printed first; incomplete until
 * all 188 are in.
 *
 * Set Code: OM1
 * Release Date: September 23, 2025
 */
object ThroughTheOmenpathsSet : MtgSet {

    override val code = "OM1"
    override val displayName = "Through the Omenpaths"
    override val releaseDate = "2025-09-23"
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.om1.cards"
}
