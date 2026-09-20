package com.wingedsheep.mtg.sets.definitions.afr.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** You See a Pair of Goblins — Adventures in the Forgotten Realms #170. */
val YouSeeAPairOfGoblins = card("You See a Pair of Goblins") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Choose one —\n" +
        "• Charge Them — Creatures you control get +2/+0 until end of turn.\n" +
        "• Befriend Them — Create two 1/1 red Goblin creature tokens."

    spell {
        effect = ModalEffect.chooseOne(
            Mode.noTarget(
                Effects.ForEachInGroup(
                    GroupFilter(GameObjectFilter.Creature.youControl()),
                    Effects.ModifyStats(2, 0, EffectTarget.Self),
                ),
                "Creatures you control get +2/+0 until end of turn",
            ),
            Mode.noTarget(
                Effects.CreateToken(
                    power = 1,
                    toughness = 1,
                    colors = setOf(Color.RED),
                    creatureTypes = setOf("Goblin"),
                    count = 2,
                ),
                "Create two 1/1 red Goblin creature tokens",
            ),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "170"
        artist = "Aaron Miller"
        flavorText = "\"I have the shot. Shall I take it?\"\n—Varis, Silverymoon ranger"
        imageUri = "https://cards.scryfall.io/normal/front/b/6/b69e7200-96ea-4455-83cc-0a497d56efe5.jpg?1783926468"
    }
}
