package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.handlers.effects.BattlefieldEntry
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.TokensCreatedThisTurnComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

class TokenCreationTurnTrackingTest : ScenarioTestBase() {

    private fun createdCondition(game: TestGame, playerId: EntityId): Boolean =
        ConditionEvaluator().evaluate(
            game.state,
            Conditions.YouCreatedTokensThisTurn,
            EffectContext(sourceId = null, controllerId = playerId),
        )

    init {
        test("creation is credited to the creator, persists after departure, and not the controller") {
            val game = scenario().withPlayers("Creator", "Controller").build()
            createdCondition(game, game.player1Id) shouldBe false

            val (tokenId, allocated) = game.state.newEntity()
            val token = ComponentContainer.of(
                CardComponent(
                    cardDefinitionId = "token:test",
                    name = "Test Token",
                    manaCost = ManaCost.ZERO,
                    typeLine = TypeLine.parse("Creature — Test"),
                    ownerId = game.player1Id,
                ),
                TokenComponent,
                ControllerComponent(game.player2Id),
                EnteredThisTurnComponent,
            )
            game.state = allocated.withEntity(tokenId, token)
            game.state = BattlefieldEntry.place(
                game.state,
                controllerId = game.player2Id,
                entityId = tokenId,
                tokenCreatorId = game.player1Id,
            )

            game.state.getEntity(game.player1Id)
                ?.get<TokensCreatedThisTurnComponent>()?.count shouldBe 1
            createdCondition(game, game.player1Id) shouldBe true
            createdCondition(game, game.player2Id) shouldBe false

            game.state = ZoneTransitionService.moveToZone(
                game.state, tokenId, Zone.GRAVEYARD,
            ).state
            createdCondition(game, game.player1Id) shouldBe true
        }

        test("ordinary battlefield placement is not token creation") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .build()

            createdCondition(game, game.player1Id) shouldBe false
            createdCondition(game, game.player2Id) shouldBe false
        }

        test("creation history resets for every player at the next turn boundary") {
            val game = scenario().withPlayers("Player", "Opponent").build()
            game.state = game.state.updateEntity(game.player1Id) { container ->
                container.with(TokensCreatedThisTurnComponent(2))
            }.updateEntity(game.player2Id) { container ->
                container.with(TokensCreatedThisTurnComponent(1))
            }

            val nextTurn = TurnManager(cardRegistry).startTurn(game.state, game.player2Id)
            nextTurn.error shouldBe null
            nextTurn.state.getEntity(game.player1Id)
                ?.get<TokensCreatedThisTurnComponent>()?.count shouldBe 0
            nextTurn.state.getEntity(game.player2Id)
                ?.get<TokensCreatedThisTurnComponent>()?.count shouldBe 0
        }
    }
}
