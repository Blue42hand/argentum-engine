package com.wingedsheep.gameserver.persistence

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gameserver.persistence.dto.PersistentGameSession
import com.wingedsheep.gameserver.session.GameSession
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.sdk.model.EntityId
import io.mockk.mockk
import io.mockk.every

class ManualHumanAdmissionPersistenceTest : FunSpec({
    test("manual admission requires a server-classified human participant") {
        val session = GameSession(cardRegistry = CardRegistry())
        val seat = EntityId("synthetic-seat")
        val player = mockk<PlayerSession>(relaxed = true) { every { playerId } returns seat }
        session.associatePlayer(player)
        session.setPlayerPersistenceInfo(seat, "Fixture", "fixture-token", isAi = true)
        session.manualHumanStart = true
        session.isManualHumanGame() shouldBe false
        session.setPlayerPersistenceInfo(seat, "Fixture", "fixture-token", isAi = false)
        session.isManualHumanGame() shouldBe true
        session.manualHumanStart = false
        session.isManualHumanGame() shouldBe false
    }
    test("old sessions fail closed and manual admission survives native persistence recovery") {
        val old = """{"sessionId":"fixture","gameState":null,"deckLists":{},"lastProcessedMessageId":{},"gameLogs":{},"playerInfos":[],"lobbyId":null}"""
        persistenceJson.decodeFromString<PersistentGameSession>(old).manualHumanStart shouldBe false
        val session = GameSession(cardRegistry = CardRegistry())
        session.manualHumanStart = true
        val encoded = persistenceJson.encodeToString(session.toPersistent(null))
        val stored = persistenceJson.decodeFromString<PersistentGameSession>(encoded)
        val (restored, _) = restoreGameSession(stored, CardRegistry())
        restored.manualHumanStart shouldBe true
    }
})
