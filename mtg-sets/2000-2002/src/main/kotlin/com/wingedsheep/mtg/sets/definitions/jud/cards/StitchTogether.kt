package com.wingedsheep.mtg.sets.definitions.jud.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * Stitch Together checks threshold when resolution starts, before returning the targeted card.
 * The card being returned counts toward seven; the resolving spell is still on the stack.
 */
val StitchTogether = card("Stitch Together") {
    manaCost = "{B}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Return target creature card from your graveyard to your hand.\nThreshold — Return that card from your graveyard to the battlefield instead if there are seven or more cards in your graveyard."

    spell {
        val creature = target(TargetFilter.CreatureInYourGraveyard)
        effect = Effects.If(
            condition = Conditions.CardsInGraveyardAtLeast(7),
            then = Effects.PutOntoBattlefieldFromGraveyard(creature),
            otherwise = Effects.ReturnToHand(creature),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "72"
        artist = "Arnie Swekel"
        imageUri = "https://cards.scryfall.io/normal/front/b/4/b43c6172-2400-4038-8b8b-c62f2fbfce39.jpg?1783945121"
    }
}
