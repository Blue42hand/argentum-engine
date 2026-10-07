package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Chittering Witch — Throne of Eldraine #319. */
val ChitteringWitch = card("Chittering Witch") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Human Warlock"
    power = 2
    toughness = 2
    oracleText = "When this creature enters, create a number of 1/1 black Rat creature tokens equal to the number of opponents you have.\n" +
        "{1}{B}, Sacrifice a creature: Target creature gets -2/-2 until end of turn."

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(
            count = DynamicAmounts.playerCount(),
            power = 1,
            toughness = 1,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Rat"),
            imageUri = "https://cards.scryfall.io/normal/front/e/4/e43a205e-43ea-4b3e-92ab-c2ee2172a50a.jpg?1783932482",
        )
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}{B}"), Costs.Sacrifice(GameObjectFilter.Creature))
        val creature = target(TargetFilter.Creature)
        effect = Effects.ModifyStats(-2, -2, creature)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "319"
        artist = "Winona Nelson"
        imageUri = "https://cards.scryfall.io/normal/front/8/c/8c298940-4277-4145-9f2f-a284fd2e1a9d.jpg?1783932550"
    }
}
