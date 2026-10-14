package com.wingedsheep.mtg.sets.definitions.afr.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val WizardClass = card("Wizard Class") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Enchantment — Class"
    oracleText = "(Gain the next level as a sorcery to add its ability.)\nYou have no maximum hand size.\n{2}{U}: Level 2\nWhen this Class becomes level 2, draw two cards.\n{4}{U}: Level 3\nWhenever you draw a card, put a +1/+1 counter on target creature you control."

    staticAbility { ability = NoMaximumHandSize }
    classLevel(2, "{2}{U}") {
        triggeredAbility { trigger = Triggers.self.enters(); effect = Effects.DrawCards(2) }
    }
    classLevel(3, "{4}{U}") {
        triggeredAbility {
            trigger = Triggers.you.draws()
            val creature = target(TargetFilter.CreatureYouControl)
            effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, creature)
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "81"
        artist = "Marta Nael"
        imageUri = "https://cards.scryfall.io/normal/front/d/1/d1f629fb-b097-4240-8560-ef47f5678f48.jpg?1783926504"
        ruling("2021-07-23", "Each Class has five abilities. The three in the major sections of its text box are class abilities. Class abilities can be static, activated, or triggered abilities. The other two are level abilities, one activated ability to advance the Class to level 2 and another to advance the Class to level 3.")
        ruling("2021-07-23", "Each Class starts with only the first of three class abilities. As the first level ability resolves, the Class becomes level 2 and gains the second class ability. As the second level ability resolves, the Class becomes level 3 and gains the third class ability.")
        ruling("2021-07-23", "Gaining a level won't remove abilities that a Class had at a previous level.")
        ruling("2021-07-23", "Gaining a level is a normal activated ability. It uses the stack and can be responded to.")
        ruling("2021-07-23", "You can't activate the first level ability of a Class unless that Class is level 1. Similarly, you can't activate the second level ability of a Class unless that Class is level 2.")
        ruling("2021-07-23", "You can multiclass or even control multiple Class enchantments of the same class. Each Class permanent tracks its own level separately.")
        ruling("2021-07-23", "Some Class cards have an effect that increases when more are under your control. For example, if you have multiple Barbarian Class cards, you roll that many additional dice and ignore that many of the lowest rolls.")
    }
}
