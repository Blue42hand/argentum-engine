package com.wingedsheep.mtg.sets.definitions.tor.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.conditions.Exists
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Tainted Wood
 * Land
 *
 * {T}: Add {C}.
 * {T}: Add {B} or {G}. Activate only if you control a Swamp.
 *
 * The colored line is represented by two mana abilities, as with every dual-tapping land. Both
 * share the same activation restriction and are legal only while their controller has a Swamp.
 */
val TaintedWood = card("Tainted Wood") {
    manaCost = ""
    colorIdentity = "BG"
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{T}: Add {B} or {G}. Activate only if you control a Swamp."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    val controlsSwamp = ActivationRestriction.OnlyIfCondition(
        Conditions.Any(
            Exists(Player.You, Zone.BATTLEFIELD, GameObjectFilter.Land.withSubtype("Swamp")),
        ),
    )

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLACK)
        manaAbility = true
        timing = TimingRule.ManaAbility
        restrictions = listOf(controlsSwamp)
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
        restrictions = listOf(controlsSwamp)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "143"
        artist = "Rob Alexander"
        imageUri = "https://cards.scryfall.io/normal/front/a/2/a20a35cc-69e5-42b8-b28c-ae5147451150.jpg?1783945138"
    }
}
