package com.wingedsheep.mtg.sets.definitions.c21.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Wake the Past — Commander 2021 #75. Only artifacts actually returned gain temporary haste. */
val WakeThePast = card("Wake the Past") {
    manaCost = "{5}{R}{W}"
    colorIdentity = "RW"
    typeLine = "Sorcery"
    oracleText = "Return all artifact cards from your graveyard to the battlefield. They gain haste until end of turn."

    spell {
        effect = Effects.Pipeline {
            val artifacts = gather(CardSource.FromZone(Zone.GRAVEYARD, Player.You, GameObjectFilter.Artifact))
            val returned = moveTracked(artifacts, CardDestination.ToZone(Zone.BATTLEFIELD))
            run(Effects.ForEachInCollection(
                collection = returned,
                effect = Effects.GrantKeyword(Keyword.HASTE, EffectTarget.IterationEntity, Duration.EndOfTurn)
            ))
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "75"
        artist = "Yeong-Hao Han"
        flavorText = "\"Belong in a museum? I think we should let relics belong wherever they please!\"\n—Quintorius, Lorehold mage-student"
        imageUri = "https://cards.scryfall.io/normal/front/a/e/aeae7fda-05f5-40af-90cc-b80521ee0521.jpg?1783927584"
    }
}
