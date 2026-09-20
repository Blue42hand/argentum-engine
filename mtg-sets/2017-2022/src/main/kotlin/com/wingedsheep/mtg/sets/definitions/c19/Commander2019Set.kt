package com.wingedsheep.mtg.sets.definitions.c19

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Commander 2019
 *
 * Canonical home for cards whose earliest real-expansion printing is Commander 2019.
 *
 * Set Code: C19
 * Release Date: August 23, 2019
 */
object Commander2019Set : MtgSet {

    override val code = "C19"
    override val displayName = "Commander 2019"
    override val releaseDate = "2019-08-23"
    override val sealedSupported = false

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.c19.cards"
}
