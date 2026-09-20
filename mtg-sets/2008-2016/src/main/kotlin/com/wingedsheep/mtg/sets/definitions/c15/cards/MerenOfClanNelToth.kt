package com.wingedsheep.mtg.sets.definitions.c15.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Meren of Clan Nel Toth — Commander 2015 #49.
 *
 * Experience is a player-scoped counter. The death trigger therefore targets the ability's
 * controller, while the end-step branch compares the targeted card's mana value with that player's
 * live experience total at resolution. The target is deliberately unrestricted by mana value when
 * the trigger goes on the stack: every creature card in the controller's graveyard is legal, and a
 * card above the threshold must go to hand rather than fizzle.
 */
val MerenOfClanNelToth = card("Meren of Clan Nel Toth") {
    manaCost = "{2}{B}{G}"
    colorIdentity = "BG"
    typeLine = "Legendary Creature — Human Shaman"
    power = 3
    toughness = 4
    oracleText = "Whenever another creature you control dies, you get an experience counter.\n" +
        "At the beginning of your end step, choose target creature card in your graveyard. " +
        "If that card's mana value is less than or equal to the number of experience counters " +
        "you have, return it to the battlefield. Otherwise, put it into your hand."

    triggeredAbility {
        trigger = Triggers.leavesBattlefield(
            filter = GameObjectFilter.Creature.youControl(),
            to = Zone.GRAVEYARD,
            binding = TriggerBinding.OTHER,
        )
        effect = Effects.AddCounters(Counters.EXPERIENCE, 1, EffectTarget.Controller)
        description = "Whenever another creature you control dies, you get an experience counter."
    }

    triggeredAbility {
        trigger = Triggers.YourEndStep
        val creatureCard = target(
            "target creature card in your graveyard",
            Targets.CreatureCardInYourGraveyard,
        )
        effect = ConditionalEffect(
            condition = Conditions.CompareAmounts(
                DynamicAmounts.targetManaValue(),
                ComparisonOperator.LTE,
                DynamicAmounts.playerCounterCount(Counters.EXPERIENCE),
            ),
            effect = Effects.PutOntoBattlefield(creatureCard),
            elseEffect = Effects.ReturnToHand(creatureCard),
        )
        description = "At the beginning of your end step, choose target creature card in your " +
            "graveyard. If that card's mana value is less than or equal to the number of " +
            "experience counters you have, return it to the battlefield. Otherwise, put it into " +
            "your hand."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "49"
        artist = "Mark Winters"
        imageUri = "https://cards.scryfall.io/normal/front/f/4/f43bf91d-bc08-417f-a549-64919c24052a.jpg?1783938107"

        ruling("2015-11-04", "If Meren of Clan Nel Toth leaves the battlefield at the same time as other creatures you control die, its first ability will trigger for each of those creatures.")
        ruling("2015-11-04", "You can’t choose to put the creature card into your hand if its mana value is less than or equal to the number of experience counters you have as the ability resolves.")
        ruling("2015-11-04", "If a creature card in your graveyard has {X} in its mana cost, X is 0.")
        ruling("2015-11-04", "All experience counters are identical, no matter how you got them. For example, the last ability will count experience counters that you got from the first ability, from another ability, from proliferating, and so on.")
    }
}
