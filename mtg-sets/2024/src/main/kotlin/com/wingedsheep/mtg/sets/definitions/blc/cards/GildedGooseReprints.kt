package com.wingedsheep.mtg.sets.definitions.blc.cards

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/** Gilded Goose reprints in Bloomburrow Commander. */
val GildedGooseReprint = Printing(
    oracleId = "f2f09757-1931-47c0-a5f0-39280445489d",
    name = "Gilded Goose",
    setCode = "BLC",
    collectorNumber = "221",
    scryfallId = "8d3facb6-314c-4dd2-a570-4515c596163c",
    artist = "Lindsey Look",
    imageUri = "https://cards.scryfall.io/normal/front/8/d/8d3facb6-314c-4dd2-a570-4515c596163c.jpg?1783910667",
    releaseDate = "2024-08-02",
    rarity = Rarity.RARE,
)

val GildedGooseInvertedReprint = Printing(
    oracleId = "f2f09757-1931-47c0-a5f0-39280445489d",
    name = "Gilded Goose",
    setCode = "BLC",
    collectorNumber = "83",
    scryfallId = "c9ab64c3-130e-4afc-9589-6d5654daeac7",
    artist = "Narendra Bintara Adi",
    imageUri = "https://cards.scryfall.io/normal/front/c/9/c9ab64c3-130e-4afc-9589-6d5654daeac7.jpg?1783910712",
    releaseDate = "2024-08-02",
    rarity = Rarity.RARE,
    frameEffects = listOf("inverted"),
)
