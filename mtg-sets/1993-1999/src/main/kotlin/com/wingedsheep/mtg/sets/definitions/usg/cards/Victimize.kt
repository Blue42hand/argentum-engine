package com.wingedsheep.mtg.sets.definitions.usg.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ForEachTargetEffect
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Victimize — Urza's Saga #166
 * {2}{B} · Sorcery
 *
 * Choose two target creature cards in your graveyard. Sacrifice a creature. If you do,
 * return the chosen cards to the battlefield tapped.
 */
val Victimize = card("Victimize") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Choose two target creature cards in your graveyard. Sacrifice a creature. " +
        "If you do, return the chosen cards to the battlefield tapped."

    spell {
        target(
            "two target creature cards in your graveyard",
            TargetObject(count = 2, filter = TargetFilter.CreatureInYourGraveyard),
        )
        effect = Effects.IfYouDo(
            action = Effects.SacrificeOwn(GameObjectFilter.Creature),
            ifYouDo = ForEachTargetEffect(
                effects = listOf(
                    Effects.Move(
                        target = EffectTarget.ContextTarget(0),
                        destination = Zone.BATTLEFIELD,
                        placement = ZonePlacement.Tapped,
                    )
                )
            ),
            successCriterion = SuccessCriterion.PermanentsSacrificed,
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "166"
        artist = "Val Mayerik"
        flavorText = "The priest cast Xantcha to the ground. \"It is defective. We must scrap it.\""
        imageUri = "https://cards.scryfall.io/normal/front/c/a/caafe7da-0167-4c53-bbad-172f900d137b.jpg?1783946336"
        ruling("2020-11-10", "You must choose two target creature cards. You can't cast Victimize targeting only one creature card.")
        ruling("2020-11-10", "If one of the targeted creature cards is an illegal target as Victimize resolves, you still sacrifice a creature and return the other card to the battlefield. If both targets are illegal, Victimize doesn't resolve and you won't sacrifice a creature.")
        ruling("2020-11-10", "The creature sacrificed for Victimize isn't chosen until it resolves. You can't sacrifice one of the creature cards targeted by Victimize because those cards are in your graveyard at that time.")
        ruling("2020-11-10", "As Victimize resolves, you must sacrifice a creature if able. You can't choose not to sacrifice one if you control a creature.")
    }
}
