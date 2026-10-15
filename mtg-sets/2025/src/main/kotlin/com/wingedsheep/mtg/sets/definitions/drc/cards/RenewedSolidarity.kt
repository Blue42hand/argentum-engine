package com.wingedsheep.mtg.sets.definitions.drc.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val RenewedSolidarity = card("Renewed Solidarity") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment"
    oracleText = "As this enchantment enters, choose a creature type.\nCreatures you control of the chosen type get +1/+0.\nAt the beginning of your end step, for each token you control of the chosen type that entered this turn, create a token that's a copy of it."

    replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE))
    staticAbility {
        ability = ModifyStats(1, 0, filter = GroupFilter(GameObjectFilter.Creature.youControl(), chosenSubtypeKey = "chosenCreatureType"))
    }
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Token.youControl().withChosenSubtype().enteredThisTurn()),
            Effects.CreateTokenCopyOfTarget(EffectTarget.IterationEntity)
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "7"
        artist = "Julie Dillon"
        imageUri = "https://cards.scryfall.io/normal/front/9/a/9a72b230-dbb0-4d38-ba7a-fb0bd03b6c47.jpg?1783907752"
        ruling("2025-02-07", "When this enchantment’s last ability resolves, for each applicable token you control that entered this turn, create a token that copies the original characteristics of that first token as stated by the effect that created it (unless that first token is copying something else; see below). It doesn’t copy whether that token is tapped or untapped, whether it has any counters on it or Auras or Equipment attached to it, or any non-copy effects that have changed its power, toughness, types, color, and so on.")
        ruling("2025-02-07", "If the last ability creates a copy of a token that is copying something else, then the new token enters as a copy of whatever the original token copied.")
        ruling("2025-02-07", "Any abilities of the copied token that trigger when that permanent enters will trigger. Any “as [this permanent] enters” or “[this permanent] enters with” abilities of the copied token will also work.")
    }
}
