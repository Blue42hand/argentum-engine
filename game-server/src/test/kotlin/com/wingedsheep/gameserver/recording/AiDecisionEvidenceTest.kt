package com.wingedsheep.gameserver.recording

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.*
import org.springframework.web.socket.WebSocketSession

class AiDecisionEvidenceTest : ScenarioTestBase() {
    data class Row(val kind: String, val payload: JsonObject, val seat: String?)
    fun fixture(): Pair<GameSession, MutableList<Row>> {
        val rows = mutableListOf<Row>()
        val session = GameSession(cardRegistry = cardRegistry, evidenceSink = GameEvidenceSink { k, p, s -> rows.add(Row(k, p.jsonObject, s)) })
        listOf("choice-a", "choice-b").forEach { name ->
            val ws = mockk<WebSocketSession>(relaxed = true) { every { id } returns "ws-$name" }
            session.addPlayer(PlayerSession(ws, EntityId.of(name), name), mapOf("Forest" to 40))
        }
        session.startGame()
        session.getPlayers().forEach { session.keepHand(it.playerId) }
        return session to rows
    }
    fun offer(session: GameSession): Pair<EntityId, ServerMessage.StateUpdate> {
        val seat = session.getStateForTesting()!!.priorityPlayerId!!
        session.clearLastSentState(seat)
        return seat to (session.createStateUpdate(seat, emptyList(), useEngineDecisionIds = true) as ServerMessage.StateUpdate)
    }
    init {
        test("evidence-only failure freezes recording while the native action still executes") {
            val (session, _) = fixture()
            val (seat, update) = offer(session)
            val context = session.beginAiDecisionEvidence(seat, update.state, update.legalActions, update.interactionEpoch, emptyList())!!
            // Synthetic corruption of an issued value exercises the otherwise unreachable failure boundary.
            context.javaClass.getDeclaredField("observationBody").also { it.isAccessible = true }.set(context, "invalid-json")
            val pass = update.legalActions.first { it.action is PassPriority }.action
            try {
                val result = session.executeRecordedAiAction(seat, pass, update.interactionEpoch, context, pass)
                (result is GameSession.ActionResult.Success || result is GameSession.ActionResult.PausedForDecision) shouldBe true
                session.getRecordedActions().last() shouldBe pass
            } finally { PrivateGameEvidence.failures.decrementAndGet() }
        }
        test("terminal accepted receipt is emitted before the native source seals") {
            val (session, rows) = fixture()
            val state = session.getStateForTesting()!!
            val seat = state.priorityPlayerId!!
            val opponent = session.getPlayers().first { it.playerId != seat }.playerId
            session.restoreFromPersistence(state.updateEntity(opponent) { it.with(LifeTotalComponent(0)) },
                emptyMap(), emptyMap(), emptyMap())
            repeat(20) {
                if (!session.isGameOver()) {
                    val (actor, update) = offer(session)
                    val context = session.beginAiDecisionEvidence(actor, update.state, update.legalActions, update.interactionEpoch, emptyList())!!
                    val pass = update.legalActions.first { it.action is PassPriority }.action
                    session.executeRecordedAiAction(actor, pass, update.interactionEpoch, context, pass)
                }
            }
            session.isGameOver() shouldBe true
            rows.indexOfLast { it.kind == "ai_decision_result" } shouldBe rows.indexOfFirst { it.kind == "terminal" } - 1
        }
        test("accepted enumerated choice joins native masked input to own masked result") {
            val (session, rows) = fixture()
            val (seat, update) = offer(session)
            val context = session.beginAiDecisionEvidence(seat, update.state, update.legalActions, update.interactionEpoch, emptyList())!!
            val body = Json.parseToJsonElement(context.observationBody).jsonObject
            PrivateGameEvidence.sha256(context.observationBody.toByteArray()) shouldBe context.stateDigest
            body.getValue("state").jsonObject.getValue("viewingPlayerId").jsonPrimitive.content shouldBe seat.value
            session.getPlayers().filter { it.playerId != seat }.flatMap { session.getHand(it.playerId) }.forEach {
                body.getValue("state").jsonObject.getValue("cards").jsonObject.containsKey(it.value) shouldBe false
            }
            val pass = update.legalActions.first { it.action is PassPriority }.action
            session.executeRecordedAiAction(seat, pass, update.interactionEpoch, context, pass)
            val result = rows.single { it.kind == "ai_decision_result" }
            result.seat shouldBe seat.value
            result.payload.getValue("status").jsonPrimitive.content shouldBe "accepted"
            result.payload.getValue("correlationId").jsonPrimitive.content shouldBe context.correlationId
            rows[rows.indexOf(result) - 1].kind shouldBe "native_transition"
            val after = Json.parseToJsonElement(result.payload.getValue("resultObservation").jsonObject.getValue("observationBody").jsonPrimitive.content).jsonObject
            after.getValue("state").jsonObject.getValue("viewingPlayerId").jsonPrimitive.content shouldBe seat.value
        }
        test("rejected stale duplicate and overridden attempts cannot produce another accepted receipt") {
            for (mode in listOf("rejected", "stale", "duplicate", "override")) {
                val (session, rows) = fixture()
                val (seat, update) = offer(session)
                val context = session.beginAiDecisionEvidence(seat, update.state, update.legalActions, update.interactionEpoch, emptyList())!!
                val pass = update.legalActions.first { it.action is PassPriority }.action
                val illegal = PlayLand(seat, EntityId.of("missing-synthetic-card"))
                when (mode) {
                    "rejected" -> session.executeRecordedAiAction(seat, illegal, update.interactionEpoch, context, illegal)
                    "stale" -> session.executeRecordedAiAction(seat, pass, "obsolete", context, pass)
                    "override" -> session.executeRecordedAiAction(seat, pass, update.interactionEpoch, context, illegal)
                    else -> {
                        session.executeRecordedAiAction(seat, pass, update.interactionEpoch, context, pass)
                        session.executeRecordedAiAction(seat, pass, update.interactionEpoch, context, pass)
                    }
                }
                rows.count { it.kind == "ai_decision_result" } shouldBe if (mode == "duplicate") 1 else 0
                rows.any { it.kind == "ai_decision_disposition" } shouldBe true
            }
        }
    }
}
