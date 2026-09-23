package com.wingedsheep.mtg.sets.definitions.fut.cards

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget

/**
 * Cloud Key
 * {3}
 * Artifact
 *
 * As this artifact enters, choose artifact, creature, enchantment, instant, or sorcery.
 * Spells you cast of the chosen type cost {1} less to cast.
 *
 * The entry replacement stores the restricted card-type choice in ChoiceSlot.CARD_TYPE.
 * The static cost modifier then reads that durable choice through the chosen-card-type filter
 * and reduces only generic mana on matching spells cast by Cloud Key's controller.
 */
val CloudKey = card("Cloud Key") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "As this artifact enters, choose artifact, creature, enchantment, instant, or sorcery.\n" +
        "Spells you cast of the chosen type cost {1} less to cast."

    replacementEffect(
        EntersWithChoice(
            choiceType = ChoiceType.CARD_TYPE,
            allowedCardTypes = listOf(
                CardType.ARTIFACT,
                CardType.CREATURE,
                CardType.ENCHANTMENT,
                CardType.INSTANT,
                CardType.SORCERY,
            ),
        )
    )

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Any.ofChosenCardTypeComponent()),
            modification = CostModification.ReduceGeneric(1),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "160"
        artist = "Trevor Hairsine"
        imageUri = "https://cards.scryfall.io/normal/front/b/8/b893ab56-44a6-4b3c-bb3e-6deec298cbce.jpg?1783943092"
        ruling(
            "2021-03-19",
            "The cost reduction applies only to generic mana in the cost of spells of the chosen type you cast."
        )
        ruling(
            "2021-03-19",
            "To determine the total cost of a spell, start with the mana cost or alternative cost you're paying " +
                "(such as a flashback cost), add any cost increases (such as kicker costs), then apply any cost " +
                "reductions (such as that of Cloud Key's ability). The mana value of the spell is determined by " +
                "only its mana cost, no matter what the total cost to cast that spell was."
        )
    }
}
