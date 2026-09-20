package com.wingedsheep.mtg.sets.definitions.bfz.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.MayEffect

val MortuaryMire = card("Mortuary Mire") {
    manaCost = ""
    colorIdentity = "B"
    typeLine = "Land"
    oracleText = "This land enters tapped.\n" +
        "When this land enters, you may put target creature card from your graveyard on top of your library.\n" +
        "{T}: Add {B}."

    replacementEffect(EntersTapped())

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val creature = target("creature card from your graveyard", Targets.CreatureCardInYourGraveyard)
        effect = MayEffect(Effects.PutOnTopOfLibrary(creature))
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLACK)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "240"
        artist = "James Paick"
        imageUri = "https://cards.scryfall.io/normal/front/c/6/c6b31722-9626-4fea-9971-54c9aa8238e8.jpg?1783938176"
    }
}
