package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Leyline Weaver
 * {1}{R/G}
 * Creature — Spider Avatar
 * 2/2
 *
 * Reach
 * {T}: Add {R} or {G}.
 * Whenever you cast a spell with mana value 4 or greater, untap this creature.
 *
 * Through the Omenpaths is the canonical (and only) printing.
 */
val LeylineWeaver = card("Leyline Weaver") {
    manaCost = "{1}{R/G}"
    colorIdentity = "RG"
    typeLine = "Creature — Spider Avatar"
    power = 2
    toughness = 2
    oracleText = "Reach\n" +
        "{T}: Add {R} or {G}.\n" +
        "Whenever you cast a spell with mana value 4 or greater, untap this creature."

    keywords(Keyword.REACH)

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.RED)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Any.manaValueAtLeast(4))
        effect = Effects.Untap(EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "144"
        artist = "Xavier Ribeiro"
        imageUri = "https://cards.scryfall.io/normal/front/0/f/0f6824b0-02d3-4b2a-bf74-61bd38e7e632.jpg?1783905463"
    }
}
