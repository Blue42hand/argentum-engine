package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.MayPlayExpiry
import com.wingedsheep.sdk.scripting.effects.PlayAdditionalLandsEffect

/**
 * Escape to the Wilds
 * {3}{R}{G}
 * Sorcery
 *
 * Exile the top five cards of your library. You may play cards exiled this way until the end of
 * your next turn.
 * You may play an additional land this turn.
 */
val EscapeToTheWilds = card("Escape to the Wilds") {
    manaCost = "{3}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Sorcery"
    oracleText = "Exile the top five cards of your library. You may play cards exiled this way " +
        "until the end of your next turn.\nYou may play an additional land this turn."

    spell {
        effect = Patterns.Exile.impulse(
            count = 5,
            expiry = MayPlayExpiry.UntilEndOfNextTurn,
        ).then(PlayAdditionalLandsEffect(count = 1))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "189"
        artist = "Chris Ostrowski"
        flavorText = "The guards kindled the hearth and locked the door to Ellwen's chamber. " +
            "By morning, the fire was out and Ellwen was gone."
        imageUri = "https://cards.scryfall.io/normal/front/3/e/3e26c10b-179f-4a6e-bc8d-3ec1d6783fb9.jpg?1783932598"

        ruling("2019-10-04", "Escape to the Wilds doesn't change when you can play the exiled " +
            "cards. For example, if you exile a sorcery card, you can cast it only during your " +
            "main phase when the stack is empty. If you exile a land card, you can play it only " +
            "during your main phase and only if you have an available land play remaining.")
        ruling("2019-10-04", "Casting an exiled card causes it to leave exile. You can't cast it " +
            "multiple times.")
        ruling("2019-10-04", "The additional land that you play doesn't have to be from among the " +
            "exiled cards.")
        ruling("2019-10-04", "If you don't play a card exiled this way, it remains in exile.")
    }
}

/** Extended-art Escape to the Wilds (ELD 379). */
val EscapeToTheWildsExtendedArt = Printing(
    oracleId = "45a8c126-5396-4736-a37e-460a50706a98",
    name = "Escape to the Wilds",
    setCode = "ELD",
    collectorNumber = "379",
    scryfallId = "1bff641e-aad3-414f-ad5b-8d32c734efa9",
    artist = "Chris Ostrowski",
    imageUri = "https://cards.scryfall.io/normal/front/1/b/1bff641e-aad3-414f-ad5b-8d32c734efa9.jpg?1783932525",
    releaseDate = "2019-10-04",
    rarity = Rarity.RARE,
    frameEffects = listOf("extendedart"),
)
