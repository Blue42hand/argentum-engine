package com.wingedsheep.mtg.sets.definitions.msc.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Vibranium Mining Mech — Marvel Super Heroes Commander #117. */
val VibraniumMiningMech = card("Vibranium Mining Mech") {
    manaCost = "{4}"
    typeLine = "Artifact — Vehicle"
    power = 6
    toughness = 6
    oracleText =
        "Trample\n" +
        "Whenever this Vehicle enters or attacks, create a tapped Vibranium token. " +
        "(It's an artifact with indestructible and \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")\n" +
        "{2}: This Vehicle gets +1/+0 until end of turn.\n" +
        "Crew 2"

    keywords(Keyword.TRAMPLE)

    val createVibranium: Effect = Effects.CreateVibranium(
        tapped = true,
        imageUri = "https://cards.scryfall.io/normal/front/7/f/7f9e9b3a-c515-449a-95da-ee3f5175b74f.jpg?1783902811",
    )

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = createVibranium
        description = "When this Vehicle enters, create a tapped Vibranium token"
    }

    triggeredAbility {
        trigger = Triggers.self.attacks()
        effect = createVibranium
        description = "When this Vehicle attacks, create a tapped Vibranium token"
    }

    activatedAbility {
        cost = Costs.Mana("{2}")
        effect = Effects.ModifyStats(1, 0, EffectTarget.Self)
        description = "This Vehicle gets +1/+0 until end of turn"
    }

    keywordAbility(KeywordAbility.crew(2))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "117"
        artist = "Ivan Dedov"
        imageUri = "https://cards.scryfall.io/normal/front/2/2/2212e15b-5deb-4ab8-9f6f-7991e750e21a.jpg?1783903254"
    }
}
