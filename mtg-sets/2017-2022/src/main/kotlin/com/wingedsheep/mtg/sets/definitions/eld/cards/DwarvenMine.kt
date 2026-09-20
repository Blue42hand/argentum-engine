package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Dwarven Mine
 * Land — Mountain
 * ({T}: Add {R}.)
 * This land enters tapped unless you control three or more other Mountains.
 * When this land enters untapped, create a 1/1 red Dwarf creature token.
 */
val DwarvenMine = card("Dwarven Mine") {
    colorIdentity = "R"
    typeLine = "Land — Mountain"
    oracleText = "({T}: Add {R}.)\n" +
        "This land enters tapped unless you control three or more other Mountains.\n" +
        "When this land enters untapped, create a 1/1 red Dwarf creature token."

    // The Mountain subtype supplies the intrinsic red mana ability.
    replacementEffect(
        EntersTapped(
            unlessCondition = Compare(
                DynamicAmount.AggregateBattlefield(
                    Player.You,
                    GameObjectFilter.Land.withSubtype("Mountain"),
                    excludeSelf = true,
                ),
                ComparisonOperator.GTE,
                DynamicAmount.Fixed(3),
            )
        )
    )

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        interveningIf = Conditions.SourceIsUntapped
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            colors = setOf(Color.RED),
            creatureTypes = setOf("Dwarf"),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "243"
        artist = "Alexander Forssberg"
        imageUri = "https://cards.scryfall.io/normal/front/5/c/5c83074d-0c9b-4b58-94ca-d75240485579.jpg?1783932578"
        ruling(
            "2019-10-04",
            "As these lands are entering the battlefield, they check for lands that are already on the battlefield. They won't see lands that are entering the battlefield at the same time (due to Scapeshift, for example).",
        )
        ruling(
            "2019-10-04",
            "If another effect puts these lands onto the battlefield tapped, they enter tapped, even if you control enough lands with the appropriate basic land type.",
        )
    }
}
