package com.wingedsheep.gameserver.recording

import com.wingedsheep.ai.AiDecisionEvidence
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.provenance.SemanticFingerprint
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.gameserver.persistence.persistenceJson
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.*
import java.util.UUID

const val AI_CALLBACK_SCHEMA = "argentum-ai-callback-seat-evidence-v2"
private const val SCOPE = "commander-gym-game-server-policy-v1"

fun callbackEvidence(kind: String, state: JsonObject, legal: JsonArray, pending: JsonElement,
                     seat: String, log: List<String> = emptyList(), terminated: Boolean = false, paymentError: String? = null): AiDecisionEvidence {
    val body = buildJsonObject {
        put("type", "GameServerSeat")
        put("callbackKind", kind)
        paymentError?.let { put("nativePaymentError", it) }
        put("state", state)
        put("legalActions", legal)
        put("pendingDecision", pending)
        put("recentGameLog", JsonArray(log.map(::JsonPrimitive)))
        put("perspectivePlayerId", seat)
        put("agentToAct", seat)
        put("terminated", terminated)
    }.toString()
    return AiDecisionEvidence(version = 2, correlationId = UUID.randomUUID().toString(), observationBody = body,
        schemaHash = PrivateGameEvidence.sha256(AI_CALLBACK_SCHEMA.toByteArray(Charsets.UTF_8)),
        stateDigest = PrivateGameEvidence.sha256(body.toByteArray(Charsets.UTF_8)))
}

fun actionCallbackEvidence(state: ClientGameState, legal: List<LegalActionInfo>, pending: PendingDecision?,
                           seat: String, log: List<String>, kind: String = "chooseAction", paymentError: String? = null): AiDecisionEvidence {
    val actions = JsonArray(legal.mapIndexed { index, info -> buildJsonObject {
        persistenceJson.encodeToJsonElement(LegalActionInfo.serializer(), info).jsonObject.forEach { (k, v) -> put(k, v) }
        put("actionId", index)
        put("kind", info.actionType)
        put("affordable", info.isAffordable)
        put("semanticId", SemanticFingerprint.forGameAction(info.actionType, info.action, SCOPE))
    } })
    val decision = pending?.let { buildJsonObject {
        val encoded = persistenceJson.encodeToJsonElement(PendingDecision.serializer(), it).jsonObject
        encoded.forEach { (k, v) -> put(k, v) }
        put("decisionId", it.id)
        put("kind", encoded.getValue("type"))
        put("requiresStructuredResponse", true)
        put("semanticId", SemanticFingerprint.forPendingDecision(it, SCOPE))
        put("responseSpec", persistenceJson.encodeToJsonElement(DecisionResponseSpec.serializer(), it.responseSpec()))
    } } ?: JsonNull
    return callbackEvidence(kind, persistenceJson.encodeToJsonElement(ClientGameState.serializer(), state).jsonObject,
        actions, decision, seat, log, state.isGameOver, paymentError)
}

private fun cardsPayload(cards: Map<EntityId, ServerMessage.MulliganCardInfo>) = buildJsonObject {
    cards.forEach { (id, card) -> put(id.value, buildJsonObject {
        put("name", card.name)
        card.manaCost?.let { put("manaCost", it) }
        card.typeLine?.let { put("typeLine", it) }
        card.oracleText?.let { put("oracleText", it) }
        card.power?.let { put("power", it) }
        card.toughness?.let { put("toughness", it) }
    }) }
}

fun mulliganPayload(message: ServerMessage.MulliganDecision) = buildJsonObject {
    put("hand", JsonArray(message.hand.map { JsonPrimitive(it.value) }))
    put("mulliganCount", message.mulliganCount)
    put("cardsToPutOnBottom", message.cardsToPutOnBottom)
    put("isOnThePlay", message.isOnThePlay)
    put("cards", cardsPayload(message.cards))
}

fun bottomPayload(message: ServerMessage.ChooseBottomCards) = buildJsonObject {
    put("hand", JsonArray(message.hand.map { JsonPrimitive(it.value) }))
    put("cardsToPutOnBottom", message.cardsToPutOnBottom)
    put("cards", cardsPayload(message.cards))
}

fun mulliganCallbackEvidence(message: ServerMessage.MulliganDecision, seat: String): AiDecisionEvidence {
    val actions = JsonArray(listOf(KeepHand(EntityId.of(seat)), TakeMulligan(EntityId.of(seat))).mapIndexed { index, action ->
        val kind = if (index == 0) "KeepHand" else "TakeMulligan"
        buildJsonObject {
            put("action", persistenceJson.encodeToJsonElement(GameAction.serializer(), action))
            put("actionType", kind); put("kind", kind); put("actionId", index); put("affordable", true)
            put("semanticId", SemanticFingerprint.forGameAction(kind, action, SCOPE))
            put("parameterSpec", buildJsonObject { put("allowedFields", buildJsonObject {}) })
        }
    })
    return callbackEvidence("decideMulligan", buildJsonObject { put("mulligan", mulliganPayload(message)) }, actions, JsonNull, seat)
}

fun bottomCallbackEvidence(message: ServerMessage.ChooseBottomCards, seat: String): AiDecisionEvidence {
    val payload = bottomPayload(message)
    val semantic = "argentum-bottom-cards-v2:" + PrivateGameEvidence.sha256(payload.toString().toByteArray(Charsets.UTF_8))
    val pending = buildJsonObject {
        put("decisionId", "bottom-cards:$seat")
        put("semanticId", semantic); put("kind", "BottomCards")
        put("prompt", "Choose cards to put on the bottom of your library")
        put("requiresStructuredResponse", true)
        put("responseSpec", buildJsonObject {
            put("responseType", "CardsSelectedResponse")
            put("requiredFields", buildJsonObject { put("selectedCards", "ENTITY_ID_ARRAY") })
        })
        payload.forEach { (k, v) -> put(k, v) }
    }
    return callbackEvidence("chooseBottomCards", buildJsonObject {}, JsonArray(emptyList()), pending, seat)
}
