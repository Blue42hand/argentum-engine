package com.wingedsheep.mtg.sets.definitions.cn2.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.mode
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val GrenzoHavocRaiser = card("Grenzo, Havoc Raiser") {
    manaCost = "{R}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Goblin Rogue"
    oracleText = "Whenever a creature you control deals combat damage to a player, choose one —\n• Goad target creature that player controls.\n• Exile the top card of that player's library. Until end of turn, you may cast that card and you may spend mana as though it were mana of any color to cast that spell."
    power = 2
    toughness = 2

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl()).dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.Modal(listOf(
            mode("Goad target creature that player controls") {
                val creature = target(TargetFilter.Creature.controlledByTriggeringPlayer())
                effect = Effects.Goad(creature)
            },
            mode("Exile that player's top card; you may cast it this turn") {
                effect = Effects.Pipeline {
                    val cards = gather(CardSource.TopOfLibrary(1, Player.TriggeringPlayer))
                    exile(cards)
                    run(Effects.GrantMayPlayFromExile(from = cards, withAnyManaType = true, nonLandOnly = true))
                }
            }
        ))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "54"
        artist = "Svetlin Velinov"
        imageUri = "https://cards.scryfall.io/normal/front/0/7/075cd558-2557-409a-8244-2de65664be53.jpg?1783937349"
        flavorText = "\"You burn. I'll pillage.\""
        ruling("2023-06-30", "If a creature you control has been goaded by multiple opponents, it must attack one of your opponents that hasn't goaded it, as that fulfills the maximum number of goad requirements. If a creature you control has been goaded by each of your opponents, the creature must attack an opponent (rather than a planeswalker or battle), but you choose which opponent it attacks.")
        ruling("2023-06-30", "If, during a player's declare attackers step, a creature that player controls that's been goaded is tapped, is affected by a spell or ability that says it can't attack, or hasn't been under that player's control continuously since the turn began (and doesn't have haste), then it doesn't attack. If there's a cost associated with having a creature attack a player, its controller isn't forced to pay that cost, so it doesn't have to attack that player.")
        ruling("2023-06-30", "If the creature doesn't meet any of the above exceptions and can attack, it must attack a player other than the controller of the spell or ability that goaded it if able. If the creature can't attack any of those players but could otherwise attack, it must attack a planeswalker an opponent controls, a battle an opponent controls, or a player who goaded it.")
        ruling("2023-06-30", "Being goaded isn't an ability the creature has. Once it's been goaded, it must attack as detailed above even if it loses all abilities.")
        ruling("2023-06-30", "Attacking with a goaded creature doesn't cause it to stop being goaded. If there is an additional combat phase that turn, or if another player gains control of it before it stops being goaded, it must attack again if able.")
        ruling("2016-08-23", "If you exile a land card this way, you can't play it.")
        ruling("2016-08-23", "Casting the exiled card follows the normal rules for casting that card. You must pay its costs, and you must follow all applicable timing rules. For example, if you exile a creature card this way, you must wait until your main phase to cast it.")
        ruling("2016-08-23", "If you don't cast the card, it will remain in exile.")
        ruling("2016-08-23", "The card exiled by the second mode is exiled face up.")
    }
}
