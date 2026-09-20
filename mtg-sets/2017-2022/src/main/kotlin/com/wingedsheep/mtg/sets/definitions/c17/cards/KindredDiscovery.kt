package com.wingedsheep.mtg.sets.definitions.c17.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggerSpec

/**
 * Kindred Discovery
 * {3}{U}{U}
 * Enchantment
 */
val KindredDiscovery = card("Kindred Discovery") {
    manaCost = "{3}{U}{U}"
    colorIdentity = "U"
    typeLine = "Enchantment"
    oracleText = "As this enchantment enters, choose a creature type.\n" +
        "Whenever a creature you control of the chosen type enters or attacks, draw a card."

    replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE))

    triggeredAbility {
        trigger = TriggerSpec(
            event = EventPattern.AnyOf(
                listOf(
                    EventPattern.ZoneChangeEvent(
                        filter = GameObjectFilter.Creature.youControl().withChosenSubtype(),
                        to = Zone.BATTLEFIELD,
                    ),
                    EventPattern.AttackEvent(
                        filter = GameObjectFilter.Creature.youControl().withChosenSubtype()
                    ),
                )
            ),
            binding = TriggerBinding.ANY,
        )
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "11"
        artist = "Lake Hurwitz"
        flavorText = "Few things are truly \"lost\" at sea."
        imageUri = "https://cards.scryfall.io/normal/front/e/d/ed07b7be-bd3d-4dbc-bc92-b5478d3604c6.jpg?1783935949"
        ruling("2017-08-25", "If you somehow control a Kindred Discovery with no chosen creature type, its last ability can't trigger, even if a creature with no creature types enters the battlefield or attacks.")
        ruling("2022-06-10", "The choice of creature type is made as Kindred Discovery enters the battlefield. Players can't take any actions between the time the choice is made and the time it enters the battlefield. Notably, this means that if it is entering the battlefield at the same time as any creatures of the chosen type, its last ability will trigger.")
        ruling("2022-06-10", "You must choose an existing creature type, such as Human or Warrior. Card types such as artifact and supertypes such as legendary can't be chosen.")
        ruling("2023-09-01", "You can't choose multiple creature types, such as \"Cat Warrior.\" A Cat Warrior is both a Cat and a Warrior. It's affected by anything that affects either type and unaffected by things that affect non-Cat or non-Warrior creatures.")
    }
}
