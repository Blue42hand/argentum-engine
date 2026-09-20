package com.wingedsheep.mtg.sets.definitions.clb.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.events.SpellCastPredicate

/**
 * Faldorn, Dread Wolf Herald — Commander Legends: Battle for Baldur's Gate #647.
 *
 * The first printed ability joins two distinct event atoms over the same payoff: a spell cast from
 * exile, and a land entering under your control specifically from exile. The latter deliberately
 * uses a zone-change trigger rather than a land-play trigger, so an effect that puts an exiled land
 * onto the battlefield also creates a Wolf. The activated ability is the standard end-of-turn
 * impulse-draw pattern after its mana, tap, and discard costs are paid.
 */
val FaldornDreadWolfHerald = card("Faldorn, Dread Wolf Herald") {
    manaCost = "{1}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Legendary Creature — Human Druid"
    power = 3
    toughness = 3
    oracleText = "Whenever you cast a spell from exile or a land you control enters from exile, " +
        "create a 2/2 green Wolf creature token.\n" +
        "{1}, {T}, Discard a card: Exile the top card of your library. You may play it this turn."

    triggeredAbility {
        trigger = Triggers.or(
            Triggers.youCastSpell(
                requires = setOf(SpellCastPredicate.CastFromZone(Zone.EXILE)),
            ),
            Triggers.entersBattlefield(
                filter = GameObjectFilter.Land.youControl(),
                from = Zone.EXILE,
                binding = TriggerBinding.ANY,
            ),
        )
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Wolf"),
            imageUri = "https://cards.scryfall.io/normal/front/8/1/" +
                "81605b8d-cf1d-49dc-aebb-a857d6796a77.jpg?1783922305",
        )
        description = "Whenever you cast a spell from exile or a land you control enters from " +
            "exile, create a 2/2 green Wolf creature token."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap, Costs.DiscardCard)
        effect = Patterns.Exile.impulse()
        description = "{1}, {T}, Discard a card: Exile the top card of your library. You may play " +
            "it this turn."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "647"
        artist = "Jason A. Engle"
        flavorText = "\"Cities are a blight on the beauty of nature.\""
        imageUri = "https://cards.scryfall.io/normal/front/2/1/" +
            "213e530e-33a9-4358-b43b-4a276a7e7190.jpg?1783922519"

        ruling(
            "2022-06-10",
            "You must pay all costs and follow all normal timing rules for cards played this way. " +
                "For example, you may only play a land from exile this way during your main phase " +
                "while the stack is empty, and only if you haven't played a land yet this turn.",
        )
    }
}
