package com.wingedsheep.mtg.sets.definitions.dmu.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule

val RelicOfLegends = card("Relic of Legends") {
    manaCost = "{3}"
    typeLine = "Artifact"
    oracleText = "{T}: Add one mana of any color.\nTap an untapped legendary creature you control: Add one mana of any color."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddManaOfChoice()
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "Tap: Add one mana of any color"
    }

    activatedAbility {
        cost = Costs.TapPermanents(1, GameObjectFilter.Creature.legendary())
        effect = Effects.AddManaOfChoice()
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "Tap a legendary creature: Add one mana of any color"
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "236"
        artist = "Titus Lunter"
        flavorText = "\"If the worst parts of history can repeat themselves, it stands to reason that the best parts can as well.\"\n—Teferi"
        imageUri = "https://cards.scryfall.io/normal/front/6/4/64a2809e-c441-416c-90ff-6fb1e246dff3.jpg?1783921269"
        ruling("2022-09-09", "You can tap any untapped legendary creature you control, including one you haven't controlled continuously since the beginning of your most recent turn, to pay the cost of Relic of Legends's activated ability.")
    }
}
