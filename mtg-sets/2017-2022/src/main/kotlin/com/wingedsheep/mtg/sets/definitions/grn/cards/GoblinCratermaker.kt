package com.wingedsheep.mtg.sets.definitions.grn.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Goblin Cratermaker — Guilds of Ravnica #103. */
val GoblinCratermaker = card("Goblin Cratermaker") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Goblin Warrior"
    power = 2
    toughness = 2
    oracleText = "{1}, Sacrifice this creature: Choose one —\n" +
        "• This creature deals 2 damage to target creature.\n" +
        "• Destroy target colorless nonland permanent."

    val colorlessNonlandPermanent = TargetPermanent(
        filter = TargetFilter(
            GameObjectFilter(
                cardPredicates = listOf(
                    CardPredicate.IsPermanent,
                    CardPredicate.IsNonland,
                    CardPredicate.IsColorless,
                ),
            ),
        ),
    )

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.SacrificeSelf)
        effect = ModalEffect.chooseOne(
            Mode.withTarget(
                Effects.DealDamage(2, EffectTarget.ContextTarget(0)),
                com.wingedsheep.sdk.dsl.Targets.Creature,
                "This creature deals 2 damage to target creature",
            ),
            Mode.withTarget(
                Effects.Destroy(EffectTarget.ContextTarget(0)),
                colorlessNonlandPermanent,
                "Destroy target colorless nonland permanent",
            ),
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "103"
        artist = "Svetlin Velinov"
        imageUri = "https://cards.scryfall.io/normal/front/8/6/86ecaedc-08f1-4de7-aae8-056df57940e0.jpg?1783934162"
    }
}
