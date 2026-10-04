package com.wingedsheep.mtg.sets.definitions.msc.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

val PantherRobot = card("Panther Robot") {
    manaCost = "{10}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Cat Robot"
    power = 8
    toughness = 8
    oracleText = "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nReach, trample"

    keywordAbility(KeywordAbility.Affinity(CardType.ARTIFACT))
    keywords(Keyword.REACH, Keyword.TRAMPLE)

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "113"
        artist = "Andreia Ugrai"
        flavorText = "Immensely powerful, the Prowlers may be released only by Wakanda's royal family."
        imageUri = "https://cards.scryfall.io/normal/front/8/b/8bb7185d-fc07-4f2f-9762-aa7e7c287cba.jpg?1783903257"
    }
}
