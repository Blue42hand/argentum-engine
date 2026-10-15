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
import io.mockk.spyk
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PriorityYieldDispatchTest : ScenarioTestBase() {
    init {
        test("same socket can cancel while one coalesced continuation is busy") {
            val board = scenario().withPlayers("A", "B").withActivePlayer(1)
                .withLandsOnBattlefield(1, "Forest", 1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val ws = mockk<WebSocketSession>(relaxed = true) { every { id } returns "yield-socket" }
            val player = PlayerSession(ws, board.player1Id, "A")
            val game = GameSession(cardRegistry = cardRegistry)
            game.injectStateForTesting(board.state, mapOf(player.playerId to player))
            val registry = SessionRegistry()
            val repository = mockk<GameRepository>(relaxed = true)
            registry.setPlayerSession(ws.id, player)
            every { repository.findById(game.sessionId) } returns game
            (registry.getPlayerSession(ws.id) === player) shouldBe true
            (repository.findById(player.currentGameSessionId!!) === game) shouldBe true
            val sender = mockk<MessageSender>(relaxed = true)
            every { sender.sendError(any(), any(), any()) } answers { error("Unexpected protocol error: ${thirdArg<String>()}") }
            val handler = spyk(GamePlayHandler(
                sessionRegistry = registry, gameRepository = repository, lobbyRepository = mockk(relaxed = true),
                sender = sender, cardRegistry = cardRegistry, printingRegistry = mockk(relaxed = true),
                tokenArtRegistry = mockk(relaxed = true), deckGenerator = mockk(relaxed = true),
                gameProperties = mockk(relaxed = true), replayService = mockk(relaxed = true),
                replayCheckpointFlusher = mockk(relaxed = true), engineVersion = mockk(relaxed = true),
                aiGameManager = mockk(relaxed = true), matchResultSink = mockk(relaxed = true),
                rankedResultSink = mockk(relaxed = true), deckProfiler = mockk(relaxed = true),
            ))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val inFlight = AtomicInteger()
            val maximum = AtomicInteger()
            every { handler.broadcastStateUpdate(game, emptyList()) } answers {
                if (Thread.currentThread().name.startsWith("priority-yield-")) {
                    val count = inFlight.incrementAndGet()
                    maximum.accumulateAndGet(count, ::maxOf)
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    inFlight.decrementAndGet()
                    finished.countDown()
                }
            }
            fun origin(): ServerMessage.StateUpdate {
                game.clearLastSentState(player.playerId)
                return game.createStateUpdate(player.playerId, emptyList()).shouldBeInstanceOf<ServerMessage.StateUpdate>()
            }
            val first = origin()
            try {
                handler.handle(ws, ClientMessage.SetPriorityYield("nextTurn", first.interactionEpoch, first.stateVersion))
                game.getPriorityYield(player.playerId) shouldBe "nextTurn"
                check(entered.await(5, TimeUnit.SECONDS)) { "Worker did not start" }
                repeat(3) {
                    val fresh = origin()
                    handler.handle(ws, ClientMessage.SetPriorityYield("nextTurn", fresh.interactionEpoch, fresh.stateVersion))
                }
                // The original rendered version is deliberately old. Cancel is still valid for
                // this live epoch, and returns on the same socket while its worker is blocked.
                handler.handle(ws, ClientMessage.SetPriorityYield(null, first.interactionEpoch, first.stateVersion))
                game.getPriorityYield(player.playerId) shouldBe null
                game.getAutoPassIntent() shouldBe null
                maximum.get() shouldBe 1
            } finally {
                release.countDown()
                if (entered.count == 0L) check(finished.await(5, TimeUnit.SECONDS)) { "Worker did not finish" }
            }
        }
    }
}
