package com.wingedsheep.mtg.sets.definitions.c13.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.LoseAllAbilities
import com.wingedsheep.sdk.scripting.SetBasePowerToughnessStatic
import com.wingedsheep.sdk.scripting.TransformPermanent

/**
 * Darksteel Mutation — Commander 2013 #9
 *
 * The Aura's four continuous effects occupy their normal layers: type replacement, ability
 * removal plus indestructible, and base power/toughness. Supertypes and colors are untouched.
 */
val DarksteelMutation = card("Darksteel Mutation") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nEnchanted creature is an Insect artifact creature with base " +
        "power and toughness 0/1 and has indestructible, and it loses all other abilities, card " +
        "types, and creature types."

    auraTarget = Targets.Creature

    staticAbility {
        ability = TransformPermanent(
            setCardTypes = setOf("ARTIFACT", "CREATURE"),
            setSubtypes = setOf("Insect"),
        )
    }
    staticAbility { ability = SetBasePowerToughnessStatic(0, 1) }
    staticAbility { ability = GrantKeyword(Keyword.INDESTRUCTIBLE) }
    staticAbility { ability = LoseAllAbilities() }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "9"
        artist = "Daniel Ljunggren"
        flavorText = "Infinitely powerless."
        imageUri = "https://cards.scryfall.io/normal/front/d/f/df7d800b-0120-4036-81d7-dec60ccc8057.jpg?1783939691"
        ruling("2013-10-17", "The enchanted creature will be only an artifact and a creature, not " +
            "any other card types. It will be only an Insect, not any other creature types.")
        ruling("2013-10-17", "The creature will keep any supertypes it previously had. Notably, " +
            "if Darksteel Mutation is enchanting a legendary creature, that creature will " +
            "continue to be legendary. Also, if it's enchanting a commander, that creature will " +
            "continue to be a commander.")
        ruling("2013-10-17", "Darksteel Mutation causes the enchanted creature to lose all " +
            "abilities except indestructible at the time it becomes enchanted. Any abilities the " +
            "creature gains after that point will work normally.")
    }
}
