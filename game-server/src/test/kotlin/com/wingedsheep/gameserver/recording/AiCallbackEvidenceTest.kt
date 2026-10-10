package com.wingedsheep.gameserver.recording

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.RecordedAiChoice
import com.wingedsheep.engine.core.*
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.persistence.persistenceJson
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.*
import org.springframework.web.socket.WebSocketSession

/** Direct deterministic server fixtures: no pilot, provider, self-play or runtime launch. */
class AiCallbackEvidenceTest : ScenarioTestBase() {
    data class Row(val kind: String, val payload: JsonObject, val seat: String?)
    private fun session(): Pair<GameSession, MutableList<Row>> {
        val rows = mutableListOf<Row>()
        return GameSession(cardRegistry = cardRegistry, evidenceSink = GameEvidenceSink { k, p, s -> rows.add(Row(k, p.jsonObject, s)) }) to rows
    }
    private fun players(session: GameSession) {
        listOf("callback-a", "callback-b").forEach { name ->
            val ws = mockk<WebSocketSession>(relaxed = true) { every { id } returns "ws-$name" }
            session.addPlayer(PlayerSession(ws, EntityId.of(name), name), mapOf("Forest" to 40))
        }
        session.startGame()
    }
    private fun offer(session: GameSession, seat: EntityId): ServerMessage.StateUpdate {
        session.clearLastSentState(seat)
        return session.createStateUpdate(seat, emptyList(), useEngineDecisionIds = true) as ServerMessage.StateUpdate
    }
    init {
        cardRegistry.register(card("Callback Fixture X Spell") {
            manaCost = "{X}{R}"
            typeLine = "Sorcery"
            spell {
                val selected = target(Targets.Any)
                effect = Effects.DealDamage(DynamicAmount.XValue, selected)
            }
        })
        test("distinct native targets and X survive concrete parameterization and application") {
            for (x in listOf(1, 3)) {
                val game = scenario().withPlayers("Fixture A", "Fixture B")
                    .withCardInHand(1, "Callback Fixture X Spell").withLandsOnBattlefield(1, "Mountain", 8)
                    .withCardOnBattlefield(2, "Hill Giant").withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val (session, rows) = session()
                session.injectStateForDevScenario(game.state)
                val update = offer(session, game.player1Id)
                val index = update.legalActions.indexOfFirst { it.action is CastSpell }
                (index >= 0) shouldBe true
                val params = buildJsonObject {
                    put("xValue", x)
                    put("targets", JsonArray(listOf(JsonPrimitive(if (x == 1) game.player2Id.value else game.findPermanent("Hill Giant")!!.value))))
                }
                val native = ActionParameterizer.apply(update.legalActions[index], persistenceJson.decodeFromJsonElement(ActionParams.serializer(), params), session.getStateForTesting()!!)
                val context = session.beginAiCallbackEvidence(game.player1Id, update.state, update.legalActions, update.pendingDecision, update.interactionEpoch, emptyList())!!
                session.executeRecordedAiCallback(game.player1Id, native, update.interactionEpoch, context,
                    RecordedAiChoice(ActionResponse.SubmitAction(native), index, params))
                val result = rows.single { it.kind == "ai_callback_result" }
                result.payload.getValue("choice").jsonObject.getValue("params") shouldBe params
                result.payload.getValue("action") shouldBe persistenceJson.encodeToJsonElement(GameAction.serializer(), native)
                rows[rows.indexOf(result) - 1].kind shouldBe "native_transition"
            }
        }
        test("mulligan receipt remains current while another seat keeps and result stays seat masked") {
            val (session, rows) = session()
            players(session)
            val seat = session.getPlayers().first().playerId
            val other = session.getPlayers().last().playerId
            val context = session.beginAiMulliganEvidence(seat, session.getMulliganDecision(seat))!!
            session.keepHand(other)
            session.executeRecordedMulligan(seat, context, true)
            val result = rows.single { it.kind == "ai_callback_result" }
            result.payload.getValue("correlationId").jsonPrimitive.content shouldBe context.correlationId
            val after = Json.parseToJsonElement(result.payload.getValue("resultObservation").jsonObject.getValue("observationBody").jsonPrimitive.content).jsonObject
            after.getValue("state").jsonObject.getValue("viewingPlayerId").jsonPrimitive.content shouldBe seat.value
            result.payload.getValue("choice").jsonObject.getValue("params") shouldBe buildJsonObject {}
        }
        test("native bottom choice records selected masked cards and stale repeat cannot certify") {
            val (session, rows) = session()
            players(session)
            val seat = session.getPlayers().first().playerId
            session.takeMulligan(seat)
            session.keepHand(seat)
            val message = session.getChooseBottomCardsMessage(seat)!!
            val context = session.beginAiBottomEvidence(seat, message)!!
            val selected = message.hand.take(message.cardsToPutOnBottom)
            session.executeRecordedBottom(seat, context, selected)
            session.executeRecordedBottom(seat, context, selected)
            rows.count { it.kind == "ai_callback_result" } shouldBe 1
            val target = rows.single { it.kind == "ai_callback_result" }.payload.getValue("choice").jsonObject.getValue("response").jsonObject
            target.getValue("selectedCards") shouldBe JsonArray(selected.map { JsonPrimitive(it.value) })
            target.containsKey("decisionId") shouldBe false
            rows.none { it.kind == "ai_callback_disposition" } shouldBe true
        }
        test("structured native callback joins exact current question to application") {
            val game = scenario().withPlayers("Fixture Human", "Fixture Seat")
                .withCardInHand(1, "Crackling Doom").withLandsOnBattlefield(1, "Mountain", 1)
                .withLandsOnBattlefield(1, "Plains", 1).withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(2, "Hill Giant").withCardOnBattlefield(2, "Raging Minotaur")
                .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Crackling Doom")
            game.resolveStack()
            val (session, rows) = session()
            session.injectStateForDevScenario(game.state)
            val update = offer(session, game.player2Id)
            val pending = update.pendingDecision as SelectCardsDecision
            val response = CardsSelectedResponse(pending.id, listOf(pending.options.first()))
            val context = session.beginAiCallbackEvidence(game.player2Id, update.state, update.legalActions, pending, update.interactionEpoch, emptyList())!!
            session.executeRecordedAiCallback(game.player2Id, SubmitDecision(game.player2Id, response), update.interactionEpoch,
                context, RecordedAiChoice(ActionResponse.SubmitDecision(game.player2Id, response)))
            val target = rows.single { it.kind == "ai_callback_result" }.payload.getValue("choice").jsonObject
            target.getValue("channel").jsonPrimitive.content shouldBe "decision"
            target.getValue("response").jsonObject.containsKey("decisionId") shouldBe false
        }
        test("overridden terminal action disposes its callback before terminal") {
            val (session, rows) = session()
            players(session)
            session.getPlayers().forEach { session.keepHand(it.playerId) }
            val seat = session.getStateForTesting()!!.priorityPlayerId!!
            val update = offer(session, seat)
            val context = session.beginAiCallbackEvidence(seat, update.state, update.legalActions, update.pendingDecision, update.interactionEpoch, emptyList())!!
            val index = update.legalActions.indexOfFirst { it.action is PassPriority }
            val selected = update.legalActions[index].action
            session.executeRecordedAiCallback(seat, Concede(seat), update.interactionEpoch, context,
                RecordedAiChoice(ActionResponse.SubmitAction(selected), index, buildJsonObject {}))
            rows.count { it.kind == "ai_callback_disposition" } shouldBe 1
            rows.none { it.kind == "ai_callback_result" } shouldBe true
            (rows.indexOfFirst { it.kind == "ai_callback_disposition" } < rows.indexOfFirst { it.kind == "terminal" }) shouldBe true
            session.closeEvidence()
            rows.last().kind shouldBe "session_closed"
        }
        test("payment feedback is authenticated in distinct native observation bytes") {
            val (session, _) = session()
            players(session)
            session.getPlayers().forEach { session.keepHand(it.playerId) }
            val seat = session.getStateForTesting()!!.priorityPlayerId!!
            val update = offer(session, seat)
            val first = actionCallbackEvidence(update.state, update.legalActions, update.pendingDecision, seat.value, emptyList(), "paymentCorrection", "native error one")
            val second = actionCallbackEvidence(update.state, update.legalActions, update.pendingDecision, seat.value, emptyList(), "paymentCorrection", "native error two")
            (first.stateDigest != second.stateDigest) shouldBe true
            Json.parseToJsonElement(first.observationBody).jsonObject.getValue("nativePaymentError").jsonPrimitive.content shouldBe "native error one"
        }
        test("superseded cancelled stale and overridden contexts retain explicit dispositions") {
            val (session, rows) = session()
            players(session)
            session.getPlayers().forEach { session.keepHand(it.playerId) }
            val seat = session.getStateForTesting()!!.priorityPlayerId!!
            val update = offer(session, seat)
            val first = session.beginAiCallbackEvidence(seat, update.state, update.legalActions, update.pendingDecision, update.interactionEpoch, emptyList())!!
            val second = session.beginAiCallbackEvidence(seat, update.state, update.legalActions, update.pendingDecision, update.interactionEpoch, emptyList())!!
            session.disposeAiCallback(seat, second, "cancelled")
            val index = update.legalActions.indexOfFirst { it.action is PassPriority }
            val pass = update.legalActions[index].action
            session.executeRecordedAiCallback(seat, pass, "obsolete", first,
                RecordedAiChoice(ActionResponse.SubmitAction(pass), index, buildJsonObject {}))
            val third = session.beginAiCallbackEvidence(seat, update.state, update.legalActions, update.pendingDecision, update.interactionEpoch, emptyList())!!
            session.executeRecordedAiCallback(seat, pass, "obsolete", third,
                RecordedAiChoice(ActionResponse.SubmitAction(pass), Int.MAX_VALUE, buildJsonObject {}))
            rows.none { it.kind == "ai_callback_result" } shouldBe true
            rows.filter { it.kind == "ai_callback_disposition" }.groupBy { it.payload.getValue("correlationId").jsonPrimitive.content }
                .values.all { it.size == 1 } shouldBe true
            session.closeEvidence()
            rows.last().kind shouldBe "session_closed"
            rows.filter { it.kind == "ai_callback_disposition" }.map { it.payload.getValue("status").jsonPrimitive.content }
                .containsAll(listOf("superseded", "cancelled", "stale")) shouldBe true
        }
    }
}
