package com.wingedsheep.gym.contract

import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.provenance.EngineSemanticIdentity

/**
 * Gym-scoped facade over Argentum's engine-level semantic identity primitive.
 *
 * The rules engine owns canonical semantic fingerprinting. Gym contributes only its schema scope,
 * preserving the existing Gym identifiers while allowing game-server and other engine consumers
 * to reuse the same canonicalization without depending on :gym.
 */
object SemanticIdentity {
    const val ACTION_VERSION: String = EngineSemanticIdentity.ACTION_VERSION
    const val DECISION_VERSION: String = EngineSemanticIdentity.DECISION_VERSION
    const val RESPONSE_VERSION: String = EngineSemanticIdentity.RESPONSE_VERSION

    fun forLegalAction(action: LegalAction): String =
        EngineSemanticIdentity.forLegalAction(action, SchemaHash.CURRENT)

    fun forPendingDecision(decision: PendingDecision): String =
        EngineSemanticIdentity.forPendingDecision(decision, SchemaHash.CURRENT)

    fun forDecisionResponse(response: DecisionResponse): String =
        EngineSemanticIdentity.forDecisionResponse(response, SchemaHash.CURRENT)
}
