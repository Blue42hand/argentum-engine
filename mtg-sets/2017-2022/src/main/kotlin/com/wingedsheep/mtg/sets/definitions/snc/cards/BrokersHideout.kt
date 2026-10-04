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

private const val BROKERS_HIDEOUT_ORACLE =
    "When this land enters, sacrifice it. When you do, search your library for a" +
        " basic Forest, Plains, or Island card, put it onto the battlefield tapped, then shuffle and you gain 1 life."

/**
 * Brokers Hideout — Streets of New Capenna #248.
 * Its sacrifice must succeed before the reflexive library search is created.
 */
val BrokersHideout = card("Brokers Hideout") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = BROKERS_HIDEOUT_ORACLE

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = ReflexiveTriggerEffect(
            action = Effects.SacrificeTarget(EffectTarget.Self),
            optional = false,
            reflexiveEffect = Effects.Composite(listOf(
                Patterns.Library.searchLibrary(
                    filter = GameObjectFilter.BasicLand.withAnySubtype("Forest", "Plains", "Island"),
                    destination = SearchDestination.BATTLEFIELD,
                    entersTapped = true,
                ),
                Effects.GainLife(1),
            )),
            descriptionOverride = "Sacrifice this land. When you do, search your library for a basic Forest, Plains, or Island card, put it onto the battlefield tapped, then shuffle and you gain 1 life.",
        )
        description = BROKERS_HIDEOUT_ORACLE
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "248"
        artist = "James Paick"
        flavorText = "Once the witness was inside, the safe house vanished first from sight, then from memory."
        imageUri = "https://cards.scryfall.io/normal/front/9/8/989b299b-daa9-4bda-94e2-9a2f0e8f2bce.jpg?1783923058"
    }
}
