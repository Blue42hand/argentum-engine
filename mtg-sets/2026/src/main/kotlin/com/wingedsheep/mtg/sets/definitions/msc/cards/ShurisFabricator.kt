package com.wingedsheep.mtg.sets.definitions.msc.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

val ShurisFabricator = card("Shuri's Fabricator") {
    manaCost = "{4}"
    typeLine = "Artifact"
    oracleText =
        "When this artifact enters, create two tapped Vibranium tokens. (They're artifacts with " +
        "indestructible and \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")\n" +
        "{6}, {T}: Return target artifact card from your graveyard to the battlefield with a " +
        "finality counter on it. Activate only as a sorcery. (If a permanent with a finality " +
        "counter on it would be put into a graveyard, exile it instead.)"

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateVibranium(
            count = 2,
            tapped = true,
            imageUri = "https://cards.scryfall.io/normal/front/7/f/7f9e9b3a-c515-449a-95da-ee3f5175b74f.jpg?1783902811",
        )
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{6}"), Costs.Tap)
        timing = TimingRule.SorcerySpeed
        target = TargetObject(filter = TargetFilter.ArtifactInYourGraveyard)
        effect = Effects.Move(
            target = EffectTarget.ContextTarget(0),
            destination = Zone.BATTLEFIELD,
            fromZone = Zone.GRAVEYARD,
            addCounterType = CounterType.FINALITY,
        )
        description = "Return target artifact card from your graveyard with a finality counter"
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "114"
        artist = "L.A. Draws"
        imageUri = "https://cards.scryfall.io/normal/front/a/c/ace834f0-7f64-4716-8599-f96f674e61a1.jpg?1783903257"
    }
}
