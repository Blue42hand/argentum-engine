package com.wingedsheep.mtg.sets.definitions.bro.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Blanchwood Prowler — The Brothers' War #172. */
val BlanchwoodProwler = card("Blanchwood Prowler") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elemental"
    power = 1
    toughness = 1
    oracleText = "When this creature enters, mill three cards. You may put a land card from among " +
        "the cards milled this way into your hand. If you don't, put a +1/+1 counter on this " +
        "creature. (To mill a card, put the top card of your library into your graveyard.)"

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Pipeline {
            val milled = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(3), Player.You))
            toGraveyard(milled)
            val lands = filter(milled, GameObjectFilter.Land)
            val chosen = chooseUpTo(1, from = lands)
            ifNotEmpty(chosen) {
                toHand(chosen)
            } orElse {
                run(Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self))
            }
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "172"
        artist = "Justine Cruz"
        imageUri = "https://cards.scryfall.io/normal/front/9/c/9c6988b6-ade0-4cd5-b27c-146e5e7ae91f.jpg?1783920050"
    }
}
