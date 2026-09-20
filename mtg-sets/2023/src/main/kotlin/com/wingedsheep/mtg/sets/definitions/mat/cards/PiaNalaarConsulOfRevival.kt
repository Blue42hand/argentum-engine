package com.wingedsheep.mtg.sets.definitions.mat.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.events.SpellCastPredicate
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Pia Nalaar, Consul of Revival — March of the Machine: The Aftermath #42.
 *
 * A spell cast from exile and a land played from exile are distinct game actions, so the printed
 * trigger is represented as two event atoms with the same token-creation payoff. This preserves the
 * land-play semantics: moving an exiled land onto the battlefield without playing it does not fire.
 */
val PiaNalaarConsulOfRevival = card("Pia Nalaar, Consul of Revival") {
    manaCost = "{R}{W}"
    colorIdentity = "RW"
    typeLine = "Legendary Creature — Human Artificer"
    power = 2
    toughness = 3
    oracleText = "Thopters you control have haste.\n" +
        "Whenever you play a land from exile or cast a spell from exile, create a 1/1 colorless " +
        "Thopter artifact creature token with flying."

    staticAbility {
        ability = GrantKeyword(
            keyword = Keyword.HASTE,
            filter = GroupFilter(
                GameObjectFilter.Creature.youControl().withSubtype("Thopter")
            ),
        )
    }

    triggeredAbility {
        trigger = Triggers.youPlayLand(fromZone = Zone.EXILE)
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            creatureTypes = setOf("Thopter"),
            keywords = setOf(Keyword.FLYING),
            artifactToken = true,
            imageUri = "https://cards.scryfall.io/normal/front/d/3/" +
                "d38fc294-ad86-441e-96fe-4ca286a11218.jpg?1783907677",
        )
        description = "Whenever you play a land from exile, create a 1/1 colorless Thopter " +
            "artifact creature token with flying."
    }

    triggeredAbility {
        trigger = Triggers.youCastSpell(
            requires = setOf(SpellCastPredicate.CastFromZone(Zone.EXILE))
        )
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            creatureTypes = setOf("Thopter"),
            keywords = setOf(Keyword.FLYING),
            artifactToken = true,
            imageUri = "https://cards.scryfall.io/normal/front/d/3/" +
                "d38fc294-ad86-441e-96fe-4ca286a11218.jpg?1783907677",
        )
        description = "Whenever you cast a spell from exile, create a 1/1 colorless Thopter " +
            "artifact creature token with flying."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "42"
        artist = "Marta Nael"
        flavorText = "Renegade turned revolutionary turned rebuilder."
        imageUri = "https://cards.scryfall.io/normal/front/0/a/" +
            "0ae89461-4bce-4b49-b875-03afc2469fe7.jpg?1783916510"

        ruling(
            "2023-05-12",
            "Once a Thopter that came under your control this turn has legally attacked, causing " +
                "it to lose haste by removing Pia Nalaar won't remove that Thopter from combat. " +
                "It will just keep attacking. Good Thopter.",
        )
    }
}
