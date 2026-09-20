package com.wingedsheep.mtg.sets.definitions.soc.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Eclipsed Steppe — Secrets of Strixhaven Commander #53. */
val EclipsedSteppe = card("Eclipsed Steppe") {
    colorIdentity = "WB"
    typeLine = "Land — Plains Swamp"
    oracleText = "({T}: Add {W} or {B}.)\n" +
        "This land enters tapped unless you control two or more basic lands."

    // Mana abilities are intrinsic from the basic land types in the type line.
    replacementEffect(
        EntersTapped(
            unlessCondition = Compare(
                DynamicAmount.AggregateBattlefield(Player.You, GameObjectFilter.BasicLand),
                ComparisonOperator.GTE,
                DynamicAmount.Fixed(2),
            )
        )
    )

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "53"
        artist = "Leon Tukker"
        flavorText = "The Arch Obscura never appears for the same eyes twice. Those who bear witness either find peace in their own insignificance or dread in the unnatural twilight."
        imageUri = "https://cards.scryfall.io/normal/front/d/8/d890999a-dcc7-479e-b0f0-60388c737043.jpg?1783903847"
        ruling("2026-03-20", "If this land enters the battlefield at the same time as any number of basic lands, those other lands are not counted when determining if this land enters the battlefield tapped or untapped.")
        ruling("2026-03-20", "Unlike some other dual lands, Eclipsed Steppe has two basic land types. It's not basic, so effects that search for basic lands can't find it, but it does have the appropriate land types for effects such as that of Shineshadow Snarl (included in the Secrets of Strixhaven Commander decks).")
    }
}

/** Extended-art Eclipsed Steppe — Secrets of Strixhaven Commander #101. */
val EclipsedSteppeExtendedArt = Printing(
    oracleId = "6216635f-8e6e-40a3-9659-ef6352ab92ce",
    name = "Eclipsed Steppe",
    setCode = "SOC",
    collectorNumber = "101",
    scryfallId = "93ca39ae-0173-44ba-8c66-9f52f468983d",
    artist = "Leon Tukker",
    imageUri = "https://cards.scryfall.io/normal/front/9/3/93ca39ae-0173-44ba-8c66-9f52f468983d.jpg?1783903825",
    releaseDate = "2026-04-24",
    rarity = Rarity.RARE,
    frameEffects = listOf("extendedart"),
)
