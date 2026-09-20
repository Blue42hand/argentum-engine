package com.wingedsheep.mtg.sets.definitions.c19.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction

/**
 * Idol of Oblivion — Commander 2019 #55
 * {2} · Artifact
 *
 * {T}: Draw a card. Activate only if you created a token this turn.
 * {8}, {T}, Sacrifice this artifact: Create a 10/10 colorless Eldrazi creature token.
 *
 * The first ability deliberately reads the player's turn-scoped token-creation history rather than
 * current battlefield state. That preserves the 2019-08-23 ruling: the qualifying token may have
 * been created before Idol entered and may already have left the battlefield.
 */
val IdolOfOblivion = card("Idol of Oblivion") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{T}: Draw a card. Activate only if you created a token this turn.\n" +
        "{8}, {T}, Sacrifice this artifact: Create a 10/10 colorless Eldrazi creature token."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.DrawCards(1)
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(Conditions.YouCreatedTokensThisTurn)
        )
        description = "{T}: Draw a card. Activate only if you created a token this turn."
    }

    activatedAbility {
        cost = Costs.Composite(
            Costs.Mana("{8}"),
            Costs.Tap,
            Costs.SacrificeSelf,
        )
        effect = Effects.CreateToken(
            power = 10,
            toughness = 10,
            creatureTypes = setOf("Eldrazi"),
            imageUri = "https://cards.scryfall.io/normal/front/c/9/c9854bcf-8729-45b9-9ea3-c43898e4fe5f.jpg?1572370795",
        )
        description = "{8}, {T}, Sacrifice this artifact: Create a 10/10 colorless Eldrazi creature token."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "55"
        artist = "Piotr Dura"
        flavorText = "\"Arise, great one, and cleanse the world of our enemies!\""
        imageUri = "https://cards.scryfall.io/normal/front/d/a/daed8456-011e-44e2-b180-6a8a75089257.jpg?1783932793"
        ruling(
            "2019-08-23",
            "You can activate Idol of Oblivion's first ability even if the token you've created was created before Idol of Oblivion entered the battlefield or if the token has left the battlefield."
        )
    }
}
