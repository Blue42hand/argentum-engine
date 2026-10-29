package com.wingedsheep.mtg.sets.definitions.rtr.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.predicates.ControllerPredicate

/** Vandalblast — Return to Ravnica #111, its earliest printing. */
val Vandalblast = card("Vandalblast") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Destroy target artifact you don't control.\n" +
        "Overload {4}{R} (You may cast this spell for its overload cost. " +
        "If you do, change \"target\" in its text to \"each.\")"

    keywordAbility(KeywordAbility.overload("{4}{R}"))

    spell {
        // Teammates' artifacts also qualify: not controlled by you is broader than opponents'.
        val artifacts = GameObjectFilter.Artifact.withControllerPredicate(
            ControllerPredicate.Not(ControllerPredicate.ControlledByYou)
        )
        val artifact = target(TargetFilter(artifacts))
        effect = Effects.Destroy(artifact)
        overloadEffect = Effects.DestroyAll(artifacts)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "111"
        artist = "Seb McKinnon"
        flavorText = "Beauty is in the eye of the exploder."
        imageUri = "https://cards.scryfall.io/normal/front/5/9/5925c559-3e3c-481b-ba95-20a405cbffce.jpg?1783940352"
        ruling("2024-01-12", "Because a spell with overload doesn't target when its overload cost is paid, it may affect permanents with hexproof or with protection from the appropriate color.")
        ruling("2024-01-12", "If you are instructed to cast a spell with overload \"without paying its mana cost,\" you can't choose to pay its overload cost instead.")
        ruling("2024-01-12", "If you don't pay the overload cost of a spell with overload, that spell will have a single target. If you pay the overload cost, the spell won't have any targets.")
    }
}
