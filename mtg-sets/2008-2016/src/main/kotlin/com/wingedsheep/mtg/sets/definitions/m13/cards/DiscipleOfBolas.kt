package com.wingedsheep.mtg.sets.definitions.m13.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val DiscipleOfBolas = card("Disciple of Bolas") {
    manaCost = "{3}{B}"
    typeLine = "Creature — Human Wizard"
    oracleText = "When this creature enters, sacrifice another creature. You gain X life and draw X cards, where X is that creature's power."
    power = 2
    toughness = 1
    colorIdentity = "B"

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.Pipeline {
            val others = gather(CardSource.BattlefieldMatching(GameObjectFilter.Creature, Player.You, excludeSelf = true))
            val chosen = chooseExactly(1, others, useTargetingUI = true, prompt = "Sacrifice another creature", selectedLabel = "Sacrifice")
            ifNotEmpty(chosen) {
                sacrifice(chosen)
                run(Effects.GainLife(DynamicAmounts.sacrificedPower()))
                run(Effects.DrawCards(DynamicAmounts.sacrificedPower()))
            }
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "88"
        artist = "Slawomir Maniak"
        imageUri = "https://cards.scryfall.io/normal/front/c/4/c4dd57f8-27bc-4ad9-a79e-48a68af33b02.jpg?1783940496"
        flavorText = "\"Lord Bolas has shown me how each miserable little life holds untold resources.\""
        ruling("2020-08-07", "If you control at least one other creature when the ability resolves, you must sacrifice one. If you don't control any other creatures at that time, the ability won't do anything.")
        ruling("2020-08-07", "The creature's power as it last existed on the battlefield is the amount of life you gain and the number of cards you draw.")
    }
}
