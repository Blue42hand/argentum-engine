package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Avenger of Zendikar — Worldwake #96
 *
 * The ETB count is evaluated when the trigger resolves. Each optional landfall resolution gathers
 * the controller's Plants at that moment and puts one +1/+1 counter on every one of them.
 */
val AvengerOfZendikar = card("Avenger of Zendikar") {
    manaCost = "{5}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elemental"
    power = 5
    toughness = 5
    oracleText = "When this creature enters, create a 0/1 green Plant creature token for each land " +
        "you control.\nLandfall — Whenever a land you control enters, you may put a +1/+1 counter " +
        "on each Plant creature you control."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateToken(
            count = DynamicAmounts.landsYouControl(),
            power = 0,
            toughness = 1,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Plant"),
            imageUri = "https://cards.scryfall.io/normal/front/c/1/c1424e8d-1f96-44af-9382-c337b6695ddf.jpg?1783942069",
        )
        description = "When this creature enters, create a 0/1 green Plant creature token for each " +
            "land you control."
    }

    triggeredAbility {
        trigger = Triggers.LandYouControlEnters
        optional = true
        effect = Effects.Pipeline {
            val plants = gather(
                CardSource.FromZone(
                    zone = Zone.BATTLEFIELD,
                    player = Player.You,
                    filter = GameObjectFilter.Creature.withSubtype(Subtype.PLANT),
                ),
            )
            run(Effects.AddCountersToCollection(plants.key, Counters.PLUS_ONE_PLUS_ONE))
        }
        description = "Whenever a land you control enters, you may put a +1/+1 counter on each " +
            "Plant creature you control."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "96"
        artist = "Zoltan Boros & Gabor Szikszai"
        imageUri = "https://cards.scryfall.io/normal/front/d/d/dde073e0-f329-4ab0-8e31-a48929e017ce.jpg?1783942047"
    }
}
