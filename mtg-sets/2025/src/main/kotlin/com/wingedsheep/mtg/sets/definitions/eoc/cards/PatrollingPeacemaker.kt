package com.wingedsheep.mtg.sets.definitions.eoc.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithCounters

/** Patrolling Peacemaker (EOC #5). */
val PatrollingPeacemaker = card("Patrolling Peacemaker") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Artifact Creature — Robot Soldier"
    power = 0
    toughness = 0
    oracleText = "This creature enters with two +1/+1 counters on it.\n" +
        "Whenever an opponent commits a crime, proliferate. " +
        "(They commit a crime if they target an opponent, anything an opponent controls, and/or cards in an opponent's graveyard. " +
        "To proliferate, you choose any number of permanents and/or players, then give each another counter of each kind already there.)"

    replacementEffect(EntersWithCounters(
        counterType = CounterType.PLUS_ONE_PLUS_ONE,
        count = 2,
        selfOnly = true
    ))

    triggeredAbility {
        trigger = Triggers.anOpponent.commitsCrime()
        effect = Effects.Proliferate()
        description = "Whenever an opponent commits a crime, proliferate."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "5"
        artist = "Michal Ivan"
        imageUri = "https://cards.scryfall.io/normal/front/1/e/1e06d6be-c2a3-4c73-a916-89674c0ddfed.jpg?1783906067"

        ruling("2025-07-25", "A player commits a crime as they cast a spell, activate an ability, or put a triggered ability on the stack that targets at least one opponent, at least one permanent, spell, or ability an opponent controls, and/or at least one card in an opponent’s graveyard.")
        ruling("2025-07-25", "The spell or ability that constituted a crime doesn’t have to have resolved yet or at all. As soon as you’re finished casting the spell, activating the ability, or putting the triggered ability on the stack, you’ve committed a crime.")
        ruling("2025-07-25", "Changing the target or targets of a spell or ability won’t affect whether the controller of that spell or ability has committed a crime. Only the initial targets chosen for that spell or ability are used to determine whether its controller committed a crime.")
        ruling("2025-07-25", "A player can commit only one crime per spell or ability they control. Targeting multiple opponents, permanents, spells, abilities, and/or cards with the same spell or ability doesn’t constitute committing multiple crimes.")
    }
}
