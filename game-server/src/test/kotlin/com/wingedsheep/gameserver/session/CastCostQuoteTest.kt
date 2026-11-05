package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.persistence.persistenceJson
import com.wingedsheep.gameserver.protocol.ClientMessage
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession

class CastCostQuoteTest : ScenarioTestBase() {
    private data class Fixture(
        val game: GameSession,
        val player: EntityId,
        val opponent: EntityId,
        val request: ClientMessage.RequestCastCostQuote,
    )

    private fun fixture(): Fixture {
        val board = scenario().withPlayers("A", "B").withActivePlayer(1)
            .withCardInHand(1, "Grizzly Bears").withCardInHand(2, "Hill Giant")
            .withLandsOnBattlefield(1, "Forest", 5)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
        fun seat(id: EntityId) = PlayerSession(
            mockk<WebSocketSession>(relaxed = true) { every { this@mockk.id } returns "socket-${id.value}" },
            id, id.value,
        )
        val game = GameSession(cardRegistry = cardRegistry, evidenceSink = null)
        game.injectStateForTesting(board.state, listOf(board.player1Id, board.player2Id).associateWith(::seat))
        val origin = game.createStateUpdate(board.player1Id, emptyList()).shouldBeInstanceOf<ServerMessage.StateUpdate>()
        val action = origin.legalActions.map { it.action }.filterIsInstance<CastSpell>().first()
        return Fixture(game, board.player1Id, board.player2Id,
            ClientMessage.RequestCastCostQuote("quote-1", origin.stateVersion, origin.interactionEpoch!!, action))
    }

    init {
        test("current masked cast quote echoes origin without changing state or delivery version") {
            val f = fixture()
            val before = f.game.getStateForTesting()
            val beforeActivity = f.game.lastActionAt
            val first = f.game.quoteCastCost(f.player, f.request)
            first.error.shouldBeNull()
            first.manaCostString shouldBe "{1}{G}"
            first.delveGenericCap shouldBe 1
            first.requestId shouldBe f.request.requestId
            first.interactionEpoch shouldBe f.request.interactionEpoch
            first.stateVersion shouldBe f.request.stateVersion
            f.game.quoteCastCost(f.player, f.request) shouldBe first
            f.game.getStateForTesting() shouldBe before
            f.game.lastActionAt shouldBe beforeActivity
            val update = f.game.createStateUpdate(f.player, emptyList())
                .shouldBeInstanceOf<ServerMessage.StateDeltaUpdate>()
            update.stateVersion shouldBe f.request.stateVersion + 1
        }

        test("stale epoch and version return only unavailable without touching state") {
            val f = fixture()
            val before = f.game.getStateForTesting()
            for (request in listOf(f.request.copy(interactionEpoch = "expired"),
                f.request.copy(stateVersion = f.request.stateVersion - 1))) {
                val quote = f.game.quoteCastCost(f.player, request)
                quote.error.shouldNotBeNull()
                quote.manaCostString.shouldBeNull()
                quote.delveGenericCap.shouldBeNull()
                quote.tapForGenericCostString.shouldBeNull()
            }
            f.game.getStateForTesting() shouldBe before
        }

        test("state revision change invalidates a quote even before another snapshot is delivered") {
            val f = fixture()
            f.game.executeAction(f.player, PassPriority(f.player))
            val afterAction = f.game.getStateForTesting()
            f.game.quoteCastCost(f.player, f.request).error.shouldNotBeNull()
            f.game.getStateForTesting() shouldBe afterAction
        }

        test("a different authenticated seat cannot quote another player's action") {
            val f = fixture()
            val other = f.game.createStateUpdate(f.opponent, emptyList()).shouldBeInstanceOf<ServerMessage.StateUpdate>()
            val request = f.request.copy(stateVersion = other.stateVersion, interactionEpoch = other.interactionEpoch!!)
            f.game.quoteCastCost(f.opponent, request).manaCostString.shouldBeNull()
            f.game.quoteCastCost(EntityId("unregistered-seat"), request).manaCostString.shouldBeNull()
            f.game.quoteCastCost(f.player, f.request, connectionId = "superseded-socket").manaCostString.shouldBeNull()
        }

        test("opponent hidden card and unoffered cast variant never return a price") {
            val f = fixture()
            val action = f.request.action as CastSpell
            val hidden = f.game.getStateForTesting()!!.getZone(ZoneKey(f.opponent, Zone.HAND)).single()
            for (forged in listOf(action.copy(cardId = hidden), action.copy(useWithoutPayingManaCost = true))) {
                val quote = f.game.quoteCastCost(f.player, f.request.copy(action = forged))
                quote.error shouldBe "Cast cost quote is stale or unavailable"
                quote.manaCostString.shouldBeNull()
            }
        }

        test("noncast requests and invalid request identifiers do not execute actions") {
            val f = fixture()
            val before = f.game.getStateForTesting()
            for (request in listOf(f.request.copy(action = PassPriority(f.player)),
                f.request.copy(requestId = ""), f.request.copy(requestId = "x".repeat(129)))) {
                f.game.quoteCastCost(f.player, request).error.shouldNotBeNull()
            }
            f.game.getStateForTesting() shouldBe before
        }

        test("offer reads and quote admission follow each installed snapshot without changing it") {
            val f = fixture()
            val original = f.game.getStateForTesting()!!
            val players = f.game.getPlayers().associateBy { it.playerId }
            val hand = ZoneKey(f.player, Zone.HAND)
            val card = original.getZone(hand).single()
            val withoutSpell = original.removeFromZone(hand, card)
            for (snapshot in listOf(withoutSpell, original, withoutSpell, original)) {
                f.game.injectStateForTesting(snapshot, players)
                f.game.getLegalActions(f.player).any { it.action is CastSpell } shouldBe
                    (card in snapshot.getZone(hand))
                // A new full seat view binds the offer and quote to this exact snapshot.
                f.game.clearLastSentState(f.player)
                val origin = f.game.createStateUpdate(f.player, emptyList())
                    .shouldBeInstanceOf<ServerMessage.StateUpdate>()
                val action = origin.legalActions.firstNotNullOfOrNull { it.action as? CastSpell }
                if (action != null) {
                    f.game.quoteCastCost(f.player, ClientMessage.RequestCastCostQuote(
                        "snapshot-quote", origin.stateVersion, origin.interactionEpoch!!, action,
                    )).manaCostString shouldBe "{1}{G}"
                } else {
                    f.game.quoteCastCost(f.player, f.request.copy(
                        stateVersion = origin.stateVersion, interactionEpoch = origin.interactionEpoch!!,
                    )).manaCostString.shouldBeNull()
                }
                f.game.getStateForTesting() shouldBe snapshot
            }
        }

        test("quote envelopes round trip through the existing polymorphic transport") {
            val f = fixture()
            val request = persistenceJson.decodeFromString(ClientMessage.serializer(),
                persistenceJson.encodeToString(ClientMessage.serializer(), f.request))
            request shouldBe f.request
            val response = f.game.quoteCastCost(f.player, f.request)
            persistenceJson.decodeFromString(ServerMessage.serializer(),
                persistenceJson.encodeToString(ServerMessage.serializer(), response)) shouldBe response
        }
    }
}
