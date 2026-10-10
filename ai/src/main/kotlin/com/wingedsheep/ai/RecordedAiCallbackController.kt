package com.wingedsheep.ai

import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.JsonObject

/** Optional evidence interface; the ordinary controller and v1 recording ABI stay unchanged. */
interface RecordedAiCallbackController : AiPlayerController {
    fun chooseRecordedCallback(state: ClientGameState, legalActions: List<LegalActionInfo>,
                               pendingDecision: PendingDecision?, recentGameLog: List<String>,
                               evidence: AiDecisionEvidence, nativePaymentError: String? = null): RecordedAiChoice
    fun decideRecordedMulligan(info: MulliganInfo, evidence: AiDecisionEvidence): Boolean
    fun chooseRecordedBottomCards(info: BottomCardsInfo, evidence: AiDecisionEvidence): List<EntityId>
}

/** Exact submitted wire parameters, retained separately from the concrete native action. */
data class RecordedAiChoice(val response: ActionResponse, val actionId: Int? = null,
                            val params: JsonObject? = null)
