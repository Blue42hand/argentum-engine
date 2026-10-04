package com.wingedsheep.mtg.sets.definitions.eoc.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/** Surge Conductor (EOC #19). */
val SurgeConductor = card("Surge Conductor") {
    manaCost = "{3}"
    typeLine = "Artifact Creature — Robot"
    power = 3
    toughness = 2
    oracleText = "Whenever another nontoken artifact you control enters, proliferate. " +
        "(Choose any number of permanents and/or players, then give each another counter of each kind already there.)"

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Artifact.nontoken().youControl()).enters()
        effect = Effects.Proliferate()
        description = "Whenever another nontoken artifact you control enters, proliferate."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "19"
        artist = "Alexandr Leskinen"
        flavorText = "A conductor for an electric symphony."
        imageUri = "https://cards.scryfall.io/normal/front/6/8/686c005b-39c8-4c6c-bf5a-462774f1d6d9.jpg?1783906062"

        ruling("2025-07-25", "Players can respond to a spell or ability whose effect includes proliferating. Once that spell or ability starts to resolve, however, and its controller chooses which permanents and players will get new counters, it’s too late for anyone to respond.")
        ruling("2025-07-25", "If Surge Conductor and one or more other nontoken artifacts you control enter the battlefield at the same time, Surge Conductor’s ability will trigger for each of those nontoken artifacts.")
        ruling("2025-07-25", "When you proliferate, you can choose any permanent that has a counter, including ones controlled by opponents. You can choose any player who has a counter, including opponents. You can’t choose cards in any zone other than the battlefield, even if they have counters on them.")
        ruling("2025-07-25", "You don’t have to choose every permanent or player that has a counter—only the ones you want to add counters to. Since “any number” includes zero, you don’t have to choose any permanents at all, and you don’t have to choose any players at all.")
        ruling("2025-07-25", "If a permanent or player has more than one kind of counter on them, and you choose for that permanent or player to get additional counters, that permanent or player must get one of each kind of counter they already have. You can’t have them get just one kind of counter they already have and not the others.")
    }
}
