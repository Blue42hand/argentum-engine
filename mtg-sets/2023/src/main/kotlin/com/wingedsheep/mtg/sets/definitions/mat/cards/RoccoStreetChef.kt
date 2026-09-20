package com.wingedsheep.mtg.sets.definitions.mat.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggerSpec
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.GrantMayPlayFromExileEffect
import com.wingedsheep.sdk.scripting.effects.MayPlayExpiry
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.events.SpellCastPredicate
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Rocco, Street Chef — March of the Machine: The Aftermath #44.
 *
 * The end-step loop gets a fresh pipeline for each player, so every permission covers only that
 * player's exiled card. The permission's turn anchor remains Rocco's controller despite that
 * per-player rebinding, making every window close together at that player's next end step.
 *
 * The second printed ability is represented as two event atoms with the same payoff. A spell cast
 * from exile and a land played from exile are disjoint game actions; keeping them separate also
 * preserves exact cast-vs-play semantics and avoids treating an effect that puts an exiled land
 * onto the battlefield as a land play.
 */
val RoccoStreetChef = card("Rocco, Street Chef") {
    manaCost = "{R}{G}{W}"
    colorIdentity = "RGW"
    typeLine = "Legendary Creature — Elf Druid"
    power = 2
    toughness = 4
    oracleText = "At the beginning of your end step, each player exiles the top card of their " +
        "library. Until your next end step, each player may play the card they exiled this way.\n" +
        "Whenever a player plays a land from exile or casts a spell from exile, you put a +1/+1 " +
        "counter on target creature and create a Food token. (It's an artifact with \"{2}, {T}, " +
        "Sacrifice this token: You gain 3 life.\")"

    triggeredAbility {
        trigger = Triggers.YourEndStep
        effect = Effects.ForEachPlayer(
            Player.ActivePlayerFirst,
            listOf(
                GatherCardsEffect(
                    source = CardSource.TopOfLibrary(DynamicAmount.Fixed(1), Player.You),
                    storeAs = "roccoExiled",
                ),
                MoveCollectionEffect(
                    from = "roccoExiled",
                    destination = CardDestination.ToZone(Zone.EXILE),
                    storeMovedAs = "roccoExiled",
                ),
                GrantMayPlayFromExileEffect(
                    from = "roccoExiled",
                    expiry = MayPlayExpiry.UntilNextEndStep,
                ),
            ),
        )
        description = "At the beginning of your end step, each player exiles the top card of " +
            "their library. Until your next end step, each player may play the card they exiled " +
            "this way."
    }

    triggeredAbility {
        trigger = Triggers.anyPlayerPlaysLand(fromZone = Zone.EXILE)
        val creature = target("target creature", Targets.Creature)
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, creature)
            .then(Effects.CreateFood())
        description = "Whenever a player plays a land from exile, you put a +1/+1 counter on " +
            "target creature and create a Food token."
    }

    triggeredAbility {
        trigger = TriggerSpec(
            event = EventPattern.SpellCastEvent(
                player = Player.Each,
                requires = setOf(SpellCastPredicate.CastFromZone(Zone.EXILE)),
            ),
            binding = TriggerBinding.ANY,
        )
        val creature = target("target creature", Targets.Creature)
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, creature)
            .then(Effects.CreateFood())
        description = "Whenever a player casts a spell from exile, you put a +1/+1 counter on " +
            "target creature and create a Food token."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "44"
        artist = "Bram Sels"
        imageUri = "https://cards.scryfall.io/normal/front/c/d/cdb53ce7-845c-4c62-98a9-4fc33c67a07b.jpg?1783916509"

        ruling("2023-05-12", "Unless an effect allows you to play additional lands that turn, you can play a land card you exiled with Rocco only if you haven't played a land yet that turn.")
        ruling("2023-05-12", "If Rocco's last ability triggers because a spell was cast, that ability will resolve before that spell does. If the ability triggers because a land was played, the land will be on the battlefield by the time you put the ability on the stack. If the land entering the battlefield causes any triggered abilities to trigger, those abilities and Rocco's ability will be put on the stack at the same time, with the abilities controlled by the player whose turn it is going on the stack first (and thus resolving last).")
        ruling("2023-05-12", "All cards not played remain exiled.")
        ruling("2023-05-12", "A player may play the card they exiled with Rocco even if Rocco leaves the battlefield before the duration expires.")
        ruling("2023-05-12", "Playing the card you exiled with Rocco follows the normal rules for playing that card. You must pay its costs, and you must follow all applicable timing rules. For example, if the card is a creature card, you can cast that card by paying its mana cost only during your main phase while the stack is empty.")
        ruling("2024-11-08", "Food is an artifact type. Even though it appears on some creatures, it's never a creature type.")
        ruling("2024-11-08", "You can't sacrifice a Food to pay multiple costs. For example, you can't sacrifice a Food token to activate its own ability and also to activate Maraleaf Rider's ability.")
        ruling("2024-11-08", "Whatever you do, don't eat the delicious cards.")
    }
}
