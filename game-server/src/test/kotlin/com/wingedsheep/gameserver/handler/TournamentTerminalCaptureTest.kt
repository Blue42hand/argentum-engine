package com.wingedsheep.gameserver.handler

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gameserver.controller.AiTournamentController
import com.wingedsheep.gameserver.lobby.LobbyState
import com.wingedsheep.gameserver.lobby.TournamentLobby
import com.wingedsheep.gameserver.repository.InMemoryGameRepository
import com.wingedsheep.gameserver.repository.InMemoryLobbyRepository
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.tournament.TournamentManager
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap

class TournamentTerminalCaptureTest : FunSpec({
    data class Case(
        val name: String,
        val state: GameState?,
        val winner: EntityId?,
        val expectedNativeGameOver: Boolean,
        val expectedTurn: Int?,
    )

    fun state(gameOver: Boolean, turn: Int): GameState {
        val snapshot = mockk<GameState>()
        every { snapshot.gameOver } returns gameOver
        every { snapshot.turnNumber } returns turn
        return snapshot
    }

    val krenko = EntityId("krenko")
    val cases = listOf(
        Case("native win survives session removal", state(true, 13), krenko, true, 13),
        Case("missing session supplies no terminal proof", null, krenko, false, null),
        Case("nonterminal snapshot supplies no terminal proof", state(false, 7), krenko, false, null),
        Case("native draw retains its final turn", state(true, 14), null, true, 14),
    )

    cases.forEach { case ->
        test(case.name) {
            val lobbyId = "terminal-lobby"
            val gameId = "played-game"
            val gameRepository = InMemoryGameRepository()
            val lobbyRepository = InMemoryLobbyRepository()
            val tournament = TournamentManager(
                lobbyId, listOf(krenko to "Krenko", EntityId("talrand") to "Talrand"), 1
            )
            tournament.startNextRound()!!.matches.single().gameSessionId = gameId
            lobbyRepository.saveTournament(lobbyId, tournament)
            if (case.state != null) {
                val session = mockk<GameSession>()
                every { session.sessionId } returns gameId
                every { session.getStateSnapshot() } returns case.state
                gameRepository.save(session)
            }

            val context = LobbySharedContext(
                mockk(), gameRepository, lobbyRepository, mockk(), mockk(),
            )
            val handler = TournamentMatchHandler(
                context, mockk(relaxed = true), mockk(), mockk(), mockk(),
                mockk(), mockk(), gameRepository, mockk(), mockk(), mockk(),
            )
            try {
                handler.handleMatchResult(lobbyId, gameId, case.winner, 17)
                gameRepository.remove(gameId)

                val lobby = mockk<TournamentLobby>()
                every { lobby.lobbyId } returns lobbyId
                every { lobby.state } returns LobbyState.TOURNAMENT_COMPLETE
                every { lobby.players } returns ConcurrentHashMap()
                every { lobby.isFreeForAll } returns false
                every { lobby.gameMode } returns com.wingedsheep.gameserver.lobby.LobbyGameMode.TOURNAMENT
                lobbyRepository.saveLobby(lobby)

                val completed = AiTournamentController(
                    mockk(), mockk(), lobbyRepository, gameRepository,
                ).status(lobbyId).body!!.completedGames.single()
                completed.nativeGameOver shouldBe case.expectedNativeGameOver
                completed.finalTurnNumber shouldBe case.expectedTurn
                completed.winnerId shouldBe case.winner?.value
                completed.isDraw shouldBe (case.winner == null)
            } finally {
                context.destroy()
            }
        }
    }
})
