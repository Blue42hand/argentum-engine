package com.wingedsheep.mtg.sets.definitions.ths.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val BidentOfThassa = card("Bident of Thassa") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Enchantment Artifact"
    oracleText = "Whenever a creature you control deals combat damage to a player, you may draw a card.\n{1}{U}, {T}: Creatures your opponents control attack this turn if able."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl()).dealsCombatDamage(Recipient.AnyPlayer)
        optional = true
        effect = Effects.DrawCards(1)
    }
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}{U}"), Costs.Tap)
        effect = Effects.ForEachInGroup(GroupFilter(GameObjectFilter.Creature.opponentControls()), Effects.MarkMustAttackThisTurn(EffectTarget.IterationEntity))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "42"
        artist = "Yeong-Hao Han"
        imageUri = "https://cards.scryfall.io/normal/front/8/5/85e45d14-a501-40b9-af0a-720ecd20dad7.jpg?1783939802"
        flavorText = "The wills of mortals shift as the tide ebbs and flows."
        ruling("2018-03-16", "The controller of each attacking creature still chooses which player or planeswalker that creature attacks.")
        ruling("2018-03-16", "If a creature can't attack for any reason (such as being tapped or having come under that player's control that turn), then it doesn't attack. If there's a cost associated with having a creature attack, the player isn't forced to pay that cost, so it doesn't have to attack in that case either.")
    }
}
