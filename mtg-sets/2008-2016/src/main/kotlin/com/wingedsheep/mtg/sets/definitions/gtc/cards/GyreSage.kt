package com.wingedsheep.mtg.sets.definitions.gtc.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.evolve
import com.wingedsheep.sdk.model.Rarity

val GyreSage = card("Gyre Sage") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Druid"
    oracleText = "Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)\n{T}: Add {G} for each +1/+1 counter on this creature."
    power = 1
    toughness = 2

    evolve()
    activatedAbility {
        cost = Costs.Tap
        manaAbility = true
        effect = Effects.AddMana(Color.GREEN, DynamicAmounts.countersOnSelf(CounterType.PLUS_ONE_PLUS_ONE))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "123"
        artist = "Tyler Jacobson"
        imageUri = "https://cards.scryfall.io/normal/front/6/3/6345376a-3d2d-4fff-9430-5e90a96e2f0f.jpg?1783940117"
        ruling("2013-04-15", "Whenever a creature enters the battlefield under your control, check its power and toughness against the power and toughness of the creature with evolve. If neither stat of the new creature is greater, evolve won't trigger at all.")
        ruling("2013-04-15", "When comparing the stats as the evolve ability resolves, it's possible that the stat that's greater changes from power to toughness or vice versa. If this happens, the ability will still resolve and you'll put a +1/+1 counter on the creature with evolve. For example, if you control a 2/2 creature with evolve and a 1/3 creature enters the battlefield under your control, it toughness is greater so evolve will trigger. In response, the 1/3 creature gets +2/-2. When the evolve trigger tries to resolve, its power is greater. You'll put a +1/+1 counter on the creature with evolve.")
        ruling("2013-04-15", "If evolve triggers, the stat comparison will happen again when the ability tries to resolve. If neither stat of the new creature is greater, the ability will do nothing. If the creature that entered the battlefield leaves the battlefield before evolve tries to resolve, use its last known power and toughness to compare the stats.")
        ruling("2013-04-15", "When comparing the stats of the two creatures for evolve, you always compare power to power and toughness to toughness.")
        ruling("2013-04-15", "If a creature enters the battlefield with +1/+1 counters on it, consider those counters when determining if evolve will trigger. For example, a 1/1 creature that enters the battlefield with two +1/+1 counters on it will cause the evolve ability of a 2/2 creature to trigger.")
        ruling("2013-04-15", "If multiple creatures enter the battlefield at the same time, evolve may trigger multiple times, although the stat comparison will take place each time one of those abilities tries to resolve. For example, if you control a 2/2 creature with evolve and two 3/3 creatures enter the battlefield, evolve will trigger twice. The first ability will resolve and put a +1/+1 counter on the creature with evolve. When the second ability tries to resolve, neither the power nor the toughness of the new creature is greater than that of the creature with evolve, so that ability does nothing.")
        ruling("2013-01-24", "Gyre Sage's last ability is a mana ability. It doesn't use the stack and can't be responded to.")
    }
}
