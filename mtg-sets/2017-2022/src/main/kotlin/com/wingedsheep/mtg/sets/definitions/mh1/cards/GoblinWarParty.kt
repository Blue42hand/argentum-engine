package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Goblin War Party — Modern Horizons #131.
 *
 * Entwine uses the existing cast-time modal extra-mode mana cost, not a
 * resolution-time choice. Printed mode order is preserved by ModalEffectExecutor.
 */
val GoblinWarParty = card("Goblin War Party") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Choose one —\n" +
        "• Create three 1/1 red Goblin creature tokens.\n" +
        "• Creatures you control get +1/+1 and gain haste until end of turn.\n" +
        "Entwine {2}{R} (Choose both if you pay the entwine cost.)"

    spell {
        modal(
            chooseCount = 2,
            minChooseCount = 1,
            additionalManaCostPerExtraMode = "{2}{R}",
        ) {
            mode(
                "Create three 1/1 red Goblin creature tokens",
                Effects.CreateToken(
                    count = 3,
                    power = 1,
                    toughness = 1,
                    colors = setOf(Color.RED),
                    creatureTypes = setOf("Goblin"),
                    imageUri = "https://cards.scryfall.io/normal/front/c/5/c5522b39-802a-4508-a671-2f73b0633032.jpg?1783933225"
                )
            )
            mode(
                "Creatures you control get +1/+1 and gain haste until end of turn",
                Effects.ForEachInGroup(
                    filter = GroupFilter(GameObjectFilter.Creature.youControl()),
                    effect = Effects.Pipeline {
                        run(Effects.ModifyStats(1, 1, EffectTarget.IterationEntity))
                        run(Effects.GrantKeyword(Keyword.HASTE, EffectTarget.IterationEntity))
                    }
                )
            )
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "131"
        artist = "Deruchenko Alexander"
        imageUri = "https://cards.scryfall.io/normal/front/2/b/2b4155d1-0a87-4d2c-a1a0-06b2553eaae4.jpg?1783933111"
        ruling(
            "2019-06-14",
            "Goblin War Party’s second mode affects only creatures you control at the time the spell resolves. Creatures you begin to control later in the turn won’t get +1/+1 or gain haste."
        )
        ruling(
            "2019-06-14",
            "If Goblin War Party is entwined, its modes are performed in order. The Goblin tokens you create get +1/+1 and gain haste."
        )
    }
}
