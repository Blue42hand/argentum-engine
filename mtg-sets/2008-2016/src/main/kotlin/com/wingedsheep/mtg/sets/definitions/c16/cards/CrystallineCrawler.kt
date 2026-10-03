package com.wingedsheep.mtg.sets.definitions.c16.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.convergeEntersWithCounters
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Crystalline Crawler — Commander 2016 #54. */
val CrystallineCrawler = card("Crystalline Crawler") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Construct"
    power = 1
    toughness = 1
    oracleText = "Converge — This creature enters with a +1/+1 counter on it for each color of mana spent to cast it.\n" +
        "Remove a +1/+1 counter from this creature: Add one mana of any color.\n" +
        "{T}: Put a +1/+1 counter on this creature."

    convergeEntersWithCounters()

    activatedAbility {
        cost = Costs.RemoveCounterFromSelf(Counters.PLUS_ONE_PLUS_ONE)
        effect = Effects.AddAnyColorMana()
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "54"
        artist = "Jason Felix"
        imageUri = "https://cards.scryfall.io/normal/front/4/1/414d65f0-36a3-42af-997c-af752898c74c.jpg?1783937080"
        ruling("2016-11-08", "Colorless mana contributes no color to the converge count.")
        ruling("2016-11-08", "Mana spent on alternative or additional costs also counts toward converge.")
    }
}
