package com.wingedsheep.mtg.sets.definitions.msc.cards

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/** Scavenger Grounds reprint in MSC; canonical definition is in HOU. */

val ScavengerGroundsReprint = Printing(
    oracleId = "5ece7d03-9ee7-4953-a06e-9d8e41874903",
    name = "Scavenger Grounds",
    setCode = "MSC",
    collectorNumber = "263",
    scryfallId = "9fbe68ba-ffe5-4fe0-ac0a-0b3221e4f395",
    artist = "Bastien Grivet",
    imageUri = "https://cards.scryfall.io/normal/front/9/f/9fbe68ba-ffe5-4fe0-ac0a-0b3221e4f395.jpg",
    releaseDate = "2026-06-26",
    rarity = Rarity.RARE,
)

val ScavengerGroundsVariantReprint = Printing(
    oracleId = "5ece7d03-9ee7-4953-a06e-9d8e41874903",
    name = "Scavenger Grounds",
    setCode = "MSC",
    collectorNumber = "491",
    scryfallId = "eb8e7aa9-e626-43d3-b83f-dfe529c9bc6b",
    artist = "Bastien Grivet",
    imageUri = "https://cards.scryfall.io/normal/front/e/b/eb8e7aa9-e626-43d3-b83f-dfe529c9bc6b.jpg",
    releaseDate = "2026-06-26",
    rarity = Rarity.RARE,
    frameEffects = listOf("extendedart"),
)
