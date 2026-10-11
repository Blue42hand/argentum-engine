package com.wingedsheep.mtg.sets.definitions.mid.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Turn the Earth — zero targets gains life without shuffling; each affected owner shuffles once. */
val TurnTheEarth = card("Turn the Earth") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Choose up to three target cards in graveyards. The owners of those cards shuffle them into their libraries. You gain 2 life.\nFlashback {1}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    spell {
        targets(TargetFilter.CardInGraveyard, count = 3, optional = true)
        effect = Effects.Pipeline {
            val cards = gather(CardSource.ChosenTargets)
            // A shuffled empty collection would still shuffle a library; Oracle explicitly forbids it here.
            ifNotEmpty(cards) {
                move(cards, CardDestination.ToZone(Zone.LIBRARY, placement = ZonePlacement.Shuffled))
            }
            run(Effects.GainLife(2))
        }
    }

    keywordAbility(KeywordAbility.flashback("{1}{G}"))

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "205"
        artist = "Alayna Danner"
        imageUri = "https://cards.scryfall.io/normal/front/3/4/34076a93-9e8f-45d4-a1d7-31f40210a5b6.jpg?1783925569"
        ruling("2025-06-06", "A spell cast using flashback will always be exiled afterward, whether it resolves, is countered, or leaves the stack in some other way.")
        ruling("2021-09-24", "You may choose no targets for Turn the Earth. In that case, no libraries will be shuffled and you'll just gain 2 life.")
        ruling("2021-09-24", "Each affected library is shuffled only once, even if multiple cards are being shuffled into it.")
        ruling("2021-09-24", "If you choose target cards in graveyards and all of those targets are somehow illegal as Turn the Earth tries to resolve (perhaps because your opponent responded with a Turn the Earth of their own), the spell will not resolve and you will gain no life.")
    }
}
