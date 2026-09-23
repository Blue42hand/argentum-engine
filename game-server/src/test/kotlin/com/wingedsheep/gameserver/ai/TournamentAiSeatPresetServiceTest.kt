package com.wingedsheep.gameserver.ai

import com.wingedsheep.ai.engine.deck.GeneratedDeck
import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.gameserver.lobby.AiDeckSpec
import com.wingedsheep.gameserver.lobby.LobbyGameMode
import com.wingedsheep.gameserver.lobby.TournamentFormat
import com.wingedsheep.gameserver.lobby.TournamentLobby
import com.wingedsheep.gameserver.session.PlayerIdentity
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class TournamentAiSeatPresetServiceTest : FunSpec({
    val aiPlayerId = EntityId("ai-seat")

    fun premadeFfaLobby(): TournamentLobby = TournamentLobby(
        setCodes = listOf("TST"),
        setNames = listOf("Test"),
        boosterGenerator = mockk<BoosterGenerator>(relaxed = true),
        format = TournamentFormat.PREMADE_DECKS,
        gameMode = LobbyGameMode.FREE_FOR_ALL,
    )

    fun aiIdentity(controllerSpec: AiControllerSpec? = null) = PlayerIdentity(
        playerId = aiPlayerId,
        playerName = "Preset AI",
        isAi = true,
        aiControllerSpec = controllerSpec,
    )

    test("provider profile and bound deck commit together to an FFA premade seat") {
        val aiGameManager = mockk<AiGameManager>()
        val randomDeckResolver = mockk<RandomDeckResolver>()
        val service = TournamentAiSeatPresetService(aiGameManager, randomDeckResolver)
        val lobby = premadeFfaLobby()
        val identity = aiIdentity()
        lobby.addPlayer(identity)

        val controller = AiControllerSpec("external-test", "profile-a")
        val boundDeck = AiDeckSpec.Fixed(
            deckList = mapOf("Plains" to 60),
            label = "Provider deck",
        )
        every { aiGameManager.isAiPlayer(aiPlayerId) } returns true
        every { aiGameManager.resolveSeatPreset(controller) } returns
            ResolvedAiSeatPreset(controller, boundDeck)
        every {
            randomDeckResolver.resolve(boundDeck, lobby.deckFormat, lobby.setCodes, lobby.usesCommanderRules)
        } returns GeneratedDeck(mapOf("Plains" to 60))

        service.applyPremadePreset(lobby, aiPlayerId, controller) shouldBe
            TournamentAiSeatPresetService.ApplyResult.Applied(controller, boundDeck)

        identity.aiControllerSpec shouldBe controller
        lobby.players.getValue(aiPlayerId).aiDeckSpec shouldBe boundDeck
        lobby.players.getValue(aiPlayerId).submittedDeck shouldBe mapOf("Plains" to 60)
        service.providerOwnedDeck(controller) shouldBe boundDeck
    }

    test("unknown explicit profile fails closed before deck generation or seat mutation") {
        val aiGameManager = mockk<AiGameManager>()
        val randomDeckResolver = mockk<RandomDeckResolver>()
        val service = TournamentAiSeatPresetService(aiGameManager, randomDeckResolver)
        val lobby = premadeFfaLobby()
        val identity = aiIdentity()
        lobby.addPlayer(identity)

        val missing = AiControllerSpec("external-test", "missing")
        every { aiGameManager.isAiPlayer(aiPlayerId) } returns true
        every { aiGameManager.resolveSeatPreset(missing) } throws
            IllegalArgumentException("Unknown AI controller profile 'missing'")

        service.applyPremadePreset(lobby, aiPlayerId, missing) shouldBe
            TournamentAiSeatPresetService.ApplyResult.Rejected("Unknown AI controller profile 'missing'")

        identity.aiControllerSpec shouldBe null
        lobby.players.getValue(aiPlayerId).aiDeckSpec shouldBe AiDeckSpec.Auto
        lobby.players.getValue(aiPlayerId).submittedDeck shouldBe null
        verify(exactly = 0) { randomDeckResolver.resolve(any(), any(), any<List<String>>(), any()) }
    }

    test("rejected replacement deck restores the previous controller and deck row") {
        val aiGameManager = mockk<AiGameManager>()
        val randomDeckResolver = mockk<RandomDeckResolver>()
        val service = TournamentAiSeatPresetService(aiGameManager, randomDeckResolver)
        val lobby = premadeFfaLobby()
        val previousController = AiControllerSpec("engine")
        val identity = aiIdentity(previousController)
        lobby.addPlayer(identity)
        lobby.submitDeck(aiPlayerId, mapOf("Plains" to 60)) shouldBe
            TournamentLobby.DeckSubmissionResult.Success(allReady = true)

        val controller = AiControllerSpec("external-test", "bad-deck")
        val boundDeck = AiDeckSpec.Fixed(
            deckList = mapOf("Lightning Bolt" to 60),
            label = "Invalid provider deck",
        )
        every { aiGameManager.isAiPlayer(aiPlayerId) } returns true
        every { aiGameManager.resolveSeatPreset(controller) } returns
            ResolvedAiSeatPreset(controller, boundDeck)
        every {
            randomDeckResolver.resolve(boundDeck, lobby.deckFormat, lobby.setCodes, lobby.usesCommanderRules)
        } returns GeneratedDeck(mapOf("Lightning Bolt" to 60))

        val result = service.applyPremadePreset(lobby, aiPlayerId, controller)
        (result as TournamentAiSeatPresetService.ApplyResult.Rejected).message.contains("rejected") shouldBe true

        identity.aiControllerSpec shouldBe previousController
        lobby.players.getValue(aiPlayerId).aiDeckSpec shouldBe AiDeckSpec.Auto
        lobby.players.getValue(aiPlayerId).submittedDeck shouldBe mapOf("Plains" to 60)
    }
})
