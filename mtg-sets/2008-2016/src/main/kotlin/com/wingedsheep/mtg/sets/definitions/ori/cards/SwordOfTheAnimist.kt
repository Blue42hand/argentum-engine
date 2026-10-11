package com.wingedsheep.mtg.sets.definitions.ori.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.effects.SearchDestination

val SwordOfTheAnimist = card("Sword of the Animist") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Legendary Artifact — Equipment"
    oracleText = "Equipped creature gets +1/+1.\nWhenever equipped creature attacks, you may search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\nEquip {2}"

    staticAbility { ability = ModifyStats(1, 1, Filters.EquippedCreature) }

    triggeredAbility {
        trigger = Triggers.attached.attacks()
        effect = Effects.May(
            Patterns.Library.searchLibrary(
                filter = GameObjectFilter.BasicLand,
                count = 1,
                destination = SearchDestination.BATTLEFIELD,
                entersTapped = true
            )
        )
        description = "You may search your library for a basic land card, put it onto the battlefield tapped, then shuffle"
    }

    equipAbility("{2}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "240"
        artist = "Daniel Ljunggren"
        flavorText = "The blade glows only for Zendikar's chosen."
        imageUri = "https://cards.scryfall.io/normal/front/f/3/f3687c36-c0ae-4dba-9379-b420236bf529.jpg?1783938307"
    }
}
