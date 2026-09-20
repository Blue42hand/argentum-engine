package com.wingedsheep.mtg.sets.definitions.c17.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CollectionFilter
import com.wingedsheep.sdk.scripting.effects.FilterCollectionEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Herald's Horn
 * {3}
 * Artifact
 */
val HeraldsHorn = card("Herald's Horn") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "As this artifact enters, choose a creature type.\n" +
        "Creature spells you cast of the chosen type cost {1} less to cast.\n" +
        "At the beginning of your upkeep, look at the top card of your library. If it's a " +
        "creature card of the chosen type, you may reveal it and put it into your hand."

    replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE))

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Creature.withChosenSubtype()),
            modification = CostModification.ReduceGeneric(1),
        )
    }

    triggeredAbility {
        trigger = Triggers.YourUpkeep
        effect = Effects.Composite(
            listOf(
                GatherCardsEffect(
                    source = CardSource.TopOfLibrary(DynamicAmount.Fixed(1)),
                    storeAs = "lookedCard",
                ),
                FilterCollectionEffect(
                    from = "lookedCard",
                    filter = CollectionFilter.MatchesFilter(
                        GameObjectFilter.Creature.withChosenSubtype()
                    ),
                    storeMatching = "matchingCreature",
                    storeNonMatching = "nonmatchingCard",
                ),
                SelectFromCollectionEffect(
                    from = "matchingCreature",
                    selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(1)),
                    storeSelected = "cardToHand",
                    selectedLabel = "Reveal and put into your hand",
                    remainderLabel = "Leave on top of your library",
                ),
                MoveCollectionEffect(
                    from = "cardToHand",
                    destination = CardDestination.ToZone(Zone.HAND),
                    revealed = true,
                    revealToSelf = false,
                ),
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "53"
        artist = "Jason Felix"
        imageUri = "https://cards.scryfall.io/normal/front/0/7/07b06421-778a-4d23-862b-30fc5fa25928.jpg?1783935932"
        ruling("2017-08-25", "The effect of Herald's Horn reduces only generic mana in a spell's cost. If that cost has no generic mana, the cost isn't reduced.")
        ruling("2017-08-25", "You can't choose multiple creature types, such as \"Cat Warrior.\" A Cat Warrior is both a Cat and a Warrior. It's affected by anything that affects either type and unaffected by things that affect non-Cat or non-Warrior creatures.")
        ruling("2017-08-25", "To determine the total cost of a spell, start with the mana cost or alternative cost you're paying, add any cost increases, then apply any cost reductions. The mana value of the spell remains unchanged, no matter what the total cost to cast it was.")
        ruling("2017-08-25", "You must choose an existing creature type, such as Vampire or Cat. Card types such as \"artifact\" can't be chosen.")
        ruling("2017-08-25", "If you don't put the top card of your library into your hand, you put it back on top of your library without revealing it. You'll draw it in that turn's draw step.")
    }
}
