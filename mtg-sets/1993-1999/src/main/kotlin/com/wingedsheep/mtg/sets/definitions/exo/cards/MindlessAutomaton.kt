package com.wingedsheep.mtg.sets.definitions.exo.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val MindlessAutomaton = card("Mindless Automaton") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Construct"
    power = 0
    toughness = 0
    oracleText = "This creature enters with two +1/+1 counters on it.\n" +
        "{1}, Discard a card: Put a +1/+1 counter on this creature.\n" +
        "Remove two +1/+1 counters from this creature: Draw a card."

    replacementEffect(EntersWithCounters(count = 2, selfOnly = true))

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.DiscardCard)
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "Discard a card: Put a +1/+1 counter on this creature."
    }

    activatedAbility {
        cost = Costs.RemoveCounterFromSelf(CounterType.PLUS_ONE_PLUS_ONE, 2)
        effect = Effects.DrawCards(1)
        description = "Remove two +1/+1 counters: Draw a card."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "135"
        artist = "Brian Snõddy"
        imageUri = "https://cards.scryfall.io/normal/front/6/d/6ddfc5ab-b11b-4ad7-ab46-8ee60d938a5b.jpg?1783946500"
        ruling("2020-11-10", "If removing two +1/+1 counters from Mindless Automaton causes the amount of damage already marked on Mindless Automaton to be equal to or greater than its toughness, it will be put into its owner's graveyard as a state-based action before the ability can be activated again and before the card is drawn.")
    }
}
