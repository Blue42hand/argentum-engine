package com.wingedsheep.mtg.sets.definitions.snc.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ReflexiveTriggerEffect
import com.wingedsheep.sdk.scripting.effects.SearchDestination
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private const val CABARETTI_COURTYARD_ORACLE =
    "When this land enters, sacrifice it. When you do, search your library for a" +
        " basic Mountain, Forest, or Plains card, put it onto the battlefield tapped, then shuffle and you gain 1 life."

/**
 * Cabaretti Courtyard — Streets of New Capenna #249.
 * Its sacrifice must succeed before the reflexive library search is created.
 */
val CabarettiCourtyard = card("Cabaretti Courtyard") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = CABARETTI_COURTYARD_ORACLE

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.ReflexiveTrigger(
            action = Effects.SacrificeTarget(EffectTarget.Self),
            optional = false,
            reflexiveEffect = (Patterns.Library.searchLibrary(
                    filter = GameObjectFilter.BasicLand.withAnySubtype("Mountain", "Forest", "Plains"),
                    destination = SearchDestination.BATTLEFIELD,
                    entersTapped = true,
                ) then
                Effects.GainLife(1)),
            descriptionOverride = "Sacrifice this land. When you do, search your library for a basic Mountain, Forest, or Plains card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        )
        description = CABARETTI_COURTYARD_ORACLE
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "249"
        artist = "Kasia 'Kafis' Zielińska"
        flavorText = "So long as the Halo keeps flowing, the festivities never end."
        imageUri = "https://cards.scryfall.io/normal/front/9/d/9dda1e1c-b330-42e0-8547-97e2afc7615f.jpg?1783923056"
    }
}
