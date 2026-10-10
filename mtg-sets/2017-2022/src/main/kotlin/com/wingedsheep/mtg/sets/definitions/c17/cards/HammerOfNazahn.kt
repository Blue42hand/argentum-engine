package com.wingedsheep.mtg.sets.definitions.c17.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val HammerOfNazahn = card("Hammer of Nazahn") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Legendary Artifact — Equipment"
    oracleText = "Whenever Hammer of Nazahn or another Equipment you control enters, you may attach that Equipment to target creature you control.\nEquipped creature gets +2/+0 and has indestructible.\nEquip {4}"

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Artifact.withSubtype(Subtype.EQUIPMENT).youControl()).enters()
        optional = true
        val creature = target(TargetFilter.Creature.youControl())
        effect = Effects.AttachTargetEquipmentToCreature(EffectTarget.TriggeringEntity, creature)
        description = "You may attach the entering Equipment to target creature you control"
    }

    staticAbility { ability = ModifyStats(2, 0, Filters.EquippedCreature) }
    staticAbility { ability = GrantKeyword(Keyword.INDESTRUCTIBLE, Filters.EquippedCreature) }
    equipAbility("{4}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "51"
        artist = "Victor Adame Minguez"
        imageUri = "https://cards.scryfall.io/normal/front/f/a/fa055f1f-be94-4682-8e4f-f022c95e6d58.jpg?1783935932"
        ruling("2020-08-07", "If Hammer of Nazahn enters the battlefield at the same time as other Equipment you control, its ability will trigger for each of those Equipment.")
        ruling("2020-08-07", "Moving Hammer to another creature can make damage already marked on the formerly equipped creature lethal.")
    }
}
