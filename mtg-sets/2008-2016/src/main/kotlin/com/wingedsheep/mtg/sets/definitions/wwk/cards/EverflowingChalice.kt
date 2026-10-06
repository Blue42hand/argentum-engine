package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.TimingRule

/** Everflowing Chalice — Worldwake #123. */
val EverflowingChalice = card("Everflowing Chalice") {
    manaCost = "{0}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Multikicker {2} (You may pay an additional {2} any number of times as you cast this spell.)\n" +
        "This artifact enters with a charge counter on it for each time it was kicked.\n" +
        "{T}: Add {C} for each charge counter on this artifact."

    keywordAbility(KeywordAbility.multikicker("{2}"))

    replacementEffect(EntersWithDynamicCounters(
        counterType = CounterType.CHARGE,
        count = DynamicAmounts.castChoice(ChoiceSlot.OPTIONAL_COST_TIMES),
    ))

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(DynamicAmounts.countersOnSelf(CounterType.CHARGE))
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C} for each charge counter on this artifact."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "123"
        artist = "Steve Argyle"
        imageUri = "https://cards.scryfall.io/normal/front/1/f/1fdcc0c3-4029-4fc3-a486-5d7f45c910bd.jpg?1783942040"
        ruling("2021-03-19", "You can cast Everflowing Chalice without kicking it at all if you wish. However, if Everflowing Chalice has no charge counters on it, activating its last ability won't produce any mana.")
    }
}
