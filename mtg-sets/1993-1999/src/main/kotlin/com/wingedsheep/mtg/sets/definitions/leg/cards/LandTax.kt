package com.wingedsheep.mtg.sets.definitions.leg.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val LandTax = card("Land Tax") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Enchantment"
    oracleText = "At the beginning of your upkeep, if an opponent controls more lands than you, you may search your library for up to three basic land cards, reveal them, put them into your hand, then shuffle."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        interveningIf = Conditions.OpponentControlsMoreLands
        optional = true
        effect = Patterns.Library.searchLibrary(filter = GameObjectFilter.BasicLand, count = 3, destination = SearchDestination.HAND, reveal = true)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "26"
        artist = "Brian Snõddy"
        imageUri = "https://cards.scryfall.io/normal/front/d/5/d53b20b0-67bc-4587-817b-efbf21cb2512.jpg?1783948083"
        ruling("2009-10-01", "You shuffle your library if you search, even if you don't put any basic land cards into your hand.")
        ruling("2009-10-01", "This ability has an \"intervening 'if' clause.\" That means (1) the ability won't trigger at all unless any one of your opponents controls more lands than you, and (2) the ability will do nothing if you control at least as many lands as each of your opponents by the time it resolves.")
        ruling("2006-10-15", "You can fetch any of the \"Snow-Covered\" lands, since they all also have the supertype basic.")
    }
}
