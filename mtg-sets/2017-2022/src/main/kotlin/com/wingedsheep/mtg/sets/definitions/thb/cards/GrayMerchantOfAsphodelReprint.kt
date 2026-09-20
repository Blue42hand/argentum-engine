package com.wingedsheep.mtg.sets.definitions.thb.cards

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

val GrayMerchantOfAsphodelReprint = Printing(
    oracleId = "38f3b157-0df4-409b-89cc-086e1531cd5b",
    name = "Gray Merchant of Asphodel",
    setCode = "THB",
    collectorNumber = "99",
    scryfallId = "7c1a7dd8-8034-4f59-a351-33666b26ff5a",
    artist = "Scott Murphy",
    imageUri = "https://cards.scryfall.io/normal/front/7/c/7c1a7dd8-8034-4f59-a351-33666b26ff5a.jpg?1783931566",
    releaseDate = "2020-01-24",
    rarity = Rarity.UNCOMMON,
)

val GrayMerchantOfAsphodelPromoReprint = Printing(
    oracleId = "38f3b157-0df4-409b-89cc-086e1531cd5b",
    name = "Gray Merchant of Asphodel",
    setCode = "THB",
    collectorNumber = "355",
    scryfallId = "d794d0a4-4041-43c0-aedb-e2a76611a3ea",
    artist = "Scott Murphy",
    imageUri = "https://cards.scryfall.io/normal/front/d/7/d794d0a4-4041-43c0-aedb-e2a76611a3ea.jpg?1783931458",
    releaseDate = "2020-01-24",
    rarity = Rarity.UNCOMMON,
    isPromo = true,
    frameEffects = listOf("inverted"),
)
