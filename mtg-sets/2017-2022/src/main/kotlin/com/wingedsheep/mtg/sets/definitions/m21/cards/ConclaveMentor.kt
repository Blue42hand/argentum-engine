package com.wingedsheep.mtg.sets.definitions.m21.cards

import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ModifyCounterPlacement

/** Conclave Mentor — Core Set 2021 #216. */
val ConclaveMentor = card("Conclave Mentor") {
    manaCost = "{G}{W}"
    colorIdentity = "GW"
    typeLine = "Creature — Centaur Cleric"
    power = 2
    toughness = 2
    oracleText = "If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on that creature instead.\n" +
        "When this creature dies, you gain life equal to its power."

    replacementEffect(ModifyCounterPlacement(modifier = 1))

    triggeredAbility {
        trigger = Triggers.Dies
        effect = Effects.GainLife(DynamicAmounts.sourcePower())
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "216"
        artist = "Raoul Vitale"
        flavorText = "\"I have faith in your potential.\""
        imageUri = "https://cards.scryfall.io/normal/front/a/3/a328a93a-e720-46d5-a190-cc65d1c90cea.jpg?1783930665"
        ruling("2020-06-23", "If a creature you control would enter the battlefield with a number of +1/+1 counters on it, it enters with that many plus one instead.")
        ruling("2020-06-23", "Conclave Mentor's first ability doesn't apply to itself if it's somehow entering the battlefield with a +1/+1 counter on it.")
        ruling("2020-06-23", "If you control two Conclave Mentors, the number of +1/+1 counters put on a creature is two plus the original number. Three Conclave Mentors add three, and so on.")
        ruling("2020-06-23", "If two or more effects attempt to modify how many counters would be put onto a creature you control, you choose the order to apply those effects, no matter who controls the sources of those effects.")
        ruling("2020-06-23", "Use Conclave Mentor's power as it last existed on the battlefield to determine how much life you gain.")
    }
}
