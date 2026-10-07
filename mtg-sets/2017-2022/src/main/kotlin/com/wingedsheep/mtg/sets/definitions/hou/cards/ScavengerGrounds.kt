package com.wingedsheep.mtg.sets.definitions.hou.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Scavenger Grounds
 * Land — Desert
 * {T}: Add {C}.
 * {2}, {T}, Sacrifice a Desert: Exile all graveyards.
 */
val ScavengerGrounds = card("Scavenger Grounds") {
    manaCost = ""
    colorIdentity = ""
    typeLine = "Land — Desert"
    oracleText = "{T}: Add {C}.\n{2}, {T}, Sacrifice a Desert: Exile all graveyards."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(
            Costs.Mana("{2}"),
            Costs.Tap,
            Costs.Sacrifice(GameObjectFilter.Land.withSubtype(Subtype.DESERT)),
        )
        effect = Effects.Pipeline {
            val allGraveyards = gather(
                CardSource.FromZone(
                    zone = Zone.GRAVEYARD,
                    player = Player.Each,
                    filter = GameObjectFilter.Any,
                )
            )
            exile(allGraveyards)
        }
        description = "{2}, {T}, Sacrifice a Desert: Exile all graveyards."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "182"
        artist = "Steven Belledin"
        flavorText = "When the last scrap of flesh is scoured away, the Curse of Wandering ends. Then the dead may sleep."
        imageUri = "https://cards.scryfall.io/normal/front/6/c/6cd91eeb-7abf-4538-91dc-47c736dfc237.jpg"
        ruling("2017-07-14", "If a Desert has an ability with a cost of \"Sacrifice a Desert,\" you can sacrifice that Desert to pay the cost for its own ability.")
        ruling("2017-07-14", "The sacrificed Desert will be in your graveyard to be exiled by the last ability of Scavenger Grounds.")
        ruling("2017-04-18", "Desert is a land subtype with no special meaning. It doesn't grant the land an intrinsic mana ability. Other cards may care about which lands are Deserts.")
    }
}
