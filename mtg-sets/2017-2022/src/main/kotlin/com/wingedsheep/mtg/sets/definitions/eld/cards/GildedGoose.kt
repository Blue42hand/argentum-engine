package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule

/** Gilded Goose — Throne of Eldraine #160. */
val GildedGoose = card("Gilded Goose") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Creature — Bird"
    power = 0
    toughness = 2
    oracleText = "Flying\n" +
        "When this creature enters, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\n" +
        "{1}{G}, {T}: Create a Food token.\n" +
        "{T}, Sacrifice a Food: Add one mana of any color."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateFood()
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}{G}"), Costs.Tap)
        effect = Effects.CreateFood()
    }

    activatedAbility {
        cost = Costs.Composite(
            Costs.Tap,
            Costs.Sacrifice(GameObjectFilter.Any.withSubtype("Food")),
        )
        effect = Effects.AddAnyColorMana()
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "160"
        artist = "Lindsey Look"
        imageUri = "https://cards.scryfall.io/normal/front/3/0/30377bf0-d9b1-4c14-8dde-f74b1e02d604.jpg?1783932611"
        ruling("2024-11-08", "If an effect refers to a Food, it means any Food artifact, not just a Food artifact token. For example, you can sacrifice Tough Cookie (an Artifact Creature — Food Golem) to activate Maraleaf Rider's ability (an ability with \"Sacrifice a Food\" in its cost).")
        ruling("2024-11-08", "You can't sacrifice a Food to pay multiple costs. For example, you can't sacrifice a Food token to activate its own ability and also to activate Maraleaf Rider's ability.")
        ruling("2024-11-08", "Food is an artifact type. Even though it appears on some creatures, it's never a creature type.")
    }
}

/** Extended-art Gilded Goose — Throne of Eldraine #369. */
val GildedGooseExtendedArt = Printing(
    oracleId = "f2f09757-1931-47c0-a5f0-39280445489d",
    name = "Gilded Goose",
    setCode = "ELD",
    collectorNumber = "369",
    scryfallId = "d274723a-27f3-49ec-ade0-d0b5e0e87d84",
    artist = "Lindsey Look",
    imageUri = "https://cards.scryfall.io/normal/front/d/2/d274723a-27f3-49ec-ade0-d0b5e0e87d84.jpg?1783932529",
    releaseDate = "2019-10-04",
    rarity = Rarity.RARE,
    frameEffects = listOf("extendedart"),
)
