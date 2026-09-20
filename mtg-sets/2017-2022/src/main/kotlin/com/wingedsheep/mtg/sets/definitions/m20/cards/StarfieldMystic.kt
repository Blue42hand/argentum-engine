package com.wingedsheep.mtg.sets.definitions.m20.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Starfield Mystic
 * {1}{W}
 * Creature — Human Cleric
 * 2/2
 *
 * Enchantment spells you cast cost {1} less to cast.
 * Whenever an enchantment you control is put into a graveyard from the battlefield, put a +1/+1
 * counter on this creature.
 */
val StarfieldMystic = card("Starfield Mystic") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Cleric"
    oracleText = "Enchantment spells you cast cost {1} less to cast.\n" +
        "Whenever an enchantment you control is put into a graveyard from the battlefield, put a " +
        "+1/+1 counter on this creature."
    power = 2
    toughness = 2

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Enchantment),
            modification = CostModification.ReduceGeneric(1),
        )
    }

    triggeredAbility {
        trigger = Triggers.leavesBattlefield(
            filter = GameObjectFilter.Enchantment.youControl(),
            to = Zone.GRAVEYARD,
            binding = TriggerBinding.ANY,
        )
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "39"
        artist = "Eric Deschamps"
        flavorText = "\"The realm of the gods opens before me!\""
        imageUri = "https://cards.scryfall.io/normal/front/8/0/80382963-a9d7-4c2d-8671-8dd3fdd4dbdc.jpg?1783933019"
        ruling("2019-07-12", "If you cast an Aura spell targeting an opponent's permanent, you still control the Aura after it resolves.")
    }
}
