package com.wingedsheep.mtg.sets.definitions.dst.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Eater of Days — Darksteel #120. */
val EaterOfDays = card("Eater of Days") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Leviathan"
    oracleText = "Flying, trample\nWhen this creature enters, you skip your next two turns."
    power = 9
    toughness = 8

    keywords(Keyword.FLYING, Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.SkipNextTurn(count = DynamicAmount.Fixed(2))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "120"
        artist = "Mark Tedin"
        flavorText = "When Mirrodin's varied civilizations developed ways to fight the levelers, Memnarch upped the stakes."
        imageUri = "https://cards.scryfall.io/normal/front/e/f/ef6870db-8aca-4aee-8e4d-c56a7d8dc242.jpg?1783944424"
    }
}
