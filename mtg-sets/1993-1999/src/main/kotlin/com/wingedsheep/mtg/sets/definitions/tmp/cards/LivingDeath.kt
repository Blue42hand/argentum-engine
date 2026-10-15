package com.wingedsheep.mtg.sets.definitions.tmp.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val LivingDeath = card("Living Death") {
    manaCost = "{3}{B}{B}"
    typeLine = "Sorcery"
    oracleText = "Each player exiles all creature cards from their graveyard, then sacrifices all creatures they control, then puts all cards they exiled this way onto the battlefield."
    colorIdentity = "B"

    spell {
        effect = Effects.Pipeline {
            val graveyardCreatures = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.Each, GameObjectFilter.Creature))
            val exiled = moveTracked(graveyardCreatures, CardDestination.ToZone(Zone.EXILE))
            val creatures = gather(GameObjectFilter.Creature)
            sacrifice(creatures)
            move(exiled, CardDestination.ToZone(Zone.BATTLEFIELD), underOwnersControl = true,
                filter = GameObjectFilter.Creature.currentlyIn(Zone.EXILE))
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "142"
        artist = "Charles Gillespie"
        imageUri = "https://cards.scryfall.io/normal/front/6/c/6c820476-fbda-4073-baf6-51e71f45ed58.jpg?1783946637"
        ruling("2018-03-16", "As Living Death resolves, all players exile their creature cards from graveyards at the same time. Then all players sacrifice all creatures they control at the same time. Then all players put all creatures they exiled onto the battlefield at the same time.")
        ruling("2018-03-16", "Only cards exiled by Living Death's first instruction are put onto the battlefield. If a replacement effect (such as that of Leyline of the Void) causes any of the sacrificed creatures to be exiled instead of put into a graveyard, those cards aren't returned to the battlefield.")
    }
}
