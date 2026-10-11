package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.effects.CopyExceptions
import com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Add Eternalize—[cost] (CR 702.129a) using ordinary graveyard activation and token-copy primitives.
 * The card is exiled as a cost, before opponents receive priority. The token copies the printed
 * card in exile, with black, 4/4, added Zombie and no-mana-cost exceptions as copiable values.
 *
 * ```kotlin
 * eternalize("{5}{G}{G}")
 * ```
 */
fun CardBuilder.eternalize(cost: String) {
    val manaCost = ManaCost.parse(cost)
    activatedAbilities.add(
        ActivatedAbility(
            id = AbilityId("eternalize_$manaCost"),
            cost = AbilityCost.Composite(
                listOf(AbilityCost.Atom(CostAtom.Mana(manaCost)), AbilityCost.ExileSelf)
            ),
            effect = CreateTokenCopyOfTargetEffect(
                target = EffectTarget.Self,
                exceptions = CopyExceptions(
                    overrideColors = setOf(Color.BLACK),
                    powerOverride = 4,
                    toughnessOverride = 4,
                    addedSubtypes = setOf(Subtype("Zombie")),
                    noManaCost = true,
                ),
            ),
            timing = TimingRule.SorcerySpeed,
            activateFromZone = Zone.GRAVEYARD,
            descriptionOverride = "Eternalize $manaCost (Exile this card from your graveyard and pay its " +
                "eternalize cost: Create a token that's a copy of it, except it's black, it's 4/4, " +
                "it has no mana cost, and it's a Zombie in addition to its other types. " +
                "Activate only as a sorcery.)",
        )
    )
    keywordSet.add(Keyword.ETERNALIZE)
}
