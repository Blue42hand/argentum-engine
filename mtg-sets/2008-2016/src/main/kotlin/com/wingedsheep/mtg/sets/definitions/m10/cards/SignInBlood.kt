package com.wingedsheep.mtg.sets.definitions.m10.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.TargetPlayer

/** Sign in Blood — Magic 2010 #112. */
val SignInBlood = card("Sign in Blood") {
    manaCost = "{B}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Target player draws two cards and loses 2 life."

    spell {
        val player = target("target player", TargetPlayer())
        effect = Effects.Composite(
            Effects.DrawCards(2, player),
            Effects.LoseLife(2, player),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "112"
        artist = "Howard Lyon"
        flavorText = "\"You know I accept only one currency here, and yet you have sought me out. Why now do you hesitate?\"\n—Xathrid demon"
        imageUri = "https://cards.scryfall.io/normal/front/1/9/1975ed97-acb8-4bb6-804a-e5da725d876e.jpg?1783942379"
    }
}
