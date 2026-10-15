package com.wingedsheep.gameserver.session

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.handler.GamePlayHandler
import com.wingedsheep.gameserver.handler.MessageSender
import com.wingedsheep.gameserver.protocol.ClientMessage
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.repository.GameRepository
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.socket.WebSocketSession
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real broadcast/persistence interleavings: only network delivery is blocked by the test. */
class PriorityYieldDeliveryTest : ScenarioTestBase() {
    private data class Fixture(val game: GameSession, val player: PlayerSession, val handler: GamePlayHandler,
        val sender: MessageSender, val repository: GameRepository)

    private fun fixture(): Fixture {
        val board = scenario().withPlayers("A", "B").withActivePlayer(1)
            .withLandsOnBattlefield(1, "Forest", 1).withLandsOnBattlefield(2, "Forest", 1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
        fun player(playerId: com.wingedsheep.sdk.model.EntityId): PlayerSession {
            val socketId = "socket-${playerId.value}"
            val socket = mockk<WebSocketSession>(relaxed = true) { every { id } returns socketId }
            return PlayerSession(socket, playerId, playerId.value)
        }
        val p1 = player(board.player1Id)
        val p2 = player(board.player2Id)
        p1.webSocketSession.id shouldBe "socket-${board.player1Id.value}"
        p2.webSocketSession.id shouldBe "socket-${board.player2Id.value}"
        val game = GameSession(cardRegistry = cardRegistry)
        game.injectStateForTesting(board.state, mapOf(p1.playerId to p1, p2.playerId to p2))
        val registry = SessionRegistry()
        val repository = mockk<GameRepository>(relaxed = true)
        registry.setPlayerSession(p1.webSocketSession.id, p1)
        every { repository.findById(game.sessionId) } returns game
        every { repository.getLobbyForGame(game.sessionId) } returns null
        (registry.getPlayerSession(p1.webSocketSession.id) === p1) shouldBe true
        (repository.findById(p1.currentGameSessionId!!) === game) shouldBe true
        val sender = mockk<MessageSender>(relaxed = true)
        every { sender.sendError(any(), any(), any()) } answers { error("Unexpected protocol error: ${thirdArg<String>()}") }
        val handler = GamePlayHandler(
            sessionRegistry = registry, gameRepository = repository, lobbyRepository = mockk(relaxed = true),
            sender = sender, cardRegistry = cardRegistry, printingRegistry = mockk(relaxed = true),
            tokenArtRegistry = mockk(relaxed = true), deckGenerator = mockk(relaxed = true),
            gameProperties = mockk(relaxed = true), replayService = mockk(relaxed = true),
            replayCheckpointFlusher = mockk(relaxed = true), engineVersion = mockk(relaxed = true),
            aiGameManager = mockk(relaxed = true), matchResultSink = mockk(relaxed = true),
            rankedResultSink = mockk(relaxed = true), deckProfiler = mockk(relaxed = true),
        )
        return Fixture(game, p1, handler, sender, repository)
    }

    private fun origin(f: Fixture) = f.game.createStateUpdate(f.player.playerId, emptyList())
        .shouldBeInstanceOf<ServerMessage.StateUpdate>()

    private fun await(latch: CountDownLatch, stage: String) { check(latch.await(10, TimeUnit.SECONDS)) { "Timed out: $stage" } }
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        condition() shouldBe true
    }

    init {
        test("Cancel mutates while delivery is blocked and newer inactive state cannot be overtaken") {
            val f = fixture()
            val before = origin(f)
            val blocked = CountDownLatch(1)
            val release = CountDownLatch(1)
            val cancelled = CountDownLatch(1)
            val once = AtomicBoolean()
            val delivered = Collections.synchronizedList(mutableListOf<Pair<Long, String?>>())
            every { f.sender.send(f.player.webSocketSession, any()) } answers {
                val message = secondArg<ServerMessage>()
                if (Thread.currentThread().name.startsWith("priority-yield-") && once.compareAndSet(false, true)) {
                    blocked.countDown()
                    await(release, "release worker delivery")
                }
                when (message) {
                    is ServerMessage.StateUpdate -> delivered.add(message.stateVersion to message.priorityYield)
                    is ServerMessage.StateDeltaUpdate -> delivered.add(message.stateVersion to message.priorityYield)
                    else -> Unit
                }
                Unit
            }
            try {
                f.handler.handle(f.player.webSocketSession, ClientMessage.SetPriorityYield("nextTurn", before.interactionEpoch, before.stateVersion))
                f.game.getPriorityYield(f.player.playerId) shouldBe "nextTurn"
                await(blocked, "worker entered delivery")
                Thread.ofVirtual().start {
                    try { f.handler.handle(f.player.webSocketSession, ClientMessage.SetPriorityYield(null, before.interactionEpoch, before.stateVersion)) }
                    finally { cancelled.countDown() }
                }
                // Mutation is independent of the blocked send. Delivery waits for its proper order.
                awaitCondition { f.game.getPriorityYield(f.player.playerId) == null }
                release.countDown()
                await(cancelled, "cancel handler completed")
                val versions = delivered.map { it.first }
                versions shouldBe versions.sorted()
                delivered.last().second shouldBe null
            } finally { release.countDown() }
        }

        test("concurrent concession finalizes once and a resumed yield cannot save a removed game") {
            val f = fixture()
            val before = origin(f)
            val blocked = CountDownLatch(1)
            val release = CountDownLatch(1)
            val conceded = CountDownLatch(1)
            val removed = CountDownLatch(1)
            val once = AtomicBoolean()
            val operations = Collections.synchronizedList(mutableListOf<String>())
            every { f.repository.save(f.game) } answers { operations.add("save"); Unit }
            every { f.repository.remove(f.game.sessionId) } answers { operations.add("remove"); removed.countDown(); f.game }
            every { f.sender.send(f.player.webSocketSession, any()) } answers {
                if (Thread.currentThread().name.startsWith("priority-yield-") && once.compareAndSet(false, true)) {
                    blocked.countDown()
                    await(release, "release worker delivery")
                }
            }
            try {
                f.handler.handle(f.player.webSocketSession, ClientMessage.SetPriorityYield("nextTurn", before.interactionEpoch, before.stateVersion))
                f.game.getPriorityYield(f.player.playerId) shouldBe "nextTurn"
                await(blocked, "worker entered delivery")
                Thread.ofVirtual().start {
                    try { f.handler.concedeSeat(f.game, f.player.playerId) }
                    finally { conceded.countDown() }
                }
                awaitCondition { f.game.isGameOver() }
                release.countDown()
                await(conceded, "concession completed")
                await(removed, "repository removed")
                // Invoke both late paths explicitly after teardown: neither may save or finalize.
                f.handler.broadcastStateUpdate(f.game, emptyList())
                f.handler.handleGameOver(f.game)
                verify(exactly = 1) { f.repository.remove(f.game.sessionId) }
                verify(exactly = 1) { f.sender.send(f.player.webSocketSession, any<ServerMessage.GameOver>()) }
                operations.last() shouldBe "remove"
                f.game.deliveryClosed shouldBe true
            } finally { release.countDown() }
        }
    }
}
