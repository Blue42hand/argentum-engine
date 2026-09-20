package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.state.components.player.TokensCreatedThisTurnComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.CreateAdditionalToken
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Regression net for the token-creation replacement batch (CR 614 — "those tokens plus an
 * additional Food token are created instead" applies once per token-creation event, regardless
 * of how many tokens the event makes; CR 614.5 — the added token is created directly and is
 * self-limiting, so it doesn't re-trigger the replacement).
 *
 * Guards the CreateTokenExecutor fix where applyAdditionalTokenReplacements was invoked twice for
 * a single custom CreateTokenEffect batch, so a multi-token creation added the extra Food *twice*.
 * Predefined-token creations (Food/Treasure/Map) route through CreatePredefinedTokenExecutor and
 * were unaffected — which is why a single-Treasure path like Worldwalker Helm stayed green even
 * with the bug. Only a custom multi-token CreateTokenEffect surfaces the regression, so that is
 * exactly what this exercises, with inline cards so it has no set dependency.
 */
class CreateAdditionalTokenBatchScenarioTest : ScenarioTestBase() {

    // "If one or more tokens would be created under your control, those tokens plus an additional
    // Food token are created instead." (Peregrin Took's replacement clause, on its own.)
    private val additionalFoodMaker = card("Additional Food Maker") {
        manaCost = "{2}{G}"
        typeLine = "Legendary Creature — Halfling"
        power = 2
        toughness = 3
        replacementEffect(CreateAdditionalToken(additionalTokenType = "Food"))
    }

    // "Create two 1/1 Soldier creature tokens." A custom CreateTokenEffect batch of two — the exact
    // shape that double-added under the bug. (Rally at the Hornburg, simplified.)
    private val makeTwoSoldiers = card("Make Two Soldiers") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell { effect = Effects.CreateToken(power = 1, toughness = 1, creatureTypes = setOf("Soldier"), count = 2) }
    }

    init {
        cardRegistry.register(additionalFoodMaker)
        cardRegistry.register(makeTwoSoldiers)

        context("additional-token replacement on a custom multi-token batch") {

            test("creating two tokens adds exactly one Food — the replacement fires once per event, not per token") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Additional Food Maker")
                    .withCardInHand(1, "Make Two Soldiers")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Make Two Soldiers")
                game.resolveStack()

                withClue("Both primary Soldier tokens are created") {
                    game.findPermanents("Soldier Token").size shouldBe 2
                }
                withClue("Exactly one additional Food — not two (the pre-fix bug double-added per batch)") {
                    game.findPermanents("Food").size shouldBe 1
                }
                withClue("The successful replacement result records all three actually-created tokens") {
                    game.state.getEntity(game.player1Id)
                        ?.get<TokensCreatedThisTurnComponent>()?.count shouldBe 3
                }
            }
        }
    }
}
