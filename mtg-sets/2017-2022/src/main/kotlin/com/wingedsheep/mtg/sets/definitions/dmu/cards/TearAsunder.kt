package com.wingedsheep.mtg.sets.definitions.dmu.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.model.Zone
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Tear Asunder
 * {1}{G}
 * Instant
 * Kicker {1}{B}
 *
 * Exile target artifact or enchantment. If this spell was kicked, exile target
 * nonland permanent instead.
 *
 * The kicked cast changes the legal target domain, not merely the effect at
 * resolution. This uses the same kicker target/effect replacement rail as
 * Fight with Fire so cast-time target validation matches the Oracle ruling.
 */
val TearAsunder = card("Tear Asunder") {
    manaCost = "{1}{G}"
    colorIdentity = "BG"
    typeLine = "Instant"
    oracleText = "Kicker {1}{B} (You may pay an additional {1}{B} as you cast this spell.)\n" +
        "Exile target artifact or enchantment. If this spell was kicked, exile target nonland permanent instead."

    keywordAbility(KeywordAbility.kicker("{1}{B}"))

    spell {
        target = Targets.ArtifactOrEnchantment
        effect = Effects.Move(EffectTarget.ContextTarget(0), Zone.EXILE)

        kickerTarget = Targets.NonlandPermanent
        kickerEffect = Effects.Move(EffectTarget.ContextTarget(0), Zone.EXILE)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "183"
        artist = "Dave Kendall"
        flavorText = "No one knew why the spiritmongers were so enraged by Phyrexian technology, but it was a stroke of good luck for the Coalition."
        imageUri = "https://cards.scryfall.io/normal/front/6/2/629aa907-9533-4681-9bf2-9e56450a4cc2.jpg"
    }
}
