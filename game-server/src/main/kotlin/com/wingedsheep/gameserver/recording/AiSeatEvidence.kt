package com.wingedsheep.gameserver.recording

import com.wingedsheep.ai.AiDecisionEvidence
import com.wingedsheep.engine.provenance.SemanticFingerprint
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.gameserver.persistence.persistenceJson
import kotlinx.serialization.json.*
import java.util.UUID

/** Hash recipe and semantic scope are native-owned and versioned. */
const val AI_SEAT_SCHEMA = "argentum-ai-enumerated-seat-evidence-v1"
private const val POLICY_SCOPE = "commander-gym-game-server-policy-v1"

fun aiSeatEvidence(state: ClientGameState, legal: List<LegalActionInfo>, seat: String,
                   log: List<String> = emptyList()): AiDecisionEvidence {
    val actions = JsonArray(legal.mapIndexed { index, item ->
        buildJsonObject {
            persistenceJson.encodeToJsonElement(LegalActionInfo.serializer(), item).jsonObject.forEach { (k, v) -> put(k, v) }
            put("actionId", index)
            put("kind", item.actionType)
            put("affordable", item.isAffordable)
            put("semanticId", SemanticFingerprint.forGameAction(item.actionType, item.action, POLICY_SCOPE))
        }
    })
    val body = buildJsonObject {
        put("type", "GameServerSeat")
        put("state", persistenceJson.encodeToJsonElement(ClientGameState.serializer(), state))
        put("legalActions", actions)
        put("pendingDecision", JsonNull)
        put("recentGameLog", JsonArray(log.map(::JsonPrimitive)))
        put("perspectivePlayerId", seat)
        put("agentToAct", seat)
        put("terminated", state.isGameOver)
    }.toString()
    return AiDecisionEvidence(correlationId = UUID.randomUUID().toString(), observationBody = body,
        schemaHash = PrivateGameEvidence.sha256(AI_SEAT_SCHEMA.toByteArray(Charsets.UTF_8)),
        stateDigest = PrivateGameEvidence.sha256(body.toByteArray(Charsets.UTF_8)))
}
