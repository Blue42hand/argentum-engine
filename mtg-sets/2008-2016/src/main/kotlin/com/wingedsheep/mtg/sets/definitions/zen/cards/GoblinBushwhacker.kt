package com.wingedsheep.mtg.sets.definitions.zen.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Goblin Bushwhacker — Zendikar #125. */
val GoblinBushwhacker = card("Goblin Bushwhacker") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Creature — Goblin Warrior"
    power = 1
    toughness = 1
    oracleText = "Kicker {R} (You may pay an additional {R} as you cast this spell.)\n" +
        "When this creature enters, if it was kicked, creatures you control get +1/+0 and gain " +
        "haste until end of turn."

    keywordAbility(KeywordAbility.kicker("{R}"))

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        interveningIf = WasKicked
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.youControl()),
            Effects.Composite(
                Effects.ModifyStats(1, 0, EffectTarget.Self),
                Effects.GrantKeyword(Keyword.HASTE, EffectTarget.Self),
            ),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "125"
        artist = "Mark Tedin"
        imageUri = "https://cards.scryfall.io/normal/front/4/0/4085a5bf-a71b-4c73-9b39-0dcc328fe11b.jpg?1783942145"
        ruling(
            "2009-10-01",
            "Only creatures you control as Goblin Bushwhacker's ability resolves are affected. This " +
                "includes Goblin Bushwhacker itself, unless it already left the battlefield by then.",
        )
    }
}
