package com.wingedsheep.gym.contract

import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.provenance.SemanticFingerprint

/**
 * Gym facade over Argentum's engine-level semantic provenance primitive.
 *
 * Gym supplies its contract [SchemaHash] as the surface scope, while the canonical semantic
 * fingerprinting itself lives in :rules-engine so game-server and other Argentum surfaces can use
 * the same primitive without depending on Gym.
 *
 * Execution handles such as the per-observation integer `actionId` and [PendingDecision.id] remain
 * authoritative for live submission. These fingerprints are durable provenance only.
 */
object SemanticIdentity {
    const val ACTION_VERSION: String = SemanticFingerprint.ACTION_VERSION
    const val DECISION_VERSION: String = SemanticFingerprint.DECISION_VERSION
    const val RESPONSE_VERSION: String = SemanticFingerprint.RESPONSE_VERSION

    fun forLegalAction(action: LegalAction): String =
        SemanticFingerprint.forLegalAction(action, SchemaHash.CURRENT)

    fun forPendingDecision(decision: PendingDecision): String =
        SemanticFingerprint.forPendingDecision(decision, SchemaHash.CURRENT)

    fun forDecisionResponse(response: DecisionResponse): String =
        SemanticFingerprint.forDecisionResponse(response, SchemaHash.CURRENT)
}
