package com.wingedsheep.mtg.sets.definitions.tmp.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Reanimate — Tempest #151. The target's graveyard mana value determines the life loss. */
val Reanimate = card("Reanimate") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Put target creature card from a graveyard onto the battlefield under your " +
        "control. You lose life equal to that card's mana value."

    spell {
        val creature = target(TargetFilter.CreatureInGraveyard)
        effect = Effects.Move(
            creature, Zone.BATTLEFIELD, controllerOverride = EffectTarget.Controller
        ) then Effects.LoseLife(DynamicAmounts.manaValueOf(creature), EffectTarget.Controller)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "151"
        artist = "Robert Bliss"
        flavorText = "\"You will learn to earn death.\"\n—Volrath"
        imageUri = "https://cards.scryfall.io/normal/front/a/e/ae1ef31c-8ca5-444c-8f39-e1d1827318f5.jpg?1783946636"
        ruling("2025-09-19", "The amount of life you lose is determined by the mana value of the card in your graveyard, not the creature once it's on the battlefield.")
        ruling("2025-09-19", "If a card in a graveyard has {X} in its mana cost, X is 0.")
        ruling("2025-09-19", "In a multiplayer game, if a player leaves the game, all cards that player owns leave as well. If you leave the game, the creature you control from Reanimate is exiled.")
        ruling("2025-09-19", "You lose life after the creature is already on the battlefield. Any abilities it has that interact with loss of life, such as that of Platinum Emperion, apply to that loss of life.")
        ruling("2025-09-19", "If any abilities trigger on the creature entering the battlefield, those abilities resolve after you lose life. If losing life results in you losing the game, those abilities won't resolve.")
    }
}
