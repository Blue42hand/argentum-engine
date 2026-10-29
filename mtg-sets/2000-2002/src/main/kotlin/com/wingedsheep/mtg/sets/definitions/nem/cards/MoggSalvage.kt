package com.wingedsheep.mtg.sets.definitions.nem.cards

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Mogg Salvage — the land condition gates casting, not resolution or artifact ownership. */
val MoggSalvage = card("Mogg Salvage") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "If an opponent controls an Island and you control a Mountain, you may cast this spell without paying its mana cost.\nDestroy target artifact."

    selfAlternativeCost = SelfAlternativeCost(
        manaCost = ManaCost.ZERO,
        condition = Conditions.All(
            Conditions.OpponentControls(Filters.IslandCard),
            Conditions.YouControl(Filters.MountainCard)
        )
    )

    spell {
        val artifact = target(TargetFilter.Artifact)
        effect = Effects.Destroy(artifact)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "94"
        artist = "Paolo Parente"
        flavorText = "Three moggs, one treasure, too bad."
        imageUri = "https://cards.scryfall.io/normal/front/4/0/403aa48c-b684-4c54-8863-460958055a1f.jpg?1783945818"
        ruling("2021-06-18", "To cast Mogg Salvage without paying its mana cost, any opponent may control an Island, not just the one who controls the target artifact.")
        ruling("2021-06-18", "Who controls what lands is important only as you cast Mogg Salvage. Once it has been cast, you no longer controlling a Mountain or each opponent no longer controlling any Islands won't affect Mogg Salvage.")
        ruling("2021-06-18", "Casting a spell without paying its mana cost has no effect on its mana value. Mogg Salvage's mana value is always 3.")
    }
}
