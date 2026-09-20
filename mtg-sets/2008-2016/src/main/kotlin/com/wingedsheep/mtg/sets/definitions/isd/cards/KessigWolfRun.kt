package com.wingedsheep.mtg.sets.definitions.isd.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Kessig Wolf Run
 * Land
 * {T}: Add {C}.
 * {X}{R}{G}, {T}: Target creature gets +X/+0 and gains trample until end of turn.
 */
val KessigWolfRun = card("Kessig Wolf Run") {
    colorIdentity = "RG"
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n" +
        "{X}{R}{G}, {T}: Target creature gets +X/+0 and gains trample until end of turn."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{X}{R}{G}"), Costs.Tap)
        val creature = target("target creature", TargetCreature(filter = TargetFilter.Creature))
        effect = Effects.Composite(
            Effects.ModifyStats(DynamicAmount.XValue, DynamicAmount.Fixed(0), creature),
            Effects.GrantKeyword(Keyword.TRAMPLE, creature),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "243"
        artist = "Eytan Zana"
        flavorText = "When a werewolf changes for the first time, that first howl is said to echo " +
            "through the wilds till moonset."
        imageUri = "https://cards.scryfall.io/normal/front/4/a/4a8447fe-7368-470a-911a-1083ec6cc831.jpg?1783940895"
    }
}
