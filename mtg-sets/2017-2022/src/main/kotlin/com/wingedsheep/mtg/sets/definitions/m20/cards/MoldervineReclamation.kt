package com.wingedsheep.mtg.sets.definitions.m20.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding

/** Moldervine Reclamation — Core Set 2020 #214. */
val MoldervineReclamation = card("Moldervine Reclamation") {
    manaCost = "{3}{B}{G}"
    colorIdentity = "BG"
    typeLine = "Enchantment"
    oracleText = "Whenever a creature you control dies, you gain 1 life and draw a card."

    triggeredAbility {
        trigger = Triggers.leavesBattlefield(
            filter = GameObjectFilter.Creature.youControl(),
            to = Zone.GRAVEYARD,
            binding = TriggerBinding.ANY,
        )
        effect = Effects.GainLife(1) then Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "214"
        artist = "Antonio José Manzanedo"
        flavorText = "\"The heroes of the past nourish our spirits by their example—and nourish " +
            "our crops by their decay.\"\n—Jeddeg, philosopher of graves"
        imageUri = "https://cards.scryfall.io/normal/front/6/1/618d21fe-8e3d-4887-b2e4-b92194ba1902.jpg?1783932949"
        ruling(
            "2019-07-12",
            "If Moldervine Reclamation leaves the battlefield at the same time as one or more " +
                "creatures you control die, its ability triggers for each of those creatures.",
        )
        ruling(
            "2019-07-12",
            "If your life total is brought to 0 or less at the same time that a creature you " +
                "control dies, you lose the game before Moldervine Reclamation's ability can save you.",
        )
    }
}
