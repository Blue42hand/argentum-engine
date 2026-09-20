package com.wingedsheep.mtg.sets.definitions.c21.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Monologue Tax
 * {2}{W}
 * Enchantment
 *
 * Whenever an opponent casts their second spell each turn, you create a Treasure token.
 */
val MonologueTax = card("Monologue Tax") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment"
    oracleText = "Whenever an opponent casts their second spell each turn, you create a Treasure token."

    triggeredAbility {
        trigger = Triggers.NthSpellCast(2, Player.EachOpponent)
        effect = Effects.CreateTreasure(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "19"
        artist = "Justine Cruz"
        flavorText = "\"Your unsolicited words enrich us all. Please, continue.\"\n" +
            "—Yold, professor of cryptorelicology"
        imageUri = "https://cards.scryfall.io/normal/front/e/9/e9449f23-6a14-453b-8bb6-5cf85ed7a851.jpg?1783927608"
        ruling("2021-04-16", "Nothing special happens on the third spell, fourth spell, and so on.")
        ruling("2021-04-16", "It doesn't matter if Monologue Tax was on the battlefield for the first spell. It also doesn't matter if that first spell resolved or not.")
        ruling("2021-04-16", "The ability can trigger once each turn for each opponent.")
    }
}
