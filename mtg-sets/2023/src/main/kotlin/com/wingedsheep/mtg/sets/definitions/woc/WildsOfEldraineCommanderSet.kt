package com.wingedsheep.mtg.sets.definitions.woc

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/** Wilds of Eldraine Commander (WOC), intentionally incomplete. */
object WildsOfEldraineCommanderSet : MtgSet {
    override val code = "WOC"
    override val displayName = "Wilds of Eldraine Commander"
    override val releaseDate = "2023-09-08"
    override val sealedSupported = false
    override val incomplete = true
    override val cards: List<CardDefinition> by lazy { CardDiscovery.findIn(CARDS_PACKAGE) }
    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code)
    }
    override val printings: List<Printing> by lazy { CardDiscovery.findPrintingsIn(CARDS_PACKAGE) }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.woc.cards"
}
