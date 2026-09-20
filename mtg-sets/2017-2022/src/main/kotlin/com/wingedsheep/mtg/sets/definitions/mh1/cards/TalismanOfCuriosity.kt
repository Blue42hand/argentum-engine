package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Talisman of Curiosity — Modern Horizons #232. */
val TalismanOfCuriosity = card("Talisman of Curiosity") {
    manaCost = "{2}"
    colorIdentity = "GU"
    typeLine = "Artifact"
    oracleText = "{T}: Add {C}.\n{T}: Add {G} or {U}. This artifact deals 1 damage to you."

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.GREEN)
            .then(Effects.DealDamage(1, EffectTarget.PlayerRef(Player.You)))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.BLUE)
            .then(Effects.DealDamage(1, EffectTarget.PlayerRef(Player.You)))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "232"
        artist = "Lindsey Look"
        flavorText = "\"The pain of exploring is less than the pain of not knowing.\"\n—Tamiyo"
        imageUri = "https://cards.scryfall.io/normal/front/f/d/fd52688a-39fd-430f-b950-cb56e0004396.jpg?1783933071"
    }
}
