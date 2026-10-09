package com.wingedsheep.mtg.sets.definitions.pls.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.EmitLibrarySearchedEventEffect


/**
 * Diabolic Intent
 * {1}{B}
 * Sorcery
 * As an additional cost to cast this spell, sacrifice a creature.
 * Search your library for a card, put that card into your hand, then shuffle.
 */
val DiabolicIntent = card("Diabolic Intent") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "As an additional cost to cast this spell, sacrifice a creature.\nSearch your library for a card, put that card into your hand, then shuffle."
    additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Creature))
    spell {
        effect = Effects.Pipeline {
            val searchable = gather(CardSource.FromZone(Zone.LIBRARY), search = true)
            val found = chooseExactly(1, from = searchable)
            toHand(found)
            run(Effects.ShuffleLibrary())
            run(EmitLibrarySearchedEventEffect)
        }
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "42"
        artist = "Dave Dorman"
        imageUri = "https://cards.scryfall.io/normal/front/7/6/76d1b5c5-cc47-465f-8549-4fd1ca4280df.jpg"
    }
}
