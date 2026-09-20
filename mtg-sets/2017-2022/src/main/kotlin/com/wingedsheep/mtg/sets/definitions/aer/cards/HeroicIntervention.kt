package com.wingedsheep.mtg.sets.definitions.aer.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val HeroicIntervention = card("Heroic Intervention") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Permanents you control gain hexproof and indestructible until end of turn."

    spell {
        effect = Effects.ForEachInGroup(
            filter = GroupFilter(baseFilter = GameObjectFilter.Permanent.youControl()),
            effect = Effects.Composite(
                Effects.GrantKeyword(Keyword.HEXPROOF, EffectTarget.Self, Duration.EndOfTurn),
                Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.Self, Duration.EndOfTurn),
            ),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "109"
        artist = "James Ryman"
        flavorText = "\"Wherever the strong would harm the weak, I will be there.\"\n—Ajani Goldmane"
        imageUri = "https://cards.scryfall.io/normal/front/8/f/8f5a620c-fde7-4b72-bf8a-efc4f14560c5.jpg?1783936743"
        ruling("2023-07-28", "A battle with indestructible still loses defense counters as it's dealt damage. If it's a Siege, it will still be exiled when the last defense counter is removed from it, and its controller may still cast it transformed without paying its mana cost.")
        ruling("2020-06-23", "A planeswalker with indestructible still loses loyalty counters as it's dealt damage and will still be put into its owner's graveyard if its loyalty reaches 0.")
        ruling("2020-06-23", "The set of permanents affected by Heroic Intervention is determined as the spell resolves. Permanents you begin to control later in the turn won't gain hexproof and indestructible.")
    }
}
