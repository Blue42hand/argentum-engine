package com.wingedsheep.mtg.sets.definitions.tmc

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Teenage Mutant Ninja Turtles Commander (2026)
 *
 * Set Code: TMC
 * Release Date: March 6, 2026
 */
object TeenageMutantNinjaTurtlesCommanderSet : MtgSet {

    override val code = "TMC"
    override val displayName = "Teenage Mutant Ninja Turtles Commander"
    override val releaseDate = "2026-03-06"
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.tmc.cards"
}
