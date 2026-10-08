package com.wingedsheep.ai

import kotlinx.serialization.Serializable
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.LegalActionInfo

/** Opt-in extension preserves the existing controller ABI. */
interface RecordedAiPlayerController : AiPlayerController {
    fun chooseRecordedAction(state: ClientGameState, legalActions: List<LegalActionInfo>,
                             recentGameLog: List<String>, evidence: AiDecisionEvidence): ActionResponse
}

/** Native seat-visible evidence. No rules state or transport credentials belong here. */
@Serializable
data class AiDecisionEvidence(
    val version: Int = 1,
    val correlationId: String,
    /** Exact native UTF-8 JSON bytes, retained so consumers never invent a hash recipe. */
    val observationBody: String,
    val schemaHash: String,
    val stateDigest: String,
)
