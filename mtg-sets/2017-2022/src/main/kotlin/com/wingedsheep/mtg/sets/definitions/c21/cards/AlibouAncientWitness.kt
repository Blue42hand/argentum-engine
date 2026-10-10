package com.wingedsheep.mtg.sets.definitions.c21.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player

val AlibouAncientWitness = card("Alibou, Ancient Witness") {
    manaCost = "{3}{R}{W}"
    colorIdentity = "RW"
    typeLine = "Legendary Artifact Creature — Golem"
    power = 4
    toughness = 5
    oracleText = "Other artifact creatures you control have haste.\n" +
        "Whenever one or more artifact creatures you control attack, Alibou deals X damage to any target " +
        "and you scry X, where X is the number of tapped artifacts you control."

    staticAbility {
        ability = GrantKeyword(Keyword.HASTE, GroupFilter(GameObjectFilter.ArtifactCreature.youControl(), excludeSelf = true))
    }

    triggeredAbility {
        trigger = Triggers.you.attacks(GameObjectFilter.ArtifactCreature)
        val victim = target(Targets.Any)
        val tappedArtifacts = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Artifact.tapped()).count()
        val x = DynamicAmounts.storedNumber("alibou_x")
        effect = Effects.StoreNumber("alibou_x", tappedArtifacts) then
            Effects.DealDamage(x, victim) then Effects.Scry(x)
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "7"
        artist = "Matt Stewart"
        imageUri = "https://cards.scryfall.io/normal/front/f/d/fd2fc2d4-c4fb-4dcb-93fa-aaf8c1182f15.jpg?1783927611"
        ruling("2021-04-16", "If Alibou leaves the battlefield, artifact creatures you control lose haste. If they haven't been under your control since the turn began and don't otherwise have haste, they can't attack that turn. If they've already attacked, losing haste won't remove them from combat and they'll remain attackers.")
        ruling("2021-04-16", "You choose a target as Alibou's triggered ability is put on the stack. If that target isn't legal as the triggered ability tries to resolve, it doesn't resolve and you don't scry.")
        ruling("2021-04-16", "If you control no tapped artifacts when the triggered ability resolves (perhaps because they were destroyed or had vigilance), no damage will be dealt and you won't scry.")
    }
}
