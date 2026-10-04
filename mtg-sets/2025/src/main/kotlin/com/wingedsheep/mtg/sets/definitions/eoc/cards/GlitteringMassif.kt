package com.wingedsheep.mtg.sets.definitions.eoc.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.KeywordAbility

/** Mountain and Plains intrinsic mana abilities come from the basic land subtypes. */
val GlitteringMassif = card("Glittering Massif") {
    colorIdentity = "RW"
    typeLine = "Land — Mountain Plains"
    oracleText = "({T}: Add {R} or {W}.)\nThis land enters tapped.\nCycling {2} ({2}, Discard this card: Draw a card.)"

    replacementEffect(EntersTapped())
    keywordAbility(KeywordAbility.cycling("{2}"))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "22"
        artist = "Cristi Balanescu"
        flavorText = "Iridescent debris and chromatic chemical fires litter Kavaron with the beauty of destruction."
        imageUri = "https://cards.scryfall.io/normal/front/c/6/c6ab42e4-4c87-4fc1-b7a9-1c0ff7d791fb.jpg?1783906060"
    }
}
