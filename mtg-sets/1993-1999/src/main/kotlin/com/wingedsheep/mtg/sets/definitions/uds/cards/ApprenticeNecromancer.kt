package com.wingedsheep.mtg.sets.definitions.uds.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ApprenticeNecromancer = card("Apprentice Necromancer") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Zombie Wizard"
    power = 1
    toughness = 1
    oracleText = "{B}, {T}, Sacrifice this creature: Return target creature card from your graveyard " +
        "to the battlefield. That creature gains haste. At the beginning of the next end step, sacrifice it."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{B}"), Costs.Tap, Costs.SacrificeSelf)
        target(TargetFilter.CreatureInYourGraveyard)
        effect = Effects.Pipeline {
            val returned = moveTracked(gather(CardSource.ChosenTargets), CardDestination.ToZone(Zone.BATTLEFIELD))
            run(Effects.ForEachInCollection(
                collection = returned,
                effect = Effects.GrantKeyword(Keyword.HASTE, EffectTarget.IterationEntity, Duration.Permanent) then
                    Effects.CreateDelayedTrigger(
                        step = Step.END,
                        effect = Effects.SacrificeTarget(EffectTarget.IterationEntity)
                    )
            ))
        }
        description = "Return a creature from your graveyard with haste; sacrifice it at the next end step"
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "51"
        artist = "Pete Venters"
        imageUri = "https://cards.scryfall.io/normal/front/6/d/6d7cc1f6-9897-4de4-8e94-40cbe2d962a2.jpg?1783946075"
        ruling("2018-12-07", "Apprentice Necromancer can’t be the target of its own ability. This is because targets are chosen for activated abilities before costs (such as “Sacrifice Apprentice Necromancer”) are paid.")
    }
}
