package com.wingedsheep.mtg.sets.definitions.mmq.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantTriggeredAbility
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Coastal Piracy
 * {2}{U}{U}
 * Enchantment
 *
 * Whenever a creature you control deals combat damage to an opponent, you may draw a card.
 *
 * Each creature receives its own self-bound combat-damage trigger, so multiple creatures connecting
 * generate separate optional draws rather than one batched trigger.
 */
val CoastalPiracy = card("Coastal Piracy") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Enchantment"
    oracleText = "Whenever a creature you control deals combat damage to an opponent, you may draw a card."

    staticAbility {
        ability = GrantTriggeredAbility(
            ability = TriggeredAbility.create(
                trigger = Triggers.DealsCombatDamageToPlayer.event,
                binding = Triggers.DealsCombatDamageToPlayer.binding,
                effect = MayEffect(Effects.DrawCards(1)),
            ),
            filter = GroupFilter(GameObjectFilter.Creature.youControl()),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "68"
        artist = "Matthew D. Wilson"
        flavorText = "\"I don't like to think of myself as a pirate. I'm more like a stimulator of the local economy.\""
        imageUri = "https://cards.scryfall.io/normal/front/1/7/179d1f76-6f4c-4a77-815a-aae7a933c9ad.jpg?1783945969"
        ruling("2004-10-04", "You draw one card per creature, not one per point of damage.")
    }
}
