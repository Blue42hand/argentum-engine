package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Alela, Artful Provocateur — Throne of Eldraine #324
 *
 * The cast trigger uses one OR-filter, rather than two triggers, so an artifact-enchantment spell
 * creates exactly one Faerie. Like every cast trigger, it resolves before the spell that caused it
 * and remains on the stack if that spell is countered.
 */
val AlelaArtfulProvocateur = card("Alela, Artful Provocateur") {
    manaCost = "{1}{W}{U}{B}"
    colorIdentity = "WUB"
    typeLine = "Legendary Creature — Faerie Warlock"
    power = 2
    toughness = 3
    oracleText = "Flying, deathtouch, lifelink\n" +
        "Other creatures you control with flying get +1/+0.\n" +
        "Whenever you cast an artifact or enchantment spell, create a 1/1 blue Faerie creature " +
        "token with flying."

    keywords(Keyword.FLYING, Keyword.DEATHTOUCH, Keyword.LIFELINK)

    staticAbility {
        ability = ModifyStats(
            powerBonus = 1,
            toughnessBonus = 0,
            filter = GroupFilter(
                GameObjectFilter.Creature.withKeyword(Keyword.FLYING).youControl(),
                excludeSelf = true,
            ),
        )
    }

    triggeredAbility {
        trigger = Triggers.youCastSpell(
            spellFilter = GameObjectFilter.Artifact or GameObjectFilter.Enchantment,
        )
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            colors = setOf(Color.BLUE),
            creatureTypes = setOf("Faerie"),
            keywords = setOf(Keyword.FLYING),
            imageUri = "https://cards.scryfall.io/normal/front/b/c/bcd82cb0-ff4b-4f4d-b3d0-3ac53883b099.jpg?1783932483",
        )
        description = "Whenever you cast an artifact or enchantment spell, create a 1/1 blue " +
            "Faerie creature token with flying."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "324"
        artist = "Grzegorz Rutkowski"
        flavorText = "\"Rankle's pranks are child's play. My games will topple kingdoms.\""
        imageUri = "https://cards.scryfall.io/normal/front/7/2/726e7dc5-2089-4758-93e1-79212aedf75f.jpg?1783932550"
        ruling("2019-10-04", "Alela's last ability resolves before the spell that caused it to trigger. It resolves even if that spell is countered.")
        ruling("2019-10-04", "A spell that's both an artifact and an enchantment spell causes Alela's last ability to trigger only once.")
    }
}
