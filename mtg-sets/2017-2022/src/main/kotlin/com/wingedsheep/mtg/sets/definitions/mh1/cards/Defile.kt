package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.times
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Defile — Modern Horizons #86. The Swamp count is locked when the spell resolves. */
val Defile = card("Defile") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Target creature gets -1/-1 until end of turn for each Swamp you control."

    spell {
        val creature = target(TargetFilter.Creature)
        val minusSwamps = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land.withSubtype("Swamp")).count() * -1
        effect = Effects.ModifyStats(minusSwamps, minusSwamps, creature)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "86"
        artist = "Joe Slucher"
        flavorText = "The only shortcut in a bog is to the bottom."
        imageUri = "https://cards.scryfall.io/normal/front/5/b/5bcb4398-edd1-41a7-a496-b12bce22ceb6.jpg?1783933130"
    }
}
