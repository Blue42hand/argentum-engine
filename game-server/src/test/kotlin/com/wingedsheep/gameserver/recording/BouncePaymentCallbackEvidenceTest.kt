package com.wingedsheep.gameserver.recording

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.RecordedAiChoice
import com.wingedsheep.engine.core.*
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.gameserver.persistence.persistenceJson
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.mtg.sets.definitions.scg.cards.WirewoodSymbiote
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*

/** Synthetic callback receipts contain the selected payment without exposing an opponent's hand. */
class BouncePaymentCallbackEvidenceTest : ScenarioTestBase() {
    data class Row(val kind: String, val payload: JsonObject, val seat: String?)
    init {
        cardRegistry.register(WirewoodSymbiote)
        for (stale in listOf(false, true)) {
            test("bounce callback retains payment and seat masking with stale=$stale") {
                val game = scenario().withPlayers("Bounce A", "Bounce B")
                    .withCardOnBattlefield(1, "Wirewood Symbiote")
                    .withCardOnBattlefield(1, "Llanowar Elves")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInHand(2, "Lightning Bolt")
                    .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Forest")
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val rows = mutableListOf<Row>()
                val session = GameSession(cardRegistry = cardRegistry,
                    evidenceSink = GameEvidenceSink { kind, payload, seat -> rows.add(Row(kind, payload.jsonObject, seat)) })
                session.injectStateForDevScenario(game.state)
                session.clearLastSentState(game.player1Id)
                val update = session.createStateUpdate(game.player1Id, emptyList(), useEngineDecisionIds = true) as ServerMessage.StateUpdate
                val source = game.findPermanent("Wirewood Symbiote")!!
                val elf = game.findPermanent("Llanowar Elves")!!
                val target = game.findPermanent("Grizzly Bears")!!
                val index = update.legalActions.indexOfFirst { (it.action as? ActivateAbility)?.sourceId == source }
                (index >= 0) shouldBe true
                update.legalActions[index].additionalCostInfo!!.validBounceTargets shouldBe listOf(elf)
                update.legalActions[index].parameterSpec.allowedFields["bouncedPermanents"] shouldBe ActionParameterFieldKind.ENTITY_ID_ARRAY
                val params = buildJsonObject {
                    put("targets", JsonArray(listOf(JsonPrimitive(target.value))))
                    put("bouncedPermanents", JsonArray(listOf(JsonPrimitive(elf.value))))
                }
                val native = ActionParameterizer.apply(update.legalActions[index],
                    persistenceJson.decodeFromJsonElement(ActionParams.serializer(), params), session.getStateForTesting()!!)
                val context = session.beginAiCallbackEvidence(game.player1Id, update.state, update.legalActions,
                    update.pendingDecision, update.interactionEpoch, emptyList())!!
                session.executeRecordedAiCallback(game.player1Id, native,
                    if (stale) "obsolete-bounce-epoch" else update.interactionEpoch, context,
                    RecordedAiChoice(ActionResponse.SubmitAction(native), index, params))
                if (stale) {
                    rows.none { it.kind == "ai_callback_result" } shouldBe true
                    rows.single { it.kind == "ai_callback_disposition" }.payload.getValue("status").jsonPrimitive.content shouldBe "stale"
                    (elf in session.getStateForTesting()!!.getBattlefield()) shouldBe true
                } else {
                    val result = rows.single { it.kind == "ai_callback_result" }
                    result.seat shouldBe game.player1Id.value
                    result.payload.getValue("choice").jsonObject.getValue("params") shouldBe params
                    result.payload.getValue("action") shouldBe persistenceJson.encodeToJsonElement(GameAction.serializer(), native)
                    (elf in session.getStateForTesting()!!.getHand(game.player1Id)) shouldBe true
                    val after = Json.parseToJsonElement(result.payload.getValue("resultObservation").jsonObject
                        .getValue("observationBody").jsonPrimitive.content).jsonObject
                    after.getValue("state").jsonObject.getValue("viewingPlayerId").jsonPrimitive.content shouldBe game.player1Id.value
                    after.toString().contains("Lightning Bolt") shouldBe false
                }
                session.closeEvidence()
            }
        }
    }
}
