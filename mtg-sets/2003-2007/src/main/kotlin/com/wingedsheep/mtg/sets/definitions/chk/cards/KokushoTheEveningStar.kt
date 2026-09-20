package com.wingedsheep.mtg.sets.definitions.chk.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Kokusho, the Evening Star — Champions of Kamigawa #122
 *
 * [Effects.DrainLife] aggregates the life actually lost by every opponent into one gain for
 * Kokusho's controller, preserving the printed multiplayer behavior.
 */
val KokushoTheEveningStar = card("Kokusho, the Evening Star") {
    manaCost = "{4}{B}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Creature — Dragon Spirit"
    power = 5
    toughness = 5
    oracleText = "Flying\nWhen Kokusho dies, each opponent loses 5 life. You gain life equal to " +
        "the life lost this way."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.Dies
        effect = Effects.DrainLife(5)
        description = "When Kokusho dies, each opponent loses 5 life. You gain life equal to the " +
            "life lost this way."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "122"
        artist = "Tsutomu Kawade"
        imageUri = "https://cards.scryfall.io/normal/front/6/3/63dc0698-e92f-4134-80e4-5cc37b80e37c.jpg?1783944312"
    }
}
