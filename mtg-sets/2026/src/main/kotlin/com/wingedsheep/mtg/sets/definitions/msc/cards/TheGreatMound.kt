package com.wingedsheep.mtg.sets.definitions.msc.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * The Great Mound
 * Land
 *
 * {T}: Add {C}.
 * {3}, {T}: Create a tapped Vibranium token.
 * {6}, {T}: Draw a card.
 */
val TheGreatMound = card("The Great Mound") {
    typeLine = "Land"
    oracleText =
        "{T}: Add {C}.\n" +
        "{3}, {T}: Create a tapped Vibranium token. (It's an artifact with indestructible and " +
        "\"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")\n" +
        "{6}, {T}: Draw a card."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{3}"), Costs.Tap)
        effect = Effects.CreateVibranium(
            tapped = true,
            imageUri = "https://cards.scryfall.io/normal/front/7/f/7f9e9b3a-c515-449a-95da-ee3f5175b74f.jpg?1783902811"
        )
        description = "Create a tapped Vibranium token"
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{6}"), Costs.Tap)
        effect = Effects.DrawCards(1)
        description = "Draw a card"
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "120"
        artist = "Pablo Mendoza"
        flavorText = "Mena Ngai, where the heaven-sent metal dwells."
        imageUri = "https://cards.scryfall.io/normal/front/1/1/11b3f96c-138e-431e-a1d6-5ebae4cb6b2f.jpg?1783903254"
    }
}
