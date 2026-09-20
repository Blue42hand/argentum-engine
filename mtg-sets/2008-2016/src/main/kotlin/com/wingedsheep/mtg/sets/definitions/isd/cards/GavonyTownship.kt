package com.wingedsheep.mtg.sets.definitions.isd.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddCountersEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Gavony Township
 * Land
 * {T}: Add {C}.
 * {2}{G}{W}, {T}: Put a +1/+1 counter on each creature you control.
 */
val GavonyTownship = card("Gavony Township") {
    colorIdentity = "GW"
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n" +
        "{2}{G}{W}, {T}: Put a +1/+1 counter on each creature you control."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}{G}{W}"), Costs.Tap)
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            AddCountersEffect(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "239"
        artist = "Peter Mohrbacher"
        flavorText = "\"The protective wards of the church have weakened, and no one can tell us why. " +
            "It's time to look to our own defenses.\"\n—Gregel, militia leader"
        imageUri = "https://cards.scryfall.io/normal/front/b/5/b5f73443-2fe8-424f-8e71-fc7ce1f3a3eb.jpg?1783940895"
    }
}
