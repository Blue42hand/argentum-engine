package com.wingedsheep.mtg.sets.definitions.khm.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget

/**
 * Starnheim Courser
 * {2}{W}
 * Creature — Pegasus
 * 2/2
 * Flying
 * Artifact and enchantment spells you cast cost {1} less to cast.
 */
val StarnheimCourser = card("Starnheim Courser") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Pegasus"
    oracleText = "Flying\nArtifact and enchantment spells you cast cost {1} less to cast."
    power = 2
    toughness = 2

    keywords(Keyword.FLYING)

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.ArtifactOrEnchantment),
            modification = CostModification.ReduceGeneric(1),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "32"
        artist = "Andrew Mar"
        flavorText = "Every worthy warrior who arrives in Starnheim is gifted a mount befitting their heroism, a mighty pegasus raised and trained by the valkyries."
        imageUri = "https://cards.scryfall.io/normal/front/5/c/5c5373dd-4aec-4e93-97fb-a639aa096764.jpg?1783928276"

        ruling("2021-02-05", "To determine the total cost of a spell, start with the mana cost or alternative cost you're paying, add any cost increases, then apply any cost reductions (such as that of Starnheim Courser). The mana value of the spell is determined only by its mana cost, no matter what the total cost to cast the spell was.")
        ruling("2021-02-05", "The cost reduction applies only to generic mana in the costs of artifact and enchantment spells you cast. It can't reduce requirements of specific colors of mana.")
    }
}
