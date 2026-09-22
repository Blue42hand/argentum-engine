package com.wingedsheep.mtg.sets.definitions.rix.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern.ZoneChangeEvent
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggerSpec

/**
 * Pitiless Plunderer
 * {3}{B}
 * Creature — Human Pirate
 * 1/4
 *
 * Whenever another creature you control dies, create a Treasure token.
 *
 * This is the per-creature death shape, not the modern "one or more" batch shape. OTHER excludes
 * Pitiless Plunderer itself while preserving last-known control, so if it dies simultaneously with
 * other creatures you control the ability triggers once for each of those other creatures.
 */
val PitilessPlunderer = card("Pitiless Plunderer") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Human Pirate"
    power = 1
    toughness = 4
    oracleText = "Whenever another creature you control dies, create a Treasure token. " +
        "(It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")"

    triggeredAbility {
        trigger = TriggerSpec(
            event = ZoneChangeEvent(
                filter = GameObjectFilter.Creature.youControl(),
                from = Zone.BATTLEFIELD,
                to = Zone.GRAVEYARD
            ),
            binding = TriggerBinding.OTHER
        )
        effect = Effects.CreateTreasure(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "81"
        artist = "David Palumbo"
        flavorText = "\"Shame to let good gold go to the grave.\""
        imageUri = "https://cards.scryfall.io/normal/front/c/8/c87ebffe-5907-427b-9f9b-7c36a12b4a03.jpg?1783935307"
        ruling(
            "2018-01-19",
            "If Pitiless Plunderer dies at the same time as one or more other creatures you control, " +
                "its ability will still trigger for each of those other creatures."
        )
    }
}
