package com.wingedsheep.mtg.sets.definitions.thb.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val WoeStrider = card("Woe Strider") {
    manaCost = "{2}{B}"
    typeLine = "Creature — Horror"
    oracleText = "When this creature enters, create a 0/1 white Goat creature token.\nSacrifice another creature: Scry 1.\nEscape—{3}{B}{B}, Exile four other cards from your graveyard. (You may cast this card from your graveyard for its escape cost.)\nThis creature escapes with two +1/+1 counters on it."
    power = 3
    toughness = 2
    colorIdentity = "B"

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(power = 0, toughness = 1, colors = setOf(Color.WHITE), creatureTypes = setOf("Goat"),
            imageUri = "https://cards.scryfall.io/normal/front/3/6/36cd5f96-5683-4959-b973-37f3c2fcf9bf.jpg?1783931456")
    }
    activatedAbility {
        cost = Costs.SacrificeAnother(GameObjectFilter.Creature)
        effect = Effects.Scry(1)
    }
    keywordAbility(KeywordAbility.escape("{3}{B}{B}", Costs.additional.ExileOtherCards(4)))
    replacementEffect(EntersWithCounters(CounterType.PLUS_ONE_PLUS_ONE, 2, selfOnly = true, condition = Conditions.Escaped))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "123"
        artist = "John Thacker"
        imageUri = "https://cards.scryfall.io/normal/front/3/4/3457b558-b35b-49fd-b499-b1ec755f86ce.jpg?1783931556"
        ruling("2020-01-24", "Once you begin casting a spell with escape, it immediately moves to the stack. Players can't take any other actions until you're done casting the spell.")
        ruling("2020-01-24", "If a card with escape is put into your graveyard during your turn, you'll be able to cast it right away if it's legal to do so, before an opponent can take any actions.")
        ruling("2020-01-24", "To determine the total cost of a spell, start with the mana cost or alternative cost you're paying (such as an escape cost), add any cost increases, then apply any cost reductions. The mana value of the spell remains unchanged, no matter what the total cost to cast it was and no matter whether an alternative cost was paid.")
        ruling("2020-01-24", "After an escaped spell resolves, it returns to its owner's graveyard if it's not a permanent spell. If it is a permanent spell, it enters the battlefield and will return to its owner's graveyard if it dies later. Perhaps it will escape again—good underworld security is so hard to come by these days.")
        ruling("2020-01-24", "Escape's permission doesn't change when you may cast the spell from your graveyard.")
        ruling("2020-01-24", "If a card has multiple abilities giving you permission to cast it, such as two escape abilities or an escape ability and a flashback ability, you choose which one to apply. The others have no effect.")
        ruling("2020-01-24", "If you cast a spell with its escape permission, you can't choose to apply any other alternative costs or to cast it without paying its mana cost. If it has any additional costs, you must pay those.")
    }
}
