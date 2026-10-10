package com.wingedsheep.mtg.sets.definitions.dmu.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.EmitLibrarySearchedEventEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator

val LagomosHandOfHatred = card("Lagomos, Hand of Hatred") {
    manaCost = "{1}{B}{R}"
    colorIdentity = "BR"
    typeLine = "Legendary Creature — Human Shaman"
    oracleText = "At the beginning of combat on your turn, create a 2/1 red Elemental creature token with trample and haste. Sacrifice it at the beginning of the next end step.\n{T}: Search your library for a card, put it into your hand, then shuffle. Activate only if five or more creatures died this turn."
    power = 1
    toughness = 3

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        effect = Effects.CreateToken(
            power = 2,
            toughness = 1,
            colors = setOf(Color.RED),
            creatureTypes = setOf("Elemental"),
            keywords = setOf(Keyword.TRAMPLE, Keyword.HASTE),
            sacrificeAtStep = Step.END,
            imageUri = "https://cards.scryfall.io/normal/front/c/6/c6b2ff6f-d55a-4fa2-86be-e2e012267de4.jpg?1783921129"
        )
    }

    activatedAbility {
        cost = Costs.Tap
        restrictions = listOf(ActivationRestriction.OnlyIfCondition(
            Conditions.CompareAmounts(DynamicAmounts.creaturesDiedThisTurn(Player.Each), ComparisonOperator.GTE, 5)
        ))
        // Unrestricted search must find a card when the library is nonempty.
        effect = Effects.Pipeline {
            val library = gather(CardSource.FromZone(Zone.LIBRARY, Player.You), search = true)
            val chosen = chooseExactly(1, library, prompt = "Search your library for a card")
            toHand(chosen)
            run(Effects.ShuffleLibrary())
            run(EmitLibrarySearchedEventEffect)
        }
        description = "Search your library for a card (five creatures died this turn)"
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "205"
        artist = "Tuan Duong Chu"
        imageUri = "https://cards.scryfall.io/normal/front/1/a/1a6bb0c2-1e3c-48cd-8f10-49f047e0514c.jpg?1783921281"
    }
}
