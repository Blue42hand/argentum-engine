package com.wingedsheep.mtg.sets.definitions.dst.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/** Coretapper — Darksteel #107. */
val Coretapper = card("Coretapper") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Myr"
    oracleText = "{T}: Put a charge counter on target artifact.\n" +
            "Sacrifice this creature: Put two charge counters on target artifact."
    power = 1
    toughness = 1

    activatedAbility {
        cost = Costs.Tap
        val artifact = target(TargetObject(filter = TargetFilter.Artifact))
        effect = Effects.AddCounters(CounterType.CHARGE, 1, artifact)
        description = "{T}: Put a charge counter on target artifact."
    }

    activatedAbility {
        cost = Costs.SacrificeSelf
        val artifact = target(TargetObject(filter = TargetFilter.Artifact))
        effect = Effects.AddCounters(CounterType.CHARGE, 2, artifact)
        description = "Sacrifice this creature: Put two charge counters on target artifact."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "107"
        artist = "Dany Orizio"
        flavorText = "It converts the faintest surges of power from Mirrodin's core into usable energy, providing endless power for Memnarch's creations on the surface."
        imageUri = "https://cards.scryfall.io/normal/front/b/b/bbeaca72-aadd-4ef8-939a-36f320100e6e.jpg?1783944428"
    }
}
