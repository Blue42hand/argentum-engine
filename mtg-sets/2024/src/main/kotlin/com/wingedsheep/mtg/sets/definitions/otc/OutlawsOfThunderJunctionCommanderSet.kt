package com.wingedsheep.mtg.sets.definitions.otc

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/** Outlaws of Thunder Junction Commander (OTC), intentionally incomplete. */
object OutlawsOfThunderJunctionCommanderSet : MtgSet {
    override val code = "OTC"
    override val displayName = "Outlaws of Thunder Junction Commander"
    override val releaseDate = "2024-04-19"
    override val sealedSupported = false
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy { CardDiscovery.findIn(CARDS_PACKAGE) }
    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code)
    }
    override val printings: List<Printing> by lazy { CardDiscovery.findPrintingsIn(CARDS_PACKAGE) }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.otc.cards"
}
