package com.wingedsheep.mtg.sets.definitions.tdc.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.Recipient
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.*

val TempestTechnique = card("Tempest Technique") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment — Aura"
    oracleText = "Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies. Copies become tokens.)\nEnchant creature you control\nEnchanted creature gets +1/+1 for each enchantment you control."

    keywords(Keyword.STORM)
    auraTarget = TargetObject(filter = TargetFilter.CreatureYouControl)
    staticAbility {
        val count = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Enchantment).count()
        ability = GrantDynamicStats(filter = GroupFilter.attachedCreature(), powerBonus = count, toughnessBonus = count)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "16"
        artist = "Nino Vecia"
        imageUri = "https://cards.scryfall.io/normal/front/5/d/5d5ee7cc-4854-4b52-ba9b-64d5a401fca1.jpg?1783907183"
        ruling("2025-04-04", "The copies are put directly onto the stack. They aren’t cast and won’t be counted by other spells with storm cast later in the turn.")
        ruling("2025-04-04", "Spells cast from zones other than a player’s hand and spells that were countered or otherwise failed to resolve are counted by the storm ability.")
        ruling("2025-04-04", "A copy of a spell can be countered like any other spell, but it must be countered individually. Countering a spell with storm won’t affect the copies.")
        ruling("2025-04-04", "The triggered ability that creates the copies can itself be countered by anything that can counter a triggered ability. If it is countered, no copies will be put onto the stack.")
        ruling("2025-04-04", "A resolving copy of a permanent spell becomes a token. That token isn’t “created” and won’t interact with abilities that care about tokens being created.")
    }
}
