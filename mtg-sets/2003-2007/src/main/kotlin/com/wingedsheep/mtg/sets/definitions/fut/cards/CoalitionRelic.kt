package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Coalition Relic
 * {3}
 * Artifact
 */
val CoalitionRelic = card("Coalition Relic") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{T}: Add one mana of any color.\n" +
        "{T}: Put a charge counter on this artifact.\n" +
        "At the beginning of your first main phase, remove all charge counters from this artifact. " +
        "Add one mana of any color for each charge counter removed this way."

    activatedAbility {
        cost = Costs.Tap
        manaAbility = true
        effect = Effects.AddManaOfChoice()
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddCounters(Counters.CHARGE, 1, EffectTarget.Self)
    }

    triggeredAbility {
        trigger = Triggers.FirstMainPhase
        effect = Effects.Composite(
            listOf(
                Effects.StoreNumber(
                    "removedChargeCounters",
                    DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.CHARGE)),
                ),
                Effects.RemoveAllCountersOfType(Counters.CHARGE, EffectTarget.Self),
                Effects.AddManaInAnyCombination(
                    DynamicAmount.VariableReference("removedChargeCounters")
                ),
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "161"
        artist = "Donato Giancola"
        imageUri = "https://cards.scryfall.io/normal/front/7/a/7a7c98b0-d64d-4d0a-b284-1187a8e7095e.jpg?1783943092"
        ruling("2021-03-19", "If you remove multiple charge counters from Coalition Relic at once, you may add a different color of mana for each one.")
        ruling("2021-03-19", "Only the first main phase each turn is considered a precombat main phase, even if additional main phases or combat phases are created.")
    }
}
