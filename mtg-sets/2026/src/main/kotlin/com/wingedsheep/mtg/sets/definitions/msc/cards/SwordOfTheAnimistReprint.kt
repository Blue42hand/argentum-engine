package com.wingedsheep.mtg.sets.definitions.msc.cards

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/** Sword of the Animist presentation rows; canonical definition is in ORI. */

val SwordOfTheAnimistReprint = Printing(
    oracleId = "d79cbc61-6c15-48ea-bbba-3cffb819ccba",
    name = "Sword of the Animist",
    setCode = "MSC",
    collectorNumber = "289",
    scryfallId = "ff890d40-710d-4b28-b303-37f4b7adcf42",
    artist = "Domenico Cava",
    imageUri = "https://cards.scryfall.io/normal/front/f/f/ff890d40-710d-4b28-b303-37f4b7adcf42.jpg?1783903185",
    releaseDate = "2026-06-26",
    rarity = Rarity.RARE,
    frameEffects = listOf("legendary"),
)

val SwordOfTheAnimistAlternateReprint = Printing(
    oracleId = "d79cbc61-6c15-48ea-bbba-3cffb819ccba",
    name = "Sword of the Animist",
    setCode = "MSC",
    collectorNumber = "452",
    scryfallId = "450697f9-4584-4a4b-b52c-1766b2af7048",
    artist = "Domenico Cava",
    imageUri = "https://cards.scryfall.io/normal/front/4/5/450697f9-4584-4a4b-b52c-1766b2af7048.jpg?1783903128",
    releaseDate = "2026-06-26",
    rarity = Rarity.RARE,
    frameEffects = listOf("legendary", "extendedart"),
)
