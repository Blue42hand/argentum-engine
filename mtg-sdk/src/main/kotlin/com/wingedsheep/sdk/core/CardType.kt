package com.wingedsheep.sdk.core

import kotlinx.serialization.Serializable

@Serializable
enum class CardType(val displayName: String) {
    CREATURE("Creature"),
    SORCERY("Sorcery"),
    INSTANT("Instant"),
    ENCHANTMENT("Enchantment"),
    ARTIFACT("Artifact"),
    LAND("Land"),
    PLANESWALKER("Planeswalker"),
    KINDRED("Kindred"),  // Replaces "Tribal" - allows non-creature spells to have creature types
    VANGUARD("Vanguard"),  // Oversized avatar card; lives only in the command zone (Momir Basic)
    BATTLE("Battle");  // CR 310 — defense counters, a protector, and it can be attacked

    val isPermanent: Boolean
        get() = this in listOf(CREATURE, ENCHANTMENT, ARTIFACT, LAND, PLANESWALKER, BATTLE)

    companion object {
        /**
         * The card types offered by generic "choose a card type" effects in ordinary games.
         * Kept here so every chooser uses one typed, ordered vocabulary.
         *
         * This intentionally preserves the engine's pre-existing choice universe: the eight
         * ordinary game card types, excluding Kindred and Vanguard. Card text that names an
         * explicit subset should pass that subset instead.
         */
        val DEFAULT_CHOOSABLE_TYPES: List<CardType> = listOf(
            ARTIFACT, BATTLE, CREATURE, ENCHANTMENT, INSTANT, LAND, PLANESWALKER, SORCERY
        )

        fun fromString(value: String): CardType? =
            entries.find { it.displayName.equals(value, ignoreCase = true) }
    }
}
