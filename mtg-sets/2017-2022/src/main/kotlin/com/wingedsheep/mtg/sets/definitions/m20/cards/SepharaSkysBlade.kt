package com.wingedsheep.mtg.sets.definitions.m20.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val SepharaSkysBlade = card("Sephara, Sky's Blade") {
    manaCost = "{4}{W}{W}{W}"
    colorIdentity = "W"
    typeLine = "Legendary Creature — Angel"
    oracleText = "You may pay {W} and tap four untapped creatures you control with flying rather than pay this spell's mana cost.\nFlying, lifelink\nOther creatures you control with flying have indestructible. (Damage and effects that say \"destroy\" don't destroy them.)"
    power = 7
    toughness = 7

    keywords(Keyword.FLYING, Keyword.LIFELINK)
    selfAlternativeCost = SelfAlternativeCost(
        manaCost = ManaCost.parse("{W}"),
        additionalCosts = listOf(Costs.additional.TapPermanents(count = 4, filter = GameObjectFilter.Creature.withKeyword(Keyword.FLYING)))
    )
    staticAbility {
        ability = GrantKeyword(Keyword.INDESTRUCTIBLE, filter = GroupFilter(GameObjectFilter.Creature.withKeyword(Keyword.FLYING).youControl(), excludeSelf = true))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "36"
        artist = "Livia Prima"
        imageUri = "https://cards.scryfall.io/normal/front/b/c/bc95881b-8fbf-4d82-b631-5e4404ccc28a.jpg?1783933020"
        ruling("2019-07-12", "To pay Sephara's alternative cost, you may tap any untapped creatures you control with flying, including ones you haven't controlled continuously since the beginning of your most recent turn.")
        ruling("2019-07-12", "To determine the total cost of a spell, start with the mana cost or alternative cost you're paying (such as Sephara's alternative cost), add any cost increases, then apply any cost reductions. The mana value of the spell remains unchanged, no matter what the total cost to cast it was.")
        ruling("2019-07-12", "Because damage remains marked on a creature until it's removed as the turn ends, lethal damage dealt to other creatures you control with flying may cause them to be destroyed if Sephara leaves the battlefield during that turn.")
    }
}
