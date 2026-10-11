package com.wingedsheep.mtg.sets.definitions.mh2.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.eternalize
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val TimelessWitness = card("Timeless Witness") {
    manaCost = "{2}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Shaman"
    oracleText = "When this creature enters, return target card from your graveyard to your hand.\nEternalize {5}{G}{G} ({5}{G}{G}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Human Shaman with no mana cost. Eternalize only as a sorcery.)"
    power = 2
    toughness = 1
    triggeredAbility {
        trigger = Triggers.self.enters()
        val returned = target(TargetFilter(GameObjectFilter.Any.ownedByYou(), zone = Zone.GRAVEYARD))
        effect = Effects.Move(returned, Zone.HAND)
    }
    eternalize("{5}{G}{G}")
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "179"
        artist = "Deruchenko Alexander"
        imageUri = "https://cards.scryfall.io/normal/front/f/2/f22c07e2-91d2-4edb-bd2b-cca6d4cefcc9.jpg?1783926823"
        ruling("2021-06-18", "If a creature card with eternalize is put into your graveyard during your main phase, you'll have priority immediately afterward. You can activate its eternalize ability before any player can try to exile it.")
        ruling("2021-06-18", "Once you've activated an eternalize ability, the card is immediately exiled. Opponents can't try to stop the ability by exiling the card.")
        ruling("2021-06-18", "The token copies exactly what was printed on the original card and nothing else, except the characteristics specifically modified by eternalize. It doesn't copy any information about the object the card was before it was put into your graveyard.")
        ruling("2021-06-18", "The token is a Zombie in addition to its other types and is black instead of its other colors. Its base power and toughness are 4/4. It has no mana cost, and thus its mana value is 0. These are copiable values of the token that other effects may copy.")
    }
}
