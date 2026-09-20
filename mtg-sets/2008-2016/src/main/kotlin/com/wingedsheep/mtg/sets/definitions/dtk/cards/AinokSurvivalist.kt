package com.wingedsheep.mtg.sets.definitions.dtk.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

/** Ainok Survivalist — Dragons of Tarkir #172. */
val AinokSurvivalist = card("Ainok Survivalist") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dog Shaman"
    oracleText = "Megamorph {1}{G} (You may cast this card face down as a 2/2 creature for {3}. " +
        "Turn it face up any time for its megamorph cost and put a +1/+1 counter on it.)\n" +
        "When this creature is turned face up, destroy target artifact or enchantment an opponent controls."
    power = 2
    toughness = 1

    morph = "{1}{G}"
    morphFaceUpEffect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)

    triggeredAbility {
        trigger = Triggers.TurnedFaceUp
        target = TargetObject(filter = TargetFilter.ArtifactOrEnchantment.opponentControls())
        effect = Effects.Destroy(EffectTarget.ContextTarget(0))
        description = "When this creature is turned face up, destroy target artifact or " +
            "enchantment an opponent controls."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "172"
        artist = "Craig J Spearing"
        imageUri = "https://cards.scryfall.io/normal/front/a/c/ac0a7410-493c-4c99-99e2-b8e1116622e8.jpg?1783938582"
        ruling("2015-02-25", "Turning a face-down creature with megamorph face up and putting a " +
            "+1/+1 counter on it is a special action. It doesn't use the stack and can't be responded to.")
        ruling("2015-02-25", "If a face-down creature with megamorph is turned face up some other " +
            "way, you won't put a +1/+1 counter on it.")
    }
}
