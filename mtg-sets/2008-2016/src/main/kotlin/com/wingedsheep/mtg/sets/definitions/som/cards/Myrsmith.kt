package com.wingedsheep.mtg.sets.definitions.som.cards

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/** Myrsmith — Scars of Mirrodin #16. */
val Myrsmith = card("Myrsmith") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Artificer"
    power = 2
    toughness = 1
    oracleText = "Whenever you cast an artifact spell, you may pay {1}. If you do, create a 1/1 colorless Myr artifact creature token."

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Artifact)
        effect = Effects.MayPay(
            cost = ManaCost.parse("{1}"),
            then = Effects.CreateToken(
                power = 1,
                toughness = 1,
                creatureTypes = setOf("Myr"),
                artifactToken = true,
                imageUri = "https://cards.scryfall.io/normal/front/1/8/182308b3-86e0-46d8-9104-95576a3d3921.jpg?1783941681"
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "16"
        artist = "Eric Deschamps"
        flavorText = "The Auriok see the artificer as a conduit, beckoning new creations into the world."
        imageUri = "https://cards.scryfall.io/normal/front/1/3/13429b63-085c-4c78-9ce3-247db5841b9d.jpg?1783941743"
        ruling("2020-08-07", "An ability that triggers when a player casts a spell resolves before the spell that caused it to trigger. It resolves even if that spell is countered.")
        ruling("2020-08-07", "While resolving Myrsmith's ability, you can't pay more than {1} to get more than one Myr.")
    }
}
