package com.wingedsheep.mtg.sets.definitions.nem.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.SelfAlternativeCost

/** Mogg Alarm — Nemesis #93. */
val MoggAlarm = card("Mogg Alarm") {
    manaCost = "{1}{R}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "You may sacrifice two Mountains rather than pay this spell's mana cost.\n" +
        "Create two 1/1 red Goblin creature tokens."

    selfAlternativeCost = SelfAlternativeCost(
        manaCost = ManaCost.parse("{0}"),
        additionalCosts = listOf(
            Costs.additional.SacrificePermanent(Filters.MountainCard, count = 2)
        )
    )

    spell {
        effect = Effects.CreateToken(
            count = 2,
            power = 1,
            toughness = 1,
            colors = setOf(Color.RED),
            creatureTypes = setOf("Goblin"),
            imageUri = "https://cards.scryfall.io/normal/front/1/d/1db51576-a755-45de-b37b-f16b27e8f3a1.jpg?1783922311",
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "93"
        artist = "Dave Dorman"
        flavorText = "They make mountains into mogg holes."
        imageUri = "https://cards.scryfall.io/normal/front/f/2/f246e128-0a43-478a-a232-51020fab76d5.jpg?1783945817"
    }
}
