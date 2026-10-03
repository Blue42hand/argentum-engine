package com.wingedsheep.gameserver.controller

import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.gameserver.ai.AiControllerSpec
import com.wingedsheep.gameserver.handler.LobbyHandler
import com.wingedsheep.gameserver.repository.GameRepository
import com.wingedsheep.gameserver.repository.LobbyRepository
import com.wingedsheep.sdk.core.GameRules
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

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
            handler.createAiTournamentWithFixedDecks(decks, null, 1, specs, GameRules.COMMANDER)
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
            handler.createAiTournamentWithFixedDecks(decks, null, 1, specs, GameRules.COMMANDER)
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
})
