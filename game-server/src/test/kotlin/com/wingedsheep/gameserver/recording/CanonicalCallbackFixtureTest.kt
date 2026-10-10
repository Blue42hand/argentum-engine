package com.wingedsheep.gameserver.recording

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.RecordedAiChoice
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.persistence.persistenceJson
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.*
import org.springframework.web.socket.WebSocketSession
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.attribute.PosixFilePermissions

/** Deterministic native writer fixture, with fake answers committed before application.
 * No controller, provider, runtime launch, self-play, or network is involved.
 */
class CanonicalCallbackFixtureTest : ScenarioTestBase() {
    init {
        test("native writer callback lifecycle survives a committed-state restore") {
            val root = System.getenv("COMMANDER_GYM_CANONICAL_FIXTURE_ROOT")?.let(Path::of)
                ?: Files.createTempDirectory("native-canonical-v2-")
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
            val revision = System.getenv("COMMANDER_GYM_CANONICAL_FIXTURE_ENGINE_SHA") ?: "a".repeat(40)
            val gameId = "native-canonical-fixture"
            val callbacks = root.resolve("fixture-callbacks.ndjson")
            Files.createFile(callbacks, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            val rows = mutableListOf<String>()
            var writer = PrivateGameEvidence.create(root, gameId, revision)
            fun newSession() = GameSession(sessionId = gameId, cardRegistry = cardRegistry,
                evidenceSink = GameEvidenceSink { kind, payload, seat ->
                    rows.add(kind)
                    writer.append(kind, payload, seat)
                })
            val seats = listOf(EntityId.of("native-fixture-a"), EntityId.of("native-fixture-b"))
            fun addPlayers(session: GameSession) = seats.forEach { seat ->
                val ws = mockk<WebSocketSession>(relaxed = true) { every { id } returns "ws-${seat.value}" }
                session.addPlayer(PlayerSession(ws, seat, "Synthetic fixture"), mapOf("Forest" to 40))
            }
            fun fakeAnswer(seat: EntityId, context: com.wingedsheep.ai.AiDecisionEvidence, choice: JsonObject) {
                val event = buildJsonObject {
                    put("seatId", seat.value)
                    put("decisionEvidence", persistenceJson.encodeToJsonElement(com.wingedsheep.ai.AiDecisionEvidence.serializer(), context))
                    put("choice", choice)
                    put("nativeSequenceBeforeApplication", rows.size)
                }
                Files.writeString(callbacks, event.toString() + "\n", APPEND)
            }
            var session = newSession()
            addPlayers(session)
            session.startGame()
            // Take, keep and bottom exercise both actual native mulligan callback shapes.
            val seat = seats.first()
            for (keep in listOf(false, true)) {
                val context = session.beginAiMulliganEvidence(seat, session.getMulliganDecision(seat))!!
                fakeAnswer(seat, context, buildJsonObject { put("keep", keep) })
                session.executeRecordedMulligan(seat, context, keep)
            }
            val bottom = session.getChooseBottomCardsMessage(seat)!!
            val bottomContext = session.beginAiBottomEvidence(seat, bottom)!!
            val selected = bottom.hand.take(bottom.cardsToPutOnBottom)
            fakeAnswer(seat, bottomContext, buildJsonObject { put("selectedCards", JsonArray(selected.map { JsonPrimitive(it.value) })) })
            session.executeRecordedBottom(seat, bottomContext, selected)
            val other = seats.last()
            val keepContext = session.beginAiMulliganEvidence(other, session.getMulliganDecision(other))!!
            fakeAnswer(other, keepContext, buildJsonObject { put("keep", true) })
            session.executeRecordedMulligan(other, keepContext, true)

            // Restore through the real persistence seam and reopen the real writer.
            val state = session.getStateForPersistence()
            val decks = session.getDeckListsForPersistence()
            val logs = session.getLogsForPersistence().mapValues { it.value.toMutableList() }
            val ids = session.getLastMessageIdsForPersistence()
            val names = session.getSeatNamesForPersistence()
            writer.close()
            writer = PrivateGameEvidence.create(root, gameId, revision)
            rows.add("resume") // the writer appends this custody row while reopening
            session = newSession()
            session.restoreFromPersistence(state, decks, logs, ids, seatNames = names)
            val actor = session.getStateForTesting()!!.priorityPlayerId!!
            val update = session.createStateUpdate(actor, emptyList(), useEngineDecisionIds = true) as ServerMessage.StateUpdate
            val index = update.legalActions.indexOfFirst { it.action is PassPriority }
            val action = update.legalActions[index].action
            val context = session.beginAiCallbackEvidence(actor, update.state, update.legalActions,
                update.pendingDecision, update.interactionEpoch, emptyList())!!
            fakeAnswer(actor, context, buildJsonObject {
                put("channel", "action"); put("actionId", index); put("params", buildJsonObject {})
            })
            session.executeRecordedAiCallback(actor, action, update.interactionEpoch, context,
                RecordedAiChoice(ActionResponse.SubmitAction(action), index, buildJsonObject {}))
            session.playerConcedes(other)
            rows.count { it == "ai_callback_input" } shouldBe 5
            rows.count { it == "ai_callback_result" } shouldBe 5
            rows.none { it == "ai_callback_disposition" } shouldBe true
            rows.last() shouldBe "terminal"
            writer.healthy() shouldBe true
            writer.close()
            println("NATIVE_CANONICAL_V2_FIXTURE root=$root callbacks=5 provider_calls=0")
        }
    }
}
