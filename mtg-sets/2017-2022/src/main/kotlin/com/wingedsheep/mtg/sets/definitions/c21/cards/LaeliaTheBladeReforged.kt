package com.wingedsheep.mtg.sets.definitions.c21.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Laelia, the Blade Reforged — Commander 2021 #53.
 *
 * The attack trigger uses the standard end-of-turn impulse-draw pattern. The growth trigger is a
 * batched library/graveyard-to-exile watcher scoped by ownership to Laelia's controller: several
 * cards exiled simultaneously produce one counter, while sequential exile events produce one
 * trigger apiece.
 */
val LaeliaTheBladeReforged = card("Laelia, the Blade Reforged") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Spirit Warrior"
    power = 2
    toughness = 2
    oracleText = "Haste\n" +
        "Whenever Laelia attacks, exile the top card of your library. You may play that card " +
        "this turn.\n" +
        "Whenever one or more cards are put into exile from your library and/or your graveyard, " +
        "put a +1/+1 counter on Laelia."

    keywords(Keyword.HASTE)

    triggeredAbility {
        trigger = Triggers.Attacks
        effect = Patterns.Exile.impulse()
        description = "Whenever Laelia attacks, exile the top card of your library. You may play " +
            "that card this turn."
    }

    triggeredAbility {
        trigger = Triggers.CardsPutIntoExile(
            fromZones = setOf(Zone.LIBRARY, Zone.GRAVEYARD),
            filter = GameObjectFilter.Any.youControl(),
        )
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "Whenever one or more cards are put into exile from your library and/or " +
            "your graveyard, put a +1/+1 counter on Laelia."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "53"
        artist = "Wisnu Tan"
        imageUri = "https://cards.scryfall.io/normal/front/a/3/" +
            "a3bb2881-e8fb-4fba-a9f9-d93e6ca24378.jpg?1783927592"

        ruling(
            "2024-06-07",
            "You must follow the normal timing permissions and restrictions while playing the " +
                "exiled card. If it's a land, you can't play it unless you have land plays " +
                "available.",
        )
        ruling("2024-06-07", "Cards exiled by Laelia, the Blade Reforged are exiled face up.")
        ruling(
            "2024-06-07",
            "You'll still pay all costs for a spell cast this way, including additional costs. " +
                "You may also pay alternative costs if any are available.",
        )
        ruling(
            "2024-06-07",
            "If a player is instructed to exile cards from their library \"until\" a certain " +
                "event occurs (for example, because of the triggered ability of a spell with " +
                "cascade or discover), that player exiles those cards one at a time. Laelia's " +
                "last ability will trigger that many times.",
        )
        ruling(
            "2024-06-07",
            "If you play a card this way, it leaves exile and becomes a new object. If it returns " +
                "to exile later in the turn, you can't play it again.",
        )
        ruling(
            "2024-06-07",
            "The last ability triggers only once for each time cards are put into exile from your " +
                "library and/or graveyard, no matter how many cards were exiled at the same time.",
        )
        ruling(
            "2024-06-07",
            "Laelia's last triggered ability doesn't care which player is exiling cards from the " +
                "library or graveyard. Cards put into exile from your library or graveyard for " +
                "any reason, such as the delve ability, cause the ability to trigger.",
        )
    }
}
