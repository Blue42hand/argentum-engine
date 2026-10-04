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

private const val MAESTROS_THEATER_ORACLE =
    "When this land enters, sacrifice it. When you do, search your library for a" +
        " basic Island, Swamp, or Mountain card, put it onto the battlefield tapped, then shuffle and you gain 1 life."

/**
 * Maestros Theater — Streets of New Capenna #251.
 * Its sacrifice must succeed before the reflexive library search is created.
 */
val MaestrosTheater = card("Maestros Theater") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = MAESTROS_THEATER_ORACLE

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = ReflexiveTriggerEffect(
            action = Effects.SacrificeTarget(EffectTarget.Self),
            optional = false,
            reflexiveEffect = Effects.Composite(listOf(
                Patterns.Library.searchLibrary(
                    filter = GameObjectFilter.BasicLand.withAnySubtype("Island", "Swamp", "Mountain"),
                    destination = SearchDestination.BATTLEFIELD,
                    entersTapped = true,
                ),
                Effects.GainLife(1),
            )),
            descriptionOverride = "Sacrifice this land. When you do, search your library for a basic Island, Swamp, or Mountain card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        )
        description = MAESTROS_THEATER_ORACLE
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "251"
        artist = "Jokubas Uogintas"
        flavorText = "Only extraordinary performers are invited to grace the grandest stage in New Capenna."
        imageUri = "https://cards.scryfall.io/normal/front/5/1/51eb4a44-a5c8-49ed-b440-2462626bb638.jpg?1783923056"
    }
}
