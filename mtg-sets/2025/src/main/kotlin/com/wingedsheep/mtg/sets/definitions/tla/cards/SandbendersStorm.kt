package com.wingedsheep.mtg.sets.definitions.tla.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.mode
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Sandbenders' Storm
 * {3}{W}
 * Instant
 * Choose one —
 * • Destroy target creature with power 4 or greater.
 * • Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's
 *   still a land. Put three +1/+1 counters on it. When it dies or is exiled, return
 *   it to the battlefield tapped.)
 *
 * A true "Choose one —" modal spell ([ModalEffect.chooseOne], countsAsModalSpell = true).
 * Mode 1 destroys a target creature constrained to power 4 or greater via
 * [TargetFilter.powerAtLeast]. Mode 2 is the Earthbend keyword action composed by
 * [Effects.Earthbend] (animate, grant haste, add counters, self-triggers) on a target
 * land you control.
 */
val SandbendersStorm = card("Sandbenders' Storm") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Choose one —\n" +
        "• Destroy target creature with power 4 or greater.\n" +
        "• Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's still a land. " +
        "Put three +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)"

    spell {
        effect = ModalEffect.chooseOne(
            mode("Destroy target creature with power 4 or greater") {
                val creature = target("target creature", TargetObject(
                    filter = TargetFilter.Creature.powerAtLeast(4),
                    id = "target creature with power 4 or greater",
                ))
                effect = Effects.Destroy(creature)
            },
            mode("Earthbend 3") {
                val land = target("target land", TargetObject(
                    filter = TargetFilter.Land.youControl(),
                    id = "target land you control",
                ))
                effect = Effects.Earthbend(3, land)
            },
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "34"
        artist = "Robin Har"
        imageUri = "https://cards.scryfall.io/normal/front/b/3/b3bf4a25-4329-4318-870a-2b06aa620dc4.jpg?1774855638"
    }
}
