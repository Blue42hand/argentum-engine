package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.LoseAllAbilities
import com.wingedsheep.sdk.scripting.SetBasePowerToughnessStatic
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Utter Insignificance — Modern Horizons 3 #78. */
val UtterInsignificance = card("Utter Insignificance") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Enchantment — Aura"
    oracleText = "Flash\nEnchant creature\nEnchanted creature loses all abilities and has base " +
        "power and toughness 1/1.\n{2}{C}: Exile enchanted creature."

    keywords(Keyword.FLASH)
    auraTarget = Targets.Creature

    staticAbility { ability = LoseAllAbilities() }
    staticAbility { ability = SetBasePowerToughnessStatic(1, 1, Filters.EnchantedCreature) }

    activatedAbility {
        cost = Costs.Mana("{2}{C}")
        effect = Effects.Exile(EffectTarget.EnchantedPermanent)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "78"
        artist = "Lius Lasahido"
        flavorText = "\"I used to think my life was meaningless. Now I know for certain.\""
        imageUri = "https://cards.scryfall.io/normal/front/3/0/3050ac06-4c16-4155-a97f-f6bc92709ee4.jpg?1783911286"
        ruling("2024-06-07", "The enchanted creature will retain any abilities it gains after Utter Insignificance becomes attached to it.")
        ruling("2024-06-07", "Utter Insignificance overwrites all previous effects that set the creature's base power and toughness to specific values. Any power- or toughness-setting effects that start to apply after Utter Insignificance becomes attached to the creature will overwrite this effect.")
        ruling("2024-06-07", "Effects that modify a creature's power and/or toughness without setting it will apply to the enchanted creature no matter when they started to take effect. The same is true for counters that change its power and/or toughness.")
    }
}
