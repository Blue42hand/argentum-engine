package com.wingedsheep.mtg.sets.definitions.aer.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantKeywordToOwnSpells
import com.wingedsheep.sdk.scripting.GameObjectFilter

val InspiringStatuary = card("Inspiring Statuary") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Nonartifact spells you cast have improvise. (Your artifacts can help cast those spells. Each artifact you tap after you're done activating mana abilities pays for {1}.)"

    staticAbility {
        ability = GrantKeywordToOwnSpells(Keyword.IMPROVISE, spellFilter = GameObjectFilter.Nonartifact)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "160"
        artist = "Kirsten Zirngibl"
        imageUri = "https://cards.scryfall.io/normal/front/4/0/40e2564f-0066-41e0-a767-13ef33a17024.jpg?1783936725"
        flavorText = "Ghirapur's most celebrated inventors have been immortalized in clockwork."
        ruling("2017-02-09", "Improvise can't pay for {W}, {U}, {B}, {R}, {G}, or {C} mana symbols in a spell's total cost.")
        ruling("2017-02-09", "Improvise can't be used to pay for anything other than the cost of casting the spell. For example, it can't be used during the resolution of an ability that says \"Counter target spell unless its controller pays {3}.\"")
        ruling("2017-02-09", "When calculating a spell's total cost, include any alternative costs, additional costs, or anything else that increases or reduces the cost to cast the spell. Improvise applies after the total cost is calculated.")
        ruling("2017-02-09", "If you cast a nonartifact spell that requires you to sacrifice a permanent as an additional cost, you may tap that permanent (if it's an artifact) for the spell's improvise ability before you sacrifice it to pay that cost.")
        ruling("2017-02-09", "When using improvise to cast a spell with {X} in its mana cost, first choose the value for X. That choice, plus any cost increases or decreases, will determine the spell's total cost. Then you can tap artifacts you control to help pay that cost. For example, if you cast Whir of Invention (a spell with improvise and mana cost {X}{U}{U}{U}) and choose X to be 3, the total cost is {3}{U}{U}{U}. If you tap two artifacts, you'll have to pay {1}{U}{U}{U}.")
        ruling("2017-02-09", "Improvise doesn't change a spell's mana cost or mana value.")
        ruling("2017-02-09", "Multiple instances of improvise are redundant.")
        ruling("2017-02-09", "Equipment attached to a creature doesn't become tapped when that creature becomes tapped, and tapping that Equipment doesn't cause the creature to become tapped.")
        ruling("2017-02-09", "Because improvise isn't an alternative cost, it can be used in conjunction with alternative costs.")
        ruling("2017-02-09", "Tapping an artifact won't cause its abilities to stop applying unless those abilities say so.")
        ruling("2017-02-09", "If an artifact you control has a mana ability with {T} in the cost, activating that ability while casting a spell with improvise will result in the artifact being tapped when you pay the spell's costs. You won't be able to tap it again for improvise. Similarly, if you sacrifice an artifact to activate a mana ability while casting a spell with improvise, that artifact won't be on the battlefield when you pay the spell's costs, so you won't be able to tap it for improvise.")
    }
}
