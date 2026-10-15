package com.wingedsheep.mtg.sets.definitions.rna.cards

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.effects.MayPlayExpiry

val LightUpTheStage = card("Light Up the Stage") {
    manaCost = "{2}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Spectacle {R} (You may cast this spell for its spectacle cost rather than its mana cost if an opponent lost life this turn.)\nExile the top two cards of your library. Until the end of your next turn, you may play those cards."

    selfAlternativeCost = SelfAlternativeCost(
        manaCost = ManaCost.parse("{R}"),
        condition = Conditions.OpponentLostLifeThisTurn
    )
    spell {
        effect = Patterns.Exile.impulse(2, MayPlayExpiry.UntilEndOfNextTurn)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "107"
        artist = "Dmitry Burmak"
        imageUri = "https://cards.scryfall.io/normal/front/9/2/9287b848-2aeb-4c70-ac4a-acafb871b7a4.jpg?1783933679"
        flavorText = "\"Places, everyone!\"\n—Judith"
        ruling("2024-01-12", "In a multiplayer game, if an opponent loses life and later that turn leaves the game, you can cast a spell for its spectacle cost. (If a player leaves the game during their turn, that turn continues without an active player.)")
        ruling("2024-01-12", "Spectacle doesn't change when you can cast the spell. For example, you can't cast a sorcery with spectacle during an opponent's turn unless another effect allows you to do so, even if that player has lost life this turn.")
        ruling("2024-01-12", "To determine the total cost of a spell, start with the mana cost or alternative cost you're paying (such as a spectacle cost), add any cost increases, then apply any cost reductions. The mana value of the spell remains unchanged, no matter what the total cost to cast it was.")
        ruling("2024-01-12", "A card's spectacle cost is the same no matter how much life your opponents lost or how many opponents lost life.")
        ruling("2024-01-12", "Damage dealt to a player causes that player to lose that much life.")
        ruling("2024-01-12", "Spectacle cares only that an opponent lost life during the turn, not that the opponent's life total is currently lower than it was. For example, if an opponent loses 1 life and then gains 2 life in the same turn, you can cast a spell for its spectacle cost that turn.")
        ruling("2019-01-25", "Casting an exiled card causes it to leave exile. You can't cast it multiple times.")
        ruling("2019-01-25", "If you don't play a card exiled this way, it remains in exile.")
        ruling("2019-01-25", "Light Up the Stage doesn't change when you can play the exiled cards. For example, if you exile a sorcery card, you can cast it only during your main phase when the stack is empty. If you exile a land card, you can play it only during your main phase and only if you have an available land play remaining.")
    }
}
