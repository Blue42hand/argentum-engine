package com.wingedsheep.mtg.sets.definitions.avr.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CantBeBlockedBy
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ChampionOfLambholt = card("Champion of Lambholt") {
    manaCost = "{1}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Warrior"
    oracleText = "Creatures with power less than this creature's power can't block creatures you control.\nWhenever another creature you control enters, put a +1/+1 counter on this creature."
    power = 1
    toughness = 1

    staticAbility {
        ability = CantBeBlockedBy(
            blockerFilter = GameObjectFilter.Creature.powerLessThanEntity(EffectTarget.Self),
            filter = GroupFilter(GameObjectFilter.Creature.youControl())
        )
    }
    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl().notSourceItself()).enters()
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "171"
        artist = "Christopher Moeller"
        imageUri = "https://cards.scryfall.io/normal/front/e/8/e8ab9cd3-2faf-4500-a2ee-90b3a8d559c4.jpg?1783940670"
        ruling("2020-08-07", "Champion of Lambholt's first ability applies even if it isn't attacking.")
        ruling("2020-08-07", "The comparison of power is done only when blockers are declared. Decreasing the power of a blocking creature (or increasing the power of Champion of Lambholt) after this point won't cause any creature to stop blocking or become unblocked.")
    }
}
