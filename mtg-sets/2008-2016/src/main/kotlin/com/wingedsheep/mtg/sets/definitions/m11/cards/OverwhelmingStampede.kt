package com.wingedsheep.mtg.sets.definitions.m11.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Overwhelming Stampede
 * {3}{G}{G}
 * Sorcery
 * Until end of turn, creatures you control gain trample and get +X/+X, where X is the greatest
 * power among creatures you control.
 */
val OverwhelmingStampede = card("Overwhelming Stampede") {
    manaCost = "{3}{G}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Until end of turn, creatures you control gain trample and get +X/+X, where X is " +
        "the greatest power among creatures you control."

    spell {
        val greatestPower = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Creature).maxPower()
        effect = Effects.Composite(
            Effects.StoreNumber("stampede_x", greatestPower),
            Patterns.Group.grantKeywordToAll(Keyword.TRAMPLE, GroupFilter.AllCreaturesYouControl),
            Patterns.Group.modifyStatsForAll(
                power = DynamicAmount.VariableReference("stampede_x"),
                toughness = DynamicAmount.VariableReference("stampede_x"),
                filter = GroupFilter.AllCreaturesYouControl,
            ),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "189"
        artist = "Steven Belledin"
        imageUri = "https://cards.scryfall.io/normal/front/1/d/1d5a46d0-09fe-454c-a920-0343f846b832.jpg?1783941794"
        ruling(
            "2010-08-15",
            "You check the power of your creatures as Overwhelming Stampede resolves. For example, if " +
                "you control a 2/1 creature, a 2/2 creature, a 2/4 creature, a 4/1 creature, a 5/5 " +
                "creature, and a 5/6 creature at that time, each of your creatures gets +5/+5 and gains " +
                "trample until end of turn."
        )
    }
}
