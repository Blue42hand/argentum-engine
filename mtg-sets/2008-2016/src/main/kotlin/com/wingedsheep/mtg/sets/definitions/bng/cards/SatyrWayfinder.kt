package com.wingedsheep.mtg.sets.definitions.bng.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Satyr Wayfinder — Born of the Gods #136. */
val SatyrWayfinder = card("Satyr Wayfinder") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Satyr"
    power = 1
    toughness = 1
    oracleText = "When this creature enters, reveal the top four cards of your library. You may " +
        "put a land card from among them into your hand. Put the rest into your graveyard."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Pipeline {
            val revealed = gather(
                CardSource.TopOfLibrary(DynamicAmount.Fixed(4)),
                revealed = true,
                name = "revealed",
            )
            val (land, rest) = chooseUpToSplit(
                1,
                from = revealed,
                filter = GameObjectFilter.Land,
                prompt = "You may put a land card into your hand",
                selectedLabel = "Put in hand",
                remainderLabel = "Put into graveyard",
                showAllCards = true,
                name = "land",
                remainderName = "rest",
            )
            toHand(land)
            toGraveyard(rest)
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "136"
        artist = "Steve Prescott"
        flavorText = "The first satyr to wake after a revel must search for the site of the next one."
        imageUri = "https://cards.scryfall.io/normal/front/1/3/13c5a1ce-932a-4b3d-8b86-ed920e646afc.jpg?1783939529"
    }
}
