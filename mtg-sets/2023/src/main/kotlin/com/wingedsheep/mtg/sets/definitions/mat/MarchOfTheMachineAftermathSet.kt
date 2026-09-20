package com.wingedsheep.mtg.sets.definitions.mat

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * March of the Machine: The Aftermath (2023)
 *
 * Set Code: MAT
 * Release Date: May 12, 2023
 */
object MarchOfTheMachineAftermathSet : MtgSet {

    override val code = "MAT"
    override val displayName = "March of the Machine: The Aftermath"
    override val releaseDate = "2023-05-12"
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

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.mat.cards"
}
