package com.wingedsheep.mtg.sets.definitions.uds.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalManaOnSourceTap
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Bubbling Muck — Urza's Destiny #54.
 *
 * Like High Tide, the granted mana static checks the land's Swamp subtype each time any player
 * taps one for mana, including lands that enter after this spell resolves.
 */
val BubblingMuck = card("Bubbling Muck") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Until end of turn, whenever a player taps a Swamp for mana, that player adds an additional {B}."

    spell {
        effect = Effects.GrantStaticAbility(
            ability = AdditionalManaOnSourceTap(
                sourceFilter = GameObjectFilter.Land.withSubtype(Subtype.SWAMP),
                color = Color.BLACK,
            ),
            target = EffectTarget.Controller,
            duration = Duration.EndOfTurn,
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "54"
        artist = "Greg Hildebrandt & Tim Hildebrandt"
        flavorText = "The muck claims a hundred living things for each meager treasure it spews forth."
        imageUri = "https://cards.scryfall.io/normal/front/6/c/6ca76614-78a1-4535-9162-70469d1e8a13.jpg?1783946074"
        ruling("2004-10-04", "Affects lands with type Swamp, not lands that are named “Swamp.”")
        ruling("2004-10-04", "Affects lands tapped for rest of turn, not just swamps on the battlefield at the time it resolves. This is because it affects players and not the lands themselves.")
    }
}
