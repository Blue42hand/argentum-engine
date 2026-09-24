package com.wingedsheep.mtg.sets.definitions.fin.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Restoration Magic
 * {W}
 * Instant
 *
 * Tiered (Choose one additional cost.)
 * • Cure — {0} — Target permanent gains hexproof and indestructible until end of turn.
 * • Cura — {1} — Target permanent gains hexproof and indestructible until end of turn. You gain 3 life.
 * • Curaga — {3}{W} — Permanents you control gain hexproof and indestructible until end of turn. You gain 6 life.
 *
 * Tiered (CR 702.183): a choose-one modal spell where the chosen tier's additional mana cost is
 * paid at cast. The first two tiers protect a single target permanent; the top tier protects all
 * permanents you control and gains the most life.
 */
val RestorationMagic = card("Restoration Magic") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Tiered (Choose one additional cost.)\n" +
        "• Cure — {0} — Target permanent gains hexproof and indestructible until end of turn.\n" +
        "• Cura — {1} — Target permanent gains hexproof and indestructible until end of turn. You gain 3 life.\n" +
        "• Curaga — {3}{W} — Permanents you control gain hexproof and indestructible until end of turn. You gain 6 life."

    spell {
        tiered {
            tier("Cure", "{0}", "Target permanent gains hexproof and indestructible until end of turn.") {
                val permanent = target("target permanent", Targets.Permanent)
                effect = Effects.Composite(
                    Effects.GrantKeyword(Keyword.HEXPROOF, permanent),
                    Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, permanent)
                )
            }
            tier(
                "Cura", "{1}",
                "Target permanent gains hexproof and indestructible until end of turn. You gain 3 life."
            ) {
                val permanent = target("target permanent", Targets.Permanent)
                effect = Effects.Composite(
                    Effects.GrantKeyword(Keyword.HEXPROOF, permanent),
                    Effects.GrantKeyword(Keyword.INDESTRUCTIBLE, permanent),
                    Effects.GainLife(3)
                )
            }
            tier(
                "Curaga", "{3}{W}",
                "Permanents you control gain hexproof and indestructible until end of turn. You gain 6 life."
            ) {
                effect = Effects.Composite(
                    Patterns.Group.grantKeywordToAll(Keyword.HEXPROOF, Filters.Group.permanentsYouControl),
                    Patterns.Group.grantKeywordToAll(Keyword.INDESTRUCTIBLE, Filters.Group.permanentsYouControl),
                    Effects.GainLife(6)
                )
            }
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "30"
        artist = "Yumi Yaoshida"
        imageUri = "https://cards.scryfall.io/normal/front/4/9/494e68e9-ecba-4482-82bc-207ad59144c1.jpg?1748705864"
    }
}
