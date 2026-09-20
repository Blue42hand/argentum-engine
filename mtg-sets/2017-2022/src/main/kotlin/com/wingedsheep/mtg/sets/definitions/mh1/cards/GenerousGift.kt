package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Generous Gift — Modern Horizons #11
 * {2}{W} · Instant
 *
 * Destroy target permanent. Its controller creates a 3/3 green Elephant creature token.
 *
 * The destroy and token creation are sequential effects on one target. Target legality therefore
 * governs resolution of the spell as a whole, while destruction itself is not a prerequisite for
 * the token: a legal indestructible permanent survives and its controller still receives the
 * Elephant. [EffectTarget.TargetController] preserves the targeted permanent's controller for the
 * second effect, matching the same controller-redirection shape used by Get Lost.
 */
val GenerousGift = card("Generous Gift") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Destroy target permanent. Its controller creates a 3/3 green Elephant creature token."

    spell {
        val permanent = target("target permanent", TargetPermanent())
        effect = Effects.Composite(
            Effects.Destroy(permanent),
            Effects.CreateToken(
                power = 3,
                toughness = 3,
                colors = setOf(Color.GREEN),
                creatureTypes = setOf("Elephant"),
                controller = EffectTarget.TargetController,
                imageUri = "https://cards.scryfall.io/normal/front/1/a/1ae11d5f-f29d-44f4-8d90-cdada1040435.jpg?1783933227",
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "11"
        artist = "Kev Walker"
        flavorText = "The best presents are impossible to regift."
        imageUri = "https://cards.scryfall.io/normal/front/9/8/983f4711-20a8-4023-8201-9a74deab10be.jpg?1783933163"
        ruling(
            "2019-06-14",
            "If the target permanent is an illegal target by the time Generous Gift tries to resolve, " +
                "the spell doesn't resolve. No player creates an Elephant. If the target is legal but " +
                "not destroyed (most likely because it has indestructible), its controller does create an Elephant."
        )
    }
}
