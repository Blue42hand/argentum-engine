package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Etchings of the Chosen
 * {1}{W}{B}
 * Enchantment
 */
val EtchingsOfTheChosen = card("Etchings of the Chosen") {
    manaCost = "{1}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Enchantment"
    oracleText = "As this enchantment enters, choose a creature type.\n" +
        "Creatures you control of the chosen type get +1/+1.\n" +
        "{1}, Sacrifice a creature of the chosen type: Target creature you control gains " +
        "indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)"

    replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE))

    staticAbility {
        ability = ModifyStats(
            powerBonus = 1,
            toughnessBonus = 1,
            filter = GroupFilter(
                GameObjectFilter.Creature.youControl(),
                chosenSubtypeKey = "chosenCreatureType",
            ),
        )
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.SacrificeChosenCreatureType)
        val creature = target("target", Targets.CreatureYouControl)
        effect = Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, creature)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "198"
        artist = "Chuck Lukacs"
        imageUri = "https://cards.scryfall.io/normal/front/7/0/7036ab83-94a3-4634-9f0a-dde7af3be450.jpg?1783933086"
        ruling("2019-06-14", "You must choose an existing creature type, such as Sliver or Warrior. Card types such as artifact, and supertypes such as legendary or snow, can't be chosen.")
        ruling("2019-06-14", "The last ability of Etchings of the Chosen can target any creature you control, not just one of the chosen type.")
    }
}
