package com.wingedsheep.mtg.sets.definitions.mom.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyCounterPlacement
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.events.RecipientFilter

/** Kami of Whispered Hopes — March of the Machine #196. */
val KamiOfWhisperedHopes = card("Kami of Whispered Hopes") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Spirit"
    power = 1
    toughness = 1
    oracleText = "If one or more +1/+1 counters would be put on a permanent you control, that many plus one +1/+1 counters are put on that permanent instead.\n" +
        "{T}: Add X mana of any one color, where X is this creature's power."

    replacementEffect(
        ModifyCounterPlacement(
            modifier = 1,
            appliesTo = EventPattern.CounterPlacementEvent(
                counterType = CounterTypeFilter.PlusOnePlusOne,
                recipient = RecipientFilter.Matching(GameObjectFilter.Permanent.youControl()),
            ),
        )
    )

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddAnyColorMana(DynamicAmounts.sourcePower())
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "196"
        artist = "Filipe Pagliuso"
        flavorText = "Nashi knelt reverently before the kami and envisioned his family reunited."
        imageUri = "https://cards.scryfall.io/normal/front/5/c/5c644650-3861-4a78-9e39-a413b073ddac.jpg?1783916966"
        ruling("2023-04-14", "If you control two Kamis of Whispered Hopes, the number of +1/+1 counters put on a permanent is two plus the original number. Three Kamis of Whispered Hopes add three, and so on.")
        ruling("2023-04-14", "If another permanent you control would enter the battlefield with a number of +1/+1 counters on it, it enters with that many plus one instead.")
        ruling("2023-04-14", "If two or more effects attempt to modify how many counters would be put onto a permanent you control, you choose the order to apply those effects, no matter who controls the sources of those effects.")
        ruling("2023-04-14", "The last ability is a mana ability. It doesn't use the stack and can't be responded to.")
        ruling("2023-04-14", "However, if Kami of Whispered Hopes somehow enters the battlefield with +1/+1 counters it, its first ability won't apply to itself.")
    }
}
