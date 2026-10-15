package com.wingedsheep.mtg.sets.definitions.khm.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.MayPlayExpiry
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val ShowdownOfTheSkalds = card("Showdown of the Skalds") {
    manaCost = "{2}{R}{W}"
    colorIdentity = "RW"
    typeLine = "Enchantment — Saga"
    oracleText = "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — Exile the top four cards of your library. Until the end of your next turn, you may play those cards.\nII, III — Whenever you cast a spell this turn, put a +1/+1 counter on target creature you control."

    sagaChapter(1) {
        effect = Patterns.Exile.impulse(4, MayPlayExpiry.UntilEndOfNextTurn)
    }
    for (chapter in 2..3) {
        sagaChapter(chapter) {
            effect = Effects.CreateDelayedTrigger(trigger = Triggers.you.casts(), fireOnce = false) {
                val creature = target(TargetFilter.CreatureYouControl)
                effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, creature)
            }
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "229"
        artist = "Steven Belledin"
        imageUri = "https://cards.scryfall.io/normal/front/3/d/3d9d840e-1f13-44e3-a4de-903cfa58a346.jpg?1783928190"
        ruling("2021-02-05", "Removing lore counters won’t cause a previous chapter ability to trigger. If lore counters are removed from a Saga, the appropriate chapter abilities will trigger again when the Saga receives more lore counters.")
        ruling("2021-02-05", "The cards you exile because of the chapter I ability are exiled face up.")
        ruling("2021-02-05", "Playing a card this way follows the normal rules for playing the card. You must pay its costs, and you must follow all applicable timing rules. For example, if one of the cards is a sorcery card, you can cast it only during your main phase while the stack is empty.")
        ruling("2021-02-05", "Under normal circumstances, you can play a land from among the exiled cards only if you haven’t played a land yet that turn.")
        ruling("2021-02-05", "Any cards you don’t play by the end of your next turn will remain exiled.")
        ruling("2021-02-05", "As a Saga enters the battlefield, its controller puts a lore counter on it. As your precombat main phase begins (immediately after your draw step), you put another lore counter on each Saga you control. Putting a lore counter on a Saga in either of these ways doesn’t use the stack.")
        ruling("2021-02-05", "Each symbol on the left of a Saga’s text box represents a chapter ability. A chapter ability is a triggered ability that triggers when a lore counter that is put on the Saga causes the number of lore counters on the Saga to become equal to or greater than the ability’s chapter number. Chapter abilities are put onto the stack and may be responded to.")
        ruling("2021-02-05", "A chapter ability doesn’t trigger if a lore counter is put on a Saga that already had a number of lore counters greater than or equal to that chapter’s number. For example, the third lore counter put on a Saga causes the chapter III ability to trigger, but chapters I and II won’t trigger again.")
        ruling("2021-02-05", "Once a chapter ability has triggered, the ability on the stack won’t be affected if the Saga gains or loses counters, or if it leaves the battlefield.")
        ruling("2021-02-05", "If multiple chapter abilities trigger at the same time, their controller puts them on the stack in any order. If any of them require targets, those targets are chosen as you put the abilities on the stack, before any of those abilities resolve.")
        ruling("2021-02-05", "Once the number of lore counters on a Saga is greater than or equal to the greatest number among its chapter abilities, the Saga’s controller sacrifices it as soon as its chapter ability has left the stack, most likely by resolving or being countered. This state-based action doesn’t use the stack.")
    }
}
