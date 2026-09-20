package com.wingedsheep.mtg.sets.definitions.otc.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Savvy Trader
 * {3}{G}
 * Creature — Human Citizen
 * 3/3
 *
 * When this creature enters, exile target permanent card from your graveyard. You may play that
 * card for as long as it remains exiled.
 * Spells you cast from anywhere other than your hand cost {1} less to cast.
 */
val SavvyTrader = card("Savvy Trader") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Citizen"
    oracleText = "When this creature enters, exile target permanent card from your graveyard. " +
        "You may play that card for as long as it remains exiled.\n" +
        "Spells you cast from anywhere other than your hand cost {1} less to cast."
    power = 3
    toughness = 3

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val card = target(
            "target permanent card from your graveyard",
            TargetObject(filter = TargetFilter.PermanentInYourGraveyard),
        )
        effect = Effects.ExileAndGrantOwnerPlayPermission(card)
    }

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCastFromZones(
                zones = Zone.entries.toSet() - Zone.HAND,
                filter = GameObjectFilter.Any,
            ),
            modification = CostModification.ReduceGeneric(1),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "33"
        artist = "Scott Murphy"
        flavorText = "\"A little dirt doesn't indicate quality—with goods or with people.\""
        imageUri = "https://cards.scryfall.io/normal/front/1/1/118f25cf-faa6-4d60-a156-27bf7aedf7eb.jpg?1783911962"

        ruling("2024-04-12", "You may play the card exiled with Savvy Trader's first ability even " +
            "if Savvy Trader leaves the battlefield. If another player gains control of Savvy " +
            "Trader, that player can't play the card, and you still can.")
        ruling("2024-04-12", "You pay all costs and follow all normal timing rules for the card " +
            "played from exile with Savvy Trader's first ability. For example, if the exiled card " +
            "is a land card, you may play it only during your main phase while the stack is empty.")
        ruling("2024-04-12", "Savvy Trader's last ability doesn't change the mana cost or mana " +
            "value of any spell. It changes only the total cost you pay to cast spells from " +
            "anywhere other than your hand.")
        ruling("2024-04-12", "Savvy Trader's last ability can't reduce the amount of colored mana " +
            "you pay for a spell. It reduces only the generic mana component of that spell's cost.")
    }
}

/** Extended-art Savvy Trader (OTC 69). */
val SavvyTraderExtendedArt = Printing(
    oracleId = "c1aba8c7-d940-4d5d-a176-68cc226906de",
    name = "Savvy Trader",
    setCode = "OTC",
    collectorNumber = "69",
    scryfallId = "8532635e-a5f3-465d-9aac-54e1ba25f3d4",
    artist = "Scott Murphy",
    imageUri = "https://cards.scryfall.io/normal/front/8/5/8532635e-a5f3-465d-9aac-54e1ba25f3d4.jpg?1783911951",
    releaseDate = "2024-04-19",
    rarity = Rarity.RARE,
    frameEffects = listOf("extendedart"),
)
