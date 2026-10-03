package com.wingedsheep.gameserver.session

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession

/**
 * Phase 2 — the session/DTO/spectator layer must be seat-count agnostic. These tests drive a real
 * N-player [GameSession] (no WebSocket plumbing) and assert the N-player accessors, per-recipient
 * masking, the seat roster, and that 2-player is the degenerate case of the same code path.
 */
class MultiplayerSessionTest : ScenarioTestBase() {

    private fun mockWs(id: String): WebSocketSession =
        mockk(relaxed = true) { every { this@mockk.id } returns id }

    /** Build and start a session with [count] seats, each with a 40-Forest deck. */
    private fun startedSession(count: Int, debugMode: Boolean = false): Pair<GameSession, List<EntityId>> {
        val session = GameSession(cardRegistry = cardRegistry, maxPlayers = count, debugMode = debugMode)
        val ids = (1..count).map { EntityId.of("player-$it") }
        ids.forEachIndexed { i, id ->
            session.addPlayer(PlayerSession(mockWs("ws$i"), id, "Player${i + 1}"), mapOf("Forest" to 40))
        }
        session.startGame()
        return session to ids
    }

    init {
        test("a four-player session seats all four players and reports three opponents each") {
            val (session, ids) = startedSession(4)

            session.getPlayers().map { it.playerId } shouldContainExactlyInAnyOrder ids
            for (id in ids) {
                session.getOpponentIds(id) shouldContainExactlyInAnyOrder ids.filter { it != id }
            }
        }

        test("seat roster is the turn order with the viewer's own seat flagged") {
            val (session, ids) = startedSession(4)

            val roster = session.seatInfos(viewerId = ids[0])
            roster shouldHaveSize 4
            roster.map { it.seatIndex } shouldBe listOf(0, 1, 2, 3)
            roster.map { it.playerId } shouldContainExactlyInAnyOrder ids.map { it.value }
            roster.count { it.isYou } shouldBe 1
            roster.single { it.isYou }.playerId shouldBe ids[0].value
            roster.none { it.isAi } shouldBe true
        }

        test("each recipient sees only their own hand; every opponent hand is masked") {
            val (session, ids) = startedSession(4)

            for (viewer in ids) {
                val update = session.createStateUpdate(viewer, emptyList())
                val state = (update as ServerMessage.StateUpdate).state
                val handZones = state.zones.filter { it.zoneId.zoneType == Zone.HAND }
                handZones shouldHaveSize 4
                for (zone in handZones) {
                    if (zone.zoneId.ownerId == viewer) {
                        zone.isVisible shouldBe true
                        zone.cardIds shouldHaveSize 7
                    } else {
                        // Opponent hand: count preserved, contents hidden.
                        zone.isVisible shouldBe false
                        zone.cardIds shouldHaveSize 0
                        zone.size shouldBe 7
                    }
                }
            }
        }

        test("a two-seat AI update masks a nonempty opponent hand even in local debug mode") {
            val (session, ids) = startedSession(2, debugMode = true)
            val ai = ids[0]
            val opponent = ids[1]

            // Prove this is the local debug configuration that exposed the recorded hand.
            val debugState = (session.createStateUpdate(ai, emptyList()) as ServerMessage.StateUpdate).state
            val debugHand = debugState.zones.single {
                it.zoneId.zoneType == Zone.HAND && it.zoneId.ownerId == opponent
            }
            debugHand.cardIds shouldHaveSize 7

            session.clearLastSentState(ai)
            // GamePlayHandler derives this flag from AiWebSocketSession for broadcasts and resync.
            val aiState = (session.createStateUpdate(
                ai, emptyList(), useEngineDecisionIds = true
            ) as ServerMessage.StateUpdate).state
            val ownHand = aiState.zones.single {
                it.zoneId.zoneType == Zone.HAND && it.zoneId.ownerId == ai
            }
            val opponentHand = aiState.zones.single {
                it.zoneId.zoneType == Zone.HAND && it.zoneId.ownerId == opponent
            }
            ownHand.cardIds shouldHaveSize 7
            opponentHand.size shouldBe 7
            opponentHand.cardIds shouldHaveSize 0
            opponentHand.isVisible shouldBe false
            aiState.cards.keys.none { it in debugHand.cardIds } shouldBe true
        }

        test("spectator state carries the full seat roster and masks all hands") {
            val (session, ids) = startedSession(4)

            val spectator = session.buildSpectatorState()!!
            spectator.players shouldHaveSize 4
            spectator.players.map { it.playerId } shouldContainExactlyInAnyOrder ids.map { it.value }

            // Every hand is hidden from spectators.
            val handZones = spectator.gameState!!.zones.filter { it.zoneId.zoneType == Zone.HAND }
            handZones.forEach {
                it.isVisible shouldBe false
                it.cardIds shouldHaveSize 0
            }
        }

        test("a debug session never exposes sideboards to a spectator") {
            val session = GameSession(cardRegistry = cardRegistry, maxPlayers = 2, debugMode = true)
            val ids = listOf(EntityId.of("player-1"), EntityId.of("player-2"))
            ids.forEachIndexed { i, id ->
                session.addPlayer(
                    PlayerSession(mockWs("ws$i"), id, "Player${i + 1}"),
                    mapOf("Forest" to 40),
                    sideboard = mapOf("Mountain" to 2),
                )
            }
            session.startGame()

            val spectator = session.buildSpectatorState()!!.gameState!!
            val sideboards = spectator.zones.filter { it.zoneId.zoneType == Zone.SIDEBOARD }
            sideboards shouldHaveSize 2
            sideboards.forEach {
                it.size shouldBe 2
                it.cardIds shouldHaveSize 0
                it.isVisible shouldBe false
            }
        }

        test("two-player is the degenerate case: one opponent, two seats") {
            val (session, ids) = startedSession(2)

            session.getOpponentIds(ids[0]) shouldBe listOf(ids[1])
            session.seatInfos(viewerId = ids[0]) shouldHaveSize 2
            session.seatInfos(viewerId = ids[0]).single { it.isYou }.playerId shouldBe ids[0].value
        }
    }
}
