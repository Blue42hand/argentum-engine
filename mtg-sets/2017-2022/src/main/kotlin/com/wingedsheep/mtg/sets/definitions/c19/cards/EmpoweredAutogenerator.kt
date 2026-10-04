package com.wingedsheep.mtg.sets.definitions.c19.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Empowered Autogenerator — Commander 2019 #54. */
val EmpoweredAutogenerator = card("Empowered Autogenerator") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "This artifact enters tapped.\n" +
            "{T}: Put a charge counter on this artifact. Add X mana of any one color, where X is the number of charge counters on this artifact."

    replacementEffect(EntersTapped())

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.Composite(listOf(
            Effects.AddCounters(CounterType.CHARGE, 1, EffectTarget.Self),
            Effects.AddAnyColorMana(DynamicAmounts.countersOnSelf(CounterType.CHARGE)),
        ))
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Put a charge counter on this artifact. Add X mana of any one color, where X is the number of charge counters on this artifact."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "54"
        artist = "Piotr Dura"
        flavorText = "Magic, unlike physics, has no unbreakable laws."
        imageUri = "https://cards.scryfall.io/normal/front/2/c/2c9b8d53-97c9-4e3d-a8c6-6abd78db6390.jpg?1783932793"
        ruling("2019-08-23", "Empowered Autogenerator's activated ability is a mana ability. It doesn't use the stack and can't be responded to.")
    }
}
