package com.wingedsheep.gameserver.controller

import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.handler.LobbyHandler
import com.wingedsheep.gameserver.lobby.LobbyState
import com.wingedsheep.gameserver.lobby.LobbyGameMode
import com.wingedsheep.gameserver.lobby.TournamentLobby
import com.wingedsheep.gameserver.repository.GameRepository
import com.wingedsheep.gameserver.repository.LobbyRepository
import com.wingedsheep.gameserver.tournament.TournamentManager
import com.wingedsheep.sdk.core.GameRules
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.ConcurrentHashMap

class AiTournamentControllerProfileTest : FunSpec({
    val handler = mockk<LobbyHandler>()
    val controller = AiTournamentController(
        handler, mockk<BoosterGenerator>(), mockk<LobbyRepository>(), mockk<GameRepository>(),
    )

    test("fixed Commander decks forward exact native controller specs") {
        val specs = listOf(
            AiControllerSpec("commander-gym", "profile-a"),
            AiControllerSpec("commander-gym", "profile-b"),
        )
        val decks = listOf(mapOf("Forest" to 99), mapOf("Island" to 99))
        every {
            handler.createAiTournamentWithFixedDecks(decks, null, 1, specs, GameRules.COMMANDER, LobbyGameMode.TOURNAMENT)
        } returns "test-lobby"

        val response = controller.createAiTournament(
            AiTournamentController.AiTournamentRequest(
                decks = decks, controllerSpecs = specs, rules = GameRules.COMMANDER,
                gamesPerMatch = 1,
            )
        )

        response.statusCode.value() shouldBe 200
        response.body!!.lobbyId shouldBe "test-lobby"
        verify(exactly = 1) {
            handler.createAiTournamentWithFixedDecks(decks, null, 1, specs, GameRules.COMMANDER, LobbyGameMode.TOURNAMENT)
        }
    }

    test("four fixed Commander profiles select one native free-for-all pod") {
        val specs = (0 until 4).map { AiControllerSpec("commander-gym", "profile-$it") }
        val decks = (0 until 4).map { mapOf("Forest" to 99) }
        every {
            handler.createAiTournamentWithFixedDecks(
                decks, null, 1, specs, GameRules.COMMANDER, LobbyGameMode.FREE_FOR_ALL,
            )
        } returns "pod-lobby"

        val response = controller.createAiTournament(
            AiTournamentController.AiTournamentRequest(
                decks = decks, controllerSpecs = specs, rules = GameRules.COMMANDER,
                gamesPerMatch = 1, gameMode = LobbyGameMode.FREE_FOR_ALL,
            )
        )

        response.statusCode.value() shouldBe 200
        response.body!!.lobbyId shouldBe "pod-lobby"
        verify(exactly = 1) {
            handler.createAiTournamentWithFixedDecks(
                decks, null, 1, specs, GameRules.COMMANDER, LobbyGameMode.FREE_FOR_ALL,
            )
        }
    }

    test("controller specs without fixed decks fail closed") {
        val response = controller.createAiTournament(
            AiTournamentController.AiTournamentRequest(
                controllerSpecs = listOf(AiControllerSpec("commander-gym", "profile-a")),
                rules = GameRules.COMMANDER,
            )
        )
        response.statusCode.value() shouldBe 400
    }

    test("status retains native terminal evidence after the game session is removed") {
        val lobbyId = "terminal-lobby"
        val gameId = "played-game"
        val krenko = EntityId("krenko")
        val talrand = EntityId("talrand")
        val tournament = TournamentManager(lobbyId, listOf(krenko to "Krenko", talrand to "Talrand"), 1)
        val match = tournament.startNextRound()!!.matches.single()
        match.gameSessionId = gameId
        tournament.reportMatchResult(gameId, krenko, 17)
        match.nativeGameOver = true
        match.finalTurnNumber = 13

        val lobby = mockk<TournamentLobby>()
        every { lobby.lobbyId } returns lobbyId
        every { lobby.state } returns LobbyState.TOURNAMENT_COMPLETE
        every { lobby.players } returns ConcurrentHashMap()
        every { lobby.isFreeForAll } returns false
        every { lobby.gameMode } returns LobbyGameMode.TOURNAMENT
        val lobbyRepository = mockk<LobbyRepository>()
        every { lobbyRepository.findLobbyById(lobbyId) } returns lobby
        every { lobbyRepository.findTournamentById(lobbyId) } returns tournament
        val gameRepository = mockk<GameRepository>()
        every { gameRepository.findById(gameId) } returns null

        val status = AiTournamentController(handler, mockk(), lobbyRepository, gameRepository)
            .status(lobbyId).body!!

        status.complete shouldBe true
        status.completedGames.single().nativeGameOver shouldBe true
        status.completedGames.single().finalTurnNumber shouldBe 13
        status.completedGames.single().winnerId shouldBe krenko.value
    }

    test("free-for-all status retains native terminal evidence without a bracket") {
        val lobby = mockk<TournamentLobby>()
        every { lobby.lobbyId } returns "pod-lobby"
        every { lobby.state } returns LobbyState.TOURNAMENT_ACTIVE
        every { lobby.players } returns ConcurrentHashMap()
        every { lobby.isFreeForAll } returns true
        every { lobby.gameMode } returns LobbyGameMode.FREE_FOR_ALL
        every { lobby.ffaGameSessionId } returns null
        every { lobby.ffaGamesPlayed } returns 1
        every { lobby.ffaLastGameSessionId } returns "pod-game"
        every { lobby.ffaLastWinnerId } returns "winner"
        every { lobby.ffaLastFinalTurnNumber } returns 15
        every { lobby.ffaLastNativeGameOver } returns true
        val lobbyRepository = mockk<LobbyRepository>()
        every { lobbyRepository.findLobbyById("pod-lobby") } returns lobby
        every { lobbyRepository.findTournamentById("pod-lobby") } returns null

        val result = AiTournamentController(handler, mockk(), lobbyRepository, mockk())
            .status("pod-lobby").body!!

        result.complete shouldBe true
        result.gameMode shouldBe LobbyGameMode.FREE_FOR_ALL.name
        result.completedGames.single().gameSessionId shouldBe "pod-game"
        result.completedGames.single().nativeGameOver shouldBe true
        result.completedGames.single().finalTurnNumber shouldBe 15
        result.completedGames.single().winnerId shouldBe "winner"
    }
})
