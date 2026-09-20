package com.wingedsheep.mtg.sets.definitions.war.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Krenko, Tin Street Kingpin — War of the Spark #137. */
val KrenkoTinStreetKingpin = card("Krenko, Tin Street Kingpin") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Goblin"
    power = 1
    toughness = 2
    oracleText = "Whenever Krenko attacks, put a +1/+1 counter on it, then create a number of " +
        "1/1 red Goblin creature tokens equal to Krenko's power."

    triggeredAbility {
        trigger = Triggers.Attacks
        effect = Effects.Composite(
            Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self),
            CreateTokenEffect(
                count = DynamicAmounts.sourcePower(),
                power = 1,
                toughness = 1,
                colors = setOf(Color.RED),
                creatureTypes = setOf("Goblin"),
            ),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "137"
        artist = "Mark Behm"
        flavorText = "\"After the people flee, but before the enemy arrives—that's grabbin' time.\""
        imageUri = "https://cards.scryfall.io/normal/front/3/7/37ed04d3-cfa1-4778-aea6-b4c2c29e6e0a.jpg?1783933423"
        ruling(
            "2019-05-03",
            "If Krenko leaves the battlefield after its ability has triggered but before it resolves, " +
                "you don't put a +1/+1 counter on it, but you do use its power as it last existed " +
                "before it left the battlefield to determine how many Goblin tokens to create.",
        )
        ruling(
            "2019-05-03",
            "The tokens created by Krenko's triggered ability aren't attacking. Because all attackers " +
                "are chosen at once, a token created this way can't attack, even if it gains haste.",
        )
    }
}
