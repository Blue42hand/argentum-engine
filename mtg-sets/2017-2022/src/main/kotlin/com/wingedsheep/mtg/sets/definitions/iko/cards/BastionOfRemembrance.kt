package com.wingedsheep.mtg.sets.definitions.iko.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Bastion of Remembrance
 * {2}{B}
 * Enchantment
 *
 * When this enchantment enters, create a 1/1 white Human Soldier creature token.
 * Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.
 *
 * The life gain is a printed fixed 1, not “life equal to the life lost this way,” so the drain is
 * deliberately two effects rather than [Effects.DrainLife].
 */
val BastionOfRemembrance = card("Bastion of Remembrance") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Enchantment"
    oracleText = "When this enchantment enters, create a 1/1 white Human Soldier creature token.\n" +
        "Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            colors = setOf(Color.WHITE),
            creatureTypes = setOf("Human", "Soldier"),
            imageUri = "https://cards.scryfall.io/normal/front/9/0/90de6d1e-e654-4f4a-8014-061ce69e540f.jpg?1783930948",
        )
    }

    triggeredAbility {
        trigger = Triggers.YourCreatureDies
        effect = Effects.LoseLife(1, EffectTarget.PlayerRef(Player.EachOpponent)) then
            Effects.GainLife(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "73"
        artist = "Volkan Baǵa"
        flavorText = "Lives taken are lives not easily forgotten."
        imageUri = "https://cards.scryfall.io/normal/front/2/d/2dd354dd-939e-4b1a-8ed6-fe89a7fd64bf.jpg?1783931067"
        ruling("2020-04-17", "If one or more creatures you control die at the same time that Bastion of Remembrance leaves the battlefield, its last ability triggers for each of those creatures.")
    }
}
