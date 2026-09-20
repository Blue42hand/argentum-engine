package com.wingedsheep.mtg.sets.definitions.afr.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.Aggregation
import com.wingedsheep.sdk.scripting.values.CardNumericProperty
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Battle Cry Goblin — Adventures in the Forgotten Realms #132. */
val BattleCryGoblin = card("Battle Cry Goblin") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Goblin"
    oracleText = "{1}{R}: Goblins you control get +1/+0 and gain haste until end of turn.\n" +
        "Pack tactics — Whenever this creature attacks, if you attacked with creatures with total " +
        "power 6 or greater this combat, create a 1/1 red Goblin creature token that's tapped and attacking."
    power = 2
    toughness = 2

    activatedAbility {
        cost = Costs.Mana("{1}{R}")
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.withSubtype(Subtype("Goblin")).youControl()),
            Effects.Composite(
                Effects.ModifyStats(1, 0, EffectTarget.Self),
                Effects.GrantKeyword(Keyword.HASTE, EffectTarget.Self),
            ),
        )
    }

    triggeredAbility {
        trigger = Triggers.Attacks
        interveningIf = Compare(
            left = DynamicAmount.AggregateBattlefield(
                player = Player.You,
                filter = GameObjectFilter.Creature.attacking(),
                aggregation = Aggregation.SUM,
                property = CardNumericProperty.POWER,
            ),
            operator = ComparisonOperator.GTE,
            right = DynamicAmount.Fixed(6),
        )
        effect = CreateTokenEffect(
            power = 1,
            toughness = 1,
            colors = setOf(Color.RED),
            creatureTypes = setOf("Goblin"),
            tapped = true,
            attacking = true,
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "132"
        artist = "April Prime"
        imageUri = "https://cards.scryfall.io/normal/front/9/7/9766a427-2bb3-4028-a502-d1194cdc93aa.jpg?1783926484"
        ruling("2021-07-23", "Battle Cry Goblin's activated ability will affect only Goblins you control as it resolves. If you activate it before attacking and then create a Goblin with its triggered ability during combat, the Goblin you create will not get +1/+0.")
    }
}
