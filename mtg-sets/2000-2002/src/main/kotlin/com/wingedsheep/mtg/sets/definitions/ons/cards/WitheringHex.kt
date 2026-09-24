package com.wingedsheep.mtg.sets.definitions.ons.cards

import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantDynamicStats
import com.wingedsheep.sdk.scripting.effects.AddCountersEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.dsl.Triggers

/**
 * Withering Hex
 * {B}
 * Enchantment — Aura
 * Enchant creature
 * Whenever a player cycles a card, put a plague counter on Withering Hex.
 * Enchanted creature gets -1/-1 for each plague counter on Withering Hex.
 */
val WitheringHex = card("Withering Hex") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nWhenever a player cycles a card, put a plague counter on Withering Hex.\nEnchanted creature gets -1/-1 for each plague counter on Withering Hex."

    auraTarget = Targets.Creature

    triggeredAbility {
        trigger = Triggers.AnyPlayerCycles
        effect = AddCountersEffect(CounterType.PLAGUE, 1, EffectTarget.Self)
    }

    staticAbility {
        val plagueCounters = DynamicAmount.EntityProperty(
            EffectTarget.Self,
            EntityNumericProperty.CounterCount(CounterType.PLAGUE)
        )
        ability = GrantDynamicStats(
            filter = GroupFilter.attachedCreature(),
            powerBonus = DynamicAmount.Multiply(plagueCounters, -1),
            toughnessBonus = DynamicAmount.Multiply(plagueCounters, -1)
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "181"
        artist = "Greg Hildebrandt & Tim Hildebrandt"
        imageUri = "https://cards.scryfall.io/normal/front/9/c/9ce4be1e-97dd-45ec-89e5-2fb56145c098.jpg?1562932026"
    }
}
