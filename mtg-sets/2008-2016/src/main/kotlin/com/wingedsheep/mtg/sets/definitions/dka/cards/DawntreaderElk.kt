package com.wingedsheep.mtg.sets.definitions.dka.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination

val DawntreaderElk = card("Dawntreader Elk") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elk"
    oracleText = "{G}, Sacrifice this creature: Search your library for a basic land card, " +
        "put it onto the battlefield tapped, then shuffle."
    power = 2
    toughness = 2

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{G}"), Costs.SacrificeSelf)
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.BasicLand,
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = true,
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "111"
        artist = "John Avon"
        flavorText = "Silent as winter snow, it seeks wild places unspoiled by the walking dead."
        imageUri = "https://cards.scryfall.io/normal/front/1/2/127c969b-1c9a-4265-af0e-5b9dbe136064.jpg?1783940809"
    }
}
