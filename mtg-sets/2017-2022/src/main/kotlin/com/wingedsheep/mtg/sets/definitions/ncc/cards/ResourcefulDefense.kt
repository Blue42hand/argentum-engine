package com.wingedsheep.mtg.sets.definitions.ncc.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.targets.TargetOther

/** Resourceful Defense — New Capenna Commander #19. */
val ResourcefulDefense = card("Resourceful Defense") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment"
    oracleText = "Whenever a permanent you control leaves the battlefield, if it had counters on it, " +
        "put those counters on target permanent you control.\n" +
        "{4}{W}: Move any number of counters from target permanent you control onto a second target permanent you control."

    triggeredAbility {
        val destination = target(TargetFilter.PermanentYouControl)
        trigger = Triggers.a(GameObjectFilter.Permanent.youControl()).leaves()
        interveningIf = Conditions.TriggeringEntityHadCounters
        effect = Effects.MoveAllLastKnownCounters(destination)
    }

    activatedAbility {
        cost = Costs.Mana("{4}{W}")
        val source = target(TargetFilter.PermanentYouControl)
        val destination = target(TargetOther(TargetObject(filter = TargetFilter.PermanentYouControl), excludeSource = false))
        effect = Effects.MoveChosenCountersToTarget(source, destination)
        description = "Move any number of counters between two permanents you control"
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "19"
        artist = "Francis Tneh"
        imageUri = "https://cards.scryfall.io/normal/front/9/1/91f4dfc0-7478-4e47-89b4-685b72197ef0.jpg?1783923374"
    }
}
