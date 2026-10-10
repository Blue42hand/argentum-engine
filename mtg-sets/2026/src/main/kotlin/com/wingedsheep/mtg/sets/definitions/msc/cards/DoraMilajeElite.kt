package com.wingedsheep.mtg.sets.definitions.msc.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

// "An opponent" compares each opponent individually, rather than adding their lands together.
private val AnOpponentControlsMoreLands = Conditions.CompareAmounts(
    DynamicAmounts.greatestAmongPlayers(
        DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land).count(),
        Player.EachOpponent,
    ),
    ComparisonOperator.GT,
    DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land).count(),
)

val DoraMilajeElite = card("Dora Milaje Elite") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Warrior"
    power = 2
    toughness = 2
    oracleText = "First strike\nWhen this creature enters, if an opponent controls more lands than you, create a tapped Vibranium token. (It's an artifact with indestructible and \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")\nSacrifice this creature: Legendary permanents you control gain indestructible until end of turn."

    keywords(Keyword.FIRST_STRIKE)

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = AnOpponentControlsMoreLands
        effect = Effects.CreateVibranium(
            count = 1,
            tapped = true,
            imageUri = "https://cards.scryfall.io/normal/front/7/f/7f9e9b3a-c515-449a-95da-ee3f5175b74f.jpg?1783902811",
        )
        description = "If an opponent controls more lands than you, create a tapped Vibranium token"
    }

    activatedAbility {
        cost = Costs.SacrificeSelf
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Permanent.youControl().legendary()),
            Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, EffectTarget.IterationEntity, Duration.EndOfTurn),
        )
        description = "Legendary permanents you control gain indestructible until end of turn"
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "12"
        artist = "Mateus Manhanini"
        imageUri = "https://cards.scryfall.io/normal/front/1/d/1d1e00e3-7f3c-4a49-859b-398283c38e61.jpg?1783903300"
    }
}
