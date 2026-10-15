package com.wingedsheep.mtg.sets.definitions.snc.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val ProfessionalFaceBreaker = card("Professional Face-Breaker") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Human Warrior"
    oracleText = "Menace\nWhenever one or more creatures you control deal combat damage to a player, create a Treasure token.\nSacrifice a Treasure: Exile the top card of your library. You may play that card this turn."
    power = 2
    toughness = 3

    keywords(Keyword.MENACE)
    triggeredAbility {
        trigger = Triggers.oneOrMore(GameObjectFilter.Creature.youControl()).dealCombatDamageToAPlayer()
        effect = Effects.CreateTreasure()
    }
    activatedAbility {
        cost = Costs.Sacrifice(GameObjectFilter.Any.withSubtype("Treasure"))
        effect = Patterns.Exile.impulse()
        description = "Sacrifice a Treasure: Exile your top card; you may play it this turn"
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "116"
        artist = "Dan Murayama Scott"
        imageUri = "https://cards.scryfall.io/normal/front/4/2/42acbf52-b137-44f0-a815-2817fe8d2da2.jpg?1783923119"
    }
}
