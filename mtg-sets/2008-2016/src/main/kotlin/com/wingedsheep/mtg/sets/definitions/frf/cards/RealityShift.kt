package com.wingedsheep.mtg.sets.definitions.frf.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Reality Shift — Fate Reforged #46.
 *
 * The manifest runs under the exiled creature's controller. The target id remains available after
 * exile, so the controller binding is the same established shape used by Unwanted Remake.
 */
val RealityShift = card("Reality Shift") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile target creature. Its controller manifests the top card of their library. " +
        "(That player puts the top card of their library onto the battlefield face down as a 2/2 " +
        "creature. If it's a creature card, it can be turned face up any time for its mana cost.)"

    spell {
        target("target creature", Targets.Creature)
        effect = Effects.Composite(
            Effects.Exile(EffectTarget.ContextTarget(0)),
            Effects.ForEachPlayer(
                players = Player.ControllerOf("target creature"),
                effects = Patterns.Library.manifest().effects,
            ),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "46"
        artist = "Howard Lyon"
        imageUri = "https://cards.scryfall.io/normal/front/e/0/e01367cb-79f4-4ed9-b12c-66f3c30264a0.jpg?1783938704"
    }
}
