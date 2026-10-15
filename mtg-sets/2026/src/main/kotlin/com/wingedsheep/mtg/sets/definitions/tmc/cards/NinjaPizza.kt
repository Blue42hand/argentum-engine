package com.wingedsheep.mtg.sets.definitions.tmc.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val NinjaPizza = card("Ninja Pizza") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "Foods you control have \"{T}, Sacrifice this artifact: Add one mana of any color.\"\nAt the beginning of your second main phase, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")"

    staticAbility {
        ability = GrantActivatedAbility(
            ability = ActivatedAbility(
                id = AbilityId.next(),
                cost = Costs.Composite(Costs.Tap, Costs.SacrificeSelf),
                effect = Effects.AddManaOfChoice(),
                isManaAbility = true,
                timing = TimingRule.ManaAbility
            ),
            filter = GroupFilter(GameObjectFilter.Any.withSubtype("Food").youControl())
        )
    }
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.POSTCOMBAT_MAIN)
        effect = Effects.CreateFood()
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "32"
        artist = "Brian Yuen"
        imageUri = "https://cards.scryfall.io/normal/front/7/4/74c24ff9-d3d2-4ffb-bf8b-dba803481a99.jpg?1783904164"
        flavorText = "The pizza that vanishes quickly without a trace."
        ruling("2026-01-27", "Food is an artifact type. Even though it appears on some creatures, it's never a creature type.")
        ruling("2026-01-27", "Some spells and abilities that create Food tokens may require targets. If each target chosen is an illegal target as that spell or ability tries to resolve, it won't resolve. You won't create any Food tokens.")
        ruling("2026-01-27", "Whatever you do, don't eat the cards. (Also, don't make the pizzas pictured on the cards. You don't have the digestive system of a Ninja Turtle.)")
        ruling("2026-01-27", "If an effect refers to a Food, it means any Food artifact, not just a Food artifact token. For example, you can sacrifice Pizza Face (a Legendary Artifact Creature — Food Mutant) to activate Maraleaf Rider's ability (an ability with \"Sacrifice a Food\" in its cost). You can't sacrifice a Food to pay multiple costs. For example, you can't sacrifice a Food token to activate its own ability and also to activate Maraleaf Rider's ability.")
    }
}
