package com.wingedsheep.gameserver.persistence

import com.wingedsheep.gameserver.persistence.dto.PersistentMatch
import com.wingedsheep.gameserver.tournament.TournamentManager
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class TournamentTerminalPersistenceTest : FunSpec({
    test("native terminal evidence survives tournament persistence and restore") {
        val winner = EntityId("krenko")
        val tournament = TournamentManager(
            "terminal-lobby", listOf(winner to "Krenko", EntityId("talrand") to "Talrand"), 1
        )
        val match = tournament.startNextRound()!!.matches.single()
        match.gameSessionId = "played-game"
        tournament.reportMatchResult("played-game", winner, 17)
        match.nativeGameOver = true
        match.finalTurnNumber = 13

        val persisted = tournament.toPersistent("terminal-lobby")
        val decoded = Json.decodeFromString<com.wingedsheep.gameserver.persistence.dto.PersistentTournament>(
            Json.encodeToString(persisted)
        )
        val restored = restoreTournamentManager(decoded)
            .getRoundsForPersistence().single().matches.single()

        restored.nativeGameOver shouldBe true
        restored.finalTurnNumber shouldBe 13
        restored.winnerId shouldBe winner
    }

    test("old persistent matches default to no native terminal evidence") {
        val oldJson = """{"player1Id":"a","player2Id":"b","gameSessionId":"game","winnerId":"a","isDraw":false,"isComplete":true}"""
        val restored = Json.decodeFromString<PersistentMatch>(oldJson)

        restored.nativeGameOver shouldBe false
        restored.finalTurnNumber shouldBe null
    }
})
