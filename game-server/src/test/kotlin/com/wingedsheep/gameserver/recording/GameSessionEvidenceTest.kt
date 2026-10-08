package com.wingedsheep.gameserver.recording

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.*
import org.springframework.web.socket.WebSocketSession
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** Exercises server capture and masking with synthetic seats; no external policy calls. */
class GameSessionEvidenceTest : ScenarioTestBase() {
    init {
        test("four seat native game captures own offers actions RNG and terminal separately") {
            data class Row(val kind: String, val payload: JsonElement, val seat: String?)
            val rows = mutableListOf<Row>()
            val root = Files.createTempDirectory("synthetic-four-seat-capture-")
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
            val writer = PrivateGameEvidence.create(root, "synthetic-four-seat", "a".repeat(40))
            var captureNanos = 0L
            val session = GameSession(cardRegistry = cardRegistry, maxPlayers = 4,
                evidenceSink = GameEvidenceSink { kind, payload, seat ->
                    rows.add(Row(kind, payload, seat))
                    val started = System.nanoTime()
                    writer.append(kind, payload, seat)
                    captureNanos += System.nanoTime() - started
                })
            session.engineFormat = Format.Commander()
            val seats = (1..4).map { EntityId.of("synthetic-seat-$it") }
            seats.forEachIndexed { index, player ->
                val ws = mockk<WebSocketSession>(relaxed = true) { every { id } returns "synthetic-ws-$index" }
                session.addPlayer(PlayerSession(ws, player, "Synthetic $index"), mapOf("Island" to 99), commanderCardName = "Talrand, Sky Summoner")
            }
            session.startGame()
            val setup = rows.single { it.kind == "initialization" }.payload.jsonObject.getValue("setup").jsonObject
            setup.getValue("players").jsonArray.size shouldBe 4
            setup.containsKey("seed") shouldBe true
            seats.forEach { player ->
                val offer = session.getMulliganDecision(player)
                val captured = rows.last { it.kind == "seat_mulligan_offer" && it.seat == player.value }
                captured.payload.jsonObject.getValue("hand").jsonArray.size shouldBe offer.hand.size
                session.createStateUpdate(player, emptyList(), useEngineDecisionIds = true)
                val visible = rows.last { it.kind == "seat_observation" && it.seat == player.value }
                val state = visible.payload.jsonObject.getValue("state").jsonObject
                state.getValue("viewingPlayerId").jsonPrimitive.content shouldBe player.value
                val visibleCards = state.getValue("cards").jsonObject.keys
                seats.filter { it != player }.flatMap { session.getHand(it) }.forEach { hidden ->
                    visibleCards.contains(hidden.value) shouldBe false
                }
                session.keepHand(player)
            }
            val player = seats.first()
            (session.keepHand(player) is GameSession.MulliganActionResult.Failure) shouldBe true
            (session.takeMulligan(player) is GameSession.MulliganActionResult.Failure) shouldBe true
            (session.chooseBottomCards(player, listOf(EntityId.of("synthetic-stale-card"))) is GameSession.MulliganActionResult.Failure) shouldBe true
            rows.count { it.kind == "native_rejection" } shouldBe 3
            (session.executeClientAction(player, PassPriority(player), interactionEpoch = "synthetic-stale-epoch") is GameSession.ActionResult.Failure) shouldBe true
            rows.last { it.kind == "seat_submission_result" }.seat shouldBe player.value
            seats.take(3).forEach { session.playerConcedes(it) }
            rows.map { it.kind } shouldContain "native_transition"
            rows.last { it.kind == "terminal" }.payload.jsonObject.getValue("winnerId").jsonPrimitive.content shouldBe seats.last().value
            rows.filter { it.kind == "native_transition" }.all { it.seat == null } shouldBe true
            rows.filter { it.kind == "seat_observation" }.all { it.seat != null } shouldBe true
            println("SYNTHETIC_CAPTURE rows=${rows.size} bytes=${Files.size(root.resolve("synthetic-four-seat/native-000000.ndjson"))} fsync_milliseconds=${captureNanos / 1_000_000.0}")
        }
    }
}
