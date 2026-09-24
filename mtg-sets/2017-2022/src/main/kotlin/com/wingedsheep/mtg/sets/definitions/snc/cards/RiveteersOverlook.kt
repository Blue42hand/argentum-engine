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

/**
 * Riveteers Overlook — Streets of New Capenna #255
 * Land
 *
 * When this land enters, sacrifice it. When you do, search your library for a basic Swamp,
 * Mountain, or Forest card, put it onto the battlefield tapped, then shuffle and you gain 1 life.
 *
 * The sacrifice and the "when you do" payoff are a genuine reflexive trigger (CR 603.12). If the
 * land cannot be sacrificed when the enters-the-battlefield trigger resolves, the search/life-gain
 * reflexive ability is not created.
 */
val RiveteersOverlook = card("Riveteers Overlook") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "When this land enters, sacrifice it. When you do, search your library for a " +
        "basic Swamp, Mountain, or Forest card, put it onto the battlefield tapped, then shuffle " +
        "and you gain 1 life."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = ReflexiveTriggerEffect(
            action = Effects.SacrificeTarget(EffectTarget.Self),
            optional = false,
            reflexiveEffect = Effects.Composite(
                Patterns.Library.searchLibrary(
                    filter = GameObjectFilter.BasicLand.withAnySubtype("Swamp", "Mountain", "Forest"),
                    destination = SearchDestination.BATTLEFIELD,
                    entersTapped = true,
                ),
                Effects.GainLife(1),
            ),
            descriptionOverride = "Sacrifice this land. When you do, search your library for a " +
                "basic Swamp, Mountain, or Forest card, put it onto the battlefield tapped, then " +
                "shuffle and you gain 1 life.",
        )
        description = oracleText
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "255"
        artist = "Lucas Staniec"
        flavorText = "Along the outer girders, in gold filigree, are inscribed the names of all " +
            "those who fell to their deaths during construction."
        imageUri = "https://cards.scryfall.io/normal/front/1/d/1d161f81-c01a-4c91-b025-52dcb1881638.jpg?1783923054"
    }
}
