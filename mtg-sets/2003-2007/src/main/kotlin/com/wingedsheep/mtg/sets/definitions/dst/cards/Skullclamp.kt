package com.wingedsheep.mtg.sets.definitions.dst.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TriggerBinding

/**
 * Skullclamp — Darksteel #140
 * {1} · Artifact — Equipment
 *
 * Equipped creature gets +1/-1.
 * Whenever equipped creature dies, draw two cards.
 * Equip {1}
 *
 * The death trigger uses the same attached-object binding as Deathrender and Forebear's Blade.
 * That binding preserves the identity of the creature Skullclamp was attached to when it leaves
 * the battlefield, including when the -1 toughness from Skullclamp itself causes it to die.
 */
val Skullclamp = card("Skullclamp") {
    manaCost = "{1}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature gets +1/-1.\n" +
        "Whenever equipped creature dies, draw two cards.\n" +
        "Equip {1}"

    staticAbility {
        ability = ModifyStats(+1, -1, Filters.EquippedCreature)
    }

    triggeredAbility {
        trigger = Triggers.leavesBattlefield(to = Zone.GRAVEYARD, binding = TriggerBinding.ATTACHED)
        effect = Effects.DrawCards(2)
        description = "Whenever equipped creature dies, draw two cards."
    }

    equipAbility("{1}")

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "140"
        artist = "Luca Zontini"
        imageUri = "https://cards.scryfall.io/normal/front/5/5/55318397-de3c-47ea-a088-72a24df5c8fa.jpg"
    }
}
