package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.TokensCreatedThisTurnComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario coverage for Idol of Oblivion (C19 #55).
 *
 * The important card-specific rule is that the draw activation looks at turn history, not the
 * current battlefield. The qualifying token can have been created before Idol entered and can
 * already be gone. Token-creation provenance itself is tested at the engine layer; here we prove
 * that Idol consumes that persisted history as its activation restriction and that its sacrifice
 * ability creates the printed 10/10 colorless Eldrazi token.
 */
class IdolOfOblivionScenarioTest : ScenarioTestBase() {

    private val drawAbilityId by lazy {
        cardRegistry.getCard("Idol of Oblivion")!!.script.activatedAbilities[0].id
    }

    private val eldraziAbilityId by lazy {
        cardRegistry.getCard("Idol of Oblivion")!!.script.activatedAbilities[1].id
    }

    init {
        context("Idol of Oblivion") {

            test("draw ability is unavailable before you create a token this turn") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Idol of Oblivion")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(4) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(4) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                val idol = game.findPermanent("Idol of Oblivion")!!
                val handBefore = game.handSize(1)

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = idol,
                        abilityId = drawAbilityId,
                    )
                )

                withClue("The draw ability must fail closed without token-creation history") {
                    (result.error != null) shouldBe true
                    game.handSize(1) shouldBe handBefore
                }
            }

            test("2019-08-23 ruling — persisted token history enables the draw with no token remaining") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Idol of Oblivion")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(4) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(4) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                // Model a token created earlier this turn and already gone. There is deliberately no
                // token permanent on the battlefield; the condition must consume persisted history.
                game.state = game.state.updateEntity(game.player1Id) { container ->
                    container.with(TokensCreatedThisTurnComponent(1))
                }

                val idol = game.findPermanent("Idol of Oblivion")!!
                val handBefore = game.handSize(1)
                val libraryBefore = game.librarySize(1)

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = idol,
                        abilityId = drawAbilityId,
                    )
                )
                withClue("Persisted token creation should satisfy Idol's activation restriction") {
                    result.error shouldBe null
                }
                game.resolveStack()

                game.handSize(1) shouldBe handBefore + 1
                game.librarySize(1) shouldBe libraryBefore - 1
            }

            test("sacrifice ability creates a 10/10 colorless Eldrazi token") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Idol of Oblivion")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val idol = game.findPermanent("Idol of Oblivion")!!
                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = idol,
                        abilityId = eldraziAbilityId,
                    )
                )
                withClue("Eight mana should make Idol's sacrifice ability activatable") {
                    result.error shouldBe null
                }
                withClue("Idol is sacrificed as a cost before the ability resolves") {
                    game.isOnBattlefield("Idol of Oblivion") shouldBe false
                    game.isInGraveyard(1, "Idol of Oblivion") shouldBe true
                }

                game.resolveStack()

                val eldrazi = game.findPermanents("Eldrazi Token").single()
                withClue("The created permanent is a token") {
                    game.state.getEntity(eldrazi)?.has<TokenComponent>() shouldBe true
                }
                game.state.projectedState.getPower(eldrazi) shouldBe 10
                game.state.projectedState.getToughness(eldrazi) shouldBe 10
            }
        }
    }
}
