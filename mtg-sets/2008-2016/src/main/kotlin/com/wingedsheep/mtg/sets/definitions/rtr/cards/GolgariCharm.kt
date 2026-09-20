package com.wingedsheep.mtg.sets.definitions.rtr.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.effects.RegenerateEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Golgari Charm — Return to Ravnica #164. */
val GolgariCharm = card("Golgari Charm") {
    manaCost = "{B}{G}"
    colorIdentity = "BG"
    typeLine = "Instant"
    oracleText = "Choose one —\n• All creatures get -1/-1 until end of turn.\n" +
        "• Destroy target enchantment.\n• Regenerate each creature you control."

    spell {
        modal {
            mode("All creatures get -1/-1 until end of turn") {
                effect = Effects.ForEachInGroup(
                    GroupFilter.AllCreatures,
                    ModifyStatsEffect(-1, -1, EffectTarget.Self),
                )
            }
            mode("Destroy target enchantment") {
                val enchantment = target(
                    "target enchantment",
                    TargetPermanent(filter = TargetFilter(GameObjectFilter.Enchantment)),
                )
                effect = Effects.Destroy(enchantment)
            }
            mode("Regenerate each creature you control") {
                effect = Effects.ForEachInGroup(
                    GroupFilter.AllCreaturesYouControl,
                    RegenerateEffect(EffectTarget.Self),
                )
            }
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "164"
        artist = "Zoltan Boros"
        flavorText = "\"Let the rest of Ravnica sneer. One way or another, they all end up in " +
            "the undercity.\"\n—Jarad"
        imageUri = "https://cards.scryfall.io/normal/front/4/8/48fce388-eefc-4234-8dd9-1260c1ba97eb.jpg?1783940339"
    }
}
