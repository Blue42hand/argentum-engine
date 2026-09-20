package com.wingedsheep.mtg.sets.definitions.m19.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Poison-Tip Archer — Core Set 2019 #220. */
val PoisonTipArcher = card("Poison-Tip Archer") {
    manaCost = "{2}{B}{G}"
    colorIdentity = "BG"
    typeLine = "Creature — Elf Archer"
    power = 2
    toughness = 3
    oracleText = "Reach (This creature can block creatures with flying.)\n" +
        "Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)\n" +
        "Whenever another creature dies, each opponent loses 1 life."

    keywords(Keyword.REACH, Keyword.DEATHTOUCH)

    triggeredAbility {
        trigger = Triggers.leavesBattlefield(
            filter = GameObjectFilter.Creature,
            to = Zone.GRAVEYARD,
            binding = TriggerBinding.OTHER,
        )
        effect = Effects.LoseLife(1, EffectTarget.PlayerRef(Player.EachOpponent))
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "220"
        artist = "Dmitry Burmak"
        imageUri = "https://cards.scryfall.io/normal/front/5/e/5e058ff8-043c-498b-8310-0ca45466ac27.jpg?1783934519"
        ruling(
            "2018-07-13",
            "If another creature dies at the same time as Poison-Tip Archer, each opponent loses 1 life.",
        )
        ruling(
            "2018-07-13",
            "In a Two-Headed Giant game, Poison-Tip Archer's last ability causes the opposing team to lose 2 life.",
        )
    }
}
