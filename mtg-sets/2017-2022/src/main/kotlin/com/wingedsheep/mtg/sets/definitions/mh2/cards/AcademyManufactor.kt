package com.wingedsheep.mtg.sets.definitions.mh2.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ReplaceTokenCreationWithTokens

/** Replaces every qualifying token in one creation event, before any token enters. */
val AcademyManufactor = card("Academy Manufactor") {
    manaCost = "{3}"
    typeLine = "Artifact Creature — Assembly-Worker"
    power = 1
    toughness = 3
    oracleText = "If you would create a Clue, Food, or Treasure token, instead create one of each."

    replacementEffect(ReplaceTokenCreationWithTokens(
        tokenTypes = listOf("Clue", "Food", "Treasure"),
        appliesTo = EventPattern.TokenCreationEvent(
            tokenFilter = GameObjectFilter.Any.withAnySubtype("Clue", "Food", "Treasure")
        )
    ))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "219"
        artist = "Campbell White"
        flavorText = "Automated systems at the Tolarian Academy sort new acquisitions for optimal use, determining which should be studied, eaten, or sold."
        imageUri = "https://cards.scryfall.io/normal/front/9/0/90744c2c-6dd7-4623-8cbf-b138b83ee88f.jpg?1783926807"
        ruling("2021-06-18", "If you control one Academy Manufactor and would create some number of Clue, Food, or Treasure tokens, you will instead create that many Clue tokens, that many Food tokens, and that many Treasure tokens.")
        ruling("2021-06-18", "If you control two Academy Manufactors and would create some number of Clue, Food, or Treasure tokens, you will instead create three times that many Clue tokens, three times that many Food tokens, and three times that many Treasure tokens.")
        ruling("2021-06-18", "If you control eighteen Academy Manufactors (I don't know, you figure it out) and would create some number of Clue, Food, or Treasure tokens, you will instead create 129,140,163 times that many Clue tokens, 129,140,163 times that many Food tokens, and 129,140,163 times that many Treasure tokens.")
        ruling("2021-06-18", "Creating a Clue token this way isn't the same thing as investigating. Notably, an ability that triggers whenever you investigate won't trigger because you created a Clue token due to this replacement effect. If the original token creation was because you investigated once, those abilities will still each trigger once, no matter how many Clue tokens you end up creating.")
    }
}
